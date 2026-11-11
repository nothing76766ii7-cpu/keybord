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
import com.keybord.clipboard.ClipboardItem
import com.keybord.theme.BuiltInThemes
import com.keybord.theme.ThemeModel
import com.keybord.ui.UiKit

/**
 * Stitch Screen 03: Expanded Tools Drawer & Quick Rambler / Clipboard Card.
 * Replaces the keyboard layer cleanly on demand without leaving the active app.
 */
class ExpandedToolsPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    var theme: ThemeModel = BuiltInThemes.MATERIAL_MIDNIGHT
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
    private var latestClip: ClipboardItem? = null

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
    var onPasteClipClicked: ((String) -> Unit)? = null
    var onMicClicked: (() -> Unit)? = null

    private lateinit var ramblerCard: LinearLayout
    private lateinit var cleanChip: TextView
    private lateinit var rawChip: TextView
    private lateinit var fixItBtn: TextView
    private lateinit var formalBtn: TextView
    private lateinit var conciseBtn: TextView
    private lateinit var gridContainer: LinearLayout
    private lateinit var clipCard: LinearLayout
    private lateinit var clipTitleTv: TextView
    private lateinit var clipPreviewTv: TextView
    private lateinit var clipPasteBtn: TextView
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

        // 1. Rambler + Magic Quick Card
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
            background = UiKit.roundedDrawable(context, UiKit.ACCENT_AI_SOFT, 10f, UiKit.ACCENT_AI, 1f)
            val sz = (32 * dp).toInt()
            layoutParams = LayoutParams(sz, sz).apply { marginEnd = (8 * dp).toInt() }
            setOnClickListener { onMicClicked?.invoke() }
        }

        val ramblerCol = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        ramblerCol.addView(
            TextView(context).apply {
                text = "Rambler •"
                textSize = 13.5f
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

        // 2. 4x2 Stitch Tool Grid
        gridContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (8 * dp).toInt()
            }
        }
        addView(gridContainer)

        // 3. Pinned / Recent AES-256 Clipboard Quick-Paste Card
        clipCard = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((10 * dp).toInt(), (8 * dp).toInt(), (10 * dp).toInt(), (8 * dp).toInt())
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (8 * dp).toInt()
            }
        }
        val lockBadge = TextView(context).apply {
            text = "🔒"
            textSize = 14f
            gravity = Gravity.CENTER
            background = UiKit.roundedDrawable(context, UiKit.SUCCESS_BG, 10f, UiKit.SUCCESS_BORDER, 1f)
            val sz = (32 * dp).toInt()
            layoutParams = LayoutParams(sz, sz).apply { marginEnd = (10 * dp).toInt() }
        }
        val clipCol = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        clipTitleTv = TextView(context).apply {
            text = "AES-256 • CLIPBOARD READY"
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(UiKit.SUCCESS_FG)
        }
        clipPreviewTv = TextView(context).apply {
            text = "Copy text in any app to pin or paste here"
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setSingleLine(true)
            setTextColor(UiKit.TEXT_PRIMARY)
        }
        clipCol.addView(clipTitleTv)
        clipCol.addView(clipPreviewTv)

        clipPasteBtn = UiKit.createPillButton(context, "Paste", filled = true) {
            latestClip?.let { onPasteClipClicked?.invoke(it.text) }
        }.apply {
            textSize = 12f
            setPadding((12 * dp).toInt(), (6 * dp).toInt(), (12 * dp).toInt(), (6 * dp).toInt())
        }
        clipCard.addView(lockBadge)
        clipCard.addView(clipCol)
        clipCard.addView(clipPasteBtn)
        addView(clipCard)

        // 4. Bottom Return Strip
        val bottomRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        backKeysBtn = UiKit.createPillButton(context, "⌨  Keyboard", filled = false) {
            onCollapseClicked?.invoke()
        }.apply {
            layoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (38 * dp).toInt())
        }
        langPillTv = TextView(context).apply {
            text = "English + Hinglish"
            textSize = 12.5f
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(0, (38 * dp).toInt(), 1f).apply {
                marginStart = (8 * dp).toInt()
                marginEnd = (8 * dp).toInt()
            }
            setOnClickListener { onToggleHinglishClicked?.invoke() }
        }
        micPillBtn = UiKit.createPillButton(context, "🎙 Dictate", filled = true) {
            onMicClicked?.invoke()
        }.apply {
            layoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (38 * dp).toInt())
        }
        bottomRow.addView(backKeysBtn)
        bottomRow.addView(langPillTv)
        bottomRow.addView(micPillBtn)
        addView(bottomRow)

        renderGrid()
        syncRamblerChips()
    }

    fun updateClipboardPreview(totalCount: Int, topItem: ClipboardItem?) {
        clipCount = totalCount
        latestClip = topItem
        if (topItem != null) {
            clipTitleTv.text = if (topItem.isPinned) "AES-256 • PINNED" else "AES-256 • RECENT CLIP"
            clipPreviewTv.text = topItem.text.replace("\n", " ").take(42)
            clipPasteBtn.visibility = View.VISIBLE
        } else {
            clipTitleTv.text = "AES-256 • ENCRYPTED VAULT"
            clipPreviewTv.text = "No saved clips yet"
            clipPasteBtn.visibility = View.GONE
        }
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

        data class ToolTile(
            val icon: String,
            val label: String,
            val badge: String? = null,
            val active: Boolean = false,
            val onClick: () -> Unit
        )

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

        val accent = theme.accentKeyColor ?: UiKit.ACCENT_PRIMARY
        val surfaceFill = theme.keyFaceColor.primaryColor

        for (rowIdx in 0 until 2) {
            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    if (rowIdx == 0) bottomMargin = (6 * dp).toInt()
                }
            }
            for (colIdx in 0 until 4) {
                val tile = tiles[rowIdx * 4 + colIdx]
                val card = LinearLayout(context).apply {
                    orientation = VERTICAL
                    gravity = Gravity.CENTER
                    background = UiKit.roundedDrawable(
                        context,
                        if (tile.active) Color.argb(46, Color.red(accent), Color.green(accent), Color.blue(accent)) else surfaceFill,
                        12f,
                        if (tile.active) accent else theme.keyBorderColor,
                        1f
                    )
                    setPadding((4 * dp).toInt(), (8 * dp).toInt(), (4 * dp).toInt(), (8 * dp).toInt())
                    layoutParams = LayoutParams(0, (60 * dp).toInt(), 1f).apply {
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
            langPillTv.text = if (isHinglishEnabled) "🌐 English + Hinglish" else "🌐 English • QWERTY"
        }
    }

    private fun applyTheme() {
        setBackgroundColor(theme.keyboardBackground.colorStart)
        val cardFill = theme.functionalKeyColor ?: UiKit.SURFACE_CARD
        val border = theme.keyBorderColor
        ramblerCard.background = UiKit.roundedDrawable(context, cardFill, 14f, border, 1f)
        clipCard.background = UiKit.roundedDrawable(context, cardFill, 14f, border, 1f)
        langPillTv.background = UiKit.roundedDrawable(context, cardFill, 12f, border, 1f)
        langPillTv.setTextColor(theme.keyLabelColorSecondary)

        val chipFill = theme.keyFaceColor.primaryColor
        for (btn in listOf(fixItBtn, formalBtn, conciseBtn)) {
            btn.background = UiKit.roundedDrawable(context, chipFill, 9f, border, 1f)
            btn.setTextColor(theme.keyLabelColorPrimary)
        }
        renderGrid()
    }
}
