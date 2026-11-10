package com.keybord.engine.glide

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.keybord.theme.BuiltInThemes
import com.keybord.theme.EffectStyle
import com.keybord.theme.KeyboardEffectEngine
import com.keybord.theme.ThemeModel

/**
 * Transparent overlay view that renders the glowing, tapering glide trail
 * as the user swipes across the keyboard, styled dynamically by ThemeModel & EffectStyle.
 */
class GestureOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val trailPoints = mutableListOf<TouchPoint>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4285F4")
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    var theme: ThemeModel = BuiltInThemes.MATERIAL_MIDNIGHT
        set(value) {
            field = value
            paint.color = value.trailColor
            invalidate()
        }

    fun addPoint(x: Float, y: Float) {
        val now = System.currentTimeMillis()
        trailPoints.add(TouchPoint(x, y, now))
        pruneOldPoints(now)
        invalidate()
    }

    fun clearTrail() {
        trailPoints.clear()
        invalidate()
    }

    private fun pruneOldPoints(currentTime: Long) {
        val maxTrailAgeMs = theme.trailFadeDurationMs
        val iterator = trailPoints.iterator()
        while (iterator.hasNext()) {
            val pt = iterator.next()
            if (currentTime - pt.timestamp > maxTrailAgeMs) {
                iterator.remove()
            } else {
                break
            }
        }
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
        if (trailPoints.size < 2) return

        val now = System.currentTimeMillis()
        pruneOldPoints(now)

        val density = resources.displayMetrics.density
        val baseStrokeWidth = theme.trailWidthDp * density
        val maxTrailAgeMs = theme.trailFadeDurationMs
        val hasHalo = theme.effectStyle == EffectStyle.CYBER_NEON ||
            theme.effectStyle == EffectStyle.GLOW_FLOW ||
            theme.effectStyle == EffectStyle.MECH_GLOW ||
            theme.effectStyle == EffectStyle.KEY_PULSE
        val ripplePalette = if (theme.effectStyle == EffectStyle.RIPPLE) {
            KeyboardEffectEngine.resolveRipplePalette(theme)
        } else {
            null
        }
        val segmentCount = trailPoints.size - 1

        for (i in 0 until segmentCount) {
            val p1 = trailPoints[i]
            val p2 = trailPoints[i + 1]

            val age = now - p2.timestamp
            val alphaFactor = (1.0f - (age.toFloat() / maxTrailAgeMs)).coerceIn(0.1f, 1.0f)
            val widthFactor = (i.toFloat() / trailPoints.size).coerceIn(0.2f, 1.0f)
            val coreWidth = baseStrokeWidth * widthFactor

            val segmentColor = when (theme.effectStyle) {
                EffectStyle.MECH_GLOW -> {
                    val hue = ((i.toFloat() / segmentCount) * 320f + (now % 1800L) * 0.2f) % 360f
                    Color.HSVToColor(floatArrayOf(hue, 0.95f, 1f))
                }
                EffectStyle.RIPPLE -> {
                    val pal = ripplePalette!!
                    pal[(i * pal.size / segmentCount.coerceAtLeast(1)) % pal.size]
                }
                else -> theme.trailColor
            }

            if (hasHalo) {
                haloPaint.color = segmentColor
                haloPaint.alpha = (alphaFactor * 72).toInt()
                haloPaint.strokeWidth = coreWidth * 1.9f
                canvas.drawLine(p1.x, p1.y, p2.x, p2.y, haloPaint)
            }

            paint.color = segmentColor
            paint.alpha = (alphaFactor * 230).toInt()
            paint.strokeWidth = coreWidth
            canvas.drawLine(p1.x, p1.y, p2.x, p2.y, paint)
        }

        if (trailPoints.isNotEmpty()) {
            postInvalidateOnAnimation()
        }
    }
}
