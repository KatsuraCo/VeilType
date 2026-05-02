package com.truelock.enigma.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.truelock.enigma.R
import com.truelock.enigma.license.LicenseStore

class LicenseActivity : AppCompatActivity() {
    private lateinit var licenseStore: LicenseStore
    private lateinit var statusText: TextView
    private lateinit var codeInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        licenseStore = LicenseStore(applicationContext)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(32))
        }
        setContentView(
            ScrollView(this).apply { addView(root) },
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        root.addView(title(getString(R.string.license_title), 26f))
        root.addView(body(getString(R.string.license_body)))
        root.addView(label(getString(R.string.license_device_id_title)))
        root.addView(body(licenseStore.deviceId()))
        root.addView(
            Button(this).apply {
                text = getString(R.string.license_copy_device_id)
                setOnClickListener { copyToClipboard(getString(R.string.license_device_id_title), licenseStore.deviceId()) }
            },
        )

        codeInput = EditText(this).apply {
            hint = getString(R.string.license_code_hint)
            minLines = 4
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        root.addView(codeInput)
        root.addView(
            Button(this).apply {
                text = getString(R.string.license_paste_code)
                setOnClickListener { pasteCode() }
            },
        )
        root.addView(
            Button(this).apply {
                text = getString(R.string.license_activate)
                setOnClickListener { activate() }
            },
        )
        root.addView(
            Button(this).apply {
                text = getString(R.string.license_deactivate)
                setOnClickListener {
                    licenseStore.clear()
                    renderStatus()
                }
            },
        )

        statusText = body("")
        root.addView(statusText)
        renderStatus()
    }

    private fun pasteCode() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
        codeInput.setText(text)
        codeInput.setSelection(codeInput.text?.length ?: 0)
    }

    private fun activate() {
        val entitlement = licenseStore.activate(codeInput.text?.toString().orEmpty())
        statusText.text = when (entitlement.reason) {
            LicenseStore.Entitlement.Reason.ACTIVE -> getString(R.string.license_status_active)
            LicenseStore.Entitlement.Reason.MISSING -> getString(R.string.license_status_missing)
            LicenseStore.Entitlement.Reason.INVALID_FORMAT -> getString(R.string.license_status_invalid_format)
            LicenseStore.Entitlement.Reason.INVALID_SIGNATURE -> getString(R.string.license_status_invalid_signature)
            LicenseStore.Entitlement.Reason.WRONG_DEVICE -> getString(R.string.license_status_wrong_device)
            LicenseStore.Entitlement.Reason.EXPIRED -> getString(R.string.license_status_expired)
            LicenseStore.Entitlement.Reason.FREE_PLAN -> getString(R.string.license_status_free_plan)
        }
    }

    private fun renderStatus() {
        val entitlement = licenseStore.current()
        statusText.text = if (entitlement.active) {
            getString(
                R.string.license_status_active_details,
                entitlement.payload?.licenseId.orEmpty(),
            )
        } else {
            getString(R.string.license_status_missing)
        }
    }

    private fun copyToClipboard(label: String, value: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
    }

    private fun title(textValue: String, size: Float): TextView =
        TextView(this).apply {
            text = textValue
            textSize = size
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dp(10))
        }

    private fun label(textValue: String): TextView =
        title(textValue, 16f)

    private fun body(textValue: String): TextView =
        TextView(this).apply {
            text = textValue
            textSize = 14f
            setPadding(0, dp(6), 0, dp(12))
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
