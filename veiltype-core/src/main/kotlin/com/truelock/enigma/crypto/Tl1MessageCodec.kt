package com.truelock.enigma.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class Tl1MessageCodec(
    private val secureRandom: SecureRandom = SecureRandom(),
) {
    companion object {
        const val PREFIX = "TL1:"
        const val VERSION = 0x01
        const val ALGORITHM_ID_AES_256_GCM = 0x01
        const val DEFAULT_FLAGS = 0x03
        const val RESERVED = 0x00
        const val PROFILE_HINT_BYTES = 8
        const val NONCE_BYTES = 12
        const val TAG_BYTES = 16
        const val HEADER_BYTES = 24
    }

    fun encrypt(
        plaintext: String,
        profileKey: ByteArray,
        profileHint: ByteArray,
        nonceOverride: ByteArray? = null,
    ): Tl1Ciphertext {
        require(profileKey.size == 32) { "profileKey must be 32 bytes" }
        require(profileHint.size == PROFILE_HINT_BYTES) { "profileHint must be 8 bytes" }
        require(plaintext.isNotEmpty()) { "plaintext must not be empty" }

        val nonce = nonceOverride ?: ByteArray(NONCE_BYTES).also(secureRandom::nextBytes)
        require(nonce.size == NONCE_BYTES) { "nonce must be 12 bytes" }

        val header = Tl1Header(
            version = VERSION,
            algorithmId = ALGORITHM_ID_AES_256_GCM,
            flags = DEFAULT_FLAGS,
            reserved = RESERVED,
            profileHint = profileHint,
            nonce = nonce,
        )
        val aad = header.toBytes()

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(TAG_BYTES * 8, nonce)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(profileKey, "AES"), spec)
        cipher.updateAAD(aad)
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val ciphertext = encrypted.copyOfRange(0, encrypted.size - TAG_BYTES)
        val tag = encrypted.copyOfRange(encrypted.size - TAG_BYTES, encrypted.size)
        val payload = aad + ciphertext + tag

        return Tl1Ciphertext(
            version = VERSION,
            algorithmId = ALGORITHM_ID_AES_256_GCM,
            flags = DEFAULT_FLAGS,
            reserved = RESERVED,
            profileHint = profileHint,
            nonce = nonce,
            ciphertext = ciphertext,
            tag = tag,
            encodedMessage = PREFIX + Base64Url.encodeNoPadding(payload),
        )
    }

    fun decrypt(encodedMessage: String, candidateProfileKeys: List<ByteArray>): String {
        require(encodedMessage.startsWith(PREFIX)) { "Message prefix not recognized" }
        val payload = Base64Url.decode(encodedMessage.removePrefix(PREFIX))
        require(payload.size >= HEADER_BYTES + TAG_BYTES) { "Payload too short" }

        val headerBytes = payload.copyOfRange(0, HEADER_BYTES)
        val version = headerBytes[0].toInt() and 0xFF
        val algorithmId = headerBytes[1].toInt() and 0xFF
        require(version == VERSION) { "Unsupported version: $version" }
        require(algorithmId == ALGORITHM_ID_AES_256_GCM) { "Unsupported algorithm: $algorithmId" }

        val nonce = headerBytes.copyOfRange(12, 24)
        val cipherBytes = payload.copyOfRange(HEADER_BYTES, payload.size)

        candidateProfileKeys.forEach { key ->
            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val spec = GCMParameterSpec(TAG_BYTES * 8, nonce)
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), spec)
                cipher.updateAAD(headerBytes)
                val plaintext = cipher.doFinal(cipherBytes)
                return plaintext.toString(Charsets.UTF_8)
            } catch (_: Exception) {
                // Try next candidate.
            }
        }

        throw IllegalArgumentException("Wrong key or invalid message")
    }

    fun extractProfileHint(encodedMessage: String): ByteArray {
        require(encodedMessage.startsWith(PREFIX)) { "Message prefix not recognized" }
        val payload = Base64Url.decode(encodedMessage.removePrefix(PREFIX))
        require(payload.size >= HEADER_BYTES + TAG_BYTES) { "Payload too short" }
        return payload.copyOfRange(4, 12)
    }
}
