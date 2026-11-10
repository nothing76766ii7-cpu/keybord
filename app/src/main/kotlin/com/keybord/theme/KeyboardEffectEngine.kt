package com.keybord.theme

import android.graphics.*
import android.os.Build
import android.os.SystemClock
import android.view.animation.OvershootInterpolator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/**
 * Shared visual effect engine and background shader helper for both live IME KeyboardView
 * and ThemePreviewView cards. Implements inter-key Ripple waves, Mech Glow rainbow
 * keycap border shockwaves, Cyber Neon glow halos, 2-layer Key Pulse highlights, and Glow Flow sweeps.
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
    private val glowFlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val fillKeysPath = Path()
    private val strokeKeysPath = Path()
    private var cachedCornerRadiusPx = 0f
    private var mechHueDeg = 0f
    private var cachedFlowWidth = -1f
    private var cachedFlowColorsKey = 0
    private var cachedFlowShader: LinearGradient? = null

    fun clear() {
        radialWaves.clear()
        keyPulses.clear()
        ripplePaint.shader = null
        mechGlowPaint.shader = null
        glowFlowPaint.shader = null
        cachedFlowShader = null
        cachedFlowWidth = -1f
    }

    fun hasActiveAnimations(): Boolean = radialWaves.isNotEmpty() || keyPulses.isNotEmpty()

    /**
     * Rebuilds cached keycap paths used for clipping inter-key ripples and stroking
     * key outlines during Mech Glow and Glow Flow sweeps. Called on layout or theme change.
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
            EffectStyle.CYBER_NEON, EffectStyle.KEY_PULSE, EffectStyle.GLOW_FLOW -> {
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
     * Renders effects drawn above the key faces (Mech Glow waves, 2-layer Key Pulse rings, and Glow Flow).
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
            EffectStyle.CYBER_NEON -> return drawKeyPulseWaves(canvas, now, strokeWidthPx, twoLayer = false)
            EffectStyle.KEY_PULSE -> return drawKeyPulseWaves(canvas, now, strokeWidthPx, twoLayer = true)
            EffectStyle.GLOW_FLOW -> {
                if (width <= 0f) return false
                val phase = (now % GLOW_FLOW_PERIOD_MS.toLong()).toFloat() / GLOW_FLOW_PERIOD_MS
                val shader = ensureFlowShader(theme, width)
                shaderMatrix.setTranslate(width * phase, 0f)
                shader.setLocalMatrix(shaderMatrix)
                glowFlowPaint.shader = shader

                // Sinusoidal outer glow decay across 2 halo layers (AnimatedGlowStrokeView mechanics)
                val breath = sin(phase * PI).toFloat().coerceIn(0f, 1f)
                for (layer in 1..2) {
                    val decay = exp(-3.0 * layer / 2.0).toFloat()
                    glowFlowPaint.strokeWidth = strokeWidthPx * (1.2f + layer * 1.6f)
                    glowFlowPaint.alpha = ((95f + 115f * breath) * decay).toInt().coerceIn(0, 255)
                    canvas.drawPath(strokeKeysPath, glowFlowPaint)
                }
                glowFlowPaint.strokeWidth = max(1.5f, strokeWidthPx * 1.15f)
                glowFlowPaint.alpha = (165f + 85f * breath).toInt().coerceIn(0, 255)
                canvas.drawPath(strokeKeysPath, glowFlowPaint)

                drawKeyPulseWaves(canvas, now, strokeWidthPx, twoLayer = false)
                return true
            }
            else -> return false
        }
    }

    private fun drawKeyPulseWaves(
        canvas: Canvas,
        now: Long,
        strokeWidthPx: Float,
        twoLayer: Boolean
    ): Boolean {
        if (keyPulses.isEmpty()) return false
        val densityFactor = max(1f, strokeWidthPx)
        val expandMax = densityFactor * 3.8f
        val durationMs = if (twoLayer) KEY_PULSE_LOTTIE_MS else KEY_PULSE_DURATION_MS
        val it = keyPulses.iterator()
        while (it.hasNext()) {
            val pulse = it.next()
            val progress = (now - pulse.startMs).toFloat() / durationMs
            if (progress >= 1f) {
                it.remove()
                continue
            }
            if (twoLayer) {
                // Inner crisp stroke + outer breathing halo (key_highlight_lottie pattern)
                pulsePaint.color = pulse.color
                pulsePaint.strokeWidth = max(1.5f, densityFactor * 1.1f)
                pulsePaint.alpha = ((1f - progress) * 235f).toInt().coerceIn(0, 255)
                canvas.drawRoundRect(pulse.rect, cachedCornerRadiusPx, cachedCornerRadiusPx, pulsePaint)
            }
            val outset = if (twoLayer) {
                expandMax * sin(progress * PI).toFloat()
            } else {
                expandMax * progress
            }
            val pulseRect = RectF(
                pulse.rect.left - outset,
                pulse.rect.top - outset,
                pulse.rect.right + outset,
                pulse.rect.bottom + outset
            )
            pulsePaint.color = pulse.color
            pulsePaint.strokeWidth = max(2f, densityFactor * (1.8f - 0.7f * progress))
            pulsePaint.alpha = ((1f - progress) * 205f).toInt().coerceIn(0, 255)
            canvas.drawRoundRect(
                pulseRect,
                cachedCornerRadiusPx + outset * 0.5f,
                cachedCornerRadiusPx + outset * 0.5f,
                pulsePaint
            )
        }
        return keyPulses.isNotEmpty()
    }

    private fun ensureFlowShader(theme: ThemeModel, width: Float): LinearGradient {
        val colors = resolveFlowPalette(theme)
        val key = colors.contentHashCode()
        val existing = cachedFlowShader
        if (existing != null && cachedFlowWidth == width && cachedFlowColorsKey == key) {
            return existing
        }
        val gradient = LinearGradient(0f, 0f, width, 0f, colors, null, Shader.TileMode.MIRROR)
        cachedFlowWidth = width
        cachedFlowColorsKey = key
        cachedFlowShader = gradient
        return gradient
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
                pulsePaint.strokeWidth = max(1.5f, strokeWidthPx * 1.1f)
                pulsePaint.alpha = 230
                canvas.drawRoundRect(focalKey, cornerRadiusPx, cornerRadiusPx, pulsePaint)
                pulsePaint.strokeWidth = max(2f, strokeWidthPx * 1.6f)
                pulsePaint.alpha = 150
                val ring = RectF(focalKey.left - 3f, focalKey.top - 3f, focalKey.right + 3f, focalKey.bottom + 3f)
                canvas.drawRoundRect(ring, cornerRadiusPx + 1.5f, cornerRadiusPx + 1.5f, pulsePaint)
            }
            EffectStyle.GLOW_FLOW -> {
                updateKeyGeometry(keyRects, cornerRadiusPx, strokeWidthPx)
                glowFlowPaint.shader = ensureFlowShader(theme, width)
                glowFlowPaint.strokeWidth = max(2f, strokeWidthPx * 1.6f)
                glowFlowPaint.alpha = 220
                canvas.drawPath(strokeKeysPath, glowFlowPaint)
                glowFlowPaint.shader = null
            }
            EffectStyle.NONE -> Unit
        }
    }

    companion object {
        private const val RIPPLE_DURATION_MS = 2000f
        private const val MECH_GLOW_DURATION_MS = 1500f
        private const val KEY_PULSE_DURATION_MS = 480f
        private const val KEY_PULSE_LOTTIE_MS = 720f
        private const val GLOW_FLOW_PERIOD_MS = 3000f
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

        val GLOW_FLOW_AURORA_PALETTE = listOf(
            Color.parseColor("#FF00E5FF"),
            Color.parseColor("#FF8B5CF6"),
            Color.parseColor("#FFEC4899"),
            Color.parseColor("#FF3B82F6")
        )

        val GLOW_FLOW_EMERALD_PALETTE = listOf(
            Color.parseColor("#FF00FF9C"),
            Color.parseColor("#FF06B6D4"),
            Color.parseColor("#FF38BDF8"),
            Color.parseColor("#FF14B8A6")
        )

        fun resolveRipplePalette(theme: ThemeModel): List<Int> {
            if (theme.effectColors.isNotEmpty()) return theme.effectColors
            return if (Color.luminance(theme.keyboardBackground.colorStart) > 0.5f) {
                RIPPLE_LIGHT_PALETTE
            } else {
                RIPPLE_DARK_PALETTE
            }
        }

        private fun resolveFlowPalette(theme: ThemeModel): IntArray {
            if (theme.effectColors.size >= 2) return theme.effectColors.toIntArray()
            val accent = theme.trailColor
            val soft = withAlpha(accent, 0.25f)
            return intArrayOf(accent, soft, accent)
        }

        /**
         * Composites a foreground ARGB color over a background ARGB color using source-over blending
         * so semi-transparent key colors stay solid and readable on floating preview bubbles.
         */
        fun compositeOver(fgColor: Int, bgColor: Int): Int {
            val fgA = Color.alpha(fgColor)
            if (fgA >= 255) return fgColor
            val a = fgA / 255f
            val invA = 1f - a
            val bgR = Color.red(bgColor)
            val bgG = Color.green(bgColor)
            val bgB = Color.blue(bgColor)
            val outR = (Color.red(fgColor) * a + bgR * invA).toInt().coerceIn(0, 255)
            val outG = (Color.green(fgColor) * a + bgG * invA).toInt().coerceIn(0, 255)
            val outB = (Color.blue(fgColor) * a + bgB * invA).toInt().coerceIn(0, 255)
            return Color.argb(255, outR, outG, outB)
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
