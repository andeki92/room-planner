package com.roomplanner.domain.geometry

import androidx.compose.ui.geometry.Offset
import kotlinx.serialization.Serializable
import kotlin.math.sqrt

/**
 * 2D point with Double precision for CAD accuracy.
 * Immutable data class with operator overloads for vector math.
 *
 * Design rationale:
 * - Double (not Float) for sub-millimeter precision required in CAD
 * - Serializable for project persistence
 * - Operator overloads for natural vector math syntax
 */
@Serializable
data class Point2(
    val x: Double,
    val y: Double,
) {
    /**
     * Vector addition: p1 + p2
     */
    operator fun plus(other: Point2) = Point2(x + other.x, y + other.y)

    /**
     * Vector subtraction: p1 - p2
     */
    operator fun minus(other: Point2) = Point2(x - other.x, y - other.y)

    /**
     * Scalar multiplication: p * scalar
     */
    operator fun times(scalar: Double) = Point2(x * scalar, y * scalar)

    /**
     * Scalar division: p / scalar
     */
    operator fun div(scalar: Double) = Point2(x / scalar, y / scalar)

    /**
     * Calculate Euclidean distance to another point.
     * Uses Double precision to avoid floating-point errors.
     */
    fun distanceTo(other: Point2): Double {
        val dx = x - other.x
        val dy = y - other.y
        return sqrt(dx * dx + dy * dy)
    }

    /**
     * Calculate squared distance (faster when you don't need the actual distance).
     * Useful for comparisons: if (p1.distanceSquaredTo(p2) < threshold * threshold)
     */
    fun distanceSquaredTo(other: Point2): Double {
        val dx = x - other.x
        val dy = y - other.y
        return dx * dx + dy * dy
    }

    /**
     * Normalize to unit vector (length = 1.0).
     * Returns the same point if already at origin.
     */
    fun normalize(): Point2 {
        val length = distanceTo(ORIGIN)
        return if (length > EPSILON) {
            Point2(x / length, y / length)
        } else {
            this
        }
    }

    /**
     * Convert to Compose Offset for rendering.
     * Note: Offset uses Float, so precision loss occurs here.
     * Only use for final rendering, not intermediate calculations.
     */
    fun toOffset(): Offset = Offset(x.toFloat(), y.toFloat())

    companion object {
        val ORIGIN = Point2(0.0, 0.0)

        /**
         * Epsilon for floating-point comparisons.
         * Points closer than this are considered equal.
         */
        const val EPSILON = 1e-6

        /**
         * Create Point2 from Compose Offset (screen coordinates).
         */
        fun fromOffset(offset: Offset): Point2 = Point2(offset.x.toDouble(), offset.y.toDouble())
    }
}
