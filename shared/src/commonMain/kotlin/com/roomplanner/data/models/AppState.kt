package com.roomplanner.data.models

import com.roomplanner.domain.drawing.Line
import com.roomplanner.domain.drawing.Vertex
import kotlinx.serialization.Serializable

/**
 * Global application state.
 * Immutable data class with copy semantics.
 *
 * Design rationale:
 * - Single source of truth for entire app
 * - Immutable state enables time-travel debugging and undo/redo
 * - Map-based storage for O(1) lookup by ID
 * - Structural sharing minimizes memory allocations
 */
@Serializable
data class AppState(
    val currentMode: AppMode = AppMode.ProjectBrowser,
    val settings: Settings = Settings.default(),
    val currentProjectId: String? = null,
    // Drawing state (Floor Plan mode)
    val vertices: Map<String, Vertex> = emptyMap(),
    val lines: List<Line> = emptyList(),
    val cameraTransform: CameraTransform = CameraTransform.default(),
    val snapSettings: SnapSettings = SnapSettings.defaultImperial(),
    val drawingConfig: DrawingConfig = DrawingConfig.default(),
    // Drawing tool state
    val activeVertexId: String? = null, // Last placed vertex (for continuous drawing)
) {
    companion object {
        fun initial() = AppState()
    }

    /**
     * Helper: Add a vertex to the state immutably.
     */
    fun withVertex(vertex: Vertex): AppState =
        copy(
            vertices = vertices + (vertex.id to vertex),
            activeVertexId = vertex.id,
        )

    /**
     * Helper: Add a line to the state immutably.
     */
    fun withLine(line: Line): AppState = copy(lines = lines + line)

    /**
     * Helper: Get the currently active vertex (for continuous drawing).
     */
    fun getActiveVertex(): Vertex? = activeVertexId?.let { vertices[it] }
}
