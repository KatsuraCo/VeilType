package com.truelock.enigma.media

import com.truelock.enigma.crypto.hex
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.SecureRandom
import java.time.Instant

class MediaCapsuleFileCodecTest {
    private val codec = MediaCapsuleFileCodec(SecureRandom())
    private val key = hex("4f3dbdf40fdf38b4131990efc6d319ebb80f5c32faffe8df4e97a02cc607b453")
    private val hint = hex("1e69f31d92861b44")

    @Test
    fun audioCapsule_roundTripsEncryptedBytesAndMetadata() {
        val mediaBytes = byteArrayOf(1, 3, 5, 7, 9, 11)
        val metadata = MediaCapsuleMetadata(
            mimeType = "audio/aac",
            durationMs = 4_200,
            createdAt = Instant.parse("2026-03-29T10:15:30Z"),
            originalFileName = "voice_note.m4a",
        )

        val encrypted = codec.encrypt(
            type = MediaCapsuleType.AUDIO,
            metadata = metadata,
            mediaBytes = mediaBytes,
            profileKey = key,
            profileHint = hint,
            nonceOverride = hex("00112233445566778899aabb"),
        )
        val decoded = codec.decrypt(encrypted.containerBytes, listOf(key))

        assertEquals(MediaCapsuleType.AUDIO, decoded.type)
        assertEquals(metadata, decoded.metadata)
        assertArrayEquals(hint, decoded.profileHint)
        assertArrayEquals(mediaBytes, decoded.mediaBytes)
    }

    @Test
    fun videoCapsule_roundTripsEncryptedBytesAndDimensions() {
        val mediaBytes = ByteArray(32) { index -> (index * 3).toByte() }
        val metadata = MediaCapsuleMetadata(
            mimeType = "video/mp4",
            durationMs = 8_800,
            createdAt = Instant.parse("2026-03-29T10:20:00Z"),
            width = 480,
            height = 480,
            originalFileName = "video_circle.mp4",
        )

        val encrypted = codec.encrypt(
            type = MediaCapsuleType.VIDEO,
            metadata = metadata,
            mediaBytes = mediaBytes,
            profileKey = key,
            profileHint = hint,
            nonceOverride = hex("aabbccddeeff001122334455"),
        )
        val decoded = codec.decrypt(encrypted.containerBytes, listOf(key))

        assertEquals(MediaCapsuleType.VIDEO, decoded.type)
        assertEquals(metadata, decoded.metadata)
        assertArrayEquals(mediaBytes, decoded.mediaBytes)
    }

    @Test
    fun detectTypeAndExtractProfileHint_returnExpectedValues() {
        val encrypted = codec.encrypt(
            type = MediaCapsuleType.AUDIO,
            metadata = MediaCapsuleMetadata(mimeType = "audio/ogg", durationMs = 900),
            mediaBytes = byteArrayOf(8, 6, 4, 2),
            profileKey = key,
            profileHint = hint,
            nonceOverride = hex("102030405060708090a0b0c0"),
        )

        assertEquals(MediaCapsuleType.AUDIO, codec.detectType(encrypted.containerBytes))
        assertArrayEquals(hint, codec.extractProfileHint(encrypted.containerBytes))
    }

    @Test
    fun decrypt_rejectsWrongKey() {
        val encrypted = codec.encrypt(
            type = MediaCapsuleType.AUDIO,
            metadata = MediaCapsuleMetadata(mimeType = "audio/ogg", durationMs = 900),
            mediaBytes = byteArrayOf(8, 6, 4, 2),
            profileKey = key,
            profileHint = hint,
        )

        val wrongKey = hex("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
        assertThrows(IllegalArgumentException::class.java) {
            codec.decrypt(encrypted.containerBytes, listOf(wrongKey))
        }
    }
}
