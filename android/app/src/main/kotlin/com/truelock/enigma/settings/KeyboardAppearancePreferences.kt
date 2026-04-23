package com.truelock.enigma.settings

import android.content.Context

class KeyboardAppearancePreferences(context: Context) {
    enum class ThemePreset {
        MIDNIGHT,
        OCEAN,
        GRAPHITE,
    }

    enum class KeyShapePreset {
        ROUNDED,
        FULL_SQUARE,
        SPACED_SQUARE,
        SPACED_ROUNDED,
    }

    enum class HeightPreset {
        AUTO,
        COMPACT,
        NORMAL,
        TALL,
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getThemePreset(): ThemePreset =
        runCatching {
            ThemePreset.valueOf(
                prefs.getString(KEY_THEME_PRESET, ThemePreset.MIDNIGHT.name) ?: ThemePreset.MIDNIGHT.name,
            )
        }.getOrDefault(ThemePreset.MIDNIGHT)

    fun setThemePreset(preset: ThemePreset) {
        prefs.edit().putString(KEY_THEME_PRESET, preset.name).apply()
    }

    fun getKeyShapePreset(): KeyShapePreset =
        runCatching {
            KeyShapePreset.valueOf(
                prefs.getString(KEY_KEY_SHAPE_PRESET, KeyShapePreset.ROUNDED.name) ?: KeyShapePreset.ROUNDED.name,
            )
        }.getOrDefault(KeyShapePreset.ROUNDED)

    fun setKeyShapePreset(preset: KeyShapePreset) {
        prefs.edit().putString(KEY_KEY_SHAPE_PRESET, preset.name).apply()
    }

    fun getHeightPreset(): HeightPreset =
        runCatching {
            HeightPreset.valueOf(
                prefs.getString(KEY_HEIGHT_PRESET, HeightPreset.AUTO.name) ?: HeightPreset.AUTO.name,
            )
        }.getOrDefault(HeightPreset.AUTO)

    fun setHeightPreset(preset: HeightPreset) {
        prefs.edit().putString(KEY_HEIGHT_PRESET, preset.name).apply()
    }

    companion object {
        private const val PREFS_NAME = "veiltype_preferences"
        private const val KEY_THEME_PRESET = "keyboard_theme_preset"
        private const val KEY_KEY_SHAPE_PRESET = "keyboard_key_shape_preset"
        private const val KEY_HEIGHT_PRESET = "keyboard_height_preset"
    }
}
