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
    val minDurationMs: Long = 60L,
    val minTapIntervalMs: Long = 40L,
    val resamplePointCount: Int = 40,
    val spatialSigmaXDp: Float = 22.0f,
    val spatialSigmaYDp: Float = 16.0f,
    val anchorRadiusDp: Float = 36.0f,
    val minBeamWidth: Int = 30,
    val maxBeamWidth: Int = 80,
    val spatialWeight: Float = 0.65f,
    val lmWeight: Float = 0.35f,
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

    val root = Node()
    var maxFrequency: Float = 1000f
        private set

    fun insert(word: String, frequency: Float = 1.0f) {
        if (word.isBlank()) return
        val clean = word.trim().lowercase()
        root.maxSubtreeFreq = maxOf(root.maxSubtreeFreq, frequency)
        var current = root
        for (c in clean) {
            current = current.children.getOrPut(c) { Node() }
            current.maxSubtreeFreq = maxOf(current.maxSubtreeFreq, frequency)
        }
        current.isWord = true
        current.frequency = maxOf(current.frequency, frequency)
        maxFrequency = maxOf(maxFrequency, current.frequency)
        current.word = clean
    }

    fun contains(word: String): Boolean {
        var current = root
        for (c in word.lowercase()) {
            current = current.children[c] ?: return false
        }
        return current.isWord
    }

    fun getFrequency(word: String): Float {
        var current = root
        for (c in word.lowercase()) {
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

    companion object {
        fun dedupConsecutiveChars(word: String): List<Char> {
            val out = ArrayList<Char>(word.length)
            for (ch in word.lowercase()) {
                if (out.isEmpty() || out.last() != ch) out.add(ch)
            }
            return out
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
        val endKeys = spatialModel.findProximityKeys(endPt, anchorRadius)
            .filter { spatialModel.anchorScoreForChar(endPt, it) > 0f }
            .toSet()
        if (startKeys.isEmpty() || endKeys.isEmpty()) return emptyList()

        val refArc = 400.0f * spatialModel.dpiScale
        val dynamicBeam = (config.minBeamWidth.toFloat() +
            (arcLength / refArc) * (config.maxBeamWidth - config.minBeamWidth).toFloat())
            .toInt()
            .coerceIn(config.minBeamWidth, config.maxBeamWidth)

        val sharpCorners = processor.extractSharpCornerIndices(smoothed)
        val cornerIndices = processor.extractCorners(smoothed, 0.5f)
        val step = max(1, smoothed.size / 20)
        val sampleIndices = ((step until smoothed.size step step) + sharpCorners + cornerIndices + (smoothed.size - 1))
            .distinct()
            .sorted()
            .filter { it in 1 until smoothed.size }

        val maxFreqLn = ln(lexicon.maxFrequency + 1f).coerceAtLeast(1f)

        data class BeamNode(
            val node: TrieLexicon.Node,
            val word: String,
            val cumScore: Float,
            val rankScore: Float
        )
        var beam = mutableListOf<BeamNode>()
        for (sc in startKeys) {
            val child = lexicon.root.children[sc] ?: continue
            val s = spatialModel.pointKeyDistanceScore(startPt, sc, avgVelocity)
            val prior = (ln(child.maxSubtreeFreq + 1f) / maxFreqLn).coerceIn(0f, 1f)
            beam.add(BeamNode(child, sc.toString(), s, s + 0.12f * prior))
        }

        val stepRadius = spatialModel.computeKeyUnitPx() * 1.15f

        for (pIdx in sampleIndices) {
            if (beam.isEmpty()) break
            val pt = smoothed[pIdx]
            val nearKeys = spatialModel.findProximityKeys(pt, stepRadius)
            val nextBeam = mutableListOf<BeamNode>()
            for (b in beam) {
                for (k in nearKeys) {
                    val child = b.node.children[k] ?: continue
                    val score = spatialModel.pointKeyDistanceScore(pt, k, avgVelocity)
                    if (score < 0.22f) continue
                    val newCum = b.cumScore + (score - 0.18f)
                    val prior = (ln(child.maxSubtreeFreq + 1f) / maxFreqLn).coerceIn(0f, 1f)
                    nextBeam.add(BeamNode(child, b.word + k, newCum, newCum + 0.12f * prior))
                }
                val lastC = b.word.last()
                val doubleChild = b.node.children[lastC]
                if (doubleChild != null) {
                    val stayScore = spatialModel.pointKeyDistanceScore(pt, lastC, avgVelocity)
                    if (stayScore > 0.35f) {
                        val newCum = b.cumScore - 0.01f
                        val prior = (ln(doubleChild.maxSubtreeFreq + 1f) / maxFreqLn).coerceIn(0f, 1f)
                        nextBeam.add(BeamNode(doubleChild, b.word + lastC, newCum, newCum + 0.12f * prior))
                    }
                }
                val cruiseCum = b.cumScore - 0.015f
                val cruisePrior = (ln(b.node.maxSubtreeFreq + 1f) / maxFreqLn).coerceIn(0f, 1f)
                nextBeam.add(BeamNode(b.node, b.word, cruiseCum, cruiseCum + 0.12f * cruisePrior))
            }
            nextBeam.sortByDescending { it.rankScore }
            beam = nextBeam.distinctBy { it.word }.take(dynamicBeam).toMutableList()
        }

        val candidates = mutableSetOf<String>()
        for (b in beam) {
            if (b.node.isWord && endKeys.contains(b.word.last())) candidates.add(b.word)
            for (ec in endKeys) {
                val endChild = b.node.children[ec]
                if (endChild != null && endChild.isWord && endChild.word != null) {
                    candidates.add(endChild.word!!)
                }
                val nested = b.node.children[b.word.last()]?.children?.get(ec)
                if (nested != null && nested.isWord && nested.word != null) {
                    candidates.add(nested.word!!)
                }
            }
        }
        for (sc in startKeys) {
            val sNode = lexicon.root.children[sc] ?: continue
            for (ec in endKeys) {
                val twoChar = sNode.children[ec]
                if (twoChar != null && twoChar.isWord && twoChar.word != null) candidates.add(twoChar.word!!)
            }
        }

        val expectedLen = sharpCorners.size + 2
        val results = mutableListOf<GlideCandidate>()
        for (w in candidates) {
            if (w.length < 2) continue
            val anchor = spatialModel.anchorScoreForChar(startPt, w.first()) *
                spatialModel.anchorScoreForChar(endPt, w.last())
            if (anchor <= 0f) continue

            val spatialScore = spatialModel.alignWordTrajectory(w, smoothed, avgVelocity)
            if (spatialScore < 0.005f) continue

            val dedupChars = SpatialModel.dedupConsecutiveChars(w)
            val cornerRecall = spatialModel.cornerRecallScore(dedupChars, smoothed, sharpCorners, avgVelocity)
            val lmScore = (ln(lexicon.getFrequency(w) + 1f) / maxFreqLn).coerceIn(0f, 1f)
            val lenDiff = abs(dedupChars.size.toFloat() - expectedLen.toFloat())
            val lenPenalty = 1.0f / (1.0f + lenDiff * 0.06f)

            val total = (config.spatialWeight * spatialScore + config.lmWeight * lmScore) *
                anchor * lenPenalty * cornerRecall
            results.add(GlideCandidate(w, total, spatialScore, lmScore))
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
