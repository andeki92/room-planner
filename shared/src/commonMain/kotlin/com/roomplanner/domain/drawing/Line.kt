package com.roomplanner.domain.drawing

import com.roomplanner.common.generateUUID
import com.roomplanner.domain.geometry.LineSegment
import kotlinx.serialization.Serializable

/**
 * Line represents a connection between two vertices.
 * This is the simplified version for Phase 1.1 drawing.
 *
 * Design rationale:
 * - References vertices by ID (stable across edits)
 * - Cached LineSegment geometry for rendering
 * - Immutable data class
 *
 * Note: Phase 2 will replace this with Edge + HalfEdge mesh (BREP topology).
 * This simplified Line is sufficient for basic drawing and testing.
 */
@Serializable
data class Line(
    val id: String = generateUUID(),
    val startVertexId: String,
    val endVertexId: String,
    val geometry: LineSegment, // Cached for rendering
) {
    companion object {
        /**
         * Create a line between two vertices.
         */
        fun between(
            start: Vertex,
            end: Vertex,
        ): Line =
            Line(
                startVertexId = start.id,
                endVertexId = end.id,
                geometry = LineSegment(start.position, end.position),
            )
    }
}
