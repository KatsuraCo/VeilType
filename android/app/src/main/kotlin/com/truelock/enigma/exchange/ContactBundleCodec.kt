package com.truelock.enigma.exchange

import com.truelock.enigma.crypto.Base64Url
import org.json.JSONObject
import java.time.Instant

object ContactBundleCodec {
    private const val PREFIX = "EKC1:"

    fun encode(bundle: ContactBundle): String {
        val payload = JSONObject()
            .put("v", bundle.version)
            .put("app", bundle.app)
            .put("device_id", bundle.deviceId)
            .put("display_name", bundle.displayName)
            .put("created_at", bundle.createdAt.toString())
            .put("identity_public_key", Base64Url.encodeNoPadding(bundle.publicKeyEncoded))
            .toString()

        return PREFIX + Base64Url.encodeNoPadding(payload.toByteArray(Charsets.UTF_8))
    }

    fun decode(raw: String): ContactBundle {
        require(raw.startsWith(PREFIX)) { "Unsupported bundle format" }
        val json = JSONObject(String(Base64Url.decode(raw.removePrefix(PREFIX)), Charsets.UTF_8))
        return ContactBundle(
            version = json.getInt("v"),
            app = json.getString("app"),
            deviceId = json.getString("device_id"),
            displayName = json.getString("display_name"),
            createdAt = Instant.parse(json.getString("created_at")),
            publicKeyEncoded = Base64Url.decode(json.getString("identity_public_key")),
        )
    }
}
