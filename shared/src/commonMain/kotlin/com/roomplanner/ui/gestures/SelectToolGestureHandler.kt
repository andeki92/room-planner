package com.roomplanner.ui.gestures

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import co.touchlab.kermit.Logger
import com.roomplanner.data.events.EventBus
import com.roomplanner.data.events.GeometryEvent
import com.roomplanner.data.models.CameraTransform
import com.roomplanner.data.models.ProjectDrawingState
import com.roomplanner.data.models.SnapResultWithGuidelines
import com.roomplanner.domain.geometry.Point2
import com.roomplanner.domain.snapping.SmartSnapSystem
import com.roomplanner.ui.utils.HapticFeedback
import com.roomplanner.ui.utils.HitTesting

/**
 * Gesture handler for SELECT mode (tap to select, drag to move).
 *
 * Behavior:
 * 1. Press: Check if pressing on selected vertex → start drag
 * 2. Drag: Move vertex (emit VertexDragged events)
 * 3. Release: Finalize vertex position (emit VertexDragEnded)
 * 4. Tap: Show radial menu on vertex/line (handled in DrawingCanvas via callbacks)
 *
 * Design rationale:
 * - Isolated from DrawingCanvas (single responsibility)
 * - Uses pure utilities (HitTesting)
 * - Emits events for state changes (vertex drag)
 * - Radial menu logic stays in DrawingCanvas (UI concern)
 */
class SelectToolGestureHandler : ToolGestureHandler {
    // Track if we're dragging a vertex (local state)
    private var draggedVertexId: String? = null

    override suspend fun handlePress(
        screenPosition: Offset,
        drawingState: ProjectDrawingState,
        camera: CameraTransform,
        density: Density,
        eventBus: EventBus,
        onPreview: (Point2?) -> Unit,
        onSnapHint: (SnapResultWithGuidelines?) -> Unit,
    ) {
        // Check if pressing on a selected vertex (start drag)
        val vertexId =
            HitTesting.findVertexAt(
                tapScreen = screenPosition,
                drawingState = drawingState,
                camera = camera,
                density = density,
                config = drawingState.drawingConfig,
            )

        if (vertexId != null && drawingState.isVertexSelected(vertexId)) {
            // Start dragging selected vertex
            draggedVertexId = vertexId
            val vertex = drawingState.vertices[vertexId]!!

            eventBus.emit(
                GeometryEvent.VertexDragStarted(
                    vertexId = vertexId,
                    startPosition = vertex.position,
                ),
            )

            Logger.d { "→ SELECT: Drag started for vertex $vertexId" }
        }
    }

    override suspend fun handleDrag(
        screenPosition: Offset,
        dragDelta: Offset,
        drawingState: ProjectDrawingState,
        camera: CameraTransform,
        density: Density,
        eventBus: EventBus,
        onPreview: (Point2?) -> Unit,
        onSnapHint: (SnapResultWithGuidelines?) -> Unit,
    ) {
        val vertexId = draggedVertexId ?: return

        // Convert screen drag delta → world space
        val dragWorld =
            Point2(
                x = (dragDelta.x / camera.zoom).toDouble(),
                y = (dragDelta.y / camera.zoom).toDouble(),
            )

        val currentVertex = drawingState.vertices[vertexId] ?: return
        val newPosition =
            Point2(
                x = currentVertex.position.x + dragWorld.x,
                y = currentVertex.position.y + dragWorld.y,
            )

        // Phase 1.6.2: Calculate snap for vertex being dragged (supports multiple guidelines)
        val snapResult =
            SmartSnapSystem.calculateSnapWithGuidelines(
                cursorWorld = newPosition,
                cursorScreen = screenPosition,
                drawingState = drawingState,
                camera = camera,
                density = density,
            )

        // Show snap hint (guidelines) if any
        onSnapHint(if (snapResult.guidelines.isNotEmpty()) snapResult else null)

        // Use snap position (which may be intersection of multiple guidelines)
        val finalPosition =
            if (snapResult.guidelines.isNotEmpty()) {
                snapResult.snapPosition
            } else {
                newPosition
            }

        // Calculate drag delta for this vertex
        val actualDelta =
            Point2(
                x = finalPosition.x - currentVertex.position.x,
                y = finalPosition.y - currentVertex.position.y,
            )

        // Move the dragged vertex
        eventBus.emit(
            GeometryEvent.VertexDragged(
                vertexId = vertexId,
                newPosition = finalPosition,
            ),
        )

        // Check for distance-constrained lines connected to this vertex
        // Move connected vertices to maintain the constraint
        val connectedLines = drawingState.getLinesConnectedToVertex(vertexId)

        connectedLines.forEach { line ->
            // Find distance constraint for this line
            val distanceConstraint =
                drawingState.constraints.values
                    .filterIsInstance<com.roomplanner.data.models.Constraint.Distance>()
                    .find { it.lineId == line.id && it.enabled }

            if (distanceConstraint != null) {
                // This line has a distance constraint - move the other vertex
                val otherVertexId =
                    if (line.startVertexId == vertexId) {
                        line.endVertexId
                    } else {
                        line.startVertexId
                    }

                val otherVertex = drawingState.vertices[otherVertexId]
                if (otherVertex != null && !otherVertex.fixed) {
                    // Move other vertex by the same delta to maintain distance
                    val newOtherPosition =
                        Point2(
                            x = otherVertex.position.x + actualDelta.x,
                            y = otherVertex.position.y + actualDelta.y,
                        )

                    eventBus.emit(
                        GeometryEvent.VertexDragged(
                            vertexId = otherVertexId,
                            newPosition = newOtherPosition,
                        ),
                    )

                    Logger.d { "→ Moving connected vertex $otherVertexId to maintain constraint" }
                }
            }
        }
    }

    override suspend fun handleRelease(
        screenPosition: Offset,
        drawingState: ProjectDrawingState,
        camera: CameraTransform,
        density: Density,
        eventBus: EventBus,
        onPreview: (Point2?) -> Unit,
        onSnapHint: (SnapResultWithGuidelines?) -> Unit,
    ) {
        val vertexId = draggedVertexId ?: return
        val finalVertex = drawingState.vertices[vertexId] ?: return

        // Clear snap hint on release
        onSnapHint(null)

        eventBus.emit(
            GeometryEvent.VertexDragEnded(
                vertexId = vertexId,
                finalPosition = finalVertex.position,
            ),
        )

        Logger.i { "✓ SELECT: Vertex drag ended: $vertexId" }
        draggedVertexId = null
    }

    override suspend fun handleTap(
        screenPosition: Offset,
        drawingState: ProjectDrawingState,
        camera: CameraTransform,
        density: Density,
        eventBus: EventBus,
        onPreview: (Point2?) -> Unit,
        onSnapHint: (SnapResultWithGuidelines?) -> Unit,
    ) {
        // Tap on vertex → select it (make it active for continuing drawing)
        // Note: Radial menu logic handled in DrawingCanvas via long press

        val tappedVertexId =
            HitTesting.findVertexAt(
                tapScreen = screenPosition,
                drawingState = drawingState,
                camera = camera,
                density = density,
                config = drawingState.drawingConfig,
            )

        if (tappedVertexId != null) {
            // Select vertex (make it active for DRAW mode)
            HapticFeedback.light() // Light haptic feedback on selection
            eventBus.emit(GeometryEvent.VertexSelected(vertexId = tappedVertexId))
            Logger.i { "✓ SELECT: Vertex selected: $tappedVertexId (active for drawing)" }
            return
        }

        val tappedLineId =
            HitTesting.findLineAt(
                tapScreen = screenPosition,
                drawingState = drawingState,
                camera = camera,
                density = density,
                config = drawingState.drawingConfig,
            )

        if (tappedLineId != null) {
            Logger.d { "→ SELECT: Tapped line $tappedLineId" }
            return
        }

        // Tap on empty space
        Logger.d { "→ SELECT: Tapped empty space" }
    }
}
