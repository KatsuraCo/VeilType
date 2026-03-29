package com.truelock.enigma.profiles

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class KeyProfileJsonCodecTest {
    @Test
    fun encodeDecode_roundTripsProfile() {
        val original = KeyProfile(
            id = "p1",
            title = "Yasha TG",
            appPackage = "org.telegram.messenger",
            peerHint = "Yasha",
            secretSequenceKind = SecretSequenceKind.EMOJI_SEQUENCE,
            profileVersion = 2,
            profileSalt = ByteArray(16) { 1 },
            wrappedProfileKey = ByteArray(32) { 2 },
            profileHint = ByteArray(8) { 3 },
            createdAt = Instant.parse("2026-03-27T12:00:00Z"),
            expiresAt = Instant.parse("2026-03-29T12:00:00Z"),
            lastUsedAt = Instant.parse("2026-03-27T12:10:00Z"),
            status = KeyProfileStatus.EXPIRING,
            allowDecryptAfterExpiry = true,
            rotationPeriodHours = 48,
        )

        val encoded = KeyProfileJsonCodec.encode(original)
        val decoded = KeyProfileJsonCodec.decode(encoded)

        assertEquals(original.id, decoded.id)
        assertEquals(original.title, decoded.title)
        assertEquals(original.appPackage, decoded.appPackage)
        assertEquals(original.peerHint, decoded.peerHint)
        assertEquals(original.secretSequenceKind, decoded.secretSequenceKind)
        assertEquals(original.profileVersion, decoded.profileVersion)
        assertEquals(original.createdAt, decoded.createdAt)
        assertEquals(original.expiresAt, decoded.expiresAt)
        assertEquals(original.lastUsedAt, decoded.lastUsedAt)
        assertEquals(original.status, decoded.status)
        assertArrayEquals(original.profileSalt, decoded.profileSalt)
        assertArrayEquals(original.wrappedProfileKey, decoded.wrappedProfileKey)
        assertArrayEquals(original.profileHint, decoded.profileHint)
    }
}
