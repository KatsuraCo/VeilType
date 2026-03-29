package com.truelock.enigma.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.SecureRandom

class Tl1MessageCodecTest {
    private val codec = Tl1MessageCodec(SecureRandom())

    @Test
    fun encrypt_matchesKnownVector_ru_short() {
        val key = hex("4f3dbdf40fdf38b4131990efc6d319ebb80f5c32faffe8df4e97a02cc607b453")
        val hint = hex("1e69f31d92861b44")
        val nonce = hex("00112233445566778899aabb")

        val result = codec.encrypt(
            plaintext = "Завтра в 18:00",
            profileKey = key,
            profileHint = hint,
            nonceOverride = nonce,
        )

        assertEquals(
            "TL1:AQEDAB5p8x2ShhtEABEiM0RVZneImaq7nGE56D3d16NAiZU9cf4ns3NtX2KguJYYFeO7kb_22-ZJEp2iBg",
            result.encodedMessage,
        )
    }

    @Test
    fun decrypt_recoversKnownVector_ru_short() {
        val key = hex("4f3dbdf40fdf38b4131990efc6d319ebb80f5c32faffe8df4e97a02cc607b453")
        val plaintext = codec.decrypt(
            encodedMessage = "TL1:AQEDAB5p8x2ShhtEABEiM0RVZneImaq7nGE56D3d16NAiZU9cf4ns3NtX2KguJYYFeO7kb_22-ZJEp2iBg",
            candidateProfileKeys = listOf(key),
        )
        assertEquals("Завтра в 18:00", plaintext)
    }

    @Test
    fun extractProfileHint_returnsExpectedBytes() {
        val hint = codec.extractProfileHint(
            "TL1:AQEDAB5p8x2ShhtEABEiM0RVZneImaq7nGE56D3d16NAiZU9cf4ns3NtX2KguJYYFeO7kb_22-ZJEp2iBg",
        )
        assertArrayEquals(hex("1e69f31d92861b44"), hint)
    }
}
