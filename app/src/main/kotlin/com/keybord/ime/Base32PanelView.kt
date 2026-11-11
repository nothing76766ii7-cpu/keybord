package com.keybord.ime

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.keybord.engine.base32.Base32Engine
import com.keybord.theme.BuiltInThemes
import com.keybord.theme.KeyboardEffectEngine
import com.keybord.theme.ThemeModel
import com.keybord.ui.UiKit

/**
 * On-keyboard All-in-One Base32 Studio:
 * - Tab 1 (Generate): Cryptographic RFC 4648 Base32 keys/tokens (16/26/32/48 ch, Padded/Lower/Grouped)
 * - Tab 2 (Codec): Text <-> Base32 and Hex <-> Base32 encoder/decoder with clipboard & selection pull
 * - Tab 3 (TOTP 2FA): Live 6-digit RFC 6238 HMAC-SHA1 OTP code from Base32 secret + built-in Base32 keypad
 */
class Base32PanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    enum class TabMode {
        GENERATE,
        CODEC,
        TOTP
    }

    enum class CodecMode(val label: String) {
        TEXT_TO_B32("Text → B32"),
        B32_TO_TEXT("B32 → Text"),
        HEX_TO_B32("Hex → B32"),
        B32_TO_HEX("B32 → Hex")
    }

    var theme: ThemeModel = BuiltInThemes.SKEUO_DARK_GRAPHITE
        set(value) {
            field = value
            applyTheme()
        }

    var onInsertText: ((String) -> Unit)? = null
    var onReplaceSelectionOrInsert: ((String) -> Unit)? = null
    var onCopyText: ((String) -> Unit)? = null
    var onRequestClipboardText: (() -> String?)? = null
    var onRequestAppText: (() -> String?)? = null
    var onBackToKeyboardClicked: (() -> Unit)? = null

    private var activeTab: TabMode = TabMode.GENERATE

    // Generate state
    private var genLengthChars: Int = 16
    private var genPadded: Boolean = false
    private var genLowercase: Boolean = false
    private var genGrouped: Boolean = true
    private var generatedValue: String = ""

    // Codec state
    private var codecMode: CodecMode = CodecMode.TEXT_TO_B32
    private var codecInput: String = "Keybord"
    private var codecOutput: String = ""
    private var codecError: String? = null

    // TOTP state
    private var totpSecretBuffer: StringBuilder = StringBuilder()
    private var currentTotpRawCode: String = ""
    private var showTotpKeypad: Boolean = false

    // Shared UI refs
    private lateinit var segRow: LinearLayout
    private val tabButtons = mutableListOf<Pair<TabMode, TextView>>()
    private lateinit var genContainer: LinearLayout
    private lateinit var genOutputTv: TextView
    private lateinit var genMetaTv: TextView
    private lateinit var codecContainer: LinearLayout
    private lateinit var codecInputTv: TextView
    private lateinit var codecOutputTv: TextView
    private lateinit var codecErrorTv: TextView
    private lateinit var totpContainer: LinearLayout
    private lateinit var totpCodeTv: TextView
    private lateinit var totpTimerTv: TextView
    private lateinit var totpProgress: ProgressBar
    private lateinit var totpSecretTv: TextView
    private lateinit var totpKeypadContainer: LinearLayout
    private lateinit var backKeysBtn: TextView
    private lateinit var primaryInsertBtn: TextView
    private lateinit var primaryCopyBtn: TextView

    private val tickerHandler = Handler(Looper.getMainLooper())
    private val tickerRunnable = object : Runnable {
        override fun run() {
            if (visibility == View.VISIBLE && activeTab == TabMode.TOTP) {
                refreshTotpCode()
                tickerHandler.postDelayed(this, 1000L)
            }
        }
    }

    init {
        orientation = VERTICAL
        setupViews()
        regenerateRandom()
        recomputeCodec()
        applyTheme()
    }

    fun onPanelOpened() {
        val pulled = onRequestAppText?.invoke()?.trim()
        if (!pulled.isNullOrEmpty() && activeTab == TabMode.CODEC) {
            codecInput = pulled
            recomputeCodec()
        }
        if (activeTab == TabMode.TOTP) {
            startTicker()
        }
    }

    fun pauseTicker() {
        tickerHandler.removeCallbacks(tickerRunnable)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pauseTicker()
    }

    private fun startTicker() {
        tickerHandler.removeCallbacks(tickerRunnable)
        if (visibility == View.VISIBLE && activeTab == TabMode.TOTP) {
            refreshTotpCode()
            tickerHandler.postDelayed(tickerRunnable, 1000L)
        }
    }

    private fun setupViews() {
        val dp = resources.displayMetrics.density
        setPadding((10 * dp).toInt(), (8 * dp).toInt(), (10 * dp).toInt(), (10 * dp).toInt())

        // 1. Header + 3-Tab Switcher
        val headerRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (8 * dp).toInt())
        }
        headerRow.addView(
            TextView(context).apply {
                text = "🔐 Base32"
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(UiKit.TEXT_PRIMARY)
                layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
        )
        headerRow.addView(
            UiKit.createBadgePill(context, "RFC 4648", UiKit.ACCENT_SOFT, UiKit.ACCENT_PRIMARY).apply {
                textSize = 10.5f
                setPadding((8 * dp).toInt(), (3 * dp).toInt(), (8 * dp).toInt(), (3 * dp).toInt())
            }
        )
        addView(headerRow)

        segRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding((3 * dp).toInt(), (3 * dp).toInt(), (3 * dp).toInt(), (3 * dp).toInt())
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (8 * dp).toInt()
            }
        }
        tabButtons.clear()
        listOf(
            TabMode.GENERATE to "Generate",
            TabMode.CODEC to "Encode / Decode",
            TabMode.TOTP to "TOTP 2FA"
        ).forEach { (mode, title) ->
            val btn = TextView(context).apply {
                text = title
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding((6 * dp).toInt(), (7 * dp).toInt(), (6 * dp).toInt(), (7 * dp).toInt())
                layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener { switchTab(mode) }
            }
            tabButtons.add(mode to btn)
            segRow.addView(btn)
        }
        addView(segRow)

        // 2. Tab 1: Random Generator Container
        genContainer = buildGenerateContainer(dp)
        addView(genContainer)

        // 3. Tab 2: Codec Container
        codecContainer = buildCodecContainer(dp).apply { visibility = View.GONE }
        addView(codecContainer)

        // 4. Tab 3: TOTP 2FA Container
        totpContainer = buildTotpContainer(dp).apply { visibility = View.GONE }
        addView(totpContainer)

        // 5. Bottom Shared Action Bar ([⌨ Keyboard] [📋 Copy] [↳ Insert])
        val bottomRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (8 * dp).toInt(), 0, 0)
        }
        backKeysBtn = UiKit.createPillButton(context, "⌨ Keys", filled = false) {
            pauseTicker()
            onBackToKeyboardClicked?.invoke()
        }.apply {
            layoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (38 * dp).toInt())
        }
        primaryCopyBtn = UiKit.createPillButton(context, "📋 Copy", filled = false) {
            val payload = currentActionPayload()
            if (payload != null) {
                onCopyText?.invoke(payload)
                Toast.makeText(context, "Copied Base32 output", Toast.LENGTH_SHORT).show()
            }
        }.apply {
            layoutParams = LayoutParams(0, (38 * dp).toInt(), 1f).apply {
                marginStart = (6 * dp).toInt()
                marginEnd = (6 * dp).toInt()
            }
        }
        primaryInsertBtn = UiKit.createPillButton(context, "↳ Insert Code", filled = true) {
            val payload = currentActionPayload()
            if (payload != null) {
                onInsertText?.invoke(payload)
                if (activeTab == TabMode.GENERATE) {
                    regenerateRandom()
                }
            }
        }.apply {
            layoutParams = LayoutParams(0, (38 * dp).toInt(), 1.3f)
        }
        bottomRow.addView(backKeysBtn)
        bottomRow.addView(primaryCopyBtn)
        bottomRow.addView(primaryInsertBtn)
        addView(bottomRow)

        switchTab(TabMode.GENERATE)
    }

    private fun buildGenerateContainer(dp: Float): LinearLayout {
        val wrap = LinearLayout(context).apply { orientation = VERTICAL }

        // Length selector row
        val lenRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (6 * dp).toInt()
            }
        }
        val lenViews = mutableListOf<Pair<Int, TextView>>()
        fun syncLen() {
            for ((len, tv) in lenViews) {
                val active = (len == genLengthChars)
                tv.setTextColor(if (active) Color.WHITE else theme.keyLabelColorSecondary)
                tv.typeface = if (active) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                tv.background = UiKit.roundedDrawable(
                    context,
                    if (active) UiKit.ACCENT_PRIMARY else theme.keyFaceColor.primaryColor,
                    9f,
                    theme.keyBorderColor,
                    1f
                )
            }
        }
        listOf(16 to "16 ch", 26 to "26 ch", 32 to "32 ch", 48 to "48 ch").forEachIndexed { i, (len, lbl) ->
            val tv = TextView(context).apply {
                text = lbl
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(0, (6 * dp).toInt(), 0, (6 * dp).toInt())
                layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (i < 3) marginEnd = (6 * dp).toInt()
                }
                setOnClickListener {
                    genLengthChars = len
                    syncLen()
                    regenerateRandom()
                }
            }
            lenViews.add(len to tv)
            lenRow.addView(tv)
        }
        syncLen()
        wrap.addView(lenRow)

        // Format options row
        val optRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (8 * dp).toInt()
            }
        }
        fun addToggleChip(label: String, isActive: () -> Boolean, onFlip: () -> Unit, hasMargin: Boolean) {
            lateinit var tv: TextView
            fun sync() {
                val on = isActive()
                tv.text = if (on) "✓ $label" else label
                tv.setTextColor(if (on) UiKit.ACCENT_PRIMARY else theme.keyLabelColorSecondary)
                tv.background = UiKit.roundedDrawable(
                    context,
                    if (on) UiKit.ACCENT_SOFT else UiKit.SURFACE_INPUT,
                    8f,
                    if (on) UiKit.ACCENT_PRIMARY else theme.keyBorderColor,
                    1f
                )
            }
            tv = TextView(context).apply {
                textSize = 11.5f
                gravity = Gravity.CENTER
                setPadding((8 * dp).toInt(), (5 * dp).toInt(), (8 * dp).toInt(), (5 * dp).toInt())
                layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (hasMargin) marginEnd = (6 * dp).toInt()
                }
                setOnClickListener {
                    onFlip()
                    sync()
                    regenerateRandom()
                }
            }
            sync()
            optRow.addView(tv)
        }
        addToggleChip("Groups", { genGrouped }, { genGrouped = !genGrouped }, true)
        addToggleChip("lowercase", { genLowercase }, { genLowercase = !genLowercase }, true)
        addToggleChip("Pad (=)", { genPadded }, { genPadded = !genPadded }, false)
        wrap.addView(optRow)

        // Output box + Regenerate pill
        val outCard = LinearLayout(context).apply {
            orientation = VERTICAL
            background = UiKit.roundedDrawable(context, UiKit.SURFACE_INPUT, 14f, UiKit.ACCENT_PRIMARY, 1f)
            setPadding((12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt())
        }
        val topMeta = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (4 * dp).toInt())
        }
        genMetaTv = TextView(context).apply {
            text = "80-bit SecureRandom • RFC 4648"
            textSize = 10.5f
            setTextColor(UiKit.TEXT_SECONDARY)
            layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val regenBtn = TextView(context).apply {
            text = "↻ New"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = UiKit.roundedDrawable(context, UiKit.ACCENT_PRIMARY, 8f)
            setPadding((10 * dp).toInt(), (4 * dp).toInt(), (10 * dp).toInt(), (4 * dp).toInt())
            setOnClickListener { regenerateRandom() }
        }
        topMeta.addView(genMetaTv)
        topMeta.addView(regenBtn)

        genOutputTv = TextView(context).apply {
            textSize = 16f
            typeface = Typeface.MONOSPACE
            setTextColor(UiKit.TEXT_PRIMARY)
            setPadding(0, (4 * dp).toInt(), 0, (2 * dp).toInt())
            setTextIsSelectable(true)
        }

        outCard.addView(topMeta)
        outCard.addView(genOutputTv)
        wrap.addView(outCard)
        return wrap
    }

    private fun buildCodecContainer(dp: Float): LinearLayout {
        val wrap = LinearLayout(context).apply { orientation = VERTICAL }

        // Mode switcher pills
        val modeRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (6 * dp).toInt()
            }
        }
        val modeViews = mutableListOf<Pair<CodecMode, TextView>>()
        fun syncModes() {
            for ((m, tv) in modeViews) {
                val active = (m == codecMode)
                tv.setTextColor(if (active) Color.WHITE else UiKit.TEXT_SECONDARY)
                tv.typeface = if (active) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                tv.background = UiKit.roundedDrawable(
                    context,
                    if (active) UiKit.ACCENT_PRIMARY else UiKit.SURFACE_INPUT,
                    8f,
                    if (active) null else UiKit.BORDER_SUBTLE,
                    1f
                )
            }
        }
        CodecMode.values().forEachIndexed { idx, mode ->
            val tv = TextView(context).apply {
                text = mode.label
                textSize = 11f
                gravity = Gravity.CENTER
                setPadding((4 * dp).toInt(), (6 * dp).toInt(), (4 * dp).toInt(), (6 * dp).toInt())
                layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (idx < CodecMode.values().lastIndex) marginEnd = (4 * dp).toInt()
                }
                setOnClickListener {
                    codecMode = mode
                    syncModes()
                    recomputeCodec()
                }
            }
            modeViews.add(mode to tv)
            modeRow.addView(tv)
        }
        syncModes()
        wrap.addView(modeRow)

        // Source controls ([📋 Paste] [📄 App Text] [⇅ Swap] [↻ Replace Sel])
        val srcRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (6 * dp).toInt()
            }
        }
        fun addSrcBtn(label: String, margin: Boolean = true, onClick: () -> Unit) {
            srcRow.addView(
                TextView(context).apply {
                    text = label
                    textSize = 11f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(UiKit.TEXT_PRIMARY)
                    gravity = Gravity.CENTER
                    background = UiKit.roundedDrawable(context, UiKit.SURFACE_ELEVATED, 8f, UiKit.BORDER_SUBTLE, 1f)
                    setPadding((6 * dp).toInt(), (5 * dp).toInt(), (6 * dp).toInt(), (5 * dp).toInt())
                    layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        if (margin) marginEnd = (5 * dp).toInt()
                    }
                    setOnClickListener { onClick() }
                }
            )
        }
        addSrcBtn("📋 Paste") {
            val clip = onRequestClipboardText?.invoke()
            if (!clip.isNullOrEmpty()) {
                codecInput = clip
                recomputeCodec()
            } else {
                Toast.makeText(context, "Clipboard empty", Toast.LENGTH_SHORT).show()
            }
        }
        addSrcBtn("📄 Pull Text") {
            val appTxt = onRequestAppText?.invoke()
            if (!appTxt.isNullOrEmpty()) {
                codecInput = appTxt
                recomputeCodec()
            } else {
                Toast.makeText(context, "No text near cursor or selected", Toast.LENGTH_SHORT).show()
            }
        }
        addSrcBtn("⇅ Swap") {
            if (codecError == null && codecOutput.isNotEmpty()) {
                codecInput = codecOutput
                codecMode = when (codecMode) {
                    CodecMode.TEXT_TO_B32 -> CodecMode.B32_TO_TEXT
                    CodecMode.B32_TO_TEXT -> CodecMode.TEXT_TO_B32
                    CodecMode.HEX_TO_B32 -> CodecMode.B32_TO_HEX
                    CodecMode.B32_TO_HEX -> CodecMode.HEX_TO_B32
                }
                syncModes()
                recomputeCodec()
            }
        }
        addSrcBtn("↻ Replace", margin = false) {
            if (codecError == null && codecOutput.isNotEmpty()) {
                onReplaceSelectionOrInsert?.invoke(codecOutput)
            }
        }
        wrap.addView(srcRow)

        // Input preview line
        codecInputTv = TextView(context).apply {
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(UiKit.TEXT_SECONDARY)
            setSingleLine(true)
            background = UiKit.roundedDrawable(context, UiKit.SURFACE_INPUT, 10f, UiKit.BORDER_SUBTLE, 1f)
            setPadding((10 * dp).toInt(), (6 * dp).toInt(), (10 * dp).toInt(), (6 * dp).toInt())
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (6 * dp).toInt()
            }
        }
        wrap.addView(codecInputTv)

        // Output box
        val outBox = LinearLayout(context).apply {
            orientation = VERTICAL
            background = UiKit.roundedDrawable(context, UiKit.SURFACE_INPUT, 12f, UiKit.ACCENT_PRIMARY, 1f)
            setPadding((10 * dp).toInt(), (8 * dp).toInt(), (10 * dp).toInt(), (8 * dp).toInt())
        }
        codecOutputTv = TextView(context).apply {
            textSize = 14f
            typeface = Typeface.MONOSPACE
            setTextColor(UiKit.TEXT_PRIMARY)
            maxLines = 2
        }
        codecErrorTv = TextView(context).apply {
            textSize = 11.5f
            setTextColor(UiKit.DANGER_FG)
            visibility = View.GONE
        }
        outBox.addView(codecOutputTv)
        outBox.addView(codecErrorTv)
        wrap.addView(outBox)
        return wrap
    }

    private fun buildTotpContainer(dp: Float): LinearLayout {
        val wrap = LinearLayout(context).apply { orientation = VERTICAL }

        // TOTP Output Card
        val otpCard = LinearLayout(context).apply {
            orientation = VERTICAL
            background = UiKit.roundedDrawable(context, UiKit.SURFACE_INPUT, 14f, UiKit.ACCENT_AI, 1f)
            setPadding((12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt())
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (6 * dp).toInt()
            }
        }
        val topLine = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        totpCodeTv = TextView(context).apply {
            text = "--- ---"
            textSize = 24f
            typeface = Typeface.MONOSPACE
            setTextColor(UiKit.TEXT_PRIMARY)
            layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        totpTimerTv = TextView(context).apply {
            text = "⏱ 30s"
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(UiKit.ACCENT_PRIMARY)
            background = UiKit.roundedDrawable(context, UiKit.ACCENT_SOFT, 8f)
            setPadding((8 * dp).toInt(), (3 * dp).toInt(), (8 * dp).toInt(), (3 * dp).toInt())
        }
        topLine.addView(totpCodeTv)
        topLine.addView(totpTimerTv)
        otpCard.addView(topLine)

        totpProgress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 30
            progress = 30
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (6 * dp).toInt()).apply {
                topMargin = (6 * dp).toInt()
            }
        }
        otpCard.addView(totpProgress)
        wrap.addView(otpCard)

        // Secret controls + display
        val secRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (6 * dp).toInt()
            }
        }
        fun addSecBtn(label: String, onClick: () -> Unit) {
            secRow.addView(
                TextView(context).apply {
                    text = label
                    textSize = 11f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(UiKit.TEXT_PRIMARY)
                    gravity = Gravity.CENTER
                    background = UiKit.roundedDrawable(context, UiKit.SURFACE_ELEVATED, 8f, UiKit.BORDER_SUBTLE, 1f)
                    setPadding((8 * dp).toInt(), (5 * dp).toInt(), (8 * dp).toInt(), (5 * dp).toInt())
                    layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginEnd = (5 * dp).toInt()
                    }
                    setOnClickListener { onClick() }
                }
            )
        }
        addSecBtn("📋 Paste") {
            val clip = onRequestClipboardText?.invoke()
            if (!clip.isNullOrBlank()) {
                totpSecretBuffer = StringBuilder(clip.uppercase().filter { it in 'A'..'Z' || it in '2'..'7' })
                refreshTotpCode()
            } else {
                Toast.makeText(context, "Clipboard empty", Toast.LENGTH_SHORT).show()
            }
        }
        addSecBtn("📄 App") {
            val appTxt = onRequestAppText?.invoke()
            if (!appTxt.isNullOrBlank()) {
                totpSecretBuffer = StringBuilder(appTxt.uppercase().filter { it in 'A'..'Z' || it in '2'..'7' })
                refreshTotpCode()
            }
        }
        addSecBtn("🎲 Seed") {
            totpSecretBuffer = StringBuilder(Base32Engine.generateRandom(16, pad = false))
            refreshTotpCode()
        }
        secRow.addView(
            TextView(context).apply {
                text = "⌨ Pad"
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                background = UiKit.roundedDrawable(context, UiKit.ACCENT_AI, 8f)
                setPadding((8 * dp).toInt(), (5 * dp).toInt(), (8 * dp).toInt(), (5 * dp).toInt())
                layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    showTotpKeypad = !showTotpKeypad
                    totpKeypadContainer.visibility = if (showTotpKeypad) View.VISIBLE else View.GONE
                }
            }
        )
        wrap.addView(secRow)

        totpSecretTv = TextView(context).apply {
            text = "Secret: (paste or tap ⌨ Pad / 🎲 Seed)"
            textSize = 11.5f
            typeface = Typeface.MONOSPACE
            setTextColor(UiKit.TEXT_SECONDARY)
            setSingleLine(true)
            setPadding((4 * dp).toInt(), 0, (4 * dp).toInt(), (4 * dp).toInt())
        }
        wrap.addView(totpSecretTv)

        // Built-in Compact Base32 Keypad (A-Z + 2-7 + Backspace + Clear)
        totpKeypadContainer = buildBase32MiniKeypad(dp).apply {
            visibility = View.GONE
        }
        wrap.addView(totpKeypadContainer)
        return wrap
    }

    private fun buildBase32MiniKeypad(dp: Float): LinearLayout {
        val box = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(0, (4 * dp).toInt(), 0, 0)
        }
        val rows = listOf(
            listOf("2", "3", "4", "5", "6", "7", "Q", "W", "E", "R"),
            listOf("T", "Y", "U", "I", "O", "P", "A", "S", "D", "F"),
            listOf("G", "H", "J", "K", "L", "Z", "X", "C", "V", "B", "N", "M", "⌫")
        )
        rows.forEachIndexed { rIdx, keys ->
            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (30 * dp).toInt()).apply {
                    if (rIdx < rows.lastIndex) bottomMargin = (4 * dp).toInt()
                }
            }
            keys.forEachIndexed { cIdx, label ->
                val btn = TextView(context).apply {
                    text = label
                    textSize = 12f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setTextColor(UiKit.TEXT_PRIMARY)
                    background = UiKit.roundedDrawable(context, UiKit.SURFACE_ELEVATED, 6f, UiKit.BORDER_SUBTLE, 1f)
                    layoutParams = LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, if (label == "⌫") 1.5f else 1f).apply {
                        if (cIdx < keys.lastIndex) marginEnd = (3 * dp).toInt()
                    }
                    setOnClickListener {
                        if (label == "⌫") {
                            if (totpSecretBuffer.isNotEmpty()) {
                                totpSecretBuffer.deleteCharAt(totpSecretBuffer.lastIndex)
                            }
                        } else {
                            totpSecretBuffer.append(label)
                        }
                        refreshTotpCode()
                    }
                    if (label == "⌫") {
                        setOnLongClickListener {
                            totpSecretBuffer.clear()
                            refreshTotpCode()
                            true
                        }
                    }
                }
                row.addView(btn)
            }
            box.addView(row)
        }
        return box
    }

    private fun switchTab(tab: TabMode) {
        activeTab = tab
        genContainer.visibility = if (tab == TabMode.GENERATE) View.VISIBLE else View.GONE
        codecContainer.visibility = if (tab == TabMode.CODEC) View.VISIBLE else View.GONE
        totpContainer.visibility = if (tab == TabMode.TOTP) View.VISIBLE else View.GONE

        primaryInsertBtn.text = when (tab) {
            TabMode.GENERATE -> "↳ Insert Token"
            TabMode.CODEC -> "↳ Insert Result"
            TabMode.TOTP -> "↳ Insert Code"
        }

        if (tab == TabMode.TOTP) {
            startTicker()
        } else {
            pauseTicker()
        }
        syncTabStyles()
    }

    private fun syncTabStyles() {
        val accent = theme.accentKeyColor ?: UiKit.ACCENT_PRIMARY
        for ((mode, tv) in tabButtons) {
            val active = (mode == activeTab)
            tv.setTextColor(if (active) Color.WHITE else theme.keyLabelColorSecondary)
            tv.typeface = if (active) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            tv.background = if (active) UiKit.skeuoButtonDrawable(context, accent, 9f) else null
        }
    }

    private fun regenerateRandom() {
        generatedValue = Base32Engine.generateRandom(
            charCount = genLengthChars,
            pad = genPadded,
            lowercase = genLowercase,
            grouped = genGrouped
        )
        genOutputTv.text = generatedValue
        val bits = (genLengthChars * 5)
        genMetaTv.text = "${bits}-bit SecureRandom • RFC 4648"
    }

    private fun recomputeCodec() {
        codecInputTv.text = "Input: ${codecInput.replace("\n", " ").take(46)}"
        val res = when (codecMode) {
            CodecMode.TEXT_TO_B32 -> Result.success(Base32Engine.encodeText(codecInput, pad = true))
            CodecMode.B32_TO_TEXT -> Base32Engine.decodeToText(codecInput)
            CodecMode.HEX_TO_B32 -> Base32Engine.encodeHex(codecInput, pad = true)
            CodecMode.B32_TO_HEX -> Base32Engine.decodeToHex(codecInput)
        }
        res.onSuccess { out ->
            codecOutput = out
            codecError = null
            codecOutputTv.visibility = View.VISIBLE
            codecErrorTv.visibility = View.GONE
            codecOutputTv.text = if (out.isEmpty()) "(empty)" else out
        }.onFailure { err ->
            codecOutput = ""
            codecError = err.message ?: "Invalid input"
            codecOutputTv.visibility = View.GONE
            codecErrorTv.visibility = View.VISIBLE
            codecErrorTv.text = codecError
        }
    }

    private fun refreshTotpCode() {
        val secret = totpSecretBuffer.toString()
        if (secret.isEmpty()) {
            currentTotpRawCode = ""
            totpCodeTv.text = "--- ---"
            totpTimerTv.text = "⏱ 30s"
            totpProgress.progress = 30
            totpSecretTv.text = "Secret: (paste or tap ⌨ Pad / 🎲 Seed)"
            return
        }
        totpSecretTv.text = "Secret: ${Base32Engine.groupFourChars(secret)}"
        Base32Engine.generateTotp(secret).onSuccess { res ->
            currentTotpRawCode = res.code
            totpCodeTv.text = "${res.code.take(3)} ${res.code.drop(3)}"
            totpTimerTv.text = "⏱ ${res.remainingSeconds}s"
            totpProgress.progress = res.remainingSeconds
        }.onFailure { err ->
            currentTotpRawCode = ""
            totpCodeTv.text = "ERR"
            totpSecretTv.text = err.message ?: "Invalid Base32 secret"
        }
    }

    private fun currentActionPayload(): String? {
        return when (activeTab) {
            TabMode.GENERATE -> generatedValue.ifBlank { null }
            TabMode.CODEC -> if (codecError == null) codecOutput.ifBlank { null } else null
            TabMode.TOTP -> currentTotpRawCode.ifBlank { null }
        }
    }

    private fun applyTheme() {
        val solidBg = KeyboardEffectEngine.compositeOver(theme.keyboardBackground.colorStart, Color.BLACK)
        setBackgroundColor(solidBg)
        val innerFill = theme.functionalKeyColor ?: UiKit.SURFACE_CARD
        segRow.background = UiKit.skeuoWellDrawable(context, innerFill, 11f, theme.keyBorderColor)
        syncTabStyles()
    }
}
