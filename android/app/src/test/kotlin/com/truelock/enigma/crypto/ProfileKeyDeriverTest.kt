package com.truelock.enigma.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileKeyDeriverTest {
    @Test
    fun canonicalSequence_formatsAsExpected() {
        val result = ProfileKeyDeriver.canonicalVisualSequence(listOf(3, 17, 8, 24, 11))
        assertEquals("03-17-08-24-11", result)
    }

    @Test
    fun canonicalEmojiSequence_formatsAsExpected() {
        val result = ProfileKeyDeriver.canonicalEmojiSequence(
            listOf("🔒", "🌙", "🦊", "⚡", "🧠"),
        )
        assertEquals("1f512-1f319-1f98a-26a1-1f9e0", result)
    }

    @Test
    fun deriveProfileKey_matchesKnownVector() {
        val salt = hex("3a8f94d9b2d12055e6f4b6c39018cf41")
        val key = ProfileKeyDeriver.deriveProfileKey("03-17-08-24-11", salt)
        assertEquals(
            "4f3dbdf40fdf38b4131990efc6d319ebb80f5c32faffe8df4e97a02cc607b453",
            key.toHex(),
        )
    }

    @Test
    fun deriveProfileHint_matchesKnownVector() {
        val key = hex("4f3dbdf40fdf38b4131990efc6d319ebb80f5c32faffe8df4e97a02cc607b453")
        val hint = ProfileKeyDeriver.deriveProfileHint(key)
        assertArrayEquals(hex("1e69f31d92861b44"), hint)
    }

    @Test
    fun deriveProfileKey_matchesKnownEmojiVector() {
        val salt = hex("11223344556677889900aabbccddeeff")
        val sequence = "1f512-1f319-1f98a-26a1-1f9e0"
        val key = ProfileKeyDeriver.deriveProfileKey(sequence, salt)
        assertEquals(
            "e23b54a295c276c0c9ba3e1a7280c33e0b894c1fe0f326672aa9d5057ea19c98",
            key.toHex(),
        )
    }
}
