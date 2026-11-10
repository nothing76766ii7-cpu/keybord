package com.keybord.ime

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.keyboard.engine.magicwrite.WritingStyle
import com.keybord.clipboard.ClipboardItem
import com.keybord.theme.BuiltInThemes
import com.keybord.theme.ThemeModel

/**
 * Enhanced Suggestion Strip supporting:
 * [Clipboard] [Magic ✨] [Candidate 1] [Candidate 2] [Candidate 3] [Mic 🎙] [Settings ⚙]
 * plus expandable Magic Writer toolbar (Fix it, 6 styles, Undo) and live Voice Rambler bar.
 */
class SuggestionStripView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private lateinit var clipboardBtn: TextView
    private lateinit var emojiBtn: TextView
    private lateinit var magicBtn: TextView
    private lateinit var candidateContainer: LinearLayout
    private lateinit var micBtn: TextView
    private lateinit var settingsBtn: TextView
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
    private var isMicListening = false
    private var hasRamblerRevertChunk = false

    var onCandidateSelected: ((String) -> Unit)? = null
    var onCandidateDeleteRequested: ((String) -> Unit)? = null
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
            setPadding((8 * dp).toInt(), (2 * dp).toInt(), (8 * dp).toInt(), (2 * dp).toInt())
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

        // Row 4: Main Suggestion Strip
        val stripRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (42 * dp).toInt())
        }

        clipboardBtn = createStripIconButton("📋", dp, 16.5f) {
            onClipboardIconClicked?.invoke()
        }

        emojiBtn = createStripIconButton("😊", dp, 16.5f) {
            onEmojiIconClicked?.invoke()
        }

        magicBtn = createStripIconButton("✨", dp, 16.5f) {
            toggleMagicBar()
        }

        candidateContainer = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        }

        micBtn = createStripIconButton("🎙", dp, 16.5f) {
            onMicClicked?.invoke()
        }

        settingsBtn = createStripIconButton("⚙", dp, 16.5f) {
            onSettingsIconClicked?.invoke()
        }

        stripRow.addView(clipboardBtn)
        stripRow.addView(emojiBtn)
        stripRow.addView(magicBtn)
        stripRow.addView(candidateContainer)
        stripRow.addView(micBtn)
        stripRow.addView(settingsBtn)
        addView(stripRow)
    }

    fun setEmojiPanelActive(active: Boolean) {
        isEmojiActive = active
        applyTheme()
    }

    private fun createStripIconButton(
        label: String,
        dp: Float,
        sizeSp: Float,
        onClick: () -> Unit
    ): TextView {
        return TextView(context).apply {
            text = label
            textSize = sizeSp
            gravity = Gravity.CENTER
            setPadding((7 * dp).toInt(), 0, (7 * dp).toInt(), 0)
            layoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setOnClickListener { onClick() }
        }
    }

    private fun buildMagicChips() {
        magicChipsRow.removeAllViews()
        val dp = resources.displayMetrics.density

        fun addChip(label: String, onClick: () -> Unit): TextView {
            val chip = TextView(context).apply {
                text = label
                textSize = 12f
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
        val accent = theme.effectColors.firstOrNull() ?: theme.accentKeyColor ?: theme.trailColor
        clipboardBtn.setTextColor(textColor)
        emojiBtn.setTextColor(if (isEmojiActive) accent else textColor)
        magicBtn.setTextColor(if (isMagicBarVisible) accent else textColor)
        micBtn.setTextColor(if (isMicListening) Color.parseColor("#FF5252") else textColor)
        settingsBtn.setTextColor(textColor)

        val barTint = Color.argb(
            36,
            Color.red(accent),
            Color.green(accent),
            Color.blue(accent)
        )
        ramblerBarContainer.setBackgroundColor(barTint)
        ramblerStatusTv.setTextColor(textColor)
        ramblerDismissBtn.setTextColor(textColor)

        val chipBg = GradientDrawable().apply {
            cornerRadius = 14 * dp
            setColor(
                Color.argb(
                    48,
                    Color.red(accent),
                    Color.green(accent),
                    Color.blue(accent)
                )
            )
            setStroke((1 * dp).toInt().coerceAtLeast(1), Color.argb(110, Color.red(accent), Color.green(accent), Color.blue(accent)))
        }
        ramblerActionBtn.background = chipBg.constantState?.newDrawable()?.mutate() ?: chipBg
        ramblerActionBtn.setTextColor(textColor)

        for (i in 0 until magicChipsRow.childCount) {
            val tv = magicChipsRow.getChildAt(i) as? TextView ?: continue
            tv.background = chipBg.constantState?.newDrawable()?.mutate() ?: chipBg
            tv.setTextColor(textColor)
        }

        for (i in 0 until candidateContainer.childCount) {
            (candidateContainer.getChildAt(i) as? TextView)?.setTextColor(textColor)
        }
    }

    /**
     * Updates candidate predictions (up to 3 candidates per PRD spec).
     */
    fun setCandidates(words: List<String>) {
        candidateContainer.removeAllViews()
        val dp = resources.displayMetrics.density
        val topCandidates = words.take(3)

        if (topCandidates.isEmpty()) {
            return
        }

        for (word in topCandidates) {
            val tv = TextView(context).apply {
                text = word
                textSize = 15f
                setTextColor(theme.suggestionStripTextColor)
                gravity = Gravity.CENTER
                typeface = theme.keyLabelFont.toTypeface()
                layoutParams = LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                setPadding((4 * dp).toInt(), 0, (4 * dp).toInt(), 0)

                setOnClickListener {
                    onCandidateSelected?.invoke(word)
                }

                // PRD: "Long-press any candidate deletes it from personal history with one confirmation tap"
                setOnLongClickListener {
                    confirmCandidateDeletion(word)
                    true
                }
            }
            candidateContainer.addView(tv)
        }
    }

    /**
     * Updates recent clipboard items shown inline in suggestion strip (PRD Feature 1).
     */
    fun setRecentClipboardItems(items: List<ClipboardItem>) {
        recentClipsContainer.removeAllViews()
        val dp = resources.displayMetrics.density

        if (items.isEmpty()) {
            recentClipsContainer.visibility = View.GONE
            return
        }

        recentClipsContainer.visibility = View.VISIBLE
        for (item in items.take(3)) {
            val chip = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(Color.parseColor("#334285F4"))
                setPadding((8 * dp).toInt(), (2 * dp).toInt(), (4 * dp).toInt(), (2 * dp).toInt())
                val lp = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = (6 * dp).toInt()
                }
                layoutParams = lp
            }

            val tvText = TextView(context).apply {
                text = item.text.replace("\n", " ").take(16) + if (item.text.length > 16) "…" else ""
                setTextColor(Color.WHITE)
                textSize = 11f
                setOnClickListener { onRecentClipSelected?.invoke(item.text) }
            }

            val tvDismiss = TextView(context).apply {
                text = " ✕"
                setTextColor(Color.parseColor("#B0BEC5"))
                textSize = 11f
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
