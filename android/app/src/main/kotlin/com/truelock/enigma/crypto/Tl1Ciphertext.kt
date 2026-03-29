package com.truelock.enigma.crypto

data class Tl1Ciphertext(
    val version: Int,
    val algorithmId: Int,
    val flags: Int,
    val reserved: Int,
    val profileHint: ByteArray,
    val nonce: ByteArray,
    val ciphertext: ByteArray,
    val tag: ByteArray,
    val encodedMessage: String,
)

data class Tl1Header(
    val version: Int,
    val algorithmId: Int,
    val flags: Int,
    val reserved: Int,
    val profileHint: ByteArray,
    val nonce: ByteArray,
) {
    fun toBytes(): ByteArray {
        require(profileHint.size == 8) { "profileHint must be 8 bytes" }
        require(nonce.size == 12) { "nonce must be 12 bytes" }
        return byteArrayOf(
            version.toByte(),
            algorithmId.toByte(),
            flags.toByte(),
            reserved.toByte(),
        ) + profileHint + nonce
    }
}
