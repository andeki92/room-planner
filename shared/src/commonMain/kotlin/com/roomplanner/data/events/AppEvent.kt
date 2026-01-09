package com.roomplanner.data.events

import com.roomplanner.data.models.AppMode
import com.roomplanner.data.models.MeasurementUnits
import com.roomplanner.data.models.Project
import com.roomplanner.domain.geometry.Point2

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

/**
 * Geometry events (drawing mode)
 * Following event-driven architecture: tools emit events, systems react.
 */
sealed interface GeometryEvent : AppEvent {
    /**
     * User placed a point by tapping on the canvas.
     * @param position world coordinates of the point (after snap)
     * @param snappedTo ID of vertex this point snapped to (null if new vertex)
     */
    data class PointPlaced(
        val position: Point2,
        val snappedTo: String? = null,
    ) : GeometryEvent

    /**
     * User transformed the camera view (pan/zoom).
     * Phase 1.2: Enables viewport navigation
     * @param panDelta change in pan offset (screen pixels)
     * @param zoomDelta multiplicative zoom change (1.0 = no change)
     * @param zoomCenter screen coordinates of zoom center (for zoom-to-point)
     */
    data class CameraTransformed(
        val panDelta: androidx.compose.ui.geometry.Offset = androidx.compose.ui.geometry.Offset.Zero,
        val zoomDelta: Float = 1f,
        val zoomCenter: androidx.compose.ui.geometry.Offset? = null,
    ) : GeometryEvent
}
