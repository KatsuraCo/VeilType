package com.truelock.enigma.sharing

import android.content.Context

class ShareInvitePreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getMode(): ShareInviteMode {
        val raw = prefs.getString(KEY_SHARE_INVITE_MODE, ShareInviteMode.VIRAL.name)
        return ShareInviteMode.entries.firstOrNull { it.name == raw } ?: ShareInviteMode.VIRAL
    }

    fun setMode(mode: ShareInviteMode) {
        prefs.edit().putString(KEY_SHARE_INVITE_MODE, mode.name).apply()
    }

    private companion object {
        const val PREFS_NAME = "veiltype_preferences"
        const val KEY_SHARE_INVITE_MODE = "share_invite_mode"
    }
}

