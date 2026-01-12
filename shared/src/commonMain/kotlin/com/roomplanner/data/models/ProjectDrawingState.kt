package com.roomplanner.data.models

import com.roomplanner.domain.drawing.Line
import com.roomplanner.domain.drawing.Vertex
import kotlin.math.atan2
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
    // Selection state (Phase 1.4)
    val selectedVertexId: String? = null, // Currently selected vertex
    // Settings (project-specific overrides)
    val snapSettings: SnapSettings = SnapSettings.defaultImperial(),
    val drawingConfig: DrawingConfig = DrawingConfig.default(),
    // Phase 1.5: Constraints
    val constraints: Map<String, Constraint> = emptyMap(),
    val showDimensions: Boolean = true, // Toggle dimension labels
    val dimensionPrecision: Int = 1, // Decimal places (e.g., 240.5 cm)
    // Phase 1.5: Line selection (for radial menu)
    val selectedLineId: String? = null,
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

    /**
     * Helper: Check if vertex is selected (Phase 1.4)
     */
    fun isVertexSelected(vertexId: String) = vertexId == selectedVertexId

    /**
     * Helper: Clear selection (Phase 1.4)
     */
    fun clearSelection() = copy(activeVertexId = null, selectedVertexId = null)

    /**
     * Helper: Select vertex (Phase 1.4)
     */
    fun selectVertex(vertexId: String) = copy(selectedVertexId = vertexId)

    /**
     * Helper: Add constraint immutably (Phase 1.5)
     */
    fun withConstraint(constraint: Constraint) =
        copy(
            constraints = constraints + (constraint.id to constraint),
        )

    /**
     * Helper: Remove constraint (Phase 1.5)
     */
    fun withoutConstraint(constraintId: String) =
        copy(
            constraints = constraints - constraintId,
        )

    /**
     * Helper: Get constraints affecting a line (Phase 1.5)
     */
    fun getConstraintsForLine(lineId: String): List<Constraint> =
        constraints.values.filter { constraint ->
            when (constraint) {
                is Constraint.Distance -> constraint.lineId == lineId
                is Constraint.Angle -> constraint.lineId1 == lineId || constraint.lineId2 == lineId
                is Constraint.Parallel -> constraint.lineId1 == lineId || constraint.lineId2 == lineId
                is Constraint.Perpendicular -> constraint.lineId1 == lineId || constraint.lineId2 == lineId
            }
        }

    /**
     * Helper: Check if line is selected (Phase 1.5)
     */
    fun isLineSelected(lineId: String) = lineId == selectedLineId

    /**
     * Helper: Select line (Phase 1.5)
     */
    fun selectLine(lineId: String) = copy(selectedLineId = lineId)

    /**
     * Helper: Clear line selection (Phase 1.5)
     */
    fun clearLineSelection() = copy(selectedLineId = null)

    /**
     * Get all lines connected to a vertex (Phase 2: Angle preservation)
     */
    fun getLinesConnectedToVertex(vertexId: String): List<Line> =
        lines.filter { line ->
            line.startVertexId == vertexId || line.endVertexId == vertexId
        }

    /**
     * Get angle of line at vertex (direction vector) in radians.
     * Returns null if line not found.
     *
     * Phase 2: Used for angle preservation during constraint solving.
     * The angle represents the direction FROM the specified vertex.
     *
     * @param lineId ID of the line
     * @param vertexId ID of the vertex (must be start or end of line)
     * @return Angle in radians, or null if line/vertex not found
     */
    fun getLineAngleAtVertex(
        lineId: String,
        vertexId: String
    ): Double? {
        val line = lines.find { it.id == lineId } ?: return null
        val geometry = line.getGeometry(vertices)

        return when (vertexId) {
            line.startVertexId ->
                atan2(
                    geometry.end.y - geometry.start.y,
                    geometry.end.x - geometry.start.x,
                )

            line.endVertexId ->
                atan2(
                    geometry.start.y - geometry.end.y,
                    geometry.start.x - geometry.end.x,
                )

            else -> null
        }
    }
}
