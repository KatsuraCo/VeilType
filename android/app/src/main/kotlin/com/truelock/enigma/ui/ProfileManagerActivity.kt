package com.truelock.enigma.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.truelock.enigma.R
import com.truelock.enigma.crypto.ProfileKeyDeriver
import com.truelock.enigma.databinding.ActivityProfileManagerBinding
import com.truelock.enigma.profiles.EmojiKeyBundle
import com.truelock.enigma.profiles.EmojiKeyBundleCodec
import com.truelock.enigma.profiles.KeyProfile
import com.truelock.enigma.profiles.KeyProfileFactory
import com.truelock.enigma.profiles.ProfileLifecycleService
import com.truelock.enigma.profiles.ProfileSelectionPolicy
import com.truelock.enigma.security.BiometricDecryptHelper
import com.truelock.enigma.storage.FileKeyProfileRepository
import com.truelock.enigma.storage.ProfileKeyVault
import com.truelock.enigma.storage.SecureProfileStore

class ProfileManagerActivity : AppCompatActivity() {
    private companion object {
        const val EXTRA_FOCUS_IMPORT = "focus_import"
        val EXPIRY_OPTIONS_HOURS = listOf(24, 48, 24 * 7, 24 * 30)
    }

    private lateinit var binding: ActivityProfileManagerBinding
    private lateinit var secureProfileStore: SecureProfileStore
    private lateinit var clipboardManager: ClipboardManager
    private lateinit var biometricHelper: BiometricDecryptHelper
    private var focusImportMode: Boolean = false
    private var hasResumedOnce: Boolean = false

    private val profileFactory = KeyProfileFactory()
    private val keyBundleCodec = EmojiKeyBundleCodec()
    private var renderedProfiles: List<KeyProfile> = emptyList()
    private var currentKeyBundle: EmojiKeyBundle? = null
    private var selectedRotationHours: Int = 48

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        secureProfileStore = SecureProfileStore(
            repository = FileKeyProfileRepository(applicationContext),
            keyVault = ProfileKeyVault(),
        )
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        biometricHelper = BiometricDecryptHelper(this)

        binding.generateKeyButton.setOnClickListener { generateRandomKey() }
        binding.editGeneratedKeyButton.setOnClickListener { showCurrentKeyEditDialog() }
        binding.copyKeyButton.setOnClickListener {
            copyKeyBundle()
        }
        binding.shareKeyButton.setOnClickListener {
            shareKeyBundle()
        }
        binding.saveGeneratedKeyButton.setOnClickListener {
            upsertProfileFromInputs(forceCreate = true)
        }
        binding.importKeyButton.setOnClickListener {
            importKeyBundle()
        }
        binding.saveImportedKeyButton.setOnClickListener {
            upsertProfileFromInputs()
        }
        binding.keyExpiryButton.setOnClickListener {
            showExpiryPicker()
        }
        binding.createProfileButton.setOnClickListener {
            upsertProfileFromInputs()
        }
        binding.resetFormButton.setOnClickListener {
            resetForm(getString(R.string.profile_status_form_reset))
        }
        binding.clearProfilesButton.setOnClickListener {
            secureProfileStore.clearAll()
            resetForm(getString(R.string.profile_status_all_cleared))
        }
        binding.renewProfileButton.setOnClickListener {
            renewSelectedProfile()
        }
        binding.archiveProfileButton.setOnClickListener {
            archiveSelectedProfile()
        }
        binding.deleteProfileButton.setOnClickListener {
            deleteSelectedProfile()
        }
        binding.deleteOldKeysButton.setOnClickListener {
            deleteOldKeys()
        }

        seedDefaults()
        focusImportMode = intent.getBooleanExtra(EXTRA_FOCUS_IMPORT, false)
        binding.importKeyCard.visibility = if (focusImportMode) android.view.View.VISIBLE else android.view.View.GONE
        binding.manualKeyEditCard.visibility = android.view.View.GONE
        if (focusImportMode) {
            prepareImportMode()
        } else {
            generateRandomKey()
        }
        renderProfiles(getString(R.string.profile_status_ready))

        if (focusImportMode) {
            binding.importKeyInput.requestFocus()
            binding.statusText.text = getString(R.string.profile_status_key_missing)
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasResumedOnce && !focusImportMode) {
            generateRandomKey()
        }
        hasResumedOnce = true
    }

    private fun seedDefaults() {
        if (binding.titleInput.text.isNullOrBlank()) {
            binding.titleInput.setText(getString(R.string.profile_default_title))
        }
        binding.appPackageInput.setText("")
        binding.peerHintInput.setText("")
        binding.oneTimeReadCheckBox.isChecked = false
        binding.biometricDecryptCheckBox.isChecked = false
        binding.exportAllowedCheckBox.isChecked = true
        updateExpiryButton()
    }

    private fun clearSelectedProfileForNewKey() {
        binding.selectedProfileIdInput.setText("")
        binding.manualKeyEditCard.visibility = android.view.View.GONE
        binding.createProfileButton.setText(R.string.save_profile)
        binding.saveGeneratedKeyButton.setText(R.string.save_profile)
        binding.saveImportedKeyButton.setText(R.string.save_profile)
        binding.oneTimeReadCheckBox.isChecked = false
        binding.biometricDecryptCheckBox.isChecked = false
        binding.exportAllowedCheckBox.isChecked = true
    }

    private fun generateRandomKey() {
        clearSelectedProfileForNewKey()
        currentKeyBundle = keyBundleCodec.createRandomBundle(
            title = nextDefaultKeyTitle(),
            appPackage = null,
            peerHint = null,
        )
        applyBundleToInputs(
            bundle = currentKeyBundle ?: return,
            keepImportField = false,
        )
        renderProfiles(getString(R.string.profile_status_ready))
    }

    private fun copyKeyBundle() {
        val existing = findSelectedProfileOrNull()
        if (existing?.exportAllowed == false) {
            renderProfiles(getString(R.string.profile_export_blocked))
            return
        }
        val bundle = resolveExportBundle() ?: run {
            renderProfiles(getString(R.string.profile_error_exactly_five_emoji))
            return
        }
        val doCopy = {
            val encoded = keyBundleCodec.encode(bundle)
            clipboardManager.setPrimaryClip(
                ClipData.newPlainText(getString(R.string.profile_key_clipboard_label), encoded),
            )
            binding.importKeyInput.setText(encoded)
            renderProfiles(getString(R.string.profile_status_key_copied))
        }
        protectExport(doCopy)
    }

    private fun shareKeyBundle() {
        val existing = findSelectedProfileOrNull()
        if (existing?.exportAllowed == false) {
            renderProfiles(getString(R.string.profile_export_blocked))
            return
        }
        val bundle = resolveExportBundle() ?: run {
            renderProfiles(getString(R.string.profile_error_exactly_five_emoji))
            return
        }
        val doShare = {
            val encoded = keyBundleCodec.encode(bundle)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, encoded)
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.profile_key_clipboard_label))
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.profile_share_key_chooser)))
            renderProfiles(getString(R.string.profile_status_key_shared))
        }
        protectExport(doShare)
    }

    private fun importKeyBundle() {
        val raw = binding.importKeyInput.text?.toString()?.trim().orEmpty()
            .ifBlank {
                clipboardManager.primaryClip
                    ?.getItemAt(0)
                    ?.coerceToText(this)
                    ?.toString()
                    ?.trim()
                    .orEmpty()
            }
        if (raw.isBlank()) {
            renderProfiles(getString(R.string.profile_status_key_missing))
            return
        }
        binding.importKeyInput.setText("")
        binding.importKeyInput.setText(raw)
        binding.importKeyInput.setSelection(binding.importKeyInput.text?.length ?: 0)
        val bundle = runCatching { keyBundleCodec.decode(raw) }.getOrElse {
            renderProfiles(getString(R.string.profile_status_key_invalid))
            return
        }
        clearSelectedProfileForNewKey()
        currentKeyBundle = bundle
        applyBundleToInputs(bundle, keepImportField = true)
        renderProfiles(getString(R.string.profile_status_key_imported))
    }

    private fun applyBundleToInputs(bundle: EmojiKeyBundle, keepImportField: Boolean) {
        val display = bundle.emojis.joinToString(" ")
        if (!bundle.title.isNullOrBlank()) {
            binding.titleInput.setText(bundle.title)
        }
        binding.appPackageInput.setText("")
        binding.peerHintInput.setText("")
        binding.emojiSequenceInput.setText(display)
        binding.generatedEmojiText.text = display
        if (!keepImportField) {
            binding.importKeyInput.setText("")
        }
    }

    private fun prepareImportMode() {
        currentKeyBundle = null
        binding.selectedProfileIdInput.setText("")
        binding.importKeyInput.setText("")
        binding.emojiSequenceInput.setText("")
        binding.generatedEmojiText.text = ""
        binding.emojiSequenceInput.hint = getString(R.string.profile_import_key_hint)
        binding.createProfileButton.setText(R.string.save_profile)
        binding.saveImportedKeyButton.setText(R.string.save_profile)
    }

    private fun resolveExportBundle(): EmojiKeyBundle? {
        val selected = findSelectedProfileOrNull()
        if (selected?.secretSequenceDisplay != null) {
            return EmojiKeyBundle(
                title = binding.titleInput.text?.toString()?.trim().orEmpty().ifBlank { selected.title },
                emojis = selected.secretSequenceDisplay.split(Regex("\\s+")).filter { it.isNotBlank() },
                profileSalt = selected.profileSalt,
                appPackage = null,
                peerHint = null,
            )
        }

        val emojiRaw = binding.emojiSequenceInput.text?.toString()?.trim().orEmpty()
        val emojis = emojiRaw.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (emojis.size != ProfileKeyDeriver.EMOJI_SEQUENCE_LENGTH) {
            return null
        }

        val bundle = currentKeyBundle
        return if (bundle != null && bundle.emojis == emojis) {
            bundle.copy(
                title = binding.titleInput.text?.toString()?.trim().orEmpty().ifBlank { bundle.title ?: "" },
                appPackage = null,
                peerHint = null,
            )
        } else {
            keyBundleCodec.createRandomBundle(
                title = binding.titleInput.text?.toString(),
                appPackage = null,
                peerHint = null,
            ).copy(emojis = emojis)
        }
    }

    private fun upsertProfileFromInputs(forceCreate: Boolean = false) {
        val title = binding.titleInput.text?.toString()?.trim().orEmpty()
            .ifBlank {
                currentKeyBundle?.title?.takeIf { it.isNotBlank() }
                    ?: getString(R.string.profile_default_title)
            }
        val emojiRaw = binding.emojiSequenceInput.text?.toString()?.trim().orEmpty()
        val emojis = emojiRaw.split(Regex("\\s+")).filter { it.isNotBlank() }

        val existing = if (forceCreate) null else findSelectedProfileOrNull()
        if (existing == null && emojis.size != ProfileKeyDeriver.EMOJI_SEQUENCE_LENGTH) {
            renderProfiles(getString(R.string.profile_error_exactly_five_emoji))
            return
        }
        if (existing != null && emojiRaw.isBlank()) {
            val updated = existing.copy(
                title = title,
                appPackage = null,
                peerHint = null,
                oneTimeRead = binding.oneTimeReadCheckBox.isChecked,
                requireBiometricForDecrypt = binding.biometricDecryptCheckBox.isChecked,
                exportAllowed = binding.exportAllowedCheckBox.isChecked,
            )
            secureProfileStore.saveProfile(updated)
            renderProfiles(getString(R.string.profile_status_updated, updated.title))
            return
        }
        if (emojis.size != ProfileKeyDeriver.EMOJI_SEQUENCE_LENGTH) {
            renderProfiles(getString(R.string.profile_error_exactly_five_emoji))
            return
        }

        val matchingBundle = when {
            existing?.secretSequenceDisplay != null &&
                existing.secretSequenceDisplay.split(Regex("\\s+")).filter { it.isNotBlank() } == emojis ->
                EmojiKeyBundle(existing.title, emojis, existing.profileSalt, null, null)
            currentKeyBundle?.emojis == emojis -> currentKeyBundle
            else -> null
        }

        val result = if (matchingBundle != null) {
            runCatching {
                profileFactory.createFromEmojiSequenceWithSalt(
                    title = title,
                    emojis = emojis,
                    profileSalt = matchingBundle.profileSalt,
                    appPackage = null,
                    peerHint = null,
                    rotationHours = selectedRotationHours,
                    oneTimeRead = binding.oneTimeReadCheckBox.isChecked,
                    requireBiometricForDecrypt = binding.biometricDecryptCheckBox.isChecked,
                    exportAllowed = binding.exportAllowedCheckBox.isChecked,
                )
            }.getOrElse {
                renderProfiles(getString(R.string.profile_status_key_invalid))
                return
            }
        } else {
            runCatching {
                profileFactory.createFromEmojiSequence(
                    title = title,
                    emojis = emojis,
                    appPackage = null,
                    peerHint = null,
                    rotationHours = selectedRotationHours,
                    oneTimeRead = binding.oneTimeReadCheckBox.isChecked,
                    requireBiometricForDecrypt = binding.biometricDecryptCheckBox.isChecked,
                    exportAllowed = binding.exportAllowedCheckBox.isChecked,
                )
            }.getOrElse {
                renderProfiles(getString(R.string.profile_status_key_invalid))
                return
            }
        }

        if (existing != null) {
            secureProfileStore.saveProfile(
                result.profile.copy(
                    id = existing.id,
                    profileVersion = existing.profileVersion + 1,
                    createdAt = existing.createdAt,
                    lastUsedAt = existing.lastUsedAt,
                    oneTimeRead = binding.oneTimeReadCheckBox.isChecked,
                    requireBiometricForDecrypt = binding.biometricDecryptCheckBox.isChecked,
                    exportAllowed = binding.exportAllowedCheckBox.isChecked,
                ),
                result.profileKey,
            )
            binding.selectedProfileIdInput.setText(existing.id)
            binding.createProfileButton.setText(R.string.save_profile)
            currentKeyBundle = matchingBundle
                ?: EmojiKeyBundle(title, emojis, result.profile.profileSalt, null, null)
            renderProfiles(getString(R.string.profile_status_ready))
            return
        }

        secureProfileStore.saveProfile(result.profile, result.profileKey)
        binding.selectedProfileIdInput.setText(result.profile.id)
        binding.createProfileButton.setText(R.string.save_profile)
        currentKeyBundle = matchingBundle
            ?: EmojiKeyBundle(title, emojis, result.profile.profileSalt, null, null)
        renderProfiles(getString(R.string.profile_status_ready))
    }

    private fun renewSelectedProfile() {
        val profile = findSelectedProfile() ?: return
        val renewed = ProfileLifecycleService.renew(profile)
        secureProfileStore.saveProfile(renewed)
        renderProfiles(getString(R.string.profile_status_renewed, renewed.title))
    }

    private fun archiveSelectedProfile() {
        val profile = findSelectedProfile() ?: return
        val archived = ProfileLifecycleService.archive(profile)
        secureProfileStore.saveProfile(archived)
        renderProfiles(getString(R.string.profile_status_archived_message, archived.title))
    }

    private fun deleteSelectedProfile() {
        val profile = findSelectedProfile() ?: return
        secureProfileStore.deleteProfile(profile.id)
        resetForm(getString(R.string.profile_status_deleted, profile.title))
    }

    private fun deleteOldKeys() {
        val profiles = secureProfileStore.listProfiles()
        val currentProfile = ProfileSelectionPolicy.selectDefault(profiles) ?: profiles.firstOrNull()
        val oldProfiles = profiles.filter { it.id != currentProfile?.id }
        if (oldProfiles.isEmpty()) {
            renderProfiles(getString(R.string.profile_status_no_old_keys))
            return
        }

        AlertDialog.Builder(this)
            .setMessage(getString(R.string.profile_delete_old_keys_confirm))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.profile_delete_old_keys) { _, _ ->
                oldProfiles.forEach { secureProfileStore.deleteProfile(it.id) }
                renderProfiles(getString(R.string.profile_status_old_keys_deleted))
            }
            .show()
    }

    private fun bindSelectedProfile(profile: KeyProfile) {
        binding.selectedProfileIdInput.setText(profile.id)
        binding.manualKeyEditCard.visibility = View.GONE
        binding.titleInput.setText(profile.title)
        binding.appPackageInput.setText("")
        binding.peerHintInput.setText("")
        binding.emojiSequenceInput.setText(profile.secretSequenceDisplay.orEmpty())
        binding.generatedEmojiText.text = profile.secretSequenceDisplay.orEmpty()
        binding.importKeyInput.setText("")
        binding.emojiSequenceInput.hint = getString(R.string.profile_emoji_sequence_edit_hint)
        binding.createProfileButton.setText(R.string.save_profile)
        binding.saveGeneratedKeyButton.setText(R.string.save_profile)
        binding.saveImportedKeyButton.setText(R.string.save_profile)
        selectedRotationHours = profile.rotationPeriodHours
        binding.oneTimeReadCheckBox.isChecked = profile.oneTimeRead
        binding.biometricDecryptCheckBox.isChecked = profile.requireBiometricForDecrypt
        binding.exportAllowedCheckBox.isChecked = profile.exportAllowed
        updateExpiryButton()
        currentKeyBundle = profile.secretSequenceDisplay
            ?.split(Regex("\\s+"))
            ?.filter { it.isNotBlank() }
            ?.takeIf { it.size == ProfileKeyDeriver.EMOJI_SEQUENCE_LENGTH }
            ?.let { emojis ->
                EmojiKeyBundle(
                    title = profile.title,
                    emojis = emojis,
                    profileSalt = profile.profileSalt,
                    appPackage = null,
                    peerHint = null,
                )
            }
    }

    private fun resetForm(status: String) {
        binding.selectedProfileIdInput.setText("")
        binding.titleInput.setText(getString(R.string.profile_default_title))
        binding.appPackageInput.setText("")
        binding.peerHintInput.setText("")
        binding.importKeyInput.setText("")
        binding.emojiSequenceInput.hint = getString(R.string.profile_emoji_sequence_hint)
        binding.createProfileButton.setText(R.string.save_profile)
        binding.saveGeneratedKeyButton.setText(R.string.save_profile)
        binding.saveImportedKeyButton.setText(R.string.save_profile)
        binding.manualKeyEditCard.visibility = android.view.View.GONE
        selectedRotationHours = 48
        binding.oneTimeReadCheckBox.isChecked = false
        binding.biometricDecryptCheckBox.isChecked = false
        binding.exportAllowedCheckBox.isChecked = true
        updateExpiryButton()
        generateRandomKey()
        renderProfiles(status)
    }

    private fun showCurrentKeyEditDialog() {
        val title = binding.titleInput.text?.toString()?.trim().orEmpty()
            .ifBlank { currentKeyBundle?.title.orEmpty().ifBlank { nextDefaultKeyTitle() } }
        val emojiSequence = binding.emojiSequenceInput.text?.toString()?.trim().orEmpty()
            .ifBlank { currentKeyBundle?.emojis?.joinToString(" ").orEmpty() }
        showKeyEditDialog(
            title = getString(R.string.profile_action_edit),
            initialTitle = title,
            initialEmojiSequence = emojiSequence,
            positiveLabel = getString(R.string.profile_return),
        ) { keyTitle, editedEmojiSequence ->
            val emojis = splitEmojiSequence(editedEmojiSequence)
            binding.titleInput.setText(keyTitle)
            binding.emojiSequenceInput.setText(editedEmojiSequence.trim())
            binding.generatedEmojiText.text = buildString {
                append(keyTitle)
                append("\n")
                append(editedEmojiSequence.trim())
            }
            currentKeyBundle = (currentKeyBundle ?: keyBundleCodec.createRandomBundle(title = keyTitle)).copy(
                title = keyTitle,
                emojis = emojis,
            )
            renderProfiles(getString(R.string.profile_status_ready))
        }
    }

    private fun showCreateKeyDialog() {
        val randomBundle = keyBundleCodec.createRandomBundle(title = nextDefaultKeyTitle())
        showKeyEditDialog(
            title = getString(R.string.profile_generate_key),
            initialTitle = randomBundle.title.orEmpty(),
            initialEmojiSequence = randomBundle.emojis.joinToString(" "),
            positiveLabel = getString(R.string.save_profile),
        ) { keyTitle, emojiSequence ->
            currentKeyBundle = randomBundle.copy(
                title = keyTitle,
                emojis = splitEmojiSequence(emojiSequence),
            )
            binding.selectedProfileIdInput.setText("")
            binding.titleInput.setText(keyTitle)
            binding.emojiSequenceInput.setText(emojiSequence.trim())
            upsertProfileFromInputs(forceCreate = true)
            binding.manualKeyEditCard.visibility = View.GONE
        }
    }

    private fun showEditKeyDialog(profile: KeyProfile) {
        showKeyEditDialog(
            title = getString(R.string.profile_action_edit),
            initialTitle = profile.title,
            initialEmojiSequence = profile.secretSequenceDisplay.orEmpty(),
            positiveLabel = getString(R.string.save_profile),
        ) { keyTitle, emojiSequence ->
            binding.selectedProfileIdInput.setText(profile.id)
            binding.titleInput.setText(keyTitle)
            binding.emojiSequenceInput.setText(emojiSequence.trim())
            selectedRotationHours = profile.rotationPeriodHours
            binding.oneTimeReadCheckBox.isChecked = profile.oneTimeRead
            binding.biometricDecryptCheckBox.isChecked = profile.requireBiometricForDecrypt
            binding.exportAllowedCheckBox.isChecked = profile.exportAllowed
            upsertProfileFromInputs()
            binding.manualKeyEditCard.visibility = View.GONE
        }
    }

    private fun showKeyEditDialog(
        title: String,
        initialTitle: String,
        initialEmojiSequence: String,
        positiveLabel: String,
        onSave: (String, String) -> Unit,
    ) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val titleInput = EditText(this).apply {
            hint = getString(R.string.profile_title_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine(true)
            setText(initialTitle)
        }
        val emojiInput = EditText(this).apply {
            hint = getString(R.string.profile_emoji_sequence_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setSingleLine(true)
            setText(initialEmojiSequence)
        }
        container.addView(titleInput)
        container.addView(emojiInput)

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(positiveLabel, null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val keyTitle = titleInput.text?.toString()?.trim().orEmpty()
                            .ifBlank { nextDefaultKeyTitle() }
                        val emojiSequence = emojiInput.text?.toString()?.trim().orEmpty()
                        if (splitEmojiSequence(emojiSequence).size != ProfileKeyDeriver.EMOJI_SEQUENCE_LENGTH) {
                            binding.statusText.text = getString(R.string.profile_error_exactly_five_emoji)
                            binding.statusText.visibility = View.VISIBLE
                            return@setOnClickListener
                        }
                        onSave(keyTitle, emojiSequence)
                        dialog.dismiss()
                    }
                }
            }
            .show()
    }

    private fun splitEmojiSequence(raw: String): List<String> =
        raw.trim().split(Regex("\\s+")).filter { it.isNotBlank() }

    private fun nextDefaultKeyTitle(): String {
        val maxNumber = secureProfileStore.listProfiles()
            .mapNotNull { profile ->
                Regex("""^Key_(\d+)$""").matchEntire(profile.title)?.groupValues?.getOrNull(1)?.toIntOrNull()
            }
            .maxOrNull() ?: secureProfileStore.listProfiles().size
        return "Key_${maxNumber + 1}"
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun findSelectedProfile(): KeyProfile? {
        val id = binding.selectedProfileIdInput.text?.toString()?.trim().orEmpty()
        if (id.isBlank()) {
            renderProfiles(getString(R.string.profile_error_id_required))
            return null
        }

        return secureProfileStore.listProfiles().firstOrNull { it.id == id }
            ?: run {
                renderProfiles(getString(R.string.profile_error_not_found, id))
                null
            }
    }

    private fun findSelectedProfileOrNull(): KeyProfile? {
        val id = binding.selectedProfileIdInput.text?.toString()?.trim().orEmpty()
        if (id.isBlank()) return null
        return secureProfileStore.listProfiles().firstOrNull { it.id == id }
    }

    private fun formatProfileMeta(profile: KeyProfile): String =
        buildString {
            append(localizedSecretKind(profile.secretSequenceKind))
            append(" • ")
            append(localizedProfileStatus(profile.status))
            if (profile.oneTimeRead) {
                append(" • ")
                append(getString(R.string.profile_one_time_short))
            }
            if (profile.requireBiometricForDecrypt) {
                append(" • ")
                append(getString(R.string.profile_biometric_short))
            }
            if (!profile.exportAllowed) {
                append(" • ")
                append(getString(R.string.profile_no_export_short))
            }
            append("\n")
            append(profile.expiresAt.toString())
        }

    private fun localizedSecretKind(kind: com.truelock.enigma.profiles.SecretSequenceKind): String = when (kind) {
        com.truelock.enigma.profiles.SecretSequenceKind.EMOJI_SEQUENCE -> getString(R.string.profile_kind_emoji_sequence)
        com.truelock.enigma.profiles.SecretSequenceKind.VISUAL_SEQUENCE -> getString(R.string.profile_kind_visual_sequence)
        com.truelock.enigma.profiles.SecretSequenceKind.CONTACT_HANDSHAKE -> getString(R.string.profile_kind_contact_handshake)
    }

    private fun localizedProfileStatus(status: com.truelock.enigma.profiles.KeyProfileStatus): String = when (status) {
        com.truelock.enigma.profiles.KeyProfileStatus.ACTIVE -> getString(R.string.profile_status_active)
        com.truelock.enigma.profiles.KeyProfileStatus.EXPIRING -> getString(R.string.profile_status_expiring)
        com.truelock.enigma.profiles.KeyProfileStatus.EXPIRED -> getString(R.string.profile_status_expired)
        com.truelock.enigma.profiles.KeyProfileStatus.ARCHIVED -> getString(R.string.profile_status_archived)
    }

    private fun renderProfiles(status: String) {
        val profiles = secureProfileStore.listProfiles()
        val currentProfile = ProfileSelectionPolicy.selectDefault(profiles) ?: profiles.firstOrNull()
        renderedProfiles = profiles
        binding.statusText.text = status
        binding.statusText.visibility =
            if (status == getString(R.string.profile_status_ready)) android.view.View.GONE else android.view.View.VISIBLE
        val hasProfiles = profiles.isNotEmpty()
        val showSavedKeys = hasProfiles && !focusImportMode
        binding.profileSummaryCard.visibility =
            if (!focusImportMode) android.view.View.VISIBLE else android.view.View.GONE
        binding.manualKeyEditCard.visibility = android.view.View.GONE
        binding.savedKeysTitleText.visibility = if (showSavedKeys) android.view.View.VISIBLE else android.view.View.GONE
        binding.savedKeysSubtitleText.visibility = if (showSavedKeys) android.view.View.VISIBLE else android.view.View.GONE
        binding.profileListSurface.visibility = if (showSavedKeys) android.view.View.VISIBLE else android.view.View.GONE
        binding.deleteOldKeysButton.visibility = android.view.View.GONE
        binding.clearProfilesButton.visibility = if (hasProfiles) android.view.View.VISIBLE else android.view.View.GONE
        currentProfile
            ?.takeIf {
                currentKeyBundle == null &&
                    binding.selectedProfileIdInput.text?.toString()?.trim().isNullOrBlank()
            }
            ?.let { profile ->
            binding.generatedEmojiText.text = buildString {
                append(profile.title)
                append("\n")
                append(profile.secretSequenceDisplay.orEmpty())
            }
        }

        val selectedId = binding.selectedProfileIdInput.text?.toString()?.trim().orEmpty()
        renderProfilesList(profiles, selectedId, currentProfile?.id)
    }

    private fun renderProfilesList(
        profiles: List<KeyProfile>,
        selectedId: String,
        currentProfileId: String?,
    ) {
        binding.profileListContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)

        if (profiles.isEmpty()) {
            return
        }

        profiles.forEach { profile ->
            val itemView = inflater.inflate(R.layout.item_profile_list, binding.profileListContainer, false)
            itemView.findViewById<TextView>(R.id.profileTitleText).text =
                if (profile.id == currentProfileId) "• ${profile.title}" else profile.title
            itemView.findViewById<TextView>(R.id.profileEmojiText).text =
                profile.secretSequenceDisplay.orEmpty()
            itemView.findViewById<TextView>(R.id.profileMetaText).text = formatProfileMeta(profile)
            itemView.isActivated = profile.id == selectedId
            itemView.alpha = if (itemView.isActivated) 1f else 0.92f
            itemView.setOnClickListener {
                secureProfileStore.touchProfile(profile.id)
                bindSelectedProfile(profile)
                renderProfiles(getString(R.string.profile_status_ready))
            }
            itemView.findViewById<View>(R.id.copyProfileItemButton).setOnClickListener {
                copyProfileItem(profile)
            }
            itemView.findViewById<View>(R.id.editProfileItemButton).setOnClickListener {
                showEditKeyDialog(profile)
            }
            itemView.findViewById<View>(R.id.deleteProfileItemButton).setOnClickListener {
                secureProfileStore.deleteProfile(profile.id)
                val statusMessage = getString(R.string.profile_status_deleted, profile.title)
                if (binding.selectedProfileIdInput.text?.toString()?.trim() == profile.id) {
                    resetForm(statusMessage)
                } else {
                    renderProfiles(statusMessage)
                }
            }
            binding.profileListContainer.addView(itemView)
        }
    }

    private fun copyProfileItem(profile: KeyProfile) {
        if (!profile.exportAllowed) {
            renderProfiles(getString(R.string.profile_export_blocked))
            return
        }
        val emojis = profile.secretSequenceDisplay
            ?.split(Regex("\\s+"))
            ?.filter { it.isNotBlank() }
            ?.takeIf { it.size == ProfileKeyDeriver.EMOJI_SEQUENCE_LENGTH }
            ?: return
        val bundle = EmojiKeyBundle(
            title = profile.title,
            emojis = emojis,
            profileSalt = profile.profileSalt,
            appPackage = null,
            peerHint = null,
        )
        protectExport {
            val encoded = keyBundleCodec.encode(bundle)
            clipboardManager.setPrimaryClip(
                ClipData.newPlainText(getString(R.string.profile_key_clipboard_label), encoded),
            )
            renderProfiles(getString(R.string.profile_status_key_copied_item, profile.title))
        }
    }

    private fun protectExport(action: () -> Unit) {
        if (!biometricHelper.canUseBiometric()) {
            action()
            return
        }
        biometricHelper.authenticate(
            onSuccess = action,
            onError = { renderProfiles(it) },
        )
    }

    private fun showExpiryPicker() {
        val labels = EXPIRY_OPTIONS_HOURS.map(::expiryLabelForHours).toTypedArray()
        val currentIndex = EXPIRY_OPTIONS_HOURS.indexOf(selectedRotationHours).takeIf { it >= 0 } ?: 1
        AlertDialog.Builder(this)
            .setTitle(R.string.profile_expiry_picker_title)
            .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                selectedRotationHours = EXPIRY_OPTIONS_HOURS[which]
                updateExpiryButton()
                dialog.dismiss()
            }
            .show()
    }

    private fun updateExpiryButton() {
        binding.keyExpiryButton.text = expiryLabelForHours(selectedRotationHours)
    }

    private fun expiryLabelForHours(hours: Int): String = when (hours) {
        24 -> getString(R.string.profile_expiry_24h)
        48 -> getString(R.string.profile_expiry_48h)
        24 * 7 -> getString(R.string.profile_expiry_7d)
        24 * 30 -> getString(R.string.profile_expiry_30d)
        else -> getString(R.string.profile_expiry_default)
    }
}
