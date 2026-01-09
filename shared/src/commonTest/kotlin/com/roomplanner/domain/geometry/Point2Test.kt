package com.roomplanner.domain.geometry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for Point2 geometric primitive.
 * Tests vector math, distance calculations, and precision.
 */
class Point2Test {
    @Test
    fun `distance calculation is correct`() {
        val p1 = Point2(0.0, 0.0)
        val p2 = Point2(3.0, 4.0)

        // Pythagorean triple: 3-4-5
        assertEquals(5.0, p1.distanceTo(p2), Point2.EPSILON)
    }

    @Test
    fun `distance is symmetric`() {
        val p1 = Point2(1.0, 2.0)
        val p2 = Point2(4.0, 6.0)

        assertEquals(p1.distanceTo(p2), p2.distanceTo(p1), Point2.EPSILON)
    }

    @Test
    fun `vector addition works correctly`() {
        val p1 = Point2(1.0, 2.0)
        val p2 = Point2(3.0, 4.0)
        val result = p1 + p2

        assertEquals(4.0, result.x, Point2.EPSILON)
        assertEquals(6.0, result.y, Point2.EPSILON)
    }

    @Test
    fun `vector subtraction works correctly`() {
        val p1 = Point2(5.0, 7.0)
        val p2 = Point2(2.0, 3.0)
        val result = p1 - p2

        assertEquals(3.0, result.x, Point2.EPSILON)
        assertEquals(4.0, result.y, Point2.EPSILON)
    }

    @Test
    fun `scalar multiplication works correctly`() {
        val p = Point2(2.0, 3.0)
        val result = p * 2.5

        assertEquals(5.0, result.x, Point2.EPSILON)
        assertEquals(7.5, result.y, Point2.EPSILON)
    }

    @Test
    fun `scalar division works correctly`() {
        val p = Point2(10.0, 15.0)
        val result = p / 5.0

        assertEquals(2.0, result.x, Point2.EPSILON)
        assertEquals(3.0, result.y, Point2.EPSILON)
    }

    @Test
    fun `normalize creates unit vector`() {
        val p = Point2(3.0, 4.0)
        val normalized = p.normalize()

        // Length should be 1.0
        val length = normalized.distanceTo(Point2.ORIGIN)
        assertEquals(1.0, length, Point2.EPSILON)

        // Direction should be preserved
        assertEquals(0.6, normalized.x, Point2.EPSILON) // 3/5
        assertEquals(0.8, normalized.y, Point2.EPSILON) // 4/5
    }

    @Test
    fun `normalize at origin returns origin`() {
        val origin = Point2.ORIGIN
        val normalized = origin.normalize()

        assertEquals(Point2.ORIGIN, normalized)
    }

    @Test
    fun `distanceSquaredTo is faster for comparisons`() {
        val p1 = Point2(0.0, 0.0)
        val p2 = Point2(3.0, 4.0)

        // Squared distance should be 25 (3² + 4² = 9 + 16 = 25)
        assertEquals(25.0, p1.distanceSquaredTo(p2), Point2.EPSILON)
    }

    @Test
    fun `toOffset converts to Float precision`() {
        val p = Point2(123.456789, 987.654321)
        val offset = p.toOffset()

        // Offset uses Float, so we lose some precision
        assertEquals(123.456789f, offset.x, 0.0001f)
        assertEquals(987.654321f, offset.y, 0.0001f)
    }

    @Test
    fun `fromOffset creates Point2 from Offset`() {
        val offset =
            androidx.compose.ui.geometry.Offset(
                10.5f,
                20.75f,
            )
        val point = Point2.fromOffset(offset)

        assertEquals(10.5, point.x, Point2.EPSILON)
        assertEquals(20.75, point.y, Point2.EPSILON)
    }

    @Test
    fun `CAD precision requirement - sub-millimeter accuracy`() {
        // Test that we can represent 0.1mm at scale (1 unit = 1 inch = 25.4mm)
        val p1 = Point2(0.0, 0.0)
        val p2 = Point2(0.0, 0.1 / 25.4) // 0.1mm in inches

        val distance = p1.distanceTo(p2)
        assertTrue(distance > 0.0, "Should represent sub-millimeter distance")

        // Should be approximately 0.1mm / 25.4mm/inch = 0.00394 inches
        assertEquals(0.00394, distance, 0.00001)
    }
}
