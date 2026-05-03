package com.truelock.enigma.security

import android.content.Context
import java.security.MessageDigest

class DecryptUsageStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun messageFingerprint(encodedMessage: String): String = sha256(encodedMessage.toByteArray(Charsets.UTF_8))

    fun mediaFingerprint(bytes: ByteArray): String = sha256(bytes)

    fun isConsumed(profileId: String, fingerprint: String): Boolean =
        prefs.contains(storageKey(profileId, fingerprint))

    fun markConsumed(profileId: String, fingerprint: String) {
        prefs.edit().putLong(storageKey(profileId, fingerprint), System.currentTimeMillis()).commit()
    }

    private fun storageKey(profileId: String, fingerprint: String): String = "$profileId:$fingerprint"

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val PREFS_NAME = "veiltype_decrypt_usage"
    }
}
