package com.keyboard

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * SuggestionStrip custom view strictly matching Section E layout:
 * [Clipboard icon 40dp] [Candidate 1] [Candidate 2] [Candidate 3] [Magic Write icon 40dp]
 * Height: 44dp.
 * Supports Undo and Raw<->Clean temporary chips with 5s expiry.
 */
class SuggestionStrip @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val handler = Handler(Looper.getMainLooper())

    private val clipboardIconView: TextView
    private val candidatesScrollView: HorizontalScrollView
    private val candidatesContainer: LinearLayout
    private val magicWriteIconView: TextView

    var onClipboardIconTapped: (() -> Unit)? = null
    var onMagicWriteIconTapped: (() -> Unit)? = null
    var onCandidateTapped: ((String) -> Unit)? = null
    var onCandidateLongPressed: ((String) -> Unit)? = null
    var onUndoTapped: (() -> Unit)? = null
    var onToggleTranscriptTapped: (() -> Unit)? = null

    var onCandidateSelected: ((String) -> Unit)?
        get() = onCandidateTapped
        set(value) { onCandidateTapped = value }

    var onMagicWriteTapped: (() -> Unit)?
        get() = onMagicWriteIconTapped
        set(value) { onMagicWriteIconTapped = value }

    var onVoiceToggleTapped: (() -> Unit)?
        get() = onToggleTranscriptTapped
        set(value) { onToggleTranscriptTapped = value }

    private var labelColor = Color.parseColor("#CDD6F4")
    private var stripBgColor = Color.parseColor("#1E1E2E")

    private var temporaryActionRunnable: Runnable? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(stripBgColor)

        // Fixed height: 44dp (Section E)
        val density = context.resources.displayMetrics.density
        val heightPx = (44 * density).toInt()
        val iconWidthPx = (40 * density).toInt()

        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, heightPx)

        // 1. [Clipboard icon 40dp]
        clipboardIconView = TextView(context).apply {
            text = "📋"
            textSize = 16f
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(iconWidthPx, LayoutParams.MATCH_PARENT)
            setOnClickListener { onClipboardIconTapped?.invoke() }
        }
        addView(clipboardIconView)

        // 2. Candidates scrollable center (Candidate 1, Candidate 2, Candidate 3)
        candidatesScrollView = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1.0f)
        }
        candidatesContainer = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
        }
        candidatesScrollView.addView(candidatesContainer)
        addView(candidatesScrollView)

        // 3. [Magic Write icon 40dp]
        magicWriteIconView = TextView(context).apply {
            text = "✨"
            textSize = 16f
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(iconWidthPx, LayoutParams.MATCH_PARENT)
            setOnClickListener { onMagicWriteIconTapped?.invoke() }
        }
        addView(magicWriteIconView)
    }

    fun applyThemeColors(stripBg: String, labelHex: String) {
        try {
            stripBgColor = Color.parseColor(stripBg)
            labelColor = Color.parseColor(labelHex)
            setBackgroundColor(stripBgColor)
            clipboardIconView.setTextColor(labelColor)
            magicWriteIconView.setTextColor(labelColor)
        } catch (e: Exception) {}
    }

    fun setCandidates(candidates: List<String>) {
        cancelTemporaryChip()
        candidatesContainer.removeAllViews()

        for (candidate in candidates.take(3)) {
            val tv = TextView(context).apply {
                text = candidate
                setTextColor(labelColor)
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(32, 0, 32, 0)
                layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
                setOnClickListener { onCandidateTapped?.invoke(candidate) }
                setOnLongClickListener {
                    onCandidateLongPressed?.invoke(candidate)
                    true
                }
            }
            candidatesContainer.addView(tv)
        }
    }

    // Section E: "Undo" chip shown for 5 seconds after Magic Write action
    fun showUndoChip() {
        cancelTemporaryChip()
        candidatesContainer.removeAllViews()

        val undoChip = TextView(context).apply {
            text = "↩ Undo"
            setTextColor(Color.parseColor("#F38BA8"))
            setBackgroundColor(Color.parseColor("#313244"))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(28, 8, 28, 8)
            setOnClickListener {
                onUndoTapped?.invoke()
                cancelTemporaryChip()
            }
        }
        candidatesContainer.addView(undoChip)

        temporaryActionRunnable = Runnable {
            candidatesContainer.removeAllViews()
        }.also { handler.postDelayed(it, 5000) }
    }

    // Section E: "Raw ↔ Clean" toggle chip shown for 5 seconds after Rambler commit
    fun showRawCleanToggleChip() {
        cancelTemporaryChip()
        candidatesContainer.removeAllViews()

        val toggleChip = TextView(context).apply {
            text = "Raw ↔ Clean"
            setTextColor(Color.parseColor("#89B4FA"))
            setBackgroundColor(Color.parseColor("#313244"))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(28, 8, 28, 8)
            setOnClickListener {
                onToggleTranscriptTapped?.invoke()
            }
        }
        candidatesContainer.addView(toggleChip)

        temporaryActionRunnable = Runnable {
            candidatesContainer.removeAllViews()
        }.also { handler.postDelayed(it, 5000) }
    }

    private fun cancelTemporaryChip() {
        temporaryActionRunnable?.let { handler.removeCallbacks(it) }
        temporaryActionRunnable = null
    }
}
