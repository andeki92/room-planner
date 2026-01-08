package com.roomplanner.data.models

import kotlinx.serialization.Serializable

/**
 * User preferences and settings
 */
@Serializable
data class Settings(
    val measurementUnits: MeasurementUnits = MeasurementUnits.IMPERIAL
) {
    companion object {
        fun default() = Settings()
    }
}

@Serializable
enum class MeasurementUnits {
    IMPERIAL,  // feet, inches
    METRIC     // meters, centimeters
}
