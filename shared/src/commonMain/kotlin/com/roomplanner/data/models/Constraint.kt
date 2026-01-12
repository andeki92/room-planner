package com.roomplanner.data.models

import com.roomplanner.common.generateUUID
import kotlinx.serialization.Serializable

/**
 * Geometric constraint definitions.
 * Phase 1.5: Distance constraints (lock line lengths)
 * Phase 1.6: Angle constraints with priority-based solving
 *
 * Design rationale:
 * - Sealed interface for type-safe constraint types
 * - Immutable data classes for state management
 * - Serializable for project persistence
 * - enabled flag allows temporary disabling without removal
 * - userSet distinguishes user-defined from auto-generated constraints
 * - priority determines solving order (higher priority = more rigid)
 */
@Serializable
sealed interface Constraint {
    val id: String
    val enabled: Boolean

    /**
     * Priority for constraint solving (higher = more rigid, solved first).
     *
     * Priority levels:
     * - 100: Perpendicular (90° angles) - Never broken, highest priority
     * - 50: Distance constraints - Medium priority
     * - 10: Other angle constraints - Flexible, lowest priority
     *
     * Phase 1.6: Priority system ensures 90° angles are preserved.
     */
    val priority: Int
        get() =
            when (this) {
                is Perpendicular -> 100 // Highest - never break right angles
                is Distance -> 50 // Medium - important but can flex
                is Angle -> 10 // Lowest - flexible
                is Parallel -> 10 // Lowest - flexible
            }

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
     * Phase 1.6: Implemented with highest priority (auto-created during drawing).
     *
     * @param userSet True if explicitly set by user, false if auto-detected
     */
    @Serializable
    data class Perpendicular(
        override val id: String = generateUUID(),
        val lineId1: String,
        val lineId2: String,
        override val enabled: Boolean = true,
        val userSet: Boolean = false,
    ) : Constraint
}
