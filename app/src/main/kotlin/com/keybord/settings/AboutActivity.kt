package com.keybord.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.keybord.diagnostics.KeybordCrashLogger
import com.keybord.ui.UiKit

/**
 * About & Diagnostics screen presenting Keybord v1.2.1 architecture,
 * honest local-first privacy guarantees, and crash log viewer.
 */
class AboutActivity : AppCompatActivity() {

    private lateinit var logOutputTv: TextView

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
                subtitle = "Privacy-first tactile keyboard engine",
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
            background = UiKit.skeuoCardDrawable(this@AboutActivity, UiKit.SURFACE_CARD, 20f, UiKit.BORDER_SUBTLE, 3f)
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
                background = UiKit.skeuoIconWellDrawable(this@AboutActivity, UiKit.ACCENT_SOFT, 18f)
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
            UiKit.createBadgePill(
                this,
                "Version 1.2.1 • Skeuomorphic Edition",
                UiKit.SURFACE_INPUT,
                UiKit.ACCENT_PRIMARY,
                UiKit.ACCENT_PRIMARY
            ).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = (4 * dp).toInt()
                }
            }
        )
        brandCard.addView(
            TextView(this).apply {
                text = "Physical skeuomorphic feel across Dark Graphite, Black Glass, Soft White Ceramic, Brushed Metal, Leather & Classic Mechanical — with Hinglish fluency and local-first privacy."
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
                "🔒 Zero Telemetry & Local Core Vault",
                "Normal typing, clipboard storage, Base32 tools, and Hinglish/English vocabulary learning stay offline inside local device storage. android.permission.INTERNET is used exclusively when you trigger BYOK Cloud AI Writer with your own configured endpoint and API key."
            )
        )
        content.addView(
            buildFeatureCard(
                "⚡ What's Inside v1.2.1",
                "• Tactile Skeuomorphic Design System + 6 physical Material themes\n" +
                    "• Hardened multiline InputMethodService engine with async SQLite & prediction pipelines\n" +
                    "• Fixed Spacebar commit behavior & independent English / EN+HI switcher\n" +
                    "• Full on-keyboard AI Writer Composer (Gemini & OpenAI-compatible BYOK + offline fallback)\n" +
                    "• On-keyboard Base32 Studio: keys, Text/Hex Encode & Decode, live 30s TOTP 2FA\n" +
                    "• Expanded Tools Drawer, Voice Rambler dictation, Emoji Picker & AES clipboard vault"
            )
        )

        content.addView(UiKit.createSectionLabel(this, "Diagnostics & Crash Logs"))
        content.addView(buildDiagnosticsCard())

        scroll.addView(content)
        root.addView(scroll)
        setContentView(root)
    }

    private fun buildDiagnosticsCard(): LinearLayout {
        val dp = resources.displayMetrics.density
        val card = UiKit.createCardContainer(this, paddingDp = 16f, radiusDp = 18f, bottomMarginDp = 10f)
        card.addView(
            TextView(this).apply {
                text = "Local Crash & Exception Reporter"
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(UiKit.TEXT_PRIMARY)
            }
        )
        card.addView(
            TextView(this).apply {
                text = "Captures uncaught IME exceptions and non-fatal diagnostics locally (never stores keystrokes, passwords, or API keys)."
                textSize = 12f
                setTextColor(UiKit.TEXT_SECONDARY)
                setPadding(0, (3 * dp).toInt(), 0, (10 * dp).toInt())
            }
        )

        val logWell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = UiKit.skeuoWellDrawable(this@AboutActivity, UiKit.SURFACE_INPUT, 12f, UiKit.BORDER_SUBTLE)
            setPadding((10 * dp).toInt(), (10 * dp).toInt(), (10 * dp).toInt(), (10 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (10 * dp).toInt()
            }
        }
        logOutputTv = TextView(this).apply {
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextColor(UiKit.TEXT_SECONDARY)
            setTextIsSelectable(true)
        }
        refreshCrashLogText()
        logWell.addView(logOutputTv)
        card.addView(logWell)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val refreshBtn = UiKit.createPillButton(this, "Refresh", filled = false) {
            refreshCrashLogText()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (6 * dp).toInt()
            }
        }
        val copyBtn = UiKit.createPillButton(this, "Copy Log", filled = true) {
            val logs = KeybordCrashLogger.readLogs(this)
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.setPrimaryClip(ClipData.newPlainText("Keybord Crash Log", logs.ifBlank { "No crash entries recorded." }))
            Toast.makeText(this, "Diagnostics copied to clipboard", Toast.LENGTH_SHORT).show()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (6 * dp).toInt()
            }
        }
        val clearBtn = UiKit.createPillButton(this, "Clear", filled = false, danger = true) {
            KeybordCrashLogger.clearLogs(this)
            refreshCrashLogText()
            Toast.makeText(this, "Crash log cleared", Toast.LENGTH_SHORT).show()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        actions.addView(refreshBtn)
        actions.addView(copyBtn)
        actions.addView(clearBtn)
        card.addView(actions)
        return card
    }

    private fun refreshCrashLogText() {
        val raw = KeybordCrashLogger.readLogs(this)
        logOutputTv.text = if (raw.isBlank()) {
            "✓ No uncaught crashes recorded on this device."
        } else {
            raw.takeLast(3500)
        }
    }

    private fun buildFeatureCard(title: String, body: String): LinearLayout {
        val dp = resources.displayMetrics.density
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = UiKit.skeuoCardDrawable(this@AboutActivity, UiKit.SURFACE_CARD, 16f, UiKit.BORDER_SUBTLE, 2f)
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
