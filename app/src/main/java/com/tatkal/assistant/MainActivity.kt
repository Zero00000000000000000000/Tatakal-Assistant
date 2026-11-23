package com.tatkal.assistant

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * IRCTC-inspired booking preparation screen.
 * Login, CAPTCHA, OTP, booking submission and payment happen on the
 * official IRCTC website and remain under the user's direct control.
 */
class MainActivity : Activity() {
    private val fields = linkedMapOf<String, EditText>()

    private val red = Color.rgb(190, 25, 45)
    private val navy = Color.rgb(28, 48, 75)
    private val ink = Color.rgb(40, 48, 58)
    private val muted = Color.rgb(105, 114, 125)
    private val page = Color.rgb(246, 247, 249)
    private val line = Color.rgb(218, 223, 229)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = red
        window.navigationBarColor = Color.rgb(245, 245, 245)
        window.decorView.systemUiVisibility = 0

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(page)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(16))
            setBackgroundColor(red)
        }
        header.addView(TextView(this).apply {
            text = "TATKAL ASSISTANT"
            textSize = 21f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        })
        header.addView(TextView(this).apply {
            text = "Plan your journey • Get ready to book"
            textSize = 13f
            setTextColor(Color.WHITE)
            alpha = 0.92f
            setPadding(0, dp(3), 0, 0)
        })
        root.addView(header)

        val quickLogin = Button(this).apply {
            text = "QUICK LOGIN  •  OPEN IRCTC"
            textSize = 14f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = rounded(red, 8)
            setOnClickListener { openIrctcInBrowser() }
        }
        val loginWrap = LinearLayout(this).apply {
            setPadding(dp(14), dp(10), dp(14), dp(2))
            addView(quickLogin, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)
            ))
        }
        root.addView(loginWrap)

        val scroll = ScrollView(this).apply {
            isFillViewport = false
            clipToPadding = false
            setPadding(dp(14), dp(8), dp(14), dp(14))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val intro = TextView(this).apply {
            text = "BOOK TICKET"
            textSize = 17f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(navy)
            setPadding(dp(2), dp(4), 0, dp(10))
        }
        content.addView(intro)

        val journeyCard = card()
        addSectionTitle(journeyCard, "Journey details")
        addField(journeyCard, "From station", "e.g. NJP")
        addField(journeyCard, "To station", "e.g. HWH")
        addField(journeyCard, "Journey date", "DD-MM-YYYY")
        addField(journeyCard, "Train number / name", "Enter train number or name")
        addField(journeyCard, "Travel class", "e.g. 3A, SL, CC")
        addField(journeyCard, "Quota", "Tatkal")
        content.addView(journeyCard)

        val passengerCard = card()
        addSectionTitle(passengerCard, "Passenger details")
        addField(passengerCard, "Passenger name", "Full name as per ID")
        addField(passengerCard, "Age", "Age in years")
        addField(passengerCard, "Gender", "Male / Female / Transgender")
        addField(passengerCard, "Berth preference", "Optional")
        content.addView(passengerCard)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(4))
        }
        val copy = Button(this).apply {
            text = "Copy details"
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = rounded(navy, 8)
            setOnClickListener { copyPreparedDetails() }
        }
        val clear = Button(this).apply {
            text = "Clear form"
            isAllCaps = false
            setTextColor(navy)
            background = rounded(Color.WHITE, 8, line)
            setOnClickListener {
                fields.values.forEach { it.text.clear() }
                Toast.makeText(this@MainActivity, "Form cleared", Toast.LENGTH_SHORT).show()
            }
        }
        actions.addView(copy, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
            marginEnd = dp(6)
        })
        actions.addView(clear, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
            marginStart = dp(6)
        })
        content.addView(actions)

        content.addView(TextView(this).apply {
            text = "Your details stay on this screen until you leave. Copying puts them on your clipboard. Login, CAPTCHA, OTP, passenger verification and payment are completed manually on IRCTC."
            textSize = 12f
            setTextColor(muted)
            setPadding(dp(4), dp(8), dp(4), dp(12))
        })

        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        setContentView(root)
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(8), dp(14), dp(12))
        background = rounded(Color.WHITE, 12, line)
        elevation = dp(1).toFloat()
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.bottomMargin = dp(12)
        layoutParams = params
    }

    private fun addSectionTitle(parent: LinearLayout, label: String) {
        parent.addView(TextView(this).apply {
            text = label
            textSize = 16f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(navy)
            setPadding(0, dp(5), 0, dp(8))
        })
    }

    private fun addField(parent: LinearLayout, label: String, hint: String) {
        parent.addView(TextView(this).apply {
            text = label
            textSize = 12f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(muted)
            setPadding(0, dp(5), 0, 0)
        })
        val input = EditText(this).apply {
            this.hint = hint
            textSize = 15f
            setTextColor(ink)
            setHintTextColor(Color.rgb(150, 157, 166))
            setSingleLine(true)
            contentDescription = label
            backgroundTintList = ColorStateList.valueOf(line)
            setPadding(dp(4), 0, dp(4), 0)
        }
        fields[label] = input
        parent.addView(input, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(46)
        ))
    }

    private fun openIrctcInBrowser() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(IRCTC_URL))
        try {
            startActivity(intent)
        } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(this, "No browser is installed. Please install or enable a browser.", Toast.LENGTH_LONG).show()
        }
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

    private fun rounded(color: Int, radius: Int, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val IRCTC_URL = "https://www.irctc.co.in/nget/"
    }
}
