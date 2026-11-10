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
            invalidate()
        }

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

    private val sampleRows = listOf(
        listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
        listOf("A", "S", "D", "F", "G", "H", "J", "K", "L"),
        listOf("⇧", "Z", "X", "C", "V", "B", "N", "M", "⌫"),
        listOf("?123", "☺", " ", "Enter")
    )

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

        // 3. Draw Keys
        val keyHeight = (theme.keyHeightDp * density).coerceAtMost((height - stripHeight) / 4.5f)
        val cornerRadius = theme.keyCornerRadiusDp * density
        val gapH = theme.keyGapHorizontalDp * density
        val gapV = theme.keyGapVerticalDp * density

        borderPaint.color = theme.keyBorderColor
        borderPaint.strokeWidth = (theme.keyBorderWidthDp * density).coerceAtLeast(0.5f)

        textPaint.color = theme.keyLabelColorPrimary
        textPaint.textSize = (theme.keyLabelSizeSp * density).coerceAtMost(keyHeight * 0.45f)
        textPaint.typeface = theme.keyLabelFont.toTypeface()

        secondaryTextPaint.color = theme.keyLabelColorSecondary
        secondaryTextPaint.textSize = (theme.keyLabelSizeSp * density * 0.65f).coerceAtMost(keyHeight * 0.3f)
        secondaryTextPaint.typeface = theme.keyLabelFont.toTypeface()

        var currentY = stripHeight + gapV

        for (row in sampleRows) {
            val totalGaps = (row.size + 1) * gapH
            val availWidth = width.toFloat() - totalGaps
            var currentX = gapH

            // Check if space row
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

                // Key Face
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
                if (theme.keyBorderWidthDp > 0f) {
                    canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)
                }

                // Primary label
                val textY = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
                canvas.drawText(key, rect.centerX(), textY, textPaint)

                currentX += keyWidth + gapH
            }
            currentY += keyHeight + gapV
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
