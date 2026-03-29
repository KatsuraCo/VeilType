package com.truelock.enigma.ui

import android.content.Context
import com.truelock.enigma.R
import com.truelock.enigma.profiles.KeyProfile
import com.truelock.enigma.profiles.KeyProfileStatus
import com.truelock.enigma.profiles.SecretSequenceKind

fun Context.localizedSecretKind(kind: SecretSequenceKind): String = when (kind) {
    SecretSequenceKind.EMOJI_SEQUENCE -> getString(R.string.profile_kind_emoji_sequence)
    SecretSequenceKind.VISUAL_SEQUENCE -> getString(R.string.profile_kind_visual_sequence)
    SecretSequenceKind.CONTACT_HANDSHAKE -> getString(R.string.profile_kind_contact_handshake)
}

fun Context.localizedProfileStatus(status: KeyProfileStatus): String = when (status) {
    KeyProfileStatus.ACTIVE -> getString(R.string.profile_status_active)
    KeyProfileStatus.EXPIRING -> getString(R.string.profile_status_expiring)
    KeyProfileStatus.EXPIRED -> getString(R.string.profile_status_expired)
    KeyProfileStatus.ARCHIVED -> getString(R.string.profile_status_archived)
}

fun Context.formatProfileListItem(profile: KeyProfile): String {
    val anyValue = getString(R.string.profile_value_any)
    return getString(
        R.string.profile_list_item_format,
        profile.title,
        profile.id,
        profile.appPackage ?: anyValue,
        profile.peerHint ?: anyValue,
        localizedSecretKind(profile.secretSequenceKind),
        localizedProfileStatus(profile.status),
        profile.expiresAt.toString(),
    )
}
