package com.roomplanner.data.models

import com.roomplanner.domain.drawing.Line
import com.roomplanner.domain.drawing.Vertex
import kotlinx.serialization.Serializable

/**
 * Drawing state for a single project.
 * This contains all the CAD data (vertices, lines, camera position) for one project.
 *
 * Design rationale:
 * - Per-project state isolation: each project has its own drawing data
 * - Serializable for persistence to disk (saved as drawing.json per project)
 * - Immutable data class with structural sharing
 * - Separate from Project metadata (name, dates) for efficiency
 *
 * File storage:
 * - projects/{projectId}/drawing.json
 */
@Serializable
data class ProjectDrawingState(
    // Geometry
    val vertices: Map<String, Vertex> = emptyMap(),
    val lines: List<Line> = emptyList(),
    // Camera state
    val cameraTransform: CameraTransform = CameraTransform.default(),
    // Drawing tool state
    val activeVertexId: String? = null, // Last placed vertex (for continuous drawing)
    // Settings (project-specific overrides)
    val snapSettings: SnapSettings = SnapSettings.defaultImperial(),
    val drawingConfig: DrawingConfig = DrawingConfig.default(),
) {
    companion object {
        /**
         * Empty drawing state for new projects
         */
        fun empty() = ProjectDrawingState()
    }

    /**
     * Helper: Add a vertex to the state immutably.
     */
    fun withVertex(vertex: Vertex): ProjectDrawingState =
        copy(
            vertices = vertices + (vertex.id to vertex),
            activeVertexId = vertex.id,
        )

    /**
     * Helper: Add a line to the state immutably.
     */
    fun withLine(line: Line): ProjectDrawingState = copy(lines = lines + line)

    /**
     * Helper: Get the currently active vertex (for continuous drawing).
     */
    fun getActiveVertex(): Vertex? = activeVertexId?.let { vertices[it] }

    /**
     * Helper: Update camera transform
     */
    fun withCamera(camera: CameraTransform): ProjectDrawingState = copy(cameraTransform = camera)
}
