package com.roomplanner.data.models

import com.roomplanner.common.getSystemLanguage
import com.roomplanner.localization.AppLanguage
import kotlinx.serialization.Serializable

/**
 * User preferences and settings
 */
@Serializable
data class Settings(
    val measurementUnits: MeasurementUnits = MeasurementUnits.IMPERIAL,
    val language: AppLanguage = getSystemLanguage(),
) {
    companion object {
        fun default() = Settings()
    }
}

@Serializable
enum class MeasurementUnits {
    IMPERIAL, // feet, inches
    METRIC, // meters, centimeters
}
