package com.roomplanner.domain.geometry

import kotlinx.serialization.Serializable
import kotlin.math.atan2

/**
 * Line segment defined by two endpoints.
 * Immutable data class with computed properties for common geometric queries.
 *
 * Design rationale:
 * - Represents a straight line segment (not infinite line)
 * - Cached geometry properties (length, midpoint, angle)
 * - Serializable for persistence
 */
@Serializable
data class LineSegment(
    val start: Point2,
    val end: Point2,
) {
    /**
     * Length of the line segment.
     */
    val length: Double
        get() = start.distanceTo(end)

    /**
     * Midpoint of the line segment.
     */
    val midpoint: Point2
        get() =
            Point2(
                (start.x + end.x) / 2.0,
                (start.y + end.y) / 2.0,
            )

    /**
     * Angle of the line segment in radians.
     * Returns angle from start to end point.
     * Range: [-π, π]
     */
    fun angle(): Double = atan2(end.y - start.y, end.x - start.x)

    /**
     * Direction vector (unit vector from start to end).
     */
    fun direction(): Point2 = (end - start).normalize()

    /**
     * Check if this line segment is approximately vertical.
     * @param tolerance angle tolerance in radians (default: 0.01 rad ≈ 0.57°)
     */
    fun isVertical(tolerance: Double = 0.01): Boolean {
        val angle = kotlin.math.abs(angle())
        return kotlin.math.abs(angle - kotlin.math.PI / 2) < tolerance ||
            kotlin.math.abs(angle + kotlin.math.PI / 2) < tolerance
    }

    /**
     * Check if this line segment is approximately horizontal.
     * @param tolerance angle tolerance in radians (default: 0.01 rad ≈ 0.57°)
     */
    fun isHorizontal(tolerance: Double = 0.01): Boolean {
        val angle = kotlin.math.abs(angle())
        return angle < tolerance || kotlin.math.abs(angle - kotlin.math.PI) < tolerance
    }

    /**
     * Find the closest point on this line segment to a given point.
     * @param point the query point
     * @return the closest point on the segment (may be start or end)
     */
    fun closestPointTo(point: Point2): Point2 {
        val dx = end.x - start.x
        val dy = end.y - start.y

        // Calculate parameter t (0 = start, 1 = end)
        val t =
            if (length < Point2.EPSILON) {
                0.0 // Degenerate segment (start == end)
            } else {
                val dot = (point.x - start.x) * dx + (point.y - start.y) * dy
                val lengthSquared = dx * dx + dy * dy
                (dot / lengthSquared).coerceIn(0.0, 1.0)
            }

        return Point2(
            start.x + t * dx,
            start.y + t * dy,
        )
    }

    /**
     * Distance from a point to this line segment.
     */
    fun distanceToPoint(point: Point2): Double {
        val closest = closestPointTo(point)
        return point.distanceTo(closest)
    }
}
