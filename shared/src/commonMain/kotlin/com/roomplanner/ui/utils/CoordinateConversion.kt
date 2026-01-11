package com.roomplanner.ui.utils

import androidx.compose.ui.geometry.Offset
import com.roomplanner.data.models.CameraTransform
import com.roomplanner.domain.geometry.Point2

/**
 * Coordinate conversion utilities for DrawingCanvas.
 * Pure functions (no side effects) - easy to test.
 *
 * Design rationale:
 * - Extracted from DrawingCanvas to reduce complexity
 * - Pure functions → testable in isolation
 * - Consistent screen ↔ world conversion across entire app
 */
object CoordinateConversion {
    /**
     * Convert screen coordinates to world coordinates.
     * Inverse transform: world = (screen - pan) / zoom
     *
     * @param screenOffset Position in screen space (pixels)
     * @param camera Camera transform (pan, zoom)
     * @return Position in world space (cm or inches)
     */
    fun screenToWorld(
        screenOffset: Offset,
        camera: CameraTransform,
    ): Point2 {
        val worldX = ((screenOffset.x - camera.panX) / camera.zoom).toDouble()
        val worldY = ((screenOffset.y - camera.panY) / camera.zoom).toDouble()
        return Point2(worldX, worldY)
    }

    /**
     * Convert world coordinates to screen coordinates.
     * Forward transform: screen = (world * zoom) + pan
     *
     * @param worldPoint Position in world space (cm or inches)
     * @param camera Camera transform (pan, zoom)
     * @return Position in screen space (pixels)
     */
    fun worldToScreen(
        worldPoint: Point2,
        camera: CameraTransform,
    ): Offset {
        val x = (worldPoint.x.toFloat() * camera.zoom) + camera.panX
        val y = (worldPoint.y.toFloat() * camera.zoom) + camera.panY
        return Offset(x, y)
    }

    /**
     * Calculate screen-space distance between two points.
     * Used for hit testing (consistent across zoom levels).
     *
     * @param a First point (screen space)
     * @param b Second point (screen space)
     * @return Distance in pixels
     */
    fun distanceScreen(
        a: Offset,
        b: Offset,
    ): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}
