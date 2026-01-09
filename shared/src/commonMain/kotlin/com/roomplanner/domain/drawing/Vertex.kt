package com.roomplanner.domain.drawing

import com.roomplanner.common.generateUUID
import com.roomplanner.domain.geometry.Point2
import kotlinx.serialization.Serializable

/**
 * Vertex represents a point in the floor plan.
 * Used as endpoints for walls, corners, and snap targets.
 *
 * Design rationale:
 * - Immutable data class for state management
 * - UUID for stable identity across edits
 * - Fixed flag for constraint-locked vertices
 * - Serializable for project persistence
 *
 * Note: This is the simplified version for Phase 1.1.
 * Phase 2 will add outgoingHalfedge for BREP topology.
 */
@Serializable
data class Vertex(
    val id: String = generateUUID(),
    val position: Point2,
    val fixed: Boolean = false, // Locked by constraints (future)
) {
    companion object {
        /**
         * Create a vertex at a specific position.
         */
        fun at(
            x: Double,
            y: Double,
            fixed: Boolean = false,
        ): Vertex = Vertex(position = Point2(x, y), fixed = fixed)
    }
}
