package com.truelock.enigma.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class AudioPermissionRequestActivity : AppCompatActivity() {
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        sendResult(granted)
        if (!granted && !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName"),
                ),
            )
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            sendResult(true)
            finish()
            return
        }

        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }

    private fun sendResult(granted: Boolean) {
        sendBroadcast(
            Intent(ACTION_AUDIO_PERMISSION_RESULT).apply {
                setPackage(packageName)
                putExtra(EXTRA_GRANTED, granted)
            },
        )
    }

    companion object {
        const val ACTION_AUDIO_PERMISSION_RESULT = "com.truelock.enigma.ACTION_AUDIO_PERMISSION_RESULT"
        const val EXTRA_GRANTED = "granted"
    }
}
