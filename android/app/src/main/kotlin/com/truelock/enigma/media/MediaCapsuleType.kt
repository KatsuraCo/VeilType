package com.truelock.enigma.media

enum class MediaCapsuleType(
    val magic: String,
    val fileExtension: String,
    val capsuleMimeType: String,
    val legacyMimeTypes: List<String> = emptyList(),
    val legacyFileExtensions: List<String> = emptyList(),
) {
    AUDIO(
        "TLA1",
        "veil",
        "application/x-veiltype-audio-capsule",
        legacyMimeTypes = listOf(
            "application/x-enigma-audio-capsule",
        ),
        legacyFileExtensions = listOf("tla1"),
    ),
    VIDEO(
        "TLV1",
        "veil",
        "application/x-veiltype-video-capsule",
        legacyMimeTypes = listOf(
            "application/x-enigma-video-capsule",
        ),
        legacyFileExtensions = listOf("tlv1"),
    ),
    PHOTO(
        "TLP1",
        "veil",
        "application/x-veiltype-photo-capsule",
        legacyMimeTypes = listOf(
            "application/x-enigma-photo-capsule",
        ),
        legacyFileExtensions = listOf("tlp1"),
    ),
    ;

    fun magicBytes(): ByteArray = magic.toByteArray(Charsets.US_ASCII)

    companion object {
        fun fromMagic(magic: ByteArray): MediaCapsuleType {
            val value = magic.toString(Charsets.US_ASCII)
            return entries.firstOrNull { it.magic == value }
                ?: throw IllegalArgumentException("Unsupported media capsule type: $value")
        }
    }
}
