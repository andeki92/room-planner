package com.roomplanner.data.models

import com.roomplanner.common.generateUUID
import kotlinx.serialization.Serializable

/**
 * Geometric constraint definitions.
 * Phase 1.5: Distance constraints (lock line lengths)
 * Future: Angle, parallel, perpendicular, etc.
 *
 * Design rationale:
 * - Sealed interface for type-safe constraint types
 * - Immutable data classes for state management
 * - Serializable for project persistence
 * - enabled flag allows temporary disabling without removal
 * - userSet distinguishes user-defined from auto-generated constraints
 */
@Serializable
sealed interface Constraint {
    val id: String
    val enabled: Boolean

    /**
     * Distance constraint: Lock line to specific length.
     * Most important constraint for CAD applications.
     *
     * Phase 1.5: Only constraint type implemented.
     *
     * @param id Unique constraint identifier
     * @param lineId ID of line to constrain
     * @param distance Target length in world units (cm or inches)
     * @param enabled Whether constraint is active
     * @param userSet True if user explicitly set this constraint (vs auto-generated)
     */
    @Serializable
    data class Distance(
        override val id: String = generateUUID(),
        val lineId: String,
        val distance: Double,
        override val enabled: Boolean = true,
        val userSet: Boolean = true,
    ) : Constraint

    /**
     * Angle constraint: Lock angle between two lines.
     * Example: 90° for perpendicular walls.
     *
     * Phase 2: Not implemented yet.
     *
     * @param angle Angle in degrees (0-360)
     */
    @Serializable
    data class Angle(
        override val id: String = generateUUID(),
        val lineId1: String,
        val lineId2: String,
        val angle: Double,
        override val enabled: Boolean = true,
        val userSet: Boolean = true,
    ) : Constraint

    /**
     * Parallel constraint: Keep two lines parallel.
     * Phase 2: Not implemented yet.
     */
    @Serializable
    data class Parallel(
        override val id: String = generateUUID(),
        val lineId1: String,
        val lineId2: String,
        override val enabled: Boolean = true,
    ) : Constraint

    /**
     * Perpendicular constraint: Keep two lines at 90°.
     * Phase 2: Not implemented yet.
     */
    @Serializable
    data class Perpendicular(
        override val id: String = generateUUID(),
        val lineId1: String,
        val lineId2: String,
        override val enabled: Boolean = true,
    ) : Constraint
}
