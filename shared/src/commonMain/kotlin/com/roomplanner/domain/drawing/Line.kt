package com.roomplanner.domain.drawing

import com.roomplanner.common.generateUUID
import com.roomplanner.domain.geometry.LineSegment
import com.roomplanner.domain.geometry.Point2
import kotlinx.serialization.Serializable

/**
 * Line represents a connection between two vertices.
 * This is the simplified version for Phase 1.1 drawing.
 *
 * Design rationale:
 * - References vertices by ID (stable across edits)
 * - NO cached geometry - computed on demand from current vertex positions
 * - Immutable data class
 *
 * CRITICAL: Geometry is ALWAYS computed fresh from vertices map.
 * This eliminates stale cache bugs where line geometry doesn't update when vertices move.
 *
 * Note: Phase 2 will replace this with Edge + HalfEdge mesh (BREP topology).
 * This simplified Line is sufficient for basic drawing and testing.
 */
@Serializable
data class Line(
    val id: String = generateUUID(),
    val startVertexId: String,
    val endVertexId: String,
    // ❌ REMOVED: val geometry: LineSegment  (was cached, became stale!)
) {
    /**
     * Compute geometry from current vertex positions.
     * ALWAYS fresh, NEVER stale.
     *
     * @param vertices Map of all vertices in the drawing state
     * @return LineSegment connecting the current positions of start and end vertices
     * @throws IllegalArgumentException if referenced vertices are missing
     */
    fun getGeometry(vertices: Map<String, Vertex>): LineSegment {
        val start = vertices[startVertexId] ?: error("Line $id references missing start vertex $startVertexId")
        val end = vertices[endVertexId] ?: error("Line $id references missing end vertex $endVertexId")

        return LineSegment(start.position, end.position)
    }

    /**
     * Get line midpoint (computed from current vertex positions).
     */
    fun getMidpoint(vertices: Map<String, Vertex>): Point2 = getGeometry(vertices).midpoint

    /**
     * Get line length (computed from current vertex positions).
     */
    fun getLength(vertices: Map<String, Vertex>): Double = getGeometry(vertices).length

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
                // ✅ No geometry parameter - will be computed on demand
            )
    }
}
