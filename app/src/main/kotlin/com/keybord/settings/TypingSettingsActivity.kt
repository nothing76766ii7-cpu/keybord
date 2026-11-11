package com.keybord.settings

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.keybord.theme.KeyboardLayoutPrefs
import com.keybord.ui.UiKit

/**
 * Stitch Screen 07: Typing & Layout Settings
 * Grouped cards for Smart Typing Engine, Layout & Key Rows, and Keypress Feedback.
 */
class TypingSettingsActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences("keybord_typing_prefs", Context.MODE_PRIVATE) }
    private val layoutPrefs by lazy { KeyboardLayoutPrefs(this) }

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

        val verChip = UiKit.createBadgePill(this, "v1.2.1", UiKit.SURFACE_CARD, UiKit.TEXT_SECONDARY, UiKit.BORDER_SUBTLE)
        root.addView(
            UiKit.createHeaderBar(
                this,
                title = "Typing & Layout",
                subtitle = "Spatial glide engine, predictions & physical keybed geometry.",
                showBack = true,
                trailingView = verChip
            )
        )

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            isVerticalScrollBarEnabled = false
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, (14 * dp).toInt())
        }

        // Card 1: Smart Typing Engine
        val smartCard = UiKit.createCardContainer(this, paddingDp = 16f, radiusDp = 18f, bottomMarginDp = 14f)
        smartCard.addView(buildCardHeader("🧠", "#1E2D52", "Smart Typing Engine"))
        smartCard.addView(
            UiKit.createSwitchRow(
                this,
                "Glide Typing",
                "Spatial trajectory decoding & glowing trail",
                prefs.getBoolean("glide_typing", true)
            ) { prefs.edit().putBoolean("glide_typing", it).apply() }
        )
        smartCard.addView(UiKit.createHairlineDivider(this))
        smartCard.addView(
            UiKit.createSwitchRow(
                this,
                "Auto-Correction",
                "Dynamic Damerau-Levenshtein typo fixes (⌫ reverts)",
                prefs.getBoolean("auto_correct", true),
                badgeText = "Active"
            ) { prefs.edit().putBoolean("auto_correct", it).apply() }
        )
        smartCard.addView(UiKit.createHairlineDivider(this))
        smartCard.addView(
            UiKit.createSwitchRow(
                this,
                "Auto-Capitalization",
                "Capitalize first word of sentences & 'I'",
                prefs.getBoolean("auto_capitalise", true)
            ) { prefs.edit().putBoolean("auto_capitalise", it).apply() }
        )
        smartCard.addView(UiKit.createHairlineDivider(this))
        smartCard.addView(
            UiKit.createSwitchRow(
                this,
                "Hinglish Bilingual Lexicon",
                "English + Romanized Hindi glide, prefixes & next-word hints",
                prefs.getBoolean("hinglish_typing", true),
                badgeText = "EN + HI"
            ) { prefs.edit().putBoolean("hinglish_typing", it).apply() }
        )
        smartCard.addView(UiKit.createHairlineDivider(this))
        smartCard.addView(
            UiKit.createSwitchRow(
                this,
                "Double-Space Period Shortcut",
                "Inserts '. ' when Space is double-tapped",
                prefs.getBoolean("double_space_period", true)
            ) { prefs.edit().putBoolean("double_space_period", it).apply() }
        )
        content.addView(smartCard)

        // Card 2: Layout & Key Rows
        val layoutCard = UiKit.createCardContainer(this, paddingDp = 16f, radiusDp = 18f, bottomMarginDp = 14f)
        layoutCard.addView(buildCardHeader("⌨", "#261E4A", "Layout & Key Rows"))
        layoutCard.addView(
            UiKit.createSwitchRow(
                this,
                "Dedicated Number Row",
                "Permanent 1–0 row pinned above QWERTY letters",
                layoutPrefs.showNumberRow
            ) { layoutPrefs.showNumberRow = it }
        )
        layoutCard.addView(UiKit.createHairlineDivider(this))
        layoutCard.addView(
            UiKit.createSwitchRow(
                this,
                "Secondary Symbol Hints",
                "Upper-corner 360ms long-press glyph preview",
                layoutPrefs.showSecondarySymbols
            ) { layoutPrefs.showSecondarySymbols = it }
        )
        layoutCard.addView(UiKit.createHairlineDivider(this))
        layoutCard.addView(
            buildLinkRow("Height, Corners & Spacing Studio", "Key ${layoutPrefs.keyHeightDp.toInt()}dp • Radius ${layoutPrefs.cornerRadiusDp.toInt()}dp") {
                startActivity(Intent(this, KeyboardLayoutCustomizeActivity::class.java))
            }
        )
        layoutCard.addView(UiKit.createHairlineDivider(this))
        layoutCard.addView(
            buildLinkRow("Personal Dictionary & Shortcuts", "Custom words, expansions & system sync") {
                startActivity(Intent(this, PersonalDictionaryActivity::class.java))
            }
        )
        content.addView(layoutCard)

        // Card 3: Keypress Feedback (Stitch 07 bottom card)
        val feedbackCard = UiKit.createCardContainer(this, paddingDp = 16f, radiusDp = 18f, bottomMarginDp = 14f)
        feedbackCard.addView(buildCardHeader("📳", "#172E36", "Keypress Feedback"))
        feedbackCard.addView(
            UiKit.createSwitchRow(
                this,
                "Haptic Feedback on Keypress",
                "Tactile motor impulse on each key actuation",
                layoutPrefs.vibrationDurationMs > 0L
            ) { enabled ->
                layoutPrefs.vibrationDurationMs = if (enabled) 18L else 0L
            }
        )
        feedbackCard.addView(UiKit.createHairlineDivider(this))
        feedbackCard.addView(
            UiKit.createSwitchRow(
                this,
                "Key-Pop Magnification Preview",
                "Elevate key bubble badge above active fingertip",
                layoutPrefs.popPreviewEnabled
            ) { layoutPrefs.popPreviewEnabled = it }
        )
        content.addView(feedbackCard)

        content.addView(
            UiKit.createSliderCard(
                this,
                "Vibration Duration",
                " ms",
                layoutPrefs.vibrationDurationMs.toInt(),
                0,
                40
            ) { v ->
                layoutPrefs.vibrationDurationMs = v.toLong()
            }
        )

        content.addView(
            UiKit.createPrivacyFooter(
                this,
                "Zero-Telemetry • 100% Local Inference",
                "Keystrokes and spatial trajectory data never leave this device."
            )
        )

        scroll.addView(content)
        root.addView(scroll)
        setContentView(root)
    }

    private fun buildCardHeader(icon: String, iconBg: String, title: String): LinearLayout {
        val dp = resources.displayMetrics.density
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (10 * dp).toInt())
            addView(
                TextView(context).apply {
                    text = icon
                    textSize = 16f
                    gravity = Gravity.CENTER
                    background = UiKit.roundedDrawable(context, Color.parseColor(iconBg), 10f)
                    val sz = (34 * dp).toInt()
                    layoutParams = LinearLayout.LayoutParams(sz, sz).apply { marginEnd = (10 * dp).toInt() }
                }
            )
            addView(
                TextView(context).apply {
                    text = title
                    textSize = 16f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(UiKit.TEXT_PRIMARY)
                }
            )
        }
    }

    private fun buildLinkRow(title: String, subtitle: String, onClick: () -> Unit): LinearLayout {
        val dp = resources.displayMetrics.density
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (11 * dp).toInt(), 0, (11 * dp).toInt())
            setOnClickListener { onClick() }
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    addView(
                        TextView(context).apply {
                            text = title
                            textSize = 15f
                            typeface = Typeface.DEFAULT_BOLD
                            setTextColor(UiKit.TEXT_PRIMARY)
                        }
                    )
                    addView(
                        TextView(context).apply {
                            text = subtitle
                            textSize = 12f
                            setTextColor(UiKit.ACCENT_PRIMARY)
                            setPadding(0, (2 * dp).toInt(), 0, 0)
                        }
                    )
                }
            )
            addView(
                TextView(context).apply {
                    text = "›"
                    textSize = 20f
                    setTextColor(UiKit.TEXT_MUTED)
                    setPadding((8 * dp).toInt(), 0, (4 * dp).toInt(), 0)
                }
            )
        }
    }
}
