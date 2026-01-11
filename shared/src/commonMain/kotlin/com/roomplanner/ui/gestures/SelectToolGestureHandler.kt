package com.roomplanner.ui.gestures

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import co.touchlab.kermit.Logger
import com.roomplanner.data.events.EventBus
import com.roomplanner.data.events.GeometryEvent
import com.roomplanner.data.models.CameraTransform
import com.roomplanner.data.models.ProjectDrawingState
import com.roomplanner.domain.geometry.Point2
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
        onSnapHint: (Point2?) -> Unit,
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
        onSnapHint: (Point2?) -> Unit,
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

        eventBus.emit(
            GeometryEvent.VertexDragged(
                vertexId = vertexId,
                newPosition = newPosition,
            ),
        )
    }

    override suspend fun handleRelease(
        screenPosition: Offset,
        drawingState: ProjectDrawingState,
        camera: CameraTransform,
        density: Density,
        eventBus: EventBus,
        onPreview: (Point2?) -> Unit,
        onSnapHint: (Point2?) -> Unit,
    ) {
        val vertexId = draggedVertexId ?: return
        val finalVertex = drawingState.vertices[vertexId] ?: return

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
    ) {
        // Tap to show radial menu on vertex or line
        // Note: Actual radial menu logic handled in DrawingCanvas (UI concern)
        // This handler just logs for debugging

        val tappedVertexId =
            HitTesting.findVertexAt(
                tapScreen = screenPosition,
                drawingState = drawingState,
                camera = camera,
                density = density,
                config = drawingState.drawingConfig,
            )

        if (tappedVertexId != null) {
            Logger.d { "→ SELECT: Tapped vertex $tappedVertexId" }
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
