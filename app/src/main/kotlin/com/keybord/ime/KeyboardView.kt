package com.keybord.ime

import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.keybord.engine.glide.KeyPosition
import com.keybord.theme.*

/**
 * High-performance custom KeyboardView supporting:
 * - Dynamic theme geometry, optional number row, secondary symbol hints & one-handed mode
 * - Multi-touch 2-thumb rollover for rapid typing
 * - Long-press secondary symbols (numbers / punctuation)
 * - Gboard-style Space-bar horizontal cursor scrubbing with live visual indicator
 * - Delete key repeat-char -> repeat-word & leftward swipe-to-delete
 */
class KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class KeyboardLayer {
        ALPHA,
        SYMBOLS,
        EXTENDED_SYMBOLS
    }

    enum class ShiftState {
        OFF,
        SHIFTED,
        CAPS_LOCK
    }

    var layer: KeyboardLayer = KeyboardLayer.ALPHA
        set(value) {
            field = value
            requestLayout()
            recomputeKeyBounds()
            invalidate()
        }

    var shiftState: ShiftState = ShiftState.OFF
        set(value) {
            field = value
            invalidate()
        }

    var theme: ThemeModel = BuiltInThemes.MATERIAL_MIDNIGHT
        set(value) {
            field = value
            effectEngine.clear()
            requestLayout()
            recomputeKeyBounds()
            invalidate()
        }

    var showNumberRow: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
                recomputeKeyBounds()
                invalidate()
            }
        }

    var showSecondarySymbols: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var vibrationDurationMs: Long = 14L

    var spaceBarLabel: String = "English • QWERTY"
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var onKeyTap: ((String) -> Unit)? = null
    var onKeyLongPress: ((String) -> Unit)? = null
    var onDeleteRepeatChar: (() -> Unit)? = null
    var onDeleteWord: (() -> Unit)? = null
    var onDeleteSwipeStart: (() -> Unit)? = null
    var onDeleteSwipeStep: ((wordCount: Int) -> Int)? = null
    var onDeleteSwipeFinish: ((commitDelete: Boolean) -> Unit)? = null
    var onSpaceSwipeCursorStep: ((deltaSteps: Int) -> Unit)? = null
    var onSpaceSwipeStateChanged: ((active: Boolean) -> Unit)? = null
    var onKeyPositionsChanged: ((Map<Char, KeyPosition>) -> Unit)? = null

    var popPreviewView: KeyPopPreviewView? = null

    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    private val effectEngine = KeyboardEffectEngine()
    private val deleteHandler = Handler(Looper.getMainLooper())

    // Drawing paints
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bevelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val secondaryTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.RIGHT }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val spaceCursorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shiftDotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    data class KeyDef(
        val code: String,
        val secondaryCode: String? = null,
        val widthWeight: Float = 1.0f,
        var bounds: RectF = RectF()
    )

    private val keyPositions = HashMap<Char, KeyPosition>()
    private val activeKeys = mutableListOf<KeyDef>()
    private var pressedKey: KeyDef? = null
    private var activePointerId: Int = MotionEvent.INVALID_POINTER_ID

    private var touchDownTime = 0L
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var isSwipeGesture = false

    // Long-press secondary char state
    private var longPressSecondaryKey: KeyDef? = null
    private var longPressSecondaryTriggered = false
    private val secondaryLongPressRunnable = Runnable {
        val target = longPressSecondaryKey ?: return@Runnable
        val sec = target.secondaryCode ?: return@Runnable
        if (!isSwipeGesture && pressedKey == target) {
            longPressSecondaryTriggered = true
            triggerHapticFeedback(vibrationDurationMs.coerceAtMost(18L))
            popPreviewView?.show(sec, target.bounds)
            onKeyLongPress?.invoke(sec)
        }
    }

    // Delete key long-press & swipe-to-delete state
    private var deleteKeyDown: KeyDef? = null
    private var hasPerformedRepeatDelete = false
    private var isDeleteSwipeActive = false
    private var activeDeleteSwipeCount = 0
    private var deleteRepeatTicks = 0

    // Space-bar cursor slide state
    private var spaceKeyDown: KeyDef? = null
    private var isSpaceCursorActive = false
    private var spaceLastStepIndex = 0

    private val deleteRepeatRunnable = object : Runnable {
        override fun run() {
            if (deleteKeyDown == null || isDeleteSwipeActive) return
            hasPerformedRepeatDelete = true
            deleteRepeatTicks++
            val nextIntervalMs = if (deleteRepeatTicks <= DELETE_CHAR_TICKS_BEFORE_WORDS) {
                onDeleteRepeatChar?.invoke() ?: onKeyTap?.invoke("⌫")
                DELETE_CHAR_INTERVAL_MS
            } else {
                triggerHapticFeedback(vibrationDurationMs.coerceAtMost(10L))
                onDeleteWord?.invoke() ?: onDeleteRepeatChar?.invoke() ?: onKeyTap?.invoke("⌫")
                DELETE_WORD_INTERVAL_MS
            }
            deleteHandler.postDelayed(this, nextIntervalMs)
        }
    }

    private val numberRow = listOf(
        KeyDef("1"), KeyDef("2"), KeyDef("3"), KeyDef("4"), KeyDef("5"),
        KeyDef("6"), KeyDef("7"), KeyDef("8"), KeyDef("9"), KeyDef("0")
    )

    private val alphaRows = listOf(
        listOf(
            KeyDef("q", "1"), KeyDef("w", "2"), KeyDef("e", "3"), KeyDef("r", "4"),
            KeyDef("t", "5"), KeyDef("y", "6"), KeyDef("u", "7"), KeyDef("i", "8"),
            KeyDef("o", "9"), KeyDef("p", "0")
        ),
        listOf(
            KeyDef("a", "@"), KeyDef("s", "#"), KeyDef("d", "$"), KeyDef("f", "%"),
            KeyDef("g", "&"), KeyDef("h", "-"), KeyDef("j", "+"), KeyDef("k", "("),
            KeyDef("l", ")")
        ),
        listOf(
            KeyDef("⇧", widthWeight = 1.4f), KeyDef("z", "*"), KeyDef("x", "\""), KeyDef("c", "'"),
            KeyDef("v", ":"), KeyDef("b", ";"), KeyDef("n", "/"), KeyDef("m", "?"), KeyDef("⌫", widthWeight = 1.4f)
        ),
        listOf(
            KeyDef("?123", widthWeight = 1.4f), KeyDef(",", "!"),
            KeyDef(" ", widthWeight = 4.2f), KeyDef(".", "?"),
            KeyDef("Enter", widthWeight = 1.6f)
        )
    )

    private val symbolsRows = listOf(
        listOf(
            KeyDef("1"), KeyDef("2"), KeyDef("3"), KeyDef("4"), KeyDef("5"),
            KeyDef("6"), KeyDef("7"), KeyDef("8"), KeyDef("9"), KeyDef("0")
        ),
        listOf(
            KeyDef("@"), KeyDef("#"), KeyDef("$"), KeyDef("%"), KeyDef("&"),
            KeyDef("-"), KeyDef("+"), KeyDef("("), KeyDef(")"), KeyDef("/")
        ),
        listOf(
            KeyDef("=\\<", widthWeight = 1.4f), KeyDef("*"), KeyDef("\""), KeyDef("'"),
            KeyDef(":"), KeyDef(";"), KeyDef("!"), KeyDef("?"), KeyDef("⌫", widthWeight = 1.4f)
        ),
        listOf(
            KeyDef("ABC", widthWeight = 1.4f), KeyDef(","), KeyDef(" ", widthWeight = 4.2f),
            KeyDef("."), KeyDef("Enter", widthWeight = 1.6f)
        )
    )

    private val extendedSymbolsRows = listOf(
        listOf(
            KeyDef("~"), KeyDef("`"), KeyDef("|"), KeyDef("•"), KeyDef("√"),
            KeyDef("π"), KeyDef("÷"), KeyDef("×"), KeyDef("¶"), KeyDef("∆")
        ),
        listOf(
            KeyDef("£"), KeyDef("¢"), KeyDef("€"), KeyDef("¥"), KeyDef("^"),
            KeyDef("°"), KeyDef("="), KeyDef("{"), KeyDef("}"), KeyDef("\\")
        ),
        listOf(
            KeyDef("?123", widthWeight = 1.4f), KeyDef("%"), KeyDef("©"), KeyDef("®"),
            KeyDef("™"), KeyDef("✓"), KeyDef("["), KeyDef("]"), KeyDef("⌫", widthWeight = 1.4f)
        ),
        listOf(
            KeyDef("ABC", widthWeight = 1.4f), KeyDef("<"), KeyDef(" ", widthWeight = 4.2f),
            KeyDef(">"), KeyDef("Enter", widthWeight = 1.6f)
        )
    )

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = resources.displayMetrics.density
        val hasNumRow = showNumberRow && layer == KeyboardLayer.ALPHA
        val mainRowsHeight = theme.keyHeightDp * 4f
        val numRowHeight = if (hasNumRow) theme.keyHeightDp * NUMBER_ROW_HEIGHT_RATIO else 0f
        val gapCount = if (hasNumRow) 6f else 5f
        val totalHeightDp = mainRowsHeight + numRowHeight + (theme.keyGapVerticalDp * gapCount) + theme.bottomPaddingDp
        val desiredHeightPx = (totalHeightDp * density).toInt()
        val widthPx = MeasureSpec.getSize(widthMeasureSpec)
        val heightPx = resolveSize(desiredHeightPx, heightMeasureSpec)
        setMeasuredDimension(widthPx, heightPx)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recomputeKeyBounds()
    }

    fun recomputeKeyBounds() {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return

        val density = resources.displayMetrics.density
        val rows = when (layer) {
            KeyboardLayer.ALPHA -> if (showNumberRow) listOf(numberRow) + alphaRows else alphaRows
            KeyboardLayer.SYMBOLS -> symbolsRows
            KeyboardLayer.EXTENDED_SYMBOLS -> extendedSymbolsRows
        }

        activeKeys.clear()
        keyPositions.clear()

        var keyboardWidth = w.toFloat()
        var keyboardOffsetLeft = 0f
        if (theme.oneHandedMode.enabled) {
            keyboardWidth = w * (theme.oneHandedMode.widthPercent / 100f)
            if (theme.oneHandedMode.gravityRight) {
                keyboardOffsetLeft = w - keyboardWidth
            }
        }

        val gapH = theme.keyGapHorizontalDp * density
        val gapV = theme.keyGapVerticalDp * density
        val baseKeyHeight = theme.keyHeightDp * density
        var currentY = gapV

        for ((rowIdx, row) in rows.withIndex()) {
            val isNumRow = showNumberRow && layer == KeyboardLayer.ALPHA && rowIdx == 0
            val rowKeyHeight = if (isNumRow) baseKeyHeight * NUMBER_ROW_HEIGHT_RATIO else baseKeyHeight
            val totalWeights = row.sumOf { it.widthWeight.toDouble() }.toFloat()
            val totalGaps = (row.size + 1) * gapH
            val availWidth = keyboardWidth - totalGaps
            var currentX = keyboardOffsetLeft + gapH

            for (key in row) {
                val keyWidth = (key.widthWeight / totalWeights) * (availWidth + (row.size - totalWeights) * gapH)
                val bounds = RectF(currentX, currentY, currentX + keyWidth, currentY + rowKeyHeight)
                key.bounds = bounds
                activeKeys.add(key)

                if (key.code.length == 1 && key.code[0].isLetter()) {
                    val char = key.code[0].lowercaseChar()
                    keyPositions[char] = KeyPosition(char, bounds)
                }

                currentX += keyWidth + gapH
            }
            currentY += rowKeyHeight + gapV
        }

        val strokePx = kotlin.math.max(1f, theme.keyBorderWidthDp) * density
        effectEngine.updateKeyGeometry(
            activeKeys.map { it.bounds },
            theme.keyCornerRadiusDp * density,
            strokePx
        )
        onKeyPositionsChanged?.invoke(HashMap(keyPositions))
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelLongPressSecondary()
        resetDeleteGesture(commitSwipe = false)
        resetSpaceGesture()
        effectEngine.clear()
    }

    fun getKeyPositions(): Map<Char, KeyPosition> = HashMap(keyPositions)

    fun isAlphabeticKeyAt(x: Float, y: Float): Boolean = isInAlphabeticGlideZone(x, y)

    fun isInAlphabeticGlideZone(x: Float, y: Float): Boolean {
        val key = findKeyAt(x, y) ?: return false
        return key.code.length == 1 && key.code[0].isLetter()
    }

    fun cancelActiveTouch() {
        popPreviewView?.dismiss()
        cancelLongPressSecondary()
        resetDeleteGesture(commitSwipe = false)
        resetSpaceGesture()
        activePointerId = MotionEvent.INVALID_POINTER_ID
        pressedKey = null
        isSwipeGesture = false
        invalidate()
    }

    private fun cancelLongPressSecondary() {
        deleteHandler.removeCallbacks(secondaryLongPressRunnable)
        longPressSecondaryKey = null
        longPressSecondaryTriggered = false
    }

    private fun resetSpaceGesture() {
        if (isSpaceCursorActive) {
            isSpaceCursorActive = false
            onSpaceSwipeStateChanged?.invoke(false)
        }
        spaceKeyDown = null
        spaceLastStepIndex = 0
    }

    private fun resetDeleteGesture(commitSwipe: Boolean) {
        deleteHandler.removeCallbacks(deleteRepeatRunnable)
        if (isDeleteSwipeActive) {
            onDeleteSwipeFinish?.invoke(commitSwipe)
        }
        deleteKeyDown = null
        hasPerformedRepeatDelete = false
        isDeleteSwipeActive = false
        activeDeleteSwipeCount = 0
        deleteRepeatTicks = 0
    }

    private fun isFunctionalKey(key: KeyDef): Boolean {
        return key.code == "⇧" || key.code == "⌫" || key.code == "?123" || key.code == "ABC" || key.code == "=\\<"
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density

        // 1. Keyboard Surface Background
        when (theme.keyboardBackground.type) {
            BackgroundSpec.Type.SOLID -> {
                bgPaint.shader = null
                bgPaint.color = theme.keyboardBackground.colorStart
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
            }
            BackgroundSpec.Type.GRADIENT -> {
                bgPaint.shader = KeyboardEffectEngine.buildBackgroundShader(
                    theme.keyboardBackground,
                    width.toFloat(),
                    height.toFloat()
                )
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
            }
            BackgroundSpec.Type.IMAGE -> {
                bgPaint.shader = null
                bgPaint.color = Color.BLACK
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
            }
        }

        val hasUnderEffect = effectEngine.drawUnderKeys(canvas, theme, width.toFloat())

        val cornerRadius = theme.keyCornerRadiusDp * density
        val borderStrokePx = theme.keyBorderWidthDp * density
        borderPaint.color = theme.keyBorderColor
        borderPaint.strokeWidth = borderStrokePx

        textPaint.textSize = theme.keyLabelSizeSp * density
        textPaint.typeface = theme.keyLabelFont.toTypeface()

        secondaryTextPaint.color = theme.keyLabelColorSecondary
        secondaryTextPaint.textSize = (theme.keyLabelSizeSp * density * 0.62f).coerceAtLeast(9f * density)
        secondaryTextPaint.typeface = theme.keyLabelFont.toTypeface()

        highlightPaint.color = theme.keyPressHighlightColor
        val accentTint = theme.effectColors.firstOrNull() ?: theme.accentKeyColor ?: theme.trailColor
        val isLightBg = Color.luminance(theme.keyboardBackground.colorStart) > 0.5f
        shadowPaint.color = if (isLightBg) Color.argb(28, 20, 23, 32) else Color.argb(110, 0, 0, 0)
        bevelPaint.strokeWidth = 1f * density
        bevelPaint.color = if (isLightBg) Color.argb(190, 255, 255, 255) else Color.argb(26, 255, 255, 255)

        for (key in activeKeys) {
            val rect = key.bounds
            val isEnter = key.code == "Enter"
            val isSpace = key.code == " "
            val isShift = key.code == "⇧"
            val isFunc = isFunctionalKey(key)

            if (theme.effectStyle == EffectStyle.CYBER_NEON && theme.keyBorderWidthDp > 0f) {
                effectEngine.drawStaticCyberGlow(
                    canvas, rect, cornerRadius, theme.keyBorderColor, borderStrokePx
                )
            }

            // Hardware keycap drop-shadow lip
            if (theme.effectStyle != EffectStyle.CYBER_NEON) {
                val lipRect = RectF(rect.left, rect.top + 1.5f * density, rect.right, rect.bottom + 1.5f * density)
                canvas.drawRoundRect(lipRect, cornerRadius, cornerRadius, shadowPaint)
            }

            val overrideColor = when {
                isEnter -> theme.accentKeyColor
                isFunc -> theme.functionalKeyColor
                else -> null
            }
            if (overrideColor != null) {
                keyPaint.shader = null
                keyPaint.color = overrideColor
            } else if (theme.keyFaceColor.isGradient) {
                keyPaint.shader = LinearGradient(
                    rect.left, rect.top, rect.left, rect.bottom,
                    theme.keyFaceColor.primaryColor,
                    theme.keyFaceColor.secondaryColor,
                    Shader.TileMode.CLAMP
                )
            } else {
                keyPaint.shader = null
                keyPaint.color = theme.keyFaceColor.primaryColor
            }

            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, keyPaint)

            if (theme.effectStyle != EffectStyle.CYBER_NEON && !isEnter) {
                val padX = cornerRadius.coerceAtLeast(4f * density)
                if (rect.right - rect.left > padX * 2f) {
                    canvas.drawLine(
                        rect.left + padX,
                        rect.top + 0.8f * density,
                        rect.right - padX,
                        rect.top + 0.8f * density,
                        bevelPaint
                    )
                }
            }

            if (isSpace && isSpaceCursorActive) {
                spaceCursorPaint.color = Color.argb(
                    64,
                    Color.red(accentTint),
                    Color.green(accentTint),
                    Color.blue(accentTint)
                )
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, spaceCursorPaint)
            }

            if (theme.keyBorderWidthDp > 0f) {
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)
            }

            if (key == pressedKey) {
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, highlightPaint)
            }

            textPaint.color = when {
                isSpace && isSpaceCursorActive -> accentTint
                isSpace -> theme.keyLabelColorSecondary
                isEnter && theme.accentKeyColor != null -> theme.accentKeyTextColor ?: theme.keyLabelColorPrimary
                else -> theme.keyLabelColorPrimary
            }

            val displayText = if (isSpace && isSpaceCursorActive) "◀   Cursor   ▶" else getDisplayText(key)
            val savedSize = textPaint.textSize
            if (isSpace) {
                textPaint.textSize = (theme.keyLabelSizeSp * density * if (isSpaceCursorActive) 0.84f else 0.76f)
            } else if (key.code.length > 2) {
                textPaint.textSize = (theme.keyLabelSizeSp * density * 0.82f)
            }
            val textY = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
            canvas.drawText(displayText, rect.centerX(), textY, textPaint)
            textPaint.textSize = savedSize

            if (isShift && shiftState != ShiftState.OFF) {
                shiftDotPaint.color = accentTint
                canvas.drawCircle(
                    rect.centerX(),
                    rect.bottom - (5.5f * density),
                    2.2f * density,
                    shiftDotPaint
                )
            }

            if (showSecondarySymbols && key.secondaryCode != null && layer == KeyboardLayer.ALPHA) {
                val secX = rect.right - (4.5f * density)
                val secY = rect.top + (12f * density)
                canvas.drawText(key.secondaryCode, secX, secY, secondaryTextPaint)
            }
        }

        val overStrokePx = kotlin.math.max(1.25f, theme.keyBorderWidthDp) * density
        val hasOverEffect = effectEngine.drawOverKeys(canvas, theme, width.toFloat(), overStrokePx)
        if ((hasUnderEffect || hasOverEffect) &&
            isAttachedToWindow &&
            visibility == View.VISIBLE &&
            windowVisibility == View.VISIBLE
        ) {
            postInvalidateOnAnimation()
        }
    }

    private fun getDisplayText(key: KeyDef): String {
        return when {
            key.code == "⇧" -> {
                when (shiftState) {
                    ShiftState.OFF -> "⇧"
                    ShiftState.SHIFTED -> "⬆"
                    ShiftState.CAPS_LOCK -> "⇪"
                }
            }
            key.code.length == 1 && key.code[0].isLetter() -> {
                if (shiftState != ShiftState.OFF) key.code.uppercase() else key.code.lowercase()
            }
            key.code == " " -> spaceBarLabel
            key.code == "Enter" && theme.accentKeyColor != null -> "➤"
            else -> key.code
        }
    }

    private fun findKeyAt(x: Float, y: Float): KeyDef? {
        activeKeys.find { it.bounds.contains(x, y) }?.let { return it }
        val tolPx = 5f * resources.displayMetrics.density
        var bestKey: KeyDef? = null
        var bestDistSq = Float.MAX_VALUE
        for (key in activeKeys) {
            val b = key.bounds
            if (x in (b.left - tolPx)..(b.right + tolPx) && y in (b.top - tolPx)..(b.bottom + tolPx)) {
                val dx = x - b.centerX()
                val dy = y - b.centerY()
                val dSq = dx * dx + dy * dy
                if (dSq < bestDistSq) {
                    bestDistSq = dSq
                    bestKey = key
                }
            }
        }
        return bestKey
    }

    fun handleTouchEvent(event: MotionEvent): Boolean {
        val density = resources.displayMetrics.density

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                val x = event.x
                val y = event.y
                startPointerKeyDown(x, y)
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // Multi-touch 2-thumb rollover for rapid typing
                val prevKey = pressedKey
                if (prevKey != null &&
                    !isSwipeGesture &&
                    !isDeleteSwipeActive &&
                    !isSpaceCursorActive &&
                    deleteKeyDown == null &&
                    spaceKeyDown == null &&
                    !isFunctionalKey(prevKey)
                ) {
                    val commitChar = if (longPressSecondaryTriggered && prevKey.secondaryCode != null) {
                        prevKey.secondaryCode
                    } else {
                        getDisplayText(prevKey)
                    }
                    cancelLongPressSecondary()
                    popPreviewView?.dismiss()
                    onKeyTap?.invoke(commitChar)
                }

                val newIndex = event.actionIndex
                activePointerId = event.getPointerId(newIndex)
                startPointerKeyDown(event.getX(newIndex), event.getY(newIndex))
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val ptrIdx = if (activePointerId != MotionEvent.INVALID_POINTER_ID) {
                    event.findPointerIndex(activePointerId)
                } else {
                    0
                }
                if (ptrIdx < 0) return true
                val x = event.getX(ptrIdx)
                val y = event.getY(ptrIdx)

                val activeDeleteKey = deleteKeyDown
                if (activeDeleteKey != null) {
                    handleDeleteMove(activeDeleteKey, x, y, density)
                    return true
                }

                if (spaceKeyDown != null) {
                    val dx = x - touchDownX
                    val dy = Math.abs(y - touchDownY)
                    val swipeStartPx = SPACE_SWIPE_START_SLOP_DP * density
                    val stepPx = SPACE_SWIPE_STEP_DP * density
                    if (isSpaceCursorActive || (Math.abs(dx) >= swipeStartPx && Math.abs(dx) >= dy)) {
                        if (!isSpaceCursorActive) {
                            isSpaceCursorActive = true
                            isSwipeGesture = true
                            popPreviewView?.dismiss()
                            onSpaceSwipeStateChanged?.invoke(true)
                            invalidate()
                        }
                        val stepIndex = (dx / stepPx).toInt()
                        val delta = stepIndex - spaceLastStepIndex
                        if (delta != 0) {
                            spaceLastStepIndex = stepIndex
                            triggerHapticFeedback(vibrationDurationMs.coerceAtMost(7L))
                            onSpaceSwipeCursorStep?.invoke(delta)
                        }
                        return true
                    }
                }

                val touchSlopPx = 12f * density
                val dist = Math.hypot((x - touchDownX).toDouble(), (y - touchDownY).toDouble()).toFloat()
                if (dist > touchSlopPx) {
                    isSwipeGesture = true
                    cancelLongPressSecondary()
                    popPreviewView?.dismiss()
                }
                val currentKey = findKeyAt(x, y)
                if (currentKey != pressedKey) {
                    cancelLongPressSecondary()
                    pressedKey = currentKey
                    if (pressedKey != null && !isSwipeGesture) {
                        popPreviewView?.show(getDisplayText(pressedKey!!), pressedKey!!.bounds)
                    } else {
                        popPreviewView?.dismiss()
                    }
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val upIndex = event.actionIndex
                val upId = event.getPointerId(upIndex)
                if (upId == activePointerId) {
                    finishPointerKeyUp(event.getX(upIndex), event.getY(upIndex))
                    val nextIndex = if (upIndex == 0) 1 else 0
                    activePointerId = if (nextIndex < event.pointerCount) event.getPointerId(nextIndex) else MotionEvent.INVALID_POINTER_ID
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                val ptrIdx = if (activePointerId != MotionEvent.INVALID_POINTER_ID) {
                    event.findPointerIndex(activePointerId).coerceAtLeast(0)
                } else {
                    0
                }
                val x = if (ptrIdx < event.pointerCount) event.getX(ptrIdx) else event.x
                val y = if (ptrIdx < event.pointerCount) event.getY(ptrIdx) else event.y
                finishPointerKeyUp(x, y)
                activePointerId = MotionEvent.INVALID_POINTER_ID
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                cancelActiveTouch()
                return true
            }
        }
        return false
    }

    private fun startPointerKeyDown(x: Float, y: Float) {
        cancelLongPressSecondary()
        resetDeleteGesture(commitSwipe = false)
        resetSpaceGesture()
        touchDownTime = System.currentTimeMillis()
        touchDownX = x
        touchDownY = y
        isSwipeGesture = false

        pressedKey = findKeyAt(x, y)
        pressedKey?.let { key ->
            triggerHapticFeedback(vibrationDurationMs)
            popPreviewView?.show(getDisplayText(key), key.bounds)
            effectEngine.triggerKeyPress(
                key.bounds.centerX(),
                key.bounds.centerY(),
                key.bounds,
                theme,
                width.toFloat()
            )
            when (key.code) {
                "⌫" -> {
                    deleteKeyDown = key
                    deleteHandler.postDelayed(deleteRepeatRunnable, DELETE_REPEAT_INITIAL_DELAY_MS)
                }
                " " -> {
                    spaceKeyDown = key
                    spaceLastStepIndex = 0
                }
                else -> {
                    if (key.secondaryCode != null && layer == KeyboardLayer.ALPHA) {
                        longPressSecondaryKey = key
                        longPressSecondaryTriggered = false
                        deleteHandler.postDelayed(secondaryLongPressRunnable, LONG_PRESS_SECONDARY_DELAY_MS)
                    }
                }
            }
            Unit
        }
        invalidate()
    }

    private fun finishPointerKeyUp(x: Float, y: Float) {
        popPreviewView?.dismiss()
        val activeDeleteKey = deleteKeyDown
        if (activeDeleteKey != null) {
            val shouldFireSingleTap = !isDeleteSwipeActive && !hasPerformedRepeatDelete && !isSwipeGesture
            resetDeleteGesture(commitSwipe = true)
            resetSpaceGesture()
            cancelLongPressSecondary()
            pressedKey = null
            isSwipeGesture = false
            invalidate()
            if (shouldFireSingleTap) {
                onKeyTap?.invoke("⌫")
            }
            return
        }

        val targetKey = pressedKey ?: findKeyAt(x, y) ?: findKeyAt(touchDownX, touchDownY)
        val wasSwipe = isSwipeGesture || isSpaceCursorActive
        val secTriggered = longPressSecondaryTriggered
        val secValue = longPressSecondaryKey?.secondaryCode

        cancelLongPressSecondary()
        resetSpaceGesture()
        pressedKey = null
        isSwipeGesture = false
        invalidate()

        if (!wasSwipe && targetKey != null) {
            val keyText = if (secTriggered && secValue != null) secValue else getDisplayText(targetKey)
            onKeyTap?.invoke(keyText)
        }
    }

    private fun handleDeleteMove(activeDeleteKey: KeyDef, x: Float, y: Float, density: Float) {
        val leftwardPx = touchDownX - x
        val verticalPx = Math.abs(y - touchDownY)
        val swipeStartSlopPx = DELETE_SWIPE_START_SLOP_DP * density
        val wordStepPx = DELETE_SWIPE_WORD_STEP_DP * density

        if (!isDeleteSwipeActive) {
            if (!hasPerformedRepeatDelete && leftwardPx >= swipeStartSlopPx && leftwardPx >= verticalPx) {
                isDeleteSwipeActive = true
                isSwipeGesture = true
                deleteHandler.removeCallbacks(deleteRepeatRunnable)
                onDeleteSwipeStart?.invoke()
            } else {
                val dist = Math.hypot(leftwardPx.toDouble(), verticalPx.toDouble()).toFloat()
                if (dist > DELETE_REPEAT_CANCEL_DRIFT_DP * density) {
                    deleteHandler.removeCallbacks(deleteRepeatRunnable)
                    isSwipeGesture = true
                    popPreviewView?.dismiss()
                    if (pressedKey != null) {
                        pressedKey = null
                        invalidate()
                    }
                }
                return
            }
        }

        pressedKey = activeDeleteKey
        val requestedWords = if (leftwardPx < swipeStartSlopPx * 0.55f) {
            0
        } else {
            1 + ((leftwardPx - swipeStartSlopPx) / wordStepPx).toInt().coerceAtLeast(0)
        }
        val actualWords = onDeleteSwipeStep?.invoke(requestedWords) ?: requestedWords
        if (actualWords != activeDeleteSwipeCount) {
            activeDeleteSwipeCount = actualWords
            triggerHapticFeedback(vibrationDurationMs.coerceAtMost(10L))
        }
        val label = if (actualWords == 0) "Keep" else "−$actualWords w"
        popPreviewView?.show(label, activeDeleteKey.bounds)
    }

    private fun triggerHapticFeedback(durationMs: Long = vibrationDurationMs) {
        if (durationMs <= 0L) return
        if (vibrator != null && vibrator.hasVibrator()) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(durationMs)
                }
            } catch (_: Exception) {
                // Permission or system vibrator error
            }
        }
    }

    companion object {
        private const val NUMBER_ROW_HEIGHT_RATIO = 0.82f
        private const val LONG_PRESS_SECONDARY_DELAY_MS = 360L
        private const val DELETE_REPEAT_INITIAL_DELAY_MS = 380L
        private const val DELETE_CHAR_INTERVAL_MS = 55L
        private const val DELETE_WORD_INTERVAL_MS = 150L
        private const val DELETE_CHAR_TICKS_BEFORE_WORDS = 5
        private const val DELETE_SWIPE_START_SLOP_DP = 18f
        private const val DELETE_SWIPE_WORD_STEP_DP = 28f
        private const val DELETE_REPEAT_CANCEL_DRIFT_DP = 26f
        private const val SPACE_SWIPE_START_SLOP_DP = 10f
        private const val SPACE_SWIPE_STEP_DP = 10f
    }
}
