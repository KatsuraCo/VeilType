package com.truelock.enigma.sharing

import android.content.Context
import com.truelock.enigma.R

object CapsuleShareText {
    fun build(context: Context): String? =
        when (ShareInvitePreferences(context.applicationContext).getMode()) {
            ShareInviteMode.VIRAL -> buildString {
                append(context.getString(R.string.keyboard_viral_invite_locked_line))
                append('\n')
                append(
                    context.getString(
                        R.string.keyboard_viral_invite_install_line,
                        context.getString(R.string.keyboard_viral_invite_url),
                    ),
                )
            }

            ShareInviteMode.BALANCED -> context.getString(
                R.string.keyboard_balanced_invite_line,
                context.getString(R.string.keyboard_viral_invite_url),
            )

            ShareInviteMode.MINIMAL -> null
        }
}
