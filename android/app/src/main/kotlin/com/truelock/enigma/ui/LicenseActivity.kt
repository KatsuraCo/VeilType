package com.truelock.enigma.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
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
    private lateinit var statusPill: TextView
    private lateinit var statusText: TextView
    private lateinit var codeInput: EditText
    private lateinit var testLicenseButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        licenseStore = LicenseStore(applicationContext)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(34))
        }
        setContentView(
            ScrollView(this).apply {
                setBackgroundResource(R.drawable.bg_app_page)
                addView(root)
            },
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        root.addView(
            card {
                addView(kicker(getString(R.string.license_kicker)))
                addView(title(getString(R.string.license_title), 30f))
                addView(body(getString(R.string.license_body)))
                statusPill = TextView(context).apply {
                    gravity = Gravity.CENTER
                    setTypeface(typeface, Typeface.BOLD)
                    setPadding(dp(12), dp(8), dp(12), dp(8))
                }
                addView(statusPill, matchWrap().apply { topMargin = dp(10) })
            },
        )

        root.addView(
            card {
                addView(sectionTitle(getString(R.string.license_device_id_title)))
                addView(body(getString(R.string.license_device_id_body)))
                val deviceText = TextView(context).apply {
                    text = licenseStore.deviceId()
                    setTextColor(Color.parseColor("#F6F8FC"))
                    textSize = 14f
                    typeface = Typeface.MONOSPACE
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                    background = rounded("#13243A", "#2A4668", 18)
                }
                addView(deviceText, matchWrap().apply { topMargin = dp(10) })
                addView(
                    styledButton(getString(R.string.license_copy_device_id), primary = false).apply {
                        setOnClickListener {
                            copyToClipboard(getString(R.string.license_device_id_title), licenseStore.deviceId())
                            statusText.text = getString(R.string.license_status_device_copied)
                        }
                    },
                    matchFixed(52).apply { topMargin = dp(12) },
                )
            },
        )

        root.addView(
            card {
                addView(sectionTitle(getString(R.string.license_enter_code_title)))
                addView(body(getString(R.string.license_enter_code_body)))
                codeInput = EditText(context).apply {
                    hint = getString(R.string.license_code_hint)
                    minLines = 5
                    gravity = Gravity.TOP or Gravity.START
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    setTextColor(Color.parseColor("#F6F8FC"))
                    setHintTextColor(Color.parseColor("#728196"))
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                    background = rounded("#13243A", "#2A4668", 18)
                }
                addView(codeInput, matchWrap().apply { topMargin = dp(12) })

                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(
                        styledButton(getString(R.string.license_paste_code), primary = false).apply {
                            setOnClickListener { pasteCode() }
                        },
                        LinearLayout.LayoutParams(0, dp(52), 1f),
                    )
                    addView(
                        styledButton(getString(R.string.license_activate), primary = true).apply {
                            setOnClickListener { activate() }
                        },
                        LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginStart = dp(10) },
                    )
                }
                addView(row, matchWrap().apply { topMargin = dp(12) })

                addView(
                    styledButton(getString(R.string.license_deactivate), primary = false).apply {
                        setOnClickListener {
                            licenseStore.clear()
                            renderStatus(getString(R.string.license_status_missing))
                        }
                    },
                    matchFixed(52).apply { topMargin = dp(10) },
                )
            },
        )

        if (licenseStore.canUseTestLicense()) {
            root.addView(
                card {
                    addView(sectionTitle(getString(R.string.license_test_title)))
                    addView(body(getString(R.string.license_test_body)))
                    testLicenseButton = styledButton("", primary = false).apply {
                        setOnClickListener { toggleTestLicense() }
                    }
                    addView(testLicenseButton, matchFixed(52).apply { topMargin = dp(12) })
                },
            )
        }

        statusText = body("").apply {
            setTextColor(Color.parseColor("#D8E3F0"))
        }
        root.addView(statusText, matchWrap().apply { topMargin = dp(8) })
        renderStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::statusText.isInitialized) {
            renderStatus()
        }
    }

    private fun pasteCode() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
        codeInput.setText(text)
        codeInput.setSelection(codeInput.text?.length ?: 0)
        statusText.text = getString(R.string.license_status_code_pasted)
    }

    private fun activate() {
        val entitlement = licenseStore.activate(codeInput.text?.toString().orEmpty())
        val message = when (entitlement.reason) {
            LicenseStore.Entitlement.Reason.ACTIVE -> getString(R.string.license_status_active)
            LicenseStore.Entitlement.Reason.MISSING -> getString(R.string.license_status_missing)
            LicenseStore.Entitlement.Reason.INVALID_FORMAT -> getString(R.string.license_status_invalid_format)
            LicenseStore.Entitlement.Reason.INVALID_SIGNATURE -> getString(R.string.license_status_invalid_signature)
            LicenseStore.Entitlement.Reason.WRONG_DEVICE -> getString(R.string.license_status_wrong_device)
            LicenseStore.Entitlement.Reason.EXPIRED -> getString(R.string.license_status_expired)
            LicenseStore.Entitlement.Reason.FREE_PLAN -> getString(R.string.license_status_free_plan)
        }
        renderStatus(message)
    }

    private fun toggleTestLicense() {
        if (licenseStore.isTestLicenseActive()) {
            licenseStore.deactivateTestLicense()
            renderStatus(getString(R.string.license_test_deactivated))
        } else {
            licenseStore.activateTestLicense()
            renderStatus(getString(R.string.license_test_activated))
        }
    }

    private fun renderStatus(message: String? = null) {
        val entitlement = licenseStore.current()
        statusPill.text = if (entitlement.active) {
            getString(R.string.license_status_active_details, entitlement.payload?.licenseId.orEmpty())
        } else {
            getString(R.string.license_status_missing)
        }
        statusPill.setTextColor(
            if (entitlement.active) Color.parseColor("#D8FFE0") else Color.parseColor("#FFD7B5"),
        )
        statusPill.background =
            if (entitlement.active) rounded("#163A27", "#4D8A62", 999) else rounded("#3A2616", "#A87C2A", 999)
        statusText.text = message ?: if (entitlement.active) {
            getString(R.string.license_status_paid_features_unlocked)
        } else {
            getString(R.string.license_required_status)
        }
        if (::testLicenseButton.isInitialized) {
            testLicenseButton.text =
                if (licenseStore.isTestLicenseActive()) {
                    getString(R.string.license_deactivate_test)
                } else {
                    getString(R.string.license_activate_test)
                }
        }
    }

    private fun copyToClipboard(label: String, value: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
    }

    private fun card(build: LinearLayout.() -> Unit): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = rounded("#18273A", "#2A4668", 24)
            build()
            layoutParams = matchWrap().apply { bottomMargin = dp(14) }
        }

    private fun kicker(textValue: String): TextView =
        TextView(this).apply {
            text = textValue.uppercase()
            setTextColor(Color.parseColor("#E4BE67"))
            textSize = 12f
            letterSpacing = 0.12f
            setTypeface(typeface, Typeface.BOLD)
        }

    private fun title(textValue: String, size: Float): TextView =
        TextView(this).apply {
            text = textValue
            textSize = size
            setTextColor(Color.parseColor("#F6F8FC"))
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(6), 0, dp(10))
        }

    private fun sectionTitle(textValue: String): TextView =
        title(textValue, 18f)

    private fun body(textValue: String): TextView =
        TextView(this).apply {
            text = textValue
            textSize = 14f
            setTextColor(Color.parseColor("#A6B0C3"))
            setPadding(0, dp(4), 0, dp(8))
        }

    private fun styledButton(textValue: String, primary: Boolean): Button =
        Button(this).apply {
            text = textValue
            isAllCaps = false
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(if (primary) Color.parseColor("#0D2340") else Color.WHITE)
            background =
                if (primary) rounded("#E4BE67", "#A87C2A", 18) else rounded("#213854", "#4D6688", 18)
            minHeight = 0
            minWidth = 0
        }

    private fun rounded(fill: String, stroke: String, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(Color.parseColor(fill))
            cornerRadius = dp(radiusDp).toFloat()
            setStroke(dp(1), Color.parseColor(stroke))
        }

    private fun matchWrap(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )

    private fun matchFixed(heightDp: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(heightDp),
        )

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
