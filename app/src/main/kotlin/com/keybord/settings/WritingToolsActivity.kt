package com.keybord.settings

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.keyboard.engine.magicwrite.WritingStyle
import com.keybord.engine.ai.AiWriterClient
import com.keybord.ui.UiKit

/**
 * Writing Tools Studio: BYOK AI Writer Provider Setup,
 * Rambler Voice Cleanup, and On-Device Magic Rewriter.
 */
class WritingToolsActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    private lateinit var aiClient: AiWriterClient

    private lateinit var rawPreviewTv: TextView
    private lateinit var processedPreviewTv: TextView
    private lateinit var previewStatusPill: TextView
    private var selectedTone: WritingStyle = WritingStyle.FORMAL
    private val toneButtons = mutableListOf<Pair<WritingStyle, TextView>>()

    private var selectedProvider: AiWriterClient.Provider = AiWriterClient.Provider.GEMINI
    private lateinit var aiStatusBadge: TextView
    private lateinit var endpointInput: EditText
    private lateinit var modelInput: EditText
    private lateinit var apiKeyInput: EditText
    private lateinit var cloudFieldsContainer: LinearLayout
    private lateinit var apiTestStatusTv: TextView
    private val providerButtons = mutableMapOf<AiWriterClient.Provider, TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        aiClient = AiWriterClient(this)

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
                subtitle = "On-device Rambler & Rewriter + optional BYOK Cloud AI",
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

        // Card 1: BYOK AI Writer API Configuration
        content.addView(buildByokAiCard())

        // Card 2: Rambler Voice Cleanup
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

        // Bottom Honest Privacy Banner
        content.addView(
            UiKit.createPrivacyFooter(
                this,
                "Zero Telemetry • Local Core + Optional BYOK AI",
                "Normal typing, clipboard, and offline Rust rewriting never leave your device. Cloud AI Writer only contacts your own BYOK endpoint when triggered."
            )
        )

        scroll.addView(content)
        root.addView(scroll)
        setContentView(root)
        refreshTransformationPreview()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::aiClient.isInitialized) {
            aiClient.shutdown()
        }
    }

    private fun buildByokAiCard(): LinearLayout {
        val dp = resources.displayMetrics.density
        val cfg = aiClient.loadConfig()
        selectedProvider = cfg.provider

        val card = UiKit.createCardContainer(this, paddingDp = 16f, radiusDp = 18f, bottomMarginDp = 14f)

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (12 * dp).toInt())
        }
        headerRow.addView(
            TextView(this).apply {
                text = "🤖"
                textSize = 18f
                gravity = Gravity.CENTER
                background = UiKit.skeuoIconWellDrawable(this@WritingToolsActivity, UiKit.ACCENT_AI_SOFT, 12f)
                val sz = (38 * dp).toInt()
                layoutParams = LinearLayout.LayoutParams(sz, sz).apply { marginEnd = (12 * dp).toInt() }
            }
        )
        val headerCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        headerCol.addView(
            TextView(this).apply {
                text = "AI Writer Engine (BYOK)"
                textSize = 16.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(UiKit.TEXT_PRIMARY)
            }
        )
        headerCol.addView(
            TextView(this).apply {
                text = "Connect Gemini or any OpenAI-compatible endpoint"
                textSize = 12f
                setTextColor(UiKit.TEXT_SECONDARY)
            }
        )
        headerRow.addView(headerCol)

        aiStatusBadge = UiKit.createBadgePill(
            this,
            if (cfg.isCloudConfigured) "● Configured" else "● Offline Mode",
            if (cfg.isCloudConfigured) UiKit.SUCCESS_BG else UiKit.WARNING_BG,
            if (cfg.isCloudConfigured) UiKit.SUCCESS_FG else UiKit.WARNING_FG,
            if (cfg.isCloudConfigured) UiKit.SUCCESS_BORDER else UiKit.BORDER_SUBTLE
        )
        headerRow.addView(aiStatusBadge)
        card.addView(headerRow)

        val providerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (10 * dp).toInt()
            }
        }
        providerButtons.clear()

        fun makeFieldLabel(label: String): TextView = TextView(this).apply {
            text = label.uppercase()
            textSize = 10.5f
            letterSpacing = 0.06f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(UiKit.TEXT_SECONDARY)
            setPadding(0, (4 * dp).toInt(), 0, (4 * dp).toInt())
        }

        fun makeInputBox(hintText: String, initialValue: String, isSecret: Boolean = false): EditText =
            EditText(this).apply {
                hint = hintText
                setText(initialValue)
                textSize = 13f
                setTextColor(UiKit.TEXT_PRIMARY)
                setHintTextColor(UiKit.TEXT_MUTED)
                background = UiKit.skeuoWellDrawable(this@WritingToolsActivity, UiKit.SURFACE_INPUT, 12f, UiKit.BORDER_SUBTLE)
                setPadding((12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt())
                setSingleLine(true)
                inputType = if (isSecret) {
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                } else {
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = (8 * dp).toInt() }
            }

        cloudFieldsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        endpointInput = makeInputBox(
            AiWriterClient.Provider.GEMINI.defaultEndpoint,
            cfg.endpointUrl
        )
        modelInput = makeInputBox(
            AiWriterClient.Provider.GEMINI.defaultModel,
            cfg.model
        ).apply {
            inputType = InputType.TYPE_CLASS_TEXT
        }
        apiKeyInput = makeInputBox(
            "Paste your API key (stored locally only)",
            cfg.apiKey,
            isSecret = true
        )

        var keyVisible = false
        val keyRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (8 * dp).toInt()
            }
        }
        apiKeyInput.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = (6 * dp).toInt()
        }
        val toggleKeyBtn = UiKit.createPillButton(this, "Show", filled = false) {
            keyVisible = !keyVisible
            val sel = apiKeyInput.selectionStart
            apiKeyInput.inputType = if (keyVisible) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            if (sel >= 0 && sel <= apiKeyInput.text.length) {
                apiKeyInput.setSelection(sel)
            }
        }
        // Keep button label synchronized
        toggleKeyBtn.setOnClickListener {
            keyVisible = !keyVisible
            val sel = apiKeyInput.selectionStart
            apiKeyInput.inputType = if (keyVisible) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            toggleKeyBtn.text = if (keyVisible) "Hide" else "Show"
            if (sel >= 0 && sel <= apiKeyInput.text.length) {
                apiKeyInput.setSelection(sel)
            }
        }
        keyRow.addView(apiKeyInput)
        keyRow.addView(toggleKeyBtn)

        cloudFieldsContainer.addView(makeFieldLabel("Endpoint URL"))
        cloudFieldsContainer.addView(endpointInput)
        cloudFieldsContainer.addView(makeFieldLabel("Model Name"))
        cloudFieldsContainer.addView(modelInput)
        cloudFieldsContainer.addView(makeFieldLabel("API Key (BYOK)"))
        cloudFieldsContainer.addView(keyRow)

        fun refreshProviderUi(updateDefaults: Boolean) {
            if (updateDefaults && selectedProvider != AiWriterClient.Provider.OFFLINE_ONLY) {
                val curUrl = endpointInput.text?.toString()?.trim().orEmpty()
                val curModel = modelInput.text?.toString()?.trim().orEmpty()
                val isOtherDefaultUrl = curUrl.isBlank() || AiWriterClient.Provider.values().any { it.defaultEndpoint == curUrl }
                val isOtherDefaultModel = curModel.isBlank() || AiWriterClient.Provider.values().any { it.defaultModel == curModel }
                if (isOtherDefaultUrl) endpointInput.setText(selectedProvider.defaultEndpoint)
                if (isOtherDefaultModel) modelInput.setText(selectedProvider.defaultModel)
                endpointInput.hint = selectedProvider.defaultEndpoint
                modelInput.hint = selectedProvider.defaultModel
            }
            for ((prov, btn) in providerButtons) {
                val active = (prov == selectedProvider)
                btn.setTextColor(if (active) Color.WHITE else UiKit.TEXT_SECONDARY)
                btn.typeface = if (active) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                btn.background = UiKit.skeuoButtonDrawable(
                    this,
                    if (active) UiKit.ACCENT_AI else UiKit.SURFACE_ELEVATED,
                    11f,
                    if (active) UiKit.ACCENT_AI else UiKit.BORDER_SUBTLE
                )
            }
            cloudFieldsContainer.visibility =
                if (selectedProvider == AiWriterClient.Provider.OFFLINE_ONLY) View.GONE else View.VISIBLE
        }

        val providers = AiWriterClient.Provider.values()
        providers.forEachIndexed { idx, prov ->
            val btn = TextView(this).apply {
                text = prov.displayName
                textSize = 11.5f
                gravity = Gravity.CENTER
                setSingleLine(true)
                setPadding((6 * dp).toInt(), (8 * dp).toInt(), (6 * dp).toInt(), (8 * dp).toInt())
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (idx < providers.lastIndex) marginEnd = (6 * dp).toInt()
                }
                setOnClickListener {
                    selectedProvider = prov
                    refreshProviderUi(updateDefaults = true)
                }
            }
            providerButtons[prov] = btn
            providerRow.addView(btn)
        }
        card.addView(providerRow)
        card.addView(cloudFieldsContainer)

        apiTestStatusTv = TextView(this).apply {
            textSize = 12f
            visibility = View.GONE
            setPadding((10 * dp).toInt(), (8 * dp).toInt(), (10 * dp).toInt(), (8 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (4 * dp).toInt()
                bottomMargin = (8 * dp).toInt()
            }
        }

        fun updateStatusBanner(msg: String, isError: Boolean = false, isLoading: Boolean = false) {
            apiTestStatusTv.visibility = View.VISIBLE
            apiTestStatusTv.text = msg
            val (bg, fg, stroke) = when {
                isError -> Triple(UiKit.DANGER_BG, UiKit.DANGER_FG, UiKit.DANGER_FG)
                isLoading -> Triple(UiKit.ACCENT_SOFT, UiKit.ACCENT_PRIMARY, UiKit.ACCENT_PRIMARY)
                else -> Triple(UiKit.SUCCESS_BG, UiKit.SUCCESS_FG, UiKit.SUCCESS_BORDER)
            }
            apiTestStatusTv.setTextColor(fg)
            apiTestStatusTv.background = UiKit.skeuoWellDrawable(this, bg, 10f, stroke)
        }

        fun syncBadgeFromSaved() {
            val saved = aiClient.loadConfig()
            aiStatusBadge.text = if (saved.isCloudConfigured) "● Configured" else "● Offline Mode"
            aiStatusBadge.setTextColor(if (saved.isCloudConfigured) UiKit.SUCCESS_FG else UiKit.WARNING_FG)
            aiStatusBadge.background = UiKit.skeuoWellDrawable(
                this,
                if (saved.isCloudConfigured) UiKit.SUCCESS_BG else UiKit.WARNING_BG,
                999f,
                if (saved.isCloudConfigured) UiKit.SUCCESS_BORDER else UiKit.BORDER_SUBTLE
            )
        }

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (4 * dp).toInt()
            }
        }
        val saveBtn = UiKit.createPillButton(this, "Save API Config", filled = true) {
            val url = endpointInput.text?.toString()?.trim().orEmpty()
            val model = modelInput.text?.toString()?.trim().orEmpty()
            val key = apiKeyInput.text?.toString()?.trim().orEmpty()
            aiClient.saveConfig(selectedProvider, url, model, key)
            syncBadgeFromSaved()
            if (selectedProvider == AiWriterClient.Provider.OFFLINE_ONLY) {
                updateStatusBanner("Saved: Offline Proofread/Rewrite mode active.")
            } else if (key.isBlank()) {
                updateStatusBanner("Saved without API key — cloud generation requires an API key.", isError = true)
            } else {
                updateStatusBanner("Saved ${selectedProvider.displayName} ($model) locally.")
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (6 * dp).toInt()
            }
        }
        val testBtn = UiKit.createPillButton(this, "Test Connection", filled = false) {
            val url = endpointInput.text?.toString()?.trim().orEmpty()
            val model = modelInput.text?.toString()?.trim().orEmpty()
            val key = apiKeyInput.text?.toString()?.trim().orEmpty()
            val previewCfg = AiWriterClient.AiConfig(selectedProvider, url, model, key)
            updateStatusBanner("⏳ Testing ${selectedProvider.displayName} connection…", isLoading = true)
            val started = aiClient.testConnectionAsync(previewCfg) { outcome ->
                when (outcome) {
                    is AiWriterClient.AiOutcome.Success -> {
                        aiClient.saveConfig(selectedProvider, url, model, key)
                        syncBadgeFromSaved()
                        updateStatusBanner("✓ Connected (${outcome.engineLabel}): ${outcome.text.take(40)}")
                    }
                    is AiWriterClient.AiOutcome.Error -> {
                        updateStatusBanner("⚠ ${outcome.message}", isError = true)
                    }
                }
            }
            if (!started) {
                updateStatusBanner("Test already in progress…", isLoading = true)
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        btnRow.addView(saveBtn)
        btnRow.addView(testBtn)

        card.addView(apiTestStatusTv)
        card.addView(btnRow)
        refreshProviderUi(updateDefaults = false)
        return card
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
                background = UiKit.skeuoIconWellDrawable(this@WritingToolsActivity, UiKit.ACCENT_AI_SOFT, 12f)
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
            background = UiKit.skeuoWellDrawable(this@WritingToolsActivity, UiKit.SURFACE_INPUT, 14f, UiKit.BORDER_SUBTLE)
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
            background = UiKit.skeuoCardDrawable(this@WritingToolsActivity, UiKit.SURFACE_CARD, 10f, UiKit.BORDER_SUBTLE, 1.5f)
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
            background = UiKit.skeuoCardDrawable(this@WritingToolsActivity, UiKit.SURFACE_CARD, 10f, UiKit.ACCENT_PRIMARY, 1.5f)
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
                btn.background = UiKit.skeuoButtonDrawable(
                    this,
                    if (active) UiKit.ACCENT_AI else UiKit.SURFACE_ELEVATED,
                    12f,
                    if (active) UiKit.ACCENT_AI else UiKit.BORDER_SUBTLE
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
