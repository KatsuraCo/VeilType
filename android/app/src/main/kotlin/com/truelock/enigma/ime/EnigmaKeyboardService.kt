package com.truelock.enigma.ime

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.res.ColorStateList
import android.Manifest
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.TypedValue
import android.view.MotionEvent
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.EditorInfo
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Button
import android.widget.FrameLayout
import android.widget.PopupWindow
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.content.FileProvider
import androidx.core.os.LocaleListCompat
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import com.truelock.enigma.R
import com.truelock.enigma.clipboard.ClipboardDecryptResult
import com.truelock.enigma.clipboard.ClipboardDecryptService
import com.truelock.enigma.crypto.Tl1MessageCodec
import com.truelock.enigma.crypto.Tl1ShareEnvelope
import com.truelock.enigma.media.MediaCapsuleService
import com.truelock.enigma.media.MediaCapsuleType
import com.truelock.enigma.media.PendingCapsuleStore
import com.truelock.enigma.media.createSpeechMediaRecorder
import com.truelock.enigma.media.describeAudioSource
import com.truelock.enigma.profiles.KeyProfile
import com.truelock.enigma.profiles.KeyProfileStatus
import com.truelock.enigma.profiles.ProfileSelectionPolicy
import com.truelock.enigma.profiles.SecretSequenceKind
import com.truelock.enigma.prediction.KeyboardPredictionEngine
import com.truelock.enigma.settings.KeyboardAppearancePreferences
import com.truelock.enigma.settings.KeyboardLanguagePreferences
import com.truelock.enigma.sharing.ShareInvitePreferences
import com.truelock.enigma.storage.FileKeyProfileRepository
import com.truelock.enigma.storage.ProfileKeyVault
import com.truelock.enigma.storage.SecureProfileStore
import com.truelock.enigma.ui.AudioPermissionRequestActivity
import com.truelock.enigma.ui.AudioCapsuleActivity
import com.truelock.enigma.ui.DecryptGateActivity
import com.truelock.enigma.ui.MainActivity
import com.truelock.enigma.ui.MediaCapsuleRouterActivity
import com.truelock.enigma.ui.PhotoCapsuleActivity
import com.truelock.enigma.ui.VideoCapsuleActivity
import java.util.Locale

class EnigmaKeyboardService : InputMethodService() {
    private enum class KeyboardMode {
        IDLE,
        ENIGMA,
        DECRYPT,
    }

    private enum class KeyboardLanguage(
        val localeTag: String,
        val displayName: String,
    ) {
        RU("ru", "Русский"),
        EN("en", "English"),
        TR("tr", "Türkçe"),
        ES("es", "Español"),
        PT("pt", "Português"),
        DE("de", "Deutsch"),
        FR("fr", "Français"),
        IT("it", "Italiano"),
        ;

        val locale: Locale
            get() = Locale.forLanguageTag(localeTag)

        companion object {
            fun fromStoredValue(value: String?): KeyboardLanguage =
                values().firstOrNull { it.name == value } ?: EN
        }
    }

    private enum class CharacterMode {
        LETTERS,
        SYMBOLS,
        NUMERIC,
    }

    private enum class SymbolPage {
        PRIMARY,
        SECONDARY,
    }

    private enum class PreviewTone {
        DEFAULT,
        SUCCESS,
        ERROR,
        DECRYPTED,
    }

    private enum class KeyboardHeightProfile {
        COMPACT,
        NORMAL,
        TALL,
    }

    private var mode: KeyboardMode = KeyboardMode.IDLE
    private var currentLanguage: KeyboardLanguage = KeyboardLanguage.EN
    private var lastKeyboardLanguage: KeyboardLanguage = KeyboardLanguage.RU
    private var characterMode: CharacterMode = CharacterMode.LETTERS
    private var currentSymbolPage: SymbolPage = SymbolPage.PRIMARY
    private var shiftEnabled: Boolean = false
    private var capsLockEnabled: Boolean = false
    private var previewMessage: String? = null
    private var previewTone: PreviewTone = PreviewTone.DEFAULT
    private var selectedProfileId: String? = null
    private var decryptResultReceiverRegistered = false
    private val repeatHandler = Handler(Looper.getMainLooper())
    private var lastSpaceTapAt: Long = 0L
    private var lastShiftTapAt: Long = 0L
    private var recentWords: List<String> = emptyList()
    private var renderInputView: (() -> Unit)? = null
    private var applyKeyboardAppearanceToInputView: (() -> Unit)? = null
    private var refreshPendingVideoCapsuleState: (() -> Unit)? = null
    private var rebuildingInputView = false
    private var knownPackageUpdateTime = 0L
    private val predictionLexiconCache = mutableMapOf<KeyboardLanguage, List<String>>()
    private val predictionNextWordCache = mutableMapOf<KeyboardLanguage, Map<String, List<String>>>()
    private val predictionEngineCache = mutableMapOf<KeyboardLanguage, KeyboardPredictionEngine>()
    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private val pendingCapsuleStore by lazy { PendingCapsuleStore(applicationContext) }
    private var lastAudioCapsuleFile: java.io.File? = null
    private var lastAudioPlaybackFile: java.io.File? = null
    private var lastAudioPlaybackCapsulePath: String? = null
    private var lastAudioCapsuleNeedsManualSend: Boolean = false
    private var inlineAudioRecorder: MediaRecorder? = null
    private var inlineAudioRecordingPaused: Boolean = false
    private var inlineAudioPausedAt: Long = 0L
    private var inlineAudioPausedDurationMs: Long = 0L
    private var inlineAudioPlayer: MediaPlayer? = null
    private var inlineAudioSourceFile: java.io.File? = null
    private var inlineAudioStartedAt = 0L
    private var lastVideoCapsuleFile: java.io.File? = null
    private var lastVideoCapsuleNeedsManualSend: Boolean = false
    private var lastVideoCapsuleReadyForDirectInsert: Boolean = false
    private var lastPhotoCapsuleFile: java.io.File? = null
    private var lastPhotoCapsuleNeedsManualSend: Boolean = false
    private val decryptResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != DecryptGateActivity.ACTION_DECRYPT_RESULT) return
            val success = intent.getBooleanExtra(DecryptGateActivity.EXTRA_SUCCESS, false)
            if (success) {
                val plaintext = intent.getStringExtra(DecryptGateActivity.EXTRA_PLAINTEXT).orEmpty()
                val profileTitle = intent.getStringExtra(DecryptGateActivity.EXTRA_PROFILE_TITLE)
                    .orEmpty()
                    .ifBlank { getString(R.string.clipboard_unknown_profile) }
                mode = KeyboardMode.DECRYPT
                previewMessage = getString(R.string.keyboard_preview_decrypt_success, profileTitle, plaintext)
                previewTone = PreviewTone.DECRYPTED
            } else {
                mode = KeyboardMode.DECRYPT
                previewMessage =
                    intent.getStringExtra(DecryptGateActivity.EXTRA_ERROR_MESSAGE)
                        ?: getString(R.string.keyboard_decrypt_invalid)
                previewTone = PreviewTone.ERROR
            }
            renderInputView?.invoke()
        }
    }
    private val shareInvitePreferences by lazy { ShareInvitePreferences(applicationContext) }
    private val keyboardLanguagePreferences by lazy { KeyboardLanguagePreferences(applicationContext) }
    private val keyboardAppearancePreferences by lazy { KeyboardAppearancePreferences(applicationContext) }
    private val liveSettingsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "keyboard_theme_preset",
                "keyboard_key_shape_preset",
                "keyboard_height_preset",
                -> {
                    if (!rebuildInputViewIfShown()) {
                        applyKeyboardAppearanceToInputView?.invoke()
                        renderInputView?.invoke()
                    }
                }
                "enabled_keyboard_languages" -> {
                    refreshEnabledKeyboardLanguageState()
                    if (!rebuildInputViewIfShown()) {
                        renderInputView?.invoke()
                    }
                }
            }
        }

    private fun keyboardString(resId: Int, vararg args: Any): String {
        val resourcesForLocale = applicationContext.createConfigurationContext(
            Configuration(applicationContext.resources.configuration).apply {
                setLocale(currentLanguage.locale)
            },
        ).resources
        return if (args.isEmpty()) {
            resourcesForLocale.getString(resId)
        } else {
            resourcesForLocale.getString(resId, *args)
        }
    }


    private val row1Ids = intArrayOf(
        R.id.keyR1C1, R.id.keyR1C2, R.id.keyR1C3, R.id.keyR1C4, R.id.keyR1C5, R.id.keyR1C6,
        R.id.keyR1C7, R.id.keyR1C8, R.id.keyR1C9, R.id.keyR1C10, R.id.keyR1C11, R.id.keyR1C12,
    )
    private val row2Ids = intArrayOf(
        R.id.keyR2C1, R.id.keyR2C2, R.id.keyR2C3, R.id.keyR2C4, R.id.keyR2C5, R.id.keyR2C6,
        R.id.keyR2C7, R.id.keyR2C8, R.id.keyR2C9, R.id.keyR2C10, R.id.keyR2C11,
    )
    private val row3CharIds = intArrayOf(
        R.id.keyR3C2, R.id.keyR3C3, R.id.keyR3C4, R.id.keyR3C5,
        R.id.keyR3C6, R.id.keyR3C7, R.id.keyR3C8, R.id.keyR3C9, R.id.keyR3C10,
    )
    private val numberRowIds = intArrayOf(
        R.id.keyN1, R.id.keyN2, R.id.keyN3, R.id.keyN4, R.id.keyN5,
        R.id.keyN6, R.id.keyN7, R.id.keyN8, R.id.keyN9, R.id.keyN0,
    )

    private val ruRows = listOf(
        listOf("\u0439", "\u0446", "\u0443", "\u043a", "\u0435", "\u043d", "\u0433", "\u0448", "\u0449", "\u0437", "\u0445", "\u044a"),
        listOf("\u0444", "\u044b", "\u0432", "\u0430", "\u043f", "\u0440", "\u043e", "\u043b", "\u0434", "\u0436", "\u044d"),
        listOf("\u044f", "\u0447", "\u0441", "\u043c", "\u0438", "\u0442", "\u044c", "\u0431", "\u044e"),
    )

    private val enRows = listOf(
        listOf("", "q", "w", "e", "r", "t", "y", "u", "i", "o", "p", ""),
        listOf("", "a", "s", "d", "f", "g", "h", "j", "k", "l", ""),
        listOf("", "z", "x", "c", "v", "b", "n", "m", ""),
    )

    private val trRows = listOf(
        listOf("q", "w", "e", "r", "t", "y", "u", "ı", "o", "p", "ğ", "ü"),
        listOf("a", "s", "d", "f", "g", "h", "j", "k", "l", "ş", "i"),
        listOf("z", "x", "c", "v", "b", "n", "m", "ö", "ç"),
    )

    private val esRows = listOf(
        listOf("", "q", "w", "e", "r", "t", "y", "u", "i", "o", "p", ""),
        listOf("", "a", "s", "d", "f", "g", "h", "j", "k", "l", "ñ"),
        listOf("", "z", "x", "c", "v", "b", "n", "m", ""),
    )

    private val ptRows = listOf(
        listOf("", "q", "w", "e", "r", "t", "y", "u", "i", "o", "p", ""),
        listOf("", "a", "s", "d", "f", "g", "h", "j", "k", "l", "ç"),
        listOf("", "z", "x", "c", "v", "b", "n", "m", ""),
    )

    private val deRows = listOf(
        listOf("q", "w", "e", "r", "t", "z", "u", "i", "o", "p", "ü", "ß"),
        listOf("a", "s", "d", "f", "g", "h", "j", "k", "l", "ö", "ä"),
        listOf("y", "x", "c", "v", "b", "n", "m"),
    )

    private val frRows = listOf(
        listOf("a", "z", "e", "r", "t", "y", "u", "i", "o", "p", "", ""),
        listOf("q", "s", "d", "f", "g", "h", "j", "k", "l", "m", ""),
        listOf("w", "x", "c", "v", "b", "n", "ç", ""),
    )

    private val itRows = listOf(
        listOf("", "q", "w", "e", "r", "t", "y", "u", "i", "o", "p", ""),
        listOf("", "a", "s", "d", "f", "g", "h", "j", "k", "l", ""),
        listOf("", "z", "x", "c", "v", "b", "n", "m", ""),
    )

    private val symbolRows = listOf(
        listOf("@", "#", "$", "%", "&", "*", "(", ")", "-", "=", "_", "+"),
        listOf("/", "\\", ":", ";", "\"", "'", "[", "]", "<", ">", "?"),
        listOf("!", "~", "`", "|", "{", "}", "^", ",", "."),
    )

    private val symbolRowsExtra = listOf(
        listOf("~", "`", "|", "{", "}", "<", ">", "\\", "^", "+", "=", "_"),
        listOf("/", "*", "\"", "'", ":", ";", "(", ")", "[", "]", "@"),
        listOf("!", "?", "#", "$", "%", "&", "-", ",", "."),
    )

    private val symbolRowsAlt = listOf(
        listOf("~", "`", "|", "•", "√", "π", "÷", "×", "{", "}", "\\", "§"),
        listOf("^", "°", "€", "£", "¥", "©", "®", "<", ">", "№", "₽"),
        listOf("…", "—", "–", "!", "?", "%", "&", "*", "="),
    )

    private val numericRows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
    )

    private data class KeyboardPalette(
        val rootStart: String,
        val rootEnd: String,
        val card: String,
        val cardAlt: String,
        val charStart: String,
        val charEnd: String,
        val charStroke: String,
        val utilityStart: String,
        val utilityEnd: String,
        val utilityStroke: String,
        val textPrimary: String,
        val textSecondary: String,
        val primaryButtonStart: String,
        val primaryButtonEnd: String,
        val primaryButtonText: String,
    )

    private fun resolveKeyboardPalette(theme: KeyboardAppearancePreferences.ThemePreset): KeyboardPalette =
        when (theme) {
            KeyboardAppearancePreferences.ThemePreset.MIDNIGHT ->
                KeyboardPalette(
                    rootStart = "#09101C",
                    rootEnd = "#101D31",
                    card = "#13202B",
                    cardAlt = "#18283A",
                    charStart = "#22344A",
                    charEnd = "#111B28",
                    charStroke = "#D7B76F",
                    utilityStart = "#223449",
                    utilityEnd = "#142030",
                    utilityStroke = "#8CC7FF",
                    textPrimary = "#F6F8FC",
                    textSecondary = "#D7E2EE",
                    primaryButtonStart = "#E6C577",
                    primaryButtonEnd = "#F0D28E",
                    primaryButtonText = "#102030",
                )
            KeyboardAppearancePreferences.ThemePreset.OCEAN ->
                KeyboardPalette(
                    rootStart = "#071923",
                    rootEnd = "#0E2B39",
                    card = "#0F2634",
                    cardAlt = "#123445",
                    charStart = "#1C5366",
                    charEnd = "#123342",
                    charStroke = "#86DBF6",
                    utilityStart = "#174254",
                    utilityEnd = "#102B38",
                    utilityStroke = "#9DEFFF",
                    textPrimary = "#F2FBFF",
                    textSecondary = "#CBEAF4",
                    primaryButtonStart = "#7BE3F1",
                    primaryButtonEnd = "#B1F3F8",
                    primaryButtonText = "#07202C",
                )
            KeyboardAppearancePreferences.ThemePreset.GRAPHITE ->
                KeyboardPalette(
                    rootStart = "#101216",
                    rootEnd = "#1A1E26",
                    card = "#1C212B",
                    cardAlt = "#242A35",
                    charStart = "#3A4250",
                    charEnd = "#232933",
                    charStroke = "#C9D0DA",
                    utilityStart = "#313947",
                    utilityEnd = "#1D232D",
                    utilityStroke = "#A9B5C7",
                    textPrimary = "#F5F7FA",
                    textSecondary = "#D6DCE6",
                    primaryButtonStart = "#D9E0EA",
                    primaryButtonEnd = "#F4F7FA",
                    primaryButtonText = "#12161D",
                )
        }

    private fun createKeyboardStateDrawable(
        startColor: String,
        endColor: String,
        strokeColor: String,
        radiusDp: Int,
        pressedOverlay: Int,
    ): StateListDrawable {
        fun shape(multiplier: Float): GradientDrawable =
            GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    blendColor(Color.parseColor(startColor), pressedOverlay, multiplier),
                    blendColor(Color.parseColor(endColor), pressedOverlay, multiplier),
                ),
            ).apply {
                cornerRadius = dpFloat(radiusDp.toFloat())
                setStroke(dpFloat(1f).toInt(), Color.parseColor(strokeColor))
            }

        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), shape(0.14f))
            addState(intArrayOf(android.R.attr.state_selected), shape(0.1f))
            addState(intArrayOf(), shape(0f))
        }
    }

    private fun createKeyboardFillDrawable(
        startColor: String,
        endColor: String,
        radiusDp: Int,
    ): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor(startColor), Color.parseColor(endColor)),
        ).apply {
            cornerRadius = dpFloat(radiusDp.toFloat())
        }

    private fun blendColor(baseColor: Int, overlayColor: Int, ratio: Float): Int {
        val inverse = 1f - ratio
        return Color.argb(
            (Color.alpha(baseColor) * inverse + Color.alpha(overlayColor) * ratio).toInt(),
            (Color.red(baseColor) * inverse + Color.red(overlayColor) * ratio).toInt(),
            (Color.green(baseColor) * inverse + Color.green(overlayColor) * ratio).toInt(),
            (Color.blue(baseColor) * inverse + Color.blue(overlayColor) * ratio).toInt(),
        )
    }

    private fun dpFloat(value: Float): Float =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            resources.displayMetrics,
        )

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()
        knownPackageUpdateTime = packageUpdateTime()
        getSharedPreferences("veiltype_preferences", Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(liveSettingsListener)
        if (!decryptResultReceiverRegistered) {
            val filter = IntentFilter(DecryptGateActivity.ACTION_DECRYPT_RESULT)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(decryptResultReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(decryptResultReceiver, filter)
            }
            decryptResultReceiverRegistered = true
        }
    }

    override fun onDestroy() {
        getSharedPreferences("veiltype_preferences", Context.MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(liveSettingsListener)
        if (decryptResultReceiverRegistered) {
            unregisterReceiver(decryptResultReceiver)
            decryptResultReceiverRegistered = false
        }
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        return try {
            currentLanguage = loadKeyboardLanguagePreference()
            lastKeyboardLanguage = loadLastKeyboardLanguagePreference(currentLanguage)
            refreshEnabledKeyboardLanguageState()
            if (recentWords.isEmpty()) {
                recentWords = loadRecentWords()
            }
            val root = LayoutInflater.from(this).inflate(R.layout.input_view, FrameLayout(this), false)
            val keyboardSurface = root.findViewById<LinearLayout>(R.id.keyboardSurface)
            val basePaddingBottom = root.paddingBottom
            var currentNavigationInsetBottom = 0
            ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
                val navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
                currentNavigationInsetBottom = navigationBars.bottom
                view.setPadding(
                    view.paddingLeft,
                    view.paddingTop,
                    view.paddingRight,
                    basePaddingBottom + currentNavigationInsetBottom,
                )
                insets
            }
            root.doOnAttachRequestInsets()
            val numberRowLayout = root.findViewById<LinearLayout>(R.id.keyNumberRow)
            val row1Layout = root.findViewById<LinearLayout>(R.id.keyRow1)
            val row2Layout = root.findViewById<LinearLayout>(R.id.keyRow2)
            val row3Layout = root.findViewById<LinearLayout>(R.id.keyRow3)
            val row4Layout = root.findViewById<LinearLayout>(R.id.keyRow4)
            val statusText = root.findViewById<TextView>(R.id.statusText)
            val profileInfoText = root.findViewById<TextView>(R.id.profileInfoText)
            val previewText = root.findViewById<TextView>(R.id.previewText)
            val previewScroll = root.findViewById<ScrollView>(R.id.previewScroll)
            val audioRecordingPanel = root.findViewById<LinearLayout>(R.id.audioRecordingPanel)
            val audioRecordingText = root.findViewById<TextView>(R.id.audioRecordingText)
            val audioRecordingPauseButton = root.findViewById<Button>(R.id.audioRecordingPauseButton)
            val audioRecordingStopButton = root.findViewById<Button>(R.id.audioRecordingStopButton)
            val audioCapsuleActionPanel = root.findViewById<LinearLayout>(R.id.audioCapsuleActionPanel)
            val audioCapsuleActionText = root.findViewById<TextView>(R.id.audioCapsuleActionText)
            val playAudioCapsuleActionButton = root.findViewById<ImageButton>(R.id.playAudioCapsuleActionButton)
            val deleteCapsuleActionButton = root.findViewById<Button>(R.id.deleteCapsuleActionButton)
            val sendAudioCapsuleActionButton = root.findViewById<Button>(R.id.sendAudioCapsuleActionButton)
            val actionBarContainer = root.findViewById<FrameLayout>(R.id.actionBarContainer)
            val mainActionRow = root.findViewById<LinearLayout>(R.id.mainActionRow)
            val attachActionRow = root.findViewById<LinearLayout>(R.id.attachActionRow)
            val suggestionRow = root.findViewById<LinearLayout>(R.id.suggestionRow)
            val suggestionButtons = listOf(
                root.findViewById<EnigmaKeyView>(R.id.suggestionButton1),
                root.findViewById<EnigmaKeyView>(R.id.suggestionButton2),
                root.findViewById<EnigmaKeyView>(R.id.suggestionButton3),
            )
            val enigmaToggleButton = root.findViewById<ImageButton>(R.id.enigmaToggleButton)
            val decryptButton = root.findViewById<ImageButton>(R.id.decryptButton)
            val attachToggleButton = root.findViewById<ImageButton>(R.id.attachToggleButton)
            val attachBackButton = root.findViewById<ImageButton>(R.id.attachBackButton)
            val photoCapsuleButton = root.findViewById<ImageButton>(R.id.photoCapsuleButton)
            val clearButton = root.findViewById<ImageButton>(R.id.clearButton)
            val audioCapsuleButton = root.findViewById<ImageButton>(R.id.audioCapsuleButton)
            val videoCapsuleButton = root.findViewById<ImageButton>(R.id.videoCapsuleButton)
            val numberButtons = numberRowIds.map { root.findViewById<EnigmaKeyView>(it) }
            val numericPadContainer = root.findViewById<LinearLayout>(R.id.numericPadContainer)
            val languageToggleButton = root.findViewById<EnigmaKeyView>(R.id.languageToggleButton)
            val commaButton = root.findViewById<EnigmaKeyView>(R.id.commaButton)
            val dotButton = root.findViewById<EnigmaKeyView>(R.id.dotButton)
            val spaceButton = root.findViewById<EnigmaKeyView>(R.id.spaceButton)
            val symbolsToggleButton = root.findViewById<EnigmaKeyView>(R.id.symbolsToggleButton)
            val shiftButton = root.findViewById<EnigmaKeyView>(R.id.shiftButton)
            val backspaceButton = root.findViewById<EnigmaKeyView>(R.id.backspaceButton)
            val enterButton = root.findViewById<EnigmaKeyView>(R.id.enterButton)
            val numericDeleteButton = root.findViewById<EnigmaKeyView>(R.id.numericDeleteButton)
            val numericEnterButton = root.findViewById<EnigmaKeyView>(R.id.numericEnterButton)
            val numericMinusButton = root.findViewById<EnigmaKeyView>(R.id.numericMinusButton)
            val numericLettersButton = root.findViewById<EnigmaKeyView>(R.id.numericLettersButton)
            val numericCommaButton = root.findViewById<EnigmaKeyView>(R.id.numericCommaButton)
            val numericDotButton = root.findViewById<EnigmaKeyView>(R.id.numericDotButton)
            val numericDigitButtons = listOf(
                root.findViewById<EnigmaKeyView>(R.id.numericKey1),
                root.findViewById<EnigmaKeyView>(R.id.numericKey2),
                root.findViewById<EnigmaKeyView>(R.id.numericKey3),
                root.findViewById<EnigmaKeyView>(R.id.numericKey4),
                root.findViewById<EnigmaKeyView>(R.id.numericKey5),
                root.findViewById<EnigmaKeyView>(R.id.numericKey6),
                root.findViewById<EnigmaKeyView>(R.id.numericKey7),
                root.findViewById<EnigmaKeyView>(R.id.numericKey8),
                root.findViewById<EnigmaKeyView>(R.id.numericKey9),
                root.findViewById<EnigmaKeyView>(R.id.numericKey0),
            )
        val numericPadButtons =
            numericDigitButtons +
                listOf(
                    numericDeleteButton,
                    numericEnterButton,
                    numericMinusButton,
                    numericLettersButton,
                    numericCommaButton,
                    numericDotButton,
                )
        val secureProfileStore = SecureProfileStore(
            repository = FileKeyProfileRepository(applicationContext),
            keyVault = ProfileKeyVault(),
        )
        val decryptService = ClipboardDecryptService(
            context = applicationContext,
            clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager,
            secureProfileStore = secureProfileStore,
            codec = Tl1MessageCodec(),
        )
        val codec = Tl1MessageCodec()
        val mediaCapsuleService = MediaCapsuleService(applicationContext, secureProfileStore)
        var activeKeyPopup: PopupWindow? = null
        var pressedKeyPreviewPopup: PopupWindow? = null

        val rowButtons = listOf(
            row1Ids.map { root.findViewById<EnigmaKeyView>(it) },
            row2Ids.map { root.findViewById<EnigmaKeyView>(it) },
            row3CharIds.map { root.findViewById<EnigmaKeyView>(it) },
        )
        val utilityButtons = listOf(
            languageToggleButton,
            commaButton,
            dotButton,
            spaceButton,
            symbolsToggleButton,
            shiftButton,
            backspaceButton,
            enterButton,
        )
        var attachActionsExpanded = false
        var currentHeightProfile: KeyboardHeightProfile? = null
        lateinit var render: () -> Unit

        fun setAttachActionsExpanded(expanded: Boolean) {
            attachActionsExpanded = false
            mainActionRow.animate().cancel()
            attachActionRow.animate().cancel()
            mainActionRow.visibility = View.VISIBLE
            mainActionRow.alpha = 1f
            mainActionRow.translationX = 0f
            attachActionRow.visibility = View.GONE
            attachActionRow.alpha = 1f
            attachActionRow.translationX = 0f
        }

        fun installActionRowSwipe(view: View) {
            var downX = 0f
            var downY = 0f
            var swipeHandled = false
            val threshold = dpFloat(18f)
            view.isClickable = true
            view.setOnTouchListener { touchedView, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX
                        downY = event.rawY
                        swipeHandled = false
                        touchedView.parent?.requestDisallowInterceptTouchEvent(true)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - downX
                        val dy = event.rawY - downY
                        if (!swipeHandled && kotlin.math.abs(dx) > threshold && kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                            swipeHandled = true
                            setAttachActionsExpanded(dx < 0)
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val dx = event.rawX - downX
                        val dy = event.rawY - downY
                        if (!swipeHandled && kotlin.math.abs(dx) > threshold && kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                            setAttachActionsExpanded(dx < 0)
                        } else if (!swipeHandled) {
                            touchedView.performClick()
                        }
                        touchedView.parent?.requestDisallowInterceptTouchEvent(false)
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        touchedView.parent?.requestDisallowInterceptTouchEvent(false)
                        true
                    }
                    else -> true
                }
            }
        }

        fun setExactHeight(view: View, heightDp: Int) {
            val params = view.layoutParams ?: return
            val heightPx = dpFloat(heightDp.toFloat()).toInt()
            if (params.height != heightPx) {
                params.height = heightPx
                view.layoutParams = params
            }
        }

        fun setExactHeightPx(view: View, heightPx: Int) {
            val params = view.layoutParams ?: return
            if (params.height != heightPx) {
                params.height = heightPx
                view.layoutParams = params
            }
        }

        fun setVerticalMargins(view: View, topDp: Int? = null, bottomDp: Int? = null) {
            val params = view.layoutParams as? ViewGroup.MarginLayoutParams ?: return
            var changed = false
            topDp?.let {
                val topPx = dpFloat(it.toFloat()).toInt()
                if (params.topMargin != topPx) {
                    params.topMargin = topPx
                    changed = true
                }
            }
            bottomDp?.let {
                val bottomPx = dpFloat(it.toFloat()).toInt()
                if (params.bottomMargin != bottomPx) {
                    params.bottomMargin = bottomPx
                    changed = true
                }
            }
            if (changed) {
                view.layoutParams = params
            }
        }

        fun setBottomPadding(extraDp: Int) {
            val targetBottom = basePaddingBottom + currentNavigationInsetBottom + dpFloat(extraDp.toFloat()).toInt()
            if (root.paddingBottom != targetBottom) {
                root.setPadding(
                    root.paddingLeft,
                    root.paddingTop,
                    root.paddingRight,
                    targetBottom,
                )
            }
        }

        fun setRowChildHeights(row: LinearLayout, heightDp: Int) {
            for (index in 0 until row.childCount) {
                setExactHeight(row.getChildAt(index), heightDp)
            }
        }

        fun setRowChildHeightsPx(row: LinearLayout, heightPx: Int) {
            for (index in 0 until row.childCount) {
                setExactHeightPx(row.getChildAt(index), heightPx)
            }
        }

        fun applyKeyboardHeightProfile(profile: KeyboardHeightProfile) {
            val targetBottomPadding =
                basePaddingBottom + currentNavigationInsetBottom
            if (currentHeightProfile == profile && root.paddingBottom == targetBottomPadding) {
                return
            }
            currentHeightProfile = profile
            setBottomPadding(0)
            val previewHeightDp = when (profile) {
                KeyboardHeightProfile.COMPACT -> 48
                KeyboardHeightProfile.NORMAL -> 54
                KeyboardHeightProfile.TALL -> 58
            }
            val topMarginDp = when (profile) {
                KeyboardHeightProfile.COMPACT -> 1
                KeyboardHeightProfile.NORMAL -> 2
                KeyboardHeightProfile.TALL -> 3
            }
            val actionRowMarginDp = when (profile) {
                KeyboardHeightProfile.COMPACT -> 2
                KeyboardHeightProfile.NORMAL -> 3
                KeyboardHeightProfile.TALL -> 4
            }
            val topControlHeightDp = when (profile) {
                KeyboardHeightProfile.COMPACT -> 34
                KeyboardHeightProfile.NORMAL -> 38
                KeyboardHeightProfile.TALL -> 42
            }
            val topButtonHeightDp = when (profile) {
                KeyboardHeightProfile.COMPACT -> 32
                KeyboardHeightProfile.NORMAL -> 36
                KeyboardHeightProfile.TALL -> 40
            }
            val rowHeightsDp = when (profile) {
                KeyboardHeightProfile.COMPACT -> listOf(26, 32, 32, 31, 31)
                KeyboardHeightProfile.NORMAL -> listOf(29, 37, 37, 36, 36)
                KeyboardHeightProfile.TALL -> listOf(32, 42, 42, 41, 41)
            }
            setExactHeight(previewScroll, previewHeightDp)
            previewText.minHeight = dpFloat(previewHeightDp.toFloat()).toInt()
            setVerticalMargins(audioRecordingPanel, topDp = topMarginDp)
            setVerticalMargins(audioCapsuleActionPanel, topDp = topMarginDp)
            setVerticalMargins(actionBarContainer, topDp = actionRowMarginDp)
            setExactHeight(audioRecordingText, topButtonHeightDp)
            setExactHeight(audioRecordingPauseButton, topButtonHeightDp)
            setExactHeight(audioRecordingStopButton, topButtonHeightDp)
            setExactHeight(playAudioCapsuleActionButton, topButtonHeightDp)
            setExactHeight(deleteCapsuleActionButton, topButtonHeightDp)
            setExactHeight(sendAudioCapsuleActionButton, topButtonHeightDp)
            setExactHeight(actionBarContainer, topControlHeightDp)
            setExactHeight(mainActionRow, topControlHeightDp)
            setExactHeight(attachActionRow, topControlHeightDp)
            setVerticalMargins(suggestionRow, topDp = actionRowMarginDp)
            setRowChildHeights(suggestionRow, topButtonHeightDp)
            listOf(
                enigmaToggleButton,
                decryptButton,
                clearButton,
                audioCapsuleButton,
                photoCapsuleButton,
                videoCapsuleButton,
            ).forEach { setExactHeight(it, topControlHeightDp) }
            setVerticalMargins(numberRowLayout, topDp = topMarginDp)
            setVerticalMargins(numericPadContainer, topDp = topMarginDp)
            setVerticalMargins(row1Layout, topDp = topMarginDp)
            setVerticalMargins(row2Layout, topDp = topMarginDp)
            setVerticalMargins(row3Layout, topDp = topMarginDp)
            setVerticalMargins(row4Layout, topDp = topMarginDp, bottomDp = topMarginDp)

            setRowChildHeights(numberRowLayout, rowHeightsDp[0])
            setRowChildHeights(numericPadContainer.findViewById(R.id.numericPadRow1), rowHeightsDp[2])
            setRowChildHeights(numericPadContainer.findViewById(R.id.numericPadRow2), rowHeightsDp[2])
            setRowChildHeights(numericPadContainer.findViewById(R.id.numericPadRow3), rowHeightsDp[2])
            setRowChildHeights(numericPadContainer.findViewById(R.id.numericPadRow4), rowHeightsDp[3])
            setRowChildHeights(row1Layout, rowHeightsDp[1])
            setRowChildHeights(row2Layout, rowHeightsDp[2])
            setRowChildHeights(row3Layout, rowHeightsDp[3])
            setRowChildHeights(row4Layout, rowHeightsDp[4])
        }

        fun updateKeyboardHeightProfile() {
            val rootHeight = root.height
            if (rootHeight <= 0) return
            val extraPanelsVisible =
                previewScroll.visibility == View.VISIBLE ||
                    audioRecordingPanel.visibility == View.VISIBLE ||
                    audioCapsuleActionPanel.visibility == View.VISIBLE
            val forcedPreset = keyboardAppearancePreferences.getHeightPreset()
            val profile = when (forcedPreset) {
                KeyboardAppearancePreferences.HeightPreset.AUTO -> {
                    val usableHeightDp =
                        ((rootHeight - root.paddingTop - root.paddingBottom) / resources.displayMetrics.density)
                    when {
                        extraPanelsVisible && usableHeightDp < 300f -> KeyboardHeightProfile.COMPACT
                        usableHeightDp < 250f -> KeyboardHeightProfile.COMPACT
                        usableHeightDp < 335f -> KeyboardHeightProfile.NORMAL
                        else -> KeyboardHeightProfile.TALL
                    }
                }
                KeyboardAppearancePreferences.HeightPreset.COMPACT -> KeyboardHeightProfile.COMPACT
                KeyboardAppearancePreferences.HeightPreset.NORMAL -> KeyboardHeightProfile.NORMAL
                KeyboardAppearancePreferences.HeightPreset.TALL -> KeyboardHeightProfile.TALL
            }
            applyKeyboardHeightProfile(profile)
        }

        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateKeyboardHeightProfile()
        }

        fun applyKeyboardAppearance() {
            val theme = keyboardAppearancePreferences.getThemePreset()
            val shape = keyboardAppearancePreferences.getKeyShapePreset()
            val palette = resolveKeyboardPalette(theme)
            val radiusDp = when (shape) {
                KeyboardAppearancePreferences.KeyShapePreset.ROUNDED -> 14
                KeyboardAppearancePreferences.KeyShapePreset.SPACED_ROUNDED -> 14
                KeyboardAppearancePreferences.KeyShapePreset.FULL_SQUARE -> 3
                KeyboardAppearancePreferences.KeyShapePreset.SPACED_SQUARE -> 3
            }
            val spacingDp = when (shape) {
                KeyboardAppearancePreferences.KeyShapePreset.ROUNDED -> 1
                KeyboardAppearancePreferences.KeyShapePreset.FULL_SQUARE -> 0
                KeyboardAppearancePreferences.KeyShapePreset.SPACED_SQUARE -> 4
                KeyboardAppearancePreferences.KeyShapePreset.SPACED_ROUNDED -> 4
            }

            fun styleMargins(row: LinearLayout) {
                for (index in 0 until row.childCount) {
                    val view = row.getChildAt(index)
                    (view.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
                        params.marginStart = if (index == 0) 0 else dpFloat(spacingDp.toFloat()).toInt()
                        params.topMargin = 0
                        view.layoutParams = params
                    }
                }
            }

            fun styleKey(button: EnigmaKeyView, utility: Boolean) {
                button.background = createKeyboardStateDrawable(
                    startColor = if (utility) palette.utilityStart else palette.charStart,
                    endColor = if (utility) palette.utilityEnd else palette.charEnd,
                    strokeColor = if (utility) palette.utilityStroke else palette.charStroke,
                    radiusDp = radiusDp,
                    pressedOverlay = Color.WHITE,
                )
                button.setTextColor(Color.parseColor(if (utility) palette.textSecondary else palette.textPrimary))
            }

            fun styleIconButton(button: ImageButton) {
                button.background = createKeyboardStateDrawable(
                    startColor = palette.utilityStart,
                    endColor = palette.utilityEnd,
                    strokeColor = palette.utilityStroke,
                    radiusDp = radiusDp,
                    pressedOverlay = Color.WHITE,
                )
                button.imageTintList = ColorStateList.valueOf(Color.parseColor("#9AA7B7"))
            }

            fun styleTextButton(button: Button, primary: Boolean) {
                button.background = createKeyboardStateDrawable(
                    startColor = if (primary) palette.primaryButtonStart else palette.utilityStart,
                    endColor = if (primary) palette.primaryButtonEnd else palette.utilityEnd,
                    strokeColor = if (primary) palette.primaryButtonStart else palette.utilityStroke,
                    radiusDp = radiusDp,
                    pressedOverlay = Color.WHITE,
                )
                button.setTextColor(
                    Color.parseColor(
                        if (primary) palette.primaryButtonText else palette.textPrimary,
                    ),
                )
            }

            keyboardSurface.background = createKeyboardFillDrawable(palette.rootStart, palette.rootEnd, radiusDp + 2)
            previewScroll.background = createKeyboardFillDrawable(palette.card, palette.cardAlt, radiusDp)
            previewText.setTextColor(Color.parseColor(palette.textPrimary))
            audioCapsuleActionPanel.background = createKeyboardFillDrawable(palette.cardAlt, palette.card, radiusDp)
            audioRecordingPanel.background = createKeyboardFillDrawable(palette.cardAlt, palette.card, radiusDp)
            audioRecordingText.background = createKeyboardStateDrawable(
                palette.utilityStart,
                palette.utilityEnd,
                palette.utilityStroke,
                radiusDp,
                Color.WHITE,
            )
            audioRecordingText.setTextColor(Color.parseColor(palette.textPrimary))
            audioCapsuleActionText.background = createKeyboardStateDrawable(
                palette.utilityStart,
                palette.utilityEnd,
                palette.utilityStroke,
                radiusDp,
                Color.WHITE,
            )
            audioCapsuleActionText.setTextColor(Color.parseColor(palette.textPrimary))

            rowButtons.flatten().forEach { styleKey(it, utility = false) }
            numberButtons.forEach { styleKey(it, utility = false) }
            utilityButtons.forEach { styleKey(it, utility = true) }
            numericPadButtons.forEach { styleKey(it, utility = true) }
            suggestionButtons.forEach {
                it.applySuggestionStyle()
                it.background = createKeyboardStateDrawable(
                    startColor = palette.utilityStart,
                    endColor = palette.utilityEnd,
                    strokeColor = palette.utilityStroke,
                    radiusDp = radiusDp,
                    pressedOverlay = Color.WHITE,
                )
                it.setTextColor(Color.parseColor(palette.textPrimary))
            }

            listOf(
                enigmaToggleButton,
                decryptButton,
                clearButton,
                attachToggleButton,
                attachBackButton,
                audioCapsuleButton,
                photoCapsuleButton,
                videoCapsuleButton,
                playAudioCapsuleActionButton,
            ).forEach(::styleIconButton)

            listOf(deleteCapsuleActionButton).forEach { styleTextButton(it, primary = false) }
            listOf(audioRecordingPauseButton, deleteCapsuleActionButton).forEach { styleTextButton(it, primary = false) }
            listOf(sendAudioCapsuleActionButton, audioRecordingStopButton).forEach { styleTextButton(it, primary = true) }

            listOf(root.findViewById<LinearLayout>(R.id.keyNumberRow), suggestionRow, row1Layout, row2Layout, row3Layout).forEach(::styleMargins)
            styleMargins(root.findViewById(R.id.keyRow4))
        }

        fun clearPendingVideoCapsuleState() {
            runCatching { lastVideoCapsuleFile?.takeIf { it.exists() }?.delete() }
            lastVideoCapsuleFile = null
            lastVideoCapsuleNeedsManualSend = false
            lastVideoCapsuleReadyForDirectInsert = false
        }

        fun clearPendingPhotoCapsuleState() {
            runCatching { lastPhotoCapsuleFile?.takeIf { it.exists() }?.delete() }
            lastPhotoCapsuleFile = null
            lastPhotoCapsuleNeedsManualSend = false
        }

        fun clearPendingAudioCapsuleState() {
            inlineAudioPlayer?.release()
            inlineAudioPlayer = null
            runCatching { lastAudioCapsuleFile?.takeIf { it.exists() }?.delete() }
            runCatching {
                if (lastAudioPlaybackFile?.exists() == true) {
                    lastAudioPlaybackFile?.delete()
                }
            }
            lastAudioCapsuleFile = null
            lastAudioPlaybackFile = null
            lastAudioPlaybackCapsulePath = null
            lastAudioCapsuleNeedsManualSend = false
        }

        fun clearPendingCapsulesOnly() {
            clearPendingAudioCapsuleState()
            clearPendingVideoCapsuleState()
            clearPendingPhotoCapsuleState()
            pendingCapsuleStore.clear()
        }

        fun deleteQuietly(file: java.io.File?) {
            runCatching {
                if (file?.exists() == true) {
                    file.delete()
                }
            }
        }

        fun releaseInlineAudioPlayback(deletePlaybackFile: Boolean = true) {
            inlineAudioPlayer?.release()
            inlineAudioPlayer = null
            if (deletePlaybackFile) {
                deleteQuietly(lastAudioPlaybackFile)
                lastAudioPlaybackFile = null
                lastAudioPlaybackCapsulePath = null
            }
        }

        fun refreshPendingCapsule() {
            val pending = pendingCapsuleStore.peek() ?: return
            when (pending.type) {
                MediaCapsuleType.AUDIO -> {
                    if (lastAudioCapsuleFile?.absolutePath == pending.file.absolutePath) {
                        Log.d(TAG, "refreshPendingCapsule audio alreadyLoaded=${pending.file.name}")
                        return
                    }
                    lastAudioCapsuleFile = pending.file
                    Log.d(TAG, "refreshPendingCapsule audio imported=${pending.file.name}")
                    val editorInfo = currentInputEditorInfo
                    val supportedMimeTypes = if (editorInfo == null) {
                        emptyArray()
                    } else {
                        EditorInfoCompat.getContentMimeTypes(editorInfo)
                    }
                    val canInsertDirectly = editorInfo != null &&
                        editorInfo.packageName !in setOf(
                            "org.telegram.messenger",
                            "com.google.android.apps.messaging",
                            "com.whatsapp",
                            "com.whatsapp.w4b",
                        ) &&
                        supportedMimeTypes.any { supportedType ->
                            ClipDescription.compareMimeTypes(MediaCapsuleType.AUDIO.capsuleMimeType, supportedType) ||
                                ClipDescription.compareMimeTypes("application/octet-stream", supportedType)
                    }
                    lastAudioCapsuleNeedsManualSend = !canInsertDirectly
                    previewMessage = keyboardString(R.string.keyboard_voice_capsule_ready_manual)
                    previewTone = PreviewTone.DEFAULT
                    mode = KeyboardMode.IDLE
                }
                MediaCapsuleType.VIDEO -> {
                    if (lastVideoCapsuleFile?.absolutePath == pending.file.absolutePath) {
                        Log.d(TAG, "refreshPendingCapsule video alreadyLoaded=${pending.file.name}")
                        return
                    }
                    clearPendingAudioCapsuleState()
                    lastVideoCapsuleFile = pending.file
                    Log.d(TAG, "refreshPendingCapsule video imported=${pending.file.name}")
                    val editorInfo = currentInputEditorInfo
                    val supportedMimeTypes = if (editorInfo == null) {
                        emptyArray()
                    } else {
                        EditorInfoCompat.getContentMimeTypes(editorInfo)
                    }
                    lastVideoCapsuleReadyForDirectInsert = supportedMimeTypes.any { supportedType ->
                        ClipDescription.compareMimeTypes(MediaCapsuleType.VIDEO.capsuleMimeType, supportedType) ||
                            ClipDescription.compareMimeTypes("application/octet-stream", supportedType)
                    }
                    lastVideoCapsuleNeedsManualSend = !lastVideoCapsuleReadyForDirectInsert
                    previewMessage =
                        if (lastVideoCapsuleReadyForDirectInsert) {
                            keyboardString(R.string.keyboard_video_capsule_ready_insert)
                        } else {
                            keyboardString(R.string.keyboard_video_capsule_ready_manual)
                        }
                    previewTone = PreviewTone.DEFAULT
                    mode = KeyboardMode.IDLE
                }
                MediaCapsuleType.PHOTO -> {
                    if (lastPhotoCapsuleFile?.absolutePath == pending.file.absolutePath) {
                        Log.d(TAG, "refreshPendingCapsule photo alreadyLoaded=${pending.file.name}")
                        return
                    }
                    clearPendingAudioCapsuleState()
                    clearPendingVideoCapsuleState()
                    lastPhotoCapsuleFile = pending.file
                    lastPhotoCapsuleNeedsManualSend = true
                    previewMessage = keyboardString(R.string.photo_capsule_ready_manual)
                    previewTone = PreviewTone.DEFAULT
                    mode = KeyboardMode.IDLE
                }
            }
        }
        refreshPendingVideoCapsuleState = ::refreshPendingCapsule

        fun matchingProfiles(store: SecureProfileStore): List<KeyProfile> {
            return store.listProfiles()
                .filter { it.status == KeyProfileStatus.ACTIVE || it.status == KeyProfileStatus.EXPIRING }
                .sortedBy { it.title.lowercase() }
        }

        fun resolveSelectedProfile(store: SecureProfileStore): KeyProfile? {
            val matches = matchingProfiles(store)
            val manual = matches.firstOrNull { it.id == selectedProfileId }
            return manual ?: ProfileSelectionPolicy.selectDefault(matches)
        }

        fun currentRows(): List<List<String>> = when (characterMode) {
            CharacterMode.SYMBOLS -> when (currentSymbolPage) {
                SymbolPage.PRIMARY -> symbolRows
                SymbolPage.SECONDARY -> symbolRowsExtra
            }
            CharacterMode.NUMERIC -> numericRows
            CharacterMode.LETTERS -> when (currentLanguage) {
                KeyboardLanguage.RU -> ruRows
                KeyboardLanguage.EN -> enRows
                KeyboardLanguage.TR -> trRows
                KeyboardLanguage.ES -> esRows
                KeyboardLanguage.PT -> ptRows
                KeyboardLanguage.DE -> deRows
                KeyboardLanguage.FR -> frRows
                KeyboardLanguage.IT -> itRows
            }
        }

        fun currentLanguageLocale(): Locale = currentLanguage.locale

        fun lowercaseForCurrentLanguage(value: String): String = value.lowercase(currentLanguageLocale())

        fun uppercaseForCurrentLanguage(value: String): String = value.uppercase(currentLanguageLocale())

        fun titlecaseForCurrentLanguage(value: String): String =
            value.replaceFirstChar { char ->
                char.titlecase(currentLanguageLocale())
            }

        fun applyCase(value: String): String {
            if (characterMode != CharacterMode.LETTERS) return value
            return if (shiftEnabled || capsLockEnabled) {
                uppercaseForCurrentLanguage(value)
            } else {
                lowercaseForCurrentLanguage(value)
            }
        }

        fun dp(value: Int): Int =
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value.toFloat(),
                resources.displayMetrics,
            ).toInt()

        fun dismissActiveKeyPopup() {
            activeKeyPopup?.dismiss()
            activeKeyPopup = null
        }

        fun dismissPressedKeyPreview() {
            pressedKeyPreviewPopup?.dismiss()
            pressedKeyPreviewPopup = null
        }

        fun currentLanguageDisplayName(): String = currentLanguage.displayName

        fun appLanguageChoices(): List<Pair<String, String>> =
            KeyboardLanguage.entries.map { it.displayName to it.localeTag }

        fun currentAppLanguageTag(): String {
            val appLocales = AppCompatDelegate.getApplicationLocales()
            val locale = appLocales[0] ?: Locale.ENGLISH
            val tag = locale.toLanguageTag().substringBefore('-')
            return KeyboardLanguage.entries.firstOrNull { it.localeTag == tag }?.localeTag ?: "en"
        }

        fun currentAppLanguageButtonLabel(): String =
            KeyboardLanguage.entries.firstOrNull { it.localeTag == currentAppLanguageTag() }?.displayName?.uppercase()
                ?.take(2)
                ?: "EN"

        fun setAppLanguage(languageTag: String) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag))
        }

        fun cycleAppLanguage() {
            val supported = KeyboardLanguage.entries.map { it.localeTag }
            val currentTag = currentAppLanguageTag()
            val currentIndex = supported.indexOf(currentTag).takeIf { it >= 0 } ?: 0
            val nextTag = supported[(currentIndex + 1) % supported.size]
            setAppLanguage(nextTag)
        }

        fun languageChoices(): List<Pair<String, KeyboardLanguage>> =
            loadEnabledKeyboardLanguages().map { it.displayName to it }

        fun allLanguageChoices(): List<Pair<String, KeyboardLanguage>> =
            KeyboardLanguage.entries.map { it.displayName to it }

        fun ensureKeyboardLanguageEnabled(language: KeyboardLanguage) {
            val enabledTags = keyboardLanguagePreferences.getEnabledLanguageTags().toMutableSet()
            if (language.localeTag !in enabledTags) {
                enabledTags += language.localeTag
                keyboardLanguagePreferences.setEnabledLanguageTags(enabledTags)
            }
        }

        fun showKeyPopup(
            anchor: View,
            labels: List<String>,
            useUtilityStyle: Boolean = false,
            onSelect: (String) -> Unit,
        ) {
            dismissPressedKeyPreview()
            dismissActiveKeyPopup()
            if (labels.isEmpty()) return

            val useVerticalLayout = labels.size > 5 || labels.any { it.length > 6 }

            val container = LinearLayout(this).apply {
                orientation = if (useVerticalLayout) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
                setPadding(dp(6), dp(6), dp(6), dp(6))
                background = AppCompatResources.getDrawable(
                    this@EnigmaKeyboardService,
                    R.drawable.bg_app_card,
                )
            }

            labels.forEachIndexed { index, label ->
                val choiceButton = EnigmaKeyView(this).apply {
                    if (useUtilityStyle) applyUtilityStyle() else applyCharacterStyle()
                    text = label
                    if (label.length > 2) {
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    }
                    minWidth = dp(if (label.length > 2) 68 else 42)
                    setPadding(dp(10), dp(4), dp(10), dp(4))
                    layoutParams = LinearLayout.LayoutParams(
                        if (useVerticalLayout) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT,
                        dp(40),
                    ).apply {
                        if (useVerticalLayout) {
                            if (index > 0) topMargin = dp(4)
                        } else if (index > 0) {
                            marginStart = dp(4)
                        }
                    }
                    setOnClickListener {
                        dismissActiveKeyPopup()
                        onSelect(label)
                    }
                }
                container.addView(choiceButton)
            }

            val popup = PopupWindow(
                container,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true,
            ).apply {
                isOutsideTouchable = true
                setBackgroundDrawable(ColorDrawable(0))
                elevation = dp(8).toFloat()
                setOnDismissListener {
                    if (activeKeyPopup === this) {
                        activeKeyPopup = null
                    }
                }
            }

            val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            container.measure(unspecified, unspecified)
            val popupWidth = container.measuredWidth
            val popupHeight = container.measuredHeight
            val xOffset = (anchor.width - popupWidth) / 2
            val yOffset = -(anchor.height + popupHeight + dp(8))
            popup.showAsDropDown(anchor, xOffset, yOffset)
            activeKeyPopup = popup
        }

        fun showPressedKeyPreview(anchor: View, label: String) {
            dismissPressedKeyPreview()
            if (label.isBlank()) return

            val preview = TextView(this).apply {
                background = AppCompatResources.getDrawable(this@EnigmaKeyboardService, R.drawable.bg_app_card)
                setPadding(dp(16), dp(10), dp(16), dp(10))
                text = label
                setTextColor(0xFFF6F8FC.toInt())
                gravity = android.view.Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, if (label.length > 2) 20f else 28f)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }

            val popup = PopupWindow(
                preview,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                false,
            ).apply {
                isTouchable = false
                isOutsideTouchable = false
                setBackgroundDrawable(ColorDrawable(0))
                elevation = dp(10).toFloat()
                setOnDismissListener {
                    if (pressedKeyPreviewPopup === this) {
                        pressedKeyPreviewPopup = null
                    }
                }
            }

            val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            preview.measure(unspecified, unspecified)
            val xOffset = (anchor.width - preview.measuredWidth) / 2
            val yOffset = -(anchor.height + preview.measuredHeight + dp(10))
            popup.showAsDropDown(anchor, xOffset, yOffset)
            pressedKeyPreviewPopup = popup
        }

        fun previewLabelFor(button: EnigmaKeyView): String? {
            if (button === spaceButton || button === enterButton || button === backspaceButton) return null
            val label = button.text?.toString()?.trim().orEmpty()
            if (label.isBlank()) return null
            if (label.length > 4 && button !== languageToggleButton) return null
            return label
        }

        fun launchFromKeyboard(intent: Intent): Result<Unit> =
            runCatching {
                startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                requestHideSelf(0)
            }

        fun currentEditorPackageName(): String? = currentInputEditorInfo?.packageName?.takeIf { it.isNotBlank() }

        fun shouldUseShareFallback(editorPackageName: String?): Boolean = when (editorPackageName) {
            // These apps either reject opaque IME content or don't reliably request URI access.
            "org.telegram.messenger",
            "com.google.android.apps.messaging",
            "com.whatsapp",
            "com.whatsapp.w4b",
            -> true
            else -> false
        }

        fun resolveShareTargetPackages(intent: Intent): Set<String> =
            runCatching {
                packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                    .mapNotNull { it.activityInfo?.packageName }
                    .filter { it.isNotBlank() }
                    .toSet()
            }.getOrElse { emptySet() }

        fun grantUriReadAccess(uri: android.net.Uri, packages: Iterable<String>) {
            packages
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
                .forEach { packageName ->
                    runCatching {
                        grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                }
        }

        fun buildCapsuleShareIntent(file: java.io.File): Intent {
            val capsuleType = runCatching {
                file.inputStream().use { input ->
                    val magic = ByteArray(4)
                    val read = input.read(magic)
                    if (read == 4) MediaCapsuleType.fromMagic(magic) else null
                }
            }.getOrNull()
            val mimeType = capsuleType?.capsuleMimeType ?: "application/octet-stream"
            val uri = FileProvider.getUriForFile(
                this@EnigmaKeyboardService,
                "${applicationContext.packageName}.fileprovider",
                file,
            )
            val targetPackage = currentEditorPackageName()
            val targetedIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = android.content.ClipData.newUri(contentResolver, file.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                targetPackage?.let(::setPackage)
            }
            val targetedResolves = runCatching {
                packageManager.resolveActivity(targetedIntent, PackageManager.MATCH_DEFAULT_ONLY)
            }.getOrNull() != null
            if (targetedResolves) {
                grantUriReadAccess(
                    uri,
                    buildSet {
                        targetPackage?.let(::add)
                        addAll(resolveShareTargetPackages(targetedIntent))
                    },
                )
                return targetedIntent
            }
            return Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = android.content.ClipData.newUri(contentResolver, file.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.also { fallbackIntent ->
                grantUriReadAccess(uri, resolveShareTargetPackages(fallbackIntent))
            }
        }

        fun buildCapsulePreviewIntent(file: java.io.File): Intent {
            val uri = FileProvider.getUriForFile(
                this@EnigmaKeyboardService,
                "${applicationContext.packageName}.fileprovider",
                file,
            )
            grantUriReadAccess(uri, listOf(packageName))
            return Intent(this@EnigmaKeyboardService, MediaCapsuleRouterActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = uri
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        fun buildPhotoCapsulePreviewIntent(file: java.io.File): Intent =
            Intent(this@EnigmaKeyboardService, PhotoCapsuleActivity::class.java).apply {
                putExtra(PhotoCapsuleActivity.EXTRA_FROM_KEYBOARD, true)
                putExtra(PhotoCapsuleActivity.EXTRA_PREVIEW_CAPSULE_PATH, file.absolutePath)
            }

        fun buildAudioCapsulePreviewIntent(file: java.io.File): Intent =
            Intent(this@EnigmaKeyboardService, AudioCapsuleActivity::class.java).apply {
                putExtra(AudioCapsuleActivity.EXTRA_PREVIEW_CAPSULE_PATH, file.absolutePath)
            }

        fun buildVideoCapsulePreviewIntent(file: java.io.File): Intent =
            Intent(this@EnigmaKeyboardService, VideoCapsuleActivity::class.java).apply {
                putExtra(VideoCapsuleActivity.EXTRA_FROM_KEYBOARD, true)
                putExtra(VideoCapsuleActivity.EXTRA_PREVIEW_CAPSULE_PATH, file.absolutePath)
            }

        fun alternateCharactersFor(value: String): List<String> {
            if (characterMode != CharacterMode.LETTERS || value.isBlank()) return emptyList()

            val alternatives = when (currentLanguage) {
                KeyboardLanguage.RU -> mapOf(
                    "е" to listOf("ё"),
                    "ь" to listOf("ъ"),
                )
                KeyboardLanguage.EN -> mapOf(
                    "a" to listOf("á", "à", "ä", "â"),
                    "c" to listOf("ç"),
                    "e" to listOf("é", "è", "ë", "ê"),
                    "i" to listOf("í", "ï", "î"),
                    "n" to listOf("ñ"),
                    "o" to listOf("ó", "ö", "ô"),
                    "u" to listOf("ú", "ü", "û"),
                )
                KeyboardLanguage.TR -> mapOf(
                    "a" to listOf("â"),
                    "c" to listOf("ç"),
                    "e" to listOf("ê"),
                    "g" to listOf("ğ"),
                    "i" to listOf("ı", "î"),
                    "ı" to listOf("i", "İ"),
                    "o" to listOf("ö", "ô"),
                    "s" to listOf("ş"),
                    "u" to listOf("ü", "û"),
                )
                KeyboardLanguage.ES -> mapOf(
                    "a" to listOf("á"),
                    "e" to listOf("é"),
                    "i" to listOf("í"),
                    "n" to listOf("ñ"),
                    "o" to listOf("ó"),
                    "u" to listOf("ú", "ü"),
                )
                KeyboardLanguage.PT -> mapOf(
                    "a" to listOf("á", "à", "ã", "â"),
                    "c" to listOf("ç"),
                    "e" to listOf("é", "ê"),
                    "i" to listOf("í"),
                    "o" to listOf("ó", "ô", "õ"),
                    "u" to listOf("ú"),
                )
                KeyboardLanguage.DE -> mapOf(
                    "a" to listOf("ä"),
                    "o" to listOf("ö"),
                    "s" to listOf("ß"),
                    "u" to listOf("ü"),
                )
                KeyboardLanguage.FR -> mapOf(
                    "a" to listOf("à", "â", "æ"),
                    "c" to listOf("ç"),
                    "e" to listOf("é", "è", "ê", "ë"),
                    "i" to listOf("î", "ï"),
                    "o" to listOf("ô", "œ"),
                    "u" to listOf("ù", "û", "ü"),
                    "y" to listOf("ÿ"),
                )
                KeyboardLanguage.IT -> mapOf(
                    "a" to listOf("à"),
                    "e" to listOf("è", "é"),
                    "i" to listOf("ì", "í"),
                    "o" to listOf("ò", "ó"),
                    "u" to listOf("ù", "ú"),
                )
            }

            val normalized = lowercaseForCurrentLanguage(value)
            return alternatives[normalized].orEmpty().map(::applyCase)
        }

        fun punctuationAlternatives(primary: String): List<String> = when {
            characterMode == CharacterMode.SYMBOLS && primary == "." -> listOf(",", "?", "!")
            characterMode == CharacterMode.SYMBOLS && primary == "/" -> listOf("\\", "|", "_")
            characterMode == CharacterMode.LETTERS && primary == "," -> when (currentLanguage) {
                KeyboardLanguage.RU -> listOf(";", "?", "!")
                KeyboardLanguage.EN -> listOf("?", "!", ";", ":")
                KeyboardLanguage.TR -> listOf(";", "?", "!")
                KeyboardLanguage.ES -> listOf(";", "¿", "?", "!")
                KeyboardLanguage.PT -> listOf(";", "?", "!", ":")
                KeyboardLanguage.DE -> listOf(";", "?", "!", ":")
                KeyboardLanguage.FR -> listOf(";", "?", "!", ":")
                KeyboardLanguage.IT -> listOf(";", "?", "!", ":")
            }
            characterMode == CharacterMode.LETTERS && primary == "." -> when (currentLanguage) {
                KeyboardLanguage.ES -> listOf(":", "¡", "!", "?", "…")
                KeyboardLanguage.FR -> listOf(":", "!", "?", "…", "«")
                else -> listOf("!", "?", "…")
            }
            else -> emptyList()
        }

        fun nextKeyboardLanguage(): KeyboardLanguage {
            val enabled = loadEnabledKeyboardLanguages()
            if (enabled.size <= 1) return currentLanguage
            val currentIndex = enabled.indexOf(currentLanguage).takeIf { it >= 0 } ?: 0
            return enabled[(currentIndex + 1) % enabled.size]
        }

        fun switchKeyboardLanguage(language: KeyboardLanguage) {
            dismissActiveKeyPopup()
            if (currentLanguage == language && characterMode == CharacterMode.LETTERS) {
                render()
                return
            }

            val previousLanguage = currentLanguage
            ensureKeyboardLanguageEnabled(language)
            currentLanguage = language
            lastKeyboardLanguage = previousLanguage
            persistKeyboardLanguage(currentLanguage, lastKeyboardLanguage)
            characterMode = CharacterMode.LETTERS
            currentSymbolPage = SymbolPage.PRIMARY
            capsLockEnabled = false
            shiftEnabled = shouldAutoCapitalizeFor(currentInputEditorInfo)
            previewMessage = null
            previewTone = PreviewTone.DEFAULT
            mode = KeyboardMode.IDLE
            render()
        }

        fun setPreview(message: String?, tone: PreviewTone = PreviewTone.DEFAULT) {
            previewMessage = message
            previewTone = tone
        }

        fun setIconActive(button: ImageButton, active: Boolean) {
            button.imageTintList = ColorStateList.valueOf(
                if (active) Color.WHITE else Color.parseColor("#9AA7B7"),
            )
            button.alpha = if (active) 1f else 0.82f
        }

        fun formatDurationShort(durationMs: Long): String {
            val totalSeconds = (durationMs / 1000).toInt().coerceAtLeast(0)
            return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
        }

        val inlineAudioTicker = object : Runnable {
            override fun run() {
                val recorder = inlineAudioRecorder ?: return
                val selectedProfile = resolveSelectedProfile(secureProfileStore)
                val elapsed = (System.currentTimeMillis() - inlineAudioStartedAt).coerceAtLeast(0L)
                setPreview(
                    "Voice capsule recording: ${formatDurationShort(elapsed)}" +
                        (selectedProfile?.let { " • ${it.title}" } ?: ""),
                    PreviewTone.DEFAULT,
                )
                render()
                repeatHandler.postDelayed(this, 250L)
            }
        }

        fun releaseInlineAudioRecorder() {
            repeatHandler.removeCallbacks(inlineAudioTicker)
            inlineAudioRecorder?.release()
            inlineAudioRecorder = null
            inlineAudioSourceFile = null
            inlineAudioRecordingPaused = false
            inlineAudioPausedAt = 0L
            inlineAudioPausedDurationMs = 0L
        }

        fun toggleInlineAudioPause() {
            val recorder = inlineAudioRecorder ?: return
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) return
            runCatching {
                if (inlineAudioRecordingPaused) {
                    recorder.resume()
                    inlineAudioPausedDurationMs += System.currentTimeMillis() - inlineAudioPausedAt
                    inlineAudioPausedAt = 0L
                    inlineAudioRecordingPaused = false
                } else {
                    recorder.pause()
                    inlineAudioPausedAt = System.currentTimeMillis()
                    inlineAudioRecordingPaused = true
                }
                render()
            }.onFailure {
                Log.e(TAG, "toggleInlineAudioPause failed", it)
            }
        }

        fun tryCommitCapsuleFile(file: java.io.File, mimeType: String): Boolean {
            val inputConnection = currentInputConnection ?: return false
            val editorInfo = currentInputEditorInfo ?: return false
            val editorPackageName = editorInfo.packageName?.takeIf { it.isNotBlank() } ?: return false
            if (shouldUseShareFallback(editorPackageName)) return false

            val uri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                file,
            )
            grantUriReadAccess(uri, listOf(editorPackageName))
            val description = ClipDescription(
                file.name,
                arrayOf(mimeType, "application/octet-stream", "*/*"),
            )
            val contentInfo = InputContentInfoCompat(uri, description, null)
            return InputConnectionCompat.commitContent(
                inputConnection,
                editorInfo,
                contentInfo,
                InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION,
                null,
            )
        }

        fun supportsDirectCapsuleInsert(editorInfo: EditorInfo?, mimeType: String): Boolean {
            if (editorInfo == null) return false
            if (shouldUseShareFallback(editorInfo.packageName)) return false
            val supportedMimeTypes = EditorInfoCompat.getContentMimeTypes(editorInfo)
            if (supportedMimeTypes.isEmpty()) return false
            return supportedMimeTypes.any { supportedType ->
                ClipDescription.compareMimeTypes(mimeType, supportedType) ||
                    ClipDescription.compareMimeTypes("application/octet-stream", supportedType)
            }
        }

        fun startInlineAudioRecording() {
            val profile = resolveSelectedProfile(secureProfileStore)
                ?: run {
                    setPreview(getString(R.string.keyboard_encrypt_missing_profile), PreviewTone.ERROR)
                    render()
                    return
                }
            releaseInlineAudioPlayback()
            clearPendingVideoCapsuleState()
            pendingCapsuleStore.clear()
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                launchFromKeyboard(
                    Intent(this, AudioPermissionRequestActivity::class.java).apply {
                    },
                )
                setPreview(getString(R.string.media_capsule_error_audio_permission), PreviewTone.DEFAULT)
                render()
                return
            }

            runCatching {
                val sourceFile = mediaCapsuleService.createRecordingFile(MediaCapsuleType.AUDIO, "m4a")
                releaseInlineAudioRecorder()
                inlineAudioSourceFile = sourceFile
                val preparedRecorder = createSpeechMediaRecorder(sourceFile.absolutePath)
                inlineAudioRecorder = preparedRecorder.recorder
                Log.d(
                    TAG,
                    "startInlineAudioRecording source=${describeAudioSource(preparedRecorder.audioSource)} file=${sourceFile.name}",
                )
                inlineAudioStartedAt = System.currentTimeMillis()
                inlineAudioPausedAt = 0L
                inlineAudioPausedDurationMs = 0L
                inlineAudioRecordingPaused = false
                lastAudioCapsuleFile = null
                lastAudioPlaybackFile = null
                lastAudioPlaybackCapsulePath = null
                lastAudioCapsuleNeedsManualSend = false
                mode = KeyboardMode.IDLE
                previewTone = PreviewTone.DEFAULT
                previewMessage = "Voice capsule recording: 0:00 • ${profile.title}"
                repeatHandler.removeCallbacks(inlineAudioTicker)
                repeatHandler.post(inlineAudioTicker)
                render()
            }.onFailure {
                releaseInlineAudioRecorder()
                setPreview(getString(R.string.media_capsule_error_encrypt), PreviewTone.ERROR)
                render()
            }
        }

        fun stopInlineAudioRecording() {
            val recorder = inlineAudioRecorder
            val sourceFile = inlineAudioSourceFile
            val profile = resolveSelectedProfile(secureProfileStore)
            val directInsertAdvertised = supportsDirectCapsuleInsert(
                currentInputEditorInfo,
                MediaCapsuleType.AUDIO.capsuleMimeType,
            )
            if (recorder == null || sourceFile == null || profile == null) {
                releaseInlineAudioRecorder()
                setPreview(getString(R.string.media_capsule_error_no_recording), PreviewTone.ERROR)
                render()
                return
            }

            runCatching {
                recorder.stop()
                recorder.reset()
                val pausedNow = if (inlineAudioRecordingPaused) System.currentTimeMillis() - inlineAudioPausedAt else 0L
                val durationMs = (System.currentTimeMillis() - inlineAudioStartedAt - inlineAudioPausedDurationMs - pausedNow)
                    .coerceAtLeast(1000L)
                val capsule = mediaCapsuleService.encryptFile(
                    sourceFile = sourceFile,
                    type = MediaCapsuleType.AUDIO,
                    mimeType = "audio/mp4",
                    durationMs = durationMs,
                    profile = profile,
                )
                lastAudioCapsuleFile = capsule
                pendingCapsuleStore.save(MediaCapsuleType.AUDIO, capsule)
                Log.d(
                    TAG,
                    "stopInlineAudioRecording source=${mediaCapsuleService.describeMediaFile(sourceFile)} capsule=${capsule.name}:${capsule.length()}",
                )
                if (tryCommitCapsuleFile(capsule, MediaCapsuleType.AUDIO.capsuleMimeType)) {
                    lastAudioCapsuleNeedsManualSend = false
                    setPreview(getString(R.string.keyboard_voice_capsule_received_inline), PreviewTone.SUCCESS)
                } else if (directInsertAdvertised) {
                    lastAudioCapsuleNeedsManualSend = true
                    setPreview(
                        getString(R.string.keyboard_voice_capsule_inline_failed_support),
                        PreviewTone.DEFAULT,
                    )
                } else {
                    lastAudioCapsuleNeedsManualSend = true
                    setPreview(
                        getString(R.string.keyboard_voice_capsule_inline_failed_no_support),
                        PreviewTone.DEFAULT,
                    )
                }
            }.onFailure {
                Log.e(TAG, "stopInlineAudioRecording failed", it)
                setPreview(getString(R.string.media_capsule_error_encrypt), PreviewTone.ERROR)
            }

            releaseInlineAudioRecorder()
            mode = KeyboardMode.IDLE
            render()
        }

        fun resolveLastAudioPlaybackFile(): java.io.File? {
            val capsule = lastAudioCapsuleFile ?: return null
            if (lastAudioPlaybackCapsulePath == capsule.absolutePath && lastAudioPlaybackFile?.exists() == true) {
                return lastAudioPlaybackFile
            }
            val profile = runCatching { mediaCapsuleService.resolveProfileForCapsule(capsule) }.getOrNull()
            if (profile?.requireBiometricForDecrypt == true) {
                launchFromKeyboard(buildAudioCapsulePreviewIntent(capsule))
                setPreview(getString(R.string.biometric_prompt_subtitle), PreviewTone.DEFAULT)
                render()
                return null
            }
            releaseInlineAudioPlayback()
            val decrypted = mediaCapsuleService.decryptFile(capsule)
            lastAudioPlaybackFile = decrypted.plaintextFile
            lastAudioPlaybackCapsulePath = capsule.absolutePath
            Log.d(
                TAG,
                "resolveLastAudioPlaybackFile playback=${mediaCapsuleService.describeMediaFile(decrypted.plaintextFile)} capsule=${capsule.name}:${capsule.length()}",
            )
            return lastAudioPlaybackFile
        }

        fun toggleInlineAudioPlayback() {
            val capsule = lastAudioCapsuleFile ?: run {
                setPreview(getString(R.string.media_capsule_error_open_first), PreviewTone.ERROR)
                render()
                return
            }
            if (inlineAudioPlayer != null) {
                releaseInlineAudioPlayback()
                setPreview(getString(R.string.keyboard_voice_capsule_ready_manual), PreviewTone.DEFAULT)
                render()
                return
            }

            val playbackFile = runCatching { resolveLastAudioPlaybackFile() }.getOrNull()
            if (playbackFile == null) {
                setPreview(getString(R.string.media_capsule_error_decrypt), PreviewTone.ERROR)
                render()
                return
            }

            runCatching {
                Log.d(TAG, "toggleInlineAudioPlayback playback=${mediaCapsuleService.describeMediaFile(playbackFile)}")
                inlineAudioPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build(),
                    )
                    setDataSource(playbackFile.absolutePath)
                    setOnCompletionListener {
                        releaseInlineAudioPlayback()
                        setPreview(getString(R.string.keyboard_voice_capsule_ready_manual), PreviewTone.DEFAULT)
                        render()
                    }
                    prepare()
                    start()
                }
                setPreview(getString(R.string.audio_capsule_status_playing), PreviewTone.DEFAULT)
            }.onFailure {
                Log.e(TAG, "toggleInlineAudioPlayback failed", it)
                releaseInlineAudioPlayback()
                setPreview(getString(R.string.media_capsule_error_open_first), PreviewTone.ERROR)
            }
            render()
        }

        fun schedulePreviewClear() {
            repeatHandler.removeCallbacksAndMessages(PREVIEW_CLEAR_TOKEN)
            if (previewTone == PreviewTone.DECRYPTED || inlineAudioRecorder != null) return
            repeatHandler.postAtTime(
                {
                    if (mode != KeyboardMode.DECRYPT) {
                        previewMessage = null
                        previewTone = PreviewTone.DEFAULT
                        mode = KeyboardMode.IDLE
                        statusText.setText(R.string.keyboard_status_idle)
                        previewText.text = getString(R.string.keyboard_preview_placeholder)
                        previewScroll.visibility = View.GONE
                    }
                },
                PREVIEW_CLEAR_TOKEN,
                SystemClock.uptimeMillis() + 2200L,
            )
        }

        fun clearPreviewForTyping() {
            repeatHandler.removeCallbacksAndMessages(PREVIEW_CLEAR_TOKEN)
            if (mode != KeyboardMode.DECRYPT) {
                mode = KeyboardMode.IDLE
                previewMessage = null
                previewTone = PreviewTone.DEFAULT
            }
        }

        fun isNumericLikeEditor(info: EditorInfo?): Boolean {
            val inputType = info?.inputType ?: return false
            return when (inputType and InputType.TYPE_MASK_CLASS) {
                InputType.TYPE_CLASS_NUMBER,
                InputType.TYPE_CLASS_PHONE,
                InputType.TYPE_CLASS_DATETIME,
                -> true
                else -> false
            }
        }

        fun shouldAutoCapitalize(): Boolean {
            if (characterMode != CharacterMode.LETTERS) return false
            if (capsLockEnabled) return true
            val inputType = currentInputEditorInfo?.inputType ?: 0
            val inputClass = inputType and InputType.TYPE_MASK_CLASS
            val variation = inputType and InputType.TYPE_MASK_VARIATION
            if (
                variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            ) {
                return false
            }
            val capsMode = currentInputConnection?.getCursorCapsMode(inputType) ?: 0
            if (capsMode != 0) return true
            if (inputClass != InputType.TYPE_CLASS_TEXT) return false
            val before = currentInputConnection?.getTextBeforeCursor(32, 0)?.toString().orEmpty()
            if (before.isBlank()) return true
            return before.endsWith(". ") ||
                before.endsWith("! ") ||
                before.endsWith("? ") ||
                before.endsWith("… ")
        }

        fun textBeforeCursor(maxChars: Int = 4): String =
            currentInputConnection?.getTextBeforeCursor(maxChars, 0)?.toString().orEmpty()

        fun applyReplacementCase(original: String, replacement: String): String = when {
            original.all { it.isUpperCase() } -> uppercaseForCurrentLanguage(replacement)
            original.firstOrNull()?.isUpperCase() == true -> titlecaseForCurrentLanguage(replacement)
            else -> replacement
        }

        fun softAutocorrectCandidate(normalized: String): String? = when (currentLanguage) {
            KeyboardLanguage.RU -> RU_SOFT_AUTOCORRECT_V2[normalized]
            KeyboardLanguage.EN -> EN_SOFT_AUTOCORRECT_V2[normalized]
            else -> null
        }

        fun autocorrectBeforeSeparator(softOnly: Boolean = false) {
            if (characterMode != CharacterMode.LETTERS) return

            val source = textBeforeCursor(32)
            val match = WORD_AT_END.find(source) ?: return
            val originalWord = match.value
            val normalized = lowercaseForCurrentLanguage(originalWord)
            val replacementBase =
                if (softOnly) {
                    softAutocorrectCandidate(normalized)
                } else {
                    when (currentLanguage) {
                        KeyboardLanguage.RU -> RU_AUTOCORRECT_V2[normalized]
                        KeyboardLanguage.EN -> EN_AUTOCORRECT_V2[normalized]
                        KeyboardLanguage.TR -> TR_AUTOCORRECT[normalized]
                        KeyboardLanguage.ES -> ES_AUTOCORRECT[normalized]
                        KeyboardLanguage.PT -> PT_AUTOCORRECT[normalized]
                        KeyboardLanguage.DE -> DE_AUTOCORRECT[normalized]
                        KeyboardLanguage.FR -> FR_AUTOCORRECT[normalized]
                        KeyboardLanguage.IT -> IT_AUTOCORRECT[normalized]
                    }
                } ?: return

            val replacement = applyReplacementCase(originalWord, replacementBase)
            currentInputConnection?.deleteSurroundingText(originalWord.length, 0)
            currentInputConnection?.commitText(replacement, 1)
            rememberRecentWord(lowercaseForCurrentLanguage(replacement))
            recentWords = loadRecentWords()
        }

        fun currentWordBeforeCursor(): String? =
            WORD_AT_END.find(textBeforeCursor(48))?.value

        fun previousWordBeforeCursor(): String? {
            val before = textBeforeCursor(64)
            val trimmedEnd = before.trimEnd()
            if (trimmedEnd.isBlank()) return null
            val withoutCurrent =
                if (before.lastOrNull()?.isWhitespace() == false) {
                    trimmedEnd.substringBeforeLast(' ', "")
                } else {
                    trimmedEnd
                }
            return WORD_AT_END.find(withoutCurrent)?.value
        }

        fun replaceCurrentWord(replacement: String) {
            val currentWord = currentWordBeforeCursor() ?: return
            currentInputConnection?.deleteSurroundingText(currentWord.length, 0)
            currentInputConnection?.commitText("$replacement ", 1)
            rememberRecentWord(lowercaseForCurrentLanguage(replacement))
            recentWords = loadRecentWords()
            shiftEnabled = shouldAutoCapitalize()
        }

        fun applySuggestion(replacement: String) {
            val currentWord = currentWordBeforeCursor()
            if (!currentWord.isNullOrBlank()) {
                replaceCurrentWord(replacement)
                return
            }

            val before = textBeforeCursor(4)
            val needsLeadingSpace =
                before.isNotBlank() &&
                    before.lastOrNull()?.isWhitespace() == false &&
                    before.lastOrNull() !in listOf('(', '[', '{', '"', '\'')
            val textToCommit = buildString {
                if (needsLeadingSpace) append(' ')
                append(replacement)
                append(' ')
            }
            currentInputConnection?.commitText(textToCommit, 1)
            rememberRecentWord(lowercaseForCurrentLanguage(replacement))
            recentWords = loadRecentWords()
            shiftEnabled = shouldAutoCapitalize()
        }

        fun predictionLexicon(language: KeyboardLanguage): List<String> =
            predictionLexiconCache.getOrPut(language) {
                val assetName = when (language) {
                    KeyboardLanguage.RU -> "prediction_ru.txt"
                    KeyboardLanguage.EN -> "prediction_en.txt"
                    else -> return@getOrPut emptyList()
                }
                runCatching {
                    assets.open(assetName).bufferedReader(Charsets.UTF_8).use { reader ->
                        reader.readLines()
                            .map { it.trim() }
                            .filter { it.length >= 2 }
                            .distinct()
                    }
                }.getOrElse {
                    when (language) {
                        KeyboardLanguage.RU -> RU_SUGGESTIONS_V2
                        KeyboardLanguage.EN -> EN_SUGGESTIONS_V2
                        else -> emptyList()
                    }
                }
            }

        fun predictionPriorityWords(language: KeyboardLanguage): Set<String> = when (language) {
            KeyboardLanguage.RU -> RU_PRIORITY_SUGGESTIONS_V2
            KeyboardLanguage.EN -> EN_PRIORITY_SUGGESTIONS_V2
            else -> emptySet()
        }

        fun predictionCorrections(language: KeyboardLanguage): Map<String, String> = when (language) {
            KeyboardLanguage.RU -> RU_AUTOCORRECT_V2
            KeyboardLanguage.EN -> EN_AUTOCORRECT_V2
            else -> emptyMap()
        }

        fun predictionNextWords(language: KeyboardLanguage): Map<String, List<String>> =
            predictionNextWordCache.getOrPut(language) {
                val assetName = when (language) {
                    KeyboardLanguage.RU -> "prediction_next_ru.txt"
                    KeyboardLanguage.EN -> "prediction_next_en.txt"
                    else -> return@getOrPut emptyMap()
                }
                runCatching {
                    assets.open(assetName).bufferedReader(Charsets.UTF_8).useLines { lines ->
                        lines.mapNotNull { line ->
                            val trimmed = line.trim()
                            if (trimmed.isBlank() || !trimmed.contains('\t')) return@mapNotNull null
                            val (key, values) = trimmed.split('\t', limit = 2)
                            val suggestions = values.split(',')
                                .map { it.trim() }
                                .filter { it.length >= 2 }
                            if (key.isBlank() || suggestions.isEmpty()) null else key to suggestions
                        }.toMap()
                    }
                }.getOrElse { emptyMap() }
            }

        fun predictionEngine(language: KeyboardLanguage): KeyboardPredictionEngine =
            predictionEngineCache.getOrPut(language) {
                KeyboardPredictionEngine.create(
                    terms = predictionLexicon(language),
                    priorityWords = predictionPriorityWords(language),
                    explicitCorrections = predictionCorrections(language),
                    nextWordMap = predictionNextWords(language),
                )
            }

        fun suggestionsForContext(): List<String> {
            if (characterMode != CharacterMode.LETTERS) return emptyList()
            val language = currentLanguage
            if (language != KeyboardLanguage.RU && language != KeyboardLanguage.EN) return emptyList()
            val engine = predictionEngine(language)
            val currentWord = currentWordBeforeCursor()
            return if (!currentWord.isNullOrBlank()) {
                engine.suggestions(lowercaseForCurrentLanguage(currentWord), maxSuggestions = 3)
                    .map { applyReplacementCase(currentWord, it) }
            } else {
                previousWordBeforeCursor()?.let { previous ->
                    engine.nextSuggestions(lowercaseForCurrentLanguage(previous), maxSuggestions = 3)
                        .map(::applyCase)
                }.orEmpty()
            }
        }

        fun supportsPredictiveTyping(): Boolean {
            if (!PREDICTIVE_TYPING_ENABLED) return false
            if (characterMode != CharacterMode.LETTERS) return false
            if (currentLanguage != KeyboardLanguage.EN && currentLanguage != KeyboardLanguage.RU) return false
            val inputType = currentInputEditorInfo?.inputType ?: 0
            val variation = inputType and InputType.TYPE_MASK_VARIATION
            return variation != InputType.TYPE_TEXT_VARIATION_PASSWORD &&
                variation != InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD &&
                variation != InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        }

        fun refreshShiftAfterEdit() {
            if (characterMode != CharacterMode.LETTERS) {
                shiftEnabled = false
                capsLockEnabled = false
                return
            }
            if (capsLockEnabled) {
                shiftEnabled = true
                return
            }
            shiftEnabled = shouldAutoCapitalize()
        }

        fun commitSmartSpace() {
            autocorrectBeforeSeparator(softOnly = true)
            currentWordBeforeCursor()?.let { word ->
                val normalized = lowercaseForCurrentLanguage(word)
                rememberRecentWord(normalized)
                recentWords = loadRecentWords()
            }
            val before = textBeforeCursor(2)
            val now = SystemClock.uptimeMillis()
            val previousChar = before.lastOrNull()

            if (
                now - lastSpaceTapAt < 700L &&
                previousChar == ' ' &&
                before.length >= 2 &&
                before[before.length - 2].isLetterOrDigit()
            ) {
                currentInputConnection?.deleteSurroundingText(1, 0)
                currentInputConnection?.commitText(". ", 1)
                refreshShiftAfterEdit()
                lastSpaceTapAt = 0L
                return
            }

            if (previousChar != ' ') {
                currentInputConnection?.commitText(" ", 1)
            }
            refreshShiftAfterEdit()
            lastSpaceTapAt = now
        }

        fun commitSmartPunctuation(mark: String) {
            if (characterMode == CharacterMode.NUMERIC) {
                currentInputConnection?.commitText(mark, 1)
                return
            }
            autocorrectBeforeSeparator()
            currentWordBeforeCursor()?.let { word ->
                val normalized = lowercaseForCurrentLanguage(word)
                rememberRecentWord(normalized)
                recentWords = loadRecentWords()
            }
            val before = textBeforeCursor(3)
            val needsSpaceAfter = characterMode == CharacterMode.LETTERS
            val previousChar = before.lastOrNull()
            val shouldReplacePreviousSpace =
                needsSpaceAfter &&
                    previousChar == ' ' &&
                    before.length >= 2 &&
                    before[before.length - 2].isLetterOrDigit()
            val shouldInsertTrailingSpace = needsSpaceAfter && previousChar != null && !previousChar.isWhitespace()
            when {
                shouldReplacePreviousSpace -> {
                    currentInputConnection?.deleteSurroundingText(1, 0)
                    currentInputConnection?.commitText("$mark ", 1)
                }
                shouldInsertTrailingSpace -> {
                    currentInputConnection?.commitText("$mark ", 1)
                }
                else -> {
                    currentInputConnection?.commitText(mark, 1)
                }
            }
            refreshShiftAfterEdit()
            lastSpaceTapAt = 0L
        }

        fun updateEnterKey() {
            val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
            enterButton.text = when (action) {
                EditorInfo.IME_ACTION_SEND -> "➤"
                EditorInfo.IME_ACTION_SEARCH -> "⌕"
                EditorInfo.IME_ACTION_GO -> "→"
                EditorInfo.IME_ACTION_DONE -> "✓"
                else -> keyboardString(R.string.keyboard_action_enter)
            }
        }

        fun updateShiftState(forceOff: Boolean = false) {
            if (characterMode != CharacterMode.LETTERS) {
                shiftEnabled = false
                return
            }
            if (forceOff) {
                shiftEnabled = false
                return
            }
            shiftEnabled = shouldAutoCapitalize()
        }

        fun commitText(text: String) {
            currentInputConnection?.commitText(text, 1)
            if (shiftEnabled && !capsLockEnabled && characterMode == CharacterMode.LETTERS) {
                shiftEnabled = false
            }
            updateShiftState()
        }

        fun updateRowInsets() {
            when {
                characterMode == CharacterMode.NUMERIC -> {
                    row1Layout.setPadding(0, 0, 0, 0)
                    row2Layout.setPadding(0, 0, 0, 0)
                    row3Layout.setPadding(0, 0, 0, 0)
                }
                characterMode == CharacterMode.SYMBOLS -> {
                    row1Layout.setPadding(0, 0, 0, 0)
                    row2Layout.setPadding(0, 0, 0, 0)
                    row3Layout.setPadding(0, 0, 0, 0)
                }
                currentLanguage == KeyboardLanguage.TR -> {
                    row1Layout.setPadding(0, 0, 0, 0)
                    row2Layout.setPadding(0, 0, 0, 0)
                    row3Layout.setPadding(0, 0, 0, 0)
                }
                else -> {
                    row1Layout.setPadding(0, 0, 0, 0)
                    row2Layout.setPadding(0, 0, 0, 0)
                    row3Layout.setPadding(0, 0, 0, 0)
                }
            }
        }

        fun updateCharacterKeys() {
            val rows = currentRows()

            rowButtons.forEachIndexed { rowIndex, buttons ->
                val chars = rows.getOrElse(rowIndex) { emptyList() }
                buttons.forEachIndexed { index, button ->
                    val value = chars.getOrNull(index)
                    if (value.isNullOrBlank()) {
                        button.visibility = View.GONE
                        button.isEnabled = false
                        button.text = ""
                        button.tag = null
                    } else {
                        val displayValue = applyCase(value)
                        button.visibility = View.VISIBLE
                        button.isEnabled = true
                        button.text = displayValue
                        button.tag = displayValue
                    }
                }
            }

            languageToggleButton.text = when (currentLanguage) {
                KeyboardLanguage.RU,
                KeyboardLanguage.EN,
                KeyboardLanguage.TR,
                KeyboardLanguage.ES,
                KeyboardLanguage.PT,
                KeyboardLanguage.DE,
                KeyboardLanguage.FR,
                KeyboardLanguage.IT,
                -> "🌐"
            }
            languageToggleButton.text = currentLanguage.localeTag.uppercase()
            symbolsToggleButton.text = if (characterMode == CharacterMode.NUMERIC) {
                keyboardString(R.string.keyboard_action_letters)
            } else if (characterMode == CharacterMode.SYMBOLS) {
                keyboardString(R.string.keyboard_action_letters)
            } else {
                keyboardString(R.string.keyboard_action_symbols_alt)
            }
            shiftButton.text = if (shiftEnabled) {
                if (capsLockEnabled) {
                    SHIFT_LOCKED_SYMBOL
                } else {
                    SHIFT_LOCKED_SYMBOL
                }
            } else {
                SHIFT_UP_SYMBOL
            }
            shiftButton.isEnabled = characterMode == CharacterMode.LETTERS
            shiftButton.visibility = if (characterMode == CharacterMode.NUMERIC) View.GONE else View.VISIBLE
            languageToggleButton.visibility = if (characterMode == CharacterMode.NUMERIC) View.GONE else View.VISIBLE
            commaButton.text = if (characterMode == CharacterMode.NUMERIC) {
                "-"
            } else if (characterMode == CharacterMode.SYMBOLS) {
                if (currentSymbolPage == SymbolPage.PRIMARY) {
                    keyboardString(R.string.keyboard_action_symbols_alt)
                } else {
                    keyboardString(R.string.keyboard_action_symbols)
                }
            } else {
                ","
            }
            dotButton.text = if (characterMode == CharacterMode.NUMERIC) {
                java.text.DecimalFormatSymbols.getInstance(currentLanguage.locale).decimalSeparator.toString()
            } else {
                "."
            }
            spaceButton.text = if (characterMode == CharacterMode.NUMERIC) "0" else keyboardString(R.string.keyboard_action_space)
            numberButtons.forEach { button ->
                button.visibility = if (characterMode == CharacterMode.NUMERIC) View.GONE else View.VISIBLE
            }
            numberRowLayout.visibility = if (characterMode == CharacterMode.NUMERIC) View.GONE else View.VISIBLE
            numericPadContainer.visibility = if (characterMode == CharacterMode.NUMERIC) View.VISIBLE else View.GONE
            row1Layout.visibility = if (characterMode == CharacterMode.NUMERIC) View.GONE else View.VISIBLE
            row2Layout.visibility = if (characterMode == CharacterMode.NUMERIC) View.GONE else View.VISIBLE
            row3Layout.visibility = if (characterMode == CharacterMode.NUMERIC) View.GONE else View.VISIBLE
            row4Layout.visibility = if (characterMode == CharacterMode.NUMERIC) View.GONE else View.VISIBLE
            numericCommaButton.text = ","
            numericDotButton.text = java.text.DecimalFormatSymbols.getInstance(currentLanguage.locale).decimalSeparator.toString()
            numericEnterButton.text = enterButton.text
            updateRowInsets()
            updateEnterKey()
        }

        render = {
            resolveSelectedProfile(secureProfileStore)
            profileInfoText.text = ""
            profileInfoText.visibility = View.GONE
            statusText.visibility = View.GONE
            when (mode) {
                KeyboardMode.IDLE -> {
                    previewText.text = previewMessage ?: getString(R.string.keyboard_preview_placeholder)
                }

                KeyboardMode.ENIGMA -> {
                    previewText.text = previewMessage ?: getString(R.string.keyboard_preview_encrypt_hint)
                }

                KeyboardMode.DECRYPT -> {
                    previewText.text = previewMessage ?: getString(R.string.keyboard_preview_decrypt_hint)
                }
            }
            previewScroll.visibility =
                if (!previewMessage.isNullOrBlank() && (
                    previewTone == PreviewTone.DECRYPTED ||
                        previewTone == PreviewTone.ERROR ||
                        previewTone == PreviewTone.SUCCESS
                    )
                ) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
            when (previewTone) {
                PreviewTone.DEFAULT -> previewText.setTextColor(0xFFEAF4FF.toInt())
                PreviewTone.SUCCESS -> previewText.setTextColor(0xFFC9F7D4.toInt())
                PreviewTone.ERROR -> previewText.setTextColor(0xFFFFB8B8.toInt())
                PreviewTone.DECRYPTED -> previewText.setTextColor(0xFFFFFFFF.toInt())
            }
            previewText.setTextSize(
                TypedValue.COMPLEX_UNIT_SP,
                if (previewTone == PreviewTone.DECRYPTED) 16f else 14f,
            )
            audioCapsuleButton.setImageResource(
                if (inlineAudioRecorder != null) android.R.drawable.ic_media_pause
                else android.R.drawable.ic_btn_speak_now,
            )
            val recordingVisible = inlineAudioRecorder != null
            audioRecordingPanel.visibility = if (recordingVisible) View.VISIBLE else View.GONE
            if (recordingVisible) {
                val now = System.currentTimeMillis()
                val pausedNow = if (inlineAudioRecordingPaused) now - inlineAudioPausedAt else 0L
                val elapsed = (now - inlineAudioStartedAt - inlineAudioPausedDurationMs - pausedNow).coerceAtLeast(0L)
                audioRecordingText.text = keyboardString(R.string.audio_capsule_recording_panel, formatDurationShort(elapsed))
                audioRecordingPauseButton.text =
                    if (inlineAudioRecordingPaused) keyboardString(R.string.video_capsule_resume)
                    else keyboardString(R.string.video_capsule_pause)
                audioRecordingStopButton.text = keyboardString(R.string.audio_capsule_stop_short)
            }
            videoCapsuleButton.isEnabled = !recordingVisible
            photoCapsuleButton.isEnabled = !recordingVisible
            val audioActionVisible = lastAudioCapsuleFile != null && inlineAudioRecorder == null
            val videoActionVisible = lastVideoCapsuleFile != null && inlineAudioRecorder == null
            val photoActionVisible = lastPhotoCapsuleFile != null && inlineAudioRecorder == null
            Log.d(
                TAG,
                "render audioActionVisible=$audioActionVisible videoActionVisible=$videoActionVisible photoActionVisible=$photoActionVisible lastAudio=${lastAudioCapsuleFile?.name} lastVideo=${lastVideoCapsuleFile?.name} lastPhoto=${lastPhotoCapsuleFile?.name}",
            )
            audioCapsuleActionPanel.visibility =
                if (audioActionVisible || videoActionVisible || photoActionVisible) View.VISIBLE else View.GONE
            val anyCapsuleVisible = audioActionVisible || videoActionVisible || photoActionVisible
            val suggestions =
                if (
                    supportsPredictiveTyping() &&
                    mode == KeyboardMode.IDLE &&
                    !recordingVisible &&
                    !anyCapsuleVisible
                ) {
                    suggestionsForContext()
                } else {
                    emptyList()
                }
            suggestionButtons.forEachIndexed { index, button ->
                val suggestion = suggestions.getOrNull(index)
                button.text = suggestion.orEmpty()
                button.visibility = if (suggestion.isNullOrBlank()) View.GONE else View.VISIBLE
            }
            suggestionRow.visibility = if (suggestions.isNotEmpty()) View.VISIBLE else View.GONE
            playAudioCapsuleActionButton.visibility = if (anyCapsuleVisible) View.VISIBLE else View.GONE
            playAudioCapsuleActionButton.isEnabled = anyCapsuleVisible
            playAudioCapsuleActionButton.alpha = if (anyCapsuleVisible) 1f else 0.55f
            deleteCapsuleActionButton.visibility =
                if (audioActionVisible || videoActionVisible || photoActionVisible) View.VISIBLE else View.GONE
            deleteCapsuleActionButton.text = keyboardString(R.string.keyboard_action_close)
            audioCapsuleActionText.text =
                if (lastAudioCapsuleNeedsManualSend) keyboardString(R.string.keyboard_voice_capsule_ready_manual)
                else if (supportsDirectCapsuleInsert(currentInputEditorInfo, MediaCapsuleType.AUDIO.capsuleMimeType)) {
                    keyboardString(R.string.keyboard_voice_capsule_support_direct)
                } else {
                    keyboardString(R.string.keyboard_voice_capsule_no_direct_support)
                }
            when {
                audioActionVisible -> {
                    audioCapsuleActionText.text = keyboardString(R.string.audio_capsule_action_ready_text)
                    playAudioCapsuleActionButton.setImageResource(
                        if (inlineAudioPlayer != null) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    )
                    sendAudioCapsuleActionButton.text = keyboardString(R.string.keyboard_capsule_send_short)
                }
                videoActionVisible && lastVideoCapsuleReadyForDirectInsert -> {
                    audioCapsuleActionText.text = keyboardString(R.string.keyboard_video_capsule_ready_insert)
                    playAudioCapsuleActionButton.setImageResource(android.R.drawable.ic_menu_view)
                    sendAudioCapsuleActionButton.text = keyboardString(R.string.keyboard_capsule_send_short)
                }
                videoActionVisible -> {
                    audioCapsuleActionText.text = keyboardString(R.string.keyboard_video_capsule_ready_manual)
                    playAudioCapsuleActionButton.setImageResource(android.R.drawable.ic_menu_view)
                    sendAudioCapsuleActionButton.text = keyboardString(R.string.keyboard_capsule_send_short)
                }
                photoActionVisible -> {
                    audioCapsuleActionText.text = keyboardString(R.string.photo_capsule_ready_manual)
                    playAudioCapsuleActionButton.setImageResource(android.R.drawable.ic_menu_view)
                    sendAudioCapsuleActionButton.text = keyboardString(R.string.keyboard_capsule_send_short)
                }
                else -> {
                    playAudioCapsuleActionButton.setImageResource(android.R.drawable.ic_media_play)
                    sendAudioCapsuleActionButton.text = keyboardString(R.string.keyboard_capsule_send_short)
                }
            }
            setIconActive(enigmaToggleButton, mode == KeyboardMode.ENIGMA)
            setIconActive(decryptButton, mode == KeyboardMode.DECRYPT)
            setIconActive(clearButton, false)
            setIconActive(attachToggleButton, false)
            setIconActive(attachBackButton, false)
            setIconActive(audioCapsuleButton, recordingVisible || audioActionVisible)
            setIconActive(photoCapsuleButton, photoActionVisible)
            setIconActive(videoCapsuleButton, videoActionVisible)
            setIconActive(playAudioCapsuleActionButton, anyCapsuleVisible || inlineAudioPlayer != null)
            previewScroll.post { previewScroll.scrollTo(0, 0) }
            updateKeyboardHeightProfile()
            updateCharacterKeys()
            if (!previewMessage.isNullOrBlank()) {
                schedulePreviewClear()
            }
        }
        renderInputView = render
        applyKeyboardAppearanceToInputView = ::applyKeyboardAppearance

        val pendingCapsulePoll = object : Runnable {
            override fun run() {
                refreshPendingCapsule()
                render()
                repeatHandler.postAtTime(
                    this,
                    PENDING_CAPSULE_POLL_TOKEN,
                    SystemClock.uptimeMillis() + 500L,
                )
            }
        }
        repeatHandler.removeCallbacksAndMessages(PENDING_CAPSULE_POLL_TOKEN)
        repeatHandler.postAtTime(
            pendingCapsulePoll,
            PENDING_CAPSULE_POLL_TOKEN,
            SystemClock.uptimeMillis() + 500L,
        )

        rowButtons.flatten().forEach { it.applyCharacterStyle() }
        numberButtons.forEach { it.applyCharacterStyle() }

        utilityButtons.forEach { button ->
            button.applyUtilityStyle()
        }
        suggestionButtons.forEach { it.applySuggestionStyle() }
        applyKeyboardAppearance()
        rowButtons.flatten().forEach { button ->
            button.setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> previewLabelFor(button)?.let { showPressedKeyPreview(button, it) }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dismissPressedKeyPreview()
                }
                false
            }
            button.setOnClickListener {
                dismissPressedKeyPreview()
                dismissActiveKeyPopup()
                val value = button.tag as? String ?: return@setOnClickListener
                clearPreviewForTyping()
                commitText(value)
                render()
            }
            button.setOnLongClickListener {
                val value = button.tag as? String ?: return@setOnLongClickListener false
                val alternatives = alternateCharactersFor(value)
                if (alternatives.isEmpty()) {
                    false
                } else {
                    showKeyPopup(button, alternatives) { selected ->
                        clearPreviewForTyping()
                        commitText(selected)
                        render()
                    }
                    true
                }
            }
        }
        numberButtons.forEach { button ->
            button.setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> previewLabelFor(button)?.let { showPressedKeyPreview(button, it) }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dismissPressedKeyPreview()
                }
                false
            }
            button.setOnClickListener {
                dismissPressedKeyPreview()
                dismissActiveKeyPopup()
                clearPreviewForTyping()
                commitText(button.text?.toString().orEmpty())
                render()
            }
        }
        suggestionButtons.forEach { button ->
            button.setOnClickListener {
                val selected = button.text?.toString().orEmpty()
                if (selected.isBlank()) return@setOnClickListener
                dismissPressedKeyPreview()
                dismissActiveKeyPopup()
                clearPreviewForTyping()
                applySuggestion(selected)
                render()
            }
        }

        listOf(languageToggleButton, commaButton, dotButton, symbolsToggleButton, shiftButton).forEach { button ->
            button.setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> previewLabelFor(button)?.let { showPressedKeyPreview(button, it) }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dismissPressedKeyPreview()
                }
                false
            }
        }

        languageToggleButton.setOnClickListener {
            clearPreviewForTyping()
            switchKeyboardLanguage(nextKeyboardLanguage())
            previewMessage = null
            previewTone = PreviewTone.DEFAULT
            render()
        }

        languageToggleButton.setOnLongClickListener {
            showKeyPopup(
                anchor = languageToggleButton,
                labels = languageChoices().map { it.first },
                useUtilityStyle = true,
            ) { selected ->
                languageChoices().firstOrNull { it.first == selected }?.let { (_, language) ->
                    clearPreviewForTyping()
                    switchKeyboardLanguage(language)
                }
            }
            true
        }

        symbolsToggleButton.setOnClickListener {
            dismissPressedKeyPreview()
            dismissActiveKeyPopup()
            when {
                characterMode == CharacterMode.NUMERIC -> {
                    characterMode = CharacterMode.LETTERS
                    currentSymbolPage = SymbolPage.PRIMARY
                    updateShiftState()
                }
                characterMode == CharacterMode.LETTERS -> {
                    characterMode = CharacterMode.SYMBOLS
                    currentSymbolPage = SymbolPage.PRIMARY
                }
                else -> {
                    characterMode = CharacterMode.LETTERS
                    currentSymbolPage = SymbolPage.PRIMARY
                    shiftEnabled = shouldAutoCapitalizeFor(currentInputEditorInfo)
                }
            }
            previewMessage = null
            previewTone = PreviewTone.DEFAULT
            if (characterMode != CharacterMode.LETTERS) {
                shiftEnabled = false
                capsLockEnabled = false
            }
            mode = KeyboardMode.IDLE
            render()
        }

        shiftButton.setOnClickListener {
            dismissPressedKeyPreview()
            dismissActiveKeyPopup()
            if (characterMode == CharacterMode.LETTERS) {
                val now = SystemClock.uptimeMillis()
                when {
                    capsLockEnabled -> {
                        capsLockEnabled = false
                        shiftEnabled = shouldAutoCapitalizeFor(currentInputEditorInfo)
                        lastShiftTapAt = 0L
                    }
                    shiftEnabled && now - lastShiftTapAt < 400L -> {
                        capsLockEnabled = true
                        shiftEnabled = true
                        lastShiftTapAt = 0L
                    }
                    else -> {
                        shiftEnabled = !shiftEnabled
                        lastShiftTapAt = if (shiftEnabled) now else 0L
                    }
                }
                previewMessage = null
                previewTone = PreviewTone.DEFAULT
                render()
            }
        }

        shiftButton.setOnLongClickListener {
            if (characterMode == CharacterMode.LETTERS) {
                capsLockEnabled = !capsLockEnabled
                shiftEnabled = capsLockEnabled
                lastShiftTapAt = 0L
                previewMessage = null
                previewTone = PreviewTone.DEFAULT
                render()
                true
            } else {
                false
            }
        }

        spaceButton.setOnClickListener {
            dismissPressedKeyPreview()
            dismissActiveKeyPopup()
            clearPreviewForTyping()
            if (characterMode == CharacterMode.NUMERIC) {
                commitText("0")
            } else {
                commitSmartSpace()
            }
            render()
        }

        spaceButton.setOnLongClickListener {
            showKeyPopup(
                anchor = spaceButton,
                labels = allLanguageChoices().map { it.first },
                useUtilityStyle = true,
            ) { selected ->
                allLanguageChoices().firstOrNull { it.first == selected }?.let { (_, language) ->
                    clearPreviewForTyping()
                    switchKeyboardLanguage(language)
                }
            }
            true
        }

        commaButton.setOnClickListener {
            dismissPressedKeyPreview()
            dismissActiveKeyPopup()
            if (characterMode == CharacterMode.NUMERIC) {
                clearPreviewForTyping()
                commitText("-")
                render()
                return@setOnClickListener
            }
            if (characterMode == CharacterMode.SYMBOLS) {
                currentSymbolPage = if (currentSymbolPage == SymbolPage.PRIMARY) {
                    SymbolPage.SECONDARY
                } else {
                    SymbolPage.PRIMARY
                }
                previewMessage = null
                previewTone = PreviewTone.DEFAULT
                render()
                return@setOnClickListener
            }
            clearPreviewForTyping()
            commitSmartPunctuation(",")
            render()
        }

        commaButton.setOnLongClickListener {
            if (characterMode == CharacterMode.SYMBOLS) {
                return@setOnLongClickListener false
            }
            val primary = ","
            val alternatives = punctuationAlternatives(primary)
            if (alternatives.isEmpty()) {
                false
            } else {
                showKeyPopup(commaButton, alternatives) { selected ->
                    clearPreviewForTyping()
                    commitSmartPunctuation(selected)
                    render()
                }
                true
            }
        }

        dotButton.setOnClickListener {
            dismissPressedKeyPreview()
            dismissActiveKeyPopup()
            clearPreviewForTyping()
            if (characterMode == CharacterMode.NUMERIC) {
                commitText(java.text.DecimalFormatSymbols.getInstance(currentLanguage.locale).decimalSeparator.toString())
            } else {
                commitSmartPunctuation(".")
            }
            render()
        }

        dotButton.setOnLongClickListener {
            val primary = "."
            val alternatives = punctuationAlternatives(primary)
            if (alternatives.isEmpty()) {
                false
            } else {
                showKeyPopup(dotButton, alternatives) { selected ->
                    clearPreviewForTyping()
                    commitSmartPunctuation(selected)
                    render()
                }
                true
            }
        }

        backspaceButton.setOnClickListener {
            dismissPressedKeyPreview()
            dismissActiveKeyPopup()
            clearPreviewForTyping()
            handleBackspace()
            render()
        }
        val repeatBackspace = object : Runnable {
            override fun run() {
                clearPreviewForTyping()
                handleBackspace()
                render()
                repeatHandler.postDelayed(this, 70L)
            }
        }
        backspaceButton.setOnLongClickListener {
            handleBackspace()
            render()
            repeatHandler.postDelayed(repeatBackspace, 250L)
            true
        }
        backspaceButton.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> Unit
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dismissPressedKeyPreview()
            }
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                repeatHandler.removeCallbacks(repeatBackspace)
            }
            false
        }
        numericDigitButtons.forEach { button ->
            button.setOnClickListener {
                dismissPressedKeyPreview()
                dismissActiveKeyPopup()
                clearPreviewForTyping()
                commitText(button.text.toString())
                render()
            }
        }
        numericDeleteButton.setOnClickListener {
            dismissPressedKeyPreview()
            dismissActiveKeyPopup()
            clearPreviewForTyping()
            handleBackspace()
            render()
        }
        numericEnterButton.setOnClickListener {
            dismissPressedKeyPreview()
            dismissActiveKeyPopup()
            clearPreviewForTyping()
            handleEnter()
            render()
        }
        numericMinusButton.setOnClickListener {
            dismissPressedKeyPreview()
            dismissActiveKeyPopup()
            clearPreviewForTyping()
            commitText("-")
            render()
        }
        numericLettersButton.setOnClickListener {
            dismissPressedKeyPreview()
            dismissActiveKeyPopup()
            characterMode = CharacterMode.LETTERS
            currentSymbolPage = SymbolPage.PRIMARY
            refreshShiftAfterEdit()
            render()
        }
        numericCommaButton.setOnClickListener {
            dismissPressedKeyPreview()
            dismissActiveKeyPopup()
            clearPreviewForTyping()
            commitText(",")
            render()
        }
        numericDotButton.setOnClickListener {
            dismissPressedKeyPreview()
            dismissActiveKeyPopup()
            clearPreviewForTyping()
            commitText(java.text.DecimalFormatSymbols.getInstance(currentLanguage.locale).decimalSeparator.toString())
            render()
        }

        enterButton.setOnClickListener {
            dismissPressedKeyPreview()
            dismissActiveKeyPopup()
            clearPreviewForTyping()
            handleEnter()
            render()
        }

        enigmaToggleButton.setOnClickListener {
            mode = KeyboardMode.ENIGMA
            val result = encryptCurrentInput(secureProfileStore, codec)
            setPreview(
                result.message,
                if (result.success) PreviewTone.SUCCESS else PreviewTone.ERROR,
            )
            render()
        }

        decryptButton.setOnClickListener {
            mode = KeyboardMode.DECRYPT
            when (val result = decryptService.decryptPrimaryClip()) {
                ClipboardDecryptResult.ClipboardEmpty -> {
                    setPreview(getString(R.string.keyboard_decrypt_empty), PreviewTone.ERROR)
                }

                ClipboardDecryptResult.MessageNotRecognized -> {
                    setPreview(getString(R.string.keyboard_decrypt_unrecognized), PreviewTone.ERROR)
                }

                ClipboardDecryptResult.WrongKeyOrInvalidMessage -> {
                    setPreview(getString(R.string.keyboard_decrypt_invalid), PreviewTone.ERROR)
                }

                is ClipboardDecryptResult.AlreadyConsumed -> {
                    setPreview(getString(R.string.decrypt_one_time_consumed, result.profileTitle), PreviewTone.ERROR)
                }

                is ClipboardDecryptResult.RequiresBiometric -> {
                    launchFromKeyboard(
                        Intent(this, DecryptGateActivity::class.java).apply {
                            putExtra(DecryptGateActivity.EXTRA_ENCODED_MESSAGE, result.encodedMessage)
                            putExtra(DecryptGateActivity.EXTRA_PROFILE_ID, result.profileId)
                        },
                    ).onFailure {
                        setPreview(getString(R.string.biometric_unavailable), PreviewTone.ERROR)
                    }
                }

                is ClipboardDecryptResult.Success -> {
                    setPreview(
                        getString(
                            R.string.keyboard_preview_decrypt_success,
                            result.profileTitle,
                            result.plaintext,
                        ),
                        PreviewTone.DECRYPTED,
                    )
                }
            }
            render()
        }

        clearButton.setOnClickListener {
            dismissPressedKeyPreview()
            dismissActiveKeyPopup()
            clearPendingCapsulesOnly()
            decryptService.clearPrimaryClip()
            currentInputConnection?.finishComposingText()
            clearPreviewForTyping()
            mode = KeyboardMode.IDLE
            render()
        }

        attachToggleButton.setOnClickListener {
            dismissPressedKeyPreview()
            dismissActiveKeyPopup()
        }

        attachBackButton.setOnClickListener {
            dismissPressedKeyPreview()
            dismissActiveKeyPopup()
            setAttachActionsExpanded(false)
        }

        fun launchPhotoCapsuleActivity() {
            clearPendingCapsulesOnly()
            launchFromKeyboard(
                Intent(this, PhotoCapsuleActivity::class.java).apply {
                    putExtra(PhotoCapsuleActivity.EXTRA_FROM_KEYBOARD, true)
                },
            ).onFailure {
                setPreview(getString(R.string.keyboard_photo_message_open_failed), PreviewTone.ERROR)
                render()
            }
            mode = KeyboardMode.IDLE
            render()
        }

        fun launchVideoCapsuleActivity() {
            clearPendingCapsulesOnly()
            launchFromKeyboard(
                    Intent(this, VideoCapsuleActivity::class.java).apply {
                        putExtra(VideoCapsuleActivity.EXTRA_FROM_KEYBOARD, true)
                    },
                )
            .onSuccess {
                setPreview(getString(R.string.open_video_capsule), PreviewTone.DEFAULT)
            }.onFailure {
                setPreview(getString(R.string.keyboard_video_capsule_open_failed), PreviewTone.ERROR)
            }
            mode = KeyboardMode.IDLE
            render()
        }

        fun sendLastAudioCapsule() {
            val capsule = lastAudioCapsuleFile ?: run {
                setPreview(
                    getString(R.string.keyboard_audio_capsule_not_ready),
                    PreviewTone.ERROR,
                )
                render()
                return
            }
            if (tryCommitCapsuleFile(capsule, MediaCapsuleType.AUDIO.capsuleMimeType)) {
                lastAudioCapsuleNeedsManualSend = false
                setPreview(getString(R.string.keyboard_voice_capsule_received_inline), PreviewTone.SUCCESS)
                render()
                return
            }
            launchFromKeyboard(
                    buildCapsuleShareIntent(capsule),
                )
            .onFailure {
                setPreview(getString(R.string.keyboard_voice_capsule_manual_send_failed), PreviewTone.ERROR)
                render()
                return
            }
            lastAudioCapsuleNeedsManualSend = true
            setPreview(getString(R.string.media_capsule_share), PreviewTone.DEFAULT)
            render()
        }

        fun handlePendingVideoCapsuleAction() {
            val capsule = lastVideoCapsuleFile ?: run {
                setPreview(getString(R.string.keyboard_video_capsule_not_ready), PreviewTone.ERROR)
                render()
                return
            }
            if (lastVideoCapsuleReadyForDirectInsert) {
                if (tryCommitCapsuleFile(capsule, MediaCapsuleType.VIDEO.capsuleMimeType)) {
                    clearPendingVideoCapsuleState()
                    setPreview(getString(R.string.keyboard_video_capsule_inserted), PreviewTone.SUCCESS)
                    render()
                    return
                }
                lastVideoCapsuleReadyForDirectInsert = false
                lastVideoCapsuleNeedsManualSend = true
            }

            launchFromKeyboard(
                    buildCapsuleShareIntent(capsule),
                )
            .onFailure {
                setPreview(getString(R.string.keyboard_video_capsule_open_send_failed), PreviewTone.ERROR)
                render()
                return
            }
            clearPendingVideoCapsuleState()
            setPreview(getString(R.string.media_capsule_share), PreviewTone.DEFAULT)
            render()
        }

        fun handlePendingPhotoCapsuleAction() {
            val capsule = lastPhotoCapsuleFile ?: run {
                setPreview(getString(R.string.photo_capsule_ready_manual), PreviewTone.ERROR)
                render()
                return
            }
            launchFromKeyboard(
                buildCapsuleShareIntent(capsule),
            ).onFailure {
                setPreview(getString(R.string.photo_capsule_open_send_failed), PreviewTone.ERROR)
                render()
                return
            }
            clearPendingPhotoCapsuleState()
            setPreview(getString(R.string.media_capsule_share), PreviewTone.DEFAULT)
            render()
        }

        audioCapsuleButton.setOnClickListener {
            if (inlineAudioRecorder != null) {
                stopInlineAudioRecording()
            } else {
                setAttachActionsExpanded(false)
                startInlineAudioRecording()
            }
        }

        audioCapsuleButton.setOnLongClickListener {
            sendLastAudioCapsule()
            true
        }

        audioRecordingStopButton.setOnClickListener {
            if (inlineAudioRecorder != null) {
                stopInlineAudioRecording()
            }
        }

        audioRecordingPauseButton.setOnClickListener {
            toggleInlineAudioPause()
        }

        playAudioCapsuleActionButton.setOnClickListener {
            when {
                lastPhotoCapsuleFile != null ->
                    launchFromKeyboard(buildPhotoCapsulePreviewIntent(lastPhotoCapsuleFile!!)).onFailure {
                        setPreview(getString(R.string.keyboard_photo_message_open_failed), PreviewTone.ERROR)
                        render()
                    }
                lastVideoCapsuleFile != null ->
                    launchFromKeyboard(buildVideoCapsulePreviewIntent(lastVideoCapsuleFile!!)).onFailure {
                        setPreview(getString(R.string.keyboard_video_capsule_open_failed), PreviewTone.ERROR)
                        render()
                    }
                else -> toggleInlineAudioPlayback()
            }
        }

        deleteCapsuleActionButton.setOnClickListener {
            clearPendingCapsulesOnly()
            clearPreviewForTyping()
            mode = KeyboardMode.IDLE
            render()
        }

        sendAudioCapsuleActionButton.setOnClickListener {
            if (lastPhotoCapsuleFile != null) {
                handlePendingPhotoCapsuleAction()
            } else if (lastVideoCapsuleFile != null) {
                handlePendingVideoCapsuleAction()
            } else {
                sendLastAudioCapsule()
            }
        }

        audioCapsuleActionText.setOnClickListener(null)
        audioCapsuleActionPanel.setOnClickListener(null)

        photoCapsuleButton.setOnClickListener {
            if (inlineAudioRecorder != null) {
                setPreview(getString(R.string.keyboard_stop_recording_before_photo_message), PreviewTone.ERROR)
                render()
            } else {
                setAttachActionsExpanded(false)
                launchPhotoCapsuleActivity()
            }
        }

        videoCapsuleButton.setOnClickListener {
            if (inlineAudioRecorder != null) {
                setPreview(
                    getString(R.string.keyboard_stop_recording_before_video_capsule),
                    PreviewTone.ERROR,
                )
                render()
            } else {
                setAttachActionsExpanded(false)
                launchVideoCapsuleActivity()
            }
        }

            refreshPendingCapsule()
            updateShiftState()
            render()
            root
        } catch (error: Throwable) {
            Log.e(TAG, "onCreateInputView failed", error)
            buildSafeFallbackInputView()
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        runCatching {
            refreshEnabledKeyboardLanguageState()
            characterMode = if (isNumericEditorInfo(info)) CharacterMode.NUMERIC else CharacterMode.LETTERS
            currentSymbolPage = SymbolPage.PRIMARY
            mode = KeyboardMode.IDLE
            previewMessage = null
            previewTone = PreviewTone.DEFAULT
            lastSpaceTapAt = 0L
            lastShiftTapAt = 0L
            capsLockEnabled = false
            shiftEnabled = characterMode == CharacterMode.LETTERS && shouldAutoCapitalizeFor(info)
            refreshPendingVideoCapsuleState?.invoke()
            applyKeyboardAppearanceToInputView?.invoke()
            renderInputView?.invoke()
        }.onFailure { error ->
            Log.e(TAG, "onStartInputView failed", error)
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        repeatHandler.removeCallbacksAndMessages(PENDING_CAPSULE_POLL_TOKEN)
    }

    override fun onWindowShown() {
        super.onWindowShown()
        runCatching {
            val newUpdateTime = packageUpdateTime()
            if (newUpdateTime != 0L && newUpdateTime != knownPackageUpdateTime) {
                knownPackageUpdateTime = newUpdateTime
                if (rebuildInputViewIfShown()) {
                    return
                }
            }
            refreshEnabledKeyboardLanguageState()
            refreshPendingVideoCapsuleState?.invoke()
            applyKeyboardAppearanceToInputView?.invoke()
            renderInputView?.invoke()
        }.onFailure { error ->
            Log.e(TAG, "onWindowShown failed", error)
        }
    }

    private fun View.doOnAttachRequestInsets() {
        if (isAttachedToWindow) {
            requestApplyInsets()
        } else {
            addOnAttachStateChangeListener(
                object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {
                        v.removeOnAttachStateChangeListener(this)
                        v.requestApplyInsets()
                    }

                    override fun onViewDetachedFromWindow(v: View) = Unit
                },
            )
        }
    }

    private fun buildSafeFallbackInputView(): View {
        fun fallbackDp(value: Int): Int =
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value.toFloat(),
                resources.displayMetrics,
            ).toInt()

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B1020"))
            setPadding(fallbackDp(12), fallbackDp(12), fallbackDp(12), fallbackDp(12))

            addView(TextView(context).apply {
                text = "VeilType keyboard failed to load"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            })

            addView(TextView(context).apply {
                text = "Open the app and reinstall this release build."
                setTextColor(Color.parseColor("#B7C4D8"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(0, fallbackDp(8), 0, 0)
            })
        }
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(
            oldSelStart,
            oldSelEnd,
            newSelStart,
            newSelEnd,
            candidatesStart,
            candidatesEnd,
        )
        if (pendingCapsuleStore.peek() != null) {
            refreshPendingVideoCapsuleState?.invoke()
            renderInputView?.invoke()
        }
        if (characterMode != CharacterMode.LETTERS) {
            shiftEnabled = false
            capsLockEnabled = false
            return
        }
        if (capsLockEnabled) {
            shiftEnabled = true
            return
        }
        shiftEnabled = shouldAutoCapitalizeFor(currentInputEditorInfo)
    }

    private fun packageUpdateTime(): Long =
        runCatching { packageManager.getPackageInfo(packageName, 0).lastUpdateTime }.getOrDefault(0L)

    private fun rebuildInputViewIfShown(): Boolean {
        if (rebuildingInputView || !isInputViewShown) return false
        rebuildingInputView = true
        return try {
            setInputView(onCreateInputView())
            true
        } catch (error: Throwable) {
            Log.e(TAG, "rebuildInputViewIfShown failed", error)
            false
        } finally {
            rebuildingInputView = false
        }
    }

    private fun isNumericEditorInfo(info: EditorInfo?): Boolean {
        val inputType = info?.inputType ?: return false
        return when (inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_NUMBER,
            InputType.TYPE_CLASS_PHONE,
            InputType.TYPE_CLASS_DATETIME,
            -> true
            else -> false
        }
    }

    private fun shouldAutoCapitalizeFor(info: EditorInfo?): Boolean {
        val inputType = info?.inputType ?: 0
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        if (
            variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        ) {
            return false
        }
        val capsMode = currentInputConnection?.getCursorCapsMode(inputType) ?: 0
        if (capsMode != 0) return true
        if (inputClass != InputType.TYPE_CLASS_TEXT) return false
        val before = currentInputConnection?.getTextBeforeCursor(32, 0)?.toString().orEmpty()
        if (before.isBlank()) return true
        return before.endsWith(". ") ||
            before.endsWith("! ") ||
            before.endsWith("? ") ||
            before.endsWith("… ")
    }

    private fun handleBackspace() {
        val inputConnection = currentInputConnection ?: return
        val selected = inputConnection.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            inputConnection.commitText("", 1)
        } else {
            val deletedInCodePoints = runCatching { inputConnection.deleteSurroundingTextInCodePoints(1, 0) }.getOrDefault(false)
            if (!deletedInCodePoints) {
                inputConnection.deleteSurroundingText(1, 0)
            }
        }
        lastSpaceTapAt = 0L
        if (!capsLockEnabled && characterMode == CharacterMode.LETTERS) {
            shiftEnabled = shouldAutoCapitalizeFor(currentInputEditorInfo)
        }
    }

    private fun handleEnter() {
        val inputConnection = currentInputConnection ?: return
        if (characterMode == CharacterMode.LETTERS) {
            val source = inputConnection.getTextBeforeCursor(32, 0)?.toString().orEmpty()
            val match = WORD_AT_END.find(source)
            if (match != null) {
                val originalWord = match.value
                val normalized = originalWord.lowercase(keyLanguageLocale())
                val replacementBase = when (currentLanguage) {
                    KeyboardLanguage.RU -> RU_AUTOCORRECT_V2[normalized]
                    KeyboardLanguage.EN -> EN_AUTOCORRECT_V2[normalized]
                    KeyboardLanguage.TR -> TR_AUTOCORRECT[normalized]
                    KeyboardLanguage.ES -> ES_AUTOCORRECT[normalized]
                    KeyboardLanguage.PT -> PT_AUTOCORRECT[normalized]
                    KeyboardLanguage.DE -> DE_AUTOCORRECT[normalized]
                    KeyboardLanguage.FR -> FR_AUTOCORRECT[normalized]
                    KeyboardLanguage.IT -> IT_AUTOCORRECT[normalized]
                }
                if (replacementBase != null) {
                    val replacement = when {
                        originalWord.all { it.isUpperCase() } -> replacementBase.uppercase(keyLanguageLocale())
                        originalWord.firstOrNull()?.isUpperCase() == true ->
                            replacementBase.replaceFirstChar { it.titlecase(keyLanguageLocale()) }
                        else -> replacementBase
                    }
                    inputConnection.deleteSurroundingText(originalWord.length, 0)
                    inputConnection.commitText(replacement, 1)
                    rememberRecentWord(replacement.lowercase(keyLanguageLocale()))
                }
            } else {
                source.split(Regex("\\s+")).lastOrNull()?.lowercase(keyLanguageLocale())
                    ?.takeIf { it.length >= 2 }?.let {
                    rememberRecentWord(it)
                }
            }
        }

        if (!sendDefaultEditorAction(false)) {
            inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
        lastSpaceTapAt = 0L
        if (!capsLockEnabled && characterMode == CharacterMode.LETTERS) {
            shiftEnabled = shouldAutoCapitalizeFor(currentInputEditorInfo)
        }
    }

    private data class EncryptResult(
        val message: String,
        val success: Boolean,
    )

    private fun encryptCurrentInput(
        secureProfileStore: SecureProfileStore,
        codec: Tl1MessageCodec,
    ): EncryptResult {
        val inputConnection = currentInputConnection
            ?: return EncryptResult(getString(R.string.keyboard_encrypt_failed), false)
        val profiles = secureProfileStore.listProfiles()
        val selectedId = selectedProfileId
        val profile = profiles.firstOrNull { it.id == selectedId }
            ?: ProfileSelectionPolicy.selectDefault(profiles)
            ?: return EncryptResult(getString(R.string.keyboard_encrypt_missing_profile), false)

        val plaintext = extractInputPlaintext()
            ?: return EncryptResult(getString(R.string.keyboard_encrypt_empty), false)

        return try {
            val key = secureProfileStore.loadProfileKey(profile)
            val encrypted = codec.encrypt(
                plaintext = plaintext,
                profileKey = key,
                profileHint = profile.profileHint,
            )
            val shareText = Tl1ShareEnvelope.wrap(
                encodedMessage = encrypted.encodedMessage,
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

            inputConnection.beginBatchEdit()
            replaceInputText(inputConnection, shareText)
            inputConnection.endBatchEdit()
            secureProfileStore.touchProfile(profile.id)

            EncryptResult(
                getString(
                    R.string.keyboard_preview_encrypt_success,
                    profile.title,
                    getString(R.string.keyboard_encrypt_success),
                ),
                true,
            )
        } catch (_: Exception) {
            EncryptResult(getString(R.string.keyboard_encrypt_failed), false)
        }
    }

    private fun replaceInputText(inputConnection: InputConnection, newText: String) {
        val selected = inputConnection.getSelectedText(0)?.toString()
        if (!selected.isNullOrEmpty()) {
            inputConnection.commitText(newText, 1)
            return
        }

        val extracted = inputConnection.getExtractedText(ExtractedTextRequest(), 0)
        val currentText = extracted?.text?.toString().orEmpty()
        if (currentText.isNotEmpty()) {
            inputConnection.setSelection(0, currentText.length)
        }
        inputConnection.commitText(newText, 1)
    }

    private fun extractInputPlaintext(): String? {
        val inputConnection = currentInputConnection ?: return null
        val selected = inputConnection.getSelectedText(0)?.toString()?.trim()
        if (!selected.isNullOrEmpty()) return selected

        val extracted = inputConnection.getExtractedText(ExtractedTextRequest(), 0)
        val full = extracted?.text?.toString()?.trim()
        return full?.takeIf { it.isNotEmpty() }
    }

    private fun loadRecentWords(): List<String> =
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(RECENT_WORDS_KEY, "")
            .orEmpty()
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(MAX_RECENT_WORDS)

    private fun rememberRecentWord(word: String) {
        val normalized = word.trim().lowercase(
            keyLanguageLocale(),
        )
        if (normalized.length < 2) return

        val updated = buildList {
            add(normalized)
            addAll(loadRecentWords().filter { it != normalized })
        }.take(MAX_RECENT_WORDS)

        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(RECENT_WORDS_KEY, updated.joinToString("\n"))
            .apply()
    }

    private fun keyLanguageLocale(): Locale = currentLanguage.locale

    private fun loadEnabledKeyboardLanguages(): List<KeyboardLanguage> {
        val enabledTags = keyboardLanguagePreferences.getEnabledLanguageTags()
        return KeyboardLanguage.entries.filter { it.localeTag in enabledTags }
            .ifEmpty { listOf(KeyboardLanguage.EN) }
    }

    private fun refreshEnabledKeyboardLanguageState() {
        val enabled = loadEnabledKeyboardLanguages()
        if (currentLanguage !in enabled) {
            currentLanguage = enabled.first()
        }
        if (lastKeyboardLanguage !in enabled || lastKeyboardLanguage == currentLanguage) {
            lastKeyboardLanguage = enabled.firstOrNull { it != currentLanguage } ?: currentLanguage
        }
        persistKeyboardLanguage(currentLanguage, lastKeyboardLanguage)
    }

    private fun loadKeyboardLanguagePreference(): KeyboardLanguage =
        KeyboardLanguage.fromStoredValue(
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEYBOARD_LANGUAGE_KEY, null),
        )

    private fun loadLastKeyboardLanguagePreference(current: KeyboardLanguage): KeyboardLanguage =
        KeyboardLanguage.fromStoredValue(
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(LAST_KEYBOARD_LANGUAGE_KEY, null),
        ).takeIf { it != current } ?: current

    private fun persistKeyboardLanguage(
        language: KeyboardLanguage,
        lastLanguage: KeyboardLanguage,
    ) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEYBOARD_LANGUAGE_KEY, language.name)
            .putString(LAST_KEYBOARD_LANGUAGE_KEY, lastLanguage.name)
            .apply()
    }

    private fun localizedSecretKind(kind: SecretSequenceKind): String = when (kind) {
        SecretSequenceKind.EMOJI_SEQUENCE -> getString(R.string.profile_kind_emoji_sequence)
        SecretSequenceKind.VISUAL_SEQUENCE -> getString(R.string.profile_kind_visual_sequence)
        SecretSequenceKind.CONTACT_HANDSHAKE -> getString(R.string.profile_kind_contact_handshake)
    }

    private fun localizedProfileStatus(status: KeyProfileStatus): String = when (status) {
        KeyProfileStatus.ACTIVE -> getString(R.string.profile_status_active)
        KeyProfileStatus.EXPIRING -> getString(R.string.profile_status_expiring)
        KeyProfileStatus.EXPIRED -> getString(R.string.profile_status_expired)
        KeyProfileStatus.ARCHIVED -> getString(R.string.profile_status_archived)
    }

    private companion object {
        const val TAG = "EnigmaKeyboardService"
        const val PREDICTIVE_TYPING_ENABLED = false
        const val SHIFT_UP_SYMBOL = "\u2191"
        const val SHIFT_LOCKED_SYMBOL = "\u21EA"
        val PENDING_CAPSULE_POLL_TOKEN = Any()
        val PREVIEW_CLEAR_TOKEN = Any()
        val WORD_AT_END = Regex("([\\p{L}]+)$")
        const val PREFS_NAME = "enigma_keyboard_prefs"
        const val RECENT_WORDS_KEY = "recent_words"
        const val KEYBOARD_LANGUAGE_KEY = "keyboard_language"
        const val LAST_KEYBOARD_LANGUAGE_KEY = "last_keyboard_language"
        const val MAX_RECENT_WORDS = 24
        val RU_AUTOCORRECT = mapOf(
            "превет" to "привет",
            "пивет" to "привет",
            "спосибо" to "спасибо",
            "щас" to "сейчас",
            "незнаю" to "не знаю",
            "вообщем" to "в общем",
            "пожалуста" to "пожалуйста",
        )
        val EN_AUTOCORRECT = mapOf(
            "teh" to "the",
            "adn" to "and",
            "wierd" to "weird",
            "recieve" to "receive",
            "dont" to "don't",
            "cant" to "can't",
            "wont" to "won't",
        )
        val TR_AUTOCORRECT = mapOf(
            "mrb" to "merhaba",
            "slm" to "selam",
            "tesekkurler" to "teşekkürler",
            "lutfen" to "lütfen",
            "gunaydin" to "günaydın",
            "iyiaksamlar" to "iyi akşamlar",
            "gorusuruz" to "görüşürüz",
        )
        val ES_AUTOCORRECT = mapOf(
            "qeu" to "que",
            "poruqe" to "porque",
            "gracais" to "gracias",
            "holaa" to "hola",
            "manana" to "mañana",
            "tambien" to "también",
            "adioss" to "adiós",
        )
        val PT_AUTOCORRECT = mapOf(
            "obg" to "obrigado",
            "ola" to "olá",
            "voce" to "você",
            "nao" to "não",
            "tambem" to "também",
            "ate" to "até",
            "amanha" to "amanhã",
        )
        val DE_AUTOCORRECT = mapOf(
            "dankeh" to "danke",
            "bitet" to "bitte",
            "heutte" to "heute",
            "tschus" to "tschüss",
            "gruse" to "grüße",
            "uber" to "über",
        )
        val FR_AUTOCORRECT = mapOf(
            "mercie" to "merci",
            "bonjor" to "bonjour",
            "silvouplait" to "s'il vous plaît",
            "aujourdhui" to "aujourd'hui",
            "tres" to "très",
            "desole" to "désolé",
        )
        val IT_AUTOCORRECT = mapOf(
            "grazzie" to "grazie",
            "perche" to "perché",
            "piu" to "più",
            "caffe" to "caffè",
            "lunedi" to "lunedì",
            "cosi" to "così",
        )
        val RU_SUGGESTIONS = listOf(
            "привет", "спасибо", "пожалуйста", "сейчас", "завтра", "сегодня",
            "хорошо", "понятно", "давай", "вообще", "клавиатура", "шифрование",
            "сообщение", "проект", "нормально", "отлично", "проверить", "попробовать",
        )
        val EN_SUGGESTIONS = listOf(
            "hello", "thanks", "please", "today", "tomorrow", "message",
            "keyboard", "project", "encrypt", "decrypt", "normal", "great",
            "check", "update", "continue", "camera", "screen", "working",
        )
        val RU_AUTOCORRECT_V2 = mapOf(
            "\u043f\u0440\u0435\u0432\u0435\u0442" to "\u043f\u0440\u0438\u0432\u0435\u0442",
            "\u043f\u0438\u0432\u0435\u0442" to "\u043f\u0440\u0438\u0432\u0435\u0442",
            "\u0441\u043f\u043e\u0441\u0438\u0431\u043e" to "\u0441\u043f\u0430\u0441\u0438\u0431\u043e",
            "\u0441\u043f\u0430\u0441\u0438\u0431\u0430" to "\u0441\u043f\u0430\u0441\u0438\u0431\u043e",
            "\u043f\u043e\u0436\u0430\u043b\u0443\u0441\u0442\u0430" to "\u043f\u043e\u0436\u0430\u043b\u0443\u0439\u0441\u0442\u0430",
            "\u0449\u0430\u0441" to "\u0441\u0435\u0439\u0447\u0430\u0441",
            "\u0441\u0435\u0447\u0430\u0441" to "\u0441\u0435\u0439\u0447\u0430\u0441",
            "\u043d\u0435\u0437\u043d\u0430\u044e" to "\u043d\u0435 \u0437\u043d\u0430\u044e",
            "\u043d\u0438\u0437\u043d\u0430\u044e" to "\u043d\u0435 \u0437\u043d\u0430\u044e",
            "\u0432\u043e\u043e\u0431\u0449\u0435\u043c" to "\u0432 \u043e\u0431\u0449\u0435\u043c",
            "\u0432\u0430\u0430\u0431\u0449\u0435" to "\u0432\u043e\u043e\u0431\u0449\u0435",
            "\u0447\u0442\u043e\u0431" to "\u0447\u0442\u043e\u0431\u044b",
            "\u043d\u043e\u0440\u043c" to "\u043d\u043e\u0440\u043c\u0430\u043b\u044c\u043d\u043e",
            "\u0441\u043f\u0441" to "\u0441\u043f\u0430\u0441\u0438\u0431\u043e",
            "\u043f\u0436" to "\u043f\u043e\u0436\u0430\u043b\u0443\u0439\u0441\u0442\u0430",
        )
        val EN_AUTOCORRECT_V2 = mapOf(
            "teh" to "the",
            "adn" to "and",
            "wierd" to "weird",
            "recieve" to "receive",
            "recieved" to "received",
            "seperate" to "separate",
            "definately" to "definitely",
            "acommodate" to "accommodate",
            "becuase" to "because",
            "thier" to "their",
            "freind" to "friend",
            "enviroment" to "environment",
            "langauge" to "language",
            "messsage" to "message",
            "keybaord" to "keyboard",
            "dont" to "don't",
            "cant" to "can't",
            "wont" to "won't",
            "im" to "I'm",
            "ive" to "I've",
            "ill" to "I'll",
            "id" to "I'd",
            "doesnt" to "doesn't",
            "isnt" to "isn't",
            "arent" to "aren't",
            "didnt" to "didn't",
            "wasnt" to "wasn't",
            "werent" to "weren't",
            "couldnt" to "couldn't",
            "shouldnt" to "shouldn't",
            "wouldnt" to "wouldn't",
        )
        val RU_SUGGESTIONS_V2 = listOf(
            "\u043f\u0440\u0438\u0432\u0435\u0442",
            "\u0441\u043f\u0430\u0441\u0438\u0431\u043e",
            "\u043f\u043e\u0436\u0430\u043b\u0443\u0439\u0441\u0442\u0430",
            "\u0441\u0435\u0439\u0447\u0430\u0441",
            "\u0441\u0435\u0433\u043e\u0434\u043d\u044f",
            "\u0437\u0430\u0432\u0442\u0440\u0430",
            "\u0443\u0442\u0440\u043e\u043c",
            "\u0432\u0435\u0447\u0435\u0440\u043e\u043c",
            "\u0445\u043e\u0440\u043e\u0448\u043e",
            "\u043e\u0442\u043b\u0438\u0447\u043d\u043e",
            "\u043d\u043e\u0440\u043c\u0430\u043b\u044c\u043d\u043e",
            "\u043f\u043e\u043d\u044f\u0442\u043d\u043e",
            "\u043b\u0430\u0434\u043d\u043e",
            "\u0434\u0430\u0432\u0430\u0439",
            "\u043a\u043e\u043d\u0435\u0447\u043d\u043e",
            "\u0432\u043e\u043e\u0431\u0449\u0435",
            "\u043f\u0440\u043e\u0441\u0442\u043e",
            "\u043f\u043e\u0442\u043e\u043c\u0443",
            "\u043f\u043e\u0442\u043e\u043c",
            "\u043c\u043e\u0436\u043d\u043e",
            "\u043d\u0443\u0436\u043d\u043e",
            "\u0431\u0443\u0434\u0435\u0442",
            "\u0441\u0434\u0435\u043b\u0430\u0442\u044c",
            "\u043f\u043e\u043f\u0440\u0430\u0432\u0438\u0442\u044c",
            "\u043f\u0440\u043e\u0432\u0435\u0440\u0438\u0442\u044c",
            "\u043f\u043e\u043f\u0440\u043e\u0431\u043e\u0432\u0430\u0442\u044c",
            "\u043a\u043b\u0430\u0432\u0438\u0430\u0442\u0443\u0440\u0430",
            "\u043a\u043b\u0430\u0432\u0438\u0448\u0430",
            "\u0441\u043e\u043e\u0431\u0449\u0435\u043d\u0438\u0435",
            "\u043a\u0430\u043f\u0441\u0443\u043b\u0430",
            "\u0433\u043e\u043b\u043e\u0441\u043e\u0432\u0430\u044f",
            "\u0432\u0438\u0434\u0435\u043e",
            "\u0444\u043e\u0442\u043e",
            "\u043a\u043b\u044e\u0447",
            "\u0448\u0438\u0444\u0440",
            "\u0448\u0438\u0444\u0440\u043e\u0432\u0430\u043d\u0438\u0435",
            "\u0440\u0430\u0441\u0448\u0438\u0444\u0440\u043e\u0432\u043a\u0430",
            "\u043f\u0440\u043e\u0435\u043a\u0442",
            "\u043f\u0440\u0438\u043b\u043e\u0436\u0435\u043d\u0438\u0435",
            "\u043d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0438",
            "\u044f\u0437\u044b\u043a",
            "\u0440\u0430\u0431\u043e\u0442\u0430\u0435\u0442",
            "\u0441\u0442\u0430\u0431\u0438\u043b\u044c\u043d\u043e",
            "\u0431\u044b\u0441\u0442\u0440\u043e",
            "\u0443\u0434\u043e\u0431\u043d\u043e",
        )
        val EN_SUGGESTIONS_V2 = listOf(
            "hello",
            "thanks",
            "thank",
            "please",
            "today",
            "tomorrow",
            "morning",
            "evening",
            "message",
            "messages",
            "keyboard",
            "keyboards",
            "project",
            "application",
            "settings",
            "language",
            "english",
            "russian",
            "encrypt",
            "encrypted",
            "encryption",
            "decrypt",
            "decryption",
            "capsule",
            "voice",
            "video",
            "photo",
            "camera",
            "screen",
            "button",
            "buttons",
            "layout",
            "preview",
            "stable",
            "working",
            "normal",
            "great",
            "check",
            "update",
            "continue",
            "improve",
            "feature",
            "features",
            "security",
            "private",
            "release",
            "build",
            "branch",
        )
        val RU_SOFT_AUTOCORRECT_V2 = mapOf(
            "\u043f\u0440\u0435\u0432\u0435\u0442" to "\u043f\u0440\u0438\u0432\u0435\u0442",
            "\u043f\u0438\u0432\u0435\u0442" to "\u043f\u0440\u0438\u0432\u0435\u0442",
            "\u0441\u043f\u043e\u0441\u0438\u0431\u043e" to "\u0441\u043f\u0430\u0441\u0438\u0431\u043e",
            "\u0441\u043f\u0430\u0441\u0438\u0431\u0430" to "\u0441\u043f\u0430\u0441\u0438\u0431\u043e",
            "\u043f\u043e\u0436\u0430\u043b\u0443\u0441\u0442\u0430" to "\u043f\u043e\u0436\u0430\u043b\u0443\u0439\u0441\u0442\u0430",
            "\u0449\u0430\u0441" to "\u0441\u0435\u0439\u0447\u0430\u0441",
            "\u0441\u0435\u0447\u0430\u0441" to "\u0441\u0435\u0439\u0447\u0430\u0441",
            "\u0447\u0442\u043e\u0431" to "\u0447\u0442\u043e\u0431\u044b",
            "\u043d\u043e\u0440\u043c" to "\u043d\u043e\u0440\u043c\u0430\u043b\u044c\u043d\u043e",
            "\u0441\u043f\u0441" to "\u0441\u043f\u0430\u0441\u0438\u0431\u043e",
        )
        val EN_SOFT_AUTOCORRECT_V2 = mapOf(
            "teh" to "the",
            "adn" to "and",
            "wierd" to "weird",
            "recieve" to "receive",
            "recieved" to "received",
            "seperate" to "separate",
            "definately" to "definitely",
            "becuase" to "because",
            "thier" to "their",
            "freind" to "friend",
            "langauge" to "language",
            "messsage" to "message",
            "keybaord" to "keyboard",
        )
        val RU_PRIORITY_SUGGESTIONS_V2 = setOf(
            "\u043f\u0440\u0438\u0432\u0435\u0442",
            "\u0441\u043f\u0430\u0441\u0438\u0431\u043e",
            "\u043f\u043e\u0436\u0430\u043b\u0443\u0439\u0441\u0442\u0430",
            "\u0434\u0430",
            "\u043d\u0435\u0442",
            "\u043b\u0430\u0434\u043d\u043e",
            "\u0445\u043e\u0440\u043e\u0448\u043e",
            "\u0441\u0435\u0439\u0447\u0430\u0441",
            "\u0441\u0435\u0433\u043e\u0434\u043d\u044f",
            "\u0437\u0430\u0432\u0442\u0440\u0430",
            "\u043c\u043e\u0436\u043d\u043e",
            "\u043d\u0443\u0436\u043d\u043e",
            "\u0431\u0443\u0434\u0435\u0442",
            "\u0441\u0434\u0435\u043b\u0430\u0442\u044c",
            "\u043f\u0440\u043e\u0441\u0442\u043e",
            "\u043f\u043e\u0442\u043e\u043c",
            "\u0442\u043e\u043b\u044c\u043a\u043e",
            "\u043f\u0440\u043e\u0431\u043b\u0435\u043c\u0430",
            "\u0440\u0430\u0431\u043e\u0442\u0430\u0435\u0442",
            "\u043f\u0440\u043e\u0432\u0435\u0440\u0438\u0442\u044c",
        )
        val EN_PRIORITY_SUGGESTIONS_V2 = setOf(
            "hello",
            "thanks",
            "please",
            "yes",
            "no",
            "okay",
            "today",
            "tomorrow",
            "message",
            "messages",
            "keyboard",
            "project",
            "settings",
            "language",
            "security",
            "private",
            "working",
            "stable",
            "problem",
            "check",
            "update",
            "continue",
            "feature",
            "support",
        )
        val TR_SUGGESTIONS = listOf(
            "merhaba", "selam", "teşekkürler", "lütfen", "bugün", "yarın",
            "mesaj", "klavye", "şifre", "şifreleme", "devam", "tamam",
            "görüşürüz", "günaydın", "iyi", "çalışıyor", "proje", "kontrol",
        )
        val ES_SUGGESTIONS = listOf(
            "hola", "gracias", "por favor", "hoy", "mañana", "mensaje",
            "teclado", "proyecto", "cifrar", "descifrar", "privado", "listo",
            "revisar", "seguir", "instalar", "seguridad", "clave", "chat",
        )
        val PT_SUGGESTIONS = listOf(
            "olá", "obrigado", "por favor", "hoje", "amanhã", "mensagem",
            "teclado", "projeto", "criptografar", "descriptografar", "privado", "pronto",
            "verificar", "continuar", "instalar", "segurança", "chave", "chat",
        )
        val DE_SUGGESTIONS = listOf(
            "hallo", "danke", "bitte", "heute", "morgen", "nachricht",
            "tastatur", "projekt", "verschlüsseln", "entschlüsseln", "privat", "bereit",
            "prüfen", "weiter", "sicherheit", "schlüssel", "chat", "funktioniert",
        )
        val FR_SUGGESTIONS = listOf(
            "bonjour", "merci", "s'il vous plaît", "aujourd'hui", "demain", "message",
            "clavier", "projet", "chiffrer", "déchiffrer", "privé", "prêt",
            "vérifier", "continuer", "sécurité", "clé", "discussion", "installer",
        )
        val IT_SUGGESTIONS = listOf(
            "ciao", "grazie", "per favore", "oggi", "domani", "messaggio",
            "tastiera", "progetto", "crittografare", "decifrare", "privato", "pronto",
            "verificare", "continuare", "sicurezza", "chiave", "chat", "installare",
        )

        fun levenshtein(left: String, right: String): Int {
            if (left == right) return 0
            if (left.isEmpty()) return right.length
            if (right.isEmpty()) return left.length

            val costs = IntArray(right.length + 1) { it }
            for (i in 1..left.length) {
                var previous = i - 1
                costs[0] = i
                for (j in 1..right.length) {
                    val current = costs[j]
                    val substitution = if (left[i - 1] == right[j - 1]) previous else previous + 1
                    costs[j] = minOf(
                        costs[j] + 1,
                        costs[j - 1] + 1,
                        substitution,
                    )
                    previous = current
                }
            }
            return costs[right.length]
        }
    }
}
