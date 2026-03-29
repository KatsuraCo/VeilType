package com.truelock.enigma.profiles

import java.time.Duration
import java.time.Instant

object ProfileStatusResolver {
    private const val DEFAULT_WARNING_HOURS = 6L

    fun resolve(
        expiresAt: Instant,
        archived: Boolean = false,
        now: Instant = Instant.now(),
        warningHours: Long = DEFAULT_WARNING_HOURS,
    ): KeyProfileStatus {
        if (archived) return KeyProfileStatus.ARCHIVED
        if (!now.isBefore(expiresAt)) return KeyProfileStatus.EXPIRED

        val warningThreshold = expiresAt.minus(Duration.ofHours(warningHours))
        return if (!now.isBefore(warningThreshold)) {
            KeyProfileStatus.EXPIRING
        } else {
            KeyProfileStatus.ACTIVE
        }
    }
}
