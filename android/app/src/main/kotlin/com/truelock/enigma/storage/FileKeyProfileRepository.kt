package com.truelock.enigma.storage

import android.content.Context
import com.truelock.enigma.profiles.KeyProfile
import com.truelock.enigma.profiles.KeyProfileJsonCodec
import org.json.JSONArray
import java.io.File

class FileKeyProfileRepository(
    context: Context,
) : KeyProfileRepository {
    private val file: File = File(context.noBackupFilesDir, "enigma_profiles.json")

    override fun listProfiles(): List<KeyProfile> {
        if (!file.exists()) return emptyList()
        val raw = file.readText(Charsets.UTF_8).trim()
        if (raw.isEmpty()) return emptyList()

        val array = JSONArray(raw)
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                add(KeyProfileJsonCodec.decode(array.getString(index)))
            }
        }
    }

    override fun saveProfile(profile: KeyProfile) {
        val current = listProfiles().toMutableList()
        val existingIndex = current.indexOfFirst { it.id == profile.id }
        if (existingIndex >= 0) {
            current[existingIndex] = profile
        } else {
            current += profile
        }
        saveProfiles(current)
    }

    override fun saveProfiles(profiles: List<KeyProfile>) {
        file.parentFile?.mkdirs()
        val array = JSONArray()
        profiles.forEach { profile ->
            array.put(KeyProfileJsonCodec.encode(profile))
        }
        file.writeText(array.toString(), Charsets.UTF_8)
    }

    override fun findById(id: String): KeyProfile? =
        listProfiles().firstOrNull { it.id == id }

    override fun deleteById(id: String) {
        val updated = listProfiles().filterNot { it.id == id }
        saveProfiles(updated)
    }

    override fun clearAll() {
        if (file.exists()) {
            file.delete()
        }
    }
}
