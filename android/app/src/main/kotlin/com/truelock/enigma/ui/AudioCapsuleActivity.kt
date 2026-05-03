package com.truelock.enigma.ui

import android.Manifest
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.truelock.enigma.R
import com.truelock.enigma.databinding.ActivityAudioCapsuleBinding
import com.truelock.enigma.media.DecryptedMediaCapsule
import com.truelock.enigma.media.MediaCapsuleService
import com.truelock.enigma.media.MediaCapsuleType
import com.truelock.enigma.media.PendingCapsuleStore
import com.truelock.enigma.media.createSpeechMediaRecorder
import com.truelock.enigma.media.describeAudioSource
import com.truelock.enigma.profiles.KeyProfile
import com.truelock.enigma.profiles.KeyProfileStatus
import com.truelock.enigma.security.BiometricDecryptHelper
import com.truelock.enigma.sharing.CapsuleShareText
import com.truelock.enigma.storage.FileKeyProfileRepository
import com.truelock.enigma.storage.ProfileKeyVault
import com.truelock.enigma.storage.SecureProfileStore
import java.io.File

class AudioCapsuleActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "AudioCapsuleActivity"
        const val EXTRA_PREVIEW_CAPSULE_PATH = "preview_capsule_path"
    }

    private lateinit var binding: ActivityAudioCapsuleBinding
    private lateinit var secureProfileStore: SecureProfileStore
    private lateinit var mediaCapsuleService: MediaCapsuleService
    private lateinit var pendingCapsuleStore: PendingCapsuleStore
    private lateinit var biometricHelper: BiometricDecryptHelper
    private val audioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }

    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var recordingFile: File? = null
    private var currentCapsuleFile: File? = null
    private var currentDecrypted: DecryptedMediaCapsule? = null
    private var currentPlaybackFile: File? = null
    private var isPlaybackTransitioning = false
    private var playbackRouteActive = false
    private var playbackPreviousMode: Int = AudioManager.MODE_NORMAL
    private var playbackPreviousSpeakerphoneOn = false
    private var recordingStartedAt = 0L
    private var lastDurationMs = 0L
    private val timerRunnable = object : Runnable {
        override fun run() {
            val duration = if (recorder != null) {
                (System.currentTimeMillis() - recordingStartedAt).coerceAtLeast(0L)
            } else {
                lastDurationMs
            }
            binding.timerText.text = formatDuration(duration)
            if (recorder != null) {
                binding.timerText.postDelayed(this, 250L)
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startRecording()
        } else {
            renderStatus(getString(R.string.media_capsule_error_audio_permission))
        }
    }

    private val openCapsuleLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let(::importCapsule)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAudioCapsuleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        secureProfileStore = SecureProfileStore(
            repository = FileKeyProfileRepository(applicationContext),
            keyVault = ProfileKeyVault(),
        )
        mediaCapsuleService = MediaCapsuleService(applicationContext, secureProfileStore)
        pendingCapsuleStore = PendingCapsuleStore(applicationContext)
        biometricHelper = BiometricDecryptHelper(this)

        binding.recordButton.setOnClickListener {
            if (recorder != null) {
                stopRecordingAndEncrypt()
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
        binding.openCapsuleButton.setOnClickListener { openCapsuleLauncher.launch(arrayOf("*/*")) }
        binding.playButton.setOnClickListener { playCurrentCapsule() }
        binding.shareButton.setOnClickListener { shareCurrentCapsule() }

        renderStatus(getString(R.string.media_capsule_status_ready))
        syncControls()
        handleIncomingIntent(intent)
    }

    override fun onStop() {
        super.onStop()
        val pendingRecordingFile = recordingFile
        stopPlayback(deletePlaybackFile = true)
        releaseRecorder()
        deleteQuietly(currentPlaybackFile)
        deleteQuietly(pendingRecordingFile)
        binding.timerText.removeCallbacks(timerRunnable)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun startRecording() {
        val profile = resolveActiveProfile() ?: return
        stopPlayback(deletePlaybackFile = true)
        deleteQuietly(currentPlaybackFile)
        deleteQuietly(recordingFile)
        recordingFile = mediaCapsuleService.createRecordingFile(MediaCapsuleType.AUDIO, "m4a")
        releaseRecorder()
        val preparedRecorder = createSpeechMediaRecorder(recordingFile!!.absolutePath)
        recorder = preparedRecorder.recorder
        Log.d(
            TAG,
            "startRecording source=${describeAudioSource(preparedRecorder.audioSource)} file=${recordingFile?.name}",
        )
        recordingStartedAt = System.currentTimeMillis()
        lastDurationMs = 0L
        currentCapsuleFile = null
        currentDecrypted = null
        currentPlaybackFile = null
        renderStatus(getString(R.string.media_capsule_status_recording, profile.title))
        binding.timerText.removeCallbacks(timerRunnable)
        binding.timerText.post(timerRunnable)
        syncControls()
    }

    private fun stopRecordingAndEncrypt() {
        val profile = resolveActiveProfile() ?: return
        val sourceFile = recordingFile
        val activeRecorder = recorder ?: run {
            renderStatus(getString(R.string.media_capsule_error_no_recording))
            return
        }

        runCatching {
            activeRecorder.stop()
            activeRecorder.reset()
            val durationMs = (System.currentTimeMillis() - recordingStartedAt).coerceAtLeast(1000L)
            val sourceDescription = mediaCapsuleService.describeMediaFile(
                sourceFile ?: error("Recording file missing"),
            )
            val capsule = mediaCapsuleService.encryptFile(
                sourceFile = sourceFile ?: error("Recording file missing"),
                type = MediaCapsuleType.AUDIO,
                mimeType = "audio/mp4",
                durationMs = durationMs,
                profile = profile,
            )
            val decryptedPlayback = mediaCapsuleService.decryptFile(capsule)
            lastDurationMs = durationMs
            currentCapsuleFile = capsule
            currentDecrypted = decryptedPlayback
            currentPlaybackFile = decryptedPlayback.plaintextFile
            Log.d(
                TAG,
                "stopRecordingAndEncrypt source=$sourceDescription capsule=${capsule.name}:${capsule.length()} playback=${mediaCapsuleService.describeMediaFile(decryptedPlayback.plaintextFile)}",
            )
            renderStatus(getString(R.string.media_capsule_status_saved, capsule.name))
            deleteQuietly(sourceFile)
        }.onFailure {
            Log.e(TAG, "stopRecordingAndEncrypt failed", it)
            renderStatus(getString(R.string.media_capsule_error_encrypt))
        }

        binding.timerText.removeCallbacks(timerRunnable)
        releaseRecorder()
        syncControls()
    }

    private fun importCapsule(
        uri: Uri,
        autoPlay: Boolean = false,
        saveForKeyboard: Boolean = false,
    ): Boolean {
        val tempFile = runCatching {
            mediaCapsuleService.createRecordingFile(MediaCapsuleType.AUDIO, MediaCapsuleType.AUDIO.fileExtension)
                .also { copyUriToFileWithLimit(uri, it, MediaCapsuleService.MAX_MEDIA_BYTES) }
        }.getOrNull() ?: return false

        val profile = runCatching { mediaCapsuleService.resolveProfileForCapsule(tempFile) }.getOrNull()
        val decryptAction = {
            stopPlayback(deletePlaybackFile = true)
            val decrypted = mediaCapsuleService.decryptFile(tempFile)
            currentCapsuleFile = tempFile
            currentDecrypted = decrypted
            currentPlaybackFile = decrypted.plaintextFile
            lastDurationMs = decrypted.metadata.durationMs
            if (saveForKeyboard) {
                pendingCapsuleStore.save(MediaCapsuleType.AUDIO, tempFile)
            }
            Log.d(
                TAG,
                "importCapsule decrypted capsule=${tempFile.name}:${tempFile.length()} playback=${mediaCapsuleService.describeMediaFile(decrypted.plaintextFile)}",
            )
            renderStatus(
                getString(
                    R.string.media_capsule_status_decrypted,
                    decrypted.profile.title,
                    formatDuration(decrypted.metadata.durationMs),
                ),
            )
            syncControls()
            if (autoPlay) {
                playCurrentCapsule()
            }
            true
        }

        return if (mediaCapsuleService.safeRequiresBiometricForCapsule(tempFile)) {
            biometricHelper.authenticate(
                onSuccess = {
                    runCatching { decryptAction() }
                        .onFailure {
                            deleteQuietly(tempFile)
                            renderStatus(
                                if (it.message?.contains("already opened", ignoreCase = true) == true) {
                                    getString(R.string.decrypt_one_time_consumed, profile?.title ?: getString(R.string.clipboard_unknown_profile))
                                } else {
                                    getString(R.string.media_capsule_error_decrypt)
                                },
                            )
                            syncControls()
                        }
                },
                onError = {
                    deleteQuietly(tempFile)
                    renderStatus(it)
                    syncControls()
                },
            )
            true
        } else {
            runCatching { decryptAction() }
                .onFailure {
                    deleteQuietly(tempFile)
                    renderStatus(
                        if (it.message?.contains("already opened", ignoreCase = true) == true) {
                            getString(R.string.decrypt_one_time_consumed, profile?.title ?: getString(R.string.clipboard_unknown_profile))
                        } else {
                            getString(R.string.media_capsule_error_decrypt)
                        },
                    )
                    syncControls()
                }
                .isSuccess
        }
    }

    private fun playCurrentCapsule() {
        if (isPlaybackTransitioning) return

        if (player != null) {
            stopPlayback(deletePlaybackFile = false)
            renderStatus(getString(R.string.media_capsule_status_ready))
            syncControls()
            return
        }

        val playbackFile = currentPlaybackFile ?: currentCapsuleFile?.let { capsuleFile ->
            val profile = runCatching { mediaCapsuleService.resolveProfileForCapsule(capsuleFile) }.getOrNull()
            if (mediaCapsuleService.safeRequiresBiometricForCapsule(capsuleFile)) {
                biometricHelper.authenticate(
                    onSuccess = {
                        runCatching {
                            mediaCapsuleService.decryptFile(capsuleFile).also { decrypted ->
                                currentDecrypted = decrypted
                                currentPlaybackFile = decrypted.plaintextFile
                                lastDurationMs = decrypted.metadata.durationMs
                            }
                        }.onSuccess {
                            playCurrentCapsule()
                        }.onFailure {
                            renderStatus(mediaDecryptErrorMessage(it, profile?.title))
                            syncControls()
                        }
                    },
                    onError = {
                        renderStatus(it)
                        syncControls()
                    },
                )
                return
            }
            runCatching { mediaCapsuleService.decryptFile(capsuleFile) }
                .onFailure {
                    renderStatus(mediaDecryptErrorMessage(it, profile?.title))
                    syncControls()
                }
                .getOrNull()
                ?.also { decrypted ->
                    currentDecrypted = decrypted
                    currentPlaybackFile = decrypted.plaintextFile
                    lastDurationMs = decrypted.metadata.durationMs
                }?.plaintextFile
        }
        if (playbackFile == null) {
            renderStatus(getString(R.string.media_capsule_error_open_first))
            return
        }
        isPlaybackTransitioning = true
        stopPlayback(deletePlaybackFile = false)
        runCatching {
            Log.d(TAG, "playCurrentCapsule playback=${mediaCapsuleService.describeMediaFile(playbackFile)}")
            beginPlaybackRoute()
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                setDataSource(playbackFile.absolutePath)
                setOnCompletionListener {
                    stopPlayback(deletePlaybackFile = false)
                    renderStatus(getString(R.string.media_capsule_status_saved, currentCapsuleFile?.name ?: playbackFile.name))
                    syncControls()
                }
                prepare()
                start()
            }
            renderStatus(getString(R.string.audio_capsule_status_playing))
        }.onFailure {
            Log.e(TAG, "playCurrentCapsule failed", it)
            stopPlayback(deletePlaybackFile = true)
            renderStatus(getString(R.string.media_capsule_error_open_first))
        }
        isPlaybackTransitioning = false
        syncControls()
    }

    private fun shareCurrentCapsule() {
        val capsule = currentCapsuleFile ?: run {
            renderStatus(getString(R.string.media_capsule_error_share_missing))
            return
        }
        val shareFile = runCatching {
            val dir = java.io.File(cacheDir, "shared_capsules").apply { mkdirs() }
            dir.listFiles()?.forEach(::deleteQuietly)
            val baseName = capsule.nameWithoutExtension.ifBlank { capsule.name }
            val exported = java.io.File(dir, "$baseName.veil")
            capsule.inputStream().use { input ->
                exported.outputStream().use { output -> input.copyTo(output) }
            }
            exported
        }.getOrElse { capsule }
        val uri = FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.fileprovider",
            shareFile,
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            CapsuleShareText.build(this@AudioCapsuleActivity)?.let { putExtra(Intent.EXTRA_TEXT, it) }
            clipData = android.content.ClipData.newUri(contentResolver, shareFile.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        grantUriReadAccess(uri, shareIntent)
        startActivity(
            Intent.createChooser(
                shareIntent,
                getString(R.string.media_capsule_share),
            ),
        )
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val previewPath = intent?.getStringExtra(EXTRA_PREVIEW_CAPSULE_PATH)
        if (!previewPath.isNullOrBlank()) {
            importCapsule(Uri.fromFile(File(previewPath)), autoPlay = false, saveForKeyboard = false)
            return
        }
        val uri = resolveIncomingUri(intent) ?: return
        importCapsule(
            uri = uri,
            autoPlay = false,
            saveForKeyboard = true,
        )
    }

    private fun resolveIncomingUri(intent: Intent?): Uri? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> intentUriExtraCompat(intent, Intent.EXTRA_STREAM)
            else -> null
        }
    }

    private fun resolveActiveProfile(): KeyProfile? =
        secureProfileStore.listProfiles().firstOrNull { it.status != KeyProfileStatus.ARCHIVED }
            ?: run {
                renderStatus(getString(R.string.media_capsule_error_missing_profile))
                null
            }

    private fun mediaDecryptErrorMessage(error: Throwable, profileTitle: String?): String =
        if (error.message?.contains("already opened", ignoreCase = true) == true) {
            getString(R.string.decrypt_one_time_consumed, profileTitle ?: getString(R.string.clipboard_unknown_profile))
        } else {
            getString(R.string.media_capsule_error_decrypt)
        }

    private fun beginPlaybackRoute() {
        if (playbackRouteActive) return
        playbackPreviousMode = audioManager.mode
        playbackPreviousSpeakerphoneOn = audioManager.isSpeakerphoneOn
        runCatching {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = true
        }
        playbackRouteActive = true
    }

    private fun endPlaybackRoute() {
        if (!playbackRouteActive) return
        runCatching {
            audioManager.isSpeakerphoneOn = playbackPreviousSpeakerphoneOn
            audioManager.mode = playbackPreviousMode
        }
        playbackRouteActive = false
    }

    private fun renderStatus(message: String) {
        binding.statusText.text = message
    }

    private fun intentUriExtraCompat(intent: Intent, name: String): Uri? {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(name, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(name) as? Uri
        }
    }

    private fun copyUriToFileWithLimit(uri: Uri, target: File, maxBytes: Long) {
        contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var totalBytes = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    totalBytes += read
                    require(totalBytes <= maxBytes) { "Imported file exceeds limit" }
                    output.write(buffer, 0, read)
                }
            }
        } ?: error("Input stream missing")
    }

    private fun grantUriReadAccess(uri: Uri, intent: Intent) {
        val packageNames = buildSet {
            intent.`package`?.takeIf { it.isNotBlank() }?.let(::add)
            addAll(
                packageManager.queryIntentActivities(intent, 0)
                    .mapNotNull { it.activityInfo?.packageName }
                    .filter { it.isNotBlank() },
            )
        }
        packageNames.forEach { packageName ->
            runCatching {
                grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    private fun stopPlayback(deletePlaybackFile: Boolean = false) {
        runCatching {
            player?.setOnCompletionListener(null)
            if (player?.isPlaying == true) {
                player?.stop()
            }
            player?.release()
        }
        player = null
        endPlaybackRoute()
        if (deletePlaybackFile) {
            deleteQuietly(currentPlaybackFile)
            currentPlaybackFile = null
            currentDecrypted = null
        }
        syncControls()
    }

    private fun releaseRecorder() {
        runCatching { recorder?.release() }
        recorder = null
        recordingFile = null
    }

    private fun deleteQuietly(file: File?) {
        runCatching {
            if (file?.exists() == true) {
                file.delete()
            }
        }
    }

    private fun syncControls() {
        val isRecording = recorder != null
        val hasCapsule = currentCapsuleFile != null
        val hasPlayback = currentPlaybackFile != null || currentDecrypted != null
        val isPlaying = player != null

        binding.recordButton.text = if (isRecording) {
            "в– "
        } else {
            "в—Џ"
        }
        binding.recordLabelText.text = if (isRecording) {
            getString(R.string.audio_capsule_stop)
        } else {
            getString(R.string.audio_capsule_record)
        }
        binding.playButton.isEnabled = hasCapsule || hasPlayback
        binding.shareButton.isEnabled = hasCapsule
        binding.openCapsuleButton.isEnabled = !isRecording
        binding.playButton.alpha = if (hasCapsule || hasPlayback) 1f else 0.55f
        binding.shareButton.alpha = if (hasCapsule) 1f else 0.55f
        binding.openCapsuleButton.alpha = if (!isRecording) 1f else 0.55f
        if (!isRecording) {
            binding.timerText.text = formatDuration(lastDurationMs)
        }
        binding.playButton.text = if (isPlaying) {
            "в– "
        } else {
            getString(R.string.audio_capsule_play)
        }
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = (durationMs / 1000).toInt()
        return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    }
}
