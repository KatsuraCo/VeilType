package com.truelock.enigma.profiles

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Base64

class EmojiKeyBundleCodecTest {
    private val codec = EmojiKeyBundleCodec()

    @Test
    fun encodeDecode_roundTripsBundle() {
        val original = EmojiKeyBundle(
            title = "Demo",
            emojis = listOf(
                "\uD83D\uDD12",
                "\uD83D\uDEE1\uFE0F",
                "\u26A1",
                "\uD83C\uDF19",
                "\uD83E\uDDE0",
                "\uD83D\uDD25",
                "\uD83E\uDDE9",
                "\uD83C\uDF0A",
            ),
            profileSalt = ByteArray(16) { 7 },
            appPackage = "org.telegram.messenger",
            peerHint = "Alice",
        )

        val decoded = codec.decode(codec.encode(original))

        assertEquals(original.title, decoded.title)
        assertEquals(original.emojis, decoded.emojis)
        assertArrayEquals(original.profileSalt, decoded.profileSalt)
        assertEquals(original.appPackage, decoded.appPackage)
        assertEquals(original.peerHint, decoded.peerHint)
    }

    @Test
    fun decode_rejectsSaltWithWrongLength() {
        val payload = """
            {
              "version":1,
              "title":"Broken",
              "emojis":["\uD83D\uDD12","\uD83D\uDEE1\uFE0F","\u26A1","\uD83C\uDF19","\uD83E\uDDE0","\uD83D\uDD25","\uD83E\uDDE9","\uD83C\uDF0A"],
              "salt_b64":"${Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(8) { 1 })}"
            }
        """.trimIndent()

        val encoded = EmojiKeyBundleCodec.PREFIX + Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(payload.toByteArray(Charsets.UTF_8))

        assertThrows(IllegalArgumentException::class.java) {
            codec.decode(encoded)
        }
    }
}
