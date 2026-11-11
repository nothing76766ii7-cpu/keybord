package com.keybord.ime

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.keybord.engine.ai.AiWriterClient
import com.keybord.theme.BuiltInThemes
import com.keybord.theme.KeyboardEffectEngine
import com.keybord.theme.ThemeModel
import com.keybord.ui.UiKit

/**
 * Interactive docked AI Writer Composer panel rendered directly above KeyboardView.
 * Users can type instructions using the live physical keyboard keys below (prompt-capture mode),
 * pull selected/app text, choose format & tone, generate/rewrite asynchronously, preview loading/error/success,
 * and Replace / Insert / Append / Copy directly into the active Android text field.
 */
class AiWriterPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    var theme: ThemeModel = BuiltInThemes.SKEUO_DARK_GRAPHITE
        set(value) {
            field = value
            applyTheme()
        }

    var isPromptCaptureActive: Boolean = false
        private set

    var onPromptFocusChanged: ((Boolean) -> Unit)? = null
    var onInsertText: ((String) -> Unit)? = null
    var onReplaceSelectedOrText: ((text: String, hadSelection: Boolean) -> Unit)? = null
    var onAppendText: ((String) -> Unit)? = null
    var onCopyText: ((String) -> Unit)? = null
    var onRequestAppSelectionOrText: (() -> Pair<String, Boolean>?)? = null
    var onRequestClipboardText: (() -> String?)? = null
    var onOpenApiSettingsClicked: (() -> Unit)? = null
    var onCloseClicked: (() -> Unit)? = null

    private val aiClient = AiWriterClient(context)
    private val promptBuffer = StringBuilder()
    private var capturedContextText: String = ""
    private var capturedHadSelection: Boolean = false
    private var selectedFormat = AiWriterClient.WritingFormat.AUTO
    private var selectedAction = AiWriterClient.WritingAction.COMPOSE
    private var selectedTone = AiWriterClient.WritingTone.NATURAL

    private var generatedOutput: String = ""
    private var statusMessage: String = ""
    private var isLoading: Boolean = false
    private var isErrorState: Boolean = false
    private var showApiSetupShortcut: Boolean = false

    private lateinit var statusPill: TextView
    private lateinit var apiSetupHeaderBtn: TextView
    private lateinit var closeBtn: TextView
    private lateinit var promptBox: LinearLayout
    private lateinit var promptTv: TextView
    private lateinit var promptHintBadge: TextView
    private lateinit var contextPreviewRow: LinearLayout
    private lateinit var contextTv: TextView
    private lateinit var contextClearBtn: TextView
    private lateinit var actionRow: LinearLayout
    private lateinit var formatRow: LinearLayout
    private lateinit var toneRow: LinearLayout
    private lateinit var generateBtn: TextView
    private lateinit var pullBtn: TextView
    private lateinit var pastePromptBtn: TextView
    private lateinit var clearPromptBtn: TextView

    private lateinit var resultCard: LinearLayout
    private lateinit var resultHeaderTv: TextView
    private lateinit var resultScroll: ScrollView
    private lateinit var resultTv: TextView
    private lateinit var resultButtonsRow: LinearLayout
    private lateinit var replaceBtn: TextView
    private lateinit var insertBtn: TextView
    private lateinit var appendBtn: TextView
    private lateinit var copyBtn: TextView
    private lateinit var configErrorBtn: TextView

    init {
        orientation = VERTICAL
        setupViews()
        applyTheme()
        syncUiState()
    }

    private fun setupViews() {
        val dp = resources.displayMetrics.density
        setPadding((10 * dp).toInt(), (8 * dp).toInt(), (10 * dp).toInt(), (8 * dp).toInt())

        // 1. Header Row: Title + Engine status pill + API Config + Close
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (6 * dp).toInt())
        }
        val titleTv = TextView(context).apply {
            text = "✨ AI Writer"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(UiKit.TEXT_PRIMARY)
            layoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = (6 * dp).toInt()
            }
        }
        statusPill = TextView(context).apply {
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((8 * dp).toInt(), (3 * dp).toInt(), (8 * dp).toInt(), (3 * dp).toInt())
            layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (6 * dp).toInt()
            }
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        }
        apiSetupHeaderBtn = TextView(context).apply {
            text = "⚙ API"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((9 * dp).toInt(), (4 * dp).toInt(), (9 * dp).toInt(), (4 * dp).toInt())
            layoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = (6 * dp).toInt()
            }
            setOnClickListener { onOpenApiSettingsClicked?.invoke() }
        }
        closeBtn = TextView(context).apply {
            text = "✕"
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding((10 * dp).toInt(), (4 * dp).toInt(), (10 * dp).toInt(), (4 * dp).toInt())
            setOnClickListener {
                setPromptFocus(false)
                onCloseClicked?.invoke()
            }
        }
        header.addView(titleTv)
        header.addView(statusPill)
        header.addView(apiSetupHeaderBtn)
        header.addView(closeBtn)
        addView(header)

        // 2. Prompt / Instruction Box (tap to type with the live keyboard below!)
        promptBox = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((10 * dp).toInt(), (7 * dp).toInt(), (8 * dp).toInt(), (7 * dp).toInt())
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (6 * dp).toInt()
            }
            setOnClickListener {
                setPromptFocus(!isPromptCaptureActive)
            }
        }
        promptTv = TextView(context).apply {
            textSize = 13f
            maxLines = 3
            layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (6 * dp).toInt()
            }
        }
        promptHintBadge = TextView(context).apply {
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((7 * dp).toInt(), (3 * dp).toInt(), (7 * dp).toInt(), (3 * dp).toInt())
        }
        promptBox.addView(promptTv)
        promptBox.addView(promptHintBadge)
        addView(promptBox)

        // 3. Pulled/Selected App Text Context Strip
        contextPreviewRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding((8 * dp).toInt(), (4 * dp).toInt(), (8 * dp).toInt(), (4 * dp).toInt())
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (6 * dp).toInt()
            }
        }
        contextTv = TextView(context).apply {
            textSize = 11.5f
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        contextClearBtn = TextView(context).apply {
            text = "✕"
            textSize = 12f
            setPadding((8 * dp).toInt(), (2 * dp).toInt(), (4 * dp).toInt(), (2 * dp).toInt())
            setOnClickListener {
                capturedContextText = ""
                capturedHadSelection = false
                syncUiState()
            }
        }
        contextPreviewRow.addView(contextTv)
        contextPreviewRow.addView(contextClearBtn)
        addView(contextPreviewRow)

        // 4. Quick Action + Format Chips Row
        val actScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (4 * dp).toInt()
            }
        }
        actionRow = LinearLayout(context).apply { orientation = HORIZONTAL }
        actScroll.addView(actionRow)
        addView(actScroll)

        // 5. Format + Tone Chips Row
        val toneScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (6 * dp).toInt()
            }
        }
        val formatAndToneRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        formatRow = LinearLayout(context).apply { orientation = HORIZONTAL }
        toneRow = LinearLayout(context).apply { orientation = HORIZONTAL }
        formatAndToneRow.addView(formatRow)
        formatAndToneRow.addView(
            View(context).apply {
                layoutParams = LayoutParams((1 * dp).toInt().coerceAtLeast(1), (18 * dp).toInt()).apply {
                    marginStart = (4 * dp).toInt()
                    marginEnd = (6 * dp).toInt()
                }
                setBackgroundColor(UiKit.DIVIDER_HAIRLINE)
            }
        )
        formatAndToneRow.addView(toneRow)
        toneScroll.addView(formatAndToneRow)
        addView(toneScroll)

        // 6. Controls Bar: [Pull Text] [Paste] [Clear] [Generate ✨]
        val ctrlRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        fun smallBtn(label: String, onClick: () -> Unit): TextView {
            return TextView(context).apply {
                text = label
                textSize = 11.5f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding((9 * dp).toInt(), (6 * dp).toInt(), (9 * dp).toInt(), (6 * dp).toInt())
                layoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = (6 * dp).toInt()
                }
                setOnClickListener { onClick() }
            }
        }
        pullBtn = smallBtn("📄 Pull") {
            pullAppContext(preferRewriteAction = true)
        }
        pastePromptBtn = smallBtn("📋 Paste") {
            val clip = onRequestClipboardText?.invoke()?.trim().orEmpty()
            if (clip.isNotEmpty()) {
                if (promptBuffer.isNotEmpty() && !promptBuffer.endsWith(" ")) promptBuffer.append(' ')
                promptBuffer.append(clip)
                setPromptFocus(true)
                syncUiState()
            }
        }
        clearPromptBtn = smallBtn("⌫ Clear") {
            promptBuffer.clear()
            syncUiState()
        }
        generateBtn = TextView(context).apply {
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding((12 * dp).toInt(), (7 * dp).toInt(), (12 * dp).toInt(), (7 * dp).toInt())
            layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                if (isLoading) {
                    aiClient.cancelPending()
                    isLoading = false
                    statusMessage = "Request cancelled"
                    isErrorState = false
                    syncUiState()
                } else {
                    triggerGeneration()
                }
            }
        }
        ctrlRow.addView(pullBtn)
        ctrlRow.addView(pastePromptBtn)
        ctrlRow.addView(clearPromptBtn)
        ctrlRow.addView(generateBtn)
        addView(ctrlRow)

        // 7. Result / Status Preview Card
        resultCard = LinearLayout(context).apply {
            orientation = VERTICAL
            visibility = View.GONE
            setPadding((10 * dp).toInt(), (8 * dp).toInt(), (10 * dp).toInt(), (8 * dp).toInt())
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (7 * dp).toInt()
            }
        }
        val resHeadRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (4 * dp).toInt())
        }
        resultHeaderTv = TextView(context).apply {
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        configErrorBtn = TextView(context).apply {
            text = "⚙ Configure Key"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            visibility = View.GONE
            setPadding((8 * dp).toInt(), (3 * dp).toInt(), (8 * dp).toInt(), (3 * dp).toInt())
            setOnClickListener { onOpenApiSettingsClicked?.invoke() }
        }
        resHeadRow.addView(resultHeaderTv)
        resHeadRow.addView(configErrorBtn)
        resultCard.addView(resHeadRow)

        resultScroll = ScrollView(context).apply {
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            isVerticalScrollBarEnabled = true
        }
        resultTv = TextView(context).apply {
            textSize = 12.5f
            setPadding(0, (2 * dp).toInt(), 0, (6 * dp).toInt())
        }
        resultScroll.addView(resultTv)
        resultCard.addView(resultScroll)

        resultButtonsRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            visibility = View.GONE
        }
        fun outBtn(label: String, onClick: () -> Unit): TextView {
            return TextView(context).apply {
                text = label
                textSize = 11.5f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding((6 * dp).toInt(), (6 * dp).toInt(), (6 * dp).toInt(), (6 * dp).toInt())
                layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = (5 * dp).toInt()
                }
                setOnClickListener { onClick() }
            }
        }
        replaceBtn = outBtn("↻ Replace") {
            if (generatedOutput.isNotBlank()) {
                setPromptFocus(false)
                onReplaceSelectedOrText?.invoke(generatedOutput, capturedHadSelection)
            }
        }
        insertBtn = outBtn("↳ Insert") {
            if (generatedOutput.isNotBlank()) {
                setPromptFocus(false)
                onInsertText?.invoke(generatedOutput)
            }
        }
        appendBtn = outBtn("+ Append") {
            if (generatedOutput.isNotBlank()) {
                setPromptFocus(false)
                onAppendText?.invoke(generatedOutput)
            }
        }
        copyBtn = outBtn("📋 Copy") {
            if (generatedOutput.isNotBlank()) {
                onCopyText?.invoke(generatedOutput)
            }
        }.apply {
            layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        resultButtonsRow.addView(replaceBtn)
        resultButtonsRow.addView(insertBtn)
        resultButtonsRow.addView(appendBtn)
        resultButtonsRow.addView(copyBtn)
        resultCard.addView(resultButtonsRow)

        addView(resultCard)
    }

    fun onPanelOpened() {
        pullAppContext(preferRewriteAction = false)
        if (promptBuffer.isEmpty() && capturedContextText.isBlank()) {
            setPromptFocus(true)
        }
        syncUiState()
    }

    fun onPanelHidden() {
        setPromptFocus(false)
        aiClient.cancelPending()
        isLoading = false
        syncUiState()
    }

    fun setPromptFocus(focused: Boolean) {
        if (isPromptCaptureActive != focused) {
            isPromptCaptureActive = focused
            onPromptFocusChanged?.invoke(focused)
            applyTheme()
            syncUiState()
        }
    }

    fun appendPromptText(text: String) {
        if (text.isEmpty()) return
        promptBuffer.append(text)
        syncUiState()
    }

    fun handlePromptBackspace() {
        if (promptBuffer.isEmpty()) return
        val delCount = DeleteWordHelper.computeSingleCharDeleteCount(promptBuffer)
        val start = (promptBuffer.length - delCount).coerceAtLeast(0)
        promptBuffer.delete(start, promptBuffer.length)
        syncUiState()
    }

    fun handlePromptWordDelete() {
        if (promptBuffer.isEmpty()) return
        val len = DeleteWordHelper.computeWordDeleteLength(promptBuffer)
        if (len > 0) {
            promptBuffer.delete((promptBuffer.length - len).coerceAtLeast(0), promptBuffer.length)
            syncUiState()
        }
    }

    fun triggerGeneration() {
        val currentInstr = promptBuffer.toString().trim()
        val req = AiWriterClient.AiRequest(
            instruction = currentInstr,
            sourceText = capturedContextText.trim(),
            format = selectedFormat,
            action = selectedAction,
            tone = selectedTone
        )
        if (req.instruction.isEmpty() && req.sourceText.isEmpty()) {
            isErrorState = true
            showApiSetupShortcut = false
            statusMessage = "Type a prompt or pull text from the app first."
            generatedOutput = ""
            syncUiState()
            return
        }

        val accepted = aiClient.generateAsync(req) { outcome ->
            isLoading = false
            when (outcome) {
                is AiWriterClient.AiOutcome.Success -> {
                    generatedOutput = outcome.text
                    isErrorState = false
                    showApiSetupShortcut = false
                    statusMessage = "✓ ${outcome.engineLabel}"
                    setPromptFocus(false)
                }
                is AiWriterClient.AiOutcome.Error -> {
                    isErrorState = true
                    showApiSetupShortcut = outcome.needsConfig
                    statusMessage = outcome.message
                }
            }
            syncUiState()
        }

        if (accepted) {
            isLoading = true
            isErrorState = false
            showApiSetupShortcut = false
            statusMessage = "Writing with AI…"
            syncUiState()
        }
    }

    fun shutdown() {
        aiClient.shutdown()
    }

    private fun pullAppContext(preferRewriteAction: Boolean) {
        val pulled = onRequestAppSelectionOrText?.invoke()
        if (pulled != null && pulled.first.isNotBlank()) {
            capturedContextText = pulled.first.trim()
            capturedHadSelection = pulled.second
            if (preferRewriteAction && selectedAction == AiWriterClient.WritingAction.COMPOSE) {
                selectedAction = AiWriterClient.WritingAction.IMPROVE
            }
        }
        syncUiState()
    }

    private fun renderChips() {
        val dp = resources.displayMetrics.density
        val accent = theme.effectColors.firstOrNull() ?: theme.accentKeyColor ?: theme.trailColor
        val bg = theme.keyboardBackground.colorStart
        val chipFill = KeyboardEffectEngine.compositeOver(theme.keyFaceColor.primaryColor, bg)

        actionRow.removeAllViews()
        for (act in AiWriterClient.WritingAction.values()) {
            val active = (act == selectedAction)
            actionRow.addView(
                makeChip(act.label, active, accent, chipFill, dp) {
                    selectedAction = act
                    renderChips()
                }
            )
        }

        formatRow.removeAllViews()
        for (fmt in AiWriterClient.WritingFormat.values()) {
            val active = (fmt == selectedFormat)
            formatRow.addView(
                makeChip(fmt.label, active, accent, chipFill, dp) {
                    selectedFormat = fmt
                    renderChips()
                }
            )
        }

        toneRow.removeAllViews()
        for (tone in AiWriterClient.WritingTone.values()) {
            val active = (tone == selectedTone)
            toneRow.addView(
                makeChip(tone.label, active, accent, chipFill, dp) {
                    selectedTone = tone
                    if (capturedContextText.isNotBlank() && selectedAction == AiWriterClient.WritingAction.COMPOSE) {
                        selectedAction = AiWriterClient.WritingAction.REWRITE
                    }
                    renderChips()
                }
            )
        }
    }

    private fun makeChip(
        label: String,
        active: Boolean,
        accent: Int,
        chipFill: Int,
        dp: Float,
        onClick: () -> Unit
    ): TextView {
        return TextView(context).apply {
            text = label
            textSize = 11f
            typeface = if (active) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            setTextColor(if (active) Color.WHITE else theme.keyLabelColorPrimary)
            background = if (active) {
                UiKit.skeuoButtonDrawable(context, accent, 10f)
            } else {
                UiKit.skeuoCardDrawable(context, chipFill, 10f, theme.keyBorderColor, 1f)
            }
            setPadding((9 * dp).toInt(), (4 * dp).toInt(), (9 * dp).toInt(), (4 * dp).toInt())
            layoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = (5 * dp).toInt()
            }
            setOnClickListener { onClick() }
        }
    }

    private fun syncUiState() {
        val cfg = aiClient.loadConfig()
        val accent = theme.effectColors.firstOrNull() ?: theme.accentKeyColor ?: theme.trailColor

        statusPill.text = if (cfg.isCloudConfigured) {
            "● ${cfg.model}"
        } else {
            "● Offline Rewriter"
        }
        statusPill.setTextColor(if (cfg.isCloudConfigured) UiKit.SUCCESS_FG else UiKit.WARNING_FG)
        statusPill.background = UiKit.skeuoWellDrawable(
            context,
            if (cfg.isCloudConfigured) UiKit.SUCCESS_BG else UiKit.WARNING_BG,
            999f,
            if (cfg.isCloudConfigured) UiKit.SUCCESS_BORDER else theme.keyBorderColor
        )

        if (promptBuffer.isEmpty()) {
            promptTv.text = if (isPromptCaptureActive) {
                "Type your writing prompt on the keys below… ▏"
            } else {
                "Tap here to type prompt (email, message, reply, caption)…"
            }
            promptTv.setTextColor(theme.keyLabelColorSecondary)
        } else {
            promptTv.text = if (isPromptCaptureActive) "${promptBuffer}▏" else promptBuffer.toString()
            promptTv.setTextColor(theme.keyLabelColorPrimary)
        }

        promptHintBadge.text = if (isPromptCaptureActive) "Typing Prompt" else "Tap to Type"
        promptHintBadge.setTextColor(if (isPromptCaptureActive) Color.WHITE else accent)
        promptHintBadge.background = UiKit.roundedDrawable(
            context,
            if (isPromptCaptureActive) accent else Color.argb(38, Color.red(accent), Color.green(accent), Color.blue(accent)),
            8f
        )

        if (capturedContextText.isNotBlank()) {
            contextPreviewRow.visibility = View.VISIBLE
            val prefix = if (capturedHadSelection) "Selected: " else "App Draft: "
            contextTv.text = "$prefix${capturedContextText.replace("\n", " ")}"
        } else {
            contextPreviewRow.visibility = View.GONE
        }

        generateBtn.text = if (isLoading) "⏳ Writing… (Tap to Cancel)" else "✨ Generate"
        generateBtn.background = UiKit.skeuoButtonDrawable(
            context,
            if (isLoading) UiKit.SURFACE_ELEVATED else accent,
            11f
        )

        val showResult = isLoading || isErrorState || generatedOutput.isNotBlank() || statusMessage.isNotBlank()
        resultCard.visibility = if (showResult) View.VISIBLE else View.GONE
        if (showResult) {
            resultHeaderTv.text = statusMessage.ifBlank { "Generated Draft" }
            resultHeaderTv.setTextColor(
                when {
                    isErrorState -> UiKit.DANGER_FG
                    isLoading -> accent
                    else -> UiKit.SUCCESS_FG
                }
            )
            configErrorBtn.visibility = if (isErrorState && showApiSetupShortcut) View.VISIBLE else View.GONE
            if (generatedOutput.isNotBlank() && !isLoading) {
                resultScroll.visibility = View.VISIBLE
                resultTv.text = generatedOutput
                resultTv.setTextColor(theme.keyLabelColorPrimary)
                resultButtonsRow.visibility = View.VISIBLE
                val dp = resources.displayMetrics.density
                val maxResultH = (86 * dp).toInt()
                resultScroll.layoutParams = (resultScroll.layoutParams ?: LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)).apply {
                    height = if (generatedOutput.length > 140) maxResultH else ViewGroup.LayoutParams.WRAP_CONTENT
                }
            } else {
                resultScroll.visibility = View.GONE
                resultButtonsRow.visibility = View.GONE
            }
        }
    }

    private fun applyTheme() {
        val bg = theme.keyboardBackground.colorStart
        setBackgroundColor(bg)
        val accent = theme.effectColors.firstOrNull() ?: theme.accentKeyColor ?: theme.trailColor
        val wellBg = KeyboardEffectEngine.compositeOver(theme.suggestionStripBgColor, bg)
        val cardBg = KeyboardEffectEngine.compositeOver(theme.keyFaceColor.primaryColor, bg)
        val funcBg = KeyboardEffectEngine.compositeOver(theme.functionalKeyColor ?: theme.keyFaceColor.primaryColor, bg)

        promptBox.background = UiKit.skeuoWellDrawable(
            context,
            wellBg,
            12f,
            if (isPromptCaptureActive) accent else theme.keyBorderColor
        )
        contextPreviewRow.background = UiKit.roundedDrawable(
            context,
            Color.argb(26, Color.red(accent), Color.green(accent), Color.blue(accent)),
            9f,
            Color.argb(75, Color.red(accent), Color.green(accent), Color.blue(accent)),
            1f
        )
        contextTv.setTextColor(theme.keyLabelColorPrimary)
        contextClearBtn.setTextColor(theme.keyLabelColorSecondary)

        apiSetupHeaderBtn.background = UiKit.skeuoButtonDrawable(context, funcBg, 10f, theme.keyBorderColor)
        apiSetupHeaderBtn.setTextColor(theme.keyLabelColorPrimary)
        closeBtn.background = UiKit.skeuoButtonDrawable(context, funcBg, 10f, theme.keyBorderColor)
        closeBtn.setTextColor(theme.keyLabelColorSecondary)

        for (btn in listOf(pullBtn, pastePromptBtn, clearPromptBtn)) {
            btn.background = UiKit.skeuoButtonDrawable(context, funcBg, 10f, theme.keyBorderColor)
            btn.setTextColor(theme.keyLabelColorPrimary)
        }

        resultCard.background = UiKit.skeuoCardDrawable(
            context,
            cardBg,
            13f,
            if (isErrorState) UiKit.DANGER_FG else theme.keyBorderColor,
            2f
        )
        configErrorBtn.background = UiKit.skeuoButtonDrawable(context, UiKit.DANGER_BG, 9f, UiKit.DANGER_FG)
        configErrorBtn.setTextColor(UiKit.DANGER_FG)

        replaceBtn.background = UiKit.skeuoButtonDrawable(context, accent, 9f)
        replaceBtn.setTextColor(Color.WHITE)
        for (btn in listOf(insertBtn, appendBtn, copyBtn)) {
            btn.background = UiKit.skeuoButtonDrawable(context, funcBg, 9f, theme.keyBorderColor)
            btn.setTextColor(theme.keyLabelColorPrimary)
        }

        renderChips()
    }
}
