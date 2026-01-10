package com.roomplanner.data.events

import com.roomplanner.data.models.AppMode
import com.roomplanner.data.models.MeasurementUnits
import com.roomplanner.data.models.Project
import com.roomplanner.data.models.ToolMode
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

    /**
     * User selected a vertex by tapping on it.
     * @param vertexId ID of the selected vertex
     */
    data class VertexSelected(
        val vertexId: String,
    ) : GeometryEvent

    /**
     * User cleared selection by tapping empty space.
     */
    data object SelectionCleared : GeometryEvent

    /**
     * User started dragging a selected vertex.
     * @param vertexId ID of vertex being dragged
     * @param startPosition original position before drag
     */
    data class VertexDragStarted(
        val vertexId: String,
        val startPosition: Point2,
    ) : GeometryEvent

    /**
     * User moved vertex during drag operation.
     * @param vertexId ID of vertex being moved
     * @param newPosition updated world position
     */
    data class VertexDragged(
        val vertexId: String,
        val newPosition: Point2,
    ) : GeometryEvent

    /**
     * User finished dragging vertex.
     * @param vertexId ID of moved vertex
     * @param finalPosition final world position
     */
    data class VertexDragEnded(
        val vertexId: String,
        val finalPosition: Point2,
    ) : GeometryEvent

    /**
     * User requested deletion of a vertex (via context menu).
     * @param vertexId ID of vertex to delete
     */
    data class VertexDeleted(
        val vertexId: String,
    ) : GeometryEvent

    /**
     * User changed drawing tool mode (Draw vs Select).
     * Phase 1.4b: Explicit tool mode system
     * @param mode New tool mode
     */
    data class ToolModeChanged(
        val mode: ToolMode,
    ) : GeometryEvent
}
