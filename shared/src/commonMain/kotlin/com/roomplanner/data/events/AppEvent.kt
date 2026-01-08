package com.roomplanner.data.events

import com.roomplanner.data.models.AppMode
import com.roomplanner.data.models.MeasurementUnits
import com.roomplanner.data.models.Project

/**
 * Base sealed interface for all app events
 */
sealed interface AppEvent

/**
 * Navigation events (mode changes)
 */
sealed interface NavigationEvent : AppEvent {
    data class ModeChanged(
        val mode: AppMode,
    ) : NavigationEvent
}

/**
 * Project lifecycle events
 */
sealed interface ProjectEvent : AppEvent {
    data class Created(
        val project: Project,
    ) : ProjectEvent

    data class Deleted(
        val projectId: String,
    ) : ProjectEvent
}

/**
 * Settings events
 */
sealed interface SettingsEvent : AppEvent {
    data class UnitsChanged(
        val units: MeasurementUnits,
    ) : SettingsEvent
}
