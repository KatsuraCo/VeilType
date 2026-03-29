package com.truelock.enigma.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
        val uri = resolveIncomingUri(intent) ?: return
        val target = when (detectType(uri)) {
            MediaCapsuleType.AUDIO -> AudioCapsuleActivity::class.java
            MediaCapsuleType.VIDEO -> VideoCapsuleActivity::class.java
            null -> MainActivity::class.java
        }

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
            Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
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
        }.getOrNull()
    }
}
