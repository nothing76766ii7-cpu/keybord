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
                val w = kotlin.math.abs(kp.bounds.right - kp.bounds.left)
                val h = kotlin.math.abs(kp.bounds.bottom - kp.bounds.top)
                val u = (w + h) * 0.5f
                if (u > 1f) u else null
            }
            .takeIf { it.isNotEmpty() }
            ?.average()
            ?.toFloat()
        (avg ?: 80f).coerceAtLeast(40f)
    }

    /**
     * Calculates the Gaussian probability density of touch point (px, py)
     * given target key center (kx, ky).
     */
    fun pointKeyDistanceScore(
        point: TouchPoint,
        keyChar: Char,
        velocityPxPerMs: Float = 0f
    ): Float {
        val key = keyPositions[keyChar.lowercaseChar()] ?: return 0.00001f

        // Dynamic tolerance scaling: higher swipe speed widens sigma (corner cutting compensation)
        val normalizedVelocity = velocityPxPerMs / (keyUnitPx / 100f).coerceAtLeast(0.5f)
        val speedFactor = 1.0f + (normalizedVelocity * config.velocityToleranceFactor * 0.5f).coerceIn(0f, 2.0f)
        val baseSigma = max(config.spatialSigmaDp, keyUnitPx * 0.48f)
        val sigma = baseSigma * speedFactor
        val twoSigmaSq = 2f * sigma * sigma

        val dx = point.x - key.centerX
        val dy = point.y - key.centerY
        val distSq = dx * dx + dy * dy

        // Gaussian likelihood
        return exp(-distSq / twoSigmaSq)
    }

    /**
     * Aligns a candidate word with a resampled trajectory using Dynamic Time Warping (DTW)
     * and returns the normalized spatial match score in [0.0, 1.0].
     */
    fun alignWordTrajectory(
        candidateWord: String,
        trajectory: List<TouchPoint>,
        averageVelocity: Float = 0f
    ): Float {
        val word = candidateWord.lowercase()
        val n = word.length
        val m = trajectory.size
        if (n == 0 || m == 0) return 0f

        // Initial check: First and last letters should be reasonably close to start and end points
        val firstScore = pointKeyDistanceScore(trajectory.first(), word.first(), averageVelocity)
        val lastScore = pointKeyDistanceScore(trajectory.last(), word.last(), averageVelocity)

        // Prune immediately if start or end keys are excessively distant
        if (firstScore < 0.0005f || lastScore < 0.0005f) {
            return 0.0001f
        }

        val cost = Array(n + 1) { FloatArray(m + 1) { 1e6f } }
        cost[0][0] = 0f

        for (i in 1..n) {
            val char = word[i - 1]
            val isDoubleLetter = (i > 1 && word[i - 1] == word[i - 2])

            for (j in 1..m) {
                val point = trajectory[j - 1]
                val pointScore = pointKeyDistanceScore(point, char, averageVelocity)
                var pointCost = -ln(pointScore.coerceAtLeast(1e-6f))

                // Repeated-letter recognition:
                // For words like "look", the user does not swipe 'o' twice.
                // A double letter transition has negligible additional spatial cost.
                if (isDoubleLetter) {
                    pointCost *= 0.15f
                }

                val minPrev = min(cost[i - 1][j - 1], min(cost[i - 1][j], cost[i][j - 1]))
                cost[i][j] = minPrev + pointCost
            }
        }

        val totalCost = cost[n][m]
        val normalizedCost = totalCost / (n + m)

        return exp(-normalizedCost)
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
}
