package com.tatkal.assistant

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * A preparation companion only. It does not log in, submit bookings,
 * solve CAPTCHA, handle OTP, or interact with payment flows.
 */
class MainActivity : Activity() {
    private val fields = linkedMapOf<String, EditText>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }

        val title = TextView(this).apply {
            text = "Tatkal Assistant"
            textSize = 22f
            setPadding(0, dp(6), 0, dp(2))
        }
        val subtitle = TextView(this).apply {
            text = "Prepare your details here, then complete every booking step yourself on IRCTC."
            textSize = 13f
            setPadding(0, 0, 0, dp(8))
        }
        root.addView(title)
        root.addView(subtitle)

        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        section(form, "Journey details")
        addField(form, "From station", "e.g. NJP")
        addField(form, "To station", "e.g. HWH")
        addField(form, "Journey date", "DD-MM-YYYY")
        addField(form, "Train number / name", "Train details")
        addField(form, "Travel class", "e.g. 3A, SL, CC")
        addField(form, "Quota", "Tatkal")

        section(form, "Passenger 1")
        addField(form, "Passenger name", "Full name as per ID")
        addField(form, "Age", "Age in years")
        addField(form, "Gender", "Male / Female / Transgender")
        addField(form, "Berth preference", "Optional")

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val copyButton = Button(this).apply {
            text = "Copy details"
            setOnClickListener { copyPreparedDetails() }
        }
        val clearButton = Button(this).apply {
            text = "Clear"
            setOnClickListener {
                fields.values.forEach { it.text.clear() }
                Toast.makeText(this@MainActivity, "Form cleared", Toast.LENGTH_SHORT).show()
            }
        }
        actionRow.addView(copyButton, LinearLayout.LayoutParams(0, dp(48), 1f))
        actionRow.addView(clearButton, LinearLayout.LayoutParams(0, dp(48), 1f))
        form.addView(actionRow)

        val note = TextView(this).apply {
            text = "Privacy: details stay in this screen's memory and are not saved or sent by this app. Copying places them on your clipboard. CAPTCHA, OTP, passenger verification and payment must be completed manually."
            textSize = 12f
            setPadding(0, dp(6), 0, dp(10))
        }
        form.addView(note)

        val formScroll = ScrollView(this).apply {
            addView(form)
            isFillViewport = false
        }
        root.addView(formScroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        val openButton = Button(this).apply {
            text = "Open official IRCTC website"
            setOnClickListener { openIrctcInBrowser() }
        }
        root.addView(openButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
        ))

        setContentView(root)
    }

    private fun openIrctcInBrowser() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(IRCTC_URL))
        try {
            startActivity(intent)
        } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(this, "No browser is installed. Install or enable a browser and try again.", Toast.LENGTH_LONG).show()
        }
    }

    private fun section(parent: LinearLayout, label: String) {
        parent.addView(TextView(this).apply {
            text = label
            textSize = 16f
            setPadding(0, dp(8), 0, dp(4))
        })
    }

    private fun addField(parent: LinearLayout, label: String, hint: String) {
        val input = EditText(this).apply {
            this.hint = hint
            textSize = 14f
            setSingleLine(true)
            contentDescription = label
            setPadding(dp(8), 0, dp(8), 0)
        }
        fields[label] = input
        parent.addView(TextView(this).apply {
            text = label
            textSize = 12f
        })
        parent.addView(input, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(46)
        ))
    }

    private fun copyPreparedDetails() {
        val summary = fields.entries.joinToString("\n") { (label, input) ->
            "$label: ${input.text.toString().trim()}"
        }.trim()
        if (summary.isBlank()) {
            Toast.makeText(this, "Enter details first", Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Tatkal booking details", summary))
        Toast.makeText(this, "Details copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val IRCTC_URL = "https://www.irctc.co.in/nget/"
    }
}
