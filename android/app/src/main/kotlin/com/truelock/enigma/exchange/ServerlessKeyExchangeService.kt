package com.truelock.enigma.exchange

import com.truelock.enigma.crypto.Hkdf
import com.truelock.enigma.profiles.KeyProfileFactory
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import javax.crypto.KeyAgreement

class ServerlessKeyExchangeService(
    private val identityStore: IdentityStore,
    private val profileFactory: KeyProfileFactory = KeyProfileFactory(),
) {
    private val fingerprintAlphabet = listOf(
        "🔒", "🛡️", "⚡", "🌙", "🦊", "🧠", "🔥", "🛰️",
        "🧿", "🎯", "🗝️", "🧭", "🧊", "🌊", "🪙", "🎲",
        "🧬", "🐺", "🦅", "🌌", "☂️", "💎", "🪐", "🕶️",
        "🌿", "🍀", "🚀", "🏔️", "🎧", "🕯️", "🫧", "📡",
    )

    fun exportContactBundle(displayNameOverride: String? = null): String =
        ContactBundleCodec.encode(identityStore.exportBundle(displayNameOverride))

    fun importContactBundle(
        encodedBundle: String,
        appPackage: String? = null,
        rotationHours: Int = 48,
    ): HandshakePreview {
        val localIdentity = identityStore.getOrCreateIdentity()
        val remoteBundle = ContactBundleCodec.decode(encodedBundle.trim())

        require(remoteBundle.app == ContactBundle.APP_MARKER) { "Unsupported contact bundle" }
        require(remoteBundle.deviceId != localIdentity.deviceId) { "Cannot import own contact bundle" }
        require(!remoteBundle.publicKeyEncoded.contentEquals(localIdentity.publicKeyEncoded)) {
            "Cannot import own contact bundle"
        }

        val localPrivateKey = identityStore.loadPrivateKey(localIdentity)
        val remotePublicKey = identityStore.decodePublicKey(remoteBundle.publicKeyEncoded)
        val sharedSecret = deriveSharedSecret(localPrivateKey, remotePublicKey)
        val salt = sha256(
            "EK_SALT_V1".toByteArray(Charsets.UTF_8),
            stableTranscript(localIdentity, remoteBundle),
        )

        val profileKey = Hkdf.deriveSha256(
            ikm = sharedSecret,
            salt = salt,
            info = "EK_PROFILE_KEY_V1".toByteArray(Charsets.UTF_8),
            outputLength = 32,
        )
        val profileResult = profileFactory.createFromDerivedKey(
            title = remoteBundle.displayName,
            derivedProfileKey = profileKey,
            appPackage = appPackage?.trim()?.ifBlank { null },
            peerHint = remoteBundle.displayName,
            rotationHours = rotationHours,
        )
        val fingerprintBytes = Hkdf.deriveSha256(
            ikm = sharedSecret,
            salt = salt,
            info = "EK_FINGERPRINT_V1".toByteArray(Charsets.UTF_8),
            outputLength = 6,
        )

        return HandshakePreview(
            remoteBundle = remoteBundle,
            profile = profileResult.profile,
            profileKey = profileResult.profileKey,
            fingerprint = fingerprintBytes.joinToString(" ") { byte ->
                fingerprintAlphabet[(byte.toInt() and 0xFF) % fingerprintAlphabet.size]
            },
        )
    }

    private fun deriveSharedSecret(privateKey: PrivateKey, publicKey: PublicKey): ByteArray {
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(privateKey)
        agreement.doPhase(publicKey, true)
        return agreement.generateSecret()
    }

    private fun stableTranscript(localIdentity: LocalIdentity, remoteBundle: ContactBundle): ByteArray {
        val orderedDeviceIds = listOf(localIdentity.deviceId, remoteBundle.deviceId).sorted()
        val orderedPublicKeys = listOf(localIdentity.publicKeyEncoded, remoteBundle.publicKeyEncoded)
            .sortedWith(compareBy { bytes -> bytes.joinToString(separator = ",") { it.toUByte().toString() } })

        return sha256(
            orderedDeviceIds.joinToString("|").toByteArray(Charsets.UTF_8),
            orderedPublicKeys[0],
            orderedPublicKeys[1],
        )
    }

    private fun sha256(vararg parts: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        parts.forEach(digest::update)
        return digest.digest()
    }
}
