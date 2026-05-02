package com.truelock.enigma.license

import org.json.JSONObject
import java.time.Instant

data class LicensePayload(
    val licenseId: String,
    val plan: String,
    val issuedAt: Instant,
    val expiresAt: Instant?,
    val deviceId: String?,
) {
    val isPaid: Boolean
        get() = plan.equals("lifetime", ignoreCase = true) ||
            plan.equals("pro", ignoreCase = true)

    val isExpired: Boolean
        get() = expiresAt?.isBefore(Instant.now()) == true

    companion object {
        fun fromJson(raw: String): LicensePayload {
            val json = JSONObject(raw)
            return LicensePayload(
                licenseId = json.optString("licenseId", "unknown"),
                plan = json.optString("plan", "free"),
                issuedAt = runCatching { Instant.parse(json.optString("issuedAt")) }
                    .getOrDefault(Instant.EPOCH),
                expiresAt = json.optString("expiresAt")
                    .takeIf { it.isNotBlank() }
                    ?.let { runCatching { Instant.parse(it) }.getOrNull() },
                deviceId = json.optString("deviceId").takeIf { it.isNotBlank() },
            )
        }
    }
}
