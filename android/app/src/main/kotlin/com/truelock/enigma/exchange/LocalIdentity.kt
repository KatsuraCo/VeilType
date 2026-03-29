package com.truelock.enigma.exchange

import java.time.Instant

data class LocalIdentity(
    val deviceId: String,
    val displayName: String,
    val createdAt: Instant,
    val publicKeyEncoded: ByteArray,
    val wrappedPrivateKeyEncoded: ByteArray,
)
