package com.keybord.settings

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.keybord.ime.KeyPopPreviewView
import com.keybord.ime.KeyboardView
import com.keybord.ime.SuggestionStripView
import com.keybord.theme.KeyboardLayoutPrefs
import com.keybord.theme.PopPreviewSize
import com.keybord.theme.ThemeModel
import com.keybord.theme.ThemeRepository
import com.keybord.ui.UiKit

/**
 * Dedicated Keyboard Height, Spacing & Button Geometry Customisation Studio
 * with an interactive live keyboard preview (tap keys, test pop-ups,
 * toggle number row, resize keys/gaps, and test Space cursor swipe live).
 */
class KeyboardLayoutCustomizeActivity : Activity() {

    private lateinit var themeRepo: ThemeRepository
    private lateinit var layoutPrefs: KeyboardLayoutPrefs
    private lateinit var baseTheme: ThemeModel

    // Live interactive preview views
    private lateinit var previewStatsTv: TextView
    private lateinit var sandboxDisplayTv: TextView
    private lateinit var stripPreview: SuggestionStripView
    private lateinit var keyboardPreview: KeyboardView
    private lateinit var popPreview: KeyPopPreviewView
    private lateinit var controlsContainer: LinearLayout

    // Sandbox text state for interactive key testing
    private val sandboxText = StringBuilder("Namaste Keybord")
    private var sandboxCursor = sandboxText.length

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        themeRepo = ThemeRepository(this)
        layoutPrefs = KeyboardLayoutPrefs(this)
        baseTheme = themeRepo.getActiveTheme()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(UiKit.BG_CANVAS)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        UiKit.applyEdgeToEdgePadding(root, leftDp = 14f, topDp = 14f, rightDp = 14f, bottomDp = 14f)

        val resetBtn = UiKit.createPillButton(this, "Reset", filled = false, danger = false) {
            layoutPrefs.resetDefaults()
            buildControls()
            refreshLivePreview()
            Toast.makeText(this, "Layout & button settings reset to theme defaults", Toast.LENGTH_SHORT).show()
        }
        root.addView(
            UiKit.createHeaderBar(
                this,
                title = "Height & Buttons",
                subtitle = "Drag sliders below — tap & swipe the live preview",
                showBack = true,
                trailingView = resetBtn
            )
        )

        // Pinned Interactive Live Preview Card
        root.addView(buildLivePreviewCard())

        // Scrollable controls panel below live preview
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            isVerticalScrollBarEnabled = false
        }
        controlsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        buildControls()
        scrollView.addView(controlsContainer)
        root.addView(scrollView)

        val applyBtn = UiKit.createPillButton(this, "✓  Apply to Keyboard", filled = true) {
            themeRepo.setActiveTheme(baseTheme)
            Toast.makeText(this, "Keyboard height & button layout applied!", Toast.LENGTH_SHORT).show()
            finish()
        }.apply {
            val dp = resources.displayMetrics.density
            textSize = 15f
            setPadding(0, (13 * dp).toInt(), 0, (13 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (10 * dp).toInt() }
        }
        root.addView(applyBtn)

        setContentView(root)
        refreshLivePreview()
    }

    private fun buildLivePreviewCard(): LinearLayout {
        val dp = resources.displayMetrics.density
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = UiKit.roundedDrawable(this@KeyboardLayoutCustomizeActivity, UiKit.SURFACE_CARD, 18f, UiKit.BORDER_SUBTLE, 1f)
            clipChildren = true
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (10 * dp).toInt() }
        }

        // Sandbox header + test input readout
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((12 * dp).toInt(), (9 * dp).toInt(), (10 * dp).toInt(), (7 * dp).toInt())
        }

        previewStatsTv = TextView(this).apply {
            textSize = 11.5f
            setTextColor(UiKit.TEXT_SECONDARY)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val clearBtn = TextView(this).apply {
            text = "Clear"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(UiKit.ACCENT_PRIMARY)
            background = UiKit.roundedDrawable(this@KeyboardLayoutCustomizeActivity, UiKit.ACCENT_SOFT, 8f)
            setPadding((8 * dp).toInt(), (3 * dp).toInt(), (8 * dp).toInt(), (3 * dp).toInt())
            setOnClickListener {
                sandboxText.clear()
                sandboxCursor = 0
                renderSandboxText()
            }
        }
        topRow.addView(previewStatsTv)
        topRow.addView(clearBtn)
        card.addView(topRow)

        sandboxDisplayTv = TextView(this).apply {
            textSize = 14f
            setTextColor(UiKit.TEXT_PRIMARY)
            setSingleLine(true)
            background = UiKit.roundedDrawable(this@KeyboardLayoutCustomizeActivity, UiKit.SURFACE_INPUT, 10f, UiKit.BORDER_SUBTLE, 1f)
            setPadding((10 * dp).toInt(), (6 * dp).toInt(), (10 * dp).toInt(), (6 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = (10 * dp).toInt()
                rightMargin = (10 * dp).toInt()
                bottomMargin = (8 * dp).toInt()
            }
        }
        renderSandboxText()
        card.addView(sandboxDisplayTv)

        stripPreview = SuggestionStripView(this).apply {
            setCandidates(listOf("the", "bhai", "keybord"))
            onCandidateSelected = { word ->
                insertSandboxText("$word ")
            }
        }
        card.addView(stripPreview)

        val kbFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        keyboardPreview = KeyboardView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            onKeyTap = { key -> handleSandboxKey(key) }
            onDeleteRepeatChar = { deleteSandboxChar() }
            onDeleteWord = { deleteSandboxWord() }
            onSpaceSwipeCursorStep = { delta ->
                val count = kotlin.math.abs(delta).coerceAtMost(8)
                val step = if (delta < 0) -1 else 1
                sandboxCursor = (sandboxCursor + step * count).coerceIn(0, sandboxText.length)
                renderSandboxText()
            }
        }
        popPreview = KeyPopPreviewView(this).apply {
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        keyboardPreview.popPreviewView = popPreview
        keyboardPreview.setOnTouchListener { _, event ->
            keyboardPreview.handleTouchEvent(event)
            true
        }

        kbFrame.addView(keyboardPreview)
        kbFrame.addView(popPreview)
        card.addView(kbFrame)
        return card
    }

    private fun buildControls() {
        controlsContainer.removeAllViews()
        val dp = resources.displayMetrics.density
        val currentEffective = layoutPrefs.applyTo(baseTheme)

        // 1. Quick Geometry & Style Presets
        controlsContainer.addView(UiKit.createSectionLabel(this, "Quick Layout Presets"))
        val presetsScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (8 * dp).toInt() }
        }
        val presetRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        fun addPresetChip(label: String, applyFn: () -> Unit) {
            val btn = UiKit.createPillButton(this, label, filled = false) {
                applyFn()
                buildControls()
                refreshLivePreview()
            }.apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = (8 * dp).toInt() }
            }
            presetRow.addView(btn)
        }

        addPresetChip("Standard") {
            layoutPrefs.keyHeightDp = 54f
            layoutPrefs.bottomPaddingDp = 12f
            layoutPrefs.cornerRadiusDp = 8f
            layoutPrefs.gapHorizontalDp = 4f
            layoutPrefs.gapVerticalDp = 6f
            layoutPrefs.borderWidthDp = 1f
            layoutPrefs.labelSizeSp = 16f
        }
        addPresetChip("Compact") {
            layoutPrefs.keyHeightDp = 44f
            layoutPrefs.bottomPaddingDp = 6f
            layoutPrefs.cornerRadiusDp = 7f
            layoutPrefs.gapHorizontalDp = 3f
            layoutPrefs.gapVerticalDp = 4f
            layoutPrefs.labelSizeSp = 14f
        }
        addPresetChip("Tall Comfort") {
            layoutPrefs.keyHeightDp = 66f
            layoutPrefs.bottomPaddingDp = 16f
            layoutPrefs.cornerRadiusDp = 11f
            layoutPrefs.gapHorizontalDp = 5f
            layoutPrefs.gapVerticalDp = 7f
            layoutPrefs.labelSizeSp = 18f
        }
        addPresetChip("Pill Buttons") {
            layoutPrefs.cornerRadiusDp = 24f
            layoutPrefs.gapHorizontalDp = 5f
            layoutPrefs.gapVerticalDp = 6f
            layoutPrefs.borderWidthDp = 1f
        }
        addPresetChip("Sharp Box") {
            layoutPrefs.cornerRadiusDp = 2f
            layoutPrefs.gapHorizontalDp = 2f
            layoutPrefs.gapVerticalDp = 3f
            layoutPrefs.borderWidthDp = 1f
        }
        presetsScroll.addView(presetRow)
        controlsContainer.addView(presetsScroll)

        // 2. Keyboard Height & Key Spacing
        controlsContainer.addView(UiKit.createSectionLabel(this, "Keyboard Height & Spacing"))
        controlsContainer.addView(
            UiKit.createSliderCard(
                this, "Key Height", " dp", currentEffective.keyHeightDp.toInt(), 36, 82
            ) { v ->
                layoutPrefs.keyHeightDp = v.toFloat()
                refreshLivePreview()
            }
        )
        controlsContainer.addView(
            UiKit.createSliderCard(
                this, "Bottom Chin Padding", " dp", currentEffective.bottomPaddingDp.toInt(), 0, 44
            ) { v ->
                layoutPrefs.bottomPaddingDp = v.toFloat()
                refreshLivePreview()
            }
        )
        controlsContainer.addView(
            UiKit.createSliderCard(
                this, "Horizontal Button Gap", " dp", currentEffective.keyGapHorizontalDp.toInt(), 0, 10
            ) { v ->
                layoutPrefs.gapHorizontalDp = v.toFloat()
                refreshLivePreview()
            }
        )
        controlsContainer.addView(
            UiKit.createSliderCard(
                this, "Vertical Row Gap", " dp", currentEffective.keyGapVerticalDp.toInt(), 0, 10
            ) { v ->
                layoutPrefs.gapVerticalDp = v.toFloat()
                refreshLivePreview()
            }
        )

        // 3. Button Appearance & Pop Preview
        controlsContainer.addView(UiKit.createSectionLabel(this, "Button Shape & Pop-up"))
        controlsContainer.addView(
            UiKit.createSliderCard(
                this, "Button Corner Radius", " dp", currentEffective.keyCornerRadiusDp.toInt(), 0, 26
            ) { v ->
                layoutPrefs.cornerRadiusDp = v.toFloat()
                refreshLivePreview()
            }
        )
        controlsContainer.addView(
            UiKit.createSliderCard(
                this, "Button Border Stroke", " dp", currentEffective.keyBorderWidthDp.toInt(), 0, 4
            ) { v ->
                layoutPrefs.borderWidthDp = v.toFloat()
                refreshLivePreview()
            }
        )
        controlsContainer.addView(
            UiKit.createSliderCard(
                this, "Key Label Size", " sp", currentEffective.keyLabelSizeSp.toInt(), 11, 22
            ) { v ->
                layoutPrefs.labelSizeSp = v.toFloat()
                refreshLivePreview()
            }
        )
        controlsContainer.addView(
            UiKit.createSwitchCard(
                this,
                title = "Key Pop-up Preview",
                subtitle = "Enlarged floating badge when pressing keys",
                checked = layoutPrefs.popPreviewEnabled
            ) { checked ->
                layoutPrefs.popPreviewEnabled = checked
                refreshLivePreview()
            }
        )
        controlsContainer.addView(buildPopSizeSelector())

        // 4. Keys & Rows
        controlsContainer.addView(UiKit.createSectionLabel(this, "Keys & Extra Row"))
        controlsContainer.addView(
            UiKit.createSwitchCard(
                this,
                title = "Dedicated Number Row",
                subtitle = "Always show 1–0 row above letters",
                checked = layoutPrefs.showNumberRow
            ) { checked ->
                layoutPrefs.showNumberRow = checked
                refreshLivePreview()
            }
        )
        controlsContainer.addView(
            UiKit.createSwitchCard(
                this,
                title = "Secondary Symbol Hints",
                subtitle = "Display long-press digits & symbols in key corners",
                checked = layoutPrefs.showSecondarySymbols
            ) { checked ->
                layoutPrefs.showSecondarySymbols = checked
                refreshLivePreview()
            }
        )

        // 5. One-Handed & Tactile
        controlsContainer.addView(UiKit.createSectionLabel(this, "One-Handed Mode & Haptics"))
        controlsContainer.addView(
            UiKit.createSwitchCard(
                this,
                title = "One-Handed Layout",
                subtitle = "Dock keyboard narrower toward left or right thumb",
                checked = layoutPrefs.oneHandedEnabled
            ) { checked ->
                layoutPrefs.oneHandedEnabled = checked
                refreshLivePreview()
            }
        )
        controlsContainer.addView(buildOneHandedSideSelector())
        controlsContainer.addView(
            UiKit.createSliderCard(
                this, "One-Handed Width", "%", layoutPrefs.oneHandedWidthPercent.toInt(), 68, 96
            ) { v ->
                layoutPrefs.oneHandedWidthPercent = v.toFloat()
                refreshLivePreview()
            }
        )
        controlsContainer.addView(
            UiKit.createSliderCard(
                this, "Key Vibration Strength", " ms", layoutPrefs.vibrationDurationMs.toInt(), 0, 40
            ) { v ->
                layoutPrefs.vibrationDurationMs = v.toLong()
                refreshLivePreview()
            }
        )
    }

    private fun buildPopSizeSelector(): LinearLayout {
        val dp = resources.displayMetrics.density
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = UiKit.roundedDrawable(this@KeyboardLayoutCustomizeActivity, UiKit.SURFACE_CARD, 14f, UiKit.BORDER_SUBTLE, 1f)
            setPadding((14 * dp).toInt(), (10 * dp).toInt(), (14 * dp).toInt(), (10 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (10 * dp).toInt() }
        }
        val label = TextView(this).apply {
            text = "Pop-up Size"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(UiKit.TEXT_PRIMARY)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        card.addView(label)

        val chips = mutableListOf<Pair<PopPreviewSize, TextView>>()
        fun updateChipStyles() {
            val activeOrd = layoutPrefs.popPreviewSizeOrdinal
            for ((sz, tv) in chips) {
                val active = sz.ordinal == activeOrd
                tv.setTextColor(if (active) Color.WHITE else UiKit.TEXT_SECONDARY)
                tv.background = UiKit.roundedDrawable(
                    this,
                    if (active) UiKit.ACCENT_PRIMARY else UiKit.SURFACE_ELEVATED,
                    10f
                )
            }
        }
        for (sz in PopPreviewSize.values()) {
            val tv = TextView(this).apply {
                text = sz.name.lowercase().replaceFirstChar { it.uppercase() }
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setPadding((10 * dp).toInt(), (5 * dp).toInt(), (10 * dp).toInt(), (5 * dp).toInt())
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = (6 * dp).toInt() }
                setOnClickListener {
                    layoutPrefs.popPreviewSizeOrdinal = sz.ordinal
                    updateChipStyles()
                    refreshLivePreview()
                }
            }
            chips.add(sz to tv)
            card.addView(tv)
        }
        updateChipStyles()
        return card
    }

    private fun buildOneHandedSideSelector(): LinearLayout {
        val dp = resources.displayMetrics.density
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = UiKit.roundedDrawable(this@KeyboardLayoutCustomizeActivity, UiKit.SURFACE_CARD, 14f, UiKit.BORDER_SUBTLE, 1f)
            setPadding((14 * dp).toInt(), (10 * dp).toInt(), (14 * dp).toInt(), (10 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (8 * dp).toInt() }
        }
        val label = TextView(this).apply {
            text = "One-Handed Side"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(UiKit.TEXT_PRIMARY)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        card.addView(label)

        lateinit var leftBtn: TextView
        lateinit var rightBtn: TextView
        fun syncBtns() {
            val isRight = layoutPrefs.oneHandedGravityRight
            leftBtn.setTextColor(if (!isRight) Color.WHITE else UiKit.TEXT_SECONDARY)
            leftBtn.background = UiKit.roundedDrawable(this, if (!isRight) UiKit.ACCENT_PRIMARY else UiKit.SURFACE_ELEVATED, 10f)
            rightBtn.setTextColor(if (isRight) Color.WHITE else UiKit.TEXT_SECONDARY)
            rightBtn.background = UiKit.roundedDrawable(this, if (isRight) UiKit.ACCENT_PRIMARY else UiKit.SURFACE_ELEVATED, 10f)
        }

        leftBtn = TextView(this).apply {
            text = "Left"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((12 * dp).toInt(), (5 * dp).toInt(), (12 * dp).toInt(), (5 * dp).toInt())
            setOnClickListener {
                layoutPrefs.oneHandedGravityRight = false
                syncBtns()
                refreshLivePreview()
            }
        }
        rightBtn = TextView(this).apply {
            text = "Right"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((12 * dp).toInt(), (5 * dp).toInt(), (12 * dp).toInt(), (5 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = (6 * dp).toInt() }
            setOnClickListener {
                layoutPrefs.oneHandedGravityRight = true
                syncBtns()
                refreshLivePreview()
            }
        }
        card.addView(leftBtn)
        card.addView(rightBtn)
        syncBtns()
        return card
    }

    private fun refreshLivePreview() {
        val effective = layoutPrefs.applyTo(baseTheme)
        stripPreview.theme = effective
        keyboardPreview.theme = effective
        keyboardPreview.showNumberRow = layoutPrefs.showNumberRow
        keyboardPreview.showSecondarySymbols = layoutPrefs.showSecondarySymbols
        keyboardPreview.vibrationDurationMs = layoutPrefs.vibrationDurationMs
        popPreview.theme = effective

        val rowsLabel = if (layoutPrefs.showNumberRow) "5 rows" else "4 rows"
        previewStatsTv.text =
            "Key ${effective.keyHeightDp.toInt()}dp • Radius ${effective.keyCornerRadiusDp.toInt()}dp • $rowsLabel"
    }

    private fun handleSandboxKey(key: String) {
        when (key) {
            "⌫" -> deleteSandboxChar()
            "⇧", "⬆", "⇪" -> {
                keyboardPreview.shiftState = when (keyboardPreview.shiftState) {
                    KeyboardView.ShiftState.OFF -> KeyboardView.ShiftState.SHIFTED
                    KeyboardView.ShiftState.SHIFTED -> KeyboardView.ShiftState.CAPS_LOCK
                    KeyboardView.ShiftState.CAPS_LOCK -> KeyboardView.ShiftState.OFF
                }
            }
            "?123" -> keyboardPreview.layer = KeyboardView.KeyboardLayer.SYMBOLS
            "ABC" -> keyboardPreview.layer = KeyboardView.KeyboardLayer.ALPHA
            "=\\<" -> keyboardPreview.layer = KeyboardView.KeyboardLayer.EXTENDED_SYMBOLS
            "Enter" -> insertSandboxText(" ")
            else -> {
                val out = if (keyboardPreview.shiftState != KeyboardView.ShiftState.OFF && key.length == 1) {
                    key.uppercase()
                } else {
                    key
                }
                insertSandboxText(out)
                if (keyboardPreview.shiftState == KeyboardView.ShiftState.SHIFTED) {
                    keyboardPreview.shiftState = KeyboardView.ShiftState.OFF
                }
            }
        }
    }

    private fun insertSandboxText(txt: String) {
        sandboxCursor = sandboxCursor.coerceIn(0, sandboxText.length)
        sandboxText.insert(sandboxCursor, txt)
        sandboxCursor += txt.length
        renderSandboxText()
    }

    private fun deleteSandboxChar() {
        if (sandboxCursor > 0 && sandboxText.isNotEmpty()) {
            sandboxCursor = sandboxCursor.coerceIn(1, sandboxText.length)
            sandboxText.deleteCharAt(sandboxCursor - 1)
            sandboxCursor--
            renderSandboxText()
        }
    }

    private fun deleteSandboxWord() {
        if (sandboxCursor <= 0 || sandboxText.isEmpty()) return
        var i = sandboxCursor.coerceIn(0, sandboxText.length)
        while (i > 0 && sandboxText[i - 1].isWhitespace()) i--
        while (i > 0 && !sandboxText[i - 1].isWhitespace()) i--
        sandboxText.delete(i, sandboxCursor)
        sandboxCursor = i
        renderSandboxText()
    }

    private fun renderSandboxText() {
        val pos = sandboxCursor.coerceIn(0, sandboxText.length)
        val preview = buildString {
            append(sandboxText, 0, pos)
            append('│')
            append(sandboxText, pos, sandboxText.length)
        }
        sandboxDisplayTv.text = preview
    }
}
