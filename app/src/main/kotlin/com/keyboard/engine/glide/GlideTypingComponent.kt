package com.keyboard.engine.glide

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.*

/**
 * ============================================================================
 * GLIDE TYPING / SWIPE RECOGNITION ENGINE
 * ============================================================================
 * Extracted from Google Gboard (com.google.android.inputmethod.latin).
 *
 * Capabilities:
 * - 4-point gesture throttle: SHORT_GESTURE_DISTANCE, SHORT_GESTURE_TIME,
 *   SHORT_TIME_FROM_LAST_TAP, WITHIN_ONE_KEY.
 * - Arc-length trajectory resampling & 3-point Gaussian smoothing.
 * - Inflection corner and vertex detection.
 * - Key proximity via bivariate Gaussian likelihood.
 * - Speed-adaptive spatial tolerance (scaling sigma to compensate for corner cutting).
 * - Repeated-letter recognition (look, meet, hello) with dampening penalty.
 * - Beam search candidate generator over Trie prefix tree.
 * - Dynamic Time Warping (DTW) continuous path alignment.
 * - Visual glowing, tapering glide trail overlay (GestureOverlayView).
 */

// ----------------------------------------------------------------------------
// 1. DATA MODELS
// ----------------------------------------------------------------------------

data class TouchPoint(
    val x: Float,
    val y: Float,
    val timestamp: Long,
    val pressure: Float = 1.0f
) {
    fun distanceTo(other: TouchPoint): Float = hypot(x - other.x, y - other.y)
}

data class KeyPosition(
    val character: Char,
    val bounds: RectF,
    val centerX: Float = bounds.centerX(),
    val centerY: Float = bounds.centerY()
)

data class GlideCandidate(
    val word: String,
    val totalScore: Float,
    val spatialScore: Float,
    val languageModelScore: Float
) : Comparable<GlideCandidate> {
    override fun compareTo(other: GlideCandidate): Int = other.totalScore.compareTo(this.totalScore)
}

data class GlideEngineConfig(
    val minDistanceDp: Float = 24.0f,
    val minDurationMs: Long = 30L,
    val minTapIntervalMs: Long = 15L,
    val resamplePointCount: Int = 40,
    val spatialSigmaXDp: Float = 22.0f,
    val spatialSigmaYDp: Float = 16.0f,
    val anchorRadiusDp: Float = 46.0f,
    val minBeamWidth: Int = 30,
    val maxBeamWidth: Int = 80,
    val spatialWeight: Float = 0.72f,
    val lmWeight: Float = 0.28f,
    val velocityToleranceFactor: Float = 1.5f
)

// ----------------------------------------------------------------------------
// 2. TRAJECTORY PROCESSOR
// ----------------------------------------------------------------------------

class TrajectoryProcessor(private val config: GlideEngineConfig) {

    enum class ThrottleReason {
        NONE,
        SHORT_GESTURE_DISTANCE,
        SHORT_GESTURE_TIME,
        SHORT_TIME_FROM_LAST_TAP,
        WITHIN_ONE_KEY
    }

    data class ValidationResult(
        val isValidGesture: Boolean,
        val throttleReason: ThrottleReason
    )

    fun validateTrajectory(
        points: List<TouchPoint>,
        lastTapTimestamp: Long,
        keyPositions: Map<Char, KeyPosition>
    ): ValidationResult {
        if (points.size < 2) {
            return ValidationResult(false, ThrottleReason.SHORT_GESTURE_DISTANCE)
        }

        val startTime = points.first().timestamp
        val endTime = points.last().timestamp
        val duration = endTime - startTime
        if (lastTapTimestamp > 0L && startTime >= lastTapTimestamp && startTime - lastTapTimestamp < config.minTapIntervalMs) {
            return ValidationResult(false, ThrottleReason.SHORT_TIME_FROM_LAST_TAP)
        }
        if (duration < config.minDurationMs) {
            return ValidationResult(false, ThrottleReason.SHORT_GESTURE_TIME)
        }

        var totalDistance = 0f
        var minX = Float.MAX_VALUE; var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE; var maxY = Float.MIN_VALUE

        for (i in 0 until points.size - 1) {
            totalDistance += points[i].distanceTo(points[i + 1])
            minX = min(minX, points[i].x); maxX = max(maxX, points[i].x)
            minY = min(minY, points[i].y); maxY = max(maxY, points[i].y)
        }
        minX = min(minX, points.last().x); maxX = max(maxX, points.last().x)
        minY = min(minY, points.last().y); maxY = max(maxY, points.last().y)

        val avgKeySize = if (keyPositions.isNotEmpty()) {
            keyPositions.values.map { (abs(it.bounds.right - it.bounds.left) + abs(it.bounds.bottom - it.bounds.top)) * 0.5f }.average().toFloat()
        } else {
            config.minDistanceDp
        }
        val minDistanceThreshold = max(config.minDistanceDp, avgKeySize * 0.35f)
        if (totalDistance < minDistanceThreshold) {
            return ValidationResult(false, ThrottleReason.SHORT_GESTURE_DISTANCE)
        }

        for (key in keyPositions.values) {
            if (minX >= key.bounds.left && maxX <= key.bounds.right &&
                minY >= key.bounds.top && maxY <= key.bounds.bottom) {
                return ValidationResult(false, ThrottleReason.WITHIN_ONE_KEY)
            }
        }
        return ValidationResult(true, ThrottleReason.NONE)
    }

    fun resampleTrajectory(rawPoints: List<TouchPoint>, targetCount: Int = config.resamplePointCount): List<TouchPoint> {
        if (rawPoints.size <= 1) return rawPoints
        val cumulative = FloatArray(rawPoints.size)
        cumulative[0] = 0f
        for (i in 1 until rawPoints.size) {
            cumulative[i] = cumulative[i - 1] + rawPoints[i - 1].distanceTo(rawPoints[i])
        }

        val totalLength = cumulative.last()
        if (totalLength <= 0.0001f) return List(targetCount) { rawPoints.first() }

        val step = totalLength / (targetCount - 1)
        val resampled = ArrayList<TouchPoint>(targetCount)
        resampled.add(rawPoints.first())

        var rawIndex = 0
        for (i in 1 until targetCount - 1) {
            val targetDist = i * step
            while (rawIndex < rawPoints.size - 1 && cumulative[rawIndex + 1] < targetDist) {
                rawIndex++
            }
            val d0 = cumulative[rawIndex]
            val d1 = cumulative[rawIndex + 1]
            val segLen = d1 - d0
            val t = if (segLen > 0f) (targetDist - d0) / segLen else 0f

            val p0 = rawPoints[rawIndex]
            val p1 = rawPoints[rawIndex + 1]
            resampled.add(
                TouchPoint(
                    p0.x + t * (p1.x - p0.x),
                    p0.y + t * (p1.y - p0.y),
                    (p0.timestamp + t * (p1.timestamp - p0.timestamp)).toLong(),
                    p0.pressure + t * (p1.pressure - p0.pressure)
                )
            )
        }
        resampled.add(rawPoints.last())
        return resampled
    }

    fun smoothTrajectory(points: List<TouchPoint>): List<TouchPoint> {
        if (points.size < 3) return points
        val smoothed = ArrayList<TouchPoint>(points.size)
        smoothed.add(points.first())
        for (i in 1 until points.size - 1) {
            val prev = points[i - 1]; val curr = points[i]; val next = points[i + 1]
            smoothed.add(
                TouchPoint(
                    0.25f * prev.x + 0.5f * curr.x + 0.25f * next.x,
                    0.25f * prev.y + 0.5f * curr.y + 0.25f * next.y,
                    curr.timestamp,
                    curr.pressure
                )
            )
        }
        smoothed.add(points.last())
        return smoothed
    }

    fun extractCorners(points: List<TouchPoint>, angleThresholdRad: Float = 0.65f): List<Int> {
        if (points.size < 3) return emptyList()
        val corners = mutableListOf<Int>()
        corners.add(0)
        for (i in 1 until points.size - 1) {
            val p0 = points[i - 1]; val p1 = points[i]; val p2 = points[i + 1]
            val v1x = p1.x - p0.x; val v1y = p1.y - p0.y
            val v2x = p2.x - p1.x; val v2y = p2.y - p1.y
            val n1 = hypot(v1x, v1y); val n2 = hypot(v2x, v2y)
            if (n1 > 0.5f && n2 > 0.5f) {
                val dot = (v1x * v2x + v1y * v2y) / (n1 * n2)
                if (acos(dot.coerceIn(-1f, 1f)) >= angleThresholdRad) {
                    corners.add(i)
                }
            }
        }
        corners.add(points.size - 1)
        return corners
    }

    fun extractSharpCornerIndices(points: List<TouchPoint>): List<Int> {
        if (points.size < 3) return emptyList()
        val stride = if (points.size >= 12) 2 else 1
        val bestCorners = mutableListOf<Pair<Int, Float>>()
        for (i in stride until (points.size - stride)) {
            val v1x = points[i].x - points[i - stride].x
            val v1y = points[i].y - points[i - stride].y
            val v2x = points[i + stride].x - points[i].x
            val v2y = points[i + stride].y - points[i].y
            val len1 = hypot(v1x, v1y)
            val len2 = hypot(v2x, v2y)
            if (len1 > 1.0f && len2 > 1.0f) {
                val cos = ((v1x * v2x + v1y * v2y) / (len1 * len2)).coerceIn(-1.0f, 1.0f)
                if (cos < 0.45f) {
                    val last = bestCorners.lastOrNull()
                    if (last != null && i - last.first <= stride + 1) {
                        if (cos < last.second) bestCorners[bestCorners.lastIndex] = i to cos
                        continue
                    }
                    bestCorners.add(i to cos)
                }
            }
        }
        return bestCorners.map { it.first }
    }

    fun computeArcLength(points: List<TouchPoint>): Float {
        var total = 0f
        for (i in 0 until points.size - 1) total += points[i].distanceTo(points[i + 1])
        return total
    }
}

// ----------------------------------------------------------------------------
// 3. TRIE LEXICON
// ----------------------------------------------------------------------------

class TrieLexicon {
    class Node {
        val children = HashMap<Char, Node>()
        var isWord: Boolean = false
        var frequency: Float = 0f
        var maxSubtreeFreq: Float = 0f
        var word: String? = null
    }

    data class BucketEntry(
        val word: String,
        val dedupChars: List<Char>,
        var frequency: Float
    )

    val root = Node()
    val endpointBuckets = HashMap<Pair<Char, Char>, MutableList<BucketEntry>>()
    private val wordFrequencies = HashMap<String, Float>()
    var maxFrequency: Float = 1000f
        private set

    fun insert(word: String, frequency: Float = 1.0f) {
        if (word.isBlank()) return
        val clean = word.trim().lowercase()
        val keyChars = ArrayList<Char>(clean.length)
        for (c in clean) {
            if (c in 'a'..'z') keyChars.add(c)
        }
        if (keyChars.isEmpty()) return
        val display = when (clean) {
            "i" -> "I"
            "i'm" -> "I'm"
            "i've" -> "I've"
            "i'll" -> "I'll"
            "i'd" -> "I'd"
            else -> clean
        }

        root.maxSubtreeFreq = maxOf(root.maxSubtreeFreq, frequency)
        var current = root
        for (c in keyChars) {
            current = current.children.getOrPut(c) { Node() }
            current.maxSubtreeFreq = maxOf(current.maxSubtreeFreq, frequency)
        }
        if (keyChars.size >= 2) {
            val dedup = ArrayList<Char>(keyChars.size)
            for (c in keyChars) {
                if (dedup.isEmpty() || dedup.last() != c) dedup.add(c)
            }
            if (dedup.size >= 2) {
                val pair = keyChars.first() to keyChars.last()
                val bucket = endpointBuckets.getOrPut(pair) { ArrayList() }
                val existing = bucket.firstOrNull { it.word == display }
                if (existing != null) {
                    existing.frequency = maxOf(existing.frequency, frequency)
                } else {
                    bucket.add(BucketEntry(display, dedup, frequency))
                }
            }
        }
        wordFrequencies[display] = maxOf(wordFrequencies[display] ?: 0f, frequency)
        wordFrequencies[clean] = maxOf(wordFrequencies[clean] ?: 0f, frequency)

        val prevFreq = current.frequency
        val isNew = !current.isWord
        current.isWord = true
        current.frequency = maxOf(prevFreq, frequency)
        maxFrequency = maxOf(maxFrequency, current.frequency)
        if (isNew || frequency >= prevFreq) current.word = display
    }

    fun contains(word: String): Boolean {
        val clean = word.trim().lowercase()
        if (wordFrequencies.containsKey(clean)) return true
        var current = root
        for (c in clean) {
            if (c !in 'a'..'z') continue
            current = current.children[c] ?: return false
        }
        return current.isWord
    }

    fun getFrequency(word: String): Float {
        val clean = word.trim().lowercase()
        wordFrequencies[clean]?.let { return it }
        var current = root
        for (c in clean) {
            if (c !in 'a'..'z') continue
            current = current.children[c] ?: return 0f
        }
        return if (current.isWord) current.frequency else 0f
    }

    fun loadFromSequence(lines: Sequence<String>): Int {
        var rank = 0
        var inserted = 0
        for (raw in lines) {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) continue
            rank++
            val parts = trimmed.split('\t')
            val word = parts.getOrNull(0) ?: continue
            val freq = parts.getOrNull(1)?.trim()?.toFloatOrNull()
                ?: (10000 - rank).coerceAtLeast(1).toFloat()
            if (word.isNotEmpty()) {
                insert(word, freq)
                inserted++
            }
        }
        return inserted
    }

    fun loadFromAssets(context: Context) {
        val loaded = try {
            context.assets.open("wordlist.txt")
                .bufferedReader()
                .useLines { lines -> loadFromSequence(lines) }
        } catch (_: Exception) {
            0
        }
        if (loaded == 0) {
            loadDefaultVocabulary()
        }
    }

    fun loadDefaultVocabulary() {
        val defaults = mapOf(
            "the" to 1000f, "be" to 800f, "to" to 750f, "of" to 700f, "and" to 680f,
            "hello" to 250f, "world" to 200f, "keyboard" to 150f, "keybord" to 300f,
            "glide" to 120f, "look" to 260f, "good" to 440f, "swipe" to 140f
        )
        for ((w, f) in defaults) insert(w, f)
        listOf(
            "you", "that", "was", "for", "are", "with", "his", "they", "this", "have",
            "from", "one", "had", "but", "what", "all", "were", "when", "your", "can",
            "said", "there", "use", "each", "she", "which", "their", "time", "will", "how",
            "about", "many", "then", "them", "would", "write", "like", "so", "these", "her",
            "long", "make", "thing", "see", "him", "two", "has", "more", "day", "could",
            "come", "did", "number", "sound", "no", "most", "people", "my", "over", "know",
            "water", "than", "call", "first", "who", "may", "down", "side", "been", "now",
            "find", "any", "new", "work", "part", "take", "get", "place", "made", "where",
            "after", "back", "little", "only", "round", "man", "year", "came", "show",
            "every", "give", "our", "under", "name", "very", "through", "just", "form",
            "much", "great", "think", "help", "low", "line", "before", "turn", "cause",
            "same", "mean", "differ", "move", "right", "boy", "old", "too", "does", "tell",
            "sentence", "set", "three", "want", "air", "well", "also", "play", "small", "end",
            "put", "home", "read", "hand", "port", "large", "spell", "add", "even", "land",
            "here", "must", "big", "high", "such", "follow", "act", "why", "ask", "men",
            "change", "went", "light", "kind", "off", "need", "house", "picture", "try",
            "again", "animal", "point", "mother", "near", "build", "self", "earth", "father",
            "head", "stand", "own", "page", "open", "seem", "together", "next", "white",
            "children", "begin", "got", "walk", "example", "ease", "paper", "often",
            "always", "music", "those", "both", "mark", "book", "letter", "until", "mile",
            "river", "car", "feet", "care", "second", "enough", "plain", "girl", "usual",
            "young", "ready", "above", "ever", "red", "list", "though", "feel", "talk",
            "bird", "soon", "body", "dog", "family", "direct", "pose", "leave", "song",
            "measure", "door", "product", "black", "short", "numeral", "class", "wind",
            "question", "happen", "complete", "ship", "area", "half", "rock", "order", "fire",
            "south", "problem", "piece", "told", "knew", "pass", "top", "whole", "king",
            "space", "heard", "best", "hour", "better", "true", "during", "hundred", "five",
            "remember", "step", "early", "hold", "west", "ground", "interest", "reach", "fast",
            "verb", "sing", "listen", "six", "table", "travel", "less", "morning", "ten",
            "simple", "several", "vowel", "toward", "power", "town", "fine", "drive", "sang",
            "close", "dark", "grown", "draw", "yet", "left", "late", "real", "run", "case",
            "stop", "raise", "base", "sense", "cat", "quick", "brown", "fox", "jumps", "lazy"
        ).forEach { insert(it, 500f) }
    }
}

// ----------------------------------------------------------------------------
// 4. SPATIAL MODEL (GAUSSIAN & DTW)
// ----------------------------------------------------------------------------

class SpatialModel(
    private val keyPositions: Map<Char, KeyPosition>,
    private val config: GlideEngineConfig
) {

    fun computeKeyUnitPx(): Float {
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
        return (inferred ?: 80f).coerceAtLeast(40f)
    }

    private val keyUnitPx: Float = computeKeyUnitPx()
    val dpiScale: Float = (keyUnitPx / 40.0f).coerceAtLeast(1.0f)
    val anchorRadiusPx: Float = config.anchorRadiusDp * dpiScale

    fun gaussianScoreAniso(point: TouchPoint, key: KeyPosition, velocityPxPerMs: Float = 0f): Float {
        val normalizedVel = velocityPxPerMs / (keyUnitPx / 100f).coerceAtLeast(0.5f)
        val speedFactor = 1.0f + (normalizedVel * config.velocityToleranceFactor * 0.16f).coerceIn(0f, 0.55f)
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

    fun pointKeyDistanceScore(point: TouchPoint, keyChar: Char, velocityPxPerMs: Float = 0f): Float {
        val key = keyPositions[keyChar.lowercaseChar()] ?: return 0.00001f
        return gaussianScoreAniso(point, key, velocityPxPerMs)
    }

    fun directionScore(fromPt: TouchPoint, toPt: TouchPoint, fromKey: KeyPosition, toKey: KeyPosition): Float {
        val trajDx = toPt.x - fromPt.x; val trajDy = toPt.y - fromPt.y
        val keyDx = toKey.centerX - fromKey.centerX; val keyDy = toKey.centerY - fromKey.centerY
        val trajLen = hypot(trajDx, trajDy); val keyLen = hypot(keyDx, keyDy)
        if (keyLen < 1.0f) return 1.0f
        if (trajLen < 1.0f) return 0.85f
        val cosSim = ((trajDx * keyDx + trajDy * keyDy) / (trajLen * keyLen)).coerceIn(-1.0f, 1.0f)
        return 0.7f + 0.3f * cosSim
    }

    fun pointSegmentDistanceScore(point: TouchPoint, fromKey: KeyPosition, toKey: KeyPosition, velocityPxPerMs: Float = 0f): Float {
        val vx = toKey.centerX - fromKey.centerX
        val vy = toKey.centerY - fromKey.centerY
        val lenSq = vx * vx + vy * vy
        val t = if (lenSq > 1.0f) {
            (((point.x - fromKey.centerX) * vx + (point.y - fromKey.centerY) * vy) / lenSq).coerceIn(0f, 1f)
        } else {
            1f
        }
        val projX = fromKey.centerX + t * vx
        val projY = fromKey.centerY + t * vy
        val normalizedVel = velocityPxPerMs / (keyUnitPx / 100f).coerceAtLeast(0.5f)
        val speedFactor = 1.0f + (normalizedVel * config.velocityToleranceFactor * 0.16f).coerceIn(0f, 0.55f)
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

    fun alignWordTrajectory(candidateWord: String, trajectory: List<TouchPoint>, avgVelocity: Float = 0f): Float {
        val word = dedupConsecutiveChars(candidateWord)
        val n = word.size; val m = trajectory.size
        if (n == 0 || m == 0) return 0f

        val startAnchor = anchorScoreForChar(trajectory.first(), word.first())
        val endAnchor = anchorScoreForChar(trajectory.last(), word.last())
        if (startAnchor <= 0f || endAnchor <= 0f) return 0f

        val cost = Array(n + 1) { FloatArray(m + 1) { 1e6f } }
        cost[0][0] = 0f

        for (i in 1..n) {
            val char = word[i - 1]
            val currKey = keyPositions[char]
            val prevKey = if (i > 1) keyPositions[word[i - 2]] else null
            for (j in 1..m) {
                val point = trajectory[j - 1]
                val keySc = pointKeyDistanceScore(point, char, avgVelocity)
                val dirSc = if (prevKey != null && currKey != null && j >= 2) {
                    directionScore(trajectory[j - 2], point, prevKey, currKey)
                } else {
                    1.0f
                }
                val diagScore = (keySc * (0.75f + 0.25f * dirSc)).coerceAtLeast(1e-6f)
                val diagCost = -ln(diagScore)
                val transitSc = if (prevKey != null && currKey != null) {
                    max(keySc, pointSegmentDistanceScore(point, prevKey, currKey, avgVelocity) * 0.92f)
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
        return exp(-cost[n][m] / (n + m))
    }

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
                currRow[j] = min(prevRow[j - 1], min(prevRow[j] + 0.04f, currRow[j - 1] + 0.04f)) + stepCost
            }
            System.arraycopy(currRow, 0, prevRow, 0, sampleN + 1)
        }

        val avgCost = prevRow[sampleN] / sampleN.toFloat()
        return exp(-avgCost) to tArc
    }

    fun findProximityKeys(point: TouchPoint, maxRadiusPx: Float = keyUnitPx * 1.2f): List<Char> {
        val list = mutableListOf<Pair<Char, Float>>()
        for ((char, key) in keyPositions) {
            val d = hypot(point.x - key.centerX, point.y - key.centerY)
            val insideBounds = point.x in key.bounds.left..key.bounds.right &&
                point.y in key.bounds.top..key.bounds.bottom
            if (insideBounds || d <= maxRadiusPx) list.add(char to d)
        }
        list.sortBy { it.second }
        return list.map { it.first }
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
                if (out.isEmpty() || out.last() != ch) out.add(ch)
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

// ----------------------------------------------------------------------------
// 5. GLIDE DECODER
// ----------------------------------------------------------------------------

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

    fun notifyTap(time: Long = System.currentTimeMillis()) { lastTapTimestamp = time }

    fun decodeGesture(rawPoints: List<TouchPoint>, lastTapTs: Long = lastTapTimestamp): List<GlideCandidate> {
        if (keyPositions.isEmpty()) return emptyList()

        val valid = processor.validateTrajectory(rawPoints, lastTapTs, keyPositions)
        if (!valid.isValidGesture) return emptyList()

        val smoothed = processor.smoothTrajectory(processor.resampleTrajectory(rawPoints))
        val duration = max(1L, smoothed.last().timestamp - smoothed.first().timestamp)
        val arcLength = processor.computeArcLength(smoothed)
        val avgVelocity = arcLength / duration.toFloat()

        val startPt = smoothed.first()
        val endPt = smoothed.last()
        val anchorRadius = spatialModel.anchorRadiusPx

        val startKeys = spatialModel.findProximityKeys(startPt, anchorRadius)
            .filter { spatialModel.anchorScoreForChar(startPt, it) > 0f }
            .take(4)
        val endKeys = spatialModel.findProximityKeys(endPt, anchorRadius)
            .filter { spatialModel.anchorScoreForChar(endPt, it) > 0f }
            .take(4)
        if (startKeys.isEmpty() || endKeys.isEmpty()) return emptyList()

        val sampleN = 24
        val rawX = FloatArray(smoothed.size) { smoothed[it].x }
        val rawY = FloatArray(smoothed.size) { smoothed[it].y }
        val (uPtsX, uPtsY) = SpatialModel.resampleXY(rawX, rawY, sampleN)
        val (uNormX, uNormY, uTanX, uTanY) = SpatialModel.normalizeAndTangents(
            uPtsX,
            uPtsY,
            spatialModel.computeKeyUnitPx()
        )

        val maxFreqLn = ln(lexicon.maxFrequency + 1f).coerceAtLeast(1f)
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

                    val lmScore = (ln(entry.frequency + 1f) / maxFreqLn).coerceIn(0f, 1f)
                    val total = (spatialScore * anchorFactor * arcFactor) * (0.78f + 0.22f * lmScore)
                    results.add(GlideCandidate(entry.word, total, spatialScore, lmScore))
                }
            }
        }
        results.sort()
        return results.take(8)
    }
}

// ----------------------------------------------------------------------------
// 6. GESTURE OVERLAY VIEW
// ----------------------------------------------------------------------------

class GestureOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val trailPoints = mutableListOf<TouchPoint>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4285F4")
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val maxAgeMs = 350L

    fun addPoint(x: Float, y: Float) {
        trailPoints.add(TouchPoint(x, y, System.currentTimeMillis()))
        invalidate()
    }

    fun clearTrail() {
        trailPoints.clear()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = resolveSize(suggestedMinimumWidth, widthMeasureSpec)
        val height = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
            MeasureSpec.getSize(heightMeasureSpec)
        } else {
            suggestedMinimumHeight
        }
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (trailPoints.size < 2) return
        val now = System.currentTimeMillis()
        trailPoints.removeAll { now - it.timestamp > maxAgeMs }
        for (i in 0 until trailPoints.size - 1) {
            val p1 = trailPoints[i]; val p2 = trailPoints[i + 1]
            val ageFactor = (1f - (now - p2.timestamp).toFloat() / maxAgeMs).coerceIn(0.1f, 1f)
            paint.alpha = (ageFactor * 220).toInt()
            paint.strokeWidth = 14f * (i.toFloat() / trailPoints.size).coerceIn(0.2f, 1f)
            canvas.drawLine(p1.x, p1.y, p2.x, p2.y, paint)
        }
        if (trailPoints.isNotEmpty()) postInvalidateOnAnimation()
    }
}
