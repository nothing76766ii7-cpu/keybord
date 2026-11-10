package com.keybord.settings

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.keybord.ui.UiKit

/**
 * About screen presenting Keybord v1.1.7 architecture, features, and privacy trust signals.
 */
class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dp = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(UiKit.BG_CANVAS)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        UiKit.applyEdgeToEdgePadding(root, leftDp = 16f, topDp = 14f, rightDp = 16f, bottomDp = 16f)

        root.addView(
            UiKit.createHeaderBar(
                this,
                title = "About Keybord",
                subtitle = "Privacy-first smart keyboard engine",
                showBack = true
            )
        )

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            isVerticalScrollBarEnabled = false
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (4 * dp).toInt(), 0, (16 * dp).toInt())
        }

        // Brand Identity Card
        val brandCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = UiKit.roundedDrawable(this@AboutActivity, UiKit.SURFACE_CARD, 20f, UiKit.BORDER_SUBTLE, 1f)
            setPadding((20 * dp).toInt(), (22 * dp).toInt(), (20 * dp).toInt(), (20 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (14 * dp).toInt()
            }
        }
        brandCard.addView(
            TextView(this).apply {
                text = "⌨"
                textSize = 40f
                gravity = Gravity.CENTER
                background = UiKit.roundedDrawable(this@AboutActivity, UiKit.ACCENT_SOFT, 18f, UiKit.ACCENT_PRIMARY, 1f)
                val sz = (72 * dp).toInt()
                layoutParams = LinearLayout.LayoutParams(sz, sz)
            }
        )
        brandCard.addView(
            TextView(this).apply {
                text = "Keybord"
                textSize = 26f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(UiKit.TEXT_PRIMARY)
                gravity = Gravity.CENTER
                setPadding(0, (10 * dp).toInt(), 0, (2 * dp).toInt())
            }
        )
        brandCard.addView(
            TextView(this).apply {
                text = "Version 1.1.8"
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(UiKit.ACCENT_PRIMARY)
                background = UiKit.roundedDrawable(this@AboutActivity, UiKit.ACCENT_SOFT, 10f)
                setPadding((12 * dp).toInt(), (4 * dp).toInt(), (12 * dp).toInt(), (4 * dp).toInt())
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = (4 * dp).toInt()
                }
            }
        )
        brandCard.addView(
            TextView(this).apply {
                text = "The keyboard that gets out of your way — Obsidian Tactile & Clean Porcelain design, Hinglish fluency, deep layout customisation, and total on-device privacy."
                textSize = 13.5f
                setTextColor(UiKit.TEXT_SECONDARY)
                gravity = Gravity.CENTER
                setPadding(0, (12 * dp).toInt(), 0, 0)
            }
        )
        content.addView(brandCard)

        content.addView(UiKit.createSectionLabel(this, "Privacy & Architecture"))
        content.addView(
            buildFeatureCard(
                "🔒 Zero Internet Permission Guarantee",
                "Keybord does not declare android.permission.INTERNET in its manifest. Your typing history, clipboard items, and learned Hinglish/English vocabulary can never leave your device."
            )
        )
        content.addView(
            buildFeatureCard(
                "⚡ What's Inside v1.1.8",
                "• Obsidian Tactile & Clean Porcelain UI across IME, Tools Drawer & Settings\n" +
                    "• Bilingual English + Hinglish glide typing, suggestions & auto-correct\n" +
                    "• Expanded Tools & Rambler quick panel + dedicated Writing Tools studio\n" +
                    "• Live Height & Buttons Customisation Studio + Accent Swatches\n" +
                    "• Smooth Gboard-grade rollover, 360ms secondary long-press & Space cursor scrub\n" +
                    "• Built-in Emoji Picker with Recents & Hinglish/English search\n" +
                    "• Unlimited SQLite AES clipboard vault & Rust native core"
            )
        )

        scroll.addView(content)
        root.addView(scroll)
        setContentView(root)
    }

    private fun buildFeatureCard(title: String, body: String): LinearLayout {
        val dp = resources.displayMetrics.density
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = UiKit.roundedDrawable(this@AboutActivity, UiKit.SURFACE_CARD, 16f, UiKit.BORDER_SUBTLE, 1f)
            setPadding((16 * dp).toInt(), (14 * dp).toInt(), (16 * dp).toInt(), (14 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (10 * dp).toInt()
            }
            addView(
                TextView(context).apply {
                    text = title
                    textSize = 15f
                    setTextColor(UiKit.ACCENT_PRIMARY)
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(0, 0, 0, (6 * dp).toInt())
                }
            )
            addView(
                TextView(context).apply {
                    text = body
                    textSize = 13.5f
                    setTextColor(UiKit.TEXT_SECONDARY)
                    setLineSpacing(4f * dp, 1f)
                }
            )
        }
    }
}
