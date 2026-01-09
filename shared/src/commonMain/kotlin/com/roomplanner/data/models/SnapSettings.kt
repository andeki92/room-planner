package com.roomplanner.data.models

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
    val gridEnabled: Boolean = true,
    val gridSize: Double = 12.0, // 12 inches (1 foot) default for imperial
    val vertexSnapEnabled: Boolean = true, // Phase 1.3
    val snapRadius: Double = 20.0, // Screen pixels
) {
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
