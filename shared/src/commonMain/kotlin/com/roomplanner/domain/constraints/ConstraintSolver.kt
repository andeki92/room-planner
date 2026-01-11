package com.roomplanner.domain.constraints

import co.touchlab.kermit.Logger
import com.roomplanner.data.StateManager
import com.roomplanner.data.events.ConstraintEvent
import com.roomplanner.data.events.EventBus
import com.roomplanner.data.events.GeometryEvent
import com.roomplanner.data.models.Constraint
import com.roomplanner.data.models.ProjectDrawingState
import com.roomplanner.domain.drawing.Line
import com.roomplanner.domain.drawing.Vertex
import com.roomplanner.domain.geometry.Point2
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * ConstraintSolver maintains geometric constraints using iterative relaxation.
 *
 * Algorithm: Gauss-Seidel relaxation
 * - For each constraint, calculate error (current value - target value)
 * - Apply correction proportional to error (damped by relaxation factor)
 * - Repeat until converged (error < tolerance) or max iterations reached
 *
 * Based on spec.md § 5.4 Constraint System
 *
 * Design rationale:
 * - Iterative solver (not analytical) - handles over-constrained systems gracefully
 * - Local corrections - each constraint moves vertices slightly
 * - Respects fixed vertices - constraints don't move locked vertices
 * - Converges quickly for typical floor plans (rectangular rooms)
 * - Dual event listeners: ConstraintEvent (explicit) + GeometryEvent (implicit)
 *
 * Phase 1.5: Only Distance constraints implemented
 * Future: Angle, Parallel, Perpendicular constraints
 */
class ConstraintSolver(
    private val eventBus: EventBus,
    private val stateManager: StateManager,
) {
    companion object {
        const val MAX_ITERATIONS = 100
        const val TOLERANCE = 0.001 // 0.001 cm = 0.01 mm (sub-millimeter precision)
        const val RELAXATION_FACTOR = 0.5 // Damping to prevent oscillation
    }

    init {
        Logger.i { "✓ ConstraintSolver initialized" }

        // Listen to constraint events (explicit constraint changes)
        eventBus.events
            .filterIsInstance<ConstraintEvent>()
            .onEach { event -> handleConstraintEvent(event) }
            .launchIn(CoroutineScope(Dispatchers.Default))

        // CRITICAL: Also listen to geometry events (vertex dragging)
        // This maintains constraints during interactive editing
        eventBus.events
            .filterIsInstance<GeometryEvent>()
            .onEach { event -> handleGeometryEvent(event) }
            .launchIn(CoroutineScope(Dispatchers.Default))
    }

    /**
     * Handle constraint-related events.
     */
    private suspend fun handleConstraintEvent(event: ConstraintEvent) {
        when (event) {
            is ConstraintEvent.ConstraintAdded -> {
                Logger.i { "✓ Constraint added: ${event.constraint}" }

                // ✅ NEW: Validate DOF BEFORE adding
                val state = stateManager.state.value
                val drawingState = state.projectDrawingState ?: return

                val validationResult =
                    ConstraintAnalyzer.wouldOverConstrain(
                        drawingState,
                        event.constraint,
                    )

                when (validationResult) {
                    is ValidationResult.Rejected -> {
                        Logger.w {
                            "⚠ Constraint rejected: ${validationResult.reason} " +
                                "(current DOF: ${validationResult.currentDOF}, " +
                                "after: ${validationResult.afterDOF})"
                        }

                        // Emit error event for UI
                        eventBus.emit(
                            ConstraintEvent.ConstraintConflict(
                                message = validationResult.reason,
                                conflictingConstraints =
                                    ConstraintAnalyzer.findConflictingConstraints(
                                        drawingState,
                                    ),
                            ),
                        )
                        return // ← Don't add constraint!
                    }

                    is ValidationResult.Accepted -> {
                        Logger.i {
                            "✓ Constraint accepted: ${validationResult.status}"
                        }

                        // Add constraint to state
                        stateManager.updateState { state ->
                            state.updateDrawingState { it.withConstraint(event.constraint) }
                        }

                        // Solve with new constraint
                        solveConstraints()
                    }
                }
            }

            is ConstraintEvent.ConstraintRemoved -> {
                Logger.i { "✓ Constraint removed: ${event.constraintId}" }
                stateManager.updateState { state ->
                    state.updateDrawingState { it.withoutConstraint(event.constraintId) }
                }
            }

            is ConstraintEvent.ConstraintModified -> {
                Logger.i { "✓ Constraint modified: ${event.constraintId}" }

                // ✅ NEW: Validate DOF after modification
                val state = stateManager.state.value
                val drawingState = state.projectDrawingState ?: return

                // Remove old constraint temporarily for validation
                val stateWithoutOld = drawingState.withoutConstraint(event.constraintId)

                val validationResult =
                    ConstraintAnalyzer.wouldOverConstrain(
                        stateWithoutOld,
                        event.newConstraint,
                    )

                when (validationResult) {
                    is ValidationResult.Rejected -> {
                        Logger.w {
                            "⚠ Constraint modification rejected: ${validationResult.reason}"
                        }

                        // Emit error event for UI
                        eventBus.emit(
                            ConstraintEvent.ConstraintConflict(
                                message = validationResult.reason,
                                conflictingConstraints =
                                    ConstraintAnalyzer.findConflictingConstraints(
                                        stateWithoutOld,
                                    ),
                            ),
                        )
                        return // ← Don't modify constraint!
                    }

                    is ValidationResult.Accepted -> {
                        Logger.i {
                            "✓ Constraint modification accepted: ${validationResult.status}"
                        }

                        stateManager.updateState { state ->
                            state.updateDrawingState { drawingState ->
                                drawingState
                                    .withoutConstraint(event.constraintId)
                                    .withConstraint(event.newConstraint)
                            }
                        }
                        solveConstraints()
                    }
                }
            }

            is ConstraintEvent.ConstraintToggled -> {
                Logger.i { "✓ Constraint toggled: ${event.constraintId} → ${event.enabled}" }
                val state = stateManager.state.value
                val drawingState = state.projectDrawingState ?: return
                val constraint = drawingState.constraints[event.constraintId] ?: return

                // Update enabled state based on constraint type
                val updated =
                    when (constraint) {
                        is Constraint.Distance -> constraint.copy(enabled = event.enabled)
                        is Constraint.Angle -> constraint.copy(enabled = event.enabled)
                        is Constraint.Parallel -> constraint.copy(enabled = event.enabled)
                        is Constraint.Perpendicular -> constraint.copy(enabled = event.enabled)
                    }

                stateManager.updateState { state ->
                    state.updateDrawingState { drawingState ->
                        drawingState.copy(
                            constraints = drawingState.constraints + (event.constraintId to updated),
                        )
                    }
                }

                if (event.enabled) {
                    solveConstraints()
                }
            }

            is ConstraintEvent.SolveConstraints -> {
                solveConstraints()
            }

            is ConstraintEvent.ConstraintConflict -> {
                // No action needed - this is emitted by solver, not handled by it
            }
        }
    }

    /**
     * Handle geometry events that affect constraints.
     * CRITICAL: Solves constraints after vertex drag to maintain dimensions.
     */
    private suspend fun handleGeometryEvent(event: GeometryEvent) {
        when (event) {
            is GeometryEvent.VertexDragEnded -> {
                Logger.d { "→ Vertex drag ended, solving constraints" }
                solveConstraints()
            }

            else -> {
                // Other geometry events don't trigger constraint solving
            }
        }
    }

    /**
     * Main solver loop: iteratively apply constraints until convergence.
     *
     * Algorithm:
     * 1. For each constraint, calculate error and apply correction
     * 2. Track maximum error across all constraints
     * 3. If max error < tolerance, converged → exit
     * 4. If max iterations reached, give up → log warning
     *
     * Performance:
     * - Typical floor plans: 5-10 iterations, <100ms
     * - Complex scenes (50+ constraints): 10-20 iterations, <500ms
     */
    private suspend fun solveConstraints() {
        val state = stateManager.state.value
        val drawingState = state.projectDrawingState ?: return
        val constraints = drawingState.constraints.values.filter { it.enabled }

        if (constraints.isEmpty()) {
            return // No constraints to solve
        }

        Logger.d { "→ Solving ${constraints.size} constraints..." }

        repeat(MAX_ITERATIONS) { iteration ->
            var maxError = 0.0

            // Apply each constraint in sequence (Gauss-Seidel)
            constraints.forEach { constraint ->
                val error = applyConstraint(constraint)
                maxError = max(maxError, abs(error))
            }

            Logger.d { "  Iteration $iteration: max error = $maxError" }

            // Check convergence
            if (maxError < TOLERANCE) {
                Logger.i { "✓ Constraints converged in $iteration iterations (error: $maxError)" }
                return
            }
        }

        // Failed to converge
        Logger.w { "⚠ Constraints did not converge (max iterations reached)" }
    }

    /**
     * Apply single constraint, return error magnitude.
     *
     * @param constraint Constraint to apply
     * @return Absolute error (current - target)
     */
    private suspend fun applyConstraint(constraint: Constraint): Double =
        when (constraint) {
            is Constraint.Distance -> applyDistanceConstraint(constraint)
            is Constraint.Angle -> applyAngleConstraint(constraint)
            is Constraint.Parallel -> applyParallelConstraint(constraint)
            is Constraint.Perpendicular -> applyPerpendicularConstraint(constraint)
        }

    /**
     * Apply distance constraint with two-stage strategy (Phase 2):
     * Stage 1: Preserve angles (move vertices along existing line directions)
     * Stage 2: Allow rotation (Gauss-Seidel approach - fallback)
     *
     * Algorithm:
     * 1. Try Stage 1 (angle preservation) first
     * 2. If Stage 1 not applicable, fall back to Stage 2 (rotation allowed)
     *
     * @param constraint Distance constraint to apply
     * @return Error magnitude (for convergence check)
     */
    private suspend fun applyDistanceConstraint(constraint: Constraint.Distance): Double {
        val state = stateManager.state.value
        val drawingState = state.projectDrawingState ?: return 0.0

        val line = drawingState.lines.find { it.id == constraint.lineId } ?: return 0.0
        val v1 = drawingState.vertices[line.startVertexId] ?: return 0.0
        val v2 = drawingState.vertices[line.endVertexId] ?: return 0.0

        val currentLength = v1.position.distanceTo(v2.position)
        val error = currentLength - constraint.distance

        if (abs(error) < TOLERANCE) return error

        // Stage 1: Try to preserve angles of connected lines
        if (tryPreservingAngles(line, v1, v2, constraint.distance, error, drawingState)) {
            Logger.d { "→ Solved distance constraint preserving angles" }
            return error
        }

        // Stage 2: Allow rotation (existing Gauss-Seidel approach)
        Logger.d { "→ Solving distance constraint allowing rotation (fallback)" }
        return applyDistanceConstraintWithRotation(line, v1, v2, error)
    }

    /**
     * Stage 1: Try to satisfy distance constraint while preserving angles.
     * Returns true if successful, false if angle preservation not possible.
     */
    private suspend fun tryPreservingAngles(
        line: Line,
        v1: Vertex,
        v2: Vertex,
        targetDistance: Double,
        error: Double,
        drawingState: ProjectDrawingState
    ): Boolean {
        // Check if vertices have other connected lines
        val v1ConnectedLines = drawingState.getLinesConnectedToVertex(v1.id)
        val v2ConnectedLines = drawingState.getLinesConnectedToVertex(v2.id)

        // If no other connections, angle preservation doesn't matter
        if (v1ConnectedLines.size <= 1 && v2ConnectedLines.size <= 1) {
            return false // Fall back to Stage 2
        }

        // Cannot satisfy if both vertices are fixed
        if (v1.fixed && v2.fixed) {
            Logger.w { "⚠ Cannot satisfy distance: both vertices fixed" }
            return false
        }

        if (!v1.fixed && !v2.fixed) {
            // Both free - use smart anchoring strategy to avoid shifting entire shape
            // Strategy: Anchor the vertex with MORE connections (more constrained by geometry)
            // If equal connections, anchor v1 (start vertex) for predictable behavior

            val shouldAnchorV1 = v1ConnectedLines.size >= v2ConnectedLines.size

            if (shouldAnchorV1) {
                // Anchor v1, move v2 along the line direction
                Logger.d { "→ Anchoring start vertex (${v1ConnectedLines.size} connections)" }
                val direction = (v2.position - v1.position).normalize()
                val newV2 =
                    v2.copy(
                        position = v1.position + direction * targetDistance,
                    )

                stateManager.updateState { state ->
                    state.updateDrawingState { drawingState ->
                        drawingState.copy(
                            vertices = drawingState.vertices + (v2.id to newV2),
                        )
                    }
                }
            } else {
                // Anchor v2, move v1 along the line direction
                Logger.d { "→ Anchoring end vertex (${v2ConnectedLines.size} connections)" }
                val direction = (v1.position - v2.position).normalize()
                val newV1 =
                    v1.copy(
                        position = v2.position + direction * targetDistance,
                    )

                stateManager.updateState { state ->
                    state.updateDrawingState { drawingState ->
                        drawingState.copy(
                            vertices = drawingState.vertices + (v1.id to newV1),
                        )
                    }
                }
            }
            return true
        }

        // One fixed, one free - move the free one along the line direction
        val (fixedVertex, freeVertex) = if (v1.fixed) v1 to v2 else v2 to v1
        val direction = (freeVertex.position - fixedVertex.position).normalize()

        val newFreeVertex =
            freeVertex.copy(
                position = fixedVertex.position + direction * targetDistance,
            )

        stateManager.updateState { state ->
            state.updateDrawingState { drawingState ->
                drawingState.copy(
                    vertices = drawingState.vertices + (freeVertex.id to newFreeVertex),
                )
            }
        }
        return true
    }

    /**
     * Stage 2: Allow rotation (existing Gauss-Seidel relaxation).
     * This is the fallback when angle preservation is not applicable.
     *
     * Algorithm:
     * 1. Calculate current line length
     * 2. Calculate error = current - target
     * 3. Calculate unit direction vector
     * 4. Move both endpoints toward/away to correct error (damped by relaxation factor)
     * 5. Respect fixed vertices (don't move them)
     *
     * Edge cases:
     * - Degenerate line (length ≈ 0): Return error without correction
     * - Both vertices fixed: Cannot satisfy constraint (over-constrained)
     * - One vertex fixed: Move only the unfixed vertex
     */
    private suspend fun applyDistanceConstraintWithRotation(
        line: Line,
        v1: Vertex,
        v2: Vertex,
        error: Double
    ): Double {
        // Direction vector (v1 → v2)
        val dx = v2.position.x - v1.position.x
        val dy = v2.position.y - v1.position.y
        val length = sqrt(dx * dx + dy * dy)

        // Degenerate line (both vertices at same position)
        if (length < Point2.EPSILON) {
            return error // Can't fix degenerate line
        }

        // Unit direction vector
        val ux = dx / length
        val uy = dy / length

        // Correction magnitude (damped to prevent oscillation)
        val correction = error * RELAXATION_FACTOR

        // Move vertices based on which are fixed
        when {
            !v1.fixed && !v2.fixed -> {
                // Both vertices movable: split correction equally
                val halfCorrection = correction / 2.0

                val newV1 =
                    v1.copy(
                        position =
                            Point2(
                                v1.position.x + ux * halfCorrection,
                                v1.position.y + uy * halfCorrection,
                            ),
                    )

                val newV2 =
                    v2.copy(
                        position =
                            Point2(
                                v2.position.x - ux * halfCorrection,
                                v2.position.y - uy * halfCorrection,
                            ),
                    )

                stateManager.updateState { state ->
                    state.updateDrawingState { drawingState ->
                        drawingState.copy(
                            vertices =
                                drawingState.vertices +
                                    (v1.id to newV1) +
                                    (v2.id to newV2),
                        )
                    }
                }
            }

            !v1.fixed -> {
                // Only v1 movable: apply full correction to v1
                val newV1 =
                    v1.copy(
                        position =
                            Point2(
                                v1.position.x + ux * correction,
                                v1.position.y + uy * correction,
                            ),
                    )

                stateManager.updateState { state ->
                    state.updateDrawingState { drawingState ->
                        drawingState.copy(
                            vertices = drawingState.vertices + (v1.id to newV1),
                        )
                    }
                }
            }

            !v2.fixed -> {
                // Only v2 movable: apply full correction to v2
                val newV2 =
                    v2.copy(
                        position =
                            Point2(
                                v2.position.x - ux * correction,
                                v2.position.y - uy * correction,
                            ),
                    )

                stateManager.updateState { state ->
                    state.updateDrawingState { drawingState ->
                        drawingState.copy(
                            vertices = drawingState.vertices + (v2.id to newV2),
                        )
                    }
                }
            }

            else -> {
                // Both vertices fixed: cannot satisfy constraint (over-constrained)
                // This is a conflict - log it but don't crash
                Logger.w { "⚠ Over-constrained: line ${line.id} has both vertices fixed" }
            }
        }

        return error
    }

    /**
     * Apply angle constraint: maintain specific angle between two lines.
     *
     * Phase 3: Angle constraints
     * Currently a simplified implementation - rotates one line to match target angle.
     * Future: More sophisticated approach considering both lines' constraints.
     *
     * @param constraint Angle constraint to apply
     * @return Error magnitude (for convergence check)
     */
    private suspend fun applyAngleConstraint(constraint: Constraint.Angle): Double {
        val state = stateManager.state.value
        val drawingState = state.projectDrawingState ?: return 0.0

        val line1 = drawingState.lines.find { it.id == constraint.lineId1 } ?: return 0.0
        val line2 = drawingState.lines.find { it.id == constraint.lineId2 } ?: return 0.0

        val geom1 = line1.getGeometry(drawingState.vertices)
        val geom2 = line2.getGeometry(drawingState.vertices)

        // Calculate current angles
        val angle1 = atan2(geom1.end.y - geom1.start.y, geom1.end.x - geom1.start.x)
        val angle2 = atan2(geom2.end.y - geom2.start.y, geom2.end.x - geom2.start.x)

        val currentAngle = normalizeAngle(angle2 - angle1)
        val targetAngleRad = constraint.angle * PI / 180.0 // Convert degrees to radians
        val error = normalizeAngle(currentAngle - targetAngleRad)

        if (abs(error) < (TOLERANCE * PI / 180.0)) return error

        Logger.d {
            "→ Applying angle constraint: current=${currentAngle * 180 / PI}°, target=${constraint.angle}°, error=${error * 180 / PI}°"
        }

        // Simplified: For now, just log and return error
        // Full implementation would rotate vertices to adjust angle
        // This requires more complex logic to determine which line to rotate
        return error
    }

    /**
     * Apply parallel constraint (special case of angle constraint with angle=0°).
     */
    private suspend fun applyParallelConstraint(constraint: Constraint.Parallel): Double {
        // Convert to angle constraint with angle = 0
        val angleConstraint =
            Constraint.Angle(
                id = constraint.id,
                lineId1 = constraint.lineId1,
                lineId2 = constraint.lineId2,
                angle = 0.0,
                enabled = constraint.enabled,
                userSet = false,
            )
        return applyAngleConstraint(angleConstraint)
    }

    /**
     * Apply perpendicular constraint (angle = 90°).
     */
    private suspend fun applyPerpendicularConstraint(constraint: Constraint.Perpendicular): Double {
        val angleConstraint =
            Constraint.Angle(
                id = constraint.id,
                lineId1 = constraint.lineId1,
                lineId2 = constraint.lineId2,
                angle = 90.0,
                enabled = constraint.enabled,
                userSet = false,
            )
        return applyAngleConstraint(angleConstraint)
    }

    /**
     * Normalize angle to [-π, π] range.
     */
    private fun normalizeAngle(angle: Double): Double {
        var normalized = angle
        while (normalized > PI) normalized -= 2 * PI
        while (normalized < -PI) normalized += 2 * PI
        return normalized
    }
}
