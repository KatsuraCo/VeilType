package com.truelock.enigma.profiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.Instant

class ProfileLifecycleServiceTest {
    private val baseProfile = KeyProfile(
        id = "p1",
        title = "Yasha TG",
        appPackage = "org.telegram.messenger",
        peerHint = "Yasha",
        secretSequenceKind = SecretSequenceKind.EMOJI_SEQUENCE,
        profileVersion = 1,
        profileSalt = ByteArray(16) { 1 },
        wrappedProfileKey = ByteArray(32) { 2 },
        profileHint = ByteArray(8) { 3 },
        createdAt = Instant.parse("2026-03-27T12:00:00Z"),
        expiresAt = Instant.parse("2026-03-29T12:00:00Z"),
        lastUsedAt = null,
        status = KeyProfileStatus.ACTIVE,
        allowDecryptAfterExpiry = true,
        rotationPeriodHours = 48,
    )

    @Test
    fun recomputeStatus_marksExpiringNearDeadline() {
        val updated = ProfileLifecycleService.recomputeStatus(
            profile = baseProfile,
            now = Instant.parse("2026-03-29T07:00:00Z"),
            warningHours = 6,
        )
        assertEquals(KeyProfileStatus.EXPIRING, updated.status)
    }

    @Test
    fun renew_bumpsVersionAndExtendsExpiry() {
        val renewed = ProfileLifecycleService.renew(
            profile = baseProfile,
            now = Instant.parse("2026-03-30T12:00:00Z"),
        )
        assertEquals(2, renewed.profileVersion)
        assertEquals(KeyProfileStatus.ACTIVE, renewed.status)
        assertNotEquals(baseProfile.expiresAt, renewed.expiresAt)
    }

    @Test
    fun archive_setsArchivedStatus() {
        val archived = ProfileLifecycleService.archive(baseProfile)
        assertEquals(KeyProfileStatus.ARCHIVED, archived.status)
    }
}
