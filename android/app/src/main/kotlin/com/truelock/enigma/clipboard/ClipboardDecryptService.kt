package com.truelock.enigma.clipboard

import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import com.truelock.enigma.R
import com.truelock.enigma.crypto.Tl1MessageCodec
import com.truelock.enigma.crypto.Tl1ShareEnvelope
import com.truelock.enigma.profiles.KeyProfileStatus
import com.truelock.enigma.profiles.ProfileSelectionPolicy
import com.truelock.enigma.security.DecryptUsageStore
import com.truelock.enigma.storage.SecureProfileStore

class ClipboardDecryptService(
    private val context: Context,
    private val clipboardManager: ClipboardManager,
    private val secureProfileStore: SecureProfileStore,
    private val codec: Tl1MessageCodec,
) {
    private val usageStore = DecryptUsageStore(context)

    fun decryptPrimaryClip(): ClipboardDecryptResult {
        if (!clipboardManager.hasPrimaryClip()) {
            return ClipboardDecryptResult.ClipboardEmpty
        }

        val clip = clipboardManager.primaryClip ?: return ClipboardDecryptResult.ClipboardEmpty
        if (clip.itemCount == 0) return ClipboardDecryptResult.ClipboardEmpty

        val rawText = clip.getItemAt(0).coerceToText(context)?.toString()?.trim().orEmpty()
        if (rawText.isEmpty()) return ClipboardDecryptResult.ClipboardEmpty
        val text = Tl1ShareEnvelope.extractTl1Message(rawText)
            ?: rawText.takeIf { it.startsWith(Tl1MessageCodec.PREFIX) }
            ?: return ClipboardDecryptResult.MessageNotRecognized

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

        val fingerprint = usageStore.messageFingerprint(text)
        val preferredCandidate = ProfileSelectionPolicy.selectDefault(candidates) ?: candidates.first()
        if (preferredCandidate.requireBiometricForDecrypt) {
            return ClipboardDecryptResult.RequiresBiometric(
                encodedMessage = text,
                profileId = preferredCandidate.id,
                profileTitle = preferredCandidate.title,
            )
        }

        candidates.forEach { candidate ->
            if (candidate.requireBiometricForDecrypt) {
                return@forEach
            }
            if (candidate.oneTimeRead && usageStore.isConsumed(candidate.id, fingerprint)) {
                return ClipboardDecryptResult.AlreadyConsumed(candidate.title)
            }
            val plaintext = runCatching {
                codec.decrypt(text, listOf(secureProfileStore.loadProfileKey(candidate)))
            }.getOrNull() ?: return@forEach
            if (candidate.oneTimeRead) {
                usageStore.markConsumed(candidate.id, fingerprint)
            }
            return ClipboardDecryptResult.Success(
                plaintext = plaintext,
                profileTitle = candidate.title.ifBlank { context.getString(R.string.clipboard_unknown_profile) },
            )
        }

        return ClipboardDecryptResult.WrongKeyOrInvalidMessage
    }

    fun decryptWithProfile(encodedMessage: String, profileId: String): ClipboardDecryptResult {
        val profile = secureProfileStore.findProfile(profileId) ?: return ClipboardDecryptResult.WrongKeyOrInvalidMessage
        val fingerprint = usageStore.messageFingerprint(encodedMessage)
        if (profile.oneTimeRead && usageStore.isConsumed(profile.id, fingerprint)) {
            return ClipboardDecryptResult.AlreadyConsumed(profile.title)
        }
        val plaintext = runCatching {
            codec.decrypt(encodedMessage, listOf(secureProfileStore.loadProfileKey(profile)))
        }.getOrNull() ?: return ClipboardDecryptResult.WrongKeyOrInvalidMessage
        if (profile.oneTimeRead) {
            usageStore.markConsumed(profile.id, fingerprint)
        }
        return ClipboardDecryptResult.Success(plaintext = plaintext, profileTitle = profile.title)
    }

    fun clearPrimaryClip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            clipboardManager.clearPrimaryClip()
        } else {
            val clip = android.content.ClipData.newPlainText("", "")
            clipboardManager.setPrimaryClip(clip)
        }
    }
}
