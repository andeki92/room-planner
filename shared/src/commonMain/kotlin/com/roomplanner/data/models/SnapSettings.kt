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
    // Perpendicular snap disabled: use axisAlignmentSnapEnabled for horizontal/vertical instead
    val perpendicularSnapEnabled: Boolean = false,
    // Axis alignment snap: snap to horizontal/vertical from active vertex (world axes)
    val axisAlignmentSnapEnabled: Boolean = true,
    val axisAlignmentToleranceDegrees: Double = 8.0, // degrees from perfect horizontal/vertical
    // Right angle snap (to ACTIVE VERTEX, 90° guidance) - Phase 1+
    // Disabled by default: VertexAlignment provides world-axis horizontal/vertical alignment
    val rightAngleSnapEnabled: Boolean = false,
    val rightAngleSnapTolerance: Double = 2.5, // degrees
    // Snap radius in density-independent pixels (dp) - scales with screen density
    // iOS/Android guideline: 44-48dp minimum touch target
    val vertexSnapRadiusDp: Float = 44f, // 44dp = ~132px on 3x density (iPhone)
    val edgeSnapRadiusDp: Float = 36f, // 36dp = ~108px on 3x density
    val alignmentSnapRadiusDp: Float = 20f, // 20dp for horizontal/vertical alignment with other vertices
    val perpendicularAngleTolerance: Double = 5.0, // degrees (more forgiving)
) {
    // Computed properties for SmartSnapSystem (convert dp → px at runtime)
    fun vertexSnapRadius(density: Density): Float = vertexSnapRadiusDp.dpToPx(density)

    fun edgeSnapRadius(density: Density): Float = edgeSnapRadiusDp.dpToPx(density)

    fun alignmentSnapRadius(density: Density): Float = alignmentSnapRadiusDp.dpToPx(density)

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

    /** Snapped to right angle (90°) relative to active vertex */
    @Serializable
    data class RightAngle(
        val position: Point2,
        val actualAngleDegrees: Double, // Angle before snapping
        val snappedAngleDegrees: Double, // 0°, 90°, 180°, or 270°
    ) : SnapResult

    /** Snapped to horizontal/vertical alignment with another vertex */
    @Serializable
    data class VertexAlignment(
        val alignedVertexId: String, // Vertex we're aligning with
        val position: Point2,
        val isHorizontal: Boolean, // true = horizontal, false = vertical
    ) : SnapResult

    /** Snapped to world-axis horizontal/vertical from active vertex */
    @Serializable
    data class AxisAlignment(
        val position: Point2,
        val isHorizontal: Boolean, // true = horizontal (same Y as active), false = vertical (same X as active)
    ) : SnapResult
}

/**
 * Combined snap result when both horizontal and vertical axis alignment are active.
 * The position is at the intersection of both guidelines (cross point).
 *
 * This follows standard CAD behavior where orthogonal constraints combine:
 * - Horizontal guideline from active vertex (same Y)
 * - Vertical guideline from active vertex (same X)
 * - Snap position at intersection
 *
 * @see https://qcad.org/doc/qcad/2.2/reference/en/chapter14.html
 */
@Serializable
data class SnapResultWithGuidelines(
    val snapPosition: Point2, // The actual position to snap to
    val guidelines: List<SnapResult>, // All guidelines to display (can include the primary snap)
) {
    companion object {
        fun fromSingle(result: SnapResult): SnapResultWithGuidelines {
            val position =
                when (result) {
                    is SnapResult.RightAngle -> result.position
                    is SnapResult.Vertex -> result.position
                    is SnapResult.Edge -> result.position
                    is SnapResult.Perpendicular -> result.position
                    is SnapResult.VertexAlignment -> result.position
                    is SnapResult.AxisAlignment -> result.position
                    is SnapResult.Grid -> result.position
                    SnapResult.None -> Point2(0.0, 0.0)
                }
            val guidelines =
                if (result is SnapResult.None || result is SnapResult.Grid) {
                    emptyList()
                } else {
                    listOf(result)
                }
            return SnapResultWithGuidelines(position, guidelines)
        }

        val None = SnapResultWithGuidelines(Point2(0.0, 0.0), emptyList())
    }
}
