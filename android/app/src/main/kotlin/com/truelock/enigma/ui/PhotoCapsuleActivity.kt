package com.truelock.enigma.ui

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.drawable.GradientDrawable
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.truelock.enigma.R
import com.truelock.enigma.databinding.ActivityPhotoCapsuleBinding
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
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class PhotoCapsuleActivity : AppCompatActivity() {
    private data class PhotoDraft(
        val photoFile: File,
        val capsuleFile: File,
        val displayFile: File,
    )

    private lateinit var binding: ActivityPhotoCapsuleBinding
    private lateinit var secureProfileStore: SecureProfileStore
    private lateinit var mediaCapsuleService: MediaCapsuleService
    private lateinit var pendingCapsuleStore: PendingCapsuleStore
    private lateinit var biometricHelper: BiometricDecryptHelper

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var currentPhotoFile: File? = null
    private var currentCapsuleFile: File? = null
    private var currentDecrypted: DecryptedMediaCapsule? = null
    private var currentPlaybackFile: File? = null
    private var pendingCaptureFile: File? = null
    private val photoDrafts = mutableListOf<PhotoDraft>()
    private var selectedDraftIndex = -1
    private var preserveCapsulePathOnDestroy: String? = null
    private var cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startCameraPreview()
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
        binding = ActivityPhotoCapsuleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        secureProfileStore = SecureProfileStore(
            repository = FileKeyProfileRepository(applicationContext),
            keyVault = ProfileKeyVault(),
        )
        mediaCapsuleService = MediaCapsuleService(applicationContext, secureProfileStore)
        pendingCapsuleStore = PendingCapsuleStore(applicationContext)
        biometricHelper = BiometricDecryptHelper(this)

        binding.photoPreviewContainer.outlineProvider = ViewOutlineProvider.BACKGROUND
        binding.photoPreviewContainer.clipToOutline = true
        val bottomPanel = binding.shareButton.parent?.parent as? View
        val baseBottomPadding = bottomPanel?.paddingBottom ?: 0
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            bottomPanel?.setPadding(
                bottomPanel.paddingLeft,
                bottomPanel.paddingTop,
                bottomPanel.paddingRight,
                baseBottomPadding + systemBars.bottom,
            )
            insets
        }

        binding.captureButton.setOnClickListener {
            if (binding.photoView.visibility == View.VISIBLE) {
                showCameraForAdditionalPhoto()
            } else {
                capturePhoto()
            }
        }
        binding.openCapsuleButton.setOnClickListener { openCapsuleLauncher.launch(arrayOf("*/*")) }
        binding.shareButton.setOnClickListener {
            if (launchedFromKeyboard()) {
                sendCurrentCapsuleToKeyboard()
            } else {
                shareCurrentCapsule()
            }
        }
        binding.retakeButton.setOnClickListener { removeCurrentDraft() }
        binding.switchCameraButton.setOnClickListener {
            if (binding.photoView.visibility != View.VISIBLE) {
                cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }
                startCameraPreview()
            }
        }

        renderStatus(getString(R.string.media_capsule_status_ready))
        syncControls()
        if (!handleIncomingIntent(intent)) {
            ensureCameraPermissionAndStart()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!handleIncomingIntent(intent)) {
            ensureCameraPermissionAndStart()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraProvider?.unbindAll()
        cameraProvider = null
        deleteQuietly(pendingCaptureFile)
        cleanupDrafts(
            keepCapsule = preserveCapsulePathOnDestroy?.let(::File),
        )
    }

    private fun ensureCameraPermissionAndStart() {
        if (checkSelfPermission(Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            startCameraPreview()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
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
                val capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                provider.unbindAll()
                provider.bindToLifecycle(this, cameraSelector, preview, capture)
                imageCapture = capture
                binding.cameraPreviewView.visibility = View.VISIBLE
                binding.photoView.visibility = View.GONE
                renderStatus(getString(R.string.photo_capsule_status_camera_ready))
                syncControls()
            },
            ContextCompat.getMainExecutor(this),
        )
    }

    private fun capturePhoto() {
        val profile = resolveActiveProfile() ?: return
        val capture = imageCapture ?: run {
            renderStatus(getString(R.string.photo_capsule_status_opening_camera))
            return
        }
        pendingCaptureFile = mediaCapsuleService.createRecordingFile(MediaCapsuleType.PHOTO, "jpg")
        val output = ImageCapture.OutputFileOptions.Builder(pendingCaptureFile!!).build()
        renderStatus(getString(R.string.photo_capsule_status_opening_camera))
        capture.takePicture(
            output,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val photoFile = pendingCaptureFile ?: return
                    pendingCaptureFile = null
                    encryptCapturedPhoto(photoFile, profile)
                }

                override fun onError(exception: ImageCaptureException) {
                    pendingCaptureFile = null
                    renderStatus(getString(R.string.photo_capsule_status_capture_failed))
                }
            },
        )
    }

    private fun encryptCapturedPhoto(sourceFile: File, profile: KeyProfile) {
        runCatching {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(sourceFile.absolutePath, options)
            val capsule = mediaCapsuleService.encryptFile(
                sourceFile = sourceFile,
                type = MediaCapsuleType.PHOTO,
                mimeType = "image/jpeg",
                durationMs = 0L,
                profile = profile,
                width = options.outWidth.takeIf { it > 0 },
                height = options.outHeight.takeIf { it > 0 },
            )
            currentCapsuleFile = capsule
            val draft = PhotoDraft(
                photoFile = sourceFile,
                capsuleFile = capsule,
                displayFile = sourceFile,
            )
            photoDrafts.add(draft)
            selectDraft(photoDrafts.lastIndex)
            renderStatus(getString(R.string.photo_capsule_status_captured))
            syncControls()
        }.onFailure {
            renderStatus(getString(R.string.media_capsule_error_encrypt))
            syncControls()
        }
    }

    private fun importCapsule(uri: Uri) {
        val tempFile = runCatching {
            mediaCapsuleService.createRecordingFile(
                MediaCapsuleType.PHOTO,
                MediaCapsuleType.PHOTO.fileExtension,
            )
        }.getOrNull() ?: return
        runCatching {
            contentResolver.copyUriToFileWithLimit(uri, tempFile, MediaCapsuleService.MAX_MEDIA_BYTES)
        }.onFailure {
            renderStatus(getString(R.string.media_capsule_error_decrypt))
            syncControls()
            return
        }
        val profile = runCatching { mediaCapsuleService.resolveProfileForCapsule(tempFile) }.getOrNull()
        val decryptAction = {
            val decrypted = mediaCapsuleService.decryptFile(tempFile)
            cleanupDrafts()
            currentCapsuleFile = tempFile
            currentDecrypted = decrypted
            photoDrafts.clear()
            val displayFiles = if (decrypted.metadata.mimeType == "application/zip") {
                extractPhotoSet(decrypted.plaintextFile)
            } else {
                listOf(decrypted.plaintextFile)
            }
            currentPlaybackFile = displayFiles.firstOrNull()
            displayFiles.forEach { file ->
                photoDrafts.add(
                    PhotoDraft(
                        photoFile = file,
                        capsuleFile = tempFile,
                        displayFile = file,
                    ),
                )
            }
            selectDraft(0)
            renderStatus(getString(R.string.media_capsule_status_decrypted, decrypted.profile.title, "photo"))
            syncControls()
        }
        if (mediaCapsuleService.requiresBiometricForCapsule(tempFile)) {
            biometricHelper.authenticate(
                onSuccess = { decryptAction() },
                onError = {
                    deleteQuietly(tempFile)
                    renderStatus(it)
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
                syncControls()
            }
        }
    }

    private fun showPhoto(file: File) {
        binding.cameraPreviewView.visibility = View.GONE
        binding.photoView.visibility = View.VISIBLE
        binding.photoView.setImageBitmap(loadOrientedBitmap(file))
        syncControls()
    }

    private fun showCameraForAdditionalPhoto() {
        currentPhotoFile = null
        currentCapsuleFile = null
        currentDecrypted = null
        currentPlaybackFile = null
        selectedDraftIndex = -1
        binding.photoView.setImageDrawable(null)
        binding.photoView.visibility = View.GONE
        binding.cameraPreviewView.visibility = View.VISIBLE
        startCameraPreview()
        renderStatus(getString(R.string.photo_capsule_status_camera_ready))
        rebuildThumbnails()
        syncControls()
    }

    private fun resetToCaptureMode() {
        currentPhotoFile = null
        currentCapsuleFile = null
        currentDecrypted = null
        currentPlaybackFile = null
        selectedDraftIndex = -1
        binding.photoView.setImageDrawable(null)
        startCameraPreview()
        renderStatus(getString(R.string.photo_capsule_status_camera_ready))
        rebuildThumbnails()
        syncControls()
    }

    private fun selectDraft(index: Int) {
        if (index !in photoDrafts.indices) {
            resetToCaptureMode()
            return
        }
        selectedDraftIndex = index
        val draft = photoDrafts[index]
        currentPhotoFile = draft.photoFile
        currentCapsuleFile = draft.capsuleFile
        currentPlaybackFile = draft.displayFile
        showPhoto(draft.displayFile)
        renderStatus(getString(R.string.photo_capsule_status_captured))
        rebuildThumbnails()
        syncControls()
    }

    private fun removeCurrentDraft() {
        if (selectedDraftIndex !in photoDrafts.indices) {
            resetToCaptureMode()
            return
        }
        val removed = photoDrafts.removeAt(selectedDraftIndex)
        deleteQuietly(removed.capsuleFile)
        if (removed.displayFile.absolutePath != removed.photoFile.absolutePath) {
            deleteQuietly(removed.displayFile)
        }
        deleteQuietly(removed.photoFile)
        if (photoDrafts.isEmpty()) {
            resetToCaptureMode()
        } else {
            selectDraft((selectedDraftIndex - 1).coerceAtLeast(0))
        }
        syncControls()
    }

    private fun rebuildThumbnails() {
        binding.thumbnailRow.removeAllViews()
        binding.thumbnailScrollView.visibility = if (photoDrafts.isEmpty()) View.GONE else View.VISIBLE
        photoDrafts.forEachIndexed { index, draft ->
            val thumbnail = ImageView(this).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(dp(58), dp(58)).apply {
                    marginEnd = dp(8)
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageBitmap(loadOrientedBitmap(draft.displayFile, sampleSize = 4))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(12).toFloat()
                    setStroke(dp(2), if (index == selectedDraftIndex) 0xFFF0D28E.toInt() else 0x668CC7FF)
                }
                clipToOutline = true
                outlineProvider = ViewOutlineProvider.BACKGROUND
                setOnClickListener { selectDraft(index) }
            }
            binding.thumbnailRow.addView(thumbnail)
        }
        binding.thumbnailScrollView.post { binding.thumbnailScrollView.fullScroll(View.FOCUS_RIGHT) }
    }

    private fun shareCurrentCapsule() {
        val capsule = resolvePhotoCapsuleForExport() ?: run {
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
            clipData = ClipData.newUri(contentResolver, shareFile.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        grantUriAccess(uri, shareIntent)
        startActivity(Intent.createChooser(shareIntent, getString(R.string.media_capsule_share)))
    }

    private fun sendCurrentCapsuleToKeyboard() {
        val capsule = resolvePhotoCapsuleForExport() ?: run {
            renderStatus(getString(R.string.media_capsule_error_share_missing))
            return
        }
        preserveCapsulePathOnDestroy = capsule.absolutePath
        cleanupDrafts(keepCapsule = capsule)
        pendingCapsuleStore.save(MediaCapsuleType.PHOTO, capsule)
        finish()
    }

    private fun resolvePhotoCapsuleForExport(): File? {
        if (currentDecrypted != null || photoDrafts.size <= 1) {
            return currentCapsuleFile
        }
        val profile = resolveActiveProfile() ?: return null
        val zipFile = createPhotoSetZip(photoDrafts.map { it.photoFile })
        val capsule = runCatching {
            mediaCapsuleService.encryptFile(
                sourceFile = zipFile,
                type = MediaCapsuleType.PHOTO,
                mimeType = "application/zip",
                durationMs = 0L,
                profile = profile,
            )
        }.getOrElse {
            deleteQuietly(zipFile)
            renderStatus(getString(R.string.media_capsule_error_encrypt))
            return null
        }
        deleteQuietly(zipFile)
        currentCapsuleFile = capsule
        return capsule
    }

    private fun createPhotoSetZip(files: List<File>): File {
        val zipFile = mediaCapsuleService.createRecordingFile(MediaCapsuleType.PHOTO, "zip")
        ZipOutputStream(zipFile.outputStream()).use { zip ->
            files.filter { it.exists() }.forEachIndexed { index, file ->
                zip.putNextEntry(ZipEntry("photo_${index + 1}.jpg"))
                file.inputStream().use { input -> input.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return zipFile
    }

    private fun extractPhotoSet(zipFile: File): List<File> {
        val outputDir = File(cacheDir, "photo_sets_${System.currentTimeMillis()}").apply { mkdirs() }
        val extracted = mutableListOf<File>()
        ZipInputStream(zipFile.inputStream()).use { zip ->
            var entry = zip.nextEntry
            var index = 1
            while (entry != null) {
                if (!entry.isDirectory) {
                    val output = File(outputDir, "photo_${index++}.jpg")
                    output.outputStream().use { zip.copyTo(it) }
                    extracted.add(output)
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return extracted.ifEmpty { listOf(zipFile) }
    }

    private fun cleanupDrafts(keepCapsule: File? = null) {
        photoDrafts.forEach { draft ->
            if (keepCapsule?.absolutePath != draft.capsuleFile.absolutePath) {
                deleteQuietly(draft.capsuleFile)
            }
            if (draft.displayFile.absolutePath != draft.photoFile.absolutePath) {
                deleteQuietly(draft.displayFile)
            }
            if (keepCapsule?.absolutePath != draft.photoFile.absolutePath) {
                deleteQuietly(draft.photoFile)
            }
        }
        if (keepCapsule == null) {
            photoDrafts.clear()
            selectedDraftIndex = -1
        } else {
            val keptIndex = photoDrafts.indexOfFirst { it.capsuleFile.absolutePath == keepCapsule.absolutePath }
            if (keptIndex >= 0) {
                val keptDraft = photoDrafts[keptIndex]
                photoDrafts.clear()
                photoDrafts.add(keptDraft)
                selectedDraftIndex = 0
            } else {
                photoDrafts.clear()
                selectedDraftIndex = -1
            }
        }
    }

    private fun handleIncomingIntent(intent: Intent?): Boolean {
        val previewPath = intent?.getStringExtra(EXTRA_PREVIEW_CAPSULE_PATH)
        if (!previewPath.isNullOrBlank()) {
            importCapsule(Uri.fromFile(File(previewPath)))
            return true
        }
        val uri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> intent.uriExtraCompat(Intent.EXTRA_STREAM)
            else -> null
        } ?: return false
        importCapsule(uri)
        return true
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

    private fun renderStatus(message: String) {
        binding.statusText.text = message
    }

    private fun syncControls() {
        val hasPhotoPreview = binding.photoView.visibility == View.VISIBLE
        val hasCapsule = currentCapsuleFile != null
        val isOpenedCapsule = currentDecrypted != null
        binding.captureButton.text = if (hasPhotoPreview) {
            getString(R.string.photo_capsule_add_more)
        } else {
            getString(R.string.photo_capsule_capture)
        }
        binding.captureButton.visibility = if (isOpenedCapsule) View.GONE else View.VISIBLE
        binding.shareButton.isEnabled = hasCapsule
        binding.shareButton.visibility = if (hasCapsule) View.VISIBLE else View.GONE
        binding.shareButton.text = if (launchedFromKeyboard()) {
            getString(R.string.keyboard_capsule_send_short)
        } else {
            getString(R.string.media_capsule_share)
        }
        binding.openCapsuleButton.visibility = if (launchedFromKeyboard() || isOpenedCapsule) View.GONE else View.VISIBLE
        binding.openCapsuleButton.isEnabled = !launchedFromKeyboard() && !isOpenedCapsule
        binding.retakeButton.visibility = if (hasPhotoPreview && !isOpenedCapsule) View.VISIBLE else View.GONE
        binding.retakeButton.isEnabled = hasPhotoPreview && !isOpenedCapsule
        binding.switchCameraButton.visibility = if (hasPhotoPreview || isOpenedCapsule) View.GONE else View.VISIBLE
    }

    private fun loadOrientedBitmap(file: File, sampleSize: Int = 1): Bitmap? {
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize.coerceAtLeast(1) }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
        val rotation = runCatching {
            when (ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)
        if (rotation == 0f) return bitmap
        return Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            Matrix().apply { postRotate(rotation) },
            true,
        ).also {
            if (it !== bitmap) bitmap.recycle()
        }
    }

    private fun deleteQuietly(file: File?) {
        runCatching {
            if (file?.exists() == true) {
                file.delete()
            }
        }
    }

    private fun launchedFromKeyboard(): Boolean =
        intent?.getBooleanExtra(EXTRA_FROM_KEYBOARD, false) == true

    companion object {
        const val EXTRA_FROM_KEYBOARD = "from_keyboard"
        const val EXTRA_PREVIEW_CAPSULE_PATH = "preview_capsule_path"
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics,
        ).toInt()
}
