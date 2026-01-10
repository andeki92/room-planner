package com.roomplanner.data.models

import androidx.compose.ui.unit.Density
import com.roomplanner.domain.geometry.Point2
import com.roomplanner.ui.utils.dpToPx
import kotlinx.serialization.Serializable

/**
 * Snap settings control smart snapping behavior in drawing mode.
 *
 * Design rationale:
 * - Immutable data class
 * - Serializable for user preference persistence
 * - Grid snap in Phase 1.1, vertex/perpendicular snap in Phase 1.3
 *
 * Units:
 * - gridSize: inches (imperial) or cm (metric)
 * - snapRadius: screen pixels (DPI-independent)
 */
@Serializable
data class SnapSettings(
    // Grid snapping (Phase 1.1)
    val gridSize: Double = 12.0, // inches
    val gridEnabled: Boolean = true,
    // Smart snapping (Phase 1.3)
    val vertexSnapEnabled: Boolean = true,
    val edgeSnapEnabled: Boolean = true,
    val perpendicularSnapEnabled: Boolean = true,
    // Snap radius in density-independent pixels (dp) - scales with screen density
    // iOS/Android guideline: 44-48dp minimum touch target
    val vertexSnapRadiusDp: Float = 44f, // 44dp = ~132px on 3x density (iPhone)
    val edgeSnapRadiusDp: Float = 36f, // 36dp = ~108px on 3x density
    val perpendicularAngleTolerance: Double = 5.0, // degrees (more forgiving)
) {
    // Computed properties for SmartSnapSystem (convert dp → px at runtime)
    fun vertexSnapRadius(density: Density): Float = vertexSnapRadiusDp.dpToPx(density)

    fun edgeSnapRadius(density: Density): Float = edgeSnapRadiusDp.dpToPx(density)

    companion object {
        /**
         * Default snap settings for imperial units (inches).
         */
        fun defaultImperial() =
            SnapSettings(
                gridSize = 12.0, // 1 foot
            )

        /**
         * Default snap settings for metric units (cm).
         */
        fun defaultMetric() =
            SnapSettings(
                gridSize = 30.0, // 30 cm
            )

        /**
         * Create snap settings based on measurement units.
         */
        fun forUnits(units: MeasurementUnits): SnapSettings =
            when (units) {
                MeasurementUnits.METRIC -> defaultMetric()
                MeasurementUnits.IMPERIAL -> defaultImperial()
            }
    }
}

/**
 * Result of snap detection.
 * Describes what the cursor snapped to (if anything).
 */
@Serializable
sealed interface SnapResult {
    /** No snap - use cursor position as-is */
    @Serializable
    data object None : SnapResult

    /** Snapped to grid intersection */
    @Serializable
    data class Grid(
        val position: Point2
    ) : SnapResult

    /** Snapped to existing vertex */
    @Serializable
    data class Vertex(
        val vertexId: String,
        val position: Point2,
    ) : SnapResult

    /** Snapped to point on edge */
    @Serializable
    data class Edge(
        val lineId: String,
        val position: Point2,
        val t: Double, // Parameter along edge (0.0 = start, 1.0 = end)
    ) : SnapResult

    /** Snapped perpendicular to edge */
    @Serializable
    data class Perpendicular(
        val lineId: String,
        val position: Point2,
        val angle: Double, // Angle of perpendicular in degrees
    ) : SnapResult
}
