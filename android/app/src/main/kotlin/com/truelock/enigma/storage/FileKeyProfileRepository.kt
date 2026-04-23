package com.truelock.enigma.storage

import android.content.Context
import com.truelock.enigma.profiles.KeyProfile
import com.truelock.enigma.profiles.KeyProfileJsonCodec
import org.json.JSONArray
import java.io.File

class FileKeyProfileRepository(
    context: Context,
    private val vault: ProfileKeyVault = ProfileKeyVault(),
) : KeyProfileRepository {
    private val file: File = File(context.noBackupFilesDir, "enigma_profiles.json")

    override fun listProfiles(): List<KeyProfile> {
        if (!file.exists()) return emptyList()
        val raw = runCatching {
            val rawBytes = file.readBytes()
            if (rawBytes.isEmpty()) return emptyList()
            readStoredPayload(rawBytes).trim()
        }.getOrElse {
            quarantineCorruptStore()
            return emptyList()
        }
        if (raw.isEmpty()) return emptyList()

        val array = runCatching { JSONArray(raw) }
            .getOrElse {
                quarantineCorruptStore()
                return emptyList()
            }

        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                val entry = runCatching {
                    when (val item = array.get(index)) {
                        is String -> item
                        else -> item.toString()
                    }
                }.getOrNull() ?: continue

                val profile = runCatching { KeyProfileJsonCodec.decode(entry) }.getOrNull()
                if (profile != null) {
                    add(profile)
                }
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
        val payload = array.toString().toByteArray(Charsets.UTF_8)
        file.writeBytes(vault.wrap(payload))
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

    private fun readStoredPayload(rawBytes: ByteArray): String {
        return runCatching {
            String(vault.unwrap(rawBytes), Charsets.UTF_8)
        }.getOrElse {
            String(rawBytes, Charsets.UTF_8)
        }
    }

    private fun quarantineCorruptStore() {
        runCatching {
            if (!file.exists()) return
            val corruptFile = File(file.parentFile, "${file.name}.corrupt")
            if (corruptFile.exists()) {
                corruptFile.delete()
            }
            file.renameTo(corruptFile)
        }
    }
}
