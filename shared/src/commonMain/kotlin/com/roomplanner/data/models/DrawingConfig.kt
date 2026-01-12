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
     * Stroke width for snap guideline in density-independent pixels (dp).
     * Default: 1.5dp - thin dashed line for visual guidance
     */
    val guidelineStrokeWidthDp: Float = 1.5f,
    /**
     * Alpha transparency for guidelines.
     * Default: 0.6f - visible but not overwhelming
     */
    val guidelineAlpha: Float = 0.6f,
    /**
     * Dash length for guideline pattern in pixels.
     * Default: 8f - short dashes for clean look
     */
    val guidelineDashLength: Float = 8f,
    /**
     * Gap length for guideline pattern in pixels.
     * Default: 4f - short gaps between dashes
     */
    val guidelineGapLength: Float = 4f,
    /**
     * Color for alignment guidelines (horizontal/vertical alignment with other vertices).
     * Default: Green (0xFF4CAF50) - indicates alignment guidance
     */
    val alignmentGuidelineColor: Long = 0xFF4CAF50,
    /**
     * Color for snap hint guidelines (snap to vertex, edge, right angle, etc.).
     * Default: Orange (0xFFFF9800) - indicates snap guidance
     */
    val snapGuidelineColor: Long = 0xFFFF9800,
    /**
     * Touch target radius for selecting vertices in density-independent pixels (dp).
     * Default: 44dp - iOS/Android minimum touch target guideline
     */
    val selectionRadiusDp: Float = 44f,
    /**
     * Maximum duration for a tap gesture in milliseconds.
     * Default: 250ms - slightly longer than iOS standard (200ms) for tolerance
     */
    val tapMaxDurationMs: Long = 250L,
    /**
     * Time to trigger long press gesture in milliseconds.
     * Default: 300ms - standard long press delay
     */
    val longPressDelayMs: Long = 300L,
    /**
     * Minimum movement in pixels to start a drag gesture.
     * Default: 20px - lenient for mobile touch input (iOS guideline is 10pt)
     */
    val dragThresholdPx: Float = 20f,
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
    /**
     * Color for satisfied constraints (error < tolerance).
     * Default: Green (0xFF4CAF50) - indicates constraint is met
     * Phase 2: Constraint visual feedback
     */
    val constraintSatisfiedColor: Long = 0xFF4CAF50,
    /**
     * Color for solving constraints (iterations in progress).
     * Default: Yellow (0xFFFFC107) - indicates constraint being adjusted
     * Phase 2: Constraint visual feedback
     */
    val constraintSolvingColor: Long = 0xFFFFC107,
    /**
     * Color for violated constraints (error > tolerance).
     * Default: Red (0xFFF44336) - indicates constraint not met
     * Phase 2: Constraint visual feedback
     */
    val constraintViolatedColor: Long = 0xFFF44336,
    /**
     * Radius for constraint status indicator in density-independent pixels (dp).
     * Default: 10dp - visible without obscuring dimension text
     * Phase 2: Constraint visual feedback
     */
    val constraintIndicatorRadiusDp: Float = 10f,
    /**
     * Radius for anchor point indicator in density-independent pixels (dp).
     * Default: 6dp - small pin icon on anchored vertices
     * Phase 2: Anchor point visualization
     */
    val anchorIndicatorRadiusDp: Float = 6f,
    /**
     * Color for anchor point indicators.
     * Default: Dark gray (0xFF424242) - subtle but visible
     * Phase 2: Anchor point visualization
     */
    val anchorIndicatorColor: Long = 0xFF424242,
    /**
     * Default viewport width in centimeters (world units).
     * Default: 500cm (5 meters) - good starting view for room-scale work
     */
    val defaultViewportWidthCm: Double = 500.0,
    /**
     * Minimum viewport width in centimeters (max zoom in).
     * Default: 50cm (0.5 meters) - detailed view for precise work
     */
    val minViewportWidthCm: Double = 50.0,
    /**
     * Maximum viewport width in centimeters (max zoom out).
     * Default: 5000cm (50 meters) - wide overview for large floor plans
     */
    val maxViewportWidthCm: Double = 5000.0,
    /**
     * Project boundary size in centimeters (world coordinates).
     * Default: 5000cm (50 meters) - maximum extent of drawing area
     * Prevents panning and zooming beyond this boundary.
     */
    val projectBoundaryCm: Double = 5000.0,
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

    /**
     * Calculate zoom level for a given viewport width (in cm) and screen width (in pixels).
     * Zoom = screen pixels / world centimeters
     *
     * @param viewportWidthCm desired width of viewport in world coordinates (cm)
     * @param screenWidthPx screen width in pixels
     * @return zoom level to achieve the desired viewport width
     */
    fun calculateZoomForViewportWidth(
        viewportWidthCm: Double,
        screenWidthPx: Float,
    ): Float = (screenWidthPx / viewportWidthCm).toFloat()

    /**
     * Calculate default zoom level for initial camera position.
     */
    fun defaultZoom(screenWidthPx: Float): Float = calculateZoomForViewportWidth(defaultViewportWidthCm, screenWidthPx)

    /**
     * Calculate minimum zoom level (max zoom out).
     */
    fun minZoom(screenWidthPx: Float): Float = calculateZoomForViewportWidth(maxViewportWidthCm, screenWidthPx)

    /**
     * Calculate maximum zoom level (max zoom in).
     */
    fun maxZoom(screenWidthPx: Float): Float = calculateZoomForViewportWidth(minViewportWidthCm, screenWidthPx)

    fun constraintIndicatorRadiusPx(density: Density) = constraintIndicatorRadiusDp.dpToPx(density)

    fun anchorIndicatorRadiusPx(density: Density) = anchorIndicatorRadiusDp.dpToPx(density)

    // Color helpers (unchanged)
    fun vertexColorNormalCompose() = Color(vertexColorNormal)

    fun vertexColorActiveCompose() = Color(vertexColorActive)

    fun vertexColorFixedCompose() = Color(vertexColorFixed)

    fun vertexOutlineColorCompose() = Color(vertexOutlineColor)

    fun lineColorCompose() = Color(lineColor)

    fun gridColorCompose() = Color(gridColor).copy(alpha = gridAlpha)

    fun snapIndicatorColorCompose() = Color(snapIndicatorColor).copy(alpha = snapIndicatorAlpha)

    fun selectedVertexColorCompose() = Color(selectedVertexColor)

    fun constraintSatisfiedColorCompose() = Color(constraintSatisfiedColor)

    fun constraintSolvingColorCompose() = Color(constraintSolvingColor)

    fun constraintViolatedColorCompose() = Color(constraintViolatedColor)

    fun anchorIndicatorColorCompose() = Color(anchorIndicatorColor)

    // Guideline helpers
    fun guidelineStrokeWidthPx(density: Density) = guidelineStrokeWidthDp.dpToPx(density)

    fun guidelineDashPattern() = floatArrayOf(guidelineDashLength, guidelineGapLength)

    fun alignmentGuidelineColorCompose() = Color(alignmentGuidelineColor).copy(alpha = guidelineAlpha)

    fun snapGuidelineColorCompose() = Color(snapGuidelineColor).copy(alpha = guidelineAlpha)
}
