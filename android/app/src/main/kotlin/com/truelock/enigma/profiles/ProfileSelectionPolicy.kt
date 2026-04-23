package com.truelock.enigma.profiles

object ProfileSelectionPolicy {
    fun selectDefault(profiles: List<KeyProfile>): KeyProfile? {
        return profiles
            .asSequence()
            .filter { it.status == KeyProfileStatus.ACTIVE || it.status == KeyProfileStatus.EXPIRING }
            .sortedWith(
                compareByDescending<KeyProfile> { it.lastUsedAt ?: it.createdAt }
                    .thenBy { it.title.lowercase() }
            )
            .toList()
            .firstOrNull()
    }

    fun shortlistByProfileHint(
        profiles: List<KeyProfile>,
        profileHint: ByteArray,
    ): List<KeyProfile> {
        return profiles.filter { it.profileHint.contentEquals(profileHint) }
            .sortedWith(
                compareBy<KeyProfile> {
                    when (it.status) {
                        KeyProfileStatus.ACTIVE -> 0
                        KeyProfileStatus.EXPIRING -> 1
                        KeyProfileStatus.EXPIRED -> 2
                        KeyProfileStatus.ARCHIVED -> 3
                    }
                }.thenByDescending { it.lastUsedAt ?: it.createdAt }
            )
    }
}
