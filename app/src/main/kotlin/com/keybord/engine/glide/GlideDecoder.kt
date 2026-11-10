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
        val endKeys = spatialModel.findProximityKeys(endPt, anchorRadius)
            .filter { spatialModel.anchorScoreForChar(endPt, it) > 0f }
            .toSet()

        if (startKeys.isEmpty() || endKeys.isEmpty()) {
            return emptyList()
        }

        val refArc = 400.0f * spatialModel.dpiScale
        val dynamicBeam = (config.minBeamWidth.toFloat() +
            (arcLength / refArc) * (config.maxBeamWidth - config.minBeamWidth).toFloat())
            .toInt()
            .coerceIn(config.minBeamWidth, config.maxBeamWidth)

        val sharpCorners = processor.extractSharpCornerIndices(smoothed)
        val cornerIndices = processor.extractCorners(smoothed, 0.5f)
        val stepSize = max(1, smoothed.size / 20)
        val sampleIndices = ((stepSize until smoothed.size step stepSize) + sharpCorners + cornerIndices + (smoothed.size - 1))
            .distinct()
            .sorted()
            .filter { it in 1 until smoothed.size }

        val maxFreqLn = ln(lexicon.maxFrequency + 1.0f).coerceAtLeast(1.0f)

        data class BeamNode(
            val trieNode: TrieLexicon.Node,
            val currentWord: String,
            val cumulativeScore: Float,
            val rankScore: Float
        )

        var beam = mutableListOf<BeamNode>()
        for (startChar in startKeys) {
            val child = lexicon.root.children[startChar] ?: continue
            val startScore = spatialModel.pointKeyDistanceScore(startPt, startChar, avgVelocity)
            val lmPrior = (ln(child.maxSubtreeFreq + 1.0f) / maxFreqLn).coerceIn(0f, 1f)
            beam.add(BeamNode(child, startChar.toString(), startScore, startScore + 0.12f * lmPrior))
        }

        val collectedWords = HashSet<String>()
        val stepRadius = spatialModel.keyUnitPx * 1.15f

        for (currentPointIndex in sampleIndices) {
            if (beam.isEmpty()) break
            val currentPoint = smoothed[currentPointIndex]
            val candidateKeysNearPoint = spatialModel.findProximityKeys(currentPoint, maxRadiusPx = stepRadius)
            val nextBeam = ArrayList<BeamNode>(beam.size * 4)

            for (bNode in beam) {
                for (nextChar in candidateKeysNearPoint) {
                    val child = bNode.trieNode.children[nextChar] ?: continue
                    val score = spatialModel.pointKeyDistanceScore(currentPoint, nextChar, avgVelocity)
                    if (score < 0.22f) continue
                    val newCum = bNode.cumulativeScore + (score - 0.18f)
                    val lmPrior = (ln(child.maxSubtreeFreq + 1.0f) / maxFreqLn).coerceIn(0f, 1f)
                    nextBeam.add(
                        BeamNode(
                            trieNode = child,
                            currentWord = bNode.currentWord + nextChar,
                            cumulativeScore = newCum,
                            rankScore = newCum + 0.12f * lmPrior
                        )
                    )
                }

                val lastChar = bNode.currentWord.last()
                val doubleChild = bNode.trieNode.children[lastChar]
                if (doubleChild != null) {
                    val stayScore = spatialModel.pointKeyDistanceScore(currentPoint, lastChar, avgVelocity)
                    if (stayScore > 0.35f) {
                        val newCum = bNode.cumulativeScore - 0.01f
                        val lmPrior = (ln(doubleChild.maxSubtreeFreq + 1.0f) / maxFreqLn).coerceIn(0f, 1f)
                        nextBeam.add(
                            BeamNode(
                                trieNode = doubleChild,
                                currentWord = bNode.currentWord + lastChar,
                                cumulativeScore = newCum,
                                rankScore = newCum + 0.12f * lmPrior
                            )
                        )
                    }
                }

                val cruiseCum = bNode.cumulativeScore - 0.015f
                val cruisePrior = (ln(bNode.trieNode.maxSubtreeFreq + 1.0f) / maxFreqLn).coerceIn(0f, 1f)
                nextBeam.add(
                    BeamNode(
                        trieNode = bNode.trieNode,
                        currentWord = bNode.currentWord,
                        cumulativeScore = cruiseCum,
                        rankScore = cruiseCum + 0.12f * cruisePrior
                    )
                )
            }

            nextBeam.sortByDescending { it.rankScore }
            beam = nextBeam.distinctBy { it.currentWord }.take(dynamicBeam).toMutableList()
        }

        for (bNode in beam) {
            if (bNode.trieNode.isWord && endKeys.contains(bNode.currentWord.last())) {
                collectedWords.add(bNode.currentWord)
            }
            for (endCh in endKeys) {
                val endChild = bNode.trieNode.children[endCh]
                if (endChild != null && endChild.isWord && endChild.word != null) {
                    collectedWords.add(endChild.word!!)
                }
                val repeatLast = bNode.currentWord.last()
                val nested = bNode.trieNode.children[repeatLast]?.children?.get(endCh)
                if (nested != null && nested.isWord && nested.word != null) {
                    collectedWords.add(nested.word!!)
                }
            }
        }

        for (sc in startKeys) {
            val sNode = lexicon.root.children[sc] ?: continue
            for (ec in endKeys) {
                val twoCharNode = sNode.children[ec]
                if (twoCharNode != null && twoCharNode.isWord && twoCharNode.word != null) {
                    collectedWords.add(twoCharNode.word!!)
                }
            }
        }

        val expectedLen = sharpCorners.size + 2
        val results = mutableListOf<GlideCandidate>()

        for (word in collectedWords) {
            if (word.length < 2) continue

            val anchorStart = spatialModel.anchorScoreForChar(startPt, word.first())
            val anchorEnd = spatialModel.anchorScoreForChar(endPt, word.last())
            val anchor = anchorStart * anchorEnd
            if (anchor <= 0f) continue

            val spatialScore = spatialModel.alignWordTrajectory(word, smoothed, avgVelocity)
            if (spatialScore < 0.005f) continue

            val dedupChars = SpatialModel.dedupConsecutiveChars(word)
            val cornerRecall = spatialModel.cornerRecallScore(dedupChars, smoothed, sharpCorners, avgVelocity)

            val baseFreq = lexicon.getFrequency(word)
            val lmScore = (ln(baseFreq + 1.0f) / maxFreqLn).coerceIn(0f, 1f)

            val lenDiff = abs(dedupChars.size.toFloat() - expectedLen.toFloat())
            val lenPenalty = 1.0f / (1.0f + lenDiff * 0.06f)

            val totalScore =
                (config.spatialWeight * spatialScore + config.lmWeight * lmScore) *
                    anchor * lenPenalty * cornerRecall

            results.add(
                GlideCandidate(
                    word = word,
                    totalScore = totalScore,
                    spatialScore = spatialScore,
                    languageModelScore = lmScore
                )
            )
        }

        results.sort()
        return results.take(8)
    }
}
