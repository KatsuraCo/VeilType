package com.truelock.enigma.exchange

import android.content.Context
import android.os.Build
import com.truelock.enigma.crypto.Base64Url
import com.truelock.enigma.R
import org.json.JSONObject
import java.io.File
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import java.util.UUID

class IdentityStore(
    private val appContext: Context,
    private val keyVault: IdentityKeyVault = IdentityKeyVault(),
) {
    private val file = File(appContext.noBackupFilesDir, "enigma_identity.json")

    fun getOrCreateIdentity(displayNameOverride: String? = null): LocalIdentity {
        val current = loadIdentity()
        if (current == null) {
            return generateIdentity(displayNameOverride?.trim().orEmpty().ifBlank { defaultDisplayName() })
        }
        return if (!displayNameOverride.isNullOrBlank() && displayNameOverride.trim() != current.displayName) {
            current.copy(displayName = displayNameOverride.trim()).also(::saveIdentity)
        } else {
            current
        }
    }

    fun exportBundle(displayNameOverride: String? = null): ContactBundle {
        val identity = getOrCreateIdentity(displayNameOverride)
        return ContactBundle(
            deviceId = identity.deviceId,
            displayName = identity.displayName,
            createdAt = identity.createdAt,
            publicKeyEncoded = identity.publicKeyEncoded,
        )
    }

    fun loadPrivateKey(identity: LocalIdentity): PrivateKey {
        val decoded = keyVault.unwrap(identity.wrappedPrivateKeyEncoded)
        val keyFactory = KeyFactory.getInstance("EC")
        return keyFactory.generatePrivate(PKCS8EncodedKeySpec(decoded))
    }

    fun decodePublicKey(encoded: ByteArray): PublicKey {
        val keyFactory = KeyFactory.getInstance("EC")
        return keyFactory.generatePublic(X509EncodedKeySpec(encoded))
    }

    private fun loadIdentity(): LocalIdentity? {
        if (!file.exists()) return null
        val raw = file.readText(Charsets.UTF_8).trim()
        if (raw.isEmpty()) return null

        val json = JSONObject(raw)
        return LocalIdentity(
            deviceId = json.getString("device_id"),
            displayName = json.getString("display_name"),
            createdAt = Instant.parse(json.getString("created_at")),
            publicKeyEncoded = Base64Url.decode(json.getString("public_key_b64")),
            wrappedPrivateKeyEncoded = Base64Url.decode(json.getString("wrapped_private_key_b64")),
        )
    }

    private fun generateIdentity(displayName: String): LocalIdentity {
        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        keyPairGenerator.initialize(ECGenParameterSpec("secp256r1"))
        val keyPair = keyPairGenerator.generateKeyPair()

        val identity = LocalIdentity(
            deviceId = "dev_${UUID.randomUUID().toString().take(12)}",
            displayName = displayName,
            createdAt = Instant.now(),
            publicKeyEncoded = keyPair.public.encoded,
            wrappedPrivateKeyEncoded = keyVault.wrap(keyPair.private.encoded),
        )
        saveIdentity(identity)
        return identity
    }

    private fun saveIdentity(identity: LocalIdentity) {
        val payload = JSONObject()
            .put("device_id", identity.deviceId)
            .put("display_name", identity.displayName)
            .put("created_at", identity.createdAt.toString())
            .put("public_key_b64", Base64Url.encodeNoPadding(identity.publicKeyEncoded))
            .put("wrapped_private_key_b64", Base64Url.encodeNoPadding(identity.wrappedPrivateKeyEncoded))
            .toString()

        file.parentFile?.mkdirs()
        file.writeText(payload, Charsets.UTF_8)
    }

    private fun defaultDisplayName(): String =
        Build.MODEL?.trim().orEmpty().ifBlank {
            appContext.getString(R.string.key_exchange_identity_default_name)
        }
}
