package com.truelock.enigma.crypto

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object Hkdf {
    fun deriveSha256(
        ikm: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        outputLength: Int,
    ): ByteArray {
        require(outputLength > 0) { "outputLength must be positive" }

        val prk = hmacSha256(salt, ikm)
        val out = ByteArray(outputLength)
        var previous = ByteArray(0)
        var offset = 0
        var counter = 1

        while (offset < outputLength) {
            val input = ByteArray(previous.size + info.size + 1)
            System.arraycopy(previous, 0, input, 0, previous.size)
            System.arraycopy(info, 0, input, previous.size, info.size)
            input[input.lastIndex] = counter.toByte()

            previous = hmacSha256(prk, input)
            val chunkSize = minOf(previous.size, outputLength - offset)
            System.arraycopy(previous, 0, out, offset, chunkSize)
            offset += chunkSize
            counter += 1
        }

        return out
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }
}
