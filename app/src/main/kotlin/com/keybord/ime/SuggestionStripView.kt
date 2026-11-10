package com.keybord.ime

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.keybord.clipboard.ClipboardItem
import com.keybord.theme.BuiltInThemes
import com.keybord.theme.ThemeModel

/**
 * Suggestion Strip layout strictly conforming to PRD Section 4.3:
 * [Clipboard icon]  [Candidate 1]  [Candidate 2]  [Candidate 3]  [Settings icon]
 *
 * Supports inline recent clipboard chips (last 3, dismissible) and
 * candidate long-press deletion from personal history with 1-tap confirmation.
 */
class SuggestionStripView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private lateinit var clipboardBtn: TextView
    private lateinit var candidateContainer: LinearLayout
    private lateinit var settingsBtn: TextView
    private lateinit var recentClipsContainer: LinearLayout

    var onCandidateSelected: ((String) -> Unit)? = null
    var onCandidateDeleteRequested: ((String) -> Unit)? = null
    var onClipboardIconClicked: (() -> Unit)? = null
    var onSettingsIconClicked: (() -> Unit)? = null
    var onRecentClipDismissed: ((Long) -> Unit)? = null
    var onRecentClipSelected: ((String) -> Unit)? = null

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

        // Row 2: Standard PRD Suggestion Strip
        val stripRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (42 * dp).toInt())
        }

        // [Clipboard icon]
        clipboardBtn = TextView(context).apply {
            text = "📋"
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding((12 * dp).toInt(), 0, (12 * dp).toInt(), 0)
            setOnClickListener { onClipboardIconClicked?.invoke() }
        }

        // [Candidate 1] [Candidate 2] [Candidate 3]
        candidateContainer = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        }

        // [Settings icon]
        settingsBtn = TextView(context).apply {
            text = "⚙"
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding((12 * dp).toInt(), 0, (12 * dp).toInt(), 0)
            setOnClickListener { onSettingsIconClicked?.invoke() }
        }

        stripRow.addView(clipboardBtn)
        stripRow.addView(candidateContainer)
        stripRow.addView(settingsBtn)
        addView(stripRow)
    }

    private fun applyTheme() {
        setBackgroundColor(theme.suggestionStripBgColor)
        val textColor = theme.suggestionStripTextColor
        clipboardBtn.setTextColor(textColor)
        settingsBtn.setTextColor(textColor)

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
