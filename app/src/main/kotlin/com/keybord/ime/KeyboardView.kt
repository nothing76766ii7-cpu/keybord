package com.keybord.ime

import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.keybord.engine.glide.KeyPosition
import com.keybord.theme.*

/**
 * High-performance custom KeyboardView rendering dynamic layouts,
 * custom theme geometries, gradients, fonts, and touch hitboxes.
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
            recomputeKeyBounds()
            invalidate()
        }

    var onKeyTap: ((String) -> Unit)? = null
    var onKeyLongPress: ((String) -> Unit)? = null
    var onKeyPositionsChanged: ((Map<Char, KeyPosition>) -> Unit)? = null

    var popPreviewView: KeyPopPreviewView? = null

    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    // Drawing paints
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val secondaryTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.RIGHT }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Key definition & Hitbox mapping
    data class KeyDef(
        val code: String,
        val secondaryCode: String? = null,
        val widthWeight: Float = 1.0f,
        var bounds: RectF = RectF()
    )

    private val keyPositions = HashMap<Char, KeyPosition>()
    private val activeKeys = mutableListOf<KeyDef>()
    private var pressedKey: KeyDef? = null

    private var touchDownTime = 0L
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var isSwipeGesture = false

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
            KeyDef("⇧", widthWeight = 1.4f), KeyDef("z"), KeyDef("x"), KeyDef("c"),
            KeyDef("v"), KeyDef("b"), KeyDef("n"), KeyDef("m"), KeyDef("⌫", widthWeight = 1.4f)
        ),
        listOf(
            KeyDef("?123", widthWeight = 1.4f), KeyDef(",", widthWeight = 1.0f),
            KeyDef(" ", widthWeight = 4.2f), KeyDef(".", widthWeight = 1.0f),
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
        val totalHeightDp = (theme.keyHeightDp * 4f) + (theme.keyGapVerticalDp * 5f) + theme.bottomPaddingDp
        val desiredHeightPx = (totalHeightDp * density).toInt()
        val widthPx = MeasureSpec.getSize(widthMeasureSpec)
        val heightPx = resolveSize(desiredHeightPx, heightMeasureSpec)
        setMeasuredDimension(widthPx, heightPx)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recomputeKeyBounds()
    }

    /**
     * Calculates precise key hitboxes according to active geometry, gaps, and one-handed mode.
     */
    fun recomputeKeyBounds() {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return

        val density = resources.displayMetrics.density
        val rows = when (layer) {
            KeyboardLayer.ALPHA -> alphaRows
            KeyboardLayer.SYMBOLS -> symbolsRows
            KeyboardLayer.EXTENDED_SYMBOLS -> extendedSymbolsRows
        }

        activeKeys.clear()
        keyPositions.clear()

        // One-handed mode calculation
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
        val keyHeight = theme.keyHeightDp * density
        var currentY = gapV

        for (row in rows) {
            val totalWeights = row.sumOf { it.widthWeight.toDouble() }.toFloat()
            val totalGaps = (row.size + 1) * gapH
            val availWidth = keyboardWidth - totalGaps
            var currentX = keyboardOffsetLeft + gapH

            for (key in row) {
                val keyWidth = (key.widthWeight / totalWeights) * (availWidth + (row.size - totalWeights) * gapH)
                val bounds = RectF(currentX, currentY, currentX + keyWidth, currentY + keyHeight)
                key.bounds = bounds
                activeKeys.add(key)

                // If alphabetic, register with KeyPosition for GlideDecoder
                if (key.code.length == 1 && key.code[0].isLetter()) {
                    val char = key.code[0].lowercaseChar()
                    keyPositions[char] = KeyPosition(char, bounds)
                }

                currentX += keyWidth + gapH
            }
            currentY += keyHeight + gapV
        }

        onKeyPositionsChanged?.invoke(HashMap(keyPositions))
    }

    fun getKeyPositions(): Map<Char, KeyPosition> = HashMap(keyPositions)

    fun isAlphabeticKeyAt(x: Float, y: Float): Boolean {
        val key = findKeyAt(x, y) ?: return false
        return key.code.length == 1 && key.code[0].isLetter()
    }

    fun cancelActiveTouch() {
        popPreviewView?.dismiss()
        pressedKey = null
        isSwipeGesture = false
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density

        // 1. Draw Keyboard Surface Background
        when (theme.keyboardBackground.type) {
            BackgroundSpec.Type.SOLID -> {
                bgPaint.shader = null
                bgPaint.color = theme.keyboardBackground.colorStart
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
            }
            BackgroundSpec.Type.GRADIENT -> {
                bgPaint.shader = LinearGradient(
                    0f, 0f, 0f, height.toFloat(),
                    theme.keyboardBackground.colorStart,
                    theme.keyboardBackground.colorEnd,
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
            }
            BackgroundSpec.Type.IMAGE -> {
                bgPaint.color = Color.BLACK
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
            }
        }

        val cornerRadius = theme.keyCornerRadiusDp * density
        borderPaint.color = theme.keyBorderColor
        borderPaint.strokeWidth = theme.keyBorderWidthDp * density

        textPaint.color = theme.keyLabelColorPrimary
        textPaint.textSize = theme.keyLabelSizeSp * density
        textPaint.typeface = theme.keyLabelFont.toTypeface()

        secondaryTextPaint.color = theme.keyLabelColorSecondary
        secondaryTextPaint.textSize = theme.keyLabelSizeSp * density * 0.65f
        secondaryTextPaint.typeface = theme.keyLabelFont.toTypeface()

        highlightPaint.color = theme.keyPressHighlightColor

        for (key in activeKeys) {
            val rect = key.bounds

            // Key Face Fill
            if (theme.keyFaceColor.isGradient) {
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

            // Key Border
            if (theme.keyBorderWidthDp > 0f) {
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)
            }

            // Key Press Highlight
            if (key == pressedKey) {
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, highlightPaint)
            }

            // Key Text Label
            val displayText = getDisplayText(key)
            val textY = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
            canvas.drawText(displayText, rect.centerX(), textY, textPaint)

            // Secondary Label (if present)
            if (key.secondaryCode != null && layer == KeyboardLayer.ALPHA) {
                val secX = rect.right - (4f * density)
                val secY = rect.top + (12f * density)
                canvas.drawText(key.secondaryCode, secX, secY, secondaryTextPaint)
            }
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
            else -> key.code
        }
    }

    private fun findKeyAt(x: Float, y: Float): KeyDef? {
        return activeKeys.find { it.bounds.contains(x, y) }
    }

    fun handleTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownTime = System.currentTimeMillis()
                touchDownX = x
                touchDownY = y
                isSwipeGesture = false

                pressedKey = findKeyAt(x, y)
                pressedKey?.let { key ->
                    triggerHapticFeedback()
                    popPreviewView?.show(getDisplayText(key), key.bounds)
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val touchSlopPx = 16f * resources.displayMetrics.density
                val dist = Math.hypot((x - touchDownX).toDouble(), (y - touchDownY).toDouble()).toFloat()
                if (dist > touchSlopPx) {
                    isSwipeGesture = true
                    popPreviewView?.dismiss()
                }
                val currentKey = findKeyAt(x, y)
                if (currentKey != pressedKey) {
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

            MotionEvent.ACTION_UP -> {
                popPreviewView?.dismiss()
                val targetKey = pressedKey ?: findKeyAt(x, y) ?: findKeyAt(touchDownX, touchDownY)
                val wasSwipe = isSwipeGesture
                pressedKey = null
                isSwipeGesture = false
                invalidate()

                if (!wasSwipe && targetKey != null) {
                    val keyText = getDisplayText(targetKey)
                    onKeyTap?.invoke(keyText)
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                popPreviewView?.dismiss()
                pressedKey = null
                isSwipeGesture = false
                invalidate()
                return true
            }
        }
        return false
    }

    private fun triggerHapticFeedback() {
        if (vibrator != null && vibrator.hasVibrator()) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(15)
                }
            } catch (e: Exception) {
                // Permission or system vibrator error
            }
        }
    }
}
