package com.tatkal.assistant

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.api.CommonStatusCodes

/**
 * Lightweight IRCTC journey helper.
 * IRCTC opens in Chrome/the system browser, not an embedded WebView.
 * Journey details and OTP are copied only after the user taps the relevant control.
 */
class MainActivity : Activity() {
    private lateinit var from: EditText
    private lateinit var to: EditText
    private lateinit var date: EditText
    private lateinit var train: EditText
    private val prefs by lazy { getSharedPreferences("journey_profile", MODE_PRIVATE) }
    private var smsReceiverRegistered = false

    private val smsConsentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != SmsRetriever.SMS_RETRIEVED_ACTION) return
            val extras = intent.extras ?: return
            val status = extras.get(SmsRetriever.EXTRA_STATUS) as? com.google.android.gms.common.api.Status ?: return
            when (status.statusCode) {
                CommonStatusCodes.SUCCESS -> {
                    val consentIntent = extras.getParcelable<Intent>(SmsRetriever.EXTRA_CONSENT_INTENT)
                    if (consentIntent != null) {
                        @Suppress("DEPRECATION")
                        startActivityForResult(consentIntent, SMS_CONSENT_REQUEST)
                    }
                }
                CommonStatusCodes.TIMEOUT ->
                    Toast.makeText(this@MainActivity, "OTP listening timed out. Tap OTP assist to try again.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(157, 22, 40)
        window.navigationBarColor = Color.WHITE
        registerSmsConsentReceiver()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundColor(Color.rgb(247, 248, 250))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(8), dp(10))
            setBackgroundColor(Color.rgb(157, 22, 40))
        }
        header.addView(TextView(this).apply {
            text = "TATKAL LITE"
            textSize = 18f
            setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        header.addView(actionButton("Open Chrome") { openIrctcInChrome() }.apply {
            setTextColor(Color.WHITE)
        })
        root.addView(header)

        root.addView(TextView(this).apply {
            text = "Journey details"
            textSize = 17f
            setTextColor(Color.BLACK)
            setPadding(dp(4), dp(18), dp(4), dp(6))
        })

        val first = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        from = field("From station / code")
        to = field("To station / code")
        first.addView(from, weightParams())
        first.addView(to, weightParams())
        root.addView(first)

        val second = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        date = field("Journey date (DD-MM-YYYY)")
        train = field("Train no. (optional)")
        second.addView(date, weightParams())
        second.addView(train, weightParams())
        root.addView(second)

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }
        controls.addView(actionButton("Save") { saveProfile() }, weightParams())
        controls.addView(actionButton("Copy details") { copyJourneyDetails() }, weightParams())
        controls.addView(actionButton("OTP assist") { startOtpListening() }, weightParams())
        root.addView(controls)

        root.addView(TextView(this).apply {
            text = "IRCTC opens in Chrome to avoid embedded-browser access errors. Save or copy your journey details here, then enter them on the official IRCTC site. OTP assist copies the code only after you approve Google's SMS consent prompt."
            textSize = 13f
            setTextColor(Color.DKGRAY)
            setPadding(dp(6), dp(18), dp(6), dp(8))
        })

        root.addView(actionButton("OPEN OFFICIAL IRCTC WEBSITE") { openIrctcInChrome() }.apply {
            textSize = 15f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(157, 22, 40))
        }, LinearLayout.LayoutParams(-1, dp(52)).apply {
            setMargins(0, dp(12), 0, 0)
        })

        setContentView(root)

        from.setText(prefs.getString("from", ""))
        to.setText(prefs.getString("to", ""))
        date.setText(prefs.getString("date", ""))
        train.setText(prefs.getString("train", ""))
    }

    private fun openIrctcInChrome() {
        val uri = Uri.parse(IRCTC_URL)
        val chromeIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage(CHROME_PACKAGE)
        }
        try {
            startActivity(chromeIntent)
        } catch (_: android.content.ActivityNotFoundException) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (_: Exception) {
                Toast.makeText(this, "No browser is available. Install or enable Chrome.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun field(hintText: String) = EditText(this).apply {
        hint = hintText
        textSize = 12f
        isSingleLine = true
        setPadding(dp(7), 0, dp(7), 0)
    }

    private fun weightParams() = LinearLayout.LayoutParams(0, dp(46), 1f).apply {
        setMargins(dp(3), dp(3), dp(3), dp(3))
    }

    private fun actionButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 11f
        isAllCaps = false
        setPadding(dp(3), 0, dp(3), 0)
        setOnClickListener { action() }
    }

    private fun saveProfile() {
        prefs.edit()
            .putString("from", from.text.toString().trim())
            .putString("to", to.text.toString().trim())
            .putString("date", date.text.toString().trim())
            .putString("train", train.text.toString().trim())
            .apply()
        Toast.makeText(this, "Journey details saved on this device", Toast.LENGTH_SHORT).show()
    }

    private fun copyJourneyDetails() {
        val lines = listOf(
            "From: ${from.text.toString().trim()}",
            "To: ${to.text.toString().trim()}",
            "Journey date: ${date.text.toString().trim()}",
            "Train: ${train.text.toString().trim()}"
        ).filterNot { it.substringAfter(": ").isBlank() }
        if (lines.isEmpty()) {
            Toast.makeText(this, "Enter journey details first.", Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Journey details", lines.joinToString("\n")))
        Toast.makeText(this, "Journey details copied. Paste them in Chrome as needed.", Toast.LENGTH_LONG).show()
    }

    private fun registerSmsConsentReceiver() {
        val filter = IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerReceiver(smsConsentReceiver, filter, SmsRetriever.SEND_PERMISSION, null, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(smsConsentReceiver, filter, SmsRetriever.SEND_PERMISSION, null)
        }
        smsReceiverRegistered = true
    }

    private fun startOtpListening() {
        SmsRetriever.getClient(this).startSmsUserConsent(null)
            .addOnSuccessListener {
                Toast.makeText(this, "OTP listener active for up to 5 minutes. Approve the SMS prompt when it arrives.", Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Could not start OTP assist. Please enter OTP manually.", Toast.LENGTH_LONG).show()
            }
    }

    @Deprecated("Deprecated in Android API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != SMS_CONSENT_REQUEST || resultCode != RESULT_OK || data == null) return
        val message = data.getStringExtra(SmsRetriever.EXTRA_SMS_MESSAGE).orEmpty()
        val otp = Regex("(?<!\\d)\\d{4,8}(?!\\d)").find(message)?.value
        if (otp.isNullOrBlank()) {
            Toast.makeText(this, "No clear OTP found. Please enter it manually.", Toast.LENGTH_LONG).show()
            return
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("OTP", otp))
        Toast.makeText(this, "OTP copied after your approval. Paste it into IRCTC yourself.", Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        if (smsReceiverRegistered) {
            try { unregisterReceiver(smsConsentReceiver) } catch (_: IllegalArgumentException) { }
            smsReceiverRegistered = false
        }
        super.onDestroy()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val IRCTC_URL = "https://www.irctc.co.in/nget/train-search"
        private const val CHROME_PACKAGE = "com.android.chrome"
        private const val SMS_CONSENT_REQUEST = 4102
    }
}
