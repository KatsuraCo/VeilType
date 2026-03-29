package com.truelock.enigma.ui

import android.Manifest
import android.content.Intent
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.truelock.enigma.R
import com.truelock.enigma.databinding.ActivityAudioCapsuleBinding
import com.truelock.enigma.media.DecryptedMediaCapsule
import com.truelock.enigma.media.MediaCapsuleService
import com.truelock.enigma.media.MediaCapsuleType
import com.truelock.enigma.profiles.KeyProfile
import com.truelock.enigma.profiles.KeyProfileStatus
import com.truelock.enigma.storage.FileKeyProfileRepository
import com.truelock.enigma.storage.ProfileKeyVault
import com.truelock.enigma.storage.SecureProfileStore
import java.io.File

class AudioCapsuleActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAudioCapsuleBinding
    private lateinit var secureProfileStore: SecureProfileStore
    private lateinit var mediaCapsuleService: MediaCapsuleService

    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var recordingFile: File? = null
    private var currentCapsuleFile: File? = null
    private var currentDecrypted: DecryptedMediaCapsule? = null
    private var currentPlaybackFile: File? = null
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
        stopPlayback()
        releaseRecorder()
        binding.timerText.removeCallbacks(timerRunnable)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun startRecording() {
        val profile = resolveActiveProfile() ?: return
        recordingFile = mediaCapsuleService.createRecordingFile(MediaCapsuleType.AUDIO, "m4a")
        releaseRecorder()
        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(recordingFile!!.absolutePath)
            prepare()
            start()
        }
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
            val capsule = mediaCapsuleService.encryptFile(
                sourceFile = sourceFile ?: error("Recording file missing"),
                type = MediaCapsuleType.AUDIO,
                mimeType = "audio/mp4",
                durationMs = durationMs,
                profile = profile,
            )
            lastDurationMs = durationMs
            currentCapsuleFile = capsule
            currentDecrypted = null
            currentPlaybackFile = sourceFile
            renderStatus(getString(R.string.media_capsule_status_saved, capsule.name))
        }.onFailure {
            renderStatus(getString(R.string.media_capsule_error_encrypt))
        }

        binding.timerText.removeCallbacks(timerRunnable)
        releaseRecorder()
        syncControls()
    }

    private fun importCapsule(uri: Uri, autoPlay: Boolean = false) {
        runCatching {
            val tempFile = mediaCapsuleService.createRecordingFile(MediaCapsuleType.AUDIO, MediaCapsuleType.AUDIO.fileExtension)
            contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Input stream missing")
            val decrypted = mediaCapsuleService.decryptFile(tempFile)
            currentCapsuleFile = tempFile
            currentDecrypted = decrypted
            currentPlaybackFile = decrypted.plaintextFile
            lastDurationMs = decrypted.metadata.durationMs
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
        }.onFailure {
            renderStatus(getString(R.string.media_capsule_error_decrypt))
            syncControls()
        }
    }

    private fun playCurrentCapsule() {
        if (player != null) {
            stopPlayback()
            renderStatus(getString(R.string.media_capsule_status_ready))
            syncControls()
            return
        }

        val playbackFile = currentPlaybackFile ?: currentCapsuleFile?.let {
            runCatching { mediaCapsuleService.decryptFile(it) }.getOrNull()?.also { decrypted ->
                currentDecrypted = decrypted
                currentPlaybackFile = decrypted.plaintextFile
            }?.plaintextFile
        }
        if (playbackFile == null) {
            renderStatus(getString(R.string.media_capsule_error_open_first))
            return
        }
        stopPlayback()
        player = MediaPlayer().apply {
            setDataSource(playbackFile.absolutePath)
            setOnCompletionListener {
                stopPlayback()
                renderStatus(getString(R.string.media_capsule_status_saved, currentCapsuleFile?.name ?: playbackFile.name))
                syncControls()
            }
            prepare()
            start()
        }
        renderStatus(getString(R.string.audio_capsule_status_playing))
        syncControls()
    }

    private fun shareCurrentCapsule() {
        val capsule = currentCapsuleFile ?: run {
            renderStatus(getString(R.string.media_capsule_error_share_missing))
            return
        }
        val uri = FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.fileprovider",
            capsule,
        )
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = MediaCapsuleType.AUDIO.capsuleMimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                getString(R.string.media_capsule_share),
            ),
        )
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val uri = resolveIncomingUri(intent) ?: return
        importCapsule(uri, autoPlay = true)
    }

    private fun resolveIncomingUri(intent: Intent?): Uri? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        }
    }

    private fun resolveActiveProfile(): KeyProfile? =
        secureProfileStore.listProfiles().firstOrNull { it.status != KeyProfileStatus.ARCHIVED }
            ?: run {
                renderStatus(getString(R.string.media_capsule_error_missing_profile))
                null
            }

    private fun renderStatus(message: String) {
        binding.statusText.text = message
    }

    private fun stopPlayback() {
        player?.release()
        player = null
        syncControls()
    }

    private fun releaseRecorder() {
        recorder?.release()
        recorder = null
        recordingFile = null
    }

    private fun syncControls() {
        val isRecording = recorder != null
        val hasCapsule = currentCapsuleFile != null
        val hasPlayback = currentPlaybackFile != null || currentDecrypted != null
        val isPlaying = player != null

        binding.recordButton.text = if (isRecording) {
            "■"
        } else {
            "●"
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
            "■"
        } else {
            getString(R.string.audio_capsule_play)
        }
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = (durationMs / 1000).toInt()
        return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    }
}
