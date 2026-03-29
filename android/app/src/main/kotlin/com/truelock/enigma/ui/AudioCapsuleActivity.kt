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
    private var recordingStartedAt = 0L

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

        binding.recordButton.setOnClickListener { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
        binding.stopButton.setOnClickListener { stopRecordingAndEncrypt() }
        binding.openCapsuleButton.setOnClickListener { openCapsuleLauncher.launch(arrayOf("*/*")) }
        binding.playButton.setOnClickListener { playCurrentCapsule() }
        binding.shareButton.setOnClickListener { shareCurrentCapsule() }

        renderStatus(getString(R.string.media_capsule_status_ready))
    }

    override fun onStop() {
        super.onStop()
        stopPlayback()
        releaseRecorder()
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
        currentCapsuleFile = null
        currentDecrypted = null
        renderStatus(getString(R.string.media_capsule_status_recording, profile.title))
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
            currentCapsuleFile = capsule
            currentDecrypted = null
            renderStatus(getString(R.string.media_capsule_status_saved, capsule.name))
        }.onFailure {
            renderStatus(getString(R.string.media_capsule_error_encrypt))
        }

        releaseRecorder()
    }

    private fun importCapsule(uri: Uri) {
        runCatching {
            val tempFile = mediaCapsuleService.createRecordingFile(MediaCapsuleType.AUDIO, MediaCapsuleType.AUDIO.fileExtension)
            contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Input stream missing")
            val decrypted = mediaCapsuleService.decryptFile(tempFile)
            currentCapsuleFile = tempFile
            currentDecrypted = decrypted
            renderStatus(
                getString(
                    R.string.media_capsule_status_decrypted,
                    decrypted.profile.title,
                    formatDuration(decrypted.metadata.durationMs),
                ),
            )
        }.onFailure {
            renderStatus(getString(R.string.media_capsule_error_decrypt))
        }
    }

    private fun playCurrentCapsule() {
        val decrypted = currentDecrypted ?: currentCapsuleFile?.let {
            runCatching { mediaCapsuleService.decryptFile(it) }.getOrNull()
        }
        if (decrypted == null) {
            renderStatus(getString(R.string.media_capsule_error_open_first))
            return
        }
        currentDecrypted = decrypted
        stopPlayback()
        player = MediaPlayer().apply {
            setDataSource(decrypted.plaintextFile.absolutePath)
            prepare()
            start()
        }
        renderStatus(getString(R.string.audio_capsule_status_playing))
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
                    type = "application/octet-stream"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                getString(R.string.media_capsule_share),
            ),
        )
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
    }

    private fun releaseRecorder() {
        recorder?.release()
        recorder = null
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = (durationMs / 1000).toInt()
        return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    }
}
