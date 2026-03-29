package com.truelock.enigma.profiles

object ProfileSelectionPolicy {
    fun selectDefaultForApp(
        profiles: List<KeyProfile>,
        appPackage: String?,
    ): KeyProfile? {
        val candidates = profiles
            .asSequence()
            .filter { it.status == KeyProfileStatus.ACTIVE || it.status == KeyProfileStatus.EXPIRING }
            .filter { appPackage == null || it.appPackage == null || it.appPackage == appPackage }
            .sortedWith(
                compareByDescending<KeyProfile> { it.appPackage == appPackage }
                    .thenByDescending { it.lastUsedAt ?: it.createdAt }
            )
            .toList()

        return candidates.firstOrNull()
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
