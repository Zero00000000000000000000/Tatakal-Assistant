package com.tatkal.assistant

import android.annotation.SuppressLint
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
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.api.CommonStatusCodes

/**
 * Native Android companion with IRCTC displayed inside the app using WebView.
 * It does not spoof browser identity or bypass IRCTC security controls.
 */
class MainActivity : Activity() {
    private lateinit var browser: WebView
    private lateinit var progress: ProgressBar
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

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(157, 22, 40)
        window.navigationBarColor = Color.WHITE
        CookieManager.getInstance().setAcceptCookie(true)
        registerSmsConsentReceiver()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(247, 248, 250))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(6), dp(6), dp(6))
            setBackgroundColor(Color.rgb(157, 22, 40))
        }
        header.addView(TextView(this).apply {
            text = "TATKAL LITE"
            textSize = 17f
            setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(0, dp(46), 1f))
        header.addView(topButton("IRCTC") { browser.loadUrl(IRCTC_URL) })
        header.addView(topButton("Back") { if (browser.canGoBack()) browser.goBack() })
        header.addView(topButton("Reload") { browser.reload() })
        root.addView(header)

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            visibility = View.GONE
        }
        root.addView(progress, LinearLayout.LayoutParams(-1, dp(3)))

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(2), dp(8), dp(3))
            setBackgroundColor(Color.WHITE)
        }
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        from = field("From station / code")
        to = field("To station / code")
        row1.addView(from, weightParams())
        row1.addView(to, weightParams())
        panel.addView(row1)

        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        date = field("Journey date")
        train = field("Train no. (optional)")
        row2.addView(date, weightParams())
        row2.addView(train, weightParams())
        panel.addView(row2)

        val row3 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row3.addView(panelButton("Save") { saveProfile() }, weightParams())
        row3.addView(panelButton("Copy") { copyJourneyDetails() }, weightParams())
        row3.addView(panelButton("OTP assist") { startOtpListening() }, weightParams())
        panel.addView(row3)
        root.addView(panel)

        browser = WebView(this).apply { setBackgroundColor(Color.WHITE) }
        browser.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadsImagesAutomatically = true
            blockNetworkImage = false
            cacheMode = WebSettings.LOAD_DEFAULT
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            javaScriptCanOpenWindowsAutomatically = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowFileAccess = false
            allowContentAccess = false
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(browser, true)
        browser.webChromeClient = WebChromeClient()
        browser.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                if (uri.scheme != "https") return true
                val host = uri.host?.lowercase() ?: return true
                if (host == "irctc.co.in" || host.endsWith(".irctc.co.in")) return false
                return try {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                    true
                } catch (_: Exception) {
                    true
                }
            }

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                progress.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView, url: String) {
                progress.visibility = View.GONE
            }
        }
        root.addView(browser, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        from.setText(prefs.getString("from", ""))
        to.setText(prefs.getString("to", ""))
        date.setText(prefs.getString("date", ""))
        train.setText(prefs.getString("train", ""))
        if (savedInstanceState == null) browser.loadUrl(IRCTC_URL)
        else browser.restoreState(savedInstanceState)
    }

    private fun field(hintText: String) = EditText(this).apply {
        hint = hintText
        textSize = 12f
        isSingleLine = true
        setPadding(dp(7), 0, dp(7), 0)
    }

    private fun weightParams() = LinearLayout.LayoutParams(0, dp(42), 1f).apply {
        setMargins(dp(2), dp(1), dp(2), dp(1))
    }

    private fun topButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 10f
        isAllCaps = false
        setTextColor(Color.WHITE)
        setPadding(dp(2), 0, dp(2), 0)
        setOnClickListener { action() }
    }

    private fun panelButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 11f
        isAllCaps = false
        setPadding(dp(2), 0, dp(2), 0)
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
        Toast.makeText(this, "Journey details copied.", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, "OTP listener active. Approve Google's SMS prompt when it arrives.", Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Could not start OTP assist. Enter OTP manually.", Toast.LENGTH_LONG).show()
            }
    }

    @Deprecated("Deprecated in Android API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != SMS_CONSENT_REQUEST || resultCode != RESULT_OK || data == null) return
        val message = data.getStringExtra(SmsRetriever.EXTRA_SMS_MESSAGE).orEmpty()
        val otp = Regex("(?<!\\d)\\d{4,8}(?!\\d)").find(message)?.value
        if (otp.isNullOrBlank()) {
            Toast.makeText(this, "No clear OTP found. Enter it manually.", Toast.LENGTH_LONG).show()
            return
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("OTP", otp))
        Toast.makeText(this, "OTP copied after your approval. Paste it into the page.", Toast.LENGTH_LONG).show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (::browser.isInitialized) browser.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Deprecated in Android API")
    override fun onBackPressed() {
        if (::browser.isInitialized && browser.canGoBack()) browser.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        if (smsReceiverRegistered) {
            try { unregisterReceiver(smsConsentReceiver) } catch (_: IllegalArgumentException) { }
            smsReceiverRegistered = false
        }
        if (::browser.isInitialized) browser.destroy()
        super.onDestroy()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val IRCTC_URL = "https://www.irctc.co.in/nget/train-search"
        private const val SMS_CONSENT_REQUEST = 4102
    }
}
