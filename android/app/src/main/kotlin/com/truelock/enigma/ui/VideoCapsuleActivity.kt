package com.truelock.enigma.ui

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.SeekBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.truelock.enigma.R
import com.truelock.enigma.databinding.ActivityVideoCapsuleBinding
import com.truelock.enigma.media.DecryptedMediaCapsule
import com.truelock.enigma.media.MediaCapsuleService
import com.truelock.enigma.media.MediaCapsuleType
import com.truelock.enigma.media.PendingCapsuleStore
import com.truelock.enigma.profiles.KeyProfile
import com.truelock.enigma.profiles.KeyProfileStatus
import com.truelock.enigma.security.BiometricDecryptHelper
import com.truelock.enigma.storage.FileKeyProfileRepository
import com.truelock.enigma.storage.ProfileKeyVault
import com.truelock.enigma.storage.SecureProfileStore
import java.io.File

class VideoCapsuleActivity : AppCompatActivity() {
    private lateinit var binding: ActivityVideoCapsuleBinding
    private lateinit var secureProfileStore: SecureProfileStore
    private lateinit var mediaCapsuleService: MediaCapsuleService
    private lateinit var pendingCapsuleStore: PendingCapsuleStore
    private lateinit var biometricHelper: BiometricDecryptHelper

    private var currentVideoFile: File? = null
    private var currentCapsuleFile: File? = null
    private var currentDecrypted: DecryptedMediaCapsule? = null
    private var currentPlaybackFile: File? = null
    private var playbackPlayer: MediaPlayer? = null
    private var playbackSurface: Surface? = null
    private var playbackLoadedPath: String? = null
    private var playbackShouldStart = false
    private var preserveCapsulePathOnDestroy: String? = null
    private var userSeekingPlayback = false
    private var playbackPrepared = false

    private var cameraProvider: ProcessCameraProvider? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var isRecordingPaused = false
    private var recordingPausedAt = 0L
    private var recordingPausedDurationMs = 0L
    private var suppressNextRecordingFinalize = false
    private var cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var recordingStartedAt = 0L
    private var lastDurationMs = 0L
    private val timerHandler = Handler(Looper.getMainLooper())
    private val playbackProgressRunnable = object : Runnable {
        override fun run() {
            refreshPlaybackSeekBar()
            if (playbackPlayer?.isPlaying == true) {
                timerHandler.postDelayed(this, 250L)
            }
        }
    }
    private val timerRunnable = object : Runnable {
        override fun run() {
            val duration = if (activeRecording != null) {
                val pausedNow = if (isRecordingPaused) System.currentTimeMillis() - recordingPausedAt else 0L
                (System.currentTimeMillis() - recordingStartedAt - recordingPausedDurationMs - pausedNow)
                    .coerceAtLeast(0L)
            } else {
                lastDurationMs
            }
            binding.timerText.text = formatDuration(duration)
            if (activeRecording != null) {
                timerHandler.postDelayed(this, 250L)
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result.values.all { it }) {
            startCameraPreview()
            startVideoRecordingInternal()
        } else {
            renderStatus(getString(R.string.media_capsule_error_video_permission))
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
        pendingCapsuleStore = PendingCapsuleStore(applicationContext)
        biometricHelper = BiometricDecryptHelper(this)

        binding.videoPreviewContainer.outlineProvider = ViewOutlineProvider.BACKGROUND
        binding.videoPreviewContainer.clipToOutline = true
        val actionPanel = binding.actionPanel
        val baseActionPaddingBottom = actionPanel.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            actionPanel.setPadding(
                actionPanel.paddingLeft,
                actionPanel.paddingTop,
                actionPanel.paddingRight,
                baseActionPaddingBottom + systemBars.bottom,
            )
            insets
        }

        binding.recordButton.setOnClickListener {
            if (activeRecording != null) {
                stopVideoRecording()
            } else {
                ensureCameraAndStartRecording()
            }
        }
        binding.switchCameraButton.setOnClickListener {
            if (activeRecording == null) {
                cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }
                startCameraPreview()
            }
        }
        binding.openCapsuleButton.setOnClickListener {
            if (activeRecording != null) {
                toggleRecordingPause()
            } else {
                openCapsuleLauncher.launch(arrayOf("*/*"))
            }
        }
        binding.playButton.setOnClickListener {
            if (activeRecording != null) {
                stopVideoRecording()
            } else {
                playCurrentCapsule()
            }
        }
        binding.shareButton.setOnClickListener {
            if (launchedFromKeyboard()) {
                sendCurrentCapsuleToKeyboard()
            } else {
                shareCurrentCapsule()
            }
        }
        binding.retakeButton.setOnClickListener {
            if (activeRecording != null) {
                stopVideoRecording()
            } else {
                preserveCapsulePathOnDestroy = null
                cleanupCurrentVideoState()
                showCaptureMode()
                if (hasCapturePermissions()) {
                    startCameraPreview()
                }
                syncControls()
            }
        }
        binding.videoView.setOnClickListener { playCurrentCapsule() }
        binding.videoPlaybackSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val player = playbackPlayer ?: return
                val duration = runCatching { player.duration }.getOrDefault(0)
                if (fromUser && duration > 0) {
                    player.seekTo((duration * (progress / 1000f)).toInt().coerceAtLeast(0))
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                userSeekingPlayback = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                userSeekingPlayback = false
                refreshPlaybackSeekBar()
            }
        })
        binding.videoView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                playbackSurface?.release()
                playbackSurface = Surface(surface)
                currentPlaybackFile?.let { preparePlayback(it, playbackShouldStart) }
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                releasePlaybackPlayer()
                playbackSurface?.release()
                playbackSurface = null
                return true
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
        }

        renderStatus(getString(R.string.media_capsule_status_ready))
        syncControls()

        if (!handleIncomingIntent(intent) && hasCapturePermissions()) {
            startCameraPreview()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!handleIncomingIntent(intent) && hasCapturePermissions()) {
            startCameraPreview()
        }
    }

    override fun onStop() {
        super.onStop()
        timerHandler.removeCallbacks(timerRunnable)
        timerHandler.removeCallbacks(playbackProgressRunnable)
        if (playbackPlayer?.isPlaying == true) {
            pausePlaybackPreview()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        timerHandler.removeCallbacks(timerRunnable)
        timerHandler.removeCallbacks(playbackProgressRunnable)
        if (activeRecording != null) {
            suppressNextRecordingFinalize = true
        }
        activeRecording?.close()
        activeRecording = null
        cameraProvider?.unbindAll()
        cameraProvider = null
        releasePlaybackPlayer()
        playbackSurface?.release()
        playbackSurface = null
        cleanupCurrentVideoState(
            keepCapsule = preserveCapsulePathOnDestroy?.let(::File),
            clearUiState = false,
        )
    }

    private fun ensureCameraAndStartRecording() {
        resolveActiveProfile() ?: return
        if (!hasCapturePermissions()) {
            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
            return
        }
        if (videoCapture == null || cameraProvider == null) {
            startCameraPreview()
        }
        startVideoRecordingInternal()
    }

    private fun startCameraPreview() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener(
            {
                val provider = future.get()
                cameraProvider = provider
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = binding.cameraPreviewView.surfaceProvider
                }
                val recorder = Recorder.Builder()
                    .setQualitySelector(
                        QualitySelector.fromOrderedList(
                            listOf(Quality.HD, Quality.SD, Quality.LOWEST),
                        ),
                    )
                    .build()
                val capture = VideoCapture.withOutput(recorder)
                provider.unbindAll()
                provider.bindToLifecycle(this, cameraSelector, preview, capture)
                videoCapture = capture
                if (activeRecording == null && currentPlaybackFile == null) {
                    showCaptureMode()
                    renderStatus(getString(R.string.media_capsule_status_ready))
                    syncControls()
                }
            },
            ContextCompat.getMainExecutor(this),
        )
    }

    private fun startVideoRecordingInternal() {
        val profile = resolveActiveProfile() ?: return
        val capture = videoCapture ?: run {
            renderStatus(getString(R.string.media_capsule_status_ready))
            return
        }
        if (activeRecording != null) return

        cleanupCurrentVideoState()
        currentVideoFile = mediaCapsuleService.createRecordingFile(MediaCapsuleType.VIDEO, "mp4")
        currentCapsuleFile = null
        currentDecrypted = null
        currentPlaybackFile = null
        preserveCapsulePathOnDestroy = null
        lastDurationMs = 0L
        recordingStartedAt = System.currentTimeMillis()
        recordingPausedAt = 0L
        recordingPausedDurationMs = 0L
        showCaptureMode()

        val output = FileOutputOptions.Builder(currentVideoFile!!).build()
        var pending = capture.output.prepareRecording(this, output)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            pending = pending.withAudioEnabled()
        }

        activeRecording = pending.start(ContextCompat.getMainExecutor(this)) recordingEvent@ { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    isRecordingPaused = false
                    renderStatus(getString(R.string.media_capsule_status_recording, profile.title))
                    timerHandler.removeCallbacks(timerRunnable)
                    timerHandler.post(timerRunnable)
                    syncControls()
                }

                is VideoRecordEvent.Finalize -> {
                    activeRecording?.close()
                    activeRecording = null
                    isRecordingPaused = false
                    recordingPausedAt = 0L
                    recordingPausedDurationMs = 0L
                    timerHandler.removeCallbacks(timerRunnable)
                    if (suppressNextRecordingFinalize) {
                        suppressNextRecordingFinalize = false
                        cleanupCurrentVideoState()
                        syncControls()
                        return@recordingEvent
                    }
                    lastDurationMs = event.recordingStats.recordedDurationNanos / 1_000_000L
                    if (!event.hasError()) {
                        val sourceFile = currentVideoFile
                        if (sourceFile != null && sourceFile.exists()) {
                            encryptRecordedVideo(sourceFile)
                        } else {
                            cleanupCurrentVideoState()
                            renderStatus(getString(R.string.media_capsule_error_encrypt))
                            syncControls()
                        }
                    } else {
                        cleanupCurrentVideoState()
                        renderStatus(getString(R.string.video_capsule_status_cancelled))
                        syncControls()
                    }
                }
            }
        }
        syncControls()
    }

    private fun stopVideoRecording() {
        val recording = activeRecording ?: return
        renderStatus(getString(R.string.video_capsule_status_finalizing))
        recording.stop()
        syncControls()
    }

    private fun toggleRecordingPause() {
        val recording = activeRecording ?: return
        if (isRecordingPaused) {
            recording.resume()
            recordingPausedDurationMs += (System.currentTimeMillis() - recordingPausedAt).coerceAtLeast(0L)
            recordingPausedAt = 0L
            isRecordingPaused = false
            renderStatus(getString(R.string.video_capsule_status_recording_hint))
        } else {
            recording.pause()
            recordingPausedAt = System.currentTimeMillis()
            isRecordingPaused = true
            renderStatus(getString(R.string.video_capsule_status_paused))
        }
        syncControls()
    }

    private fun encryptRecordedVideo(sourceFile: File) {
        val profile = resolveActiveProfile() ?: return
        runCatching {
            val capsule = mediaCapsuleService.encryptFile(
                sourceFile = sourceFile,
                type = MediaCapsuleType.VIDEO,
                mimeType = "video/mp4",
                durationMs = lastDurationMs.coerceAtLeast(1_000L),
                profile = profile,
            )
            currentCapsuleFile = capsule
            currentPlaybackFile = sourceFile
            showPlaybackMode()
            renderStatus(getString(R.string.media_capsule_status_saved, capsule.name))
            syncControls()
        }.onFailure {
            renderStatus(getString(R.string.media_capsule_error_encrypt))
            syncControls()
        }
    }

    private fun importCapsule(uri: Uri, autoPlay: Boolean = false) {
        preserveCapsulePathOnDestroy = null
        cleanupCurrentVideoState()
        showCaptureMode()
        syncControls()
        val tempFile = runCatching {
            mediaCapsuleService.createRecordingFile(
                MediaCapsuleType.VIDEO,
                MediaCapsuleType.VIDEO.fileExtension,
            )
        }.getOrNull() ?: return
        runCatching {
            contentResolver.copyUriToFileWithLimit(uri, tempFile, MediaCapsuleService.MAX_MEDIA_BYTES)
        }.onFailure {
            renderStatus(getString(R.string.media_capsule_error_decrypt))
            showCaptureMode()
            syncControls()
            return
        }
        val profile = runCatching { mediaCapsuleService.resolveProfileForCapsule(tempFile) }.getOrNull()
        val decryptAction = {
            val decrypted = mediaCapsuleService.decryptFile(tempFile)
            cleanupCurrentVideoState()
            currentCapsuleFile = tempFile
            currentDecrypted = decrypted
            currentPlaybackFile = decrypted.plaintextFile
            preserveCapsulePathOnDestroy = null
            lastDurationMs = decrypted.metadata.durationMs
            showPlaybackMode()
            renderStatus(getString(R.string.media_capsule_status_decrypted, decrypted.profile.title, "video"))
            syncControls()
            if (autoPlay) {
                playCurrentCapsule()
            }
        }
        if (profile?.requireBiometricForDecrypt == true) {
            biometricHelper.authenticate(
                onSuccess = { decryptAction() },
                onError = {
                    deleteQuietly(tempFile)
                    renderStatus(it)
                    showCaptureMode()
                    syncControls()
                },
            )
        } else {
            runCatching { decryptAction() }.onFailure {
                deleteQuietly(tempFile)
                renderStatus(
                    if (it.message?.contains("already opened", ignoreCase = true) == true) {
                        getString(R.string.decrypt_one_time_consumed, profile?.title ?: getString(R.string.clipboard_unknown_profile))
                    } else {
                        getString(R.string.media_capsule_error_decrypt)
                    },
                )
                showCaptureMode()
                syncControls()
            }
        }
    }

    private fun playCurrentCapsule() {
        val playbackFile = currentPlaybackFile ?: currentCapsuleFile?.let { capsuleFile ->
            val profile = runCatching { mediaCapsuleService.resolveProfileForCapsule(capsuleFile) }.getOrNull()
            if (profile?.requireBiometricForDecrypt == true) {
                biometricHelper.authenticate(
                    onSuccess = { decryptCapsuleForPlayback(capsuleFile, autoPlay = true) },
                    onError = {
                        renderStatus(it)
                        syncControls()
                    },
                )
                return
            }
            decryptCapsuleForPlayback(capsuleFile, autoPlay = false) ?: return
        }
        if (playbackFile == null) {
            renderStatus(getString(R.string.media_capsule_error_open_first))
            return
        }
        val existingPlayer = playbackPlayer
        if (existingPlayer?.isPlaying == true) {
            pausePlaybackPreview()
            return
        }
        if (existingPlayer != null && playbackPrepared) {
            runCatching { existingPlayer.start() }
            playbackShouldStart = true
            timerHandler.removeCallbacks(playbackProgressRunnable)
            timerHandler.post(playbackProgressRunnable)
            renderStatus(getString(R.string.video_capsule_status_playing))
            syncControls()
            return
        }
        showPlaybackMode()
        preparePlayback(playbackFile, autoplay = true)
        renderStatus(getString(R.string.video_capsule_status_playing))
        syncControls()
    }

    private fun decryptCapsuleForPlayback(capsuleFile: File, autoPlay: Boolean): File? =
        runCatching {
            mediaCapsuleService.decryptFile(capsuleFile).also { decrypted ->
                currentDecrypted = decrypted
                currentPlaybackFile = decrypted.plaintextFile
                lastDurationMs = decrypted.metadata.durationMs
            }.plaintextFile
        }.getOrElse {
            val profileTitle = runCatching {
                mediaCapsuleService.resolveProfileForCapsule(capsuleFile)?.title
            }.getOrElse { getString(R.string.clipboard_unknown_profile) }
            renderStatus(
                if (it.message?.contains("already opened", ignoreCase = true) == true) {
                    getString(R.string.decrypt_one_time_consumed, profileTitle ?: getString(R.string.clipboard_unknown_profile))
                } else {
                    getString(R.string.media_capsule_error_decrypt)
                },
            )
            syncControls()
            null
        }?.also { file ->
            showPlaybackMode()
            if (autoPlay) {
                preparePlayback(file, autoplay = true)
                renderStatus(getString(R.string.video_capsule_status_playing))
                syncControls()
            }
        }

    private fun shareCurrentCapsule() {
        val capsule = currentCapsuleFile ?: run {
            renderStatus(getString(R.string.media_capsule_error_share_missing))
            return
        }
        if (playbackPlayer?.isPlaying == true) {
            pausePlaybackPreview()
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
            clipData = ClipData.newUri(contentResolver, shareFile.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        grantUriAccess(uri, shareIntent)
        startActivity(
            Intent.createChooser(
                shareIntent,
                getString(R.string.media_capsule_share),
            ),
        )
    }

    private fun sendCurrentCapsuleToKeyboard() {
        val capsule = currentCapsuleFile ?: run {
            renderStatus(getString(R.string.media_capsule_error_share_missing))
            return
        }
        if (playbackPlayer?.isPlaying == true) {
            pausePlaybackPreview()
        }
        preserveCapsulePathOnDestroy = capsule.absolutePath
        cleanupCurrentVideoState(keepCapsule = capsule, clearUiState = false)
        pendingCapsuleStore.save(MediaCapsuleType.VIDEO, capsule)
        finish()
    }

    private fun cleanupCurrentVideoState(keepCapsule: File? = null, clearUiState: Boolean = true) {
        deleteQuietly(currentVideoFile?.takeIf { keepCapsule?.absolutePath != it.absolutePath })
        deleteQuietly(currentCapsuleFile?.takeIf { keepCapsule?.absolutePath != it.absolutePath })
        currentPlaybackFile?.let { playbackFile ->
            if (keepCapsule?.absolutePath != playbackFile.absolutePath &&
                currentVideoFile?.absolutePath != playbackFile.absolutePath
            ) {
                deleteQuietly(playbackFile)
            }
        }
        currentVideoFile = null
        currentCapsuleFile = keepCapsule
        currentDecrypted = null
        currentPlaybackFile = null
        playbackLoadedPath = null
        if (clearUiState) {
            releasePlaybackPlayer()
            lastDurationMs = 0L
        }
    }

    private fun pausePlaybackPreview() {
        playbackShouldStart = false
        runCatching { playbackPlayer?.pause() }
        timerHandler.removeCallbacks(playbackProgressRunnable)
        renderStatus(
            getString(
                R.string.media_capsule_status_saved,
                currentCapsuleFile?.name ?: currentPlaybackFile?.name ?: "video",
            ),
        )
        syncControls()
    }

    private fun handleIncomingIntent(intent: Intent?): Boolean {
        val previewPath = intent?.getStringExtra(EXTRA_PREVIEW_CAPSULE_PATH)
        if (!previewPath.isNullOrBlank()) {
            prepareForIncomingContent()
            importCapsule(Uri.fromFile(File(previewPath)), autoPlay = false)
            return true
        }
        val uri = resolveIncomingUri(intent) ?: return false
        prepareForIncomingContent()
        importCapsule(uri, autoPlay = true)
        return true
    }

    private fun prepareForIncomingContent() {
        if (activeRecording != null) {
            suppressNextRecordingFinalize = true
            runCatching { activeRecording?.close() }
            activeRecording = null
            isRecordingPaused = false
            recordingPausedAt = 0L
            recordingPausedDurationMs = 0L
            timerHandler.removeCallbacks(timerRunnable)
        }
        cleanupCurrentVideoState()
        showCaptureMode()
        syncControls()
    }

    private fun resolveIncomingUri(intent: Intent?): Uri? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> intent.uriExtraCompat(Intent.EXTRA_STREAM)
            else -> null
        }
    }

    private fun resolveActiveProfile(): KeyProfile? =
        secureProfileStore.listProfiles().firstOrNull { it.status != KeyProfileStatus.ARCHIVED }
            ?: run {
                renderStatus(getString(R.string.media_capsule_error_missing_profile))
                null
            }

    private fun grantUriAccess(uri: Uri, intent: Intent) {
        val packageNames = buildSet {
            intent.`package`?.takeIf { it.isNotBlank() }?.let(::add)
            addAll(
                packageManager.queryIntentActivities(intent, 0)
                    .mapNotNull { it.activityInfo?.packageName }
                    .filter { it.isNotBlank() },
            )
        }
        packageNames.forEach { packageName ->
            runCatching { grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
    }

    private fun hasCapturePermissions(): Boolean =
        arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO).all { permission ->
            checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

    private fun showCaptureMode() {
        binding.cameraPreviewView.visibility = View.VISIBLE
        binding.videoView.visibility = View.GONE
        binding.timerText.visibility = View.GONE
        binding.switchCameraButton.visibility = View.VISIBLE
        playbackShouldStart = false
    }

    private fun showPlaybackMode() {
        binding.cameraPreviewView.visibility = View.GONE
        binding.videoView.visibility = View.VISIBLE
        binding.timerText.visibility = View.GONE
        binding.switchCameraButton.visibility = View.GONE
        currentPlaybackFile?.let { playbackFile ->
            if (playbackLoadedPath != playbackFile.absolutePath || playbackPlayer == null) {
                preparePlayback(playbackFile, autoplay = false)
            }
        }
    }

    private fun renderStatus(message: String) {
        binding.statusText.text = message
    }

    private fun syncControls() {
        val isRecording = activeRecording != null
        val hasCapsule = currentCapsuleFile != null
        val hasPlayback = currentPlaybackFile != null || currentDecrypted != null
        val isPlaying = playbackPlayer?.isPlaying == true
        val isReady = !isRecording && (hasCapsule || hasPlayback)
        val isOpenedCapsule = currentDecrypted != null

        binding.actionTitleText.visibility = View.GONE
        binding.actionSubtitleText.visibility = View.GONE
        binding.recordLabelText.visibility = View.GONE
        binding.recordButton.visibility = if (isRecording || isReady) View.GONE else View.VISIBLE
        binding.recordButton.text = if (isRecording) getString(R.string.video_capsule_stop_text) else getString(R.string.video_capsule_record_icon)
        binding.timerText.visibility = if (isRecording) View.VISIBLE else View.GONE
        binding.switchCameraButton.visibility = if (!isRecording && !isReady) View.VISIBLE else View.GONE
        binding.openCapsuleButton.visibility = if (isRecording) View.VISIBLE else View.GONE
        binding.openCapsuleButton.isEnabled = isRecording
        binding.playButton.visibility = if (isRecording || isReady) View.VISIBLE else View.GONE
        binding.playButton.isEnabled = isRecording || (!isRecording && (hasCapsule || hasPlayback))
        binding.shareButton.isEnabled = !isRecording && hasCapsule
        binding.shareButton.visibility = if (isReady && hasCapsule) View.VISIBLE else View.GONE
        binding.shareButton.text = if (launchedFromKeyboard()) {
            getString(R.string.keyboard_capsule_send_short)
        } else {
            getString(R.string.media_capsule_share)
        }
        binding.retakeButton.isEnabled = !isRecording
        binding.retakeButton.visibility = if (isReady && !isOpenedCapsule) View.VISIBLE else View.GONE
        binding.openCapsuleButton.text =
            if (isRecordingPaused) getString(R.string.video_capsule_resume) else getString(R.string.video_capsule_pause)
        binding.playButton.text = when {
            isRecording -> getString(R.string.video_capsule_stop_text)
            isPlaying -> getString(R.string.video_capsule_pause)
            else -> getString(R.string.video_capsule_play)
        }
        binding.videoPlaybackSeekBar.visibility = if (isReady) View.VISIBLE else View.GONE
        refreshPlaybackSeekBar()
        if (!isRecording) {
            binding.timerText.text = formatDuration(lastDurationMs)
        }
    }

    private fun refreshPlaybackSeekBar() {
        val player = playbackPlayer
        val duration = player?.duration?.takeIf { it > 0 } ?: lastDurationMs.toInt().takeIf { it > 0 } ?: 0
        if (duration <= 0 || userSeekingPlayback) return
        val position = player?.currentPosition ?: 0
        binding.videoPlaybackSeekBar.progress = ((position.coerceAtLeast(0) / duration.toFloat()) * 1000).toInt().coerceIn(0, 1000)
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = (durationMs / 1000).toInt()
        return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    }

    private fun deleteQuietly(file: File?) {
        runCatching {
            if (file?.exists() == true) {
                file.delete()
            }
        }
    }

    private fun preparePlayback(file: File, autoplay: Boolean) {
        if (playbackLoadedPath == file.absolutePath && playbackPlayer != null) {
            playbackShouldStart = autoplay
            if (autoplay && playbackPrepared) {
                runCatching { playbackPlayer?.start() }
                timerHandler.removeCallbacks(playbackProgressRunnable)
                timerHandler.post(playbackProgressRunnable)
            }
            refreshPlaybackSeekBar()
            syncControls()
            return
        }
        playbackLoadedPath = file.absolutePath
        playbackShouldStart = autoplay
        val surface = playbackSurface ?: return
        releasePlaybackPlayer()
        playbackPrepared = false
        playbackPlayer = MediaPlayer().apply {
            setSurface(surface)
            isLooping = false
            setDataSource(file.absolutePath)
            setOnPreparedListener { player ->
                playbackPrepared = true
                if (autoplay) {
                    player.start()
                    timerHandler.removeCallbacks(playbackProgressRunnable)
                    timerHandler.post(playbackProgressRunnable)
                } else {
                    player.seekTo(1)
                }
                refreshPlaybackSeekBar()
                syncControls()
            }
            setOnCompletionListener {
                playbackShouldStart = false
                timerHandler.removeCallbacks(playbackProgressRunnable)
                runCatching { it.pause() }
                runCatching { it.seekTo(0) }
                refreshPlaybackSeekBar()
                syncControls()
            }
            setOnErrorListener { _, _, _ ->
                playbackShouldStart = false
                timerHandler.removeCallbacks(playbackProgressRunnable)
                releasePlaybackPlayer()
                renderStatus(getString(R.string.media_capsule_error_open_first))
                syncControls()
                true
            }
            prepareAsync()
        }
    }

    private fun releasePlaybackPlayer() {
        runCatching {
            playbackPlayer?.setOnPreparedListener(null)
            playbackPlayer?.setOnCompletionListener(null)
            playbackPlayer?.setOnErrorListener(null)
            playbackPlayer?.stop()
        }
        runCatching { playbackPlayer?.release() }
        playbackPlayer = null
        playbackPrepared = false
    }

    private fun launchedFromKeyboard(): Boolean =
        intent?.getBooleanExtra(EXTRA_FROM_KEYBOARD, false) == true

    companion object {
        const val EXTRA_FROM_KEYBOARD = "from_keyboard"
        const val EXTRA_PREVIEW_CAPSULE_PATH = "preview_capsule_path"
    }
}
