package com.truelock.enigma.crypto

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.MessageDigest

object ProfileKeyDeriver {
    const val EMOJI_SEQUENCE_LENGTH = 8
    private const val ARGON2_MEMORY_KIB = 19_456
    private const val ARGON2_ITERATIONS = 2
    private const val ARGON2_PARALLELISM = 1
    private const val KEY_LENGTH_BYTES = 32

    fun canonicalVisualSequence(cardIds: List<Int>): String {
        require(cardIds.size == EMOJI_SEQUENCE_LENGTH) {
            "Exactly $EMOJI_SEQUENCE_LENGTH card ids are required"
        }
        return cardIds.joinToString("-") { it.toString().padStart(2, '0') }
    }

    fun canonicalEmojiSequence(emojis: List<String>): String {
        require(emojis.size == EMOJI_SEQUENCE_LENGTH) {
            "Exactly $EMOJI_SEQUENCE_LENGTH emoji tokens are required"
        }
        return emojis.joinToString("-") { token ->
            require(token.isNotBlank()) { "Emoji token must not be blank" }
            token.codePointsHex()
        }
    }

    fun deriveProfileKey(sequence: String, salt: ByteArray): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withSalt(salt)
            .withIterations(ARGON2_ITERATIONS)
            .withMemoryAsKB(ARGON2_MEMORY_KIB)
            .withParallelism(ARGON2_PARALLELISM)
            .build()

        val generator = Argon2BytesGenerator()
        generator.init(params)

        return ByteArray(KEY_LENGTH_BYTES).also { out ->
            generator.generateBytes(sequence.toCharArray(), out)
        }
    }

    fun deriveProfileHint(profileKey: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val material = "TLKH1".toByteArray(Charsets.UTF_8) + profileKey
        return digest.digest(material).copyOfRange(0, 8)
    }

    private fun String.codePointsHex(): String {
        val out = mutableListOf<String>()
        var index = 0
        while (index < length) {
            val codePoint = codePointAt(index)
            out += codePoint.toString(16)
            index += Character.charCount(codePoint)
        }
        return out.joinToString("+")
    }
}
