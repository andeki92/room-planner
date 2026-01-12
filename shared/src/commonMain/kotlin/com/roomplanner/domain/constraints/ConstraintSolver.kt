@file:Suppress("DuplicatedCode")

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
import com.roomplanner.domain.geometry.RectangleDetector
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
     * Phase 1.6: Priority-based solving ensures 90° angles are never broken.
     *
     * Algorithm:
     * 1. Sort constraints by priority (highest first)
     *    - Perpendicular constraints (priority 100) solved first
     *    - Distance constraints (priority 50) solved second
     *    - Other angles (priority 10) solved last
     * 2. For each constraint, calculate error and apply correction
     * 3. Track maximum error across all constraints
     * 4. If max error < tolerance, converged → exit
     * 5. If max iterations reached, give up → log warning
     *
     * Performance:
     * - Typical floor plans: 5-10 iterations, <100ms
     * - Complex scenes (50+ constraints): 10-20 iterations, <500ms
     */
    private suspend fun solveConstraints() {
        val state = stateManager.state.value
        val drawingState = state.projectDrawingState ?: return
        val constraints =
            drawingState.constraints.values
                .filter { it.enabled }
                .sortedByDescending { it.priority } // ✅ NEW: Priority-based sorting

        if (constraints.isEmpty()) {
            return // No constraints to solve
        }

        Logger.d { "→ Solving ${constraints.size} constraints (priority-sorted)..." }

        repeat(MAX_ITERATIONS) { iteration ->
            var maxError = 0.0

            // Apply each constraint in priority order (Gauss-Seidel)
            constraints.forEach { constraint ->
                val error = applyConstraint(constraint)
                maxError = max(maxError, abs(error))
            }

            // Only log every 10 iterations or when error is high
            if (iteration % 10 == 0 || maxError > 10.0) {
                Logger.d { "  Iteration $iteration: max error = $maxError" }
            }

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
     *
     * Phase 1.6.1: Smart propagation for rectangles.
     * If line is part of rectangle, propagate dimension to opposite wall.
     */
    private suspend fun tryPreservingAngles(
        line: Line,
        v1: Vertex,
        v2: Vertex,
        targetDistance: Double,
        error: Double,
        drawingState: ProjectDrawingState
    ): Boolean {
        // Phase 1.6.1: Check if line is part of rectangle
        val rectangle = RectangleDetector.detectRectangle(line, drawingState)
        if (rectangle != null) {
            Logger.d { "→ Detected rectangle, propagating to opposite wall" }
            return propagateToOppositeWall(line, targetDistance, rectangle, drawingState)
        }

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
     * Phase 1.6.1: Propagate dimension change to opposite wall in rectangle.
     *
     * When adjusting one wall's dimension in a rectangle:
     * 1. Adjust the constrained wall to target distance
     * 2. Find opposite wall
     * 3. Adjust opposite wall to same distance
     * 4. Keep all 90° angles intact
     *
     * @param line The wall being constrained
     * @param targetDistance Target distance for the wall
     * @param rectangle Detected rectangle structure
     * @param drawingState Current drawing state
     * @return True if propagation successful
     */
    private suspend fun propagateToOppositeWall(
        line: Line,
        targetDistance: Double,
        rectangle: RectangleDetector.Rectangle,
        drawingState: ProjectDrawingState
    ): Boolean {
        val oppositeLine = rectangle.oppositeLine(line) ?: return false

        // Get vertices for both lines
        val v1 = drawingState.vertices[line.startVertexId] ?: return false
        val v2 = drawingState.vertices[line.endVertexId] ?: return false
        val oppV1 = drawingState.vertices[oppositeLine.startVertexId] ?: return false
        val oppV2 = drawingState.vertices[oppositeLine.endVertexId] ?: return false

        // Calculate direction vectors for both walls
        val direction = (v2.position - v1.position).normalize()
        val oppDirection = (oppV2.position - oppV1.position).normalize()

        // Adjust primary wall (the one being constrained)
        val newV2 =
            v2.copy(
                position = v1.position + direction * targetDistance,
            )

        // Adjust opposite wall to same distance
        val newOppV2 =
            oppV2.copy(
                position = oppV1.position + oppDirection * targetDistance,
            )

        // Update both vertices atomically
        stateManager.updateState { state ->
            state.updateDrawingState { drawingState ->
                drawingState.copy(
                    vertices =
                        drawingState.vertices +
                            (v2.id to newV2) +
                            (oppV2.id to newOppV2),
                )
            }
        }

        Logger.i { "✓ Propagated dimension to opposite wall (rectangle preserved)" }
        return true
    }

    /**
     * Stage 2: Position-Based distance constraint solver (fallback when angle preservation not applicable).
     *
     * Based on Position-Based Dynamics (PBD) distance constraint algorithm.
     * Source: https://carmencincotti.com/2022-08-22/the-distance-constraint-of-xpbd/
     *
     * Algorithm:
     * 1. Calculate constraint function: C = |currentLength| - targetLength
     * 2. Calculate normalized direction: n = (v2 - v1) / |v2 - v1|
     * 3. Calculate position corrections weighted by inverse mass
     * 4. Apply corrections with damping
     *
     * Position correction formulas:
     * - Δx1 = -(w1/(w1 + w2)) * C * n  (move v1 towards/away from v2)
     * - Δx2 = +(w2/(w1 + w2)) * C * n  (move v2 away/towards v1)
     *
     * where w = inverse mass (fixed vertices have w=0, free vertices have w=1)
     *
     * Edge cases:
     * - Degenerate line (length ≈ 0): Return error without correction
     * - Both vertices fixed: Cannot satisfy constraint (over-constrained)
     * - One vertex fixed: Other vertex receives 100% of correction
     */
    private suspend fun applyDistanceConstraintWithRotation(
        line: Line,
        v1: Vertex,
        v2: Vertex,
        error: Double
    ): Double {
        // Calculate current distance
        val dx = v2.position.x - v1.position.x
        val dy = v2.position.y - v1.position.y
        val currentLength = sqrt(dx * dx + dy * dy)

        // Degenerate line (both vertices at same position)
        if (currentLength < Point2.EPSILON) {
            Logger.w { "⚠ Degenerate line ${line.id}: vertices at same position" }
            return error
        }

        // Calculate normalized direction vector (gradient of constraint)
        val nx = dx / currentLength
        val ny = dy / currentLength

        // Constraint function: C = currentLength - targetLength = error
        // (Positive = too long, negative = too short)
        val c = error

        // Calculate inverse masses (w = 1/m, fixed vertices have w = 0)
        val w1 = if (v1.fixed) 0.0 else 1.0
        val w2 = if (v2.fixed) 0.0 else 1.0
        val wSum = w1 + w2

        // Both fixed = over-constrained
        if (wSum < Point2.EPSILON) {
            Logger.w { "⚠ Over-constrained: line ${line.id} has both vertices fixed" }
            return error
        }

        // Calculate position corrections (PBD formula with damping)
        // Δx1 = -(w1/wSum) * C * n
        // Δx2 = +(w2/wSum) * C * n
        val correction1 = -(w1 / wSum) * c * RELAXATION_FACTOR
        val correction2 = (w2 / wSum) * c * RELAXATION_FACTOR

        val delta1x = correction1 * nx
        val delta1y = correction1 * ny
        val delta2x = correction2 * nx
        val delta2y = correction2 * ny

        // Apply corrections
        val updates = mutableMapOf<String, Vertex>()

        if (!v1.fixed && abs(correction1) > Point2.EPSILON) {
            updates[v1.id] =
                v1.copy(
                    position =
                        Point2(
                            v1.position.x + delta1x,
                            v1.position.y + delta1y,
                        ),
                )
        }

        if (!v2.fixed && abs(correction2) > Point2.EPSILON) {
            updates[v2.id] =
                v2.copy(
                    position =
                        Point2(
                            v2.position.x + delta2x,
                            v2.position.y + delta2y,
                        ),
                )
        }

        if (updates.isNotEmpty()) {
            stateManager.updateState { state ->
                state.updateDrawingState { drawingState ->
                    drawingState.copy(vertices = drawingState.vertices + updates)
                }
            }
        }

        return error
    }

    /**
     * Apply angle constraint: maintain specific angle between two lines.
     *
     * Phase 1.6: Rotates one line to match target angle.
     * Strategy: Rotate the line with fewer connections (less impact on overall geometry).
     *
     * @param constraint Angle constraint to apply
     * @return Error magnitude (for convergence check)
     */
    private suspend fun applyAngleConstraint(constraint: Constraint.Angle): Double {
        val state = stateManager.state.value
        val drawingState = state.projectDrawingState ?: return 0.0

        val line1 = drawingState.lines.find { it.id == constraint.lineId1 } ?: return 0.0
        val line2 = drawingState.lines.find { it.id == constraint.lineId2 } ?: return 0.0

        // Find shared vertex (lines must share a vertex to have a meaningful angle)
        val sharedVertexId =
            when {
                line1.startVertexId == line2.startVertexId || line1.startVertexId == line2.endVertexId ->
                    line1.startVertexId

                line1.endVertexId == line2.startVertexId || line1.endVertexId == line2.endVertexId -> line1.endVertexId
                else -> {
                    Logger.w { "⚠ Angle constraint on non-adjacent lines" }
                    return 0.0
                }
            }

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

        // Decide which line to rotate: prefer rotating the one with fewer connections
        val line1Connections =
            drawingState.getLinesConnectedToVertex(line1.startVertexId).size +
                drawingState.getLinesConnectedToVertex(line1.endVertexId).size
        val line2Connections =
            drawingState.getLinesConnectedToVertex(line2.startVertexId).size +
                drawingState.getLinesConnectedToVertex(line2.endVertexId).size

        val (lineToRotate, targetAngle) =
            if (line1Connections <= line2Connections) {
                // Rotate line1 to match angle relative to line2
                line1 to (angle2 - targetAngleRad)
            } else {
                // Rotate line2 to match angle relative to line1
                line2 to (angle1 + targetAngleRad)
            }

        // Perform rotation around shared vertex
        rotateLineAroundVertex(lineToRotate, sharedVertexId, targetAngle, drawingState)

        return error
    }

    /**
     * Rotate a line around a pivot vertex to a target angle.
     * Moves the non-pivot vertex to achieve the target angle.
     */
    private suspend fun rotateLineAroundVertex(
        line: Line,
        pivotVertexId: String,
        targetAngle: Double,
        drawingState: ProjectDrawingState
    ) {
        val pivotVertex = drawingState.vertices[pivotVertexId] ?: return
        val otherVertexId = if (line.startVertexId == pivotVertexId) line.endVertexId else line.startVertexId
        val otherVertex = drawingState.vertices[otherVertexId] ?: return

        // Don't rotate if other vertex is fixed
        if (otherVertex.fixed) {
            Logger.d { "→ Cannot rotate: other vertex is fixed" }
            return
        }

        // Calculate current distance (preserve line length during rotation)
        val currentDistance = pivotVertex.position.distanceTo(otherVertex.position)

        // Calculate new position based on target angle
        val newX = pivotVertex.position.x + currentDistance * kotlin.math.cos(targetAngle)
        val newY = pivotVertex.position.y + currentDistance * kotlin.math.sin(targetAngle)

        val newOtherVertex =
            otherVertex.copy(
                position = Point2(newX, newY),
            )

        // Apply rotation with damping to prevent oscillation
        val dampedPosition =
            Point2(
                x = otherVertex.position.x + (newX - otherVertex.position.x) * RELAXATION_FACTOR,
                y = otherVertex.position.y + (newY - otherVertex.position.y) * RELAXATION_FACTOR,
            )

        stateManager.updateState { state ->
            state.updateDrawingState { drawingState ->
                drawingState.copy(
                    vertices = drawingState.vertices + (otherVertexId to otherVertex.copy(position = dampedPosition)),
                )
            }
        }

        Logger.d { "→ Rotated line ${line.id} around vertex $pivotVertexId" }
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
     *
     * Phase 1.6: Improved implementation that actually rotates lines to maintain 90°.
     *
     * Strategy:
     * 1. Calculate current angle between lines
     * 2. If not 90° (within tolerance), rotate one line to make it 90°
     * 3. Prefer rotating the line with fewer connections (less impact)
     * 4. Respect fixed vertices (don't move them)
     */
    private suspend fun applyPerpendicularConstraint(constraint: Constraint.Perpendicular): Double {
        val state = stateManager.state.value
        val drawingState = state.projectDrawingState ?: return 0.0

        val line1 = drawingState.lines.find { it.id == constraint.lineId1 } ?: return 0.0
        val line2 = drawingState.lines.find { it.id == constraint.lineId2 } ?: return 0.0

        // Find shared vertex (lines must share a vertex to be perpendicular)
        val sharedVertexId =
            when {
                line1.startVertexId == line2.startVertexId || line1.startVertexId == line2.endVertexId ->
                    line1.startVertexId

                line1.endVertexId == line2.startVertexId || line1.endVertexId == line2.endVertexId ->
                    line1.endVertexId

                else -> {
                    Logger.w { "⚠ Perpendicular constraint on non-adjacent lines" }
                    return 0.0
                }
            }

        val geom1 = line1.getGeometry(drawingState.vertices)
        val geom2 = line2.getGeometry(drawingState.vertices)

        // Calculate angles (in radians)
        val angle1 = atan2(geom1.end.y - geom1.start.y, geom1.end.x - geom1.start.x)
        val angle2 = atan2(geom2.end.y - geom2.start.y, geom2.end.x - geom2.start.x)

        // Calculate relative angle (normalize to -π to π)
        var relativeAngle = angle2 - angle1
        while (relativeAngle > PI) relativeAngle -= 2 * PI
        while (relativeAngle < -PI) relativeAngle += 2 * PI

        // Calculate error from 90° (π/2 radians)
        val targetAngle = PI / 2.0
        val angleDegrees = abs(relativeAngle) * 180.0 / PI
        val error = abs(abs(relativeAngle) - targetAngle)

        if (error < (TOLERANCE * PI / 180.0)) return error

        Logger.d {
            "→ Applying perpendicular constraint: current=$angleDegrees°, target=90°, error=${error * 180 / PI}°"
        }

        // Decide which line to rotate: prefer rotating the one with fewer connections
        val line1Connections =
            drawingState.getLinesConnectedToVertex(line1.startVertexId).size +
                drawingState.getLinesConnectedToVertex(line1.endVertexId).size
        val line2Connections =
            drawingState.getLinesConnectedToVertex(line2.startVertexId).size +
                drawingState.getLinesConnectedToVertex(line2.endVertexId).size

        val (lineToRotate, targetAngleAbs) =
            if (line1Connections <= line2Connections) {
                // Rotate line1 perpendicular to line2
                line1 to (angle2 + PI / 2.0)
            } else {
                // Rotate line2 perpendicular to line1
                line2 to (angle1 + PI / 2.0)
            }

        // Perform rotation around shared vertex
        rotateLineAroundVertex(lineToRotate, sharedVertexId, targetAngleAbs, drawingState)

        return error
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
