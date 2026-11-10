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
    fun decodeGesture(rawPoints: List<TouchPoint>): List<GlideCandidate> {
        // Step 1: Trajectory Throttling & Validation (SHORT_TIME, SHORT_DIST, WITHIN_ONE_KEY)
        val validation = processor.validateTrajectory(rawPoints, lastTapTimestamp, keyPositions)
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

        // Step 3: Identify Candidate Start and End Keys
        val startKeys = spatialModel.findProximityKeys(smoothed.first(), maxRadiusDp = 50f)
        val endKeys = spatialModel.findProximityKeys(smoothed.last(), maxRadiusDp = 50f).toSet()

        if (startKeys.isEmpty() || endKeys.isEmpty()) {
            return emptyList()
        }

        // Step 4: Extract Key Vertices / Inflection Corners
        val cornerIndices = processor.extractCorners(smoothed)

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

        // Step through trajectory points in blocks
        val stepSize = max(1, smoothed.size / 8)
        var currentPointIndex = stepSize

        while (currentPointIndex < smoothed.size && beam.isNotEmpty()) {
            val currentPoint = smoothed[currentPointIndex]
            val candidateKeysNearPoint = spatialModel.findProximityKeys(currentPoint, maxRadiusDp = 55f)
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

                // 2. Self-transition / repeated-letter expansion (e.g. "look", "meet")
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

                // 3. Keep current node in beam if no close transition found (finger cruising)
                nextBeam.add(
                    BeamNode(
                        trieNode = bNode.trieNode,
                        currentWord = bNode.currentWord,
                        pointIndex = currentPointIndex,
                        cumulativeScore = bNode.cumulativeScore * 0.95f
                    )
                )
            }

            // Prune beam to maxBeamWidth
            nextBeam.sortByDescending { it.cumulativeScore }
            beam = nextBeam.distinctBy { it.currentWord }.take(config.maxBeamWidth).toMutableList()

            currentPointIndex += stepSize
        }

        // Gather all completed words reaching end keys
        for (bNode in beam) {
            collectCompletedWords(bNode.trieNode, endKeys, collectedWords)
        }

        // Also check any word currently reached that ends on an end key
        for (bNode in beam) {
            if (bNode.trieNode.isWord && endKeys.contains(bNode.currentWord.last())) {
                collectedWords.add(bNode.currentWord)
            }
        }

        // Step 6: Full Trajectory DTW Rescoring and Language Model Ranking
        val results = mutableListOf<GlideCandidate>()

        for (word in collectedWords) {
            val spatialScore = spatialModel.alignWordTrajectory(word, smoothed, avgVelocity)
            if (spatialScore < 0.05f) continue

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

    private fun collectCompletedWords(
        node: TrieLexicon.Node,
        endKeys: Set<Char>,
        outWords: MutableSet<String>,
        maxDepth: Int = 3
    ) {
        if (node.isWord && node.word != null && endKeys.contains(node.word!!.last())) {
            outWords.add(node.word!!)
        }
        if (maxDepth <= 0) return
        for (child in node.children.values) {
            collectCompletedWords(child, endKeys, outWords, maxDepth - 1)
        }
    }
}
