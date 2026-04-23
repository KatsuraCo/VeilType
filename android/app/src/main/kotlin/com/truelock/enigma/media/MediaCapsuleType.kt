package com.truelock.enigma.media

enum class MediaCapsuleType(
    val magic: String,
    val fileExtension: String,
    val capsuleMimeType: String,
    val legacyMimeTypes: List<String> = emptyList(),
) {
    AUDIO(
        "TLA1",
        "tla1",
        "application/x-veiltype-audio-capsule",
        legacyMimeTypes = listOf("application/x-enigma-audio-capsule"),
    ),
    VIDEO(
        "TLV1",
        "tlv1",
        "application/x-veiltype-video-capsule",
        legacyMimeTypes = listOf("application/x-enigma-video-capsule"),
    ),
    PHOTO(
        "TLP1",
        "tlp1",
        "application/x-veiltype-photo-capsule",
        legacyMimeTypes = listOf("application/x-enigma-photo-capsule"),
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
