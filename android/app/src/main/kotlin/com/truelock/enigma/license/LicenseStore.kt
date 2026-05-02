package com.truelock.enigma.license

import android.content.Context
import java.util.UUID

class LicenseStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("veiltype_license_store_v1", Context.MODE_PRIVATE)
    private val verifier = LicenseVerifier()

    data class Entitlement(
        val active: Boolean,
        val payload: LicensePayload?,
        val reason: Reason,
    ) {
        enum class Reason {
            ACTIVE,
            MISSING,
            INVALID_FORMAT,
            INVALID_SIGNATURE,
            WRONG_DEVICE,
            EXPIRED,
            FREE_PLAN,
        }
    }

    fun deviceId(): String {
        prefs.getString(KEY_DEVICE_ID, null)?.let { return it }
        val next = UUID.randomUUID().toString().replace("-", "")
        prefs.edit().putString(KEY_DEVICE_ID, next).apply()
        return next
    }

    fun current(): Entitlement {
        val raw = prefs.getString(KEY_LICENSE, null)?.takeIf { it.isNotBlank() }
            ?: return Entitlement(false, null, Entitlement.Reason.MISSING)
        return parseAndValidate(raw)
    }

    fun isActive(): Boolean = current().active

    fun activate(rawLicense: String): Entitlement {
        val entitlement = parseAndValidate(rawLicense)
        if (entitlement.active) {
            val signed = SignedLicense.parse(rawLicense)
            prefs.edit().putString(KEY_LICENSE, signed.toStoredJson()).apply()
        }
        return entitlement
    }

    fun clear() {
        prefs.edit().remove(KEY_LICENSE).apply()
    }

    private fun parseAndValidate(rawLicense: String): Entitlement {
        val signed = runCatching { SignedLicense.parse(rawLicense) }
            .getOrElse { return Entitlement(false, null, Entitlement.Reason.INVALID_FORMAT) }
        if (!verifier.verify(signed)) {
            return Entitlement(false, signed.payload, Entitlement.Reason.INVALID_SIGNATURE)
        }
        val boundDevice = signed.payload.deviceId
        if (boundDevice != null && boundDevice != deviceId()) {
            return Entitlement(false, signed.payload, Entitlement.Reason.WRONG_DEVICE)
        }
        if (signed.payload.isExpired) {
            return Entitlement(false, signed.payload, Entitlement.Reason.EXPIRED)
        }
        if (!signed.payload.isPaid) {
            return Entitlement(false, signed.payload, Entitlement.Reason.FREE_PLAN)
        }
        return Entitlement(true, signed.payload, Entitlement.Reason.ACTIVE)
    }

    companion object {
        private const val KEY_LICENSE = "signed_license"
        private const val KEY_DEVICE_ID = "device_id"
    }
}
