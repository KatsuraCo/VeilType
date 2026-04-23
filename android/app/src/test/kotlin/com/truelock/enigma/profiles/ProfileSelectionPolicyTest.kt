package com.truelock.enigma.profiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class ProfileSelectionPolicyTest {
    @Test
    fun selectDefault_prefersRecentUsableKey() {
        val profiles = listOf(
            profile("a", "org.telegram.messenger", KeyProfileStatus.ACTIVE, "2026-03-27T12:00:00Z"),
            profile("b", "com.whatsapp", KeyProfileStatus.ACTIVE, "2026-03-27T13:00:00Z"),
            profile("c", "org.telegram.messenger", KeyProfileStatus.EXPIRING, "2026-03-27T14:00:00Z"),
        )

        val selected = ProfileSelectionPolicy.selectDefault(profiles)

        assertEquals("c", selected?.id)
    }

    @Test
    fun shortlistByProfileHint_returnsOnlyMatchingProfiles() {
        val matchingHint = ByteArray(8) { 9 }
        val profiles = listOf(
            profile("a", null, KeyProfileStatus.ACTIVE, "2026-03-27T12:00:00Z", matchingHint),
            profile("b", null, KeyProfileStatus.EXPIRED, "2026-03-27T13:00:00Z", ByteArray(8) { 1 }),
        )

        val shortlisted = ProfileSelectionPolicy.shortlistByProfileHint(profiles, matchingHint)
        assertEquals(1, shortlisted.size)
        assertEquals("a", shortlisted.first().id)
    }

    @Test
    fun selectDefault_returnsNullWhenNoUsableKeys() {
        val profiles = listOf(
            profile("a", "org.telegram.messenger", KeyProfileStatus.ARCHIVED, "2026-03-27T12:00:00Z"),
            profile("b", "org.telegram.messenger", KeyProfileStatus.EXPIRED, "2026-03-27T13:00:00Z"),
        )

        val selected = ProfileSelectionPolicy.selectDefault(profiles)
        assertNull(selected)
    }

    private fun profile(
        id: String,
        appPackage: String?,
        status: KeyProfileStatus,
        lastUsedAt: String,
        hint: ByteArray = ByteArray(8) { 7 },
    ): KeyProfile = KeyProfile(
        id = id,
        title = id,
        appPackage = appPackage,
        peerHint = null,
        secretSequenceKind = SecretSequenceKind.EMOJI_SEQUENCE,
        profileVersion = 1,
        profileSalt = ByteArray(16) { 1 },
        wrappedProfileKey = ByteArray(32) { 2 },
        profileHint = hint,
        createdAt = Instant.parse("2026-03-27T10:00:00Z"),
        expiresAt = Instant.parse("2026-03-29T10:00:00Z"),
        lastUsedAt = Instant.parse(lastUsedAt),
        status = status,
        allowDecryptAfterExpiry = true,
        rotationPeriodHours = 48,
    )
}
