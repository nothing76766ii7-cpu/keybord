package com.keybord.theme

import android.graphics.*
import android.os.Build
import android.os.SystemClock
import android.view.animation.OvershootInterpolator
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Shared visual effect engine and background shader helper for both live IME KeyboardView
 * and ThemePreviewView cards. Implements inter-key Ripple waves, Mech Glow rainbow
 * keycap border shockwaves, Cyber Neon glow halos, and expanding Key Pulse highlights.
 */
class KeyboardEffectEngine {

    private data class RadialWave(
        val startMs: Long,
        val shader: RadialGradient,
        val cx: Float,
        val cy: Float
    )

    private data class KeyPulseWave(
        val startMs: Long,
        val rect: RectF,
        val color: Int
    )

    private val radialWaves = ArrayList<RadialWave>()
    private val keyPulses = ArrayList<KeyPulseWave>()

    private val rippleInterpolator = OvershootInterpolator(0.5f)
    private val shaderMatrix = Matrix()

    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val mechGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val cyberHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val fillKeysPath = Path()
    private val strokeKeysPath = Path()
    private var cachedCornerRadiusPx = 0f
    private var mechHueDeg = 0f

    fun clear() {
        radialWaves.clear()
        keyPulses.clear()
        ripplePaint.shader = null
        mechGlowPaint.shader = null
    }

    fun hasActiveAnimations(): Boolean = radialWaves.isNotEmpty() || keyPulses.isNotEmpty()

    /**
     * Rebuilds cached keycap paths used for clipping inter-key ripples and stroking
     * key outlines during Mech Glow sweeps. Called on layout or theme change, not per frame.
     */
    fun updateKeyGeometry(rects: List<RectF>, cornerRadiusPx: Float, strokeWidthPx: Float) {
        cachedCornerRadiusPx = cornerRadiusPx
        fillKeysPath.reset()
        strokeKeysPath.reset()
        val inset = strokeWidthPx / 2f
        for (r in rects) {
            fillKeysPath.addRoundRect(r, cornerRadiusPx, cornerRadiusPx, Path.Direction.CW)
            val outlineRect = RectF(
                r.left + inset,
                r.top + inset,
                r.right - inset,
                r.bottom - inset
            )
            strokeKeysPath.addRoundRect(outlineRect, cornerRadiusPx, cornerRadiusPx, Path.Direction.CW)
        }
    }

    /**
     * Spawns a new wave/pulse animation anchored at the pressed key.
     */
    fun triggerKeyPress(
        cx: Float,
        cy: Float,
        keyRect: RectF,
        theme: ThemeModel,
        viewWidth: Float
    ) {
        if (!theme.keyPressRippleEnabled || theme.effectStyle == EffectStyle.NONE || viewWidth <= 0f) {
            return
        }
        val now = SystemClock.uptimeMillis()
        when (theme.effectStyle) {
            EffectStyle.RIPPLE -> {
                val baseColor = resolveRipplePalette(theme).random()
                radialWaves.add(RadialWave(now, buildRippleShader(0f, 0f, 1f, baseColor), cx, cy))
            }
            EffectStyle.MECH_GLOW -> {
                mechHueDeg = (mechHueDeg + MECH_HUE_STEP_DEG) % 360f
                val vivid = Color.HSVToColor(floatArrayOf(mechHueDeg, 1f, 1f))
                val soft = withAlpha(vivid, 0.4f)
                val clear = withAlpha(vivid, 0f)
                val colors = intArrayOf(clear, clear, soft, vivid, soft, clear)
                val shader = RadialGradient(0f, 0f, 1f, colors, MECH_GLOW_STOPS, Shader.TileMode.CLAMP)
                radialWaves.add(RadialWave(now, shader, cx, cy))
            }
            EffectStyle.CYBER_NEON, EffectStyle.KEY_PULSE -> {
                keyPulses.add(KeyPulseWave(now, RectF(keyRect), theme.trailColor))
            }
            EffectStyle.NONE -> Unit
        }
        // Cap concurrent waves to keep frame times bounded on rapid chorded input
        while (radialWaves.size > MAX_CONCURRENT_WAVES) radialWaves.removeAt(0)
        while (keyPulses.size > MAX_CONCURRENT_WAVES) keyPulses.removeAt(0)
    }

    private fun buildRippleShader(cx: Float, cy: Float, radius: Float, baseColor: Int): RadialGradient {
        val colors = intArrayOf(
            withAlpha(baseColor, 0f),
            halfExistingAlpha(baseColor),
            baseColor,
            withAlpha(baseColor, 0f)
        )
        return RadialGradient(cx, cy, radius, colors, RIPPLE_STOPS, Shader.TileMode.CLAMP)
    }

    private fun clipOutsideKeys(canvas: Canvas): Int {
        val saveCount = canvas.save()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            canvas.clipOutPath(fillKeysPath)
        } else {
            @Suppress("DEPRECATION")
            canvas.clipPath(fillKeysPath, Region.Op.DIFFERENCE)
        }
        return saveCount
    }

    /**
     * Renders effects that belong underneath / between keys (inter-key Ripple waves).
     */
    fun drawUnderKeys(canvas: Canvas, theme: ThemeModel, width: Float): Boolean {
        if (theme.effectStyle != EffectStyle.RIPPLE || radialWaves.isEmpty() || width <= 0f) {
            return false
        }
        val now = SystemClock.uptimeMillis()
        val saveCount = clipOutsideKeys(canvas)

        val iterator = radialWaves.iterator()
        while (iterator.hasNext()) {
            val wave = iterator.next()
            val progress = (now - wave.startMs).toFloat() / RIPPLE_DURATION_MS
            if (progress >= 1f) {
                iterator.remove()
                continue
            }
            val radius = max(1f, width * rippleInterpolator.getInterpolation(progress) * 1.2f)
            shaderMatrix.setScale(radius, radius)
            shaderMatrix.postTranslate(wave.cx, wave.cy)
            wave.shader.setLocalMatrix(shaderMatrix)
            ripplePaint.shader = wave.shader
            ripplePaint.alpha = ((1f - progress) * 255f).toInt().coerceIn(0, 255)
            canvas.drawCircle(wave.cx, wave.cy, radius, ripplePaint)
        }
        canvas.restoreToCount(saveCount)
        return radialWaves.isNotEmpty()
    }

    /**
     * Draws subtle static neon outer bloom behind a single Cyber Neon keycap.
     */
    fun drawStaticCyberGlow(
        canvas: Canvas,
        rect: RectF,
        cornerRadiusPx: Float,
        glowColor: Int,
        strokeWidthPx: Float
    ) {
        val baseStroke = max(1.5f, strokeWidthPx)
        cyberHaloPaint.color = glowColor
        cyberHaloPaint.alpha = 46
        cyberHaloPaint.strokeWidth = baseStroke * 3.2f
        canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, cyberHaloPaint)
    }

    /**
     * Renders effects drawn above the key faces (Mech Glow rainbow stroke waves & Key Pulse rings).
     */
    fun drawOverKeys(
        canvas: Canvas,
        theme: ThemeModel,
        width: Float,
        strokeWidthPx: Float
    ): Boolean {
        val now = SystemClock.uptimeMillis()
        when (theme.effectStyle) {
            EffectStyle.MECH_GLOW -> {
                if (radialWaves.isEmpty() || width <= 0f) return false
                mechGlowPaint.strokeWidth = max(2f, strokeWidthPx)
                val it = radialWaves.iterator()
                while (it.hasNext()) {
                    val wave = it.next()
                    val progress = (now - wave.startMs).toFloat() / MECH_GLOW_DURATION_MS
                    if (progress >= 1f) {
                        it.remove()
                        continue
                    }
                    val radius = max(1f, width * 1.5f * progress)
                    shaderMatrix.setScale(radius, radius)
                    shaderMatrix.postTranslate(wave.cx, wave.cy)
                    wave.shader.setLocalMatrix(shaderMatrix)
                    mechGlowPaint.shader = wave.shader
                    mechGlowPaint.alpha = ((1f - progress) * 255f).toInt().coerceIn(0, 255)
                    canvas.drawPath(strokeKeysPath, mechGlowPaint)
                }
                return radialWaves.isNotEmpty()
            }
            EffectStyle.CYBER_NEON, EffectStyle.KEY_PULSE -> {
                if (keyPulses.isEmpty()) return false
                val densityFactor = max(1f, strokeWidthPx)
                val expandMax = densityFactor * 3.5f
                val it = keyPulses.iterator()
                while (it.hasNext()) {
                    val pulse = it.next()
                    val progress = (now - pulse.startMs).toFloat() / KEY_PULSE_DURATION_MS
                    if (progress >= 1f) {
                        it.remove()
                        continue
                    }
                    val outset = expandMax * progress
                    val pulseRect = RectF(
                        pulse.rect.left - outset,
                        pulse.rect.top - outset,
                        pulse.rect.right + outset,
                        pulse.rect.bottom + outset
                    )
                    pulsePaint.color = pulse.color
                    pulsePaint.strokeWidth = max(2f, densityFactor * (1.8f - 0.8f * progress))
                    pulsePaint.alpha = ((1f - progress) * 210f).toInt().coerceIn(0, 255)
                    canvas.drawRoundRect(
                        pulseRect,
                        cachedCornerRadiusPx + outset * 0.5f,
                        cachedCornerRadiusPx + outset * 0.5f,
                        pulsePaint
                    )
                }
                return keyPulses.isNotEmpty()
            }
            else -> return false
        }
    }

    /**
     * Renders a crisp frozen effect illustration inside ThemePreviewView gallery cards.
     */
    fun drawPreviewEffects(
        canvas: Canvas,
        theme: ThemeModel,
        keyRects: List<RectF>,
        cornerRadiusPx: Float,
        strokeWidthPx: Float,
        width: Float,
        height: Float
    ) {
        if (theme.effectStyle == EffectStyle.NONE || keyRects.isEmpty() || width <= 0f) return
        val focalKey = keyRects.getOrNull(keyRects.size / 2) ?: keyRects.first()
        val cx = focalKey.centerX()
        val cy = focalKey.centerY()

        when (theme.effectStyle) {
            EffectStyle.RIPPLE -> {
                updateKeyGeometry(keyRects, cornerRadiusPx, strokeWidthPx)
                val saveCount = clipOutsideKeys(canvas)
                val palette = resolveRipplePalette(theme)
                val samples = listOf(
                    Triple(cx, cy, width * 0.42f) to (palette.first() to 230),
                    Triple(width * 0.25f, height * 0.45f, width * 0.28f) to (palette.getOrElse(2) { palette.first() } to 200)
                )
                for ((geo, style) in samples) {
                    val (rx, ry, rr) = geo
                    val (col, alpha) = style
                    ripplePaint.shader = buildRippleShader(rx, ry, rr, col)
                    ripplePaint.alpha = alpha
                    canvas.drawCircle(rx, ry, rr, ripplePaint)
                }
                canvas.restoreToCount(saveCount)
            }
            EffectStyle.MECH_GLOW -> {
                updateKeyGeometry(keyRects, cornerRadiusPx, strokeWidthPx)
                val rainbow = SweepGradient(
                    cx, cy,
                    intArrayOf(
                        Color.parseColor("#FF00E5FF"),
                        Color.parseColor("#FF00FF9C"),
                        Color.parseColor("#FFFFE600"),
                        Color.parseColor("#FFFF2A6D"),
                        Color.parseColor("#FF8B5CF6"),
                        Color.parseColor("#FF00E5FF")
                    ),
                    null
                )
                mechGlowPaint.shader = rainbow
                mechGlowPaint.alpha = 235
                mechGlowPaint.strokeWidth = max(2.2f, strokeWidthPx * 1.4f)
                canvas.drawPath(strokeKeysPath, mechGlowPaint)
                mechGlowPaint.shader = null
            }
            EffectStyle.CYBER_NEON -> {
                for (r in keyRects) {
                    drawStaticCyberGlow(canvas, r, cornerRadiusPx, theme.trailColor, strokeWidthPx)
                }
            }
            EffectStyle.KEY_PULSE -> {
                pulsePaint.color = theme.trailColor
                pulsePaint.strokeWidth = max(2f, strokeWidthPx * 1.5f)
                pulsePaint.alpha = 190
                val ring = RectF(focalKey.left - 3f, focalKey.top - 3f, focalKey.right + 3f, focalKey.bottom + 3f)
                canvas.drawRoundRect(ring, cornerRadiusPx + 1.5f, cornerRadiusPx + 1.5f, pulsePaint)
            }
            EffectStyle.NONE -> Unit
        }
    }

    companion object {
        private const val RIPPLE_DURATION_MS = 2000f
        private const val MECH_GLOW_DURATION_MS = 1500f
        private const val KEY_PULSE_DURATION_MS = 480f
        private const val MECH_HUE_STEP_DEG = 50f
        private const val MAX_CONCURRENT_WAVES = 16

        private val RIPPLE_STOPS = floatArrayOf(0.0f, 0.28f, 0.65f, 1.0f)
        private val MECH_GLOW_STOPS = floatArrayOf(0.0f, 0.55f, 0.68f, 0.90f, 0.96f, 1.0f)

        val RIPPLE_DARK_PALETTE = listOf(
            Color.parseColor("#9938BDF8"),
            Color.parseColor("#9934D399"),
            Color.parseColor("#99FBBF24"),
            Color.parseColor("#99F472B6"),
            Color.parseColor("#99A78BFA"),
            Color.parseColor("#99FB923C")
        )

        val RIPPLE_LIGHT_PALETTE = listOf(
            Color.parseColor("#FF3B82F6"),
            Color.parseColor("#FF22C55E"),
            Color.parseColor("#FF8B5CF6"),
            Color.parseColor("#FFF59E0B"),
            Color.parseColor("#FFEF4444"),
            Color.parseColor("#FF06B6D4"),
            Color.parseColor("#FF14B8A6"),
            Color.parseColor("#FFEC4899")
        )

        fun resolveRipplePalette(theme: ThemeModel): List<Int> {
            if (theme.effectColors.isNotEmpty()) return theme.effectColors
            return if (Color.luminance(theme.keyboardBackground.colorStart) > 0.5f) {
                RIPPLE_LIGHT_PALETTE
            } else {
                RIPPLE_DARK_PALETTE
            }
        }

        /**
         * Builds a LinearGradient supporting 2- or 3-stop backgrounds at arbitrary angles.
         */
        fun buildBackgroundShader(spec: BackgroundSpec, width: Float, height: Float): Shader? {
            if (spec.type != BackgroundSpec.Type.GRADIENT || width <= 0f || height <= 0f) return null
            val rad = Math.toRadians(spec.gradientAngleDeg.toDouble())
            val dx = cos(rad).toFloat()
            val dy = sin(rad).toFloat()
            val cx = width / 2f
            val cy = height / 2f
            val halfSpanX = width * 0.5f
            val halfSpanY = height * 0.5f
            val x0 = cx - dx * halfSpanX
            val y0 = cy - dy * halfSpanY
            val x1 = cx + dx * halfSpanX
            val y1 = cy + dy * halfSpanY
            val mid = spec.colorMid
            return if (mid != null) {
                LinearGradient(x0, y0, x1, y1, intArrayOf(spec.colorStart, mid, spec.colorEnd), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            } else {
                LinearGradient(x0, y0, x1, y1, spec.colorStart, spec.colorEnd, Shader.TileMode.CLAMP)
            }
        }

        private fun withAlpha(color: Int, alphaFraction: Float): Int {
            val alpha = (alphaFraction * 255f).toInt().coerceIn(0, 255)
            return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
        }

        private fun halfExistingAlpha(color: Int): Int {
            return Color.argb(Color.alpha(color) / 2, Color.red(color), Color.green(color), Color.blue(color))
        }
    }
}
