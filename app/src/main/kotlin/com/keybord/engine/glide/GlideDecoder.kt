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

        // Step 1: Trajectory Throttling & Validation (SHORT_TIME, SHORT_DIST, WITHIN_ONE_KEY)
        val validation = processor.validateTrajectory(rawPoints, lastTapTs, keyPositions)
        if (!validation.isValidGesture) {
            return emptyList()
        }

        // Step 2: Resampling & Smoothing
        val resampled = processor.resampleTrajectory(rawPoints, config.resamplePointCount)
        val smoothed = processor.smoothTrajectory(resampled)

        // Calculate average velocity (px/ms)
        val durationMs = max(1L, smoothed.last().timestamp - smoothed.first().timestamp)
        var totalDist = 0f
        for (i in 0 until smoothed.size - 1) {
            totalDist += smoothed[i].distanceTo(smoothed[i + 1])
        }
        val avgVelocity = totalDist / durationMs.toFloat()

        // Step 3: Identify Candidate Start and End Keys (scaled by key geometry)
        val startKeys = spatialModel.findProximityKeys(smoothed.first())
        val endKeys = spatialModel.findProximityKeys(smoothed.last()).toSet()

        if (startKeys.isEmpty() || endKeys.isEmpty()) {
            return emptyList()
        }

        // Step 4: Extract Key Vertices / Inflection Corners and merge with uniform steps
        val cornerIndices = processor.extractCorners(smoothed)
        val stepSize = max(1, smoothed.size / 20)
        val sampleIndices = ((stepSize until smoothed.size step stepSize) + cornerIndices + (smoothed.size - 1))
            .distinct()
            .sorted()
            .filter { it in 1 until smoothed.size }

        // Step 5: Beam Search Prefix Traversal
        data class BeamNode(
            val trieNode: TrieLexicon.Node,
            val currentWord: String,
            val pointIndex: Int,
            val cumulativeScore: Float
        )

        var beam = mutableListOf<BeamNode>()

        // Initialize beam from starting keys
        for (startChar in startKeys) {
            val child = lexicon.root.children[startChar] ?: continue
            val startScore = spatialModel.pointKeyDistanceScore(smoothed.first(), startChar, avgVelocity)
            beam.add(BeamNode(child, startChar.toString(), 0, startScore))
        }

        val collectedWords = HashSet<String>()
        val beamLimit = max(config.maxBeamWidth, 64)

        for (currentPointIndex in sampleIndices) {
            if (beam.isEmpty()) break
            val currentPoint = smoothed[currentPointIndex]
            val candidateKeysNearPoint = spatialModel.findProximityKeys(
                currentPoint,
                maxRadiusPx = spatialModel.keyUnitPx * 1.3f
            )
            val nextBeam = mutableListOf<BeamNode>()

            for (bNode in beam) {
                // 1. Advance along candidate keys near current touch point
                for (nextChar in candidateKeysNearPoint) {
                    val child = bNode.trieNode.children[nextChar]
                    if (child != null) {
                        val spatialScore = spatialModel.pointKeyDistanceScore(currentPoint, nextChar, avgVelocity)
                        nextBeam.add(
                            BeamNode(
                                trieNode = child,
                                currentWord = bNode.currentWord + nextChar,
                                pointIndex = currentPointIndex,
                                cumulativeScore = bNode.cumulativeScore + spatialScore
                            )
                        )
                    }
                }

                // 2. Self-transition / repeated-letter expansion (e.g. "look", "meet", "hello")
                val lastChar = bNode.currentWord.last()
                val doubleChild = bNode.trieNode.children[lastChar]
                if (doubleChild != null) {
                    nextBeam.add(
                        BeamNode(
                            trieNode = doubleChild,
                            currentWord = bNode.currentWord + lastChar,
                            pointIndex = currentPointIndex,
                            cumulativeScore = bNode.cumulativeScore + 0.8f // Dwell bonus for double letter
                        )
                    )
                }

                // 3. Keep current node in beam while finger cruises toward next target key
                nextBeam.add(
                    BeamNode(
                        trieNode = bNode.trieNode,
                        currentWord = bNode.currentWord,
                        pointIndex = currentPointIndex,
                        cumulativeScore = bNode.cumulativeScore * 0.95f
                    )
                )
            }

            // Prune beam to beamLimit
            nextBeam.sortByDescending { it.cumulativeScore }
            beam = nextBeam.distinctBy { it.currentWord }.take(beamLimit).toMutableList()
        }

        // Gather completed words ending at endKeys (direct match or 1-step end completion)
        for (bNode in beam) {
            if (bNode.trieNode.isWord && endKeys.contains(bNode.currentWord.last())) {
                collectedWords.add(bNode.currentWord)
            }
            for (endCh in endKeys) {
                val endChild = bNode.trieNode.children[endCh]
                if (endChild != null && endChild.isWord && endChild.word != null) {
                    collectedWords.add(endChild.word!!)
                }
                // Handle terminal double letter before lift (e.g. 'all', 'good' style extensions)
                val repeatLast = bNode.currentWord.last()
                val nested = bNode.trieNode.children[repeatLast]?.children?.get(endCh)
                if (nested != null && nested.isWord && nested.word != null) {
                    collectedWords.add(nested.word!!)
                }
            }
        }

        // Fallback short-word capture: check startKey -> endKey 2-letter words directly (e.g., "to", "in", "on", "hi")
        for (sc in startKeys) {
            val sNode = lexicon.root.children[sc] ?: continue
            for (ec in endKeys) {
                val twoCharNode = sNode.children[ec]
                if (twoCharNode != null && twoCharNode.isWord && twoCharNode.word != null) {
                    collectedWords.add(twoCharNode.word!!)
                }
            }
        }

        // Step 6: Full Trajectory DTW Rescoring and Language Model Ranking
        val results = mutableListOf<GlideCandidate>()

        for (word in collectedWords) {
            val spatialScore = spatialModel.alignWordTrajectory(word, smoothed, avgVelocity)
            if (spatialScore < 0.005f) continue

            val baseFreq = lexicon.getFrequency(word)
            // Log-scale LM probability
            val lmScore = ln(baseFreq + 1.0f) / ln(1001.0f)

            val totalScore = config.spatialWeight * spatialScore + config.lmWeight * lmScore

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
