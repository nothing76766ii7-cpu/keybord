package com.keybord.settings

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * About screen presenting Keybord version, architecture, and privacy trust signals.
 */
class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dp = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#12121A"))
            setPadding((20 * dp).toInt(), (24 * dp).toInt(), (20 * dp).toInt(), (20 * dp).toInt())
        }

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Header
        val logo = TextView(this).apply {
            text = "⌨ Keybord"
            textSize = 28f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, (4 * dp).toInt())
        }

        val version = TextView(this).apply {
            text = "Version 1.0.1 (November 2026)"
            textSize = 14f
            setTextColor(Color.parseColor("#4285F4"))
            setPadding(0, 0, 0, (20 * dp).toInt())
        }

        val tagLine = TextView(this).apply {
            text = "The Keyboard That Gets Out of Your Way"
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, (12 * dp).toInt())
        }

        val intro = TextView(this).apply {
            text = "Keybord is an Android keyboard built for people who are tired of being managed by their own tools.\n\nDeep visual customisation with no design ceiling, a clipboard that behaves like you own it, and glide typing that learns as you type."
            textSize = 14f
            setTextColor(Color.parseColor("#CCCCCC"))
            setLineSpacing(4f * dp, 1f)
            setPadding(0, 0, 0, (20 * dp).toInt())
        }

        content.addView(logo)
        content.addView(version)
        content.addView(tagLine)
        content.addView(intro)

        addInfoCard(
            content,
            title = "🔒 Zero Internet Permissions",
            description = "Keybord does not request the INTERNET permission in AndroidManifest.xml. It has zero capability to send keystrokes, clipboard items, or data across any network. Everything stays strictly on your device."
        )

        addInfoCard(
            content,
            title = "📋 Unlimited Local Clipboard",
            description = "Stored locally in SQLite with AES-256-GCM encryption at rest. Pinned items are strictly exempt from retention rules. No hourly erasures."
        )

        addInfoCard(
            content,
            title = "⚡ Clean-room Delight5 Glide Typing",
            description = "Built using bivariate Gaussian spatial proximity, Dynamic Time Warping (DTW) trajectory alignment, velocity-dependent corner cutting compensation, and beam search decoding."
        )

        addInfoCard(
            content,
            title = "🧠 Adaptive Vocabulary Learning",
            description = "SQLite-backed dynamic language model with n-gram Katz backoff, 3x suggestion elevation, 4x manual correction reinforcement, and 30-day exponential half-life decay."
        )

        scroll.addView(content)
        root.addView(scroll)
        setContentView(root)
    }

    private fun addInfoCard(parent: LinearLayout, title: String, description: String) {
        val dp = resources.displayMetrics.density
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1B1B26"))
            setPadding((14 * dp).toInt(), (12 * dp).toInt(), (14 * dp).toInt(), (12 * dp).toInt())
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (12 * dp).toInt()
            }
            layoutParams = lp
        }

        val tvTitle = TextView(this).apply {
            text = title
            textSize = 15f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, (4 * dp).toInt())
        }

        val tvDesc = TextView(this).apply {
            text = description
            textSize = 13f
            setTextColor(Color.parseColor("#A0A4B8"))
            setLineSpacing(3f * dp, 1f)
        }

        card.addView(tvTitle)
        card.addView(tvDesc)
        parent.addView(card)
    }
}
