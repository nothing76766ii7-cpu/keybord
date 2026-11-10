package com.keybord.engine.glide

import kotlin.math.*

/**
 * Main Glide Typing Decoder implementing beam search over TrieLexicon,
 * spatial trajectory matching, repeated-letter heuristics, and candidate ranking.
 */
class GlideDecoder(
    private var keyPositions: Map<Char, KeyPosition>,
    private val lexicon: TrieLexicon,
    private val config: GlideEngineConfig = GlideEngineConfig()
) {

    private val processor = TrajectoryProcessor(config)
    private var spatialModel = SpatialModel(keyPositions, config)

    private var lastTapTimestamp: Long = 0L

    fun updateKeyPositions(newPositions: Map<Char, KeyPosition>) {
        this.keyPositions = newPositions
        this.spatialModel = SpatialModel(newPositions, config)
    }

    fun notifyTap(timestamp: Long = System.currentTimeMillis()) {
        lastTapTimestamp = timestamp
    }

    /**
     * Decodes a raw touch swipe trajectory into a ranked list of candidate words.
     * Returns empty list if trajectory is throttled or invalid.
     */
    fun decodeGesture(rawPoints: List<TouchPoint>, lastTapTs: Long = lastTapTimestamp): List<GlideCandidate> {
        if (keyPositions.isEmpty()) {
            return emptyList()
        }

        val validation = processor.validateTrajectory(rawPoints, lastTapTs, keyPositions)
        if (!validation.isValidGesture) {
            return emptyList()
        }

        val resampled = processor.resampleTrajectory(rawPoints, config.resamplePointCount)
        val smoothed = processor.smoothTrajectory(resampled)

        val durationMs = max(1L, smoothed.last().timestamp - smoothed.first().timestamp)
        val arcLength = processor.computeArcLength(smoothed)
        val avgVelocity = arcLength / durationMs.toFloat()

        val startPt = smoothed.first()
        val endPt = smoothed.last()
        val anchorRadius = spatialModel.anchorRadiusPx

        val startKeys = spatialModel.findProximityKeys(startPt, anchorRadius)
            .filter { spatialModel.anchorScoreForChar(startPt, it) > 0f }
            .take(4)
        val endKeys = spatialModel.findProximityKeys(endPt, anchorRadius)
            .filter { spatialModel.anchorScoreForChar(endPt, it) > 0f }
            .take(4)

        if (startKeys.isEmpty() || endKeys.isEmpty()) {
            return emptyList()
        }

        val sampleN = 24
        val rawX = FloatArray(smoothed.size) { smoothed[it].x }
        val rawY = FloatArray(smoothed.size) { smoothed[it].y }
        val (uPtsX, uPtsY) = SpatialModel.resampleXY(rawX, rawY, sampleN)
        val (uNormX, uNormY, uTanX, uTanY) = SpatialModel.normalizeAndTangents(
            uPtsX,
            uPtsY,
            spatialModel.keyUnitPx
        )

        val maxFreqLn = ln(lexicon.maxFrequency + 1.0f).coerceAtLeast(1.0f)
        val rGauss = max(40f, anchorRadius * 0.68f)

        val seenCandidates = HashSet<String>()
        val results = mutableListOf<GlideCandidate>()

        for (sc in startKeys) {
            val startK = keyPositions[sc] ?: continue
            val sdx = startPt.x - startK.centerX
            val sdy = startPt.y - startK.centerY
            val startG = exp(-((sdx * sdx + sdy * sdy) / (2.0f * rGauss * rGauss)))

            for (ec in endKeys) {
                val bucket = lexicon.endpointBuckets[sc to ec] ?: continue
                val endK = keyPositions[ec] ?: continue
                val edx = endPt.x - endK.centerX
                val edy = endPt.y - endK.centerY
                val endG = exp(-((edx * edx + edy * edy) / (2.0f * rGauss * rGauss)))
                val anchorFactor = 0.60f + 0.40f * sqrt(startG * endG)

                for (entry in bucket) {
                    if (!seenCandidates.add(entry.word)) continue

                    val (spatialScore, tArc) = spatialModel.alignShark2DualChannel(
                        dedupChars = entry.dedupChars,
                        uPtsX = uPtsX,
                        uPtsY = uPtsY,
                        uNormX = uNormX,
                        uNormY = uNormY,
                        uTanX = uTanX,
                        uTanY = uTanY,
                        averageVelocity = avgVelocity
                    )
                    if (spatialScore < 0.01f) continue

                    val arcRatio = (min(arcLength, tArc) / max(1f, max(arcLength, tArc)))
                        .coerceIn(0.45f, 1.0f)
                    val arcFactor = 0.88f + 0.12f * arcRatio

                    val lmScore = (ln(entry.frequency + 1.0f) / maxFreqLn).coerceIn(0f, 1f)
                    val totalScore = (spatialScore * anchorFactor * arcFactor) * (0.78f + 0.22f * lmScore)

                    results.add(
                        GlideCandidate(
                            word = entry.word,
                            totalScore = totalScore,
                            spatialScore = spatialScore,
                            languageModelScore = lmScore
                        )
                    )
                }
            }
        }

        results.sort()
        return results.take(8)
    }
}
