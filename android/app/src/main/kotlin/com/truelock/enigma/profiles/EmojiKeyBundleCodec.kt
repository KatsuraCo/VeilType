package com.truelock.enigma.profiles

import com.truelock.enigma.crypto.ProfileKeyDeriver
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64

class EmojiKeyBundleCodec(
    private val secureRandom: SecureRandom = SecureRandom(),
) {
    fun createRandomBundle(
        title: String?,
        appPackage: String? = null,
        peerHint: String? = null,
        size: Int = ProfileKeyDeriver.EMOJI_SEQUENCE_LENGTH,
    ): EmojiKeyBundle =
        EmojiKeyBundle(
            title = title?.trim()?.ifBlank { null },
            emojis = generateRandomSequence(size),
            profileSalt = ByteArray(16).also(secureRandom::nextBytes),
            appPackage = appPackage?.trim()?.ifBlank { null },
            peerHint = peerHint?.trim()?.ifBlank { null },
        )

    fun generateRandomSequence(size: Int = ProfileKeyDeriver.EMOJI_SEQUENCE_LENGTH): List<String> {
        require(size > 0) { "size must be positive" }
        return buildList(size) {
            repeat(size) {
                add(EMOJI_POOL[secureRandom.nextInt(EMOJI_POOL.size)])
            }
        }
    }

    fun encode(bundle: EmojiKeyBundle): String {
        val payload = JSONObject()
            .put("version", 1)
            .put("title", bundle.title)
            .put("emojis", JSONArray(bundle.emojis))
            .put("salt_b64", bundle.profileSalt.toBase64())
            .put("app_package", bundle.appPackage)
            .put("peer_hint", bundle.peerHint)
            .toString()

        return PREFIX + Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payload.toByteArray(Charsets.UTF_8))
    }

    fun decode(raw: String): EmojiKeyBundle {
        val normalized = raw.trim()
        require(normalized.startsWith(PREFIX)) { "invalid key bundle prefix" }
        val payload = String(
            Base64.getUrlDecoder().decode(normalized.removePrefix(PREFIX)),
            Charsets.UTF_8,
        )
        val json = JSONObject(payload)
        require(json.optInt("version", 0) == 1) { "unsupported key bundle version" }
        val emojisJson = json.getJSONArray("emojis")
        val emojis = buildList(emojisJson.length()) {
            for (index in 0 until emojisJson.length()) {
                add(emojisJson.getString(index))
            }
        }
        require(emojis.size == ProfileKeyDeriver.EMOJI_SEQUENCE_LENGTH) {
            "key bundle must contain exactly ${ProfileKeyDeriver.EMOJI_SEQUENCE_LENGTH} emojis"
        }
        return EmojiKeyBundle(
            title = json.optString("title").trim().ifBlank { null },
            emojis = emojis,
            profileSalt = json.getString("salt_b64").fromBase64().also { salt ->
                require(salt.size == 16) { "key bundle salt must be 16 bytes" }
            },
            appPackage = json.optString("app_package").trim().ifBlank { null },
            peerHint = json.optString("peer_hint").trim().ifBlank { null },
        )
    }

    private fun ByteArray.toBase64(): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(this)

    private fun String.fromBase64(): ByteArray =
        Base64.getUrlDecoder().decode(this)

    companion object {
        const val PREFIX = "EKS1:"

        val EMOJI_POOL = listOf(
            "🔒", "🗝️", "🛡️", "🌙", "⭐", "⚡", "🔥", "🧠",
            "🦊", "🐺", "🦉", "🐉", "🐚", "🍀", "🌊", "⛰️",
            "☀️", "🌧️", "❄️", "🌪️", "🪐", "🌋", "🧭", "🕯️",
            "🎯", "🧩", "🎲", "🏹", "🪙", "💎", "📡", "🔋",
            "🛰️", "🔮", "🧿", "🪶", "🕶️", "🦾", "🧱", "🪄",
        )
    }
}

data class EmojiKeyBundle(
    val title: String?,
    val emojis: List<String>,
    val profileSalt: ByteArray,
    val appPackage: String? = null,
    val peerHint: String? = null,
)
