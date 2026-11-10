package com.keybord.engine.glide

import android.graphics.RectF
import kotlin.math.hypot

/**
 * Represents a single touch sample along a swipe trajectory.
 */
data class TouchPoint(
    val x: Float,
    val y: Float,
    val timestamp: Long,
    val pressure: Float = 1.0f
) {
    fun distanceTo(other: TouchPoint): Float = hypot(x - other.x, y - other.y)
}

/**
 * Geometric bounds and center coordinates for a keyboard key.
 */
data class KeyPosition(
    val character: Char,
    val bounds: RectF,
    val centerX: Float = bounds.centerX(),
    val centerY: Float = bounds.centerY()
)

/**
 * Decoded word candidate with associated spatial and combined probability scores.
 */
data class GlideCandidate(
    val word: String,
    val totalScore: Float,
    val spatialScore: Float,
    val languageModelScore: Float
) : Comparable<GlideCandidate> {
    override fun compareTo(other: GlideCandidate): Int = other.totalScore.compareTo(this.totalScore)
}

/**
 * Configuration parameters for the Glide Typing Engine.
 */
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
