package com.truelock.enigma.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.truelock.enigma.security.BiometricDecryptHelper

class MediaBiometricGateActivity : AppCompatActivity() {
    private lateinit var biometricHelper: BiometricDecryptHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        setFinishOnTouchOutside(false)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.setDimAmount(0f)

        val capsulePath = intent.getStringExtra(EXTRA_CAPSULE_PATH).orEmpty()
        if (capsulePath.isBlank()) {
            broadcastResult(success = false, capsulePath = capsulePath, errorMessage = "Missing capsule")
            finish()
            return
        }

        biometricHelper = BiometricDecryptHelper(this)
        biometricHelper.authenticate(
            onSuccess = {
                broadcastResult(success = true, capsulePath = capsulePath, errorMessage = null)
                finish()
            },
            onError = {
                broadcastResult(success = false, capsulePath = capsulePath, errorMessage = it)
                finish()
            },
        )
    }

    private fun broadcastResult(success: Boolean, capsulePath: String, errorMessage: String?) {
        sendBroadcast(
            Intent(ACTION_MEDIA_BIOMETRIC_RESULT).apply {
                setPackage(packageName)
                putExtra(EXTRA_SUCCESS, success)
                putExtra(EXTRA_CAPSULE_PATH, capsulePath)
                errorMessage?.let { putExtra(EXTRA_ERROR_MESSAGE, it) }
            },
        )
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }

    companion object {
        const val ACTION_MEDIA_BIOMETRIC_RESULT = "com.truelock.enigma.ACTION_MEDIA_BIOMETRIC_RESULT"
        const val EXTRA_CAPSULE_PATH = "capsule_path"
        const val EXTRA_SUCCESS = "success"
        const val EXTRA_ERROR_MESSAGE = "error_message"
    }
}
