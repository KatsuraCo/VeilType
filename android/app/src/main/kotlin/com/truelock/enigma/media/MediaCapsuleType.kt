package com.truelock.enigma.media

enum class MediaCapsuleType(
    val magic: String,
    val fileExtension: String,
) {
    AUDIO("TLA1", "tla1"),
    VIDEO("TLV1", "tlv1"),
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
