package com.keybord.engine.glide

import kotlin.math.*

/**
 * Handles raw touch trajectory validation, gesture throttling, smoothing,
 * uniform arc-length resampling, and corner/inflection detection.
 */
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

    /**
     * Checks gesture throttling heuristics:
     * 1. SHORT_TIME_FROM_LAST_TAP: Reject if initiated too soon after discrete key tap
     * 2. SHORT_GESTURE_TIME: Reject if total duration is below minimum gesture threshold
     * 3. SHORT_GESTURE_DISTANCE: Reject if cumulative arc length is too small
     * 4. WITHIN_ONE_KEY: Reject if entire trajectory stays within bounds of single key
     */
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
        var minX = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE
        var maxY = Float.MIN_VALUE

        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]
            totalDistance += p1.distanceTo(p2)
            minX = min(minX, p1.x)
            maxX = max(maxX, p1.x)
            minY = min(minY, p1.y)
            maxY = max(maxY, p1.y)
        }
        val lastPoint = points.last()
        minX = min(minX, lastPoint.x)
        maxX = max(maxX, lastPoint.x)
        minY = min(minY, lastPoint.y)
        maxY = max(maxY, lastPoint.y)

        val avgKeySize = if (keyPositions.isNotEmpty()) {
            keyPositions.values.map { (it.bounds.width() + it.bounds.height()) * 0.5f }.average().toFloat()
        } else {
            config.minDistanceDp
        }
        val minDistanceThreshold = max(config.minDistanceDp, avgKeySize * 0.35f)
        if (totalDistance < minDistanceThreshold) {
            return ValidationResult(false, ThrottleReason.SHORT_GESTURE_DISTANCE)
        }

        // WITHIN_ONE_KEY check: Did all points stay inside a single key hitbox?
        for (key in keyPositions.values) {
            if (minX >= key.bounds.left && maxX <= key.bounds.right &&
                minY >= key.bounds.top && maxY <= key.bounds.bottom) {
                return ValidationResult(false, ThrottleReason.WITHIN_ONE_KEY)
            }
        }

        return ValidationResult(true, ThrottleReason.NONE)
    }

    /**
     * Resamples the trajectory into equidistant points along the path arc-length.
     * Guarantees consistent spatial resolution independent of finger swipe speed.
     */
    fun resampleTrajectory(rawPoints: List<TouchPoint>, targetCount: Int = config.resamplePointCount): List<TouchPoint> {
        if (rawPoints.size <= 1) return rawPoints
        if (targetCount <= 1) return listOf(rawPoints.first())

        // Calculate cumulative arc-length distances
        val cumulative = FloatArray(rawPoints.size)
        cumulative[0] = 0f
        for (i in 1 until rawPoints.size) {
            cumulative[i] = cumulative[i - 1] + rawPoints[i - 1].distanceTo(rawPoints[i])
        }

        val totalLength = cumulative.last()
        if (totalLength <= 0.0001f) {
            return List(targetCount) { rawPoints.first() }
        }

        val segmentStep = totalLength / (targetCount - 1)
        val resampled = ArrayList<TouchPoint>(targetCount)
        resampled.add(rawPoints.first())

        var rawIndex = 0
        for (i in 1 until targetCount - 1) {
            val targetDist = i * segmentStep
            while (rawIndex < rawPoints.size - 1 && cumulative[rawIndex + 1] < targetDist) {
                rawIndex++
            }
            val d0 = cumulative[rawIndex]
            val d1 = cumulative[rawIndex + 1]
            val segmentLen = d1 - d0
            val t = if (segmentLen > 0f) (targetDist - d0) / segmentLen else 0f

            val p0 = rawPoints[rawIndex]
            val p1 = rawPoints[rawIndex + 1]

            val interpX = p0.x + t * (p1.x - p0.x)
            val interpY = p0.y + t * (p1.y - p0.y)
            val interpTime = (p0.timestamp + t * (p1.timestamp - p0.timestamp)).toLong()
            val interpPressure = p0.pressure + t * (p1.pressure - p0.pressure)

            resampled.add(TouchPoint(interpX, interpY, interpTime, interpPressure))
        }

        resampled.add(rawPoints.last())
        return resampled
    }

    /**
     * Smooths resampled points using a 3-point moving Gaussian window
     * to eliminate finger micro-tremors without destroying inflection vertices.
     */
    fun smoothTrajectory(points: List<TouchPoint>): List<TouchPoint> {
        if (points.size < 3) return points

        val smoothed = ArrayList<TouchPoint>(points.size)
        smoothed.add(points.first())

        for (i in 1 until points.size - 1) {
            val prev = points[i - 1]
            val curr = points[i]
            val next = points[i + 1]

            // 0.25, 0.5, 0.25 Gaussian kernel
            val smoothX = 0.25f * prev.x + 0.5f * curr.x + 0.25f * next.x
            val smoothY = 0.25f * prev.y + 0.5f * curr.y + 0.25f * next.y
            smoothed.add(TouchPoint(smoothX, smoothY, curr.timestamp, curr.pressure))
        }

        smoothed.add(points.last())
        return smoothed
    }

    /**
     * Detects corner points (inflection points / vertices) along the trajectory.
     * Corners correspond to sharp direction changes or velocity drops (dwells)
     * where the user intentionally turned on a target key.
     */
    fun extractCorners(points: List<TouchPoint>, angleThresholdRad: Float = 0.65f): List<Int> {
        if (points.size < 3) return emptyList()

        val corners = mutableListOf<Int>()
        corners.add(0) // Start point is always a primary key candidate

        for (i in 1 until points.size - 1) {
            val pPrev = points[i - 1]
            val pCurr = points[i]
            val pNext = points[i + 1]

            val v1x = pCurr.x - pPrev.x
            val v1y = pCurr.y - pPrev.y
            val v2x = pNext.x - pCurr.x
            val v2y = pNext.y - pCurr.y

            val norm1 = hypot(v1x, v1y)
            val norm2 = hypot(v2x, v2y)

            if (norm1 > 0.5f && norm2 > 0.5f) {
                val dot = (v1x * v2x + v1y * v2y) / (norm1 * norm2)
                val clampedDot = dot.coerceIn(-1.0f, 1.0f)
                val angle = acos(clampedDot)
                if (angle >= angleThresholdRad) {
                    corners.add(i)
                }
            }
        }

        corners.add(points.size - 1) // End point is always a primary key candidate
        return corners
    }

    /**
     * Extracts deduplicated sharp direction-change corner indices using a wider stride
     * so smoothing and micro-jitter do not split one turn into multiple corners.
     */
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
                        if (cos < last.second) {
                            bestCorners[bestCorners.lastIndex] = i to cos
                        }
                        continue
                    }
                    bestCorners.add(i to cos)
                }
            }
        }
        return bestCorners.map { it.first }
    }

    fun trajectoryCornerCount(points: List<TouchPoint>): Int =
        extractSharpCornerIndices(points).size + 1

    fun computeArcLength(points: List<TouchPoint>): Float {
        var total = 0f
        for (i in 0 until points.size - 1) {
            total += points[i].distanceTo(points[i + 1])
        }
        return total
    }
}
