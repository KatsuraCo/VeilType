package com.truelock.enigma.profiles
import org.json.JSONObject
import java.time.Instant
import java.util.Base64

object KeyProfileJsonCodec {
    fun encode(profile: KeyProfile): String {
        val json = JSONObject()
            .put("id", profile.id)
            .put("title", profile.title)
            .put("app_package", profile.appPackage)
            .put("peer_hint", profile.peerHint)
            .put("secret_sequence_display", profile.secretSequenceDisplay)
            .put("secret_sequence_kind", profile.secretSequenceKind.name.lowercase())
            .put("profile_version", profile.profileVersion)
            .put("profile_salt_b64", profile.profileSalt.toBase64())
            .put("wrapped_profile_key_b64", profile.wrappedProfileKey.toBase64())
            .put("profile_hint_b64", profile.profileHint.toBase64())
            .put("created_at", profile.createdAt.toString())
            .put("expires_at", profile.expiresAt.toString())
            .put("last_used_at", profile.lastUsedAt?.toString())
            .put("status", profile.status.name.lowercase())
            .put("allow_decrypt_after_expiry", profile.allowDecryptAfterExpiry)
            .put("rotation_period_hours", profile.rotationPeriodHours)
            .put("one_time_read", profile.oneTimeRead)
            .put("require_biometric_for_decrypt", profile.requireBiometricForDecrypt)
            .put("export_allowed", profile.exportAllowed)

        return json.toString()
    }

    fun decode(raw: String): KeyProfile {
        val json = JSONObject(raw)
        return KeyProfile(
            id = json.getString("id"),
            title = json.getString("title"),
            appPackage = json.nullableString("app_package"),
            peerHint = json.nullableString("peer_hint"),
            secretSequenceDisplay = json.nullableString("secret_sequence_display"),
            secretSequenceKind = SecretSequenceKind.valueOf(
                json.getString("secret_sequence_kind").uppercase(),
            ),
            profileVersion = json.getInt("profile_version"),
            profileSalt = json.getString("profile_salt_b64").fromBase64(),
            wrappedProfileKey = json.getString("wrapped_profile_key_b64").fromBase64(),
            profileHint = json.getString("profile_hint_b64").fromBase64(),
            createdAt = Instant.parse(json.getString("created_at")),
            expiresAt = Instant.parse(json.getString("expires_at")),
            lastUsedAt = json.nullableString("last_used_at")?.let(Instant::parse),
            status = KeyProfileStatus.valueOf(json.getString("status").uppercase()),
            allowDecryptAfterExpiry = json.getBoolean("allow_decrypt_after_expiry"),
            rotationPeriodHours = json.getInt("rotation_period_hours"),
            oneTimeRead = json.optBoolean("one_time_read", false),
            requireBiometricForDecrypt = json.optBoolean("require_biometric_for_decrypt", false),
            exportAllowed = json.optBoolean("export_allowed", true),
        )
    }

    private fun ByteArray.toBase64(): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(this)

    private fun String.fromBase64(): ByteArray =
        Base64.getUrlDecoder().decode(this)

    private fun JSONObject.nullableString(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return getString(name).ifBlank { null }
    }
}
