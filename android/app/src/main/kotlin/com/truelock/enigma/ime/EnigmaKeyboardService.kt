package com.truelock.enigma.ime

import android.Manifest
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.MotionEvent
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.EditorInfo
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Button
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import com.truelock.enigma.R
import com.truelock.enigma.clipboard.ClipboardDecryptResult
import com.truelock.enigma.clipboard.ClipboardDecryptService
import com.truelock.enigma.crypto.Tl1MessageCodec
import com.truelock.enigma.media.MediaCapsuleService
import com.truelock.enigma.media.MediaCapsuleType
import com.truelock.enigma.profiles.KeyProfile
import com.truelock.enigma.profiles.KeyProfileStatus
import com.truelock.enigma.profiles.ProfileSelectionPolicy
import com.truelock.enigma.storage.FileKeyProfileRepository
import com.truelock.enigma.storage.ProfileKeyVault
import com.truelock.enigma.storage.SecureProfileStore
import com.truelock.enigma.ui.AudioPermissionRequestActivity
import com.truelock.enigma.ui.VideoCapsuleActivity
import com.truelock.enigma.ui.localizedProfileStatus
import com.truelock.enigma.ui.localizedSecretKind

class EnigmaKeyboardService : InputMethodService() {
    private enum class KeyboardMode {
        IDLE,
        ENIGMA,
        DECRYPT,
    }

    private enum class KeyboardLanguage {
        RU,
        EN,
    }

    private enum class CharacterMode {
        LETTERS,
        SYMBOLS,
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

    private var mode: KeyboardMode = KeyboardMode.IDLE
    private var currentLanguage: KeyboardLanguage = KeyboardLanguage.RU
    private var characterMode: CharacterMode = CharacterMode.LETTERS
    private var currentSymbolPage: SymbolPage = SymbolPage.PRIMARY
    private var shiftEnabled: Boolean = false
    private var capsLockEnabled: Boolean = false
    private var previewMessage: String? = null
    private var previewTone: PreviewTone = PreviewTone.DEFAULT
    private val selectedProfileByPackage = mutableMapOf<String, String>()
    private val repeatHandler = Handler(Looper.getMainLooper())
    private var lastSpaceTapAt: Long = 0L
    private var lastShiftTapAt: Long = 0L
    private var recentWords: List<String> = emptyList()

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

    private val symbolRows = listOf(
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "-", "="),
        listOf("@", "#", "$", "%", "&", "*", "(", ")", "?", "!", "_"),
        listOf("/", "+", ":", ";", "\"", "'", "[", "]", "."),
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

    override fun onCreateInputView(): View {
        if (recentWords.isEmpty()) {
            recentWords = loadRecentWords()
        }
        val root = LayoutInflater.from(this).inflate(R.layout.input_view, null)
        val row1Layout = root.findViewById<LinearLayout>(R.id.keyRow1)
        val row2Layout = root.findViewById<LinearLayout>(R.id.keyRow2)
        val row3Layout = root.findViewById<LinearLayout>(R.id.keyRow3)
        val statusText = root.findViewById<TextView>(R.id.statusText)
        val profileInfoText = root.findViewById<TextView>(R.id.profileInfoText)
        val suggestionRow = root.findViewById<LinearLayout>(R.id.suggestionRow)
        val suggestionButtons = listOf(
            root.findViewById<EnigmaKeyView>(R.id.suggestionButton1),
            root.findViewById<EnigmaKeyView>(R.id.suggestionButton2),
            root.findViewById<EnigmaKeyView>(R.id.suggestionButton3),
        )
        val previewText = root.findViewById<TextView>(R.id.previewText)
        val previewScroll = root.findViewById<ScrollView>(R.id.previewScroll)
        val audioCapsuleActionPanel = root.findViewById<LinearLayout>(R.id.audioCapsuleActionPanel)
        val audioCapsuleActionText = root.findViewById<TextView>(R.id.audioCapsuleActionText)
        val sendAudioCapsuleActionButton = root.findViewById<Button>(R.id.sendAudioCapsuleActionButton)
        val enigmaToggleButton = root.findViewById<ImageButton>(R.id.enigmaToggleButton)
        val decryptButton = root.findViewById<ImageButton>(R.id.decryptButton)
        val keyButton = root.findViewById<ImageButton>(R.id.keyButton)
        val clearButton = root.findViewById<ImageButton>(R.id.clearButton)
        val audioCapsuleButton = root.findViewById<ImageButton>(R.id.audioCapsuleButton)
        val videoCapsuleButton = root.findViewById<ImageButton>(R.id.videoCapsuleButton)
        val sendAudioCapsuleButton = root.findViewById<ImageButton>(R.id.sendAudioCapsuleButton)
        val languageToggleButton = root.findViewById<EnigmaKeyView>(R.id.languageToggleButton)
        val commaButton = root.findViewById<EnigmaKeyView>(R.id.commaButton)
        val dotButton = root.findViewById<EnigmaKeyView>(R.id.dotButton)
        val spaceButton = root.findViewById<EnigmaKeyView>(R.id.spaceButton)
        val symbolsToggleButton = root.findViewById<EnigmaKeyView>(R.id.symbolsToggleButton)
        val shiftButton = root.findViewById<EnigmaKeyView>(R.id.shiftButton)
        val backspaceButton = root.findViewById<EnigmaKeyView>(R.id.backspaceButton)
        val enterButton = root.findViewById<EnigmaKeyView>(R.id.enterButton)
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
        var inlineAudioRecorder: MediaRecorder? = null
        var inlineAudioSourceFile: java.io.File? = null
        var lastAudioCapsuleFile: java.io.File? = null
        var lastAudioCapsuleNeedsManualSend = false
        var inlineAudioStartedAt = 0L

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
        lateinit var render: () -> Unit

        fun matchingProfiles(store: SecureProfileStore): List<KeyProfile> {
            val appPackage = currentInputEditorInfo?.packageName
            return store.listProfiles()
                .filter { profile ->
                    when {
                        appPackage == null -> profile.appPackage == null
                        profile.appPackage == null -> true
                        else -> profile.appPackage == appPackage
                    }
                }
                .filter { it.status == KeyProfileStatus.ACTIVE || it.status == KeyProfileStatus.EXPIRING }
                .sortedBy { it.title.lowercase() }
        }

        fun resolveSelectedProfile(store: SecureProfileStore): KeyProfile? {
            val appPackage = currentInputEditorInfo?.packageName
                ?: return ProfileSelectionPolicy.selectDefaultForApp(store.listProfiles(), null)

            val matches = matchingProfiles(store)
            val selectedId = selectedProfileByPackage[appPackage]
            val manual = matches.firstOrNull { it.id == selectedId }
            return manual ?: ProfileSelectionPolicy.selectDefaultForApp(matches, appPackage)
        }

        fun currentRows(): List<List<String>> = when (characterMode) {
            CharacterMode.SYMBOLS -> when (currentSymbolPage) {
                SymbolPage.PRIMARY -> symbolRows
                SymbolPage.SECONDARY -> symbolRowsExtra
            }
            CharacterMode.LETTERS -> when (currentLanguage) {
                KeyboardLanguage.RU -> ruRows
                KeyboardLanguage.EN -> enRows
            }
        }

        fun applyCase(value: String): String {
            if (characterMode == CharacterMode.SYMBOLS) return value
            return if (shiftEnabled || capsLockEnabled) value.uppercase() else value.lowercase()
        }

        fun dp(value: Int): Int =
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value.toFloat(),
                resources.displayMetrics,
            ).toInt()

        fun setPreview(message: String?, tone: PreviewTone = PreviewTone.DEFAULT) {
            previewMessage = message
            previewTone = tone
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
                if (recorder != null) {
                    render()
                    repeatHandler.postDelayed(this, 250L)
                }
            }
        }

        fun releaseInlineAudioRecorder() {
            repeatHandler.removeCallbacks(inlineAudioTicker)
            inlineAudioRecorder?.release()
            inlineAudioRecorder = null
            inlineAudioSourceFile = null
        }

        fun tryCommitCapsuleFile(file: java.io.File, mimeType: String): Boolean {
            val inputConnection = currentInputConnection ?: return false
            val editorInfo = currentInputEditorInfo ?: return false
            val supportedMimeTypes = EditorInfoCompat.getContentMimeTypes(editorInfo)
            val canCommit = supportedMimeTypes.any { supportedType ->
                ClipDescription.compareMimeTypes(mimeType, supportedType) ||
                    ClipDescription.compareMimeTypes("application/octet-stream", supportedType)
            }
            if (!canCommit) return false

            val uri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                file,
            )
            val description = ClipDescription(
                file.name,
                arrayOf(mimeType, "application/octet-stream"),
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

        fun startInlineAudioRecording() {
            val profile = resolveSelectedProfile(secureProfileStore)
                ?: run {
                    setPreview(getString(R.string.keyboard_encrypt_missing_profile), PreviewTone.ERROR)
                    render()
                    return
                }
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestHideSelf(0)
                startActivity(
                    Intent(this, AudioPermissionRequestActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
                inlineAudioRecorder = MediaRecorder().apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setOutputFile(sourceFile.absolutePath)
                    prepare()
                    start()
                }
                inlineAudioStartedAt = System.currentTimeMillis()
                lastAudioCapsuleFile = null
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
            if (recorder == null || sourceFile == null || profile == null) {
                releaseInlineAudioRecorder()
                setPreview(getString(R.string.media_capsule_error_no_recording), PreviewTone.ERROR)
                render()
                return
            }

            runCatching {
                recorder.stop()
                recorder.reset()
                val durationMs = (System.currentTimeMillis() - inlineAudioStartedAt).coerceAtLeast(1000L)
                val capsule = mediaCapsuleService.encryptFile(
                    sourceFile = sourceFile,
                    type = MediaCapsuleType.AUDIO,
                    mimeType = "audio/mp4",
                    durationMs = durationMs,
                    profile = profile,
                )
                lastAudioCapsuleFile = capsule
                if (tryCommitCapsuleFile(capsule, MediaCapsuleType.AUDIO.capsuleMimeType)) {
                    lastAudioCapsuleNeedsManualSend = false
                    setPreview("Voice capsule inserted into chat.", PreviewTone.SUCCESS)
                } else {
                    lastAudioCapsuleNeedsManualSend = true
                    setPreview(
                        "Voice capsule created. This chat does not support direct capsule insert from the keyboard. Hold the mic button to send it manually.",
                        PreviewTone.DEFAULT,
                    )
                }
            }.onFailure {
                setPreview(getString(R.string.media_capsule_error_encrypt), PreviewTone.ERROR)
            }

            releaseInlineAudioRecorder()
            mode = KeyboardMode.IDLE
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

        fun textBeforeCursor(maxChars: Int = 4): String =
            currentInputConnection?.getTextBeforeCursor(maxChars, 0)?.toString().orEmpty()

        fun applyReplacementCase(original: String, replacement: String): String = when {
            original.all { it.isUpperCase() } -> replacement.uppercase()
            original.firstOrNull()?.isUpperCase() == true -> replacement.replaceFirstChar { it.uppercase() }
            else -> replacement
        }

        fun autocorrectBeforeSeparator() {
            if (characterMode != CharacterMode.LETTERS) return

            val source = textBeforeCursor(32)
            val match = WORD_AT_END.find(source) ?: return
            val originalWord = match.value
            val normalized = originalWord.lowercase()
            val replacementBase = when (currentLanguage) {
                KeyboardLanguage.RU -> RU_AUTOCORRECT[normalized]
                KeyboardLanguage.EN -> EN_AUTOCORRECT[normalized]
            } ?: return

            val replacement = applyReplacementCase(originalWord, replacementBase)
            currentInputConnection?.deleteSurroundingText(originalWord.length, 0)
            currentInputConnection?.commitText(replacement, 1)
            rememberRecentWord(replacement.lowercase())
            recentWords = loadRecentWords()
        }

        fun currentWordBeforeCursor(): String? =
            WORD_AT_END.find(textBeforeCursor(48))?.value

        fun replaceCurrentWord(replacement: String) {
            val currentWord = currentWordBeforeCursor() ?: return
            currentInputConnection?.deleteSurroundingText(currentWord.length, 0)
            currentInputConnection?.commitText("$replacement ", 1)
            rememberRecentWord(replacement.lowercase())
            recentWords = loadRecentWords()
            val inputType = currentInputEditorInfo?.inputType ?: 0
            val capsMode = currentInputConnection?.getCursorCapsMode(inputType) ?: 0
            shiftEnabled = characterMode == CharacterMode.LETTERS && capsMode != 0
        }

        fun suggestionsForWord(word: String): List<String> {
            if (word.length < 2 || characterMode != CharacterMode.LETTERS) return emptyList()
            val normalized = word.lowercase()
            val lexicon = when (currentLanguage) {
                KeyboardLanguage.RU -> RU_SUGGESTIONS
                KeyboardLanguage.EN -> EN_SUGGESTIONS
            }
            val correction = when (currentLanguage) {
                KeyboardLanguage.RU -> RU_AUTOCORRECT[normalized]
                KeyboardLanguage.EN -> EN_AUTOCORRECT[normalized]
            }

            val prefixMatches = lexicon
                .filter { it.startsWith(normalized) && it != normalized }
                .take(3)

            val fuzzyMatches = lexicon
                .asSequence()
                .filter { it != normalized }
                .map { candidate -> candidate to levenshtein(normalized, candidate) }
                .filter { (_, distance) -> distance in 1..2 }
                .sortedBy { it.second }
                .map { it.first }
                .take(3)
                .toList()

            return buildList {
                if (!correction.isNullOrBlank()) add(correction)
                addAll(prefixMatches)
                addAll(fuzzyMatches)
                addAll(recentWords.filter { it.startsWith(normalized) && it != normalized })
            }
                .distinct()
                .take(3)
                .map { applyReplacementCase(word, it) }
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
            val inputType = currentInputEditorInfo?.inputType ?: 0
            val capsMode = currentInputConnection?.getCursorCapsMode(inputType) ?: 0
            shiftEnabled = capsMode != 0
        }

        fun commitSmartSpace() {
            autocorrectBeforeSeparator()
            currentWordBeforeCursor()?.lowercase()?.let {
                rememberRecentWord(it)
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
            autocorrectBeforeSeparator()
            currentWordBeforeCursor()?.lowercase()?.let {
                rememberRecentWord(it)
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
                else -> getString(R.string.keyboard_action_enter)
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
            val inputType = currentInputEditorInfo?.inputType ?: 0
            val capsMode = currentInputConnection?.getCursorCapsMode(inputType) ?: 0
            shiftEnabled = capsMode != 0
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
                characterMode == CharacterMode.SYMBOLS -> {
                    row1Layout.setPadding(0, 0, 0, 0)
                    row2Layout.setPadding(dp(6), 0, dp(6), 0)
                    row3Layout.setPadding(0, 0, 0, 0)
                }
                currentLanguage == KeyboardLanguage.EN -> {
                    row1Layout.setPadding(dp(10), 0, dp(10), 0)
                    row2Layout.setPadding(dp(28), 0, dp(28), 0)
                    row3Layout.setPadding(0, 0, 0, 0)
                }
                else -> {
                    row1Layout.setPadding(0, 0, 0, 0)
                    row2Layout.setPadding(dp(8), 0, dp(8), 0)
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
                KeyboardLanguage.RU -> getString(R.string.keyboard_action_language_en)
                KeyboardLanguage.EN -> getString(R.string.keyboard_action_language_ru)
            }
            symbolsToggleButton.text = if (characterMode == CharacterMode.SYMBOLS) {
                if (currentSymbolPage == SymbolPage.PRIMARY) {
                    getString(R.string.keyboard_action_symbols_alt)
                } else {
                    getString(R.string.keyboard_action_letters)
                }
            } else {
                getString(R.string.keyboard_action_symbols)
            }
            shiftButton.text = if (shiftEnabled) {
                if (capsLockEnabled) {
                    getString(R.string.keyboard_action_caps_lock)
                } else {
                    getString(R.string.keyboard_action_shift_locked)
                }
            } else {
                getString(R.string.keyboard_action_shift)
            }
            shiftButton.isEnabled = characterMode == CharacterMode.LETTERS
            commaButton.text = if (characterMode == CharacterMode.SYMBOLS) "." else ","
            dotButton.text = if (characterMode == CharacterMode.SYMBOLS) "/" else "."
            updateRowInsets()
            updateEnterKey()
        }

        fun renderSuggestions() {
            val suggestions = when {
                previewTone == PreviewTone.DECRYPTED -> emptyList()
                characterMode != CharacterMode.LETTERS -> emptyList()
                !currentWordBeforeCursor().isNullOrBlank() -> suggestionsForWord(currentWordBeforeCursor().orEmpty())
                else -> {
                    val fallback = when (currentLanguage) {
                        KeyboardLanguage.RU -> RU_SUGGESTIONS
                        KeyboardLanguage.EN -> EN_SUGGESTIONS
                    }
                    (recentWords + fallback).distinct().take(3)
                }
            }
            suggestionRow.visibility = if (suggestions.isEmpty()) View.GONE else View.VISIBLE
            suggestionButtons.forEachIndexed { index, button ->
                val suggestion = suggestions.getOrNull(index)
                if (suggestion == null) {
                    button.visibility = View.GONE
                    button.text = ""
                    button.tag = null
                } else {
                    button.visibility = View.VISIBLE
                    button.text = suggestion
                    button.tag = suggestion
                    button.alpha = if (index == 0) 1f else 0.9f
                    button.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (index == 0) 13f else 12f)
                }
            }
        }

        render = {
            val selectedProfile = resolveSelectedProfile(secureProfileStore)
            profileInfoText.text = selectedProfile?.let {
                getString(
                    R.string.keyboard_profile_info_format,
                    it.title,
                    localizedSecretKind(it.secretSequenceKind),
                    localizedProfileStatus(it.status),
                )
            } ?: getString(R.string.keyboard_no_profiles)
            when (mode) {
                KeyboardMode.IDLE -> {
                    statusText.setText(R.string.keyboard_status_idle)
                    previewText.text = previewMessage ?: getString(R.string.keyboard_preview_placeholder)
                }

                KeyboardMode.ENIGMA -> {
                    statusText.setText(R.string.keyboard_status_enigma)
                    previewText.text = previewMessage ?: getString(R.string.keyboard_preview_encrypt_hint)
                }

                KeyboardMode.DECRYPT -> {
                    statusText.setText(R.string.keyboard_status_decrypt)
                    previewText.text = previewMessage ?: getString(R.string.keyboard_preview_decrypt_hint)
                }
            }
            previewScroll.visibility = if (previewMessage.isNullOrBlank()) View.GONE else View.VISIBLE
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
            audioCapsuleButton.alpha = 1f
            videoCapsuleButton.alpha = if (inlineAudioRecorder != null) 0.45f else 1f
            sendAudioCapsuleButton.visibility =
                if (lastAudioCapsuleNeedsManualSend && lastAudioCapsuleFile != null && inlineAudioRecorder == null) View.VISIBLE else View.GONE
            sendAudioCapsuleButton.alpha =
                if (lastAudioCapsuleNeedsManualSend && lastAudioCapsuleFile != null && inlineAudioRecorder == null) 1f else 0.45f
            audioCapsuleActionPanel.visibility =
                if (lastAudioCapsuleNeedsManualSend && lastAudioCapsuleFile != null && inlineAudioRecorder == null) View.VISIBLE else View.GONE
            audioCapsuleActionText.text =
                if (lastAudioCapsuleNeedsManualSend) "Голосовая капсула готова. Отправь её из этой плашки."
                else "Голосовая капсула готова к отправке"
            previewScroll.post { previewScroll.scrollTo(0, 0) }
            updateCharacterKeys()
            renderSuggestions()
            if (!previewMessage.isNullOrBlank()) {
                schedulePreviewClear()
            }
        }

        rowButtons.flatten().forEach { it.applyCharacterStyle() }

        utilityButtons.forEach { button ->
            button.applyUtilityStyle()
        }
        suggestionButtons.forEach { button ->
            button.applySuggestionStyle()
        }

        rowButtons.flatten().forEach { button ->
            button.setOnClickListener {
                val value = button.tag as? String ?: return@setOnClickListener
                clearPreviewForTyping()
                commitText(value)
                render()
            }
        }
        suggestionButtons.forEach { button ->
            button.setOnClickListener {
                val suggestion = button.tag as? String ?: return@setOnClickListener
                clearPreviewForTyping()
                replaceCurrentWord(suggestion)
                render()
            }
        }

        languageToggleButton.setOnClickListener {
            currentLanguage = when (currentLanguage) {
                KeyboardLanguage.RU -> KeyboardLanguage.EN
                KeyboardLanguage.EN -> KeyboardLanguage.RU
            }
            characterMode = CharacterMode.LETTERS
            capsLockEnabled = false
            previewMessage = getString(
                R.string.keyboard_preview_language_switched,
                if (currentLanguage == KeyboardLanguage.RU) "RU" else "EN",
            )
            previewTone = PreviewTone.DEFAULT
            mode = KeyboardMode.IDLE
            render()
        }

        symbolsToggleButton.setOnClickListener {
            when {
                characterMode == CharacterMode.LETTERS -> {
                    characterMode = CharacterMode.SYMBOLS
                    currentSymbolPage = SymbolPage.PRIMARY
                }
                currentSymbolPage == SymbolPage.PRIMARY -> {
                    currentSymbolPage = SymbolPage.SECONDARY
                }
                else -> {
                    characterMode = CharacterMode.LETTERS
                    currentSymbolPage = SymbolPage.PRIMARY
                }
            }
            previewMessage = if (characterMode == CharacterMode.SYMBOLS) {
                if (currentSymbolPage == SymbolPage.PRIMARY) {
                    getString(R.string.keyboard_preview_symbols_enabled)
                } else {
                    getString(R.string.keyboard_preview_symbols_alt_enabled)
                }
            } else {
                getString(R.string.keyboard_preview_letters_enabled)
            }
            previewTone = PreviewTone.DEFAULT
            shiftEnabled = false
            capsLockEnabled = false
            mode = KeyboardMode.IDLE
            render()
        }

        shiftButton.setOnClickListener {
            if (characterMode == CharacterMode.LETTERS) {
                val now = SystemClock.uptimeMillis()
                val doubleTap = now - lastShiftTapAt < 450L
                when {
                    doubleTap -> {
                        capsLockEnabled = !capsLockEnabled
                        shiftEnabled = capsLockEnabled
                        previewMessage = if (capsLockEnabled) {
                            getString(R.string.keyboard_preview_caps_lock_enabled)
                        } else {
                            getString(R.string.keyboard_preview_caps_lock_disabled)
                        }
                    }
                    capsLockEnabled -> {
                        capsLockEnabled = false
                        shiftEnabled = false
                        previewMessage = getString(R.string.keyboard_preview_caps_lock_disabled)
                    }
                    else -> {
                        shiftEnabled = !shiftEnabled
                        previewMessage = if (shiftEnabled) {
                            getString(R.string.keyboard_preview_shift_enabled)
                        } else {
                            getString(R.string.keyboard_preview_shift_disabled)
                        }
                    }
                }
                lastShiftTapAt = now
                previewTone = PreviewTone.DEFAULT
                render()
            }
        }

        shiftButton.setOnLongClickListener {
            if (characterMode == CharacterMode.LETTERS) {
                capsLockEnabled = !capsLockEnabled
                shiftEnabled = capsLockEnabled
                previewMessage = if (capsLockEnabled) {
                    getString(R.string.keyboard_preview_caps_lock_enabled)
                } else {
                    getString(R.string.keyboard_preview_caps_lock_disabled)
                }
                previewTone = PreviewTone.DEFAULT
                render()
                true
            } else {
                false
            }
        }

        spaceButton.setOnClickListener {
            clearPreviewForTyping()
            commitSmartSpace()
            render()
        }

        commaButton.setOnClickListener {
            clearPreviewForTyping()
            commitSmartPunctuation(if (characterMode == CharacterMode.SYMBOLS) "." else ",")
            render()
        }

        dotButton.setOnClickListener {
            clearPreviewForTyping()
            commitSmartPunctuation(if (characterMode == CharacterMode.SYMBOLS) "/" else ".")
            render()
        }

        backspaceButton.setOnClickListener {
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
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                repeatHandler.removeCallbacks(repeatBackspace)
            }
            false
        }

        enterButton.setOnClickListener {
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

        keyButton.setOnClickListener {
            val appPackage = currentInputEditorInfo?.packageName
            val matches = matchingProfiles(secureProfileStore)
            statusText.setText(R.string.keyboard_status_key)
            previewMessage = if (matches.isEmpty()) {
                getString(
                    if (appPackage == null) R.string.keyboard_no_profiles else R.string.keyboard_no_app_profiles,
                )
            } else {
                val currentId = appPackage?.let { selectedProfileByPackage[it] }
                val currentIndex = matches.indexOfFirst { it.id == currentId }
                val nextIndex = if (currentIndex == -1) 0 else (currentIndex + 1) % matches.size
                val nextProfile = matches[nextIndex]
                if (appPackage != null) {
                    selectedProfileByPackage[appPackage] = nextProfile.id
                }
                secureProfileStore.touchProfile(nextProfile.id)
                getString(
                    R.string.keyboard_preview_profile_switched,
                    nextProfile.title,
                    localizedSecretKind(nextProfile.secretSequenceKind),
                )
            }
            previewTone = if (matches.isEmpty()) PreviewTone.ERROR else PreviewTone.SUCCESS
            previewText.text = previewMessage
            render()
        }

        clearButton.setOnClickListener {
            decryptService.clearPrimaryClip()
            setPreview(getString(R.string.keyboard_clipboard_cleared), PreviewTone.DEFAULT)
            mode = KeyboardMode.IDLE
            render()
        }

        fun launchVideoCapsuleActivity() {
            requestHideSelf(0)
            runCatching {
                startActivity(
                    Intent(this, VideoCapsuleActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                )
                setPreview(getString(R.string.open_video_capsule), PreviewTone.DEFAULT)
            }.onFailure {
                setPreview("Video capsule could not be opened from the keyboard.", PreviewTone.ERROR)
            }
            mode = KeyboardMode.IDLE
            render()
        }

        fun openLastAudioCapsuleFallback() {
            val capsule = lastAudioCapsuleFile ?: run {
                setPreview(
                    "No audio capsule is ready for manual sending yet.",
                    PreviewTone.ERROR,
                )
                render()
                return
            }
            requestHideSelf(0)
            runCatching {
                startActivity(
                    Intent(Intent.ACTION_SEND).apply {
                        type = MediaCapsuleType.AUDIO.capsuleMimeType
                        putExtra(
                            Intent.EXTRA_STREAM,
                            FileProvider.getUriForFile(
                                this@EnigmaKeyboardService,
                                "${applicationContext.packageName}.fileprovider",
                                capsule,
                            ),
                        )
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                )
            }.onFailure {
                setPreview("Manual sending failed for the last voice capsule.", PreviewTone.ERROR)
                render()
                return
            }
            lastAudioCapsuleNeedsManualSend = false
            setPreview(getString(R.string.media_capsule_share), PreviewTone.DEFAULT)
            render()
        }

        audioCapsuleButton.setOnClickListener {
            if (inlineAudioRecorder != null) {
                stopInlineAudioRecording()
            } else {
                startInlineAudioRecording()
            }
        }

        audioCapsuleButton.setOnLongClickListener {
            openLastAudioCapsuleFallback()
            true
        }

        sendAudioCapsuleButton.setOnClickListener {
            openLastAudioCapsuleFallback()
        }

        sendAudioCapsuleActionButton.setOnClickListener {
            openLastAudioCapsuleFallback()
        }

        videoCapsuleButton.setOnClickListener {
            if (inlineAudioRecorder != null) {
                setPreview(
                    "Stop voice recording before opening video capsule.",
                    PreviewTone.ERROR,
                )
                render()
            } else {
                launchVideoCapsuleActivity()
            }
        }

        updateShiftState()
        render()
        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        mode = KeyboardMode.IDLE
        previewMessage = null
        previewTone = PreviewTone.DEFAULT
        lastSpaceTapAt = 0L
        lastShiftTapAt = 0L
        capsLockEnabled = false
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
        if (characterMode != CharacterMode.LETTERS) {
            shiftEnabled = false
            capsLockEnabled = false
            return
        }
        if (capsLockEnabled) {
            shiftEnabled = true
            return
        }
        val inputType = currentInputEditorInfo?.inputType ?: 0
        val capsMode = currentInputConnection?.getCursorCapsMode(inputType) ?: 0
        shiftEnabled = capsMode != 0
    }

    private fun handleBackspace() {
        val inputConnection = currentInputConnection ?: return
        val selected = inputConnection.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            inputConnection.commitText("", 1)
        } else {
            inputConnection.deleteSurroundingText(1, 0)
        }
        lastSpaceTapAt = 0L
    }

    private fun handleEnter() {
        val inputConnection = currentInputConnection ?: return
        if (characterMode == CharacterMode.LETTERS) {
            val source = inputConnection.getTextBeforeCursor(32, 0)?.toString().orEmpty()
            val match = WORD_AT_END.find(source)
            if (match != null) {
                val originalWord = match.value
                val normalized = originalWord.lowercase()
                val replacementBase = when (currentLanguage) {
                    KeyboardLanguage.RU -> RU_AUTOCORRECT[normalized]
                    KeyboardLanguage.EN -> EN_AUTOCORRECT[normalized]
                }
                if (replacementBase != null) {
                    val replacement = when {
                        originalWord.all { it.isUpperCase() } -> replacementBase.uppercase()
                        originalWord.firstOrNull()?.isUpperCase() == true -> replacementBase.replaceFirstChar { it.uppercase() }
                        else -> replacementBase
                    }
                    inputConnection.deleteSurroundingText(originalWord.length, 0)
                    inputConnection.commitText(replacement, 1)
                    rememberRecentWord(replacement.lowercase())
                }
            } else {
                source.split(Regex("\\s+")).lastOrNull()?.lowercase()?.takeIf { it.length >= 2 }?.let {
                    rememberRecentWord(it)
                }
            }
        }
        if (!sendDefaultEditorAction(false)) {
            inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
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
        val appPackage = currentInputEditorInfo?.packageName
        val profiles = secureProfileStore.listProfiles()
        val selectedId = if (appPackage != null) selectedProfileByPackage[appPackage] else null
        val profile = profiles.firstOrNull { it.id == selectedId }
            ?: ProfileSelectionPolicy.selectDefaultForApp(profiles, appPackage)
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

            inputConnection.beginBatchEdit()
            replaceInputText(inputConnection, encrypted.encodedMessage)
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
        val normalized = word.trim().lowercase()
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

    private companion object {
        val PREVIEW_CLEAR_TOKEN = Any()
        val WORD_AT_END = Regex("([\\p{L}]+)$")
        const val PREFS_NAME = "enigma_keyboard_prefs"
        const val RECENT_WORDS_KEY = "recent_words"
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
