package com.truelock.enigma.profiles

import java.time.Duration
import java.time.Instant

object ProfileLifecycleService {
    fun touch(profile: KeyProfile, now: Instant = Instant.now()): KeyProfile =
        profile.copy(lastUsedAt = now)

    fun renew(profile: KeyProfile, now: Instant = Instant.now()): KeyProfile {
        val nextVersion = profile.profileVersion + 1
        return profile.copy(
            profileVersion = nextVersion,
            createdAt = now,
            expiresAt = now.plus(Duration.ofHours(profile.rotationPeriodHours.toLong())),
            status = KeyProfileStatus.ACTIVE,
            lastUsedAt = null,
        )
    }

    fun archive(profile: KeyProfile): KeyProfile =
        profile.copy(status = KeyProfileStatus.ARCHIVED)

    fun recomputeStatus(
        profile: KeyProfile,
        now: Instant = Instant.now(),
        warningHours: Long = 6,
    ): KeyProfile {
        val resolved = ProfileStatusResolver.resolve(
            expiresAt = profile.expiresAt,
            archived = profile.status == KeyProfileStatus.ARCHIVED,
            now = now,
            warningHours = warningHours,
        )
        return profile.copy(status = resolved)
    }
}
