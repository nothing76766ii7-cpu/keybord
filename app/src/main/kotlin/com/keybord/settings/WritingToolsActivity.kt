package com.keybord.settings

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.keyboard.engine.magicwrite.WritingStyle
import com.keybord.ui.UiKit

/**
 * Stitch Screen 06: Writing Tools (Rambler Voice Cleanup & Magic Writing)
 * backed by persisted SharedPreferences ("keybord_writing_prefs").
 */
class WritingToolsActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    private lateinit var rawPreviewTv: TextView
    private lateinit var processedPreviewTv: TextView
    private lateinit var previewStatusPill: TextView
    private var selectedTone: WritingStyle = WritingStyle.FORMAL
    private val toneButtons = mutableListOf<Pair<WritingStyle, TextView>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val savedToneName = prefs.getString(KEY_DEFAULT_TONE, WritingStyle.FORMAL.name) ?: WritingStyle.FORMAL.name
        selectedTone = WritingStyle.values().firstOrNull { it.name == savedToneName } ?: WritingStyle.FORMAL

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

        val headerBadge = UiKit.createBadgePill(
            this,
            "✨",
            UiKit.SURFACE_CARD,
            UiKit.ACCENT_AI,
            UiKit.BORDER_SUBTLE
        ).apply {
            textSize = 16f
            val sz = (38 * dp).toInt()
            layoutParams = LinearLayout.LayoutParams(sz, sz)
        }
        root.addView(
            UiKit.createHeaderBar(
                this,
                title = "Writing Tools",
                subtitle = "On-device writing intelligence — zero cloud uploads.",
                showBack = true,
                trailingView = headerBadge
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

        // Card 1: Rambler Voice Cleanup
        val ramblerCard = UiKit.createCardContainer(this, paddingDp = 16f, radiusDp = 18f, bottomMarginDp = 14f)
        ramblerCard.addView(buildCardTitleRow("🎙", "Rambler Voice Cleanup", "Real-time local acoustic de-cluttering", "Local NPU", UiKit.ACCENT_AI))

        ramblerCard.addView(
            UiKit.createSwitchRow(
                this,
                "Rambler Continuous Dictation",
                "Unlimited stream without timeout disconnects",
                prefs.getBoolean(KEY_RAMBLER_CONTINUOUS, true)
            ) { prefs.edit().putBoolean(KEY_RAMBLER_CONTINUOUS, it).apply() }
        )
        ramblerCard.addView(UiKit.createHairlineDivider(this))
        ramblerCard.addView(
            UiKit.createSwitchRow(
                this,
                "Remove Filler Words",
                "Omits \"um\", \"uh\", \"like\", \"you know\"",
                prefs.getBoolean(KEY_RAMBLER_FILLERS, true)
            ) {
                prefs.edit().putBoolean(KEY_RAMBLER_FILLERS, it).apply()
                refreshTransformationPreview()
            }
        )
        ramblerCard.addView(UiKit.createHairlineDivider(this))
        ramblerCard.addView(
            UiKit.createSwitchRow(
                this,
                "Smart Punctuation & True-Casing",
                "Infers pauses, question intonations, and acronyms",
                prefs.getBoolean(KEY_RAMBLER_SMART_PUNCT, true)
            ) {
                prefs.edit().putBoolean(KEY_RAMBLER_SMART_PUNCT, it).apply()
                refreshTransformationPreview()
            }
        )
        ramblerCard.addView(UiKit.createHairlineDivider(this))
        ramblerCard.addView(
            UiKit.createSwitchRow(
                this,
                "Dual-State Undo",
                "Tap microphone chip to toggle Cleaned vs Raw",
                prefs.getBoolean(KEY_RAMBLER_DUAL_UNDO, true)
            ) { prefs.edit().putBoolean(KEY_RAMBLER_DUAL_UNDO, it).apply() }
        )

        // Live Transformation Preview Inset
        ramblerCard.addView(buildLiveTransformationBox())
        content.addView(ramblerCard)

        // Card 2: Magic Writing
        val magicCard = UiKit.createCardContainer(this, paddingDp = 16f, radiusDp = 18f, bottomMarginDp = 14f)
        magicCard.addView(buildCardTitleRow("✨", "Magic Writing", "Contextual inline text transformations", null, UiKit.ACCENT_AI))

        magicCard.addView(
            UiKit.createSwitchRow(
                this,
                "Proofread (\"Fix it\") Suggestions",
                "Displays corrections directly in keyboard suggestion strip",
                prefs.getBoolean(KEY_MAGIC_PROOFREAD, true)
            ) { prefs.edit().putBoolean(KEY_MAGIC_PROOFREAD, it).apply() }
        )
        magicCard.addView(UiKit.createHairlineDivider(this))
        magicCard.addView(
            UiKit.createSwitchRow(
                this,
                "Inline Tone Rewriting Bar",
                "One-tap tone rephrasing pill above selected text",
                prefs.getBoolean(KEY_MAGIC_TONE_BAR, true)
            ) { prefs.edit().putBoolean(KEY_MAGIC_TONE_BAR, it).apply() }
        )
        magicCard.addView(UiKit.createHairlineDivider(this))

        val toneHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (10 * dp).toInt(), 0, (10 * dp).toInt())
        }
        toneHeader.addView(
            TextView(this).apply {
                text = "Default Tone Style"
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(UiKit.TEXT_PRIMARY)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
        )
        toneHeader.addView(
            TextView(this).apply {
                text = "Customizable per-app"
                textSize = 11.5f
                setTextColor(UiKit.ACCENT_PRIMARY)
            }
        )
        magicCard.addView(toneHeader)
        magicCard.addView(buildToneGrid())
        content.addView(magicCard)

        // Bottom 100% On-Device Privacy Banner
        content.addView(
            UiKit.createPrivacyFooter(
                this,
                "100% On-Device Rust Engine",
                "Zero Internet Permission requested. No keystrokes, audio, or text clips ever leave local device sandbox memory."
            )
        )

        scroll.addView(content)
        root.addView(scroll)
        setContentView(root)
        refreshTransformationPreview()
    }

    private fun buildCardTitleRow(
        icon: String,
        title: String,
        subtitle: String,
        badge: String?,
        tint: Int
    ): LinearLayout {
        val dp = resources.displayMetrics.density
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (12 * dp).toInt())
        }
        row.addView(
            TextView(this).apply {
                text = icon
                textSize = 18f
                gravity = Gravity.CENTER
                background = UiKit.roundedDrawable(this@WritingToolsActivity, UiKit.ACCENT_AI_SOFT, 12f)
                val sz = (38 * dp).toInt()
                layoutParams = LinearLayout.LayoutParams(sz, sz).apply { marginEnd = (12 * dp).toInt() }
            }
        )
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        col.addView(
            TextView(this).apply {
                text = title
                textSize = 16.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(UiKit.TEXT_PRIMARY)
            }
        )
        col.addView(
            TextView(this).apply {
                text = subtitle
                textSize = 12f
                setTextColor(UiKit.TEXT_SECONDARY)
            }
        )
        row.addView(col)
        if (!badge.isNullOrBlank()) {
            row.addView(
                UiKit.createBadgePill(
                    this,
                    badge,
                    UiKit.SURFACE_INPUT,
                    tint,
                    UiKit.BORDER_SUBTLE
                )
            )
        }
        return row
    }

    private fun buildLiveTransformationBox(): LinearLayout {
        val dp = resources.displayMetrics.density
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = UiKit.roundedDrawable(this@WritingToolsActivity, UiKit.SURFACE_INPUT, 14f, UiKit.BORDER_SUBTLE, 1f)
            setPadding((12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (10 * dp).toInt()
            }
        }

        val topHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (8 * dp).toInt())
        }
        topHeader.addView(
            TextView(this).apply {
                text = "LIVE TRANSFORMATION PREVIEW"
                textSize = 10.5f
                letterSpacing = 0.08f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(UiKit.TEXT_SECONDARY)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
        )
        previewStatusPill = UiKit.createBadgePill(this, "● Cleaned", UiKit.SUCCESS_BG, UiKit.SUCCESS_FG, UiKit.SUCCESS_BORDER)
        topHeader.addView(previewStatusPill)
        box.addView(topHeader)

        val cols = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val rawCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = UiKit.roundedDrawable(this@WritingToolsActivity, UiKit.SURFACE_CARD, 10f, UiKit.BORDER_SUBTLE, 1f)
            setPadding((10 * dp).toInt(), (8 * dp).toInt(), (10 * dp).toInt(), (8 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        rawCard.addView(
            TextView(this).apply {
                text = "RAW INPUT"
                textSize = 9.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(UiKit.TEXT_MUTED)
            }
        )
        rawPreviewTv = TextView(this).apply {
            text = "\"um yeah lets meet at five\""
            textSize = 11.5f
            typeface = Typeface.MONOSPACE
            setTextColor(UiKit.TEXT_SECONDARY)
            paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            setPadding(0, (4 * dp).toInt(), 0, 0)
        }
        rawCard.addView(rawPreviewTv)

        val arrowTv = TextView(this).apply {
            text = "→"
            textSize = 16f
            setTextColor(UiKit.ACCENT_PRIMARY)
            setPadding((8 * dp).toInt(), 0, (8 * dp).toInt(), 0)
        }

        val procCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = UiKit.roundedDrawable(this@WritingToolsActivity, UiKit.SURFACE_CARD, 10f, UiKit.ACCENT_PRIMARY, 1f)
            setPadding((10 * dp).toInt(), (8 * dp).toInt(), (10 * dp).toInt(), (8 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        procCard.addView(
            TextView(this).apply {
                text = "PROCESSED"
                textSize = 9.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(UiKit.ACCENT_PRIMARY)
            }
        )
        processedPreviewTv = TextView(this).apply {
            text = "\"Yeah, let's meet at 5.\""
            textSize = 12f
            setTextColor(UiKit.TEXT_PRIMARY)
            setPadding(0, (4 * dp).toInt(), 0, 0)
        }
        procCard.addView(processedPreviewTv)

        cols.addView(rawCard)
        cols.addView(arrowTv)
        cols.addView(procCard)
        box.addView(cols)
        return box
    }

    private fun refreshTransformationPreview() {
        val removeFillers = prefs.getBoolean(KEY_RAMBLER_FILLERS, true)
        val smartPunct = prefs.getBoolean(KEY_RAMBLER_SMART_PUNCT, true)

        processedPreviewTv.text = when {
            removeFillers && smartPunct -> "\"Yeah, let's meet at 5.\""
            removeFillers && !smartPunct -> "\"yeah lets meet at 5\""
            !removeFillers && smartPunct -> "\"Um, yeah, let's meet at 5.\""
            else -> "\"um yeah lets meet at five\""
        }
        previewStatusPill.text = if (removeFillers || smartPunct) "● Cleaned" else "● Raw Pass-Through"
    }

    private fun buildToneGrid(): LinearLayout {
        val dp = resources.displayMetrics.density
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val styles = WritingStyle.values().toList()
        toneButtons.clear()

        fun refreshToneStyles() {
            for ((style, btn) in toneButtons) {
                val active = (style == selectedTone)
                btn.text = if (active) "✓  ${style.displayName}" else style.displayName
                btn.setTextColor(if (active) Color.WHITE else UiKit.TEXT_SECONDARY)
                btn.typeface = if (active) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                btn.background = UiKit.roundedDrawable(
                    this,
                    if (active) UiKit.ACCENT_AI else UiKit.SURFACE_INPUT,
                    12f,
                    if (active) null else UiKit.BORDER_SUBTLE,
                    1f
                )
            }
        }

        val rows = styles.chunked(3)
        rows.forEachIndexed { rIdx, rowItems ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    if (rIdx < rows.lastIndex) bottomMargin = (8 * dp).toInt()
                }
            }
            rowItems.forEachIndexed { cIdx, style ->
                val btn = TextView(this).apply {
                    textSize = 12.5f
                    gravity = Gravity.CENTER
                    setPadding((6 * dp).toInt(), (9 * dp).toInt(), (6 * dp).toInt(), (9 * dp).toInt())
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        if (cIdx < rowItems.lastIndex) marginEnd = (8 * dp).toInt()
                    }
                    setOnClickListener {
                        selectedTone = style
                        prefs.edit().putString(KEY_DEFAULT_TONE, style.name).apply()
                        refreshToneStyles()
                    }
                }
                toneButtons.add(style to btn)
                row.addView(btn)
            }
            container.addView(row)
        }
        refreshToneStyles()
        return container
    }

    companion object {
        const val PREFS_NAME = "keybord_writing_prefs"
        const val KEY_RAMBLER_CONTINUOUS = "rambler_continuous"
        const val KEY_RAMBLER_FILLERS = "rambler_remove_fillers"
        const val KEY_RAMBLER_SMART_PUNCT = "rambler_smart_punct"
        const val KEY_RAMBLER_DUAL_UNDO = "rambler_dual_undo"
        const val KEY_MAGIC_PROOFREAD = "magic_proofread_enabled"
        const val KEY_MAGIC_TONE_BAR = "magic_tone_bar_enabled"
        const val KEY_DEFAULT_TONE = "default_tone_style"
    }
}
