package com.truelock.enigma.media

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class MediaCapsuleFileCodec(
    private val secureRandom: SecureRandom = SecureRandom(),
) {
    companion object {
        const val VERSION = 0x01
        const val ALGORITHM_ID_AES_256_GCM = 0x01
        const val DEFAULT_FLAGS = 0x05
        const val RESERVED = 0x00
        const val MAGIC_BYTES = 4
        const val PROFILE_HINT_BYTES = 8
        const val NONCE_BYTES = 12
        const val TAG_BYTES = 16
        const val HEADER_BYTES = 28
        private const val METADATA_LENGTH_BYTES = 4
    }

    fun encrypt(
        type: MediaCapsuleType,
        metadata: MediaCapsuleMetadata,
        mediaBytes: ByteArray,
        profileKey: ByteArray,
        profileHint: ByteArray,
        nonceOverride: ByteArray? = null,
    ): MediaCapsuleEncryptedFile {
        require(mediaBytes.isNotEmpty()) { "mediaBytes must not be empty" }
        require(profileKey.size == 32) { "profileKey must be 32 bytes" }
        require(profileHint.size == PROFILE_HINT_BYTES) { "profileHint must be 8 bytes" }

        val nonce = nonceOverride ?: ByteArray(NONCE_BYTES).also(secureRandom::nextBytes)
        require(nonce.size == NONCE_BYTES) { "nonce must be 12 bytes" }

        val metadataBytes = metadata.toJsonBytes()
        val plaintext = ByteBuffer.allocate(METADATA_LENGTH_BYTES + metadataBytes.size + mediaBytes.size)
            .putInt(metadataBytes.size)
            .put(metadataBytes)
            .put(mediaBytes)
            .array()

        val header = ByteBuffer.allocate(HEADER_BYTES)
            .put(type.magicBytes())
            .put(VERSION.toByte())
            .put(ALGORITHM_ID_AES_256_GCM.toByte())
            .put(DEFAULT_FLAGS.toByte())
            .put(RESERVED.toByte())
            .put(profileHint)
            .put(nonce)
            .array()

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(TAG_BYTES * 8, nonce)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(profileKey, "AES"), spec)
        cipher.updateAAD(header)
        val encrypted = cipher.doFinal(plaintext)

        val ciphertext = encrypted.copyOfRange(0, encrypted.size - TAG_BYTES)
        val tag = encrypted.copyOfRange(encrypted.size - TAG_BYTES, encrypted.size)
        val containerBytes = header + ciphertext + tag

        return MediaCapsuleEncryptedFile(
            type = type,
            version = VERSION,
            algorithmId = ALGORITHM_ID_AES_256_GCM,
            flags = DEFAULT_FLAGS,
            reserved = RESERVED,
            profileHint = profileHint,
            nonce = nonce,
            ciphertext = ciphertext,
            tag = tag,
            containerBytes = containerBytes,
        )
    }

    fun decrypt(containerBytes: ByteArray, candidateProfileKeys: List<ByteArray>): MediaCapsuleDecoded {
        require(containerBytes.size >= HEADER_BYTES + TAG_BYTES) { "Container too short" }

        val header = containerBytes.copyOfRange(0, HEADER_BYTES)
        val type = MediaCapsuleType.fromMagic(header.copyOfRange(0, MAGIC_BYTES))
        val version = header[MAGIC_BYTES].toInt() and 0xFF
        val algorithmId = header[MAGIC_BYTES + 1].toInt() and 0xFF
        require(version == VERSION) { "Unsupported media capsule version: $version" }
        require(algorithmId == ALGORITHM_ID_AES_256_GCM) {
            "Unsupported media capsule algorithm: $algorithmId"
        }

        val profileHintStart = MAGIC_BYTES + 4
        val nonceStart = profileHintStart + PROFILE_HINT_BYTES
        val profileHint = header.copyOfRange(profileHintStart, nonceStart)
        val nonce = header.copyOfRange(nonceStart, nonceStart + NONCE_BYTES)
        val cipherBytes = containerBytes.copyOfRange(HEADER_BYTES, containerBytes.size)

        candidateProfileKeys.forEach { key ->
            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val spec = GCMParameterSpec(TAG_BYTES * 8, nonce)
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), spec)
                cipher.updateAAD(header)
                val plaintext = cipher.doFinal(cipherBytes)
                return decodePlaintext(type, profileHint, plaintext)
            } catch (_: Exception) {
                // Try next candidate.
            }
        }

        throw IllegalArgumentException("Wrong key or invalid media capsule")
    }

    fun detectType(containerBytes: ByteArray): MediaCapsuleType {
        require(containerBytes.size >= MAGIC_BYTES) { "Container too short" }
        return MediaCapsuleType.fromMagic(containerBytes.copyOfRange(0, MAGIC_BYTES))
    }

    fun extractProfileHint(containerBytes: ByteArray): ByteArray {
        require(containerBytes.size >= HEADER_BYTES) { "Container too short" }
        val start = MAGIC_BYTES + 4
        return containerBytes.copyOfRange(start, start + PROFILE_HINT_BYTES)
    }

    private fun decodePlaintext(
        type: MediaCapsuleType,
        profileHint: ByteArray,
        plaintext: ByteArray,
    ): MediaCapsuleDecoded {
        require(plaintext.size >= METADATA_LENGTH_BYTES) { "Plaintext payload too short" }
        val buffer = ByteBuffer.wrap(plaintext)
        val metadataLength = buffer.int
        require(metadataLength > 0) { "Metadata length must be positive" }
        require(plaintext.size >= METADATA_LENGTH_BYTES + metadataLength) { "Metadata length out of bounds" }

        val metadataBytes = ByteArray(metadataLength)
        buffer.get(metadataBytes)
        val mediaBytes = ByteArray(buffer.remaining())
        buffer.get(mediaBytes)

        return MediaCapsuleDecoded(
            type = type,
            metadata = MediaCapsuleMetadata.fromJsonBytes(metadataBytes),
            profileHint = profileHint,
            mediaBytes = mediaBytes,
        )
    }
}
