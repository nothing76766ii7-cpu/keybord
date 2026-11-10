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
        targetKeyBounds = bounds
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

        val density = resources.displayMetrics.density
        val scale = theme.keyPopPreviewSize.scaleFactor

        val popWidth = bounds.width() * scale
        val popHeight = bounds.height() * scale
        val popLeft = bounds.centerX() - popWidth / 2f
        val popTop = bounds.top - popHeight - (6f * density)
        val popRect = RectF(popLeft, popTop, popLeft + popWidth, popTop + popHeight)

        // Composite semi-transparent key face colors over surface so popup remains opaque & legible
        bgPaint.color = KeyboardEffectEngine.compositeOver(
            theme.keyFaceColor.primaryColor,
            theme.keyboardBackground.colorStart
        )
        borderPaint.color = theme.keyBorderColor
        borderPaint.strokeWidth = 2f * density

        val radius = theme.keyCornerRadiusDp * density * scale
        if (theme.effectStyle == EffectStyle.CYBER_NEON ||
            theme.effectStyle == EffectStyle.MECH_GLOW ||
            theme.effectStyle == EffectStyle.GLOW_FLOW
        ) {
            haloPaint.color = theme.trailColor
            haloPaint.alpha = 72
            haloPaint.strokeWidth = 4.5f * density
            canvas.drawRoundRect(popRect, radius, radius, haloPaint)
        }
        canvas.drawRoundRect(popRect, radius, radius, bgPaint)
        canvas.drawRoundRect(popRect, radius, radius, borderPaint)

        textPaint.color = theme.keyLabelColorPrimary
        textPaint.textSize = theme.keyLabelSizeSp * density * scale * 1.2f
        textPaint.typeface = theme.keyLabelFont.toTypeface()

        val textY = popRect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(text, popRect.centerX(), textY, textPaint)
    }
}
