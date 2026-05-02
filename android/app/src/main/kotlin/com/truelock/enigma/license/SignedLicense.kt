package com.truelock.enigma.license

import android.util.Base64
import org.json.JSONObject

data class SignedLicense(
    val payloadJson: String,
    val payload: LicensePayload,
    val signatureBase64: String,
) {
    fun toStoredJson(): String =
        JSONObject()
            .put("payload", Base64Url.encode(payloadJson.toByteArray(Charsets.UTF_8)))
            .put("signature", signatureBase64)
            .toString()

    companion object {
        fun parse(rawInput: String): SignedLicense {
            val normalized = normalizeInput(rawInput)
            val json = JSONObject(normalized)
            val payloadEncoded = json.getString("payload")
            val payloadJson = Base64Url.decodeToString(payloadEncoded)
            return SignedLicense(
                payloadJson = payloadJson,
                payload = LicensePayload.fromJson(payloadJson),
                signatureBase64 = json.getString("signature"),
            )
        }

        private fun normalizeInput(rawInput: String): String {
            val trimmed = rawInput.trim()
            if (trimmed.startsWith("{")) return trimmed

            var cleaned = trimmed.replace(Regex("[^A-Za-z0-9_-]"), "")
            if (cleaned.startsWith("VEIL", ignoreCase = true)) {
                cleaned = cleaned.substring(4)
            }
            return Base64Url.decodeToString(cleaned)
        }
    }
}

object Base64Url {
    fun encode(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    fun decode(value: String): ByteArray {
        val normalized = value + "=".repeat((4 - value.length % 4) % 4)
        return Base64.decode(normalized, Base64.URL_SAFE or Base64.NO_WRAP)
    }

    fun decodeToString(value: String): String =
        decode(value).toString(Charsets.UTF_8)
}
