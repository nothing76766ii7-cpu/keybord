package com.keybord.ime

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.keybord.theme.BuiltInThemes
import com.keybord.theme.EffectStyle
import com.keybord.theme.KeyboardEffectEngine
import com.keybord.theme.ThemeModel

/**
 * Key pop preview bubble displayed above the active pressed key.
 */
class KeyPopPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var activeKeyText: String? = null
    var targetKeyBounds: RectF? = null

    var theme: ThemeModel = BuiltInThemes.MATERIAL_MIDNIGHT
        set(value) {
            field = value
            invalidate()
        }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    fun show(key: String, bounds: RectF) {
        if (!theme.keyPopPreviewEnabled) return
        activeKeyText = key
        targetKeyBounds = RectF(bounds)
        visibility = View.VISIBLE
        invalidate()
    }

    fun dismiss() {
        activeKeyText = null
        targetKeyBounds = null
        visibility = View.GONE
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = resolveSize(suggestedMinimumWidth, widthMeasureSpec)
        val height = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
            MeasureSpec.getSize(heightMeasureSpec)
        } else {
            suggestedMinimumHeight
        }
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val text = activeKeyText ?: return
        val bounds = targetKeyBounds ?: return
        if (width <= 0 || height <= 0 || bounds.width() <= 0f || bounds.height() <= 0f) return

        val density = resources.displayMetrics.density
        val scale = theme.keyPopPreviewSize.scaleFactor
        val textScale = if (text.length > 2) 0.72f else 1.2f

        textPaint.color = theme.keyLabelColorPrimary
        textPaint.textSize = (theme.keyLabelSizeSp * density * scale * textScale).coerceAtLeast(1f)
        textPaint.typeface = theme.keyLabelFont.toTypeface()

        val minLabelWidth = if (text.length > 2) textPaint.measureText(text) + 18f * density else 0f
        val popWidth = kotlin.math.max(bounds.width() * scale, minLabelWidth).coerceAtLeast(1f)
        val popHeight = (bounds.height() * scale).coerceAtLeast(1f)
        val margin = 4f * density
        val maxLeft = (width.toFloat() - popWidth - margin).coerceAtLeast(margin)
        val popLeft = (bounds.centerX() - popWidth / 2f).coerceIn(minOf(margin, maxLeft), maxOf(margin, maxLeft))
        val popTop = (bounds.top - popHeight - (8f * density)).coerceAtLeast(margin)
        val popRect = RectF(popLeft, popTop, popLeft + popWidth, popTop + popHeight)

        // Composite semi-transparent key face colors over surface so popup remains opaque & legible
        bgPaint.color = KeyboardEffectEngine.compositeOver(
            theme.keyFaceColor.primaryColor,
            theme.keyboardBackground.colorStart
        )
        borderPaint.color = theme.keyBorderColor
        borderPaint.strokeWidth = 1.5f * density

        val desiredRadius = (theme.keyCornerRadiusDp * density * scale).coerceAtLeast(10f * density)
        val maxVisualRadius = (minOf(popRect.width(), popRect.height()) * 0.45f).coerceAtLeast(2f * density)
        val radius = desiredRadius.coerceAtMost(maxVisualRadius)
        // Soft drop shadow
        haloPaint.style = Paint.Style.FILL
        haloPaint.color = Color.argb(85, 0, 0, 0)
        canvas.drawRoundRect(
            RectF(popRect.left, popRect.top + 3f * density, popRect.right, popRect.bottom + 4f * density),
            radius,
            radius,
            haloPaint
        )

        if (theme.effectStyle == EffectStyle.CYBER_NEON ||
            theme.effectStyle == EffectStyle.MECH_GLOW ||
            theme.effectStyle == EffectStyle.GLOW_FLOW
        ) {
            haloPaint.style = Paint.Style.STROKE
            haloPaint.color = theme.trailColor
            haloPaint.alpha = 80
            haloPaint.strokeWidth = 4f * density
            canvas.drawRoundRect(popRect, radius, radius, haloPaint)
        }

        // Draw bubble + bottom pointer caret pointing to keycap (safe against large corner radiuses)
        val caretHalfWidth = 6f * density
        val caretX = clampCaretX(bounds.centerX(), popRect.left, popRect.right, radius, caretHalfWidth)
        val caretPath = Path().apply {
            moveTo(caretX - caretHalfWidth, popRect.bottom - 1f * density)
            lineTo(caretX, popRect.bottom + caretHalfWidth)
            lineTo(caretX + caretHalfWidth, popRect.bottom - 1f * density)
            close()
        }
        canvas.drawRoundRect(popRect, radius, radius, bgPaint)
        canvas.drawPath(caretPath, bgPaint)
        canvas.drawRoundRect(popRect, radius, radius, borderPaint)

        val textY = popRect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(text, popRect.centerX(), textY, textPaint)
    }

    companion object {
        internal fun clampCaretX(
            centerX: Float,
            popLeft: Float,
            popRight: Float,
            radius: Float,
            caretHalfWidth: Float
        ): Float {
            if (!centerX.isFinite() || !popLeft.isFinite() || !popRight.isFinite()) {
                return if (popLeft.isFinite()) popLeft else 0f
            }
            val safeRadius = if (radius.isFinite()) radius.coerceAtLeast(0f) else 0f
            val safeHalfW = if (caretHalfWidth.isFinite()) caretHalfWidth.coerceAtLeast(0f) else 0f
            val width = (popRight - popLeft).coerceAtLeast(0f)
            val maxInset = ((width - safeHalfW * 2f) * 0.5f).coerceAtLeast(0f)
            val inset = minOf(safeRadius, maxInset)
            val lo = minOf(popLeft + inset, popRight - inset)
            val hi = maxOf(popLeft + inset, popRight - inset)
            return centerX.coerceIn(lo, hi)
        }
    }
}
