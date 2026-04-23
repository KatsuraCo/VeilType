package com.truelock.enigma.profiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom
import java.time.Instant

class KeyProfileFactoryTest {
    @Test
    fun create_buildsExpectedProfileShape() {
        val factory = KeyProfileFactory(FixedRandom())
        val now = Instant.parse("2026-03-27T12:00:00Z")

        val result = factory.createFromVisualSequence(
            title = "Yasha TG",
            cardIds = listOf(3, 17, 8, 24, 11, 29, 34, 6),
            appPackage = "org.telegram.messenger",
            peerHint = "Yasha",
            now = now,
            rotationHours = 48,
        )

        assertEquals("Yasha TG", result.profile.title)
        assertEquals("03-17-08-24-11-29-34-06", result.canonicalSequence)
        assertEquals(SecretSequenceKind.VISUAL_SEQUENCE, result.profile.secretSequenceKind)
        assertEquals(KeyProfileStatus.ACTIVE, result.profile.status)
        assertEquals(now, result.profile.createdAt)
        assertEquals(16, result.profile.profileSalt.size)
        assertEquals(32, result.profile.profileHint.size + 24)
        assertNotNull(result.profile.id)
        assertTrue(result.profile.expiresAt.isAfter(now))
    }

    @Test
    fun createFromEmojiSequence_setsEmojiKind() {
        val factory = KeyProfileFactory(FixedRandom())
        val result = factory.createFromEmojiSequence(
            title = "Emoji TG",
            emojis = listOf(
                "\uD83D\uDD12",
                "\uD83C\uDF19",
                "\uD83E\uDD8A",
                "\u26A1",
                "\uD83E\uDDE0",
                "\uD83D\uDD25",
                "\uD83E\uDDE9",
                "\uD83C\uDF0A",
            ),
            appPackage = "org.telegram.messenger",
            peerHint = "Emoji",
            now = Instant.parse("2026-03-27T12:00:00Z"),
        )

        assertEquals("1f512-1f319-1f98a-26a1-1f9e0-1f525-1f9e9-1f30a", result.canonicalSequence)
        assertEquals(SecretSequenceKind.EMOJI_SEQUENCE, result.profile.secretSequenceKind)
    }

    private class FixedRandom : SecureRandom() {
        override fun nextBytes(bytes: ByteArray) {
            bytes.indices.forEach { index -> bytes[index] = index.toByte() }
        }
    }
}
