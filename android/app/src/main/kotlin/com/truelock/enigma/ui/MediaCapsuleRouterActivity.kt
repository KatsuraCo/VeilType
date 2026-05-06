package com.truelock.enigma.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.truelock.enigma.media.MediaCapsuleService
import com.truelock.enigma.media.MediaCapsuleType
import com.truelock.enigma.media.PendingCapsuleStore
import java.io.File

class MediaCapsuleRouterActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        routeIncomingIntent(intent)
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        routeIncomingIntent(intent)
        finish()
    }

    private fun routeIncomingIntent(intent: Intent?) {
        val uri = resolveIncomingUri(intent)
        if (uri == null) {
            Log.w(TAG, "routeIncomingIntent: uri missing action=${intent?.action} type=${intent?.type}")
            return
        }
        val detectedType = detectType(uri) ?: inferTypeFromIntent(intent, uri)
        if (detectedType == MediaCapsuleType.AUDIO) {
            routeAudioToKeyboard(uri)
            Log.d(TAG, "routeIncomingIntent audio routed to keyboard uri=$uri")
            return
        }
        val target = when (detectedType) {
            MediaCapsuleType.AUDIO -> AudioCapsuleActivity::class.java
            MediaCapsuleType.VIDEO -> VideoCapsuleActivity::class.java
            MediaCapsuleType.PHOTO -> PhotoCapsuleActivity::class.java
            null -> {
                Log.w(TAG, "routeIncomingIntent: unsupported capsule uri=$uri type=${intent?.type}")
                return
            }
        }
        Log.d(
            TAG,
            "routeIncomingIntent action=${intent?.action} type=${intent?.type} uri=$uri detected=$detectedType target=${target.simpleName}",
        )

        startActivity(
            Intent(this, target).apply {
                action = Intent.ACTION_VIEW
                data = uri
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
        )
    }

    private fun routeAudioToKeyboard(uri: Uri): Boolean =
        runCatching {
            val incomingDir = File(cacheDir, "incoming_capsules").apply {
                mkdirs()
                listFiles()?.forEach { file -> runCatching { file.delete() } }
            }
            val target = File(incomingDir, "incoming_audio_${System.currentTimeMillis()}.${MediaCapsuleType.AUDIO.fileExtension}")
            copyUriToFileWithLimit(uri, target, MediaCapsuleService.MAX_MEDIA_BYTES)
            PendingCapsuleStore(applicationContext).save(MediaCapsuleType.AUDIO, target)
            sendBroadcast(
                Intent(ACTION_PENDING_CAPSULE_READY).apply {
                    setPackage(packageName)
                    addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                    putExtra(EXTRA_PENDING_CAPSULE_TYPE, MediaCapsuleType.AUDIO.name)
                },
            )
            true
        }.onFailure {
            Log.w(TAG, "routeAudioToKeyboard failed uri=$uri", it)
        }.getOrDefault(false)

    private fun resolveIncomingUri(intent: Intent?): Uri? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> intent.uriExtraCompat(Intent.EXTRA_STREAM)
            else -> null
        }
    }

    private fun detectType(uri: Uri): MediaCapsuleType? {
        return runCatching {
            contentResolver.openInputStream(uri)?.use { input ->
                val magic = ByteArray(4)
                val read = input.read(magic)
                if (read == 4) runCatching { MediaCapsuleType.fromMagic(magic) }.getOrNull() else null
            }
        }.onFailure {
            Log.w(TAG, "detectType failed for uri=$uri", it)
        }.getOrNull()
    }

    private fun inferTypeFromIntent(intent: Intent?, uri: Uri): MediaCapsuleType? {
        val type = intent?.type.orEmpty().lowercase()
        val text = listOfNotNull(
            uri.path,
            uri.lastPathSegment,
            intent?.getStringExtra(Intent.EXTRA_TITLE),
            intent?.clipData?.getItemAt(0)?.uri?.lastPathSegment,
        ).joinToString(" ").lowercase()
        return when {
            type == MediaCapsuleType.AUDIO.capsuleMimeType ||
                type in MediaCapsuleType.AUDIO.legacyMimeTypes ||
                MediaCapsuleType.AUDIO.legacyFileExtensions.any { text.contains(".$it") } -> MediaCapsuleType.AUDIO
            type == MediaCapsuleType.VIDEO.capsuleMimeType ||
                type in MediaCapsuleType.VIDEO.legacyMimeTypes ||
                MediaCapsuleType.VIDEO.legacyFileExtensions.any { text.contains(".$it") } -> MediaCapsuleType.VIDEO
            type == MediaCapsuleType.PHOTO.capsuleMimeType ||
                type in MediaCapsuleType.PHOTO.legacyMimeTypes ||
                MediaCapsuleType.PHOTO.legacyFileExtensions.any { text.contains(".$it") } -> MediaCapsuleType.PHOTO
            else -> null
        }
    }

    private fun copyUriToFileWithLimit(uri: Uri, target: File, maxBytes: Long) {
        contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var copied = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    copied += read
                    require(copied <= maxBytes) { "Incoming capsule is too large" }
                    output.write(buffer, 0, read)
                }
            }
        } ?: error("Unable to open incoming capsule")
    }

    companion object {
        const val TAG = "MediaCapsuleRouter"
        const val ACTION_PENDING_CAPSULE_READY = "com.truelock.enigma.ACTION_PENDING_CAPSULE_READY"
        const val EXTRA_PENDING_CAPSULE_TYPE = "pending_capsule_type"
        const val EXTRA_PENDING_CAPSULE_ERROR_MESSAGE = "pending_capsule_error_message"
    }
}
