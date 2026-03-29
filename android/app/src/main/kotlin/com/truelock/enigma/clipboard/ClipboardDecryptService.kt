package com.truelock.enigma.clipboard

import android.content.ClipboardManager
import android.content.Context
import com.truelock.enigma.R
import com.truelock.enigma.crypto.Tl1MessageCodec
import com.truelock.enigma.profiles.KeyProfileStatus
import com.truelock.enigma.profiles.ProfileSelectionPolicy
import com.truelock.enigma.storage.SecureProfileStore

class ClipboardDecryptService(
    private val context: Context,
    private val clipboardManager: ClipboardManager,
    private val secureProfileStore: SecureProfileStore,
    private val codec: Tl1MessageCodec,
) {
    fun decryptPrimaryClip(): ClipboardDecryptResult {
        if (!clipboardManager.hasPrimaryClip()) {
            return ClipboardDecryptResult.ClipboardEmpty
        }

        val clip = clipboardManager.primaryClip ?: return ClipboardDecryptResult.ClipboardEmpty
        if (clip.itemCount == 0) return ClipboardDecryptResult.ClipboardEmpty

        val text = clip.getItemAt(0).coerceToText(context)?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return ClipboardDecryptResult.ClipboardEmpty
        if (!text.startsWith(Tl1MessageCodec.PREFIX)) {
            return ClipboardDecryptResult.MessageNotRecognized
        }

        val hint = try {
            codec.extractProfileHint(text)
        } catch (_: Exception) {
            return ClipboardDecryptResult.MessageNotRecognized
        }

        val candidates = ProfileSelectionPolicy.shortlistByProfileHint(
            profiles = secureProfileStore.listProfiles()
                .filter { it.status != KeyProfileStatus.ARCHIVED },
            profileHint = hint,
        )

        if (candidates.isEmpty()) return ClipboardDecryptResult.WrongKeyOrInvalidMessage

        val candidateKeys = candidates.map { secureProfileStore.loadProfileKey(it) }
        return try {
            val plaintext = codec.decrypt(text, candidateKeys)
            val matchedTitle = candidates.firstOrNull()?.title ?: context.getString(R.string.clipboard_unknown_profile)
            ClipboardDecryptResult.Success(plaintext = plaintext, profileTitle = matchedTitle)
        } catch (_: Exception) {
            ClipboardDecryptResult.WrongKeyOrInvalidMessage
        }
    }

    fun clearPrimaryClip() {
        val clip = android.content.ClipData.newPlainText("", "")
        clipboardManager.setPrimaryClip(clip)
    }
}
