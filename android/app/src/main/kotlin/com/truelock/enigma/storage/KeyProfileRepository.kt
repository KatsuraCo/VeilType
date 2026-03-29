package com.truelock.enigma.storage

import com.truelock.enigma.profiles.KeyProfile

interface KeyProfileRepository {
    fun listProfiles(): List<KeyProfile>
    fun saveProfile(profile: KeyProfile)
    fun saveProfiles(profiles: List<KeyProfile>)
    fun findById(id: String): KeyProfile?
    fun deleteById(id: String)
    fun clearAll()
}
