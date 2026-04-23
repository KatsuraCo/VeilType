package com.truelock.enigma.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.truelock.enigma.media.MediaCapsuleType

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
        val detectedType = detectType(uri)
        val target = when (detectedType) {
            MediaCapsuleType.AUDIO -> AudioCapsuleActivity::class.java
            MediaCapsuleType.VIDEO -> VideoCapsuleActivity::class.java
            MediaCapsuleType.PHOTO -> PhotoCapsuleActivity::class.java
            null -> MainActivity::class.java
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
                if (read == 4) MediaCapsuleType.fromMagic(magic) else null
            }
        }.onFailure {
            Log.w(TAG, "detectType failed for uri=$uri", it)
        }.getOrNull()
    }

    private companion object {
        const val TAG = "MediaCapsuleRouter"
    }
}
