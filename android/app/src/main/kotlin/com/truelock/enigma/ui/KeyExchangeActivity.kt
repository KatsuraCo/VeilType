package com.truelock.enigma.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.truelock.enigma.R
import com.truelock.enigma.databinding.ActivityKeyExchangeBinding
import com.truelock.enigma.exchange.HandshakePreview
import com.truelock.enigma.exchange.IdentityStore
import com.truelock.enigma.exchange.ServerlessKeyExchangeService
import com.truelock.enigma.storage.FileKeyProfileRepository
import com.truelock.enigma.storage.ProfileKeyVault
import com.truelock.enigma.storage.SecureProfileStore

class KeyExchangeActivity : AppCompatActivity() {
    private enum class NfcMode {
        READ_CONTACT,
        WRITE_CONTACT,
    }

    companion object {
        private const val NFC_MIME_TYPE = "application/vnd.com.truelock.enigma.contact"
    }

    private lateinit var binding: ActivityKeyExchangeBinding
    private lateinit var identityStore: IdentityStore
    private lateinit var secureProfileStore: SecureProfileStore
    private lateinit var keyExchangeService: ServerlessKeyExchangeService
    private var nfcAdapter: NfcAdapter? = null
    private var pendingNfcMode: NfcMode? = null
    private var pendingPreview: HandshakePreview? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityKeyExchangeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        identityStore = IdentityStore(applicationContext)
        secureProfileStore = SecureProfileStore(
            repository = FileKeyProfileRepository(applicationContext),
            keyVault = ProfileKeyVault(),
        )
        keyExchangeService = ServerlessKeyExchangeService(identityStore)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        binding.copyBundleButton.setOnClickListener { copyOwnBundle() }
        binding.shareBundleButton.setOnClickListener { shareOwnBundle() }
        binding.importBundleButton.setOnClickListener { importBundleFromClipboard() }
        binding.readNfcButton.setOnClickListener { prepareNfcMode(NfcMode.READ_CONTACT) }
        binding.writeNfcButton.setOnClickListener { prepareNfcMode(NfcMode.WRITE_CONTACT) }
        binding.createHandshakeProfileButton.setOnClickListener { createProfileFromPreview() }

        renderIdentity()
        clearPreview(getString(R.string.key_exchange_status_ready))
    }

    override fun onPause() {
        super.onPause()
        disableNfcReaderMode()
    }

    private fun renderIdentity() {
        val identity = identityStore.getOrCreateIdentity()
        binding.displayNameInput.setText(identity.displayName)
        binding.deviceIdText.text = getString(R.string.key_exchange_device_format, identity.deviceId)
    }

    private fun copyOwnBundle() {
        val displayName = binding.displayNameInput.text?.toString()?.trim().orEmpty()
            .ifBlank { getString(R.string.key_exchange_identity_default_name) }
        val encodedBundle = keyExchangeService.exportContactBundle(displayName)
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText(getString(R.string.key_exchange_bundle_label), encodedBundle),
        )
        renderIdentity()
        binding.statusText.text = getString(R.string.key_exchange_bundle_copied)
    }

    private fun shareOwnBundle() {
        val displayName = binding.displayNameInput.text?.toString()?.trim().orEmpty()
            .ifBlank { getString(R.string.key_exchange_identity_default_name) }
        val encodedBundle = keyExchangeService.exportContactBundle(displayName)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, encodedBundle)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.key_exchange_share_chooser)))
    }

    private fun prepareNfcMode(mode: NfcMode) {
        val adapter = nfcAdapter
        if (adapter == null) {
            binding.statusText.text = getString(R.string.key_exchange_nfc_unavailable)
            return
        }

        pendingNfcMode = mode
        val messageRes = when (mode) {
            NfcMode.READ_CONTACT -> R.string.key_exchange_nfc_ready_to_read
            NfcMode.WRITE_CONTACT -> R.string.key_exchange_nfc_ready_to_write
        }
        binding.statusText.text = getString(messageRes)

        adapter.enableReaderMode(
            this,
            { tag -> handleNfcTag(tag) },
            NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null,
        )
    }

    private fun importBundleFromClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val raw = clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()?.trim().orEmpty()
        if (raw.isBlank()) {
            clearPreview(getString(R.string.key_exchange_error_clipboard_empty))
            return
        }

        val preview = runCatching {
            keyExchangeService.importContactBundle(
                encodedBundle = raw,
            )
        }.getOrElse { throwable ->
            val message = if (throwable.message?.contains("own contact bundle", ignoreCase = true) == true) {
                getString(R.string.key_exchange_error_self_bundle)
            } else {
                getString(R.string.key_exchange_error_invalid_bundle)
            }
            clearPreview(message)
            return
        }

        pendingPreview = preview
        binding.remoteContactText.text = getString(
            R.string.key_exchange_remote_format,
            preview.remoteBundle.displayName,
        )
        binding.fingerprintText.text = preview.fingerprint
        binding.createHandshakeProfileButton.isEnabled = true
        binding.statusText.text = getString(R.string.key_exchange_status_imported)
    }

    private fun createProfileFromPreview() {
        val preview = pendingPreview ?: return
        val profile = preview.profile.copy(
            title = preview.remoteBundle.displayName,
            appPackage = null,
        )
        secureProfileStore.saveProfile(profile, preview.profileKey)
        clearPreview(getString(R.string.key_exchange_profile_created, profile.title))
    }

    private fun handleNfcTag(tag: Tag) {
        val mode = pendingNfcMode ?: return
        when (mode) {
            NfcMode.READ_CONTACT -> readContactFromNfc(tag)
            NfcMode.WRITE_CONTACT -> writeContactToNfc(tag)
        }
    }

    private fun readContactFromNfc(tag: Tag) {
        val payload = runCatching {
            val ndef = Ndef.get(tag) ?: error(getString(R.string.key_exchange_nfc_no_payload))
            ndef.connect()
            val message = ndef.ndefMessage ?: error(getString(R.string.key_exchange_nfc_no_payload))
            val record = message.records.firstOrNull { record ->
                record.tnf == NdefRecord.TNF_MIME_MEDIA &&
                    String(record.type, Charsets.UTF_8) == NFC_MIME_TYPE
            } ?: error(getString(R.string.key_exchange_nfc_no_payload))
            String(record.payload, Charsets.UTF_8)
        }.getOrElse {
            runOnUiThread {
                disableNfcReaderMode()
                clearPreview(it.message ?: getString(R.string.key_exchange_nfc_read_failed))
            }
            return
        }

        runOnUiThread {
            disableNfcReaderMode()
            importContactString(payload, getString(R.string.key_exchange_nfc_read_success))
        }
    }

    private fun writeContactToNfc(tag: Tag) {
        val displayName = binding.displayNameInput.text?.toString()?.trim().orEmpty()
            .ifBlank { getString(R.string.key_exchange_identity_default_name) }
        val encodedBundle = keyExchangeService.exportContactBundle(displayName)
        val message = NdefMessage(
            arrayOf(
                NdefRecord.createMime(NFC_MIME_TYPE, encodedBundle.toByteArray(Charsets.UTF_8)),
            ),
        )

        val result = runCatching {
            val ndef = Ndef.get(tag)
            when {
                ndef != null -> {
                    ndef.connect()
                    if (!ndef.isWritable) error(getString(R.string.key_exchange_nfc_write_failed))
                    if (ndef.maxSize < message.toByteArray().size) error(getString(R.string.key_exchange_nfc_write_failed))
                    ndef.writeNdefMessage(message)
                    ndef.close()
                }
                else -> {
                    val formatable = NdefFormatable.get(tag) ?: error(getString(R.string.key_exchange_nfc_write_failed))
                    formatable.connect()
                    formatable.format(message)
                    formatable.close()
                }
            }
        }

        runOnUiThread {
            disableNfcReaderMode()
            binding.statusText.text = if (result.isSuccess) {
                getString(R.string.key_exchange_nfc_write_success)
            } else {
                getString(R.string.key_exchange_nfc_write_failed)
            }
        }
    }

    private fun importContactString(raw: String, successStatus: String) {
        val preview = runCatching {
            keyExchangeService.importContactBundle(
                encodedBundle = raw,
            )
        }.getOrElse { throwable ->
            val message = if (throwable.message?.contains("own contact bundle", ignoreCase = true) == true) {
                getString(R.string.key_exchange_error_self_bundle)
            } else {
                getString(R.string.key_exchange_error_invalid_bundle)
            }
            clearPreview(message)
            return
        }

        pendingPreview = preview
        binding.remoteContactText.text = getString(
            R.string.key_exchange_remote_format,
            preview.remoteBundle.displayName,
        )
        binding.fingerprintText.text = preview.fingerprint
        binding.createHandshakeProfileButton.isEnabled = true
        binding.statusText.text = successStatus
    }

    private fun disableNfcReaderMode() {
        pendingNfcMode = null
        nfcAdapter?.disableReaderMode(this)
    }

    private fun clearPreview(status: String) {
        pendingPreview = null
        binding.remoteContactText.setText(R.string.key_exchange_remote_placeholder)
        binding.fingerprintText.setText(R.string.key_exchange_fingerprint_placeholder)
        binding.createHandshakeProfileButton.isEnabled = false
        binding.statusText.text = status
    }
}
