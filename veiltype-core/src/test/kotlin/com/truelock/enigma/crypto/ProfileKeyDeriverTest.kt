package com.truelock.enigma.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileKeyDeriverTest {
    @Test
    fun canonicalSequence_formatsAsExpected() {
        val result = ProfileKeyDeriver.canonicalVisualSequence(listOf(3, 17, 8, 24, 11, 29, 34, 6))
        assertEquals("03-17-08-24-11-29-34-06", result)
    }

    @Test
    fun canonicalEmojiSequence_formatsAsExpected() {
        val result = ProfileKeyDeriver.canonicalEmojiSequence(
            listOf(
                "\uD83D\uDD12",
                "\uD83C\uDF19",
                "\uD83E\uDD8A",
                "\u26A1",
                "\uD83E\uDDE0",
                "\uD83D\uDD25",
                "\uD83E\uDDE9",
                "\uD83C\uDF0A",
            ),
        )
        assertEquals("1f512-1f319-1f98a-26a1-1f9e0-1f525-1f9e9-1f30a", result)
    }

    @Test
    fun deriveProfileKey_matchesKnownVector() {
        val salt = hex("3a8f94d9b2d12055e6f4b6c39018cf41")
        val key = ProfileKeyDeriver.deriveProfileKey("03-17-08-24-11-29-34-06", salt)
        assertEquals(
            "8364c32d7fe40df71870b085a48dfdc9f63e91993b2d3f5801643fc55e120c2b",
            key.toHex(),
        )
    }

    @Test
    fun deriveProfileHint_matchesKnownVector() {
        val key = hex("8364c32d7fe40df71870b085a48dfdc9f63e91993b2d3f5801643fc55e120c2b")
        val hint = ProfileKeyDeriver.deriveProfileHint(key)
        assertArrayEquals(hex("e77a323eded90196"), hint)
    }

    @Test
    fun deriveProfileKey_matchesKnownEmojiVector() {
        val salt = hex("11223344556677889900aabbccddeeff")
        val sequence = "1f512-1f319-1f98a-26a1-1f9e0-1f525-1f9e9-1f30a"
        val key = ProfileKeyDeriver.deriveProfileKey(sequence, salt)
        assertEquals(
            "e362dc2740a837bd1de11039a7a73c05881bb325e297b5dc9b9c65848b76edfb",
            key.toHex(),
        )
    }
}
