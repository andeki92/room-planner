package com.roomplanner.domain.constraints

import co.touchlab.kermit.Logger
import com.roomplanner.data.models.Constraint
import com.roomplanner.data.models.ProjectDrawingState

/**
 * Analyzes constraint graphs for degrees of freedom.
 * Based on graph-constructive approach from CAD research.
 *
 * DOF Formula for 2D:
 * DOF = (num_free_vertices × 2) - Σ(constraints × DOF_removed)
 *
 * - DOF > 0: Underconstrained (infinite solutions)
 * - DOF = 0: Well-constrained (unique solution)
 * - DOF < 0: Overconstrained (conflicting constraints)
 *
 * References:
 * - Graph-Constructive Approach: https://www.academia.edu/4659739/
 * - DOF-Based Analysis: https://link.springer.com/article/10.1007/s00170-004-2391-1
 */
object ConstraintAnalyzer {
    /**
     * Calculate degrees of freedom for current drawing state.
     */
    fun analyzeDOF(drawingState: ProjectDrawingState): ConstraintAnalysis {
        val freeVertexCount = drawingState.vertices.values.count { !it.fixed }
        val totalDOF = freeVertexCount * 2 // Each vertex has 2 DOF (x, y)

        val constrainedDOF =
            drawingState.constraints.values.sumOf { constraint ->
                when (constraint) {
                    is Constraint.Distance -> if (constraint.enabled) 1 else 0
                    is Constraint.Angle -> if (constraint.enabled) 1 else 0
                    is Constraint.Parallel -> if (constraint.enabled) 1 else 0
                    is Constraint.Perpendicular -> if (constraint.enabled) 1 else 0
                }
            }

        val dof = totalDOF - constrainedDOF

        Logger.d {
            "DOF Analysis: vertices=$freeVertexCount, constraints=${drawingState.constraints.size}, DOF=$dof"
        }

        return ConstraintAnalysis(
            vertexCount = freeVertexCount,
            constraintCount = drawingState.constraints.size,
            degreesOfFreedom = dof,
            isOverConstrained = dof < 0,
            isWellConstrained = dof == 0,
            isUnderConstrained = dof > 0,
        )
    }

    /**
     * Check if adding a new constraint would overconstrain the system.
     */
    fun wouldOverConstrain(
        drawingState: ProjectDrawingState,
        newConstraint: Constraint,
    ): ValidationResult {
        val currentAnalysis = analyzeDOF(drawingState)

        // Calculate DOF removed by new constraint
        val newConstraintDOF =
            when (newConstraint) {
                is Constraint.Distance -> 1
                is Constraint.Angle -> 1
                is Constraint.Parallel -> 1
                is Constraint.Perpendicular -> 1
            }

        val newDOF = currentAnalysis.degreesOfFreedom - newConstraintDOF

        return if (newDOF < 0) {
            ValidationResult.Rejected(
                reason = "Adding this constraint would overconstrain the system",
                currentDOF = currentAnalysis.degreesOfFreedom,
                afterDOF = newDOF,
            )
        } else {
            ValidationResult.Accepted(
                newDOF = newDOF,
                status =
                    when {
                        newDOF == 0 -> "Well-constrained"
                        newDOF > 0 -> "Underconstrained ($newDOF DOF remaining)"
                        else -> "Overconstrained"
                    },
            )
        }
    }

    /**
     * Find constraints that conflict with each other.
     * (Simplified version - full implementation would use graph analysis)
     */
    fun findConflictingConstraints(drawingState: ProjectDrawingState): List<String> {
        val analysis = analyzeDOF(drawingState)
        if (!analysis.isOverConstrained) return emptyList()

        // Simplified: Return all constraint IDs if overconstrained
        // Real implementation would analyze dependency graph
        return drawingState.constraints.keys.toList()
    }
}

/**
 * Result of DOF analysis.
 */
data class ConstraintAnalysis(
    val vertexCount: Int,
    val constraintCount: Int,
    val degreesOfFreedom: Int,
    val isOverConstrained: Boolean,
    val isWellConstrained: Boolean,
    val isUnderConstrained: Boolean,
)

/**
 * Result of constraint validation.
 */
sealed interface ValidationResult {
    data class Accepted(
        val newDOF: Int,
        val status: String,
    ) : ValidationResult

    data class Rejected(
        val reason: String,
        val currentDOF: Int,
        val afterDOF: Int,
    ) : ValidationResult
}
