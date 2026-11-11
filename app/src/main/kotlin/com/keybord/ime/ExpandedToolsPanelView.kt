package com.keybord.ime

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.keyboard.engine.magicwrite.WritingStyle
import com.keybord.theme.BuiltInThemes
import com.keybord.theme.KeyboardEffectEngine
import com.keybord.theme.ThemeModel
import com.keybord.ui.UiKit

/**
 * Tactile Skeuomorphic Expanded Tools Drawer & Quick Rambler / AI Hub.
 * Removes the redundant AES-256 clip card and surfaces all tools with physical 3D tiles.
 */
class ExpandedToolsPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    var theme: ThemeModel = BuiltInThemes.SKEUO_DARK_GRAPHITE
        set(value) {
            field = value
            applyTheme()
        }

    var isHinglishEnabled: Boolean = true
        set(value) {
            field = value
            renderGrid()
        }

    var isOneHandedEnabled: Boolean = false
        set(value) {
            field = value
            renderGrid()
        }

    private var isShowingRaw = false
    private var clipCount = 0

    var onCollapseClicked: (() -> Unit)? = null
    var onOpenClipboardClicked: (() -> Unit)? = null
    var onOpenEmojiClicked: (() -> Unit)? = null
    var onOpenBase32Clicked: (() -> Unit)? = null
    var onOpenWritingToolsClicked: (() -> Unit)? = null
    var onOpenLayoutStudioClicked: (() -> Unit)? = null
    var onToggleOneHandedClicked: (() -> Unit)? = null
    var onOpenThemesClicked: (() -> Unit)? = null
    var onOpenSettingsClicked: (() -> Unit)? = null
    var onToggleHinglishClicked: (() -> Unit)? = null
    var onRamblerToggleRawClicked: (() -> Unit)? = null
    var onMagicFixItClicked: (() -> Unit)? = null
    var onMagicStyleClicked: ((WritingStyle) -> Unit)? = null
    var onMicClicked: (() -> Unit)? = null

    private lateinit var ramblerCard: LinearLayout
    private lateinit var cleanChip: TextView
    private lateinit var rawChip: TextView
    private lateinit var fixItBtn: TextView
    private lateinit var formalBtn: TextView
    private lateinit var conciseBtn: TextView
    private lateinit var gridContainer: LinearLayout
    private lateinit var backKeysBtn: TextView
    private lateinit var langPillTv: TextView
    private lateinit var micPillBtn: TextView

    init {
        orientation = VERTICAL
        setupViews()
        applyTheme()
    }

    private fun setupViews() {
        val dp = resources.displayMetrics.density
        setPadding((10 * dp).toInt(), (8 * dp).toInt(), (10 * dp).toInt(), (10 * dp).toInt())

        // 1. Rambler + Quick Polish Skeuomorphic Deck Card
        ramblerCard = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((10 * dp).toInt(), (8 * dp).toInt(), (10 * dp).toInt(), (8 * dp).toInt())
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (8 * dp).toInt()
            }
        }

        val micBadge = TextView(context).apply {
            text = "🎙"
            textSize = 15f
            gravity = Gravity.CENTER
            background = UiKit.skeuoIconWellDrawable(context, UiKit.ACCENT_AI_SOFT, 10f)
            val sz = (34 * dp).toInt()
            layoutParams = LayoutParams(sz, sz).apply { marginEnd = (8 * dp).toInt() }
            setOnClickListener { onMicClicked?.invoke() }
        }

        val ramblerCol = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        ramblerCol.addView(
            TextView(context).apply {
                text = "Voice & Quick Edit"
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(UiKit.TEXT_PRIMARY)
            }
        )
        val toggleRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding(0, (3 * dp).toInt(), 0, 0)
        }
        cleanChip = TextView(context).apply {
            text = "Cleaned"
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((7 * dp).toInt(), (2 * dp).toInt(), (7 * dp).toInt(), (2 * dp).toInt())
            setOnClickListener {
                if (isShowingRaw) {
                    isShowingRaw = false
                    syncRamblerChips()
                    onRamblerToggleRawClicked?.invoke()
                }
            }
        }
        rawChip = TextView(context).apply {
            text = "Raw"
            textSize = 10.5f
            setPadding((7 * dp).toInt(), (2 * dp).toInt(), (7 * dp).toInt(), (2 * dp).toInt())
            layoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = (4 * dp).toInt()
            }
            setOnClickListener {
                if (!isShowingRaw) {
                    isShowingRaw = true
                    syncRamblerChips()
                    onRamblerToggleRawClicked?.invoke()
                }
            }
        }
        toggleRow.addView(cleanChip)
        toggleRow.addView(rawChip)
        ramblerCol.addView(toggleRow)

        fun buildQuickAction(label: String, fn: () -> Unit): TextView {
            return TextView(context).apply {
                text = label
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setPadding((8 * dp).toInt(), (5 * dp).toInt(), (8 * dp).toInt(), (5 * dp).toInt())
                layoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginStart = (4 * dp).toInt()
                }
                setOnClickListener { fn() }
            }
        }
        fixItBtn = buildQuickAction("✨ Fix") { onMagicFixItClicked?.invoke() }
        formalBtn = buildQuickAction("Formal") { onMagicStyleClicked?.invoke(WritingStyle.FORMAL) }
        conciseBtn = buildQuickAction("Concise") { onMagicStyleClicked?.invoke(WritingStyle.CONCISE) }

        ramblerCard.addView(micBadge)
        ramblerCard.addView(ramblerCol)
        ramblerCard.addView(fixItBtn)
        ramblerCard.addView(formalBtn)
        ramblerCard.addView(conciseBtn)
        addView(ramblerCard)

        // 2. 4x2 Physical Skeuomorphic Tool Grid
        gridContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (8 * dp).toInt()
            }
        }
        addView(gridContainer)

        // 3. Bottom Navigation & Language Switch Bar
        val bottomRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        backKeysBtn = TextView(context).apply {
            text = "⌨  Keyboard"
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((12 * dp).toInt(), (7 * dp).toInt(), (12 * dp).toInt(), (7 * dp).toInt())
            layoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (38 * dp).toInt())
            setOnClickListener { onCollapseClicked?.invoke() }
        }
        langPillTv = TextView(context).apply {
            text = "🌐 English + Hinglish"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(0, (38 * dp).toInt(), 1f).apply {
                marginStart = (8 * dp).toInt()
                marginEnd = (8 * dp).toInt()
            }
            setOnClickListener { onToggleHinglishClicked?.invoke() }
        }
        micPillBtn = TextView(context).apply {
            text = "🎙 Dictate"
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding((12 * dp).toInt(), (7 * dp).toInt(), (12 * dp).toInt(), (7 * dp).toInt())
            layoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (38 * dp).toInt())
            setOnClickListener { onMicClicked?.invoke() }
        }
        bottomRow.addView(backKeysBtn)
        bottomRow.addView(langPillTv)
        bottomRow.addView(micPillBtn)
        addView(bottomRow)

        renderGrid()
        syncRamblerChips()
    }

    fun updateClipboardCount(totalCount: Int) {
        clipCount = totalCount
        renderGrid()
    }

    fun setRawState(raw: Boolean) {
        isShowingRaw = raw
        syncRamblerChips()
    }

    private fun syncRamblerChips() {
        cleanChip.setTextColor(if (!isShowingRaw) Color.WHITE else UiKit.TEXT_SECONDARY)
        cleanChip.background = UiKit.roundedDrawable(
            context,
            if (!isShowingRaw) UiKit.ACCENT_AI else UiKit.SURFACE_ELEVATED,
            8f
        )
        rawChip.setTextColor(if (isShowingRaw) Color.WHITE else UiKit.TEXT_SECONDARY)
        rawChip.background = UiKit.roundedDrawable(
            context,
            if (isShowingRaw) UiKit.ACCENT_AI else UiKit.SURFACE_ELEVATED,
            8f
        )
    }

    private fun renderGrid() {
        if (!::gridContainer.isInitialized) return
        gridContainer.removeAllViews()
        val dp = resources.displayMetrics.density
        val accent = theme.effectColors.firstOrNull() ?: theme.accentKeyColor ?: theme.trailColor
        val bg = theme.keyboardBackground.colorStart
        val surfaceFill = KeyboardEffectEngine.compositeOver(theme.keyFaceColor.primaryColor, bg)

        data class ToolTile(val icon: String, val label: String, val badge: String? = null, val active: Boolean = false, val onClick: () -> Unit)

        val tiles = listOf(
            ToolTile("📋", "Clipboard", if (clipCount > 0) clipCount.toString() else null) { onOpenClipboardClicked?.invoke() },
            ToolTile("😊", "Emoji") { onOpenEmojiClicked?.invoke() },
            ToolTile("📐", "Height") { onOpenLayoutStudioClicked?.invoke() },
            ToolTile("✋", "One-Hand", if (isOneHandedEnabled) "ON" else null, active = isOneHandedEnabled) { onToggleOneHandedClicked?.invoke() },
            ToolTile("🎨", "Themes") { onOpenThemesClicked?.invoke() },
            ToolTile("✨", "Writing") { onOpenWritingToolsClicked?.invoke() },
            ToolTile("🔐", "Base32") { onOpenBase32Clicked?.invoke() },
            ToolTile("⚙", "Settings") { onOpenSettingsClicked?.invoke() }
        )

        val rows = tiles.chunked(4)
        rows.forEachIndexed { rIdx, rowTiles ->
            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    if (rIdx < rows.lastIndex) bottomMargin = (6 * dp).toInt()
                }
            }
            rowTiles.forEachIndexed { colIdx, tile ->
                val cardFill = if (tile.active) {
                    UiKit.blendColors(surfaceFill, accent, 0.35f)
                } else {
                    surfaceFill
                }
                val card = LinearLayout(context).apply {
                    orientation = VERTICAL
                    gravity = Gravity.CENTER
                    background = UiKit.skeuoButtonDrawable(
                        context,
                        cardFill,
                        12f,
                        if (tile.active) accent else theme.keyBorderColor
                    )
                    setPadding((4 * dp).toInt(), (8 * dp).toInt(), (4 * dp).toInt(), (8 * dp).toInt())
                    layoutParams = LayoutParams(0, (62 * dp).toInt(), 1f).apply {
                        if (colIdx < 3) marginEnd = (6 * dp).toInt()
                    }
                    setOnClickListener { tile.onClick() }
                }

                val topLine = LinearLayout(context).apply {
                    orientation = HORIZONTAL
                    gravity = Gravity.CENTER
                }
                topLine.addView(
                    TextView(context).apply {
                        text = tile.icon
                        textSize = 17f
                    }
                )
                if (tile.badge != null) {
                    topLine.addView(
                        TextView(context).apply {
                            text = tile.badge
                            textSize = 9.5f
                            typeface = Typeface.DEFAULT_BOLD
                            setTextColor(Color.WHITE)
                            background = UiKit.roundedDrawable(context, accent, 8f)
                            setPadding((5 * dp).toInt(), (1 * dp).toInt(), (5 * dp).toInt(), (1 * dp).toInt())
                            layoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                                marginStart = (3 * dp).toInt()
                            }
                        }
                    )
                }

                val lbl = TextView(context).apply {
                    text = tile.label
                    textSize = 11f
                    setTextColor(theme.keyLabelColorPrimary)
                    setSingleLine(true)
                    setPadding(0, (3 * dp).toInt(), 0, 0)
                }

                card.addView(topLine)
                card.addView(lbl)
                row.addView(card)
            }
            gridContainer.addView(row)
        }

        if (::langPillTv.isInitialized) {
            langPillTv.text = if (isHinglishEnabled) "🌐 English + Hinglish" else "🌐 English Only"
        }
    }

    private fun applyTheme() {
        val bg = theme.keyboardBackground.colorStart
        setBackgroundColor(bg)
        val accent = theme.effectColors.firstOrNull() ?: theme.accentKeyColor ?: theme.trailColor
        val cardFill = KeyboardEffectEngine.compositeOver(theme.functionalKeyColor ?: theme.keyFaceColor.primaryColor, bg)
        val border = theme.keyBorderColor
        ramblerCard.background = UiKit.skeuoCardDrawable(context, cardFill, 14f, border, 2f)
        langPillTv.background = UiKit.skeuoButtonDrawable(context, cardFill, 11f, border)
        langPillTv.setTextColor(theme.keyLabelColorPrimary)
        backKeysBtn.background = UiKit.skeuoButtonDrawable(context, cardFill, 11f, border)
        backKeysBtn.setTextColor(theme.keyLabelColorPrimary)
        micPillBtn.background = UiKit.skeuoButtonDrawable(context, accent, 11f)

        val chipFill = KeyboardEffectEngine.compositeOver(theme.keyFaceColor.primaryColor, bg)
        for (btn in listOf(fixItBtn, formalBtn, conciseBtn)) {
            btn.background = UiKit.skeuoButtonDrawable(context, chipFill, 9f, border)
            btn.setTextColor(theme.keyLabelColorPrimary)
        }
        renderGrid()
    }
}
