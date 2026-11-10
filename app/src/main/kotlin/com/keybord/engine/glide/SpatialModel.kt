package com.keybord.engine.glide

import kotlin.math.*

/**
 * Spatial scoring engine modeling key proximity using bivariate Gaussian kernels,
 * velocity-dependent tolerance scaling, and repeated-letter heuristics.
 */
class SpatialModel(
    private val keyPositions: Map<Char, KeyPosition>,
    private val config: GlideEngineConfig
) {

    /**
     * Average key dimension in px derived from current layout bounds.
     * Ensures Gaussian sigma and proximity radii scale cleanly across all screen densities.
     */
    val keyUnitPx: Float = run {
        val avg = keyPositions.values
            .mapNotNull { kp ->
                val w = abs(kp.bounds.right - kp.bounds.left)
                val h = abs(kp.bounds.bottom - kp.bounds.top)
                val u = (w + h) * 0.5f
                if (u > 1f) u else null
            }
            .takeIf { it.isNotEmpty() }
            ?.average()
            ?.toFloat()
        val inferred = avg ?: run {
            val pts = keyPositions.values.toList()
            if (pts.size < 2) {
                null
            } else {
                pts.mapNotNull { a ->
                    pts.filter { it !== a }
                        .minOfOrNull { b -> hypot(a.centerX - b.centerX, a.centerY - b.centerY) }
                        ?.takeIf { it > 1f }
                }.takeIf { it.isNotEmpty() }?.average()?.toFloat()
            }
        }
        (inferred ?: 80f).coerceAtLeast(40f)
    }

    val dpiScale: Float = (keyUnitPx / 40.0f).coerceAtLeast(1.0f)

    val anchorRadiusPx: Float = config.anchorRadiusDp * dpiScale

    /**
     * Calculates anisotropic Gaussian probability density using separate X/Y sigmas
     * scaled by layout DPI and finger velocity.
     */
    fun gaussianScoreAniso(
        point: TouchPoint,
        key: KeyPosition,
        velocityPxPerMs: Float = 0f
    ): Float {
        val normVel = velocityPxPerMs / (keyUnitPx / 100f).coerceAtLeast(0.5f)
        val speedFactor = 1.0f + (normVel * config.velocityToleranceFactor * 0.16f).coerceIn(0f, 0.55f)
        val sx = config.spatialSigmaXDp * dpiScale * speedFactor
        val sy = config.spatialSigmaYDp * dpiScale * speedFactor
        val dx = (point.x - key.centerX) / sx
        val dy = (point.y - key.centerY) / sy
        return exp(-(dx * dx + dy * dy) / 2.0f)
    }

    fun anchorScore(point: TouchPoint, key: KeyPosition): Float {
        val dist = hypot(point.x - key.centerX, point.y - key.centerY)
        if (dist > anchorRadiusPx) return 0f
        return 1.0f - (dist / anchorRadiusPx)
    }

    fun anchorScoreForChar(point: TouchPoint, keyChar: Char): Float {
        val key = keyPositions[keyChar.lowercaseChar()] ?: return 0f
        return anchorScore(point, key)
    }

    fun pointKeyDistanceScore(
        point: TouchPoint,
        keyChar: Char,
        velocityPxPerMs: Float = 0f
    ): Float {
        val key = keyPositions[keyChar.lowercaseChar()] ?: return 0.00001f
        return gaussianScoreAniso(point, key, velocityPxPerMs)
    }

    fun directionScore(
        fromPt: TouchPoint,
        toPt: TouchPoint,
        fromKey: KeyPosition,
        toKey: KeyPosition
    ): Float {
        val trajDx = toPt.x - fromPt.x
        val trajDy = toPt.y - fromPt.y
        val keyDx = toKey.centerX - fromKey.centerX
        val keyDy = toKey.centerY - fromKey.centerY

        val trajLen = hypot(trajDx, trajDy)
        val keyLen = hypot(keyDx, keyDy)

        if (keyLen < 1.0f) return 1.0f
        if (trajLen < 1.0f) return 0.85f

        val cosSim = ((trajDx * keyDx + trajDy * keyDy) / (trajLen * keyLen)).coerceIn(-1.0f, 1.0f)
        return 0.7f + 0.3f * cosSim
    }

    fun pointSegmentDistanceScore(
        point: TouchPoint,
        fromKey: KeyPosition,
        toKey: KeyPosition,
        velocityPxPerMs: Float = 0f
    ): Float {
        val vx = toKey.centerX - fromKey.centerX
        val vy = toKey.centerY - fromKey.centerY
        val lenSq = vx * vx + vy * vy
        val t = if (lenSq > 1.0f) {
            (((point.x - fromKey.centerX) * vx + (point.y - fromKey.centerY) * vy) / lenSq)
                .coerceIn(0f, 1f)
        } else {
            1f
        }
        val projX = fromKey.centerX + t * vx
        val projY = fromKey.centerY + t * vy

        val normVel = velocityPxPerMs / (keyUnitPx / 100f).coerceAtLeast(0.5f)
        val speedFactor = 1.0f + (normVel * config.velocityToleranceFactor * 0.16f).coerceIn(0f, 0.55f)
        val sx = config.spatialSigmaXDp * dpiScale * speedFactor
        val sy = config.spatialSigmaYDp * dpiScale * speedFactor
        val dx = (point.x - projX) / sx
        val dy = (point.y - projY) / sy
        return exp(-(dx * dx + dy * dy) / 2.0f)
    }

    fun cornerRecallScore(
        wordChars: List<Char>,
        trajectory: List<TouchPoint>,
        cornerIndices: List<Int>,
        averageVelocity: Float = 0f
    ): Float {
        if (cornerIndices.isEmpty() || wordChars.isEmpty()) return 1.0f
        var prod = 1.0f
        for (cIdx in cornerIndices) {
            val pt = trajectory[cIdx]
            var best = 0f
            for (ch in wordChars) {
                val s = pointKeyDistanceScore(pt, ch, averageVelocity)
                if (s > best) best = s
            }
            prod *= best.coerceIn(0.05f, 1.0f)
        }
        return prod.pow(1.0f / cornerIndices.size.toFloat())
    }

    /**
     * Aligns a deduplicated candidate word with a resampled trajectory using segment-aware DTW
     * incorporating direction alignment, normalized by (n + m).
     */
    fun alignWordTrajectory(
        candidateWord: String,
        trajectory: List<TouchPoint>,
        averageVelocity: Float = 0f
    ): Float {
        val word = dedupConsecutiveChars(candidateWord)
        val n = word.size
        val m = trajectory.size
        if (n == 0 || m == 0) return 0f

        val startAnchor = anchorScoreForChar(trajectory.first(), word.first())
        val endAnchor = anchorScoreForChar(trajectory.last(), word.last())
        if (startAnchor <= 0f || endAnchor <= 0f) return 0f

        val cost = Array(n + 1) { FloatArray(m + 1) { 1e6f } }
        cost[0][0] = 0f

        for (i in 1..n) {
            val ch = word[i - 1]
            val currKey = keyPositions[ch]
            val prevKey = if (i > 1) keyPositions[word[i - 2]] else null

            for (j in 1..m) {
                val point = trajectory[j - 1]
                val keySc = pointKeyDistanceScore(point, ch, averageVelocity)
                val dirSc = if (prevKey != null && currKey != null && j >= 2) {
                    directionScore(trajectory[j - 2], point, prevKey, currKey)
                } else {
                    1.0f
                }
                val diagScore = (keySc * (0.75f + 0.25f * dirSc)).coerceAtLeast(1e-6f)
                val diagCost = -ln(diagScore)

                val transitSc = if (prevKey != null && currKey != null) {
                    max(keySc, pointSegmentDistanceScore(point, prevKey, currKey, averageVelocity) * 0.92f)
                } else {
                    keySc
                }
                val transitCost = -ln((transitSc * (0.75f + 0.25f * dirSc)).coerceAtLeast(1e-6f))

                val diag = cost[i - 1][j - 1] + diagCost
                val horiz = cost[i][j - 1] + transitCost
                val vert = cost[i - 1][j] + diagCost + 0.35f
                cost[i][j] = min(diag, min(horiz, vert))
            }
        }

        val totalCost = cost[n][m]
        val normalizedCost = totalCost / (n + m)
        return exp(-normalizedCost)
    }

    /**
     * SHARK² dual-channel DTW matcher combining absolute keyboard location likelihood,
     * translation/scale-invariant normalized gesture shape, and local tangent direction.
     * Returns Pair(spatialScore, smoothedTemplateArcLength).
     */
    fun alignShark2DualChannel(
        dedupChars: List<Char>,
        uPtsX: FloatArray,
        uPtsY: FloatArray,
        uNormX: FloatArray,
        uNormY: FloatArray,
        uTanX: FloatArray,
        uTanY: FloatArray,
        averageVelocity: Float = 0f
    ): Pair<Float, Float> {
        val sampleN = uPtsX.size
        val len = dedupChars.size
        if (len < 2 || sampleN == 0) return 0f to 0f

        val rawX = FloatArray(len)
        val rawY = FloatArray(len)
        for (i in 0 until len) {
            val k = keyPositions[dedupChars[i]] ?: return 0f to 0f
            rawX[i] = k.centerX
            rawY[i] = k.centerY
        }

        // Interior corner rounding (~26% pull toward neighbor midpoint) balances sharp and curved thumbs
        val smoothX = rawX.copyOf()
        val smoothY = rawY.copyOf()
        if (len >= 3) {
            for (i in 1 until (len - 1)) {
                smoothX[i] = 0.74f * rawX[i] + 0.13f * rawX[i - 1] + 0.13f * rawX[i + 1]
                smoothY[i] = 0.74f * rawY[i] + 0.13f * rawY[i - 1] + 0.13f * rawY[i + 1]
            }
        }

        var tArc = 0f
        for (i in 1 until len) {
            tArc += hypot(smoothX[i] - smoothX[i - 1], smoothY[i] - smoothY[i - 1])
        }

        val (tPtsX, tPtsY) = resampleXY(smoothX, smoothY, sampleN)
        val (tNormX, tNormY, tTanX, tTanY) = normalizeAndTangents(tPtsX, tPtsY, keyUnitPx)

        val normVel = averageVelocity / (keyUnitPx / 100f).coerceAtLeast(0.5f)
        val speedFactor = 1.0f + (normVel * config.velocityToleranceFactor * 0.10f).coerceIn(0f, 0.35f)
        val sx = config.spatialSigmaXDp * dpiScale * speedFactor
        val sy = config.spatialSigmaYDp * dpiScale * speedFactor

        val band = 3
        val prevRow = FloatArray(sampleN + 1) { 1e6f }
        val currRow = FloatArray(sampleN + 1) { 1e6f }
        prevRow[0] = 0f

        for (i in 1..sampleN) {
            currRow.fill(1e6f)
            val ui = i - 1
            val jStart = max(1, i - band)
            val jEnd = min(sampleN, i + band)
            val ux = uPtsX[ui]
            val uy = uPtsY[ui]
            val unx = uNormX[ui]
            val uny = uNormY[ui]
            val utx = uTanX[ui]
            val uty = uTanY[ui]

            for (j in jStart..jEnd) {
                val tj = j - 1
                val dx = (ux - tPtsX[tj]) / sx
                val dy = (uy - tPtsY[tj]) / sy
                val locErr = 0.5f * (dx * dx + dy * dy)

                val sdx = unx - tNormX[tj]
                val sdy = uny - tNormY[tj]
                val shapeErr = (sdx * sdx + sdy * sdy) * 3.0f

                val dot = (utx * tTanX[tj] + uty * tTanY[tj]).coerceIn(-1f, 1f)
                val dirErr = (1.0f - dot) * 0.55f

                val stepCost = 0.48f * locErr + 0.34f * shapeErr + 0.18f * dirErr
                val diag = prevRow[j - 1]
                val up = prevRow[j] + 0.04f
                val left = currRow[j - 1] + 0.04f
                currRow[j] = min(diag, min(up, left)) + stepCost
            }
            System.arraycopy(currRow, 0, prevRow, 0, sampleN + 1)
        }

        val avgCost = prevRow[sampleN] / sampleN.toFloat()
        return exp(-avgCost) to tArc
    }

    /**
     * Determines keys within spatial proximity of a touch point, scaled to actual key geometry.
     */
    fun findProximityKeys(point: TouchPoint, maxRadiusPx: Float = keyUnitPx * 1.2f): List<Char> {
        val candidates = mutableListOf<Pair<Char, Float>>()
        for ((char, key) in keyPositions) {
            val d = hypot(point.x - key.centerX, point.y - key.centerY)
            val insideBounds = point.x in key.bounds.left..key.bounds.right &&
                point.y in key.bounds.top..key.bounds.bottom
            if (insideBounds || d <= maxRadiusPx) {
                candidates.add(char to d)
            }
        }
        candidates.sortBy { it.second }
        return candidates.map { it.first }
    }

    data class NormalizedShape(
        val normX: FloatArray,
        val normY: FloatArray,
        val tanX: FloatArray,
        val tanY: FloatArray
    )

    companion object {
        fun dedupConsecutiveChars(word: String): List<Char> {
            val out = ArrayList<Char>(word.length)
            for (ch in word.lowercase()) {
                if (out.isEmpty() || out.last() != ch) {
                    out.add(ch)
                }
            }
            return out
        }

        fun resampleXY(srcX: FloatArray, srcY: FloatArray, targetCount: Int): Pair<FloatArray, FloatArray> {
            val outX = FloatArray(targetCount)
            val outY = FloatArray(targetCount)
            val n = srcX.size
            if (n == 0 || targetCount == 0) return outX to outY
            if (n == 1 || targetCount == 1) {
                outX.fill(srcX[0])
                outY.fill(srcY[0])
                return outX to outY
            }
            val cum = FloatArray(n)
            for (i in 1 until n) {
                cum[i] = cum[i - 1] + hypot(srcX[i] - srcX[i - 1], srcY[i] - srcY[i - 1])
            }
            val total = cum[n - 1]
            if (total <= 1e-4f) {
                outX.fill(srcX[0])
                outY.fill(srcY[0])
                return outX to outY
            }
            val step = total / (targetCount - 1).toFloat()
            outX[0] = srcX[0]
            outY[0] = srcY[0]
            var rawIdx = 0
            for (k in 1 until (targetCount - 1)) {
                val targetD = k * step
                while (rawIdx < n - 2 && cum[rawIdx + 1] < targetD) {
                    rawIdx++
                }
                val seg = cum[rawIdx + 1] - cum[rawIdx]
                val t = if (seg > 1e-5f) (targetD - cum[rawIdx]) / seg else 0f
                outX[k] = srcX[rawIdx] + t * (srcX[rawIdx + 1] - srcX[rawIdx])
                outY[k] = srcY[rawIdx] + t * (srcY[rawIdx + 1] - srcY[rawIdx])
            }
            outX[targetCount - 1] = srcX[n - 1]
            outY[targetCount - 1] = srcY[n - 1]
            return outX to outY
        }

        fun normalizeAndTangents(ptsX: FloatArray, ptsY: FloatArray, keyUnit: Float): NormalizedShape {
            val n = ptsX.size
            val normX = FloatArray(n)
            val normY = FloatArray(n)
            val tanX = FloatArray(n)
            val tanY = FloatArray(n)
            if (n == 0) return NormalizedShape(normX, normY, tanX, tanY)

            var mx = 0f
            var my = 0f
            for (i in 0 until n) {
                mx += ptsX[i]
                my += ptsY[i]
            }
            mx /= n.toFloat()
            my /= n.toFloat()

            var variance = 0f
            for (i in 0 until n) {
                val dx = ptsX[i] - mx
                val dy = ptsY[i] - my
                variance += dx * dx + dy * dy
            }
            val rms = sqrt(variance / n.toFloat()).coerceAtLeast(keyUnit * 0.5f)

            for (i in 0 until n) {
                normX[i] = (ptsX[i] - mx) / rms
                normY[i] = (ptsY[i] - my) / rms
                val prevIdx = if (i == 0) 0 else i - 1
                val nextIdx = if (i + 1 >= n) n - 1 else i + 1
                val dx = ptsX[nextIdx] - ptsX[prevIdx]
                val dy = ptsY[nextIdx] - ptsY[prevIdx]
                val len = hypot(dx, dy)
                if (len > 1e-3f) {
                    tanX[i] = dx / len
                    tanY[i] = dy / len
                }
            }
            return NormalizedShape(normX, normY, tanX, tanY)
        }
    }
}
