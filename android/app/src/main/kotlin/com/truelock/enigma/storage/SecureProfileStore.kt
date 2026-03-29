package com.truelock.enigma.storage

import com.truelock.enigma.profiles.KeyProfile
import com.truelock.enigma.profiles.ProfileLifecycleService

class SecureProfileStore(
    private val repository: KeyProfileRepository,
    private val keyVault: ProfileKeyVault,
) {
    fun listProfiles(): List<KeyProfile> {
        val original = repository.listProfiles()
        val recomputed = original.map { ProfileLifecycleService.recomputeStatus(it) }
        val changed = original.zip(recomputed).any { (before, after) -> before.status != after.status }
        if (changed) {
            repository.saveProfiles(recomputed)
        }
        return recomputed
    }

    fun saveProfile(profile: KeyProfile, rawProfileKey: ByteArray? = null) {
        val effective = if (rawProfileKey != null) {
            profile.copy(wrappedProfileKey = keyVault.wrap(rawProfileKey))
        } else {
            profile
        }
        repository.saveProfile(effective)
    }

    fun loadProfileKey(profile: KeyProfile): ByteArray =
        keyVault.unwrap(profile.wrappedProfileKey)

    fun findProfile(id: String): KeyProfile? =
        listProfiles().firstOrNull { it.id == id }

    fun touchProfile(id: String) {
        val profile = findProfile(id) ?: return
        saveProfile(ProfileLifecycleService.touch(profile))
    }

    fun deleteProfile(id: String) {
        repository.deleteById(id)
    }

    fun clearAll() {
        repository.clearAll()
    }
}
