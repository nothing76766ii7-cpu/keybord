package com.keybord.engine.glide

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.keybord.theme.BuiltInThemes
import com.keybord.theme.ThemeModel

/**
 * Transparent overlay view that renders the glowing, tapering glide trail
 * as the user swipes across the keyboard, styled dynamically by ThemeModel.
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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (trailPoints.size < 2) return

        val now = System.currentTimeMillis()
        pruneOldPoints(now)

        val density = resources.displayMetrics.density
        val baseStrokeWidth = theme.trailWidthDp * density
        val maxTrailAgeMs = theme.trailFadeDurationMs

        for (i in 0 until trailPoints.size - 1) {
            val p1 = trailPoints[i]
            val p2 = trailPoints[i + 1]

            val age = now - p2.timestamp
            val alphaFactor = (1.0f - (age.toFloat() / maxTrailAgeMs)).coerceIn(0.1f, 1.0f)
            val widthFactor = (i.toFloat() / trailPoints.size).coerceIn(0.2f, 1.0f)

            paint.alpha = (alphaFactor * 230).toInt()
            paint.strokeWidth = baseStrokeWidth * widthFactor

            canvas.drawLine(p1.x, p1.y, p2.x, p2.y, paint)
        }

        if (trailPoints.isNotEmpty()) {
            postInvalidateOnAnimation()
        }
    }
}
