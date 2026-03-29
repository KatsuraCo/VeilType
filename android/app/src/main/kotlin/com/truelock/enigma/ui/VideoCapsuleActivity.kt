package com.truelock.enigma.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.truelock.enigma.R
import com.truelock.enigma.databinding.ActivityVideoCapsuleBinding
import com.truelock.enigma.media.DecryptedMediaCapsule
import com.truelock.enigma.media.MediaCapsuleService
import com.truelock.enigma.media.MediaCapsuleType
import com.truelock.enigma.profiles.KeyProfile
import com.truelock.enigma.profiles.KeyProfileStatus
import com.truelock.enigma.storage.FileKeyProfileRepository
import com.truelock.enigma.storage.ProfileKeyVault
import com.truelock.enigma.storage.SecureProfileStore
import java.io.File

class VideoCapsuleActivity : AppCompatActivity() {
    private lateinit var binding: ActivityVideoCapsuleBinding
    private lateinit var secureProfileStore: SecureProfileStore
    private lateinit var mediaCapsuleService: MediaCapsuleService

    private var currentVideoFile: File? = null
    private var currentCapsuleFile: File? = null
    private var currentDecrypted: DecryptedMediaCapsule? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result.values.all { it }) {
            launchVideoCapture()
        } else {
            renderStatus(getString(R.string.media_capsule_error_video_permission))
        }
    }

    private val captureVideoLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == RESULT_OK && currentVideoFile != null) {
            encryptRecordedVideo(currentVideoFile!!)
        } else {
            renderStatus(getString(R.string.video_capsule_status_cancelled))
        }
    }

    private val openCapsuleLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let(::importCapsule)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoCapsuleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        secureProfileStore = SecureProfileStore(
            repository = FileKeyProfileRepository(applicationContext),
            keyVault = ProfileKeyVault(),
        )
        mediaCapsuleService = MediaCapsuleService(applicationContext, secureProfileStore)

        binding.recordButton.setOnClickListener {
            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        }
        binding.openCapsuleButton.setOnClickListener { openCapsuleLauncher.launch(arrayOf("*/*")) }
        binding.playButton.setOnClickListener { playCurrentCapsule() }
        binding.shareButton.setOnClickListener { shareCurrentCapsule() }

        renderStatus(getString(R.string.media_capsule_status_ready))
    }

    private fun launchVideoCapture() {
        resolveActiveProfile() ?: return
        currentVideoFile = mediaCapsuleService.createRecordingFile(MediaCapsuleType.VIDEO, "mp4")
        val uri = FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.fileprovider",
            currentVideoFile!!,
        )
        captureVideoLauncher.launch(
            Intent(MediaStore.ACTION_VIDEO_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, uri)
                putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            },
        )
        renderStatus(getString(R.string.video_capsule_status_recording))
    }

    private fun encryptRecordedVideo(sourceFile: File) {
        val profile = resolveActiveProfile() ?: return
        runCatching {
            val capsule = mediaCapsuleService.encryptFile(
                sourceFile = sourceFile,
                type = MediaCapsuleType.VIDEO,
                mimeType = "video/mp4",
                durationMs = 0L,
                profile = profile,
            )
            currentCapsuleFile = capsule
            currentDecrypted = null
            renderStatus(getString(R.string.media_capsule_status_saved, capsule.name))
        }.onFailure {
            renderStatus(getString(R.string.media_capsule_error_encrypt))
        }
    }

    private fun importCapsule(uri: Uri) {
        runCatching {
            val tempFile = mediaCapsuleService.createRecordingFile(MediaCapsuleType.VIDEO, MediaCapsuleType.VIDEO.fileExtension)
            contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Input stream missing")
            val decrypted = mediaCapsuleService.decryptFile(tempFile)
            currentCapsuleFile = tempFile
            currentDecrypted = decrypted
            binding.videoView.setVideoPath(decrypted.plaintextFile.absolutePath)
            renderStatus(getString(R.string.media_capsule_status_decrypted, decrypted.profile.title, "video"))
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
        binding.videoView.setVideoPath(decrypted.plaintextFile.absolutePath)
        binding.videoView.start()
        renderStatus(getString(R.string.video_capsule_status_playing))
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
}
