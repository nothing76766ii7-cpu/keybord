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
    val spatialSigmaDp: Float = 18.0f,
    val maxBeamWidth: Int = 40,
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

        val duration = points.last().timestamp - points.first().timestamp
        if (points.first().timestamp - lastTapTimestamp < config.minTapIntervalMs) {
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

        if (totalDistance < config.minDistanceDp) {
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
}

// ----------------------------------------------------------------------------
// 3. TRIE LEXICON
// ----------------------------------------------------------------------------

class TrieLexicon {
    class Node {
        val children = HashMap<Char, Node>()
        var isWord: Boolean = false
        var frequency: Float = 0f
        var word: String? = null
    }

    val root = Node()

    fun insert(word: String, frequency: Float = 1.0f) {
        if (word.isBlank()) return
        var current = root
        for (c in word.trim().lowercase()) {
            current = current.children.getOrPut(c) { Node() }
        }
        current.isWord = true
        current.frequency = maxOf(current.frequency, frequency)
        current.word = word.trim().lowercase()
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

    fun loadDefaultVocabulary() {
        val defaults = mapOf(
            "the" to 1000f, "be" to 800f, "to" to 750f, "of" to 700f, "and" to 680f,
            "hello" to 25f, "keyboard" to 15f, "glide" to 12f, "look" to 26f, "good" to 44f
        )
        for ((w, f) in defaults) insert(w, f)
    }
}

// ----------------------------------------------------------------------------
// 4. SPATIAL MODEL (GAUSSIAN & DTW)
// ----------------------------------------------------------------------------

class SpatialModel(
    private val keyPositions: Map<Char, KeyPosition>,
    private val config: GlideEngineConfig
) {

    fun pointKeyDistanceScore(point: TouchPoint, keyChar: Char, velocityPxPerMs: Float = 0f): Float {
        val key = keyPositions[keyChar.lowercaseChar()] ?: return 0.00001f
        val speedFactor = 1.0f + (velocityPxPerMs * config.velocityToleranceFactor).coerceIn(0f, 2.5f)
        val sigma = config.spatialSigmaDp * speedFactor
        val distSq = (point.x - key.centerX).pow(2) + (point.y - key.centerY).pow(2)
        return exp(-distSq / (2f * sigma * sigma))
    }

    fun alignWordTrajectory(candidateWord: String, trajectory: List<TouchPoint>, avgVelocity: Float = 0f): Float {
        val word = candidateWord.lowercase()
        val n = word.length; val m = trajectory.size
        if (n == 0 || m == 0) return 0f

        val firstScore = pointKeyDistanceScore(trajectory.first(), word.first(), avgVelocity)
        val lastScore = pointKeyDistanceScore(trajectory.last(), word.last(), avgVelocity)
        if (firstScore < 0.001f || lastScore < 0.001f) return 0.0001f

        val cost = Array(n + 1) { FloatArray(m + 1) { 1e6f } }
        cost[0][0] = 0f

        for (i in 1..n) {
            val char = word[i - 1]
            val isDouble = (i > 1 && word[i - 1] == word[i - 2])
            for (j in 1..m) {
                val ptScore = pointKeyDistanceScore(trajectory[j - 1], char, avgVelocity)
                var ptCost = -ln(ptScore.coerceAtLeast(1e-6f))
                if (isDouble) ptCost *= 0.15f // Repeated-letter dampening
                val minPrev = min(cost[i - 1][j - 1], min(cost[i - 1][j], cost[i][j - 1]))
                cost[i][j] = minPrev + ptCost
            }
        }
        return exp(-cost[n][m] / (n + m))
    }

    fun findProximityKeys(point: TouchPoint, maxRadiusDp: Float = 48.0f): List<Char> {
        val list = mutableListOf<Pair<Char, Float>>()
        for ((char, key) in keyPositions) {
            val d = hypot(point.x - key.centerX, point.y - key.centerY)
            if (d <= maxRadiusDp) list.add(char to d)
        }
        list.sortBy { it.second }
        return list.map { it.first }
    }
}

// ----------------------------------------------------------------------------
// 5. GLIDE DECODER
// ----------------------------------------------------------------------------

class GlideDecoder(
    private val keyPositions: Map<Char, KeyPosition>,
    private val lexicon: TrieLexicon,
    private val config: GlideEngineConfig = GlideEngineConfig()
) {
    private val processor = TrajectoryProcessor(config)
    private val spatialModel = SpatialModel(keyPositions, config)
    private var lastTapTimestamp: Long = 0L

    fun notifyTap(time: Long = System.currentTimeMillis()) { lastTapTimestamp = time }

    fun decodeGesture(rawPoints: List<TouchPoint>): List<GlideCandidate> {
        try {
            val pointsJson = org.json.JSONArray().apply {
                for (p in rawPoints) {
                    put(org.json.JSONObject().apply {
                        put("x", p.x.toDouble())
                        put("y", p.y.toDouble())
                        put("timestamp", p.timestamp)
                        put("pressure", 1.0)
                    })
                }
            }.toString()

            val keysJson = org.json.JSONObject().apply {
                for ((ch, key) in keyPositions) {
                    put(ch.toString(), org.json.JSONObject().apply {
                        put("character", ch.toString())
                        put("bounds", org.json.JSONObject().apply {
                            put("left", key.bounds.left.toDouble())
                            put("top", key.bounds.top.toDouble())
                            put("right", key.bounds.right.toDouble())
                            put("bottom", key.bounds.bottom.toDouble())
                        })
                        put("center_x", key.centerX.toDouble())
                        put("center_y", key.centerY.toDouble())
                    })
                }
            }.toString()

            val nativeRes = com.keyboard.engine.bridge.NativeBridge.glideDecodeGesture(
                pointsJson = pointsJson,
                keyPositionsJson = keysJson,
                lexiconWords = emptyArray(),
                lastTapTimestamp = lastTapTimestamp
            )
            val jsonArr = org.json.JSONArray(nativeRes)
            if (jsonArr.length() > 0) {
                val nativeCandidates = mutableListOf<GlideCandidate>()
                for (i in 0 until jsonArr.length()) {
                    val obj = jsonArr.getJSONObject(i)
                    nativeCandidates.add(
                        GlideCandidate(
                            word = obj.getString("word"),
                            totalScore = obj.getDouble("total_score").toFloat(),
                            spatialScore = obj.getDouble("spatial_score").toFloat(),
                            languageModelScore = obj.getDouble("language_model_score").toFloat()
                        )
                    )
                }
                return nativeCandidates
            }
        } catch (e: Throwable) {
            // fallback to Kotlin implementation
        }

        val valid = processor.validateTrajectory(rawPoints, lastTapTimestamp, keyPositions)
        if (!valid.isValidGesture) return emptyList()

        val smoothed = processor.smoothTrajectory(processor.resampleTrajectory(rawPoints))
        val duration = max(1L, smoothed.last().timestamp - smoothed.first().timestamp)
        var dist = 0f
        for (i in 0 until smoothed.size - 1) dist += smoothed[i].distanceTo(smoothed[i + 1])
        val avgVelocity = dist / duration.toFloat()

        val startKeys = spatialModel.findProximityKeys(smoothed.first())
        val endKeys = spatialModel.findProximityKeys(smoothed.last()).toSet()
        if (startKeys.isEmpty() || endKeys.isEmpty()) return emptyList()

        data class BeamNode(val node: TrieLexicon.Node, val word: String, val score: Float)
        var beam = mutableListOf<BeamNode>()
        for (sc in startKeys) {
            val child = lexicon.root.children[sc] ?: continue
            beam.add(BeamNode(child, sc.toString(), spatialModel.pointKeyDistanceScore(smoothed.first(), sc, avgVelocity)))
        }

        val step = max(1, smoothed.size / 8)
        var pIdx = step
        while (pIdx < smoothed.size && beam.isNotEmpty()) {
            val pt = smoothed[pIdx]
            val nearKeys = spatialModel.findProximityKeys(pt)
            val nextBeam = mutableListOf<BeamNode>()
            for (b in beam) {
                for (k in nearKeys) {
                    val child = b.node.children[k] ?: continue
                    nextBeam.add(BeamNode(child, b.word + k, b.score + spatialModel.pointKeyDistanceScore(pt, k, avgVelocity)))
                }
                val lastC = b.word.last()
                val doubleChild = b.node.children[lastC]
                if (doubleChild != null) {
                    nextBeam.add(BeamNode(doubleChild, b.word + lastC, b.score + 0.8f))
                }
                nextBeam.add(BeamNode(b.node, b.word, b.score * 0.95f))
            }
            nextBeam.sortByDescending { it.score }
            beam = nextBeam.distinctBy { it.word }.take(config.maxBeamWidth).toMutableList()
            pIdx += step
        }

        val candidates = mutableSetOf<String>()
        for (b in beam) {
            if (b.node.isWord && endKeys.contains(b.word.last())) candidates.add(b.word)
        }

        val results = mutableListOf<GlideCandidate>()
        for (w in candidates) {
            val spatialScore = spatialModel.alignWordTrajectory(w, smoothed, avgVelocity)
            if (spatialScore < 0.05f) continue
            val lmScore = ln(lexicon.getFrequency(w) + 1f) / ln(1001f)
            val total = config.spatialWeight * spatialScore + config.lmWeight * lmScore
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
