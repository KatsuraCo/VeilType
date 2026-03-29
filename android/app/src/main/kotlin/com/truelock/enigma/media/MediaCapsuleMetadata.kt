package com.truelock.enigma.media

import org.json.JSONObject
import java.time.Instant

data class MediaCapsuleMetadata(
    val mimeType: String,
    val durationMs: Long,
    val createdAt: Instant = Instant.now(),
    val width: Int? = null,
    val height: Int? = null,
    val originalFileName: String? = null,
) {
    init {
        require(mimeType.isNotBlank()) { "mimeType must not be blank" }
        require(durationMs >= 0) { "durationMs must not be negative" }
        require(width == null || width > 0) { "width must be positive" }
        require(height == null || height > 0) { "height must be positive" }
    }

    fun toJsonBytes(): ByteArray {
        val json = JSONObject()
            .put("mime_type", mimeType)
            .put("duration_ms", durationMs)
            .put("created_at", createdAt.toString())

        width?.let { json.put("width", it) }
        height?.let { json.put("height", it) }
        originalFileName?.let { json.put("original_file_name", it) }

        return json.toString().toByteArray(Charsets.UTF_8)
    }

    companion object {
        fun fromJsonBytes(bytes: ByteArray): MediaCapsuleMetadata {
            val json = JSONObject(bytes.toString(Charsets.UTF_8))
            return MediaCapsuleMetadata(
                mimeType = json.getString("mime_type"),
                durationMs = json.getLong("duration_ms"),
                createdAt = Instant.parse(json.getString("created_at")),
                width = json.optInt("width").takeIf { it > 0 },
                height = json.optInt("height").takeIf { it > 0 },
                originalFileName = json.optString("original_file_name").takeIf { it.isNotBlank() },
            )
        }
    }
}
