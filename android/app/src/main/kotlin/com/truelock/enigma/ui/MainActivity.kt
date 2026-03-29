package com.truelock.enigma.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.truelock.enigma.R
import com.truelock.enigma.crypto.Tl1MessageCodec
import com.truelock.enigma.databinding.ActivityMainBinding
import com.truelock.enigma.storage.FileKeyProfileRepository
import com.truelock.enigma.storage.ProfileKeyVault
import com.truelock.enigma.storage.SecureProfileStore
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var secureProfileStore: SecureProfileStore
    private val codec = Tl1MessageCodec()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        secureProfileStore = SecureProfileStore(
            repository = FileKeyProfileRepository(applicationContext),
            keyVault = ProfileKeyVault(),
        )

        binding.openProfileManagerButton.setOnClickListener {
            startActivity(Intent(this, ProfileManagerActivity::class.java))
        }
        binding.openKeyExchangeButton.setOnClickListener {
            startActivity(Intent(this, KeyExchangeActivity::class.java))
        }
        binding.openAudioCapsuleButton.setOnClickListener {
            startActivity(Intent(this, AudioCapsuleActivity::class.java))
        }
        binding.openVideoCapsuleButton.setOnClickListener {
            startActivity(Intent(this, VideoCapsuleActivity::class.java))
        }
        binding.openInputMethodSettingsButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        binding.showInputMethodPickerButton.setOnClickListener {
            runCatching {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
            }
        }
        binding.copyDemoMessageButton.setOnClickListener {
            copyDemoMessage()
        }
        binding.languageButton.setOnClickListener {
            showLanguagePicker()
        }

        renderMainState(getString(R.string.main_status_ready))
    }

    override fun onResume() {
        super.onResume()
        renderMainState(getString(R.string.main_status_ready))
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
        clipboard.setPrimaryClip(
            ClipData.newPlainText(getString(R.string.main_clipboard_label_tl1), ciphertext.encodedMessage),
        )
        renderMainState(getString(R.string.main_status_demo_copied))
    }

    private fun renderMainState(status: String) {
        val profiles = secureProfileStore.listProfiles()
        binding.profileCountText.text = getString(R.string.profile_count_format, profiles.size)
        binding.debugStatusText.text = status
        binding.languageButton.text = "🌐 ${resolveCurrentLanguageLabel()}"
        binding.keyboardStateText.text = getString(
            R.string.main_keyboard_state_format,
            resolveKeyboardStateLabel(),
        )
    }

    private fun showLanguagePicker() {
        val items = supportedLanguages.map { it.label }.toTypedArray()
        val currentIndex = supportedLanguages.indexOfFirst { it.tag == resolveCurrentLanguageTag() }
            .takeIf { it >= 0 }
            ?: 0

        AlertDialog.Builder(this)
            .setSingleChoiceItems(items, currentIndex) { dialog, which ->
                val selected = supportedLanguages[which]
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(selected.tag))
                dialog.dismiss()
            }
            .show()
    }

    private fun resolveCurrentLanguageTag(): String {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        val stored = appLocales[0]?.toLanguageTag()
        if (!stored.isNullOrBlank()) {
            return stored
        }
        return resources.configuration.locales[0]?.toLanguageTag().orEmpty()
    }

    private fun resolveCurrentLanguageLabel(): String {
        val tag = resolveCurrentLanguageTag()
        return supportedLanguages.firstOrNull { it.tag == tag }?.label ?: run {
            val locale = Locale.forLanguageTag(tag.ifBlank { "en" })
            locale.getDisplayLanguage(locale).replaceFirstChar { character ->
                if (character.isLowerCase()) {
                    character.titlecase(locale)
                } else {
                    character.toString()
                }
            }
        }
    }

    private data class SupportedLanguage(
        val tag: String,
        val label: String,
    )

    private companion object {
        val supportedLanguages = listOf(
            SupportedLanguage("ru", "Русский"),
            SupportedLanguage("en", "English"),
            SupportedLanguage("de", "Deutsch"),
            SupportedLanguage("es", "Español"),
            SupportedLanguage("fr", "Français"),
            SupportedLanguage("it", "Italiano"),
            SupportedLanguage("pt", "Português"),
            SupportedLanguage("tr", "Türkçe"),
        )
    }

    private fun resolveKeyboardStateLabel(): String {
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
            getString(labelRes)
        }.getOrElse {
            getString(R.string.main_keyboard_state_error)
        }
    }
}
