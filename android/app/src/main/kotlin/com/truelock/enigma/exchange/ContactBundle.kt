package com.truelock.enigma.exchange

import java.time.Instant

data class ContactBundle(
    val version: Int = 1,
    val app: String = APP_MARKER,
    val deviceId: String,
    val displayName: String,
    val createdAt: Instant,
    val publicKeyEncoded: ByteArray,
) {
    companion object {
        const val APP_MARKER = "enigma_keyboard"
    }
}
