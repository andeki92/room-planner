package com.roomplanner.data.models

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

/**
 * Centralized configuration for drawing visual constants.
 *
 * Design rationale:
 * - Single source of truth for all drawing-related visual parameters
 * - Easy to experiment with different values
 * - Serializable for potential user preferences
 * - Type-safe access (no magic numbers scattered throughout code)
 * - Can provide accessibility presets (large touch targets, high contrast)
 *
 * Usage:
 * ```kotlin
 * // In AppState
 * val drawingConfig: DrawingConfig = DrawingConfig.default()
 *
 * // In drawing code
 * drawCircle(radius = config.vertexRadius)
 * ```
 */
@Serializable
data class DrawingConfig(
    /**
     * Radius of vertex circles in screen pixels.
     * Default: 6f (visible but not obtrusive)
     */
    val vertexRadius: Float = 6f,
    /**
     * Stroke width for vertex outline in screen pixels.
     * Default: 2f (clear contrast)
     */
    val vertexStrokeWidth: Float = 2f,
    /**
     * Color for normal vertices (not active, not constrained).
     * Default: Green (0xFF4CAF50) - indicates clickable/editable
     */
    val vertexColorNormal: Long = 0xFF4CAF50,
    /**
     * Color for active vertex (last placed, currently selected).
     * Default: Blue (0xFF2196F3) - indicates current focus
     */
    val vertexColorActive: Long = 0xFF2196F3,
    /**
     * Color for fixed vertices (locked by constraints).
     * Default: Red (0xFFF44336) - indicates non-editable
     */
    val vertexColorFixed: Long = 0xFFF44336,
    /**
     * Color for vertex outline.
     * Default: White (0xFFFFFFFF) - provides contrast on any background
     */
    val vertexOutlineColor: Long = 0xFFFFFFFF,
    /**
     * Stroke width for lines (walls) in screen pixels.
     * Default: 3f (clear but not overwhelming)
     */
    val lineStrokeWidth: Float = 3f,
    /**
     * Color for lines.
     * Default: Black (0xFF000000) - high contrast, professional
     */
    val lineColor: Long = 0xFF000000,
    /**
     * Stroke width for grid lines in screen pixels.
     * Default: 1f (subtle, background element)
     */
    val gridLineWidth: Float = 1f,
    /**
     * Color for grid lines.
     * Default: LightGray (0xFFD3D3D3)
     */
    val gridColor: Long = 0xFFD3D3D3,
    /**
     * Alpha transparency for grid lines (0.0 = invisible, 1.0 = opaque).
     * Default: 0.3f (visible but subtle)
     */
    val gridAlpha: Float = 0.3f,
    /**
     * Radius for snap indicator circles in screen pixels.
     * Default: 10f (larger than vertex for visibility)
     */
    val snapIndicatorRadius: Float = 10f,
    /**
     * Color for snap indicators.
     * Default: Orange (0xFFFF9800) - attention-grabbing
     */
    val snapIndicatorColor: Long = 0xFFFF9800,
    /**
     * Alpha for snap indicators.
     * Default: 0.8f (semi-transparent to not obscure geometry)
     */
    val snapIndicatorAlpha: Float = 0.8f,
) {
    companion object {
        /**
         * Default configuration with standard values.
         */
        fun default() = DrawingConfig()

        /**
         * Large touch targets preset for accessibility.
         * Useful for:
         * - Users with motor control difficulties
         * - Large displays (tablets, external monitors)
         * - User preference for larger UI elements
         */
        fun largeTargets() =
            DrawingConfig(
                vertexRadius = 10f,
                vertexStrokeWidth = 3f,
                lineStrokeWidth = 5f,
                snapIndicatorRadius = 15f,
            )

        /**
         * High contrast preset for visibility.
         * Useful for:
         * - Low vision users
         * - Bright outdoor environments
         * - Projector displays
         */
        fun highContrast() =
            DrawingConfig(
                vertexColorNormal = 0xFF00FF00, // Bright green
                vertexColorActive = 0xFF0000FF, // Bright blue
                vertexColorFixed = 0xFFFF0000, // Bright red
                lineColor = 0xFF000000, // Pure black
                lineStrokeWidth = 4f,
                gridAlpha = 0.5f, // More visible grid
            )
    }

    /**
     * Convert stored color Long to Compose Color.
     * Colors are stored as Long (0xAARRGGBB) for serializability.
     */
    fun vertexColorNormalCompose() = Color(vertexColorNormal)

    fun vertexColorActiveCompose() = Color(vertexColorActive)

    fun vertexColorFixedCompose() = Color(vertexColorFixed)

    fun vertexOutlineColorCompose() = Color(vertexOutlineColor)

    fun lineColorCompose() = Color(lineColor)

    fun gridColorCompose() = Color(gridColor).copy(alpha = gridAlpha)

    fun snapIndicatorColorCompose() = Color(snapIndicatorColor).copy(alpha = snapIndicatorAlpha)
}
