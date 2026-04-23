package com.truelock.enigma.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.truelock.enigma.R
import com.truelock.enigma.clipboard.ClipboardDecryptResult
import com.truelock.enigma.clipboard.ClipboardDecryptService
import com.truelock.enigma.crypto.Tl1MessageCodec
import com.truelock.enigma.security.BiometricDecryptHelper
import com.truelock.enigma.storage.FileKeyProfileRepository
import com.truelock.enigma.storage.ProfileKeyVault
import com.truelock.enigma.storage.SecureProfileStore

class DecryptGateActivity : AppCompatActivity() {
    private lateinit var biometricHelper: BiometricDecryptHelper
    private lateinit var decryptService: ClipboardDecryptService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        biometricHelper = BiometricDecryptHelper(this)
        decryptService = ClipboardDecryptService(
            context = applicationContext,
            clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager,
            secureProfileStore = SecureProfileStore(
                repository = FileKeyProfileRepository(applicationContext),
                keyVault = ProfileKeyVault(),
            ),
            codec = Tl1MessageCodec(),
        )

        val encodedMessage = intent.getStringExtra(EXTRA_ENCODED_MESSAGE).orEmpty()
        val profileId = intent.getStringExtra(EXTRA_PROFILE_ID).orEmpty()
        if (encodedMessage.isBlank() || profileId.isBlank()) {
            broadcastFailure(getString(R.string.keyboard_decrypt_invalid))
            finish()
            return
        }

        biometricHelper.authenticate(
            onSuccess = {
                when (val result = decryptService.decryptWithProfile(encodedMessage, profileId)) {
                    is ClipboardDecryptResult.Success -> {
                        sendBroadcast(
                            Intent(ACTION_DECRYPT_RESULT).apply {
                                setPackage(packageName)
                                putExtra(EXTRA_SUCCESS, true)
                                putExtra(EXTRA_PLAINTEXT, result.plaintext)
                                putExtra(EXTRA_PROFILE_TITLE, result.profileTitle)
                            },
                        )
                    }
                    is ClipboardDecryptResult.AlreadyConsumed ->
                        broadcastFailure(getString(R.string.decrypt_one_time_consumed, result.profileTitle))
                    else -> broadcastFailure(getString(R.string.keyboard_decrypt_invalid))
                }
                finish()
            },
            onError = {
                broadcastFailure(it)
                finish()
            },
        )
    }

    private fun broadcastFailure(message: String) {
        sendBroadcast(
            Intent(ACTION_DECRYPT_RESULT).apply {
                setPackage(packageName)
                putExtra(EXTRA_SUCCESS, false)
                putExtra(EXTRA_ERROR_MESSAGE, message)
            },
        )
    }

    companion object {
        const val ACTION_DECRYPT_RESULT = "com.truelock.enigma.ACTION_DECRYPT_RESULT"
        const val EXTRA_ENCODED_MESSAGE = "encoded_message"
        const val EXTRA_PROFILE_ID = "profile_id"
        const val EXTRA_SUCCESS = "success"
        const val EXTRA_PLAINTEXT = "plaintext"
        const val EXTRA_PROFILE_TITLE = "profile_title"
        const val EXTRA_ERROR_MESSAGE = "error_message"
    }
}
