package com.roomplanner.data.models

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import com.roomplanner.ui.utils.dpToPx
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
     * Radius of vertex circles in density-independent pixels (dp).
     * Default: 8dp - visible but not overwhelming on all screen densities
     */
    val vertexRadiusDp: Float = 8f,
    /**
     * Stroke width for vertex outline in density-independent pixels (dp).
     * Default: 1.5dp - clear contrast on all screens
     */
    val vertexStrokeWidthDp: Float = 1.5f,
    /**
     * Stroke width for lines (walls) in density-independent pixels (dp).
     * Default: 5dp - clearly visible for CAD work
     */
    val lineStrokeWidthDp: Float = 5f,
    /**
     * Stroke width for grid lines in density-independent pixels (dp).
     * Default: 1dp - subtle background element
     */
    val gridLineWidthDp: Float = 1f,
    /**
     * Alpha transparency for grid lines (0.0 = invisible, 1.0 = opaque).
     * Default: 0.3f (visible but subtle)
     */
    val gridAlpha: Float = 0.3f,
    /**
     * Radius for snap indicator circles in density-independent pixels (dp).
     * Default: 20dp - clear feedback on all screens
     */
    val snapIndicatorRadiusDp: Float = 20f,
    /**
     * Alpha for snap indicators.
     * Default: 0.8f (semi-transparent to not obscure geometry)
     */
    val snapIndicatorAlpha: Float = 0.8f,
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
     * Color for lines.
     * Default: Black (0xFF000000) - high contrast, professional
     */
    val lineColor: Long = 0xFF000000,
    /**
     * Color for grid lines.
     * Default: LightGray (0xFFD3D3D3)
     */
    val gridColor: Long = 0xFFD3D3D3,
    /**
     * Color for snap indicators.
     * Default: Orange (0xFFFF9800) - attention-grabbing
     */
    val snapIndicatorColor: Long = 0xFFFF9800,
    /**
     * Touch target radius for selecting vertices in density-independent pixels (dp).
     * Default: 44dp - iOS/Android minimum touch target guideline
     */
    val selectionRadiusDp: Float = 44f,
    /**
     * Visual glow ring radius for selected vertices in density-independent pixels (dp).
     * Default: 12dp - visible feedback without obscuring nearby geometry
     */
    val selectionIndicatorRadiusDp: Float = 12f,
    /**
     * Color for selected vertices.
     * Default: Yellow (0xFFFFEB3B) - distinct from normal/active/fixed
     */
    val selectedVertexColor: Long = 0xFFFFEB3B,
    /**
     * Alpha transparency for selection glow ring.
     * Default: 0.3f - subtle highlight without obscuring geometry
     */
    val selectionGlowAlpha: Float = 0.3f,
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
                vertexRadiusDp = 16f,
                vertexStrokeWidthDp = 3f,
                lineStrokeWidthDp = 7f,
                snapIndicatorRadiusDp = 24f,
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
                lineStrokeWidthDp = 6f,
                gridAlpha = 0.5f, // More visible grid
            )
    }

    // Pixel conversion helpers (using density)
    fun vertexRadiusPx(density: Density) = vertexRadiusDp.dpToPx(density)

    fun vertexStrokeWidthPx(density: Density) = vertexStrokeWidthDp.dpToPx(density)

    fun lineStrokeWidthPx(density: Density) = lineStrokeWidthDp.dpToPx(density)

    fun gridLineWidthPx(density: Density) = gridLineWidthDp.dpToPx(density)

    fun snapIndicatorRadiusPx(density: Density) = snapIndicatorRadiusDp.dpToPx(density)

    fun selectionRadiusPx(density: Density) = selectionRadiusDp.dpToPx(density)

    fun selectionIndicatorRadiusPx(density: Density) = selectionIndicatorRadiusDp.dpToPx(density)

    // Color helpers (unchanged)
    fun vertexColorNormalCompose() = Color(vertexColorNormal)

    fun vertexColorActiveCompose() = Color(vertexColorActive)

    fun vertexColorFixedCompose() = Color(vertexColorFixed)

    fun vertexOutlineColorCompose() = Color(vertexOutlineColor)

    fun lineColorCompose() = Color(lineColor)

    fun gridColorCompose() = Color(gridColor).copy(alpha = gridAlpha)

    fun snapIndicatorColorCompose() = Color(snapIndicatorColor).copy(alpha = snapIndicatorAlpha)

    fun selectedVertexColorCompose() = Color(selectedVertexColor)
}
