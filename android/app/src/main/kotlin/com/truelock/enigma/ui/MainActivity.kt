package com.truelock.enigma.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ImageSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import com.truelock.enigma.R
import com.truelock.enigma.crypto.Tl1MessageCodec
import com.truelock.enigma.crypto.Tl1ShareEnvelope
import com.truelock.enigma.databinding.ActivityMainBinding
import com.truelock.enigma.license.LicenseStore
import com.truelock.enigma.media.PendingCapsuleStore
import com.truelock.enigma.settings.KeyboardAppearancePreferences
import com.truelock.enigma.settings.KeyboardLanguagePreferences
import com.truelock.enigma.sharing.ShareInviteMode
import com.truelock.enigma.sharing.ShareInvitePreferences
import com.truelock.enigma.storage.FileKeyProfileRepository
import com.truelock.enigma.storage.ProfileKeyVault
import com.truelock.enigma.storage.SecureProfileStore
import com.truelock.enigma.storage.TemporaryMediaJanitor
import java.util.Locale

class MainActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_FOCUS_IMPORT = "focus_import"
        const val EXTRA_OPEN_VIDEO_FROM_KEYBOARD = "open_video_from_keyboard"
    }

    private enum class SetupStage {
        ENABLE_KEYBOARD,
        CREATE_KEY,
        SELECT_KEYBOARD,
        READY,
    }

    private data class KeyboardState(
        val enabled: Boolean,
        val selected: Boolean,
        val label: String,
    )

    private lateinit var binding: ActivityMainBinding
    private lateinit var secureProfileStore: SecureProfileStore
    private lateinit var shareInvitePreferences: ShareInvitePreferences
    private lateinit var keyboardLanguagePreferences: KeyboardLanguagePreferences
    private lateinit var keyboardAppearancePreferences: KeyboardAppearancePreferences
    private lateinit var licenseStore: LicenseStore
    private val codec = Tl1MessageCodec()
    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val contentRoot = FrameLayout(this).apply {
            addView(
                binding.root,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        setContentView(contentRoot)
        showLaunchSplash(contentRoot)

        secureProfileStore = SecureProfileStore(
            repository = FileKeyProfileRepository(applicationContext),
            keyVault = ProfileKeyVault(),
        )
        shareInvitePreferences = ShareInvitePreferences(applicationContext)
        keyboardLanguagePreferences = KeyboardLanguagePreferences(applicationContext)
        keyboardAppearancePreferences = KeyboardAppearancePreferences(applicationContext)
        licenseStore = LicenseStore(applicationContext)

        ensureDefaultAppLanguage()

        handleIncomingAction(intent)

        binding.primaryActionButton.setOnClickListener {
            when (resolveSetupStage()) {
                SetupStage.ENABLE_KEYBOARD -> {
                    startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                }
                SetupStage.CREATE_KEY -> {
                    startActivity(Intent(this, ProfileManagerActivity::class.java))
                }
                SetupStage.SELECT_KEYBOARD,
                SetupStage.READY,
                -> {
                    showKeyboardPicker()
                }
            }
        }
        binding.openProfileManagerButton.setOnClickListener {
            startActivity(Intent(this, ProfileManagerActivity::class.java))
        }
        binding.openKeySetupButton.setOnClickListener {
            startActivity(Intent(this, ProfileManagerActivity::class.java))
        }
        binding.openKeyImportButton.setOnClickListener {
            startActivity(
                Intent(this, ProfileManagerActivity::class.java).apply {
                    putExtra(EXTRA_FOCUS_IMPORT, true)
                },
            )
        }
        binding.openInputMethodSettingsButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        binding.openAudioCapsuleButton.setOnClickListener {
            requireLicense { startActivity(Intent(this, AudioCapsuleActivity::class.java)) }
        }
        binding.openVideoCapsuleButton.setOnClickListener {
            requireLicense { startActivity(Intent(this, VideoCapsuleActivity::class.java)) }
        }
        binding.openPhotoCapsuleButton.setOnClickListener {
            requireLicense { startActivity(Intent(this, PhotoCapsuleActivity::class.java)) }
        }
        binding.showInputMethodPickerButton.setOnClickListener {
            showKeyboardPicker()
        }
        binding.copyDemoMessageButton.setOnClickListener {
            copyDemoMessage()
        }
        binding.languageButton.setOnClickListener {
            showAppLanguagePicker()
        }
        binding.keyboardLanguagesButton.setOnClickListener {
            showKeyboardLanguagesPicker()
        }
        binding.keyboardAppearanceButton.setOnClickListener {
            showKeyboardAppearancePicker()
        }
        binding.heroLicenseButton.setOnClickListener {
            startActivity(Intent(this, LicenseActivity::class.java))
        }
        binding.licenseButton.setOnClickListener {
            startActivity(Intent(this, LicenseActivity::class.java))
        }
        binding.panicWipeButton.setOnClickListener {
            showPanicWipeDialog()
        }
        binding.shareInviteModeButton.setOnClickListener {
            showShareModePicker()
        }

        maybeShowPermissionsNotice()
        renderMainState(getString(R.string.main_status_ready))
    }

    private fun showLaunchSplash(contentRoot: FrameLayout) {
        val overlay = FrameLayout(this).apply {
            isClickable = true
            setBackground(
                GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(
                        Color.parseColor("#1F5EA8"),
                        Color.parseColor("#123B6D"),
                        Color.parseColor("#0D2340"),
                    ),
                ),
            )
        }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.ic_launcher_foreground)
            scaleType = ImageView.ScaleType.FIT_CENTER
            alpha = 0f
            scaleX = 0.88f
            scaleY = 0.88f
        }
        overlay.addView(
            logo,
            FrameLayout.LayoutParams(dp(156), dp(156), Gravity.CENTER),
        )
        val title = TextView(this).apply {
            text = getString(R.string.app_name)
            gravity = Gravity.CENTER
            textSize = 28f
            setTextColor(Color.parseColor("#E4BE67"))
            alpha = 0f
            letterSpacing = 0.04f
        }
        overlay.addView(
            title,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ).apply {
                topMargin = dp(182)
            },
        )
        contentRoot.addView(
            overlay,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        logo.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(420L).start()
        title.animate().alpha(1f).setStartDelay(140L).setDuration(360L).start()
        overlay.postDelayed({
            overlay.animate()
                .alpha(0f)
                .setDuration(320L)
                .withEndAction { contentRoot.removeView(overlay) }
                .start()
        }, 980L)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingAction(intent)
    }

    override fun onResume() {
        super.onResume()
        renderMainState(getString(R.string.main_status_ready))
    }

    private fun requireLicense(action: () -> Unit) {
        if (licenseStore.isActive()) {
            action()
            return
        }
        startActivity(Intent(this, LicenseActivity::class.java))
        renderMainState(getString(R.string.license_required_status))
    }

    private fun handleIncomingAction(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_VIDEO_FROM_KEYBOARD, false) != true) return
        intent.removeExtra(EXTRA_OPEN_VIDEO_FROM_KEYBOARD)
        startActivity(
            Intent(this, VideoCapsuleActivity::class.java).apply {
                putExtra(VideoCapsuleActivity.EXTRA_FROM_KEYBOARD, true)
            },
        )
    }

    private fun showKeyboardPicker() {
        runCatching {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }
    }

    private fun copyDemoMessage() {
        val profile = secureProfileStore.listProfiles().firstOrNull()
        if (profile == null) {
            renderMainState(getString(R.string.main_status_create_demo_profile))
            return
        }

        val rawKey = secureProfileStore.loadProfileKey(profile)
        val ciphertext = codec.encrypt(
            plaintext = getString(R.string.main_demo_plaintext),
            profileKey = rawKey,
            profileHint = profile.profileHint,
        )

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val shareText = Tl1ShareEnvelope.wrap(
            encodedMessage = ciphertext.encodedMessage,
            mode = shareInvitePreferences.getMode(),
            strings = Tl1ShareEnvelope.Strings(
                lockedLine = getString(R.string.keyboard_viral_invite_locked_line),
                installLine = getString(
                    R.string.keyboard_viral_invite_install_line,
                    getString(R.string.keyboard_viral_invite_url),
                ),
                balancedLine = getString(
                    R.string.keyboard_balanced_invite_line,
                    getString(R.string.keyboard_viral_invite_url),
                ),
            ),
        )
        clipboard.setPrimaryClip(
            ClipData.newPlainText(getString(R.string.main_clipboard_label_tl1), shareText),
        )
        renderMainState(getString(R.string.main_status_demo_copied))
    }

    private fun renderMainState(status: String) {
        val profiles = secureProfileStore.listProfiles()
        val keyboardState = resolveKeyboardState()
        val setupStage = resolveSetupStage(keyboardState, profiles.isNotEmpty())
        val hasProfiles = profiles.isNotEmpty()

        binding.profileCountText.text = getString(R.string.profile_count_format, profiles.size)
        binding.debugStatusText.text = status
        binding.languageButton.text = resolveAppLanguageLabel()
        binding.keyboardLanguagesValueText.text = getString(
            R.string.main_keyboard_languages_value,
            resolveKeyboardLanguagesLabel(),
        )
        binding.keyboardAppearanceValueText.text = getString(
            R.string.main_keyboard_appearance_value,
            keyboardThemeLabel(keyboardAppearancePreferences.getThemePreset()),
            keyboardShapeLabel(keyboardAppearancePreferences.getKeyShapePreset()),
            keyboardHeightLabel(keyboardAppearancePreferences.getHeightPreset()),
        )
        val entitlement = licenseStore.current()
        val licenseLabel = if (entitlement.active) {
            getString(R.string.main_license_active_format, entitlement.payload?.licenseId.orEmpty())
        } else {
            getString(R.string.main_license_inactive)
        }
        binding.licenseValueText.text = licenseLabel
        binding.heroLicenseValueText.text = licenseLabel
        binding.shareInviteModeValueText.text = getString(
            R.string.main_share_mode_current_format,
            shareModeLabel(shareInvitePreferences.getMode()),
        )
        binding.keyboardIconsHelpText.text = buildKeyboardIconsHelpText()
        binding.keyboardStateText.text = getString(R.string.main_keyboard_state_format, keyboardState.label)
        binding.primaryActionButton.visibility = View.VISIBLE
        binding.quickActionsCard.visibility = View.VISIBLE
        binding.secondaryActionsCard.visibility = View.VISIBLE
        binding.openKeySetupButton.visibility = View.VISIBLE
        binding.openKeyImportButton.visibility = View.VISIBLE
        binding.openProfileManagerButton.visibility =
            if (hasProfiles && setupStage == SetupStage.READY) View.VISIBLE else View.GONE
        binding.openInputMethodSettingsButton.visibility =
            if (setupStage == SetupStage.READY && !keyboardState.enabled) View.VISIBLE else View.GONE
        binding.showInputMethodPickerButton.visibility =
            if (setupStage == SetupStage.READY && keyboardState.enabled) View.VISIBLE else View.GONE
        binding.openAudioCapsuleButton.visibility =
            if (setupStage == SetupStage.READY) View.VISIBLE else View.GONE
        binding.openVideoCapsuleButton.visibility =
            if (setupStage == SetupStage.READY) View.VISIBLE else View.GONE
        binding.openPhotoCapsuleButton.visibility =
            if (setupStage == SetupStage.READY) View.VISIBLE else View.GONE
        binding.copyDemoMessageButton.visibility =
            if (setupStage == SetupStage.READY && hasProfiles) View.VISIBLE else View.GONE
        binding.summaryCardsRow.visibility =
            if (setupStage == SetupStage.READY) View.VISIBLE else View.GONE
        binding.debugStatusText.visibility =
            if (status == getString(R.string.main_status_ready)) View.GONE else View.VISIBLE

        when (setupStage) {
            SetupStage.ENABLE_KEYBOARD -> {
                binding.nextStepTitleText.setText(R.string.main_stage_enable_title)
                binding.nextStepBodyText.setText(R.string.main_stage_enable_body)
                binding.primaryActionButton.setText(R.string.open_input_method_settings)
            }
            SetupStage.CREATE_KEY -> {
                binding.nextStepTitleText.setText(R.string.main_stage_key_title)
                binding.nextStepBodyText.setText(R.string.main_stage_key_body)
                binding.primaryActionButton.setText(R.string.main_open_profile_manager_cta)
            }
            SetupStage.SELECT_KEYBOARD -> {
                binding.nextStepTitleText.setText(R.string.main_stage_select_title)
                binding.nextStepBodyText.setText(R.string.main_stage_select_body)
                binding.primaryActionButton.setText(R.string.show_input_method_picker)
            }
            SetupStage.READY -> {
                binding.nextStepTitleText.setText(R.string.main_stage_ready_title)
                binding.nextStepBodyText.setText(R.string.main_stage_ready_body)
                binding.primaryActionButton.setText(R.string.main_primary_ready_action)
            }
        }

        val hasSecondaryActions =
            binding.openInputMethodSettingsButton.visibility == View.VISIBLE ||
                binding.showInputMethodPickerButton.visibility == View.VISIBLE ||
                binding.openAudioCapsuleButton.visibility == View.VISIBLE ||
                binding.openVideoCapsuleButton.visibility == View.VISIBLE ||
                binding.openPhotoCapsuleButton.visibility == View.VISIBLE ||
                binding.copyDemoMessageButton.visibility == View.VISIBLE
        binding.secondaryActionsCard.visibility = if (hasSecondaryActions) View.VISIBLE else View.GONE
    }

    private fun showKeyboardLanguagesPicker() {
        val supportedTags = KeyboardLanguagePreferences.SUPPORTED_LANGUAGE_TAGS
        val labels = supportedTags.map(::languageLabel).toTypedArray()
        val checked = supportedTags.map { it in keyboardLanguagePreferences.getEnabledLanguageTags() }.toBooleanArray()

        AlertDialog.Builder(this)
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val selected = supportedTags.filterIndexed { index, _ -> checked[index] }.toSet()
                    .ifEmpty { KeyboardLanguagePreferences.DEFAULT_ENABLED_LANGUAGE_TAGS }
                keyboardLanguagePreferences.setEnabledLanguageTags(selected)
                renderMainState(getString(R.string.main_status_ready))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showAppLanguagePicker() {
        val languageTags = listOf("en", "ru", "de", "es", "fr", "it", "pt", "tr")
        val labels = languageTags.map(::languageLabel).toTypedArray()
        val currentTag = AppCompatDelegate.getApplicationLocales()[0]?.toLanguageTag().orEmpty().ifBlank { "en" }
        val currentIndex = languageTags.indexOf(currentTag).takeIf { it >= 0 } ?: 0

        AlertDialog.Builder(this)
            .setTitle(R.string.main_app_language_title)
            .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.forLanguageTags(languageTags[which]),
                )
                dialog.dismiss()
            }
            .show()
    }

    private fun showKeyboardAppearancePicker() {
        val themePresets = KeyboardAppearancePreferences.ThemePreset.entries.toTypedArray()
        val shapePresets = KeyboardAppearancePreferences.KeyShapePreset.entries.toTypedArray()
        val heightPresets = KeyboardAppearancePreferences.HeightPreset.entries.toTypedArray()
        val themeLabels = themePresets.map(::keyboardThemeLabel).toTypedArray()
        val shapeLabels = shapePresets.map(::keyboardShapeLabel).toTypedArray()
        val heightLabels = heightPresets.map(::keyboardHeightLabel).toTypedArray()
        var selectedTheme = keyboardAppearancePreferences.getThemePreset()
        var selectedShape = keyboardAppearancePreferences.getKeyShapePreset()
        var selectedHeight = keyboardAppearancePreferences.getHeightPreset()

        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(32, 20, 32, 12)
            addView(android.widget.TextView(context).apply {
                setText(R.string.main_keyboard_theme_title)
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(android.widget.RadioGroup(context).apply {
                themePresets.forEachIndexed { index, preset ->
                    addView(android.widget.RadioButton(context).apply {
                        id = index + 1000
                        text = themeLabels[index]
                        isChecked = preset == selectedTheme
                    })
                }
                setOnCheckedChangeListener { _, checkedId ->
                    selectedTheme = themePresets[(checkedId - 1000).coerceAtLeast(0)]
                }
            })
            addView(android.widget.TextView(context).apply {
                setText(R.string.main_keyboard_shape_title)
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, 24, 0, 0)
            })
            addView(android.widget.RadioGroup(context).apply {
                shapePresets.forEachIndexed { index, preset ->
                    addView(android.widget.RadioButton(context).apply {
                        id = index + 2000
                        text = shapeLabels[index]
                        isChecked = preset == selectedShape
                    })
                }
                setOnCheckedChangeListener { _, checkedId ->
                    selectedShape = shapePresets[(checkedId - 2000).coerceAtLeast(0)]
                }
            })
            addView(android.widget.TextView(context).apply {
                setText(R.string.main_keyboard_height_title)
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, 24, 0, 0)
            })
            addView(android.widget.RadioGroup(context).apply {
                heightPresets.forEachIndexed { index, preset ->
                    addView(android.widget.RadioButton(context).apply {
                        id = index + 3000
                        text = heightLabels[index]
                        isChecked = preset == selectedHeight
                    })
                }
                setOnCheckedChangeListener { _, checkedId ->
                    selectedHeight = heightPresets[(checkedId - 3000).coerceAtLeast(0)]
                }
            })
        }
        val scrollContainer = android.widget.ScrollView(this).apply {
            isFillViewport = true
            addView(
                container,
                android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.main_keyboard_appearance_button)
            .setView(scrollContainer)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                keyboardAppearancePreferences.setThemePreset(selectedTheme)
                keyboardAppearancePreferences.setKeyShapePreset(selectedShape)
                keyboardAppearancePreferences.setHeightPreset(selectedHeight)
                renderMainState(getString(R.string.main_status_ready))
            }
            .show()
    }

    private fun ensureDefaultAppLanguage() {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        if (!appLocales.isEmpty) return
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
    }

    private fun maybeShowPermissionsNotice() {
        val prefs = getSharedPreferences("veiltype_preferences", Context.MODE_PRIVATE)
        if (prefs.getBoolean("permissions_notice_shown", false)) return
        prefs.edit().putBoolean("permissions_notice_shown", true).apply()
        AlertDialog.Builder(this)
            .setTitle(R.string.main_permissions_notice_title)
            .setMessage(R.string.main_permissions_notice_body)
            .setNegativeButton(R.string.main_permissions_notice_later, null)
            .setPositiveButton(R.string.main_permissions_notice_continue) { _, _ ->
                val missingPermissions = listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
                    .filter {
                        ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
                    }
                if (missingPermissions.isNotEmpty()) {
                    permissionsLauncher.launch(missingPermissions.toTypedArray())
                }
            }
            .show()
    }

    private fun showPanicWipeDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.main_panic_title)
            .setMessage(R.string.main_panic_body)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.main_panic_confirm) { _, _ ->
                performPanicWipe()
            }
            .show()
    }

    private fun performPanicWipe() {
        secureProfileStore.clearAll()
        (getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.let { clipboard ->
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                clipboard.clearPrimaryClip()
            } else {
                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }

        listOf(
            "veiltype_preferences",
            "pending_capsule_store",
            "veiltype_decrypt_usage",
            "keyboard_language_preferences",
        ).forEach { name ->
            getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().apply()
        }

        deleteRecursively(java.io.File(filesDir, "media_capsules"))
        TemporaryMediaJanitor.purgeTransientMedia(applicationContext)

        renderMainState(getString(R.string.main_panic_done))
    }

    private fun deleteRecursively(target: java.io.File) {
        runCatching {
            if (!target.exists()) return
            if (target.isDirectory) {
                target.listFiles()?.forEach(::deleteRecursively)
            }
            target.delete()
        }
    }

    private fun showShareModePicker() {
        val modes = ShareInviteMode.entries
        val labels = modes.map(::shareModeLabel).toTypedArray()
        val currentIndex = modes.indexOf(shareInvitePreferences.getMode())
            .takeIf { it >= 0 }
            ?: 0

        AlertDialog.Builder(this)
            .setTitle(R.string.main_share_mode_title)
            .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                val selectedMode = modes[which]
                shareInvitePreferences.setMode(selectedMode)
                renderMainState(
                    getString(
                        R.string.main_share_mode_changed,
                        shareModeLabel(selectedMode),
                    ),
                )
                dialog.dismiss()
            }
            .show()
    }

    private fun buildKeyboardIconsHelpText(): CharSequence {
        val rows = listOf(
            android.R.drawable.ic_lock_lock to getString(R.string.main_keyboard_icons_encrypt),
            android.R.drawable.ic_menu_view to getString(R.string.main_keyboard_icons_decrypt),
            android.R.drawable.ic_menu_close_clear_cancel to getString(R.string.main_keyboard_icons_clear),
            android.R.drawable.ic_btn_speak_now to getString(R.string.main_keyboard_icons_voice),
            android.R.drawable.ic_menu_camera to getString(R.string.main_keyboard_icons_photo),
            android.R.drawable.ic_menu_slideshow to getString(R.string.main_keyboard_icons_video),
        )
        val tint = ContextCompat.getColor(this, android.R.color.white)
        val size = (binding.keyboardIconsHelpText.lineHeight * 0.95f).toInt().coerceAtLeast(24)
        return SpannableStringBuilder().apply {
            rows.forEachIndexed { index, (iconRes, label) ->
                val start = length
                append("  ")
                AppCompatResources.getDrawable(this@MainActivity, iconRes)?.mutate()?.let { drawable ->
                    drawable.setTint(tint)
                    drawable.setBounds(0, 0, size, size)
                    setSpan(ImageSpan(drawable, ImageSpan.ALIGN_BOTTOM), start, start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                append(" ")
                append(label)
                if (index != rows.lastIndex) append('\n')
            }
        }
    }

    private fun languageLabel(tag: String): String {
        val locale = Locale.forLanguageTag(tag.ifBlank { "en" })
        return locale.getDisplayLanguage(locale).replaceFirstChar { character ->
            if (character.isLowerCase()) {
                character.titlecase(locale)
            } else {
                character.toString()
            }
        }
    }

    private fun resolveAppLanguageLabel(): String {
        val currentTag = AppCompatDelegate.getApplicationLocales()[0]?.toLanguageTag().orEmpty().ifBlank { "en" }
        return languageLabel(currentTag)
    }

    private fun resolveKeyboardLanguagesLabel(): String {
        val enabled = KeyboardLanguagePreferences.SUPPORTED_LANGUAGE_TAGS
            .filter { it in keyboardLanguagePreferences.getEnabledLanguageTags() }
        return when (enabled.size) {
            0 -> "English"
            1 -> languageLabel(enabled.first())
            2 -> enabled.joinToString(" / ") { languageLabel(it) }
            else -> "${languageLabel(enabled.first())} +${enabled.size - 1}"
        }
    }

    private fun shareModeLabel(mode: ShareInviteMode): String = when (mode) {
        ShareInviteMode.VIRAL -> getString(R.string.main_share_mode_viral)
        ShareInviteMode.BALANCED -> getString(R.string.main_share_mode_balanced)
        ShareInviteMode.MINIMAL -> getString(R.string.main_share_mode_minimal)
    }

    private fun keyboardThemeLabel(preset: KeyboardAppearancePreferences.ThemePreset): String = when (preset) {
        KeyboardAppearancePreferences.ThemePreset.MIDNIGHT -> getString(R.string.main_keyboard_theme_midnight)
        KeyboardAppearancePreferences.ThemePreset.OCEAN -> getString(R.string.main_keyboard_theme_ocean)
        KeyboardAppearancePreferences.ThemePreset.GRAPHITE -> getString(R.string.main_keyboard_theme_graphite)
    }

    private fun keyboardShapeLabel(preset: KeyboardAppearancePreferences.KeyShapePreset): String = when (preset) {
        KeyboardAppearancePreferences.KeyShapePreset.ROUNDED -> getString(R.string.main_keyboard_shape_rounded)
        KeyboardAppearancePreferences.KeyShapePreset.FULL_SQUARE -> getString(R.string.main_keyboard_shape_full_square)
        KeyboardAppearancePreferences.KeyShapePreset.SPACED_SQUARE -> getString(R.string.main_keyboard_shape_spaced_square)
        KeyboardAppearancePreferences.KeyShapePreset.SPACED_ROUNDED -> getString(R.string.main_keyboard_shape_spaced_rounded)
    }

    private fun keyboardHeightLabel(preset: KeyboardAppearancePreferences.HeightPreset): String = when (preset) {
        KeyboardAppearancePreferences.HeightPreset.AUTO -> getString(R.string.main_keyboard_height_auto)
        KeyboardAppearancePreferences.HeightPreset.COMPACT -> getString(R.string.main_keyboard_height_compact)
        KeyboardAppearancePreferences.HeightPreset.NORMAL -> getString(R.string.main_keyboard_height_normal)
        KeyboardAppearancePreferences.HeightPreset.TALL -> getString(R.string.main_keyboard_height_tall)
    }

    private fun resolveKeyboardState(): KeyboardState {
        return runCatching {
            val packageName = applicationContext.packageName
            val serviceId = "$packageName/.ime.EnigmaKeyboardService"

            val enabledInputMethods = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_INPUT_METHODS,
            ).orEmpty()
            val defaultInputMethod = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.DEFAULT_INPUT_METHOD,
            ).orEmpty()

            val isEnabled = enabledInputMethods.contains(serviceId)
            val isSelected = defaultInputMethod == serviceId

            val labelRes = when {
                isEnabled && isSelected -> R.string.main_keyboard_state_enabled_selected
                isEnabled -> R.string.main_keyboard_state_enabled_only
                else -> R.string.main_keyboard_state_disabled
            }
            KeyboardState(
                enabled = isEnabled,
                selected = isSelected,
                label = getString(labelRes),
            )
        }.getOrElse {
            KeyboardState(
                enabled = false,
                selected = false,
                label = getString(R.string.main_keyboard_state_error),
            )
        }
    }

    private fun resolveSetupStage(
        keyboardState: KeyboardState = resolveKeyboardState(),
        hasProfiles: Boolean = secureProfileStore.listProfiles().isNotEmpty(),
    ): SetupStage {
        return when {
            !keyboardState.enabled -> SetupStage.ENABLE_KEYBOARD
            !hasProfiles -> SetupStage.CREATE_KEY
            !keyboardState.selected -> SetupStage.SELECT_KEYBOARD
            else -> SetupStage.READY
        }
    }
}
