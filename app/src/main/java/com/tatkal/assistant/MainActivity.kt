package com.tatkal.assistant

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.api.CommonStatusCodes
import android.widget.Button
import android.widget.ImageView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import org.json.JSONTokener
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import org.opencv.imgproc.CLAHE

/**
 * Lightweight IRCTC companion browser.
 * User-triggered form assistance only; CAPTCHA, OTP, login, booking and payment
 * remain on the official site and are completed by the user.
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
                CommonStatusCodes.TIMEOUT -> Toast.makeText(this@MainActivity, "OTP listening timed out. Tap OTP assist to try again.", Toast.LENGTH_LONG).show()
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
            setPadding(dp(14), dp(8), dp(10), dp(8))
            setBackgroundColor(Color.rgb(157, 22, 40))
        }
        header.addView(TextView(this).apply {
            text = "TATKAL LITE"
            textSize = 18f
            setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(0, dp(44), 1f))
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
            setPadding(dp(9), dp(3), dp(9), dp(4))
            setBackgroundColor(Color.WHITE)
        }
        val first = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        from = field("From station / code")
        to = field("To station / code")
        first.addView(from, weightParams())
        first.addView(to, weightParams())
        panel.addView(first)

        val second = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        date = field("Journey date")
        train = field("Train no. (optional)")
        second.addView(date, weightParams())
        second.addView(train, weightParams())
        panel.addView(second)

        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        controls.addView(panelButton("Save") { saveProfile() }, weightParams())
        controls.addView(panelButton("Fill page") { fillVisibleFields() }, weightParams())
        controls.addView(panelButton("Open site") { browser.loadUrl(IRCTC_URL) }, weightParams())
        controls.addView(panelButton("OTP assist") { startOtpListening() }, weightParams())
        controls.addView(panelButton("OpenCV CAPTCHA") { enhanceCaptchaImages() }, weightParams())
        panel.addView(controls)
        root.addView(panel)

        browser = WebView(this).apply { setBackgroundColor(Color.WHITE) }
        browser.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadsImagesAutomatically = true
            blockNetworkImage = false // Do not break CAPTCHA/image verification.
            cacheMode = WebSettings.LOAD_DEFAULT
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            javaScriptCanOpenWindowsAutomatically = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowFileAccess = false
            allowContentAccess = false
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(browser, false)
        browser.webChromeClient = WebChromeClient()
        browser.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val host = request.url.host?.lowercase() ?: return true
                return !(host == "irctc.co.in" || host.endsWith(".irctc.co.in"))
            }
            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                progress.visibility = View.VISIBLE
            }
            override fun onPageFinished(view: WebView, url: String) {
                progress.visibility = View.GONE
                // Only hide explicitly promotional elements. CAPTCHA, form and other
                // functional images are deliberately left untouched.
                view.evaluateJavascript("""(function(){
                    var s=document.getElementById('tatkal-lite-style');
                    if(!s){s=document.createElement('style');s.id='tatkal-lite-style';
                    s.textContent='.advertisement,.promo-banner,.marketing-banner{display:none!important}';
                    document.head.appendChild(s);}
                })();""", null)
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
        singleLine = true
        setPadding(dp(7), 0, dp(7), 0)
    }

    private fun weightParams() = LinearLayout.LayoutParams(0, dp(43), 1f).apply {
        setMargins(dp(2), 0, dp(2), 0)
    }

    private fun topButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 10f
        isAllCaps = false
        setTextColor(Color.WHITE)
        setPadding(dp(3), 0, dp(3), 0)
        setOnClickListener { action() }
    }

    private fun panelButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 11f
        isAllCaps = false
        setPadding(dp(2), 0, dp(2), 0)
        setOnClickListener { action() }
    }

    // Captures only the visible page area, crops a likely CAPTCHA image and
    // enhances the crop locally with OpenCV. No OCR or challenge solving.
    private fun enhanceCaptchaImages() {
        if (!OpenCVLoader.initLocal()) {
            Toast.makeText(this, "OpenCV could not initialize. Please retry after rebuilding.", Toast.LENGTH_LONG).show()
            return
        }
        val locator = """
            (function() {
              var imgs = Array.from(document.images).filter(function(img) {
                var r = img.getBoundingClientRect();
                if (!r.width || !r.height || r.width < 45 || r.height < 16 || r.width > 900 || r.height > 350) return false;
                var meta = ((img.id||'')+' '+(img.className||'')+' '+(img.alt||'')+' '+(img.src||'')).toLowerCase();
                return /captcha|security.?code|verification.?image/.test(meta) ||
                  (r.width >= 90 && r.height >= 22 && r.height <= 130 && r.width / r.height >= 1.4);
              });
              if (!imgs.length) return '';
              imgs.sort(function(a,b) {
                var am = ((a.id||'')+' '+(a.className||'')+' '+(a.alt||'')+' '+(a.src||'')).toLowerCase();
                var bm = ((b.id||'')+' '+(b.className||'')+' '+(b.alt||'')+' '+(b.src||'')).toLowerCase();
                return (/captcha|security.?code|verification.?image/.test(bm) ? 1 : 0) -
                       (/captcha|security.?code|verification.?image/.test(am) ? 1 : 0);
              });
              var r = imgs[0].getBoundingClientRect(), d = window.devicePixelRatio || 1;
              return JSON.stringify({x:r.left*d,y:r.top*d,w:r.width*d,h:r.height*d});
            })();
        """.trimIndent()
        browser.evaluateJavascript(locator) { raw ->
            try {
                val jsonText = JSONTokener(raw ?: "\"\"").nextValue() as? String
                if (jsonText.isNullOrBlank()) {
                    Toast.makeText(this, "Could not identify a CAPTCHA image on this screen.", Toast.LENGTH_LONG).show()
                    return@evaluateJavascript
                }
                val rect = JSONObject(jsonText)
                val page = Bitmap.createBitmap(browser.width, browser.height, Bitmap.Config.ARGB_8888)
                browser.draw(Canvas(page))
                val left = rect.getDouble("x").toInt().coerceIn(0, page.width - 1)
                val top = rect.getDouble("y").toInt().coerceIn(0, page.height - 1)
                val right = (left + rect.getDouble("w").toInt()).coerceIn(left + 1, page.width)
                val bottom = (top + rect.getDouble("h").toInt()).coerceIn(top + 1, page.height)
                val crop = Bitmap.createBitmap(page, left, top, right - left, bottom - top)
                page.recycle()
                showOpenCvPreview(crop)
            } catch (e: Exception) {
                Toast.makeText(this, "Could not capture CAPTCHA image. Scroll it fully into view and retry.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showOpenCvPreview(original: Bitmap) {
        try {
            val source = Mat()
            val lab = Mat()
            val result = Mat()
            val channels = ArrayList<Mat>()
            Utils.bitmapToMat(original, source)
            Imgproc.cvtColor(source, lab, Imgproc.COLOR_RGBA2Lab)
            Core.split(lab, channels)
            val clahe: CLAHE = Imgproc.createCLAHE(2.0, org.opencv.core.Size(8.0, 8.0))
            clahe.apply(channels[0], channels[0])
            Core.merge(channels, lab)
            Imgproc.cvtColor(lab, result, Imgproc.COLOR_Lab2RGBA)
            val enhanced = Bitmap.createBitmap(result.cols(), result.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(result, enhanced)
            source.release()
            lab.release()
            result.release()
            channels.forEach { it.release() }
            clahe.collectGarbage()

            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(12), dp(16), dp(12))
            }
            layout.addView(TextView(this).apply {
                text = "OpenCV enhanced preview"
                textSize = 17f
                setTextColor(Color.BLACK)
            })
            layout.addView(TextView(this).apply {
                text = "Original image is shown below the enhanced version. Enter the CAPTCHA yourself on the IRCTC page."
                textSize = 12f
            })
            layout.addView(TextView(this).apply {
                text = "Enhanced"
                textSize = 13f
                setPadding(0, dp(8), 0, dp(4))
            })
            layout.addView(ImageView(this).apply {
                setImageBitmap(enhanced)
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
            }, LinearLayout.LayoutParams(-1, dp(110)))
            layout.addView(TextView(this).apply {
                text = "Original"
                textSize = 13f
                setPadding(0, dp(8), 0, dp(4))
            })
            layout.addView(ImageView(this).apply {
                setImageBitmap(original)
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
            }, LinearLayout.LayoutParams(-1, dp(110)))
            android.app.AlertDialog.Builder(this)
                .setView(layout)
                .setPositiveButton("Done") { dialog, _ ->
                    dialog.dismiss()
                    enhanced.recycle()
                    original.recycle()
                }
                .show()
        } catch (e: Exception) {
            original.recycle()
            Toast.makeText(this, "OpenCV image enhancement failed.", Toast.LENGTH_LONG).show()
        }
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
        val js = """
            (function(value) {
              var fields = Array.from(document.querySelectorAll('input'))
                .filter(function(el) {
                  if (el.disabled || el.readOnly || !el.offsetParent) return false;
                  var k = ((el.name||'')+' '+(el.id||'')+' '+(el.placeholder||'')+' '+(el.getAttribute('aria-label')||'')+' '+(el.autocomplete||'')).toLowerCase();
                  return /otp|one.?time|verification.?code/.test(k) || (el.maxLength >= 4 && el.maxLength <= 8 && /number|tel|text/.test(el.type));
                });
              if (!fields.length) return false;
              var el = fields[0], proto = Object.getPrototypeOf(el), desc = Object.getOwnPropertyDescriptor(proto,'value');
              if (desc && desc.set) desc.set.call(el,value); else el.value=value;
              el.dispatchEvent(new Event('input',{bubbles:true}));
              el.dispatchEvent(new Event('change',{bubbles:true}));
              el.focus();
              return true;
            })(${JSONObject.quote(otp)});
        """.trimIndent()
        browser.evaluateJavascript(js) { result ->
            val filled = result == "true"
            Toast.makeText(this, if (filled) "OTP filled. Review and submit it yourself." else "OTP received with your approval, but no OTP field was found. Enter it manually.", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveProfile() {
        prefs.edit()
            .putString("from", from.text.toString())
            .putString("to", to.text.toString())
            .putString("date", date.text.toString())
            .putString("train", train.text.toString())
            .apply()
        Toast.makeText(this, "Journey details saved on this device", Toast.LENGTH_SHORT).show()
    }

    private fun fillVisibleFields() {
        val data = JSONObject().apply {
            put("from", from.text.toString())
            put("to", to.text.toString())
            put("date", date.text.toString())
            put("train", train.text.toString())
        }
        val script = """
          (function(d) {
            function set(el, value) {
              if (!el || !value || el.disabled || el.readOnly) return false;
              var key=((el.name||'')+' '+(el.id||'')+' '+(el.placeholder||'')+' '+(el.getAttribute('aria-label')||'')).toLowerCase();
              if (/captcha|security|verification|otp|password|payment|card|cvv/.test(key)) return false;
              var proto=Object.getPrototypeOf(el);
              var desc=Object.getOwnPropertyDescriptor(proto,'value');
              if(desc && desc.set) desc.set.call(el,value); else el.value=value;
              el.dispatchEvent(new Event('input',{bubbles:true}));
              el.dispatchEvent(new Event('change',{bubbles:true}));
              return true;
            }
            var count=0;
            Array.from(document.querySelectorAll('input:not([type=hidden]),textarea')).forEach(function(el){
              var k=((el.name||'')+' '+(el.id||'')+' '+(el.placeholder||'')+' '+(el.getAttribute('aria-label')||'')).toLowerCase();
              if(/captcha|otp|password|payment|card|cvv|email|mobile|phone/.test(k)) return;
              if(/from|origin|source/.test(k)) {if(set(el,d.from)) count++;}
              else if(/to|destination|dest/.test(k)) {if(set(el,d.to)) count++;}
              else if(/journey.?date|date/.test(k)) {if(set(el,d.date)) count++;}
              else if(/train/.test(k)) {if(set(el,d.train)) count++;}
            });
            return count;
          })(${data});
        """.trimIndent()
        browser.evaluateJavascript(script) { result ->
            val count = result?.trim('"')?.toIntOrNull() ?: 0
            Toast.makeText(this, if (count > 0) "$count field(s) filled. Please review them." else "No matching fields found. Fill manually.", Toast.LENGTH_LONG).show()
        }
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(browser.windowToken, 0)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        browser.saveState(outState)
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

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val IRCTC_URL = "https://www.irctc.co.in/nget/train-search"
        private const val SMS_CONSENT_REQUEST = 4102
    }
}
