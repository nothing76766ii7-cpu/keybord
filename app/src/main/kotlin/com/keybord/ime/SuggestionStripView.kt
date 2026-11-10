package com.keybord.ime

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.keyboard.engine.magicwrite.WritingStyle
import com.keybord.clipboard.ClipboardItem
import com.keybord.theme.BuiltInThemes
import com.keybord.theme.ThemeModel
import com.keybord.ui.UiKit

/**
 * Stitch Obsidian Tactile / Clean Porcelain IME Chrome:
 * - Row 1: Expandable recent clipboard quick-paste chips
 * - Row 2: Live Voice Rambler stream & Cleaned/Raw undo bar
 * - Row 3: Expandable Magic Writer tone/proofread ribbon
 * - Row 4: Toolbar Rail ([⊞ Tools] [📋 Clipboard] [😊 Emoji] [✨ AI] [🎙 Mic] [⚙ Settings])
 * - Row 5: Balanced 3-slot Candidate Ribbon with Stitch highlighted center primary pill
 */
class SuggestionStripView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private lateinit var toolbarRow: LinearLayout
    private lateinit var toolsBtn: TextView
    private lateinit var clipboardBtn: TextView
    private lateinit var emojiBtn: TextView
    private lateinit var magicBtn: TextView
    private lateinit var micBtn: TextView
    private lateinit var settingsBtn: TextView
    private lateinit var stripDivider: View
    private lateinit var candidateContainer: LinearLayout
    private lateinit var recentClipsContainer: LinearLayout

    // Voice Rambler status / Raw-Clean toggle row
    private lateinit var ramblerBarContainer: LinearLayout
    private lateinit var ramblerStatusTv: TextView
    private lateinit var ramblerActionBtn: TextView
    private lateinit var ramblerDismissBtn: TextView

    // Magic Writer inline toolbar
    private lateinit var magicScrollView: HorizontalScrollView
    private lateinit var magicChipsRow: LinearLayout
    private var magicUndoChip: TextView? = null
    private var isMagicBarVisible = false
    private var isEmojiActive = false
    private var isToolsActive = false
    private var isMicListening = false
    private var hasRamblerRevertChunk = false
    private var lastCandidates: List<String> = emptyList()

    var onCandidateSelected: ((String) -> Unit)? = null
    var onCandidateDeleteRequested: ((String) -> Unit)? = null
    var onToolsGridClicked: (() -> Unit)? = null
    var onClipboardIconClicked: (() -> Unit)? = null
    var onEmojiIconClicked: (() -> Unit)? = null
    var onSettingsIconClicked: (() -> Unit)? = null
    var onRecentClipDismissed: ((Long) -> Unit)? = null
    var onRecentClipSelected: ((String) -> Unit)? = null

    // Magic Writer callbacks
    var onMagicProofreadClicked: (() -> Unit)? = null
    var onMagicStyleClicked: ((WritingStyle) -> Unit)? = null
    var onMagicUndoClicked: (() -> Unit)? = null

    // Voice Rambler callbacks
    var onMicClicked: (() -> Unit)? = null
    var onRamblerStopClicked: (() -> Unit)? = null
    var onRamblerToggleRawClicked: (() -> Unit)? = null

    var theme: ThemeModel = BuiltInThemes.MATERIAL_MIDNIGHT
        set(value) {
            field = value
            applyTheme()
        }

    init {
        orientation = VERTICAL
        setupViews()
        applyTheme()
    }

    private fun setupViews() {
        val dp = resources.displayMetrics.density

        // Row 1: Optional Recent Clipboard Items (last 3, dismissible)
        recentClipsContainer = LinearLayout(context).apply {
            orientation = HORIZONTAL
            visibility = View.GONE
            setPadding((8 * dp).toInt(), (4 * dp).toInt(), (8 * dp).toInt(), (2 * dp).toInt())
        }
        addView(recentClipsContainer)

        // Row 2: Expandable Voice Rambler Status + Raw/Clean Toggle Bar
        ramblerBarContainer = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding((10 * dp).toInt(), (4 * dp).toInt(), (10 * dp).toInt(), (4 * dp).toInt())
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (36 * dp).toInt())
        }
        ramblerStatusTv = TextView(context).apply {
            text = "🎙 Listening…"
            textSize = 12f
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setSingleLine(true)
        }
        ramblerActionBtn = TextView(context).apply {
            text = "Stop"
            textSize = 11.5f
            gravity = Gravity.CENTER
            setPadding((10 * dp).toInt(), (3 * dp).toInt(), (10 * dp).toInt(), (3 * dp).toInt())
            setOnClickListener {
                if (isMicListening) {
                    onRamblerStopClicked?.invoke()
                } else if (hasRamblerRevertChunk) {
                    onRamblerToggleRawClicked?.invoke()
                }
            }
        }
        ramblerDismissBtn = TextView(context).apply {
            text = "✕"
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding((10 * dp).toInt(), (3 * dp).toInt(), (6 * dp).toInt(), (3 * dp).toInt())
            setOnClickListener { hideRamblerBar() }
        }
        ramblerBarContainer.addView(ramblerStatusTv)
        ramblerBarContainer.addView(ramblerActionBtn)
        ramblerBarContainer.addView(ramblerDismissBtn)
        addView(ramblerBarContainer)

        // Row 3: Expandable Magic Writer Horizontal Toolbar
        magicScrollView = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            visibility = View.GONE
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (38 * dp).toInt())
        }
        magicChipsRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((8 * dp).toInt(), (3 * dp).toInt(), (8 * dp).toInt(), (3 * dp).toInt())
        }
        buildMagicChips()
        magicScrollView.addView(magicChipsRow)
        addView(magicScrollView)

        // Row 4: Stitch Toolbar Rail (6 crisp quick-action slots)
        toolbarRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((6 * dp).toInt(), (2 * dp).toInt(), (6 * dp).toInt(), (2 * dp).toInt())
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (36 * dp).toInt())
        }

        toolsBtn = createRailButton("⊞", dp, 17f) { onToolsGridClicked?.invoke() }
        clipboardBtn = createRailButton("📋", dp, 15.5f) { onClipboardIconClicked?.invoke() }
        emojiBtn = createRailButton("😊", dp, 16f) { onEmojiIconClicked?.invoke() }
        magicBtn = createRailButton("✨ AI", dp, 12.5f) { toggleMagicBar() }.apply {
            typeface = Typeface.DEFAULT_BOLD
            setPadding((9 * dp).toInt(), (4 * dp).toInt(), (9 * dp).toInt(), (4 * dp).toInt())
            layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.15f)
        }
        micBtn = createRailButton("🎙", dp, 16f) { onMicClicked?.invoke() }
        settingsBtn = createRailButton("⚙", dp, 16.5f) { onSettingsIconClicked?.invoke() }

        toolbarRow.addView(toolsBtn)
        toolbarRow.addView(clipboardBtn)
        toolbarRow.addView(emojiBtn)
        toolbarRow.addView(magicBtn)
        toolbarRow.addView(micBtn)
        toolbarRow.addView(settingsBtn)
        addView(toolbarRow)

        // Subtle divider between toolbar & suggestion chips
        stripDivider = View(context).apply {
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (1 * dp).toInt().coerceAtLeast(1))
        }
        addView(stripDivider)

        // Row 5: Stitch 3-slot Predictive Ribbon
        candidateContainer = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((8 * dp).toInt(), (4 * dp).toInt(), (8 * dp).toInt(), (4 * dp).toInt())
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (36 * dp).toInt())
        }
        addView(candidateContainer)
    }

    private fun createRailButton(
        label: String,
        dp: Float,
        sizeSp: Float,
        onClick: () -> Unit
    ): TextView {
        return TextView(context).apply {
            text = label
            textSize = sizeSp
            gravity = Gravity.CENTER
            setPadding((4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt())
            layoutParams = LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            setOnClickListener { onClick() }
        }
    }

    fun setEmojiPanelActive(active: Boolean) {
        isEmojiActive = active
        if (active) isToolsActive = false
        applyTheme()
    }

    fun setToolsPanelActive(active: Boolean) {
        isToolsActive = active
        if (active) isEmojiActive = false
        applyTheme()
    }

    private fun buildMagicChips() {
        magicChipsRow.removeAllViews()
        val dp = resources.displayMetrics.density

        fun addChip(label: String, onClick: () -> Unit): TextView {
            val chip = TextView(context).apply {
                text = label
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding((11 * dp).toInt(), (4 * dp).toInt(), (11 * dp).toInt(), (4 * dp).toInt())
                val lp = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (28 * dp).toInt()).apply {
                    marginEnd = (6 * dp).toInt()
                }
                layoutParams = lp
                setOnClickListener { onClick() }
            }
            magicChipsRow.addView(chip)
            return chip
        }

        addChip("✨ Fix it") { onMagicProofreadClicked?.invoke() }
        for (style in WritingStyle.values()) {
            addChip(style.displayName) { onMagicStyleClicked?.invoke(style) }
        }
        magicUndoChip = addChip("↩ Undo") { onMagicUndoClicked?.invoke() }.apply {
            visibility = View.GONE
        }
    }

    fun toggleMagicBar() {
        isMagicBarVisible = !isMagicBarVisible
        magicScrollView.visibility = if (isMagicBarVisible) View.VISIBLE else View.GONE
        applyTheme()
    }

    fun hideMagicBar() {
        isMagicBarVisible = false
        magicScrollView.visibility = View.GONE
        applyTheme()
    }

    fun setMagicUndoVisible(visible: Boolean) {
        magicUndoChip?.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun showRamblerListeningState() {
        isMicListening = true
        hasRamblerRevertChunk = false
        ramblerBarContainer.visibility = View.VISIBLE
        ramblerStatusTv.text = "🎙 Listening… speak naturally"
        ramblerActionBtn.text = "Stop"
        ramblerActionBtn.visibility = View.VISIBLE
        micBtn.text = "🔴"
        applyTheme()
    }

    fun showRamblerStreamingPreview(cleanedPreview: String) {
        isMicListening = true
        ramblerBarContainer.visibility = View.VISIBLE
        val shortPreview = if (cleanedPreview.length > 34) "…${cleanedPreview.takeLast(33)}" else cleanedPreview
        ramblerStatusTv.text = if (shortPreview.isNotBlank()) "🎙 $shortPreview" else "🎙 Listening…"
        ramblerActionBtn.text = "Stop"
        ramblerActionBtn.visibility = View.VISIBLE
    }

    fun showRamblerRevertState(isShowingRaw: Boolean) {
        isMicListening = false
        hasRamblerRevertChunk = true
        ramblerBarContainer.visibility = View.VISIBLE
        ramblerStatusTv.text = if (isShowingRaw) "📝 Raw transcript" else "✨ Dictation cleaned"
        ramblerActionBtn.text = if (isShowingRaw) "🔄 Clean" else "🔄 Raw"
        ramblerActionBtn.visibility = View.VISIBLE
        micBtn.text = "🎙"
        applyTheme()
    }

    fun showRamblerMessage(message: String) {
        isMicListening = false
        hasRamblerRevertChunk = false
        ramblerBarContainer.visibility = View.VISIBLE
        ramblerStatusTv.text = message
        ramblerActionBtn.visibility = View.GONE
        micBtn.text = "🎙"
        applyTheme()
    }

    fun hideRamblerBar() {
        isMicListening = false
        hasRamblerRevertChunk = false
        ramblerBarContainer.visibility = View.GONE
        micBtn.text = "🎙"
        applyTheme()
    }

    private fun applyTheme() {
        val dp = resources.displayMetrics.density
        setBackgroundColor(theme.suggestionStripBgColor)
        val textColor = theme.suggestionStripTextColor
        val secondaryColor = theme.keyLabelColorSecondary
        val accent = theme.effectColors.firstOrNull() ?: theme.accentKeyColor ?: theme.trailColor
        val isLight = Color.luminance(theme.suggestionStripBgColor) > 0.5f

        stripDivider.setBackgroundColor(if (isLight) Color.argb(18, 20, 23, 32) else Color.argb(18, 255, 255, 255))

        toolsBtn.setTextColor(if (isToolsActive) accent else secondaryColor)
        toolsBtn.background = if (isToolsActive) {
            UiKit.roundedDrawable(context, Color.argb(38, Color.red(accent), Color.green(accent), Color.blue(accent)), 8f)
        } else {
            null
        }
        clipboardBtn.setTextColor(textColor)
        emojiBtn.setTextColor(if (isEmojiActive) accent else textColor)
        emojiBtn.background = if (isEmojiActive) {
            UiKit.roundedDrawable(context, Color.argb(38, Color.red(accent), Color.green(accent), Color.blue(accent)), 8f)
        } else {
            null
        }

        magicBtn.setTextColor(if (isMagicBarVisible) Color.WHITE else accent)
        magicBtn.background = UiKit.roundedDrawable(
            context,
            if (isMagicBarVisible) accent else Color.argb(34, Color.red(accent), Color.green(accent), Color.blue(accent)),
            999f,
            Color.argb(90, Color.red(accent), Color.green(accent), Color.blue(accent)),
            1f
        )

        micBtn.setTextColor(if (isMicListening) Color.parseColor("#FF5252") else textColor)
        settingsBtn.setTextColor(secondaryColor)

        val barTint = Color.argb(32, Color.red(accent), Color.green(accent), Color.blue(accent))
        ramblerBarContainer.setBackgroundColor(barTint)
        ramblerStatusTv.setTextColor(textColor)
        ramblerDismissBtn.setTextColor(textColor)

        val chipBg = GradientDrawable().apply {
            cornerRadius = 14 * dp
            setColor(Color.argb(42, Color.red(accent), Color.green(accent), Color.blue(accent)))
            setStroke((1 * dp).toInt().coerceAtLeast(1), Color.argb(100, Color.red(accent), Color.green(accent), Color.blue(accent)))
        }
        ramblerActionBtn.background = chipBg.constantState?.newDrawable()?.mutate() ?: chipBg
        ramblerActionBtn.setTextColor(textColor)

        for (i in 0 until magicChipsRow.childCount) {
            val tv = magicChipsRow.getChildAt(i) as? TextView ?: continue
            tv.background = chipBg.constantState?.newDrawable()?.mutate() ?: chipBg
            tv.setTextColor(textColor)
        }

        if (lastCandidates.isNotEmpty()) {
            renderCandidateViews(lastCandidates)
        }
    }

    /**
     * Updates candidate predictions (up to 3 candidates) styled after Stitch 01/02
     * with centered primary pill highlight and vertical hairline separators.
     */
    fun setCandidates(words: List<String>) {
        lastCandidates = words.take(3)
        renderCandidateViews(lastCandidates)
    }

    private fun renderCandidateViews(topCandidates: List<String>) {
        candidateContainer.removeAllViews()
        if (topCandidates.isEmpty()) return

        val dp = resources.displayMetrics.density
        val textColor = theme.suggestionStripTextColor
        val secondaryColor = theme.keyLabelColorSecondary
        val accent = theme.effectColors.firstOrNull() ?: theme.accentKeyColor ?: theme.trailColor
        val isLight = Color.luminance(theme.suggestionStripBgColor) > 0.5f

        // Place best candidate (index 0) in center slot when >= 2 candidates exist (Gboard/Stitch convention)
        val ordered: List<Pair<String, Boolean>> = when (topCandidates.size) {
            1 -> listOf(topCandidates[0] to true)
            2 -> listOf(topCandidates[0] to true, topCandidates[1] to false)
            else -> listOf(topCandidates[1] to false, topCandidates[0] to true, topCandidates[2] to false)
        }

        ordered.forEachIndexed { idx, (word, isPrimary) ->
            if (idx > 0) {
                candidateContainer.addView(
                    View(context).apply {
                        setBackgroundColor(if (isLight) Color.argb(24, 20, 23, 32) else Color.argb(24, 255, 255, 255))
                        layoutParams = LayoutParams((1 * dp).toInt().coerceAtLeast(1), (16 * dp).toInt()).apply {
                            marginStart = (2 * dp).toInt()
                            marginEnd = (2 * dp).toInt()
                        }
                    }
                )
            }

            if (isPrimary) {
                val pillBox = FrameLayout(context).apply {
                    background = UiKit.roundedDrawable(
                        context,
                        Color.argb(38, Color.red(accent), Color.green(accent), Color.blue(accent)),
                        9f,
                        Color.argb(85, Color.red(accent), Color.green(accent), Color.blue(accent)),
                        1f
                    )
                    layoutParams = LayoutParams(0, (28 * dp).toInt(), 1.15f).apply {
                        marginStart = (3 * dp).toInt()
                        marginEnd = (3 * dp).toInt()
                    }
                    setOnClickListener { onCandidateSelected?.invoke(word) }
                    setOnLongClickListener {
                        confirmCandidateDeletion(word)
                        true
                    }
                }
                val tv = TextView(context).apply {
                    text = word
                    textSize = 14f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(if (isLight) accent else Color.WHITE)
                    gravity = Gravity.CENTER
                    setSingleLine(true)
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                val glowBar = View(context).apply {
                    background = UiKit.roundedDrawable(context, accent, 4f)
                    layoutParams = FrameLayout.LayoutParams((22 * dp).toInt(), (2 * dp).toInt().coerceAtLeast(2), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                        bottomMargin = (2 * dp).toInt()
                    }
                }
                pillBox.addView(tv)
                pillBox.addView(glowBar)
                candidateContainer.addView(pillBox)
            } else {
                val tv = TextView(context).apply {
                    text = if (idx == ordered.lastIndex && ordered.size == 3) "$word •" else word
                    textSize = 13.5f
                    setTextColor(secondaryColor)
                    gravity = Gravity.CENTER
                    setSingleLine(true)
                    typeface = theme.keyLabelFont.toTypeface()
                    layoutParams = LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                    setPadding((4 * dp).toInt(), 0, (4 * dp).toInt(), 0)
                    setOnClickListener { onCandidateSelected?.invoke(word) }
                    setOnLongClickListener {
                        confirmCandidateDeletion(word)
                        true
                    }
                }
                candidateContainer.addView(tv)
            }
        }
    }

    /**
     * Updates recent clipboard items shown inline in suggestion strip.
     */
    fun setRecentClipboardItems(items: List<ClipboardItem>) {
        recentClipsContainer.removeAllViews()
        val dp = resources.displayMetrics.density

        if (items.isEmpty()) {
            recentClipsContainer.visibility = View.GONE
            return
        }

        val accent = theme.effectColors.firstOrNull() ?: theme.accentKeyColor ?: theme.trailColor
        recentClipsContainer.visibility = View.VISIBLE
        for (item in items.take(3)) {
            val chip = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = UiKit.roundedDrawable(
                    context,
                    Color.argb(38, Color.red(accent), Color.green(accent), Color.blue(accent)),
                    10f,
                    Color.argb(75, Color.red(accent), Color.green(accent), Color.blue(accent)),
                    1f
                )
                setPadding((9 * dp).toInt(), (3 * dp).toInt(), (5 * dp).toInt(), (3 * dp).toInt())
                layoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = (6 * dp).toInt()
                }
            }

            val tvText = TextView(context).apply {
                text = item.text.replace("\n", " ").take(16) + if (item.text.length > 16) "…" else ""
                setTextColor(theme.suggestionStripTextColor)
                textSize = 11.5f
                setOnClickListener { onRecentClipSelected?.invoke(item.text) }
            }

            val tvDismiss = TextView(context).apply {
                text = " ✕"
                setTextColor(theme.keyLabelColorSecondary)
                textSize = 11.5f
                setPadding((4 * dp).toInt(), 0, (4 * dp).toInt(), 0)
                setOnClickListener { onRecentClipDismissed?.invoke(item.id) }
            }

            chip.addView(tvText)
            chip.addView(tvDismiss)
            recentClipsContainer.addView(chip)
        }
    }

    private fun confirmCandidateDeletion(word: String) {
        AlertDialog.Builder(context)
            .setTitle("Remove Learned Word")
            .setMessage("Do you want to delete '$word' from your personal vocabulary?")
            .setPositiveButton("Delete") { _, _ ->
                onCandidateDeleteRequested?.invoke(word)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
