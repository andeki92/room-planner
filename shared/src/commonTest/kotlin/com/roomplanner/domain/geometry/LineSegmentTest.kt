package com.roomplanner.domain.geometry

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for LineSegment geometric primitive.
 * Tests length, angle, midpoint, and geometric queries.
 */
class LineSegmentTest {
    @Test
    fun `length calculation is correct`() {
        val line =
            LineSegment(
                start = Point2(0.0, 0.0),
                end = Point2(3.0, 4.0),
            )

        // Pythagorean triple: 3-4-5
        assertEquals(5.0, line.length, Point2.EPSILON)
    }

    @Test
    fun `midpoint calculation is correct`() {
        val line =
            LineSegment(
                start = Point2(2.0, 4.0),
                end = Point2(8.0, 10.0),
            )

        val midpoint = line.midpoint
        assertEquals(5.0, midpoint.x, Point2.EPSILON)
        assertEquals(7.0, midpoint.y, Point2.EPSILON)
    }

    @Test
    fun `angle calculation for horizontal line`() {
        val line =
            LineSegment(
                start = Point2(0.0, 0.0),
                end = Point2(10.0, 0.0),
            )

        assertEquals(0.0, line.angle(), Point2.EPSILON)
        assertTrue(line.isHorizontal())
    }

    @Test
    fun `angle calculation for vertical line`() {
        val line =
            LineSegment(
                start = Point2(0.0, 0.0),
                end = Point2(0.0, 10.0),
            )

        // Vertical line should be π/2 radians (90 degrees)
        assertEquals(PI / 2, line.angle(), 0.01)
        assertTrue(line.isVertical())
    }

    @Test
    fun `angle calculation for diagonal line`() {
        val line =
            LineSegment(
                start = Point2(0.0, 0.0),
                end = Point2(1.0, 1.0),
            )

        // 45 degree line should be π/4 radians
        assertEquals(PI / 4, line.angle(), Point2.EPSILON)
    }

    @Test
    fun `direction vector is normalized`() {
        val line =
            LineSegment(
                start = Point2(0.0, 0.0),
                end = Point2(3.0, 4.0),
            )

        val direction = line.direction()
        val length = direction.distanceTo(Point2.ORIGIN)

        assertEquals(1.0, length, Point2.EPSILON) // Unit vector
        assertEquals(0.6, direction.x, Point2.EPSILON) // 3/5
        assertEquals(0.8, direction.y, Point2.EPSILON) // 4/5
    }

    @Test
    fun `closestPointTo returns point on segment`() {
        val line =
            LineSegment(
                start = Point2(0.0, 0.0),
                end = Point2(10.0, 0.0),
            )

        val queryPoint = Point2(5.0, 3.0)
        val closest = line.closestPointTo(queryPoint)

        // Closest point should be (5, 0) - directly below query point
        assertEquals(5.0, closest.x, Point2.EPSILON)
        assertEquals(0.0, closest.y, Point2.EPSILON)
    }

    @Test
    fun `closestPointTo clamps to segment endpoints`() {
        val line =
            LineSegment(
                start = Point2(0.0, 0.0),
                end = Point2(10.0, 0.0),
            )

        // Query point beyond end of segment
        val queryPoint = Point2(15.0, 5.0)
        val closest = line.closestPointTo(queryPoint)

        // Should clamp to end point
        assertEquals(10.0, closest.x, Point2.EPSILON)
        assertEquals(0.0, closest.y, Point2.EPSILON)
    }

    @Test
    fun `distanceToPoint calculates perpendicular distance`() {
        val line =
            LineSegment(
                start = Point2(0.0, 0.0),
                end = Point2(10.0, 0.0),
            )

        val queryPoint = Point2(5.0, 3.0)
        val distance = line.distanceToPoint(queryPoint)

        // Distance should be 3.0 (perpendicular distance)
        assertEquals(3.0, distance, Point2.EPSILON)
    }

    @Test
    fun `isHorizontal detects horizontal lines`() {
        val horizontal =
            LineSegment(
                start = Point2(0.0, 5.0),
                end = Point2(10.0, 5.0),
            )

        assertTrue(horizontal.isHorizontal())
    }

    @Test
    fun `isVertical detects vertical lines`() {
        val vertical =
            LineSegment(
                start = Point2(5.0, 0.0),
                end = Point2(5.0, 10.0),
            )

        assertTrue(vertical.isVertical())
    }
}
