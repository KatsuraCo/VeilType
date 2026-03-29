package com.truelock.enigma.media

data class MediaCapsuleEncryptedFile(
    val type: MediaCapsuleType,
    val version: Int,
    val algorithmId: Int,
    val flags: Int,
    val reserved: Int,
    val profileHint: ByteArray,
    val nonce: ByteArray,
    val ciphertext: ByteArray,
    val tag: ByteArray,
    val containerBytes: ByteArray,
)

data class MediaCapsuleDecoded(
    val type: MediaCapsuleType,
    val metadata: MediaCapsuleMetadata,
    val profileHint: ByteArray,
    val mediaBytes: ByteArray,
)
