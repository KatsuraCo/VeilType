package com.truelock.enigma.crypto

import java.util.Base64

object Base64Url {
    fun encodeNoPadding(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    fun decode(input: String): ByteArray =
        Base64.getUrlDecoder().decode(input)
}
