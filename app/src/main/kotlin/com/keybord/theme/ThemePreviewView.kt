package com.keybord.theme

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.View

/**
 * Real-time rendering preview widget that illustrates how a ThemeModel looks,
 * rendering keys, suggestion strip, and gesture trail dynamically.
 */
class ThemePreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var theme: ThemeModel = BuiltInThemes.MATERIAL_MIDNIGHT
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    var showNumberRow: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
                invalidate()
            }
        }

    private val effectEngine = KeyboardEffectEngine()
    private val previewKeyRects = ArrayList<RectF>()
    private val previewKeyLabels = ArrayList<Pair<String, RectF>>()
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val secondaryTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
    }
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val numberRow = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
    private val sampleRows = listOf(
        listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
        listOf("A", "S", "D", "F", "G", "H", "J", "K", "L"),
        listOf("⇧", "Z", "X", "C", "V", "B", "N", "M", "⌫"),
        listOf("?123", "☺", " ", "Enter")
    )

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = resources.displayMetrics.density
        val stripDp = 36f
        val rowCount = if (showNumberRow) 5 else 4
        val previewScale = 0.72f
        val desiredDp = stripDp +
            (theme.keyHeightDp * previewScale * rowCount) +
            (theme.keyGapVerticalDp * (rowCount + 1)) +
            (theme.bottomPaddingDp * 0.55f)
        val desiredPx = (desiredDp * density).toInt()
        val widthPx = MeasureSpec.getSize(widthMeasureSpec)
        val heightPx = resolveSize(desiredPx, heightMeasureSpec)
        setMeasuredDimension(widthPx, heightPx)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density

        // 1. Draw Keyboard Background
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

        // 2. Draw Suggestion Strip
        val stripHeight = 36f * density
        val stripPaint = Paint().apply {
            color = theme.suggestionStripBgColor
        }
        canvas.drawRect(0f, 0f, width.toFloat(), stripHeight, stripPaint)

        val stripTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.suggestionStripTextColor
            textSize = 13f * density
            textAlign = Paint.Align.CENTER
        }
        val candidates = listOf("📋", "Keybord", "custom", "the", "⚙")
        val stripColWidth = width.toFloat() / candidates.size
        candidates.forEachIndexed { idx, txt ->
            canvas.drawText(
                txt,
                stripColWidth * idx + stripColWidth / 2f,
                stripHeight * 0.65f,
                stripTextPaint
            )
        }

        // 3. Layout sample keys accurately (reflects height, padding, gaps & one-handed mode)
        val rows = if (showNumberRow) listOf(numberRow) + sampleRows else sampleRows
        val cornerRadius = theme.keyCornerRadiusDp * density
        val gapH = theme.keyGapHorizontalDp * density
        val gapV = theme.keyGapVerticalDp * density
        val borderStrokePx = (theme.keyBorderWidthDp * density).coerceAtLeast(0.5f)
        val bottomPadPx = theme.bottomPaddingDp * density * 0.45f
        val availKeysHeight = (height - stripHeight - bottomPadPx - (gapV * (rows.size + 1))).coerceAtLeast(32f * density)
        val maxRowH = availKeysHeight / rows.size.toFloat()
        val desiredRowH = theme.keyHeightDp * density * 0.72f
        val keyHeight = if (desiredRowH <= maxRowH) {
            desiredRowH
        } else {
            val scaleFactor = (theme.keyHeightDp / 64f).coerceIn(0.62f, 1.0f)
            maxRowH * scaleFactor
        }

        var kbWidth = width.toFloat()
        var kbOffsetX = 0f
        if (theme.oneHandedMode.enabled) {
            kbWidth = width * (theme.oneHandedMode.widthPercent / 100f)
            if (theme.oneHandedMode.gravityRight) {
                kbOffsetX = width - kbWidth
            }
        }

        borderPaint.color = theme.keyBorderColor
        borderPaint.strokeWidth = borderStrokePx

        textPaint.textSize = (theme.keyLabelSizeSp * density).coerceAtMost(keyHeight * 0.48f)
        textPaint.typeface = theme.keyLabelFont.toTypeface()

        secondaryTextPaint.color = theme.keyLabelColorSecondary
        secondaryTextPaint.textSize = (theme.keyLabelSizeSp * density * 0.62f).coerceAtMost(keyHeight * 0.3f)
        secondaryTextPaint.typeface = theme.keyLabelFont.toTypeface()

        previewKeyRects.clear()
        previewKeyLabels.clear()
        var currentY = stripHeight + gapV
        for (row in rows) {
            val totalGaps = (row.size + 1) * gapH
            val availWidth = kbWidth - totalGaps
            var currentX = kbOffsetX + gapH
            val isBottomRow = (row.size == 4)

            for (key in row) {
                val keyWidth = when {
                    isBottomRow && key == " " -> availWidth * 0.5f
                    isBottomRow && key == "?123" -> availWidth * 0.18f
                    isBottomRow && key == "Enter" -> availWidth * 0.22f
                    isBottomRow -> availWidth * 0.1f
                    key == "⇧" || key == "⌫" -> (availWidth / 10f) * 1.4f
                    else -> availWidth / 10f
                }
                val rect = RectF(currentX, currentY, currentX + keyWidth, currentY + keyHeight)
                previewKeyRects.add(rect)
                previewKeyLabels.add(key to rect)
                currentX += keyWidth + gapH
            }
            currentY += keyHeight + gapV
        }

        // Render under-key effects (Ripple rings) before key faces
        if (theme.effectStyle == EffectStyle.RIPPLE) {
            effectEngine.drawPreviewEffects(
                canvas, theme, previewKeyRects, cornerRadius, borderStrokePx, width.toFloat(), height.toFloat()
            )
        }

        val isLightBg = Color.luminance(theme.keyboardBackground.colorStart) > 0.5f
        val shadowP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isLightBg) Color.argb(34, 15, 23, 42) else Color.argb(130, 0, 0, 0)
        }
        val lipP = Paint(Paint.ANTI_ALIAS_FLAG)
        val bevelP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = 1f * density
            color = if (isLightBg) Color.argb(200, 255, 255, 255) else Color.argb(46, 255, 255, 255)
        }

        for ((key, rect) in previewKeyLabels) {
            val isEnter = key == "Enter"
            val isSpace = key == " "
            val isFunc = key == "⇧" || key == "⌫" || key == "?123"
            val overrideColor = when {
                isEnter -> theme.accentKeyColor
                isFunc -> theme.functionalKeyColor
                else -> null
            }

            if (theme.effectStyle == EffectStyle.CYBER_NEON && theme.keyBorderWidthDp > 0f) {
                effectEngine.drawStaticCyberGlow(canvas, rect, cornerRadius, theme.keyBorderColor, borderStrokePx)
            }

            val topC: Int
            val botC: Int
            if (overrideColor != null) {
                topC = com.keybord.ui.UiKit.shadeColor(overrideColor, 0.12f)
                botC = com.keybord.ui.UiKit.shadeColor(overrideColor, -0.14f)
            } else if (theme.keyFaceColor.isGradient) {
                topC = theme.keyFaceColor.primaryColor
                botC = theme.keyFaceColor.secondaryColor
            } else {
                topC = com.keybord.ui.UiKit.shadeColor(theme.keyFaceColor.primaryColor, 0.08f)
                botC = com.keybord.ui.UiKit.shadeColor(theme.keyFaceColor.primaryColor, -0.10f)
            }

            if (theme.effectStyle != EffectStyle.CYBER_NEON) {
                canvas.drawRoundRect(
                    RectF(rect.left, rect.top + 1.8f * density, rect.right, rect.bottom + 1.8f * density),
                    cornerRadius, cornerRadius, shadowP
                )
                lipP.color = com.keybord.ui.UiKit.shadeColor(botC, -0.45f)
                canvas.drawRoundRect(
                    RectF(rect.left, rect.top + 1.1f * density, rect.right, rect.bottom + 1.1f * density),
                    cornerRadius, cornerRadius, lipP
                )
            }

            keyPaint.shader = if (Color.alpha(topC) >= 250 && topC != botC) {
                LinearGradient(rect.left, rect.top, rect.left, rect.bottom, topC, botC, Shader.TileMode.CLAMP)
            } else null
            keyPaint.color = topC

            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, keyPaint)
            keyPaint.shader = null

            val padX = cornerRadius.coerceAtLeast(3f * density)
            if (theme.effectStyle != EffectStyle.CYBER_NEON && rect.width() > padX * 2f) {
                canvas.drawLine(rect.left + padX, rect.top + 0.7f * density, rect.right - padX, rect.top + 0.7f * density, bevelP)
            }

            if (theme.keyBorderWidthDp > 0f) {
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)
            }

            textPaint.color = when {
                isEnter && theme.accentKeyColor != null -> theme.accentKeyTextColor ?: theme.keyLabelColorPrimary
                isSpace -> theme.keyLabelColorSecondary
                else -> theme.keyLabelColorPrimary
            }
            val labelText = when {
                isSpace -> "English"
                isEnter && theme.accentKeyColor != null -> "➤"
                else -> key
            }
            val savedSize = textPaint.textSize
            if (isSpace) textPaint.textSize = savedSize * 0.7f
            val textY = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
            canvas.drawText(labelText, rect.centerX(), textY, textPaint)
            textPaint.textSize = savedSize
        }

        // Render over-key effects (Mech Glow rainbow edge sweep / Key Pulse / Glow Flow)
        if (theme.effectStyle == EffectStyle.MECH_GLOW ||
            theme.effectStyle == EffectStyle.KEY_PULSE ||
            theme.effectStyle == EffectStyle.GLOW_FLOW
        ) {
            effectEngine.drawPreviewEffects(
                canvas, theme, previewKeyRects, cornerRadius, borderStrokePx, width.toFloat(), height.toFloat()
            )
        }

        // 4. Draw Sample Gesture Trail
        trailPaint.color = theme.trailColor
        trailPaint.strokeWidth = theme.trailWidthDp * density
        trailPaint.alpha = 200

        val trailPath = Path().apply {
            moveTo(width * 0.3f, stripHeight + keyHeight * 1.5f)
            quadTo(
                width * 0.5f, stripHeight + keyHeight * 0.8f,
                width * 0.7f, stripHeight + keyHeight * 1.8f
            )
        }
        canvas.drawPath(trailPath, trailPaint)
    }
}
