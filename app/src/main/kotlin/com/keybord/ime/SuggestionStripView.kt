package com.keybord.ime

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
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
import com.keybord.theme.KeyboardEffectEngine
import com.keybord.theme.ThemeModel
import com.keybord.ui.UiKit

/**
 * Tactile Skeuomorphic Suggestion Strip & Quick Toolbar:
 * - Row 1: Expandable recent clipboard quick-paste chips
 * - Row 2: Live Voice Rambler stream & Cleaned/Raw undo bar
 * - Row 3: Optional inline Magic Writer tone chips / inline Safe Candidate Forget confirm bar
 * - Row 4: Tactile raised Toolbar Rail ([⊞] [📋] [😊] [B32] [EN+HI] [✨ AI] [🎙] [⚙])
 * - Row 5: Recessed Candidate Well with raised primary pill and hairline dividers
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
    private lateinit var b32Btn: TextView
    private lateinit var langBtn: TextView
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

    // Safe inline candidate removal confirmation bar (never uses AlertDialog in IME Service context)
    private lateinit var confirmDeleteBar: LinearLayout
    private lateinit var confirmDeleteLabel: TextView
    private var pendingDeleteWord: String? = null

    // Magic Writer inline quick-action ribbon (long-press ✨ AI or programmatic)
    private lateinit var magicScrollView: HorizontalScrollView
    private lateinit var magicChipsRow: LinearLayout
    private var magicUndoChip: TextView? = null
    private var isMagicBarVisible = false
    private var isAiWriterActive = false
    private var isEmojiActive = false
    private var isToolsActive = false
    private var isBase32Active = false
    private var isHinglishEnabled = true
    private var isMicListening = false
    private var hasRamblerRevertChunk = false
    private var lastCandidates: List<String> = emptyList()

    var onCandidateSelected: ((String) -> Unit)? = null
    var onCandidateDeleteRequested: ((String) -> Unit)? = null
    var onToolsGridClicked: (() -> Unit)? = null
    var onClipboardIconClicked: (() -> Unit)? = null
    var onEmojiIconClicked: (() -> Unit)? = null
    var onBase32Clicked: (() -> Unit)? = null
    var onLanguageToggleClicked: (() -> Unit)? = null
    var onAiWriterClicked: (() -> Unit)? = null
    var onSettingsIconClicked: (() -> Unit)? = null
    var onRecentClipDismissed: ((Long) -> Unit)? = null
    var onRecentClipSelected: ((String) -> Unit)? = null

    // Magic Writer quick inline callbacks
    var onMagicProofreadClicked: (() -> Unit)? = null
    var onMagicStyleClicked: ((WritingStyle) -> Unit)? = null
    var onMagicUndoClicked: (() -> Unit)? = null

    // Voice Rambler callbacks
    var onMicClicked: (() -> Unit)? = null
    var onRamblerStopClicked: (() -> Unit)? = null
    var onRamblerToggleRawClicked: (() -> Unit)? = null

    var theme: ThemeModel = BuiltInThemes.SKEUO_DARK_GRAPHITE
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

        // Row 2b: Safe inline candidate forget confirmation bar (replaces unsafe AlertDialog inside IME)
        confirmDeleteBar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding((10 * dp).toInt(), (4 * dp).toInt(), (10 * dp).toInt(), (4 * dp).toInt())
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (36 * dp).toInt())
        }
        confirmDeleteLabel = TextView(context).apply {
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setSingleLine(true)
        }
        val confirmYesBtn = TextView(context).apply {
            text = "Remove"
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = UiKit.skeuoButtonDrawable(context, Color.parseColor("#B91C1C"), 8f)
            setPadding((10 * dp).toInt(), (4 * dp).toInt(), (10 * dp).toInt(), (4 * dp).toInt())
            layoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = (6 * dp).toInt()
            }
            setOnClickListener {
                val target = pendingDeleteWord
                dismissDeleteConfirmation()
                if (!target.isNullOrBlank()) {
                    onCandidateDeleteRequested?.invoke(target)
                }
            }
        }
        val confirmNoBtn = TextView(context).apply {
            text = "Keep"
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(UiKit.TEXT_PRIMARY)
            background = UiKit.skeuoButtonDrawable(context, UiKit.SURFACE_ELEVATED, 8f)
            setPadding((10 * dp).toInt(), (4 * dp).toInt(), (10 * dp).toInt(), (4 * dp).toInt())
            setOnClickListener { dismissDeleteConfirmation() }
        }
        confirmDeleteBar.addView(confirmDeleteLabel)
        confirmDeleteBar.addView(confirmYesBtn)
        confirmDeleteBar.addView(confirmNoBtn)
        addView(confirmDeleteBar)

        // Row 3: Quick Magic Ribbon (accessible on long-press of ✨ AI)
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

        // Row 4: Skeuomorphic Toolbar Rail
        toolbarRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((6 * dp).toInt(), (4 * dp).toInt(), (6 * dp).toInt(), (4 * dp).toInt())
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (38 * dp).toInt())
        }

        toolsBtn = createRailButton("⊞", dp, 15f) { onToolsGridClicked?.invoke() }
        clipboardBtn = createRailButton("📋", dp, 14f) { onClipboardIconClicked?.invoke() }
        emojiBtn = createRailButton("😊", dp, 14.5f) { onEmojiIconClicked?.invoke() }
        b32Btn = createRailButton("B32", dp, 10.5f) { onBase32Clicked?.invoke() }.apply {
            typeface = Typeface.MONOSPACE
        }
        langBtn = createRailButton("EN+HI", dp, 10.5f) { onLanguageToggleClicked?.invoke() }.apply {
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LayoutParams(0, (28 * dp).toInt(), 1.25f).apply {
                marginEnd = (4 * dp).toInt()
            }
        }
        magicBtn = createRailButton("✨ AI", dp, 11.5f) {
            if (onAiWriterClicked != null) {
                onAiWriterClicked?.invoke()
            } else {
                toggleMagicBar()
            }
        }.apply {
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LayoutParams(0, (28 * dp).toInt(), 1.3f).apply {
                marginEnd = (4 * dp).toInt()
            }
            setOnLongClickListener {
                toggleMagicBar()
                true
            }
        }
        micBtn = createRailButton("🎙", dp, 14.5f) { onMicClicked?.invoke() }
        settingsBtn = createRailButton("⚙", dp, 14.5f) { onSettingsIconClicked?.invoke() }.apply {
            layoutParams = LayoutParams(0, (28 * dp).toInt(), 1f)
        }

        toolbarRow.addView(toolsBtn)
        toolbarRow.addView(clipboardBtn)
        toolbarRow.addView(emojiBtn)
        toolbarRow.addView(b32Btn)
        toolbarRow.addView(langBtn)
        toolbarRow.addView(magicBtn)
        toolbarRow.addView(micBtn)
        toolbarRow.addView(settingsBtn)
        addView(toolbarRow)

        // Recessed shadow hairline between rail & suggestion trough
        stripDivider = View(context).apply {
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (1 * dp).toInt().coerceAtLeast(1))
        }
        addView(stripDivider)

        // Row 5: Recessed Candidate Well (3-slot predictive ribbon)
        candidateContainer = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((6 * dp).toInt(), (3 * dp).toInt(), (6 * dp).toInt(), (3 * dp).toInt())
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (36 * dp).toInt()).apply {
                setMargins((8 * dp).toInt(), (3 * dp).toInt(), (8 * dp).toInt(), (4 * dp).toInt())
            }
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
            setSingleLine(true)
            setPadding((3 * dp).toInt(), (2 * dp).toInt(), (3 * dp).toInt(), (2 * dp).toInt())
            layoutParams = LayoutParams(0, (28 * dp).toInt(), 1f).apply {
                marginEnd = (4 * dp).toInt()
            }
            setOnClickListener { onClick() }
        }
    }

    fun setHinglishEnabled(enabled: Boolean) {
        isHinglishEnabled = enabled
        if (::langBtn.isInitialized) {
            langBtn.text = if (enabled) "EN+HI" else "EN"
        }
        applyTheme()
    }

    fun setAiWriterActive(active: Boolean) {
        isAiWriterActive = active
        if (active) {
            isEmojiActive = false
            isToolsActive = false
            isBase32Active = false
        }
        applyTheme()
    }

    fun setEmojiPanelActive(active: Boolean) {
        isEmojiActive = active
        if (active) {
            isToolsActive = false
            isBase32Active = false
            isAiWriterActive = false
        }
        applyTheme()
    }

    fun setToolsPanelActive(active: Boolean) {
        isToolsActive = active
        if (active) {
            isEmojiActive = false
            isBase32Active = false
            isAiWriterActive = false
        }
        applyTheme()
    }

    fun setBase32PanelActive(active: Boolean) {
        isBase32Active = active
        if (active) {
            isEmojiActive = false
            isToolsActive = false
            isAiWriterActive = false
        }
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
        val bg = theme.suggestionStripBgColor
        setBackgroundColor(bg)
        val textColor = theme.suggestionStripTextColor
        val secondaryColor = theme.keyLabelColorSecondary
        val accent = theme.effectColors.firstOrNull() ?: theme.accentKeyColor ?: theme.trailColor
        val isLight = Color.luminance(bg) > 0.5f
        val capFill = KeyboardEffectEngine.compositeOver(theme.functionalKeyColor ?: theme.keyFaceColor.primaryColor, bg)
        val wellFill = KeyboardEffectEngine.compositeOver(UiKit.shadeColor(bg, if (isLight) -0.09f else -0.25f), bg)

        stripDivider.setBackgroundColor(if (isLight) Color.argb(35, 15, 23, 42) else Color.argb(42, 0, 0, 0))

        // Tactile raised rail icon buttons
        fun styleCap(btn: TextView, active: Boolean, fgDefault: Int = textColor) {
            btn.setTextColor(if (active) Color.WHITE else fgDefault)
            btn.background = UiKit.skeuoButtonDrawable(
                context,
                if (active) accent else capFill,
                9f,
                if (active) accent else theme.keyBorderColor
            )
        }
        styleCap(toolsBtn, isToolsActive, secondaryColor)
        styleCap(clipboardBtn, false, textColor)
        styleCap(emojiBtn, isEmojiActive, textColor)
        styleCap(b32Btn, isBase32Active, secondaryColor)
        styleCap(langBtn, isHinglishEnabled, textColor)
        langBtn.text = if (isHinglishEnabled) "EN+HI" else "EN"
        if (!isHinglishEnabled) {
            langBtn.setTextColor(textColor)
        }

        val aiColor = if (isAiWriterActive || isMagicBarVisible) UiKit.ACCENT_AI else UiKit.blendColors(capFill, UiKit.ACCENT_AI, 0.35f)
        magicBtn.setTextColor(if (isAiWriterActive || isMagicBarVisible || !isLight) Color.WHITE else UiKit.ACCENT_AI)
        magicBtn.background = UiKit.skeuoButtonDrawable(context, aiColor, 10f, UiKit.ACCENT_AI)

        styleCap(micBtn, isMicListening, textColor)
        if (isMicListening) {
            micBtn.background = UiKit.skeuoButtonDrawable(context, Color.parseColor("#DC2626"), 9f)
        }
        styleCap(settingsBtn, false, secondaryColor)

        // Recessed candidate trough well
        candidateContainer.background = UiKit.skeuoWellDrawable(context, wellFill, 11f, theme.keyBorderColor)

        val barTint = KeyboardEffectEngine.compositeOver(Color.argb(40, Color.red(accent), Color.green(accent), Color.blue(accent)), bg)
        ramblerBarContainer.setBackgroundColor(barTint)
        ramblerStatusTv.setTextColor(textColor)
        ramblerDismissBtn.setTextColor(textColor)
        ramblerActionBtn.background = UiKit.skeuoButtonDrawable(context, capFill, 8f, accent)
        ramblerActionBtn.setTextColor(textColor)

        confirmDeleteBar.setBackgroundColor(barTint)
        confirmDeleteLabel.setTextColor(textColor)

        for (i in 0 until magicChipsRow.childCount) {
            val tv = magicChipsRow.getChildAt(i) as? TextView ?: continue
            tv.background = UiKit.skeuoButtonDrawable(context, capFill, 10f, theme.keyBorderColor)
            tv.setTextColor(textColor)
        }

        if (lastCandidates.isNotEmpty()) {
            renderCandidateViews(lastCandidates)
        }
    }

    /**
     * Updates candidate predictions (up to 3 candidates) inside the recessed suggestion well
     * with a raised tactile center primary pill.
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
        val bg = theme.suggestionStripBgColor
        val isLight = Color.luminance(bg) > 0.5f
        val raisedPillColor = KeyboardEffectEngine.compositeOver(theme.keyFaceColor.primaryColor, bg)

        // Place best candidate (index 0) in center slot when >= 2 candidates exist
        val ordered: List<Pair<String, Boolean>> = when (topCandidates.size) {
            1 -> listOf(topCandidates[0] to true)
            2 -> listOf(topCandidates[0] to true, topCandidates[1] to false)
            else -> listOf(topCandidates[1] to false, topCandidates[0] to true, topCandidates[2] to false)
        }

        ordered.forEachIndexed { idx, (word, isPrimary) ->
            if (idx > 0) {
                candidateContainer.addView(
                    View(context).apply {
                        setBackgroundColor(if (isLight) Color.argb(30, 15, 23, 42) else Color.argb(28, 255, 255, 255))
                        layoutParams = LayoutParams((1 * dp).toInt().coerceAtLeast(1), (16 * dp).toInt()).apply {
                            marginStart = (3 * dp).toInt()
                            marginEnd = (3 * dp).toInt()
                        }
                    }
                )
            }

            if (isPrimary) {
                val pillBox = FrameLayout(context).apply {
                    background = UiKit.skeuoButtonDrawable(context, raisedPillColor, 9f, accent)
                    layoutParams = LayoutParams(0, (28 * dp).toInt(), 1.15f).apply {
                        marginStart = (2 * dp).toInt()
                        marginEnd = (2 * dp).toInt()
                    }
                    setOnClickListener { onCandidateSelected?.invoke(word) }
                    setOnLongClickListener {
                        showInlineDeleteConfirmation(word)
                        true
                    }
                }
                val tv = TextView(context).apply {
                    text = word
                    textSize = 13.5f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(textColor)
                    gravity = Gravity.CENTER
                    setSingleLine(true)
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                val glowBar = View(context).apply {
                    background = UiKit.roundedDrawable(context, accent, 4f)
                    layoutParams = FrameLayout.LayoutParams(
                        (20 * dp).toInt(),
                        (2 * dp).toInt().coerceAtLeast(2),
                        Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    ).apply {
                        bottomMargin = (2 * dp).toInt()
                    }
                }
                pillBox.addView(tv)
                pillBox.addView(glowBar)
                candidateContainer.addView(pillBox)
            } else {
                val tv = TextView(context).apply {
                    text = word
                    textSize = 13f
                    setTextColor(secondaryColor)
                    gravity = Gravity.CENTER
                    setSingleLine(true)
                    typeface = theme.keyLabelFont.toTypeface()
                    layoutParams = LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                    setPadding((4 * dp).toInt(), 0, (4 * dp).toInt(), 0)
                    setOnClickListener { onCandidateSelected?.invoke(word) }
                    setOnLongClickListener {
                        showInlineDeleteConfirmation(word)
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
        val bg = theme.suggestionStripBgColor
        val chipBg = KeyboardEffectEngine.compositeOver(theme.keyFaceColor.primaryColor, bg)
        recentClipsContainer.visibility = View.VISIBLE
        for (item in items.take(3)) {
            val chip = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = UiKit.skeuoCardDrawable(context, chipBg, 10f, accent, 1.5f)
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

    private fun showInlineDeleteConfirmation(word: String) {
        pendingDeleteWord = word
        confirmDeleteLabel.text = "Forget \"$word\"?"
        confirmDeleteBar.visibility = View.VISIBLE
    }

    private fun dismissDeleteConfirmation() {
        pendingDeleteWord = null
        confirmDeleteBar.visibility = View.GONE
    }
}
