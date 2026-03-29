package com.truelock.enigma.exchange

import com.truelock.enigma.profiles.KeyProfile

data class HandshakePreview(
    val remoteBundle: ContactBundle,
    val profile: KeyProfile,
    val profileKey: ByteArray,
    val fingerprint: String,
)
