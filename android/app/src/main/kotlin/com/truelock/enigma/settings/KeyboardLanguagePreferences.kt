package com.truelock.enigma.settings

import android.content.Context

class KeyboardLanguagePreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getEnabledLanguageTags(): Set<String> {
        val stored = prefs.getStringSet(KEY_ENABLED_KEYBOARD_LANGUAGES, null)
            ?.filter { it in SUPPORTED_LANGUAGE_TAGS }
            ?.toSet()
            .orEmpty()
        return if (stored.isEmpty()) DEFAULT_ENABLED_LANGUAGE_TAGS else stored
    }

    fun setEnabledLanguageTags(tags: Set<String>) {
        val normalized = tags.filter { it in SUPPORTED_LANGUAGE_TAGS }.toSet()
            .ifEmpty { DEFAULT_ENABLED_LANGUAGE_TAGS }
        prefs.edit()
            .putStringSet(KEY_ENABLED_KEYBOARD_LANGUAGES, normalized)
            .apply()
    }

    companion object {
        val SUPPORTED_LANGUAGE_TAGS = listOf("en", "ru", "de", "es", "fr", "it", "pt", "tr")
        val DEFAULT_ENABLED_LANGUAGE_TAGS: Set<String> = setOf("en")

        private const val PREFS_NAME = "veiltype_preferences"
        private const val KEY_ENABLED_KEYBOARD_LANGUAGES = "enabled_keyboard_languages"
    }
}
