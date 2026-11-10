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
        val speedFactor = 1.0f + (velocityPxPerMs * config.velocityToleranceFactor).coerceIn(0f, 2.5f)
        val sigma = config.spatialSigmaDp * speedFactor
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
        if (firstScore < 0.001f || lastScore < 0.001f) {
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
     * Determines if a touch point is within the proximity of a key.
     */
    fun findProximityKeys(point: TouchPoint, maxRadiusDp: Float = 48.0f): List<Char> {
        val candidates = mutableListOf<Pair<Char, Float>>()
        for ((char, key) in keyPositions) {
            val d = hypot(point.x - key.centerX, point.y - key.centerY)
            if (d <= maxRadiusDp) {
                candidates.add(char to d)
            }
        }
        candidates.sortBy { it.second }
        return candidates.map { it.first }
    }
}
