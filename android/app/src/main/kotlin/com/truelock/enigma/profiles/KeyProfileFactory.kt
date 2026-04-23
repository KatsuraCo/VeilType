package com.truelock.enigma.profiles

import com.truelock.enigma.crypto.ProfileKeyDeriver
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.UUID

class KeyProfileFactory(
    private val secureRandom: SecureRandom = SecureRandom(),
) {
    fun createFromVisualSequence(
        title: String,
        cardIds: List<Int>,
        appPackage: String? = null,
        peerHint: String? = null,
        now: Instant = Instant.now(),
        rotationHours: Int = 48,
        oneTimeRead: Boolean = false,
        requireBiometricForDecrypt: Boolean = false,
        exportAllowed: Boolean = true,
    ): KeyProfileCreationResult {
        require(title.isNotBlank()) { "title must not be blank" }
        require(rotationHours > 0) { "rotationHours must be positive" }

        val sequence = ProfileKeyDeriver.canonicalVisualSequence(cardIds)
        return createInternal(
            title = title,
            canonicalSequence = sequence,
            secretSequenceKind = SecretSequenceKind.VISUAL_SEQUENCE,
            appPackage = appPackage,
            peerHint = peerHint,
            now = now,
            rotationHours = rotationHours,
            oneTimeRead = oneTimeRead,
            requireBiometricForDecrypt = requireBiometricForDecrypt,
            exportAllowed = exportAllowed,
        )
    }

    fun createFromEmojiSequence(
        title: String,
        emojis: List<String>,
        appPackage: String? = null,
        peerHint: String? = null,
        now: Instant = Instant.now(),
        rotationHours: Int = 48,
        oneTimeRead: Boolean = false,
        requireBiometricForDecrypt: Boolean = false,
        exportAllowed: Boolean = true,
    ): KeyProfileCreationResult {
        require(title.isNotBlank()) { "title must not be blank" }
        require(rotationHours > 0) { "rotationHours must be positive" }

        val sequence = ProfileKeyDeriver.canonicalEmojiSequence(emojis)
        return createInternal(
            title = title,
            canonicalSequence = sequence,
            sequenceDisplay = emojis.joinToString(" "),
            secretSequenceKind = SecretSequenceKind.EMOJI_SEQUENCE,
            appPackage = appPackage,
            peerHint = peerHint,
            now = now,
            rotationHours = rotationHours,
            oneTimeRead = oneTimeRead,
            requireBiometricForDecrypt = requireBiometricForDecrypt,
            exportAllowed = exportAllowed,
        )
    }

    fun createFromEmojiSequenceWithSalt(
        title: String,
        emojis: List<String>,
        profileSalt: ByteArray,
        appPackage: String? = null,
        peerHint: String? = null,
        now: Instant = Instant.now(),
        rotationHours: Int = 48,
        oneTimeRead: Boolean = false,
        requireBiometricForDecrypt: Boolean = false,
        exportAllowed: Boolean = true,
    ): KeyProfileCreationResult {
        require(title.isNotBlank()) { "title must not be blank" }
        require(rotationHours > 0) { "rotationHours must be positive" }
        require(profileSalt.size == 16) { "profileSalt must be 16 bytes" }

        val sequence = ProfileKeyDeriver.canonicalEmojiSequence(emojis)
        val profileKey = ProfileKeyDeriver.deriveProfileKey(sequence, profileSalt)
        val hint = ProfileKeyDeriver.deriveProfileHint(profileKey)

        val profile = KeyProfile(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            appPackage = appPackage?.trim()?.ifBlank { null },
            peerHint = peerHint?.trim()?.ifBlank { null },
            secretSequenceDisplay = emojis.joinToString(" "),
            secretSequenceKind = SecretSequenceKind.EMOJI_SEQUENCE,
            profileVersion = 1,
            profileSalt = profileSalt.copyOf(),
            wrappedProfileKey = profileKey.copyOf(),
            profileHint = hint,
            createdAt = now,
            expiresAt = now.plus(Duration.ofHours(rotationHours.toLong())),
            lastUsedAt = null,
            status = KeyProfileStatus.ACTIVE,
            allowDecryptAfterExpiry = true,
            rotationPeriodHours = rotationHours,
            oneTimeRead = oneTimeRead,
            requireBiometricForDecrypt = requireBiometricForDecrypt,
            exportAllowed = exportAllowed,
        )

        return KeyProfileCreationResult(
            profile = profile,
            profileKey = profileKey,
            canonicalSequence = sequence,
        )
    }

    fun createFromDerivedKey(
        title: String,
        derivedProfileKey: ByteArray,
        appPackage: String? = null,
        peerHint: String? = null,
        now: Instant = Instant.now(),
        rotationHours: Int = 48,
        oneTimeRead: Boolean = false,
        requireBiometricForDecrypt: Boolean = false,
        exportAllowed: Boolean = true,
    ): KeyProfileCreationResult {
        require(title.isNotBlank()) { "title must not be blank" }
        require(rotationHours > 0) { "rotationHours must be positive" }
        require(derivedProfileKey.size == 32) { "derivedProfileKey must be 32 bytes" }

        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val salt = digest.digest("EKPS1".toByteArray(Charsets.UTF_8) + derivedProfileKey).copyOfRange(0, 16)
        val hint = ProfileKeyDeriver.deriveProfileHint(derivedProfileKey)

        val profile = KeyProfile(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            appPackage = appPackage?.trim()?.ifBlank { null },
            peerHint = peerHint?.trim()?.ifBlank { null },
            secretSequenceKind = SecretSequenceKind.CONTACT_HANDSHAKE,
            profileVersion = 1,
            profileSalt = salt,
            wrappedProfileKey = derivedProfileKey.copyOf(),
            profileHint = hint,
            createdAt = now,
            expiresAt = now.plus(Duration.ofHours(rotationHours.toLong())),
            lastUsedAt = null,
            status = KeyProfileStatus.ACTIVE,
            allowDecryptAfterExpiry = true,
            rotationPeriodHours = rotationHours,
            oneTimeRead = oneTimeRead,
            requireBiometricForDecrypt = requireBiometricForDecrypt,
            exportAllowed = exportAllowed,
        )

        return KeyProfileCreationResult(
            profile = profile,
            profileKey = derivedProfileKey.copyOf(),
            canonicalSequence = "CONTACT_HANDSHAKE",
        )
    }

    private fun createInternal(
        title: String,
        canonicalSequence: String,
        sequenceDisplay: String? = null,
        secretSequenceKind: SecretSequenceKind,
        appPackage: String? = null,
        peerHint: String? = null,
        now: Instant = Instant.now(),
        rotationHours: Int = 48,
        oneTimeRead: Boolean = false,
        requireBiometricForDecrypt: Boolean = false,
        exportAllowed: Boolean = true,
    ): KeyProfileCreationResult {
        val salt = ByteArray(16).also(secureRandom::nextBytes)
        val profileKey = ProfileKeyDeriver.deriveProfileKey(canonicalSequence, salt)
        val hint = ProfileKeyDeriver.deriveProfileHint(profileKey)

        val profile = KeyProfile(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            appPackage = appPackage?.trim()?.ifBlank { null },
            peerHint = peerHint?.trim()?.ifBlank { null },
            secretSequenceDisplay = sequenceDisplay,
            secretSequenceKind = secretSequenceKind,
            profileVersion = 1,
            profileSalt = salt,
            wrappedProfileKey = profileKey.copyOf(),
            profileHint = hint,
            createdAt = now,
            expiresAt = now.plus(Duration.ofHours(rotationHours.toLong())),
            lastUsedAt = null,
            status = KeyProfileStatus.ACTIVE,
            allowDecryptAfterExpiry = true,
            rotationPeriodHours = rotationHours,
            oneTimeRead = oneTimeRead,
            requireBiometricForDecrypt = requireBiometricForDecrypt,
            exportAllowed = exportAllowed,
        )

        return KeyProfileCreationResult(
            profile = profile,
            profileKey = profileKey,
            canonicalSequence = canonicalSequence,
        )
    }
}

data class KeyProfileCreationResult(
    val profile: KeyProfile,
    val profileKey: ByteArray,
    val canonicalSequence: String,
)
