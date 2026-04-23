package com.truelock.enigma.profiles

import java.time.Instant

enum class KeyProfileStatus {
    ACTIVE,
    EXPIRING,
    EXPIRED,
    ARCHIVED,
}

enum class SecretSequenceKind {
    EMOJI_SEQUENCE,
    VISUAL_SEQUENCE,
    CONTACT_HANDSHAKE,
}

data class KeyProfile(
    val id: String,
    val title: String,
    val appPackage: String? = null,
    val peerHint: String? = null,
    val secretSequenceDisplay: String? = null,
    val secretSequenceKind: SecretSequenceKind,
    val profileVersion: Int = 1,
    val profileSalt: ByteArray,
    val wrappedProfileKey: ByteArray,
    val profileHint: ByteArray,
    val createdAt: Instant,
    val expiresAt: Instant,
    val lastUsedAt: Instant? = null,
    val status: KeyProfileStatus,
    val allowDecryptAfterExpiry: Boolean = true,
    val rotationPeriodHours: Int = 48,
    val oneTimeRead: Boolean = false,
    val requireBiometricForDecrypt: Boolean = false,
    val exportAllowed: Boolean = true,
)
