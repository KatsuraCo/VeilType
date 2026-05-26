package com.truelock.enigma.crypto

fun hex(value: String): ByteArray {
    require(value.length % 2 == 0) { "Hex string length must be even" }
    return value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

fun ByteArray.toHex(): String = joinToString("") { each -> "%02x".format(each) }
