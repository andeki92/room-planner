package com.roomplanner.data.models

import kotlinx.serialization.Serializable

/**
 * Camera transform for pan and zoom in the drawing canvas.
 * Defines the viewport transformation from world coordinates to screen coordinates.
 *
 * Design rationale:
 * - Immutable data class for state management
 * - Serializable for persisting viewport state with project
 * - Simple pan/zoom model (Phase 1.2 will add gestures)
 *
 * Coordinate systems:
 * - World coordinates: CAD coordinates in inches/cm (Double precision)
 * - Screen coordinates: Pixel coordinates on device (Float precision)
 *
 * Transform order: scale(zoom) -> translate(pan)
 */
@Serializable
data class CameraTransform(
    val panX: Float = 0f, // Pan offset in screen pixels
    val panY: Float = 0f,
    val zoom: Float = 1f, // Zoom level (1.0 = 1:1, 2.0 = 2x zoom in)
) {
    companion object {
        /**
         * Default camera at origin with no zoom.
         */
        fun default() = CameraTransform()

        /**
         * Minimum zoom (zoomed out).
         */
        const val MIN_ZOOM = 0.1f

        /**
         * Maximum zoom (zoomed in).
         */
        const val MAX_ZOOM = 10f
    }

    /**
     * Clamp zoom to valid range.
     */
    fun withZoom(newZoom: Float): CameraTransform = copy(zoom = newZoom.coerceIn(MIN_ZOOM, MAX_ZOOM))
}
