package com.truelock.enigma.media

import android.content.Context
import java.io.File

class PendingCapsuleStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(type: MediaCapsuleType, file: File) {
        prefs.edit()
            .putString(KEY_TYPE, type.name)
            .putString(KEY_PATH, file.absolutePath)
            .apply()
    }

    fun peek(): PendingCapsule? {
        val typeName = prefs.getString(KEY_TYPE, null) ?: return null
        val path = prefs.getString(KEY_PATH, null) ?: return null
        val type = runCatching { MediaCapsuleType.valueOf(typeName) }.getOrNull() ?: return null
        val file = File(path)
        if (!file.exists()) {
            clear()
            return null
        }
        return PendingCapsule(type = type, file = file)
    }

    fun consume(): PendingCapsule? = peek()?.also { clear() }

    fun clear() {
        prefs.edit()
            .remove(KEY_TYPE)
            .remove(KEY_PATH)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "pending_capsule_store"
        private const val KEY_TYPE = "pending_type"
        private const val KEY_PATH = "pending_path"
    }
}

data class PendingCapsule(
    val type: MediaCapsuleType,
    val file: File,
)
