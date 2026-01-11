package com.roomplanner.domain.geometry

import co.touchlab.kermit.Logger
import com.roomplanner.data.StateManager
import com.roomplanner.data.events.EventBus
import com.roomplanner.data.events.GeometryEvent
import com.roomplanner.domain.drawing.Line
import com.roomplanner.domain.drawing.Vertex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * GeometryManager listens to geometry events and updates application state.
 *
 * Design rationale:
 * - Event-driven architecture: tools emit events, manager reacts
 * - Decouples UI (tools) from business logic (state updates)
 * - All state updates go through StateManager (immutable updates)
 * - Kermit logging for debugging and validation
 *
 * Event flow:
 * 1. User taps canvas → DrawingCanvas emits GeometryEvent.PointPlaced
 * 2. GeometryManager listens → Creates Vertex
 * 3. If previous vertex exists → Creates Line between them
 * 4. Updates AppState immutably via StateManager
 * 5. Compose recomposes with new state
 */
class GeometryManager(
    private val eventBus: EventBus,
    private val stateManager: StateManager,
) {
    init {
        Logger.i { "✓ GeometryManager initialized" }

        // Listen to geometry events
        eventBus.events
            .filterIsInstance<GeometryEvent>()
            .onEach { event -> handleEvent(event) }
            .launchIn(CoroutineScope(Dispatchers.Default))
    }

    private suspend fun handleEvent(event: GeometryEvent) {
        when (event) {
            is GeometryEvent.PointPlaced -> handlePointPlaced(event)
            is GeometryEvent.CameraTransformed -> handleCameraTransformed(event)
            // Selection events (Phase 1.4)
            is GeometryEvent.VertexSelected -> handleVertexSelected(event)
            is GeometryEvent.SelectionCleared -> handleSelectionCleared()
            is GeometryEvent.VertexDragStarted -> handleVertexDragStarted(event)
            is GeometryEvent.VertexDragged -> handleVertexDragged(event)
            is GeometryEvent.VertexDragEnded -> handleVertexDragEnded(event)
            is GeometryEvent.VertexDeleted -> handleVertexDeleted(event)
            // Tool mode events (Phase 1.4b)
            is GeometryEvent.ToolModeChanged -> handleToolModeChanged(event)
            // Line events (Phase 1.5)
            is GeometryEvent.LineSelected -> handleLineSelected(event)
            is GeometryEvent.LineDeleted -> handleLineDeleted(event)
            is GeometryEvent.LineSplit -> handleLineSplit(event)
        }
    }

    private suspend fun handlePointPlaced(event: GeometryEvent.PointPlaced) {
        Logger.d { "→ PointPlaced at (${event.position.x}, ${event.position.y}), snappedTo=${event.snappedTo}" }

        val state = stateManager.state.value
        val drawingState = state.projectDrawingState

        if (drawingState == null) {
            Logger.w { "⚠ No active project drawing state" }
            return
        }

        // Check if snapping to existing vertex
        val vertex =
            if (event.snappedTo != null) {
                // Reuse existing vertex
                drawingState.vertices[event.snappedTo]?.also {
                    Logger.d { "  Reusing existing vertex: ${it.id}" }
                }
            } else {
                // Create new vertex
                Vertex(position = event.position).also {
                    Logger.i { "✓ Vertex created: ${it.id} at (${it.position.x}, ${it.position.y})" }
                }
            }

        if (vertex == null) {
            Logger.w { "⚠ Snap target vertex not found: ${event.snappedTo}" }
            return
        }

        // Update state with new vertex (if not snapping to existing)
        if (event.snappedTo == null) {
            stateManager.updateState { state ->
                state.updateDrawingState { drawingState ->
                    drawingState.withVertex(vertex)
                }
            }
        }

        // If continuing from previous vertex, create line
        val previousVertex = drawingState.getActiveVertex()
        if (previousVertex != null && previousVertex.id != vertex.id) {
            createLine(previousVertex, vertex)
        } else if (event.snappedTo == null) {
            // First vertex or same vertex - just set as active
            stateManager.updateState { state ->
                state.updateDrawingState { drawingState ->
                    drawingState.copy(activeVertexId = vertex.id)
                }
            }
        }
    }

    private suspend fun createLine(
        start: Vertex,
        end: Vertex,
    ) {
        Logger.i { "✓ Line created: ${start.id} → ${end.id}" }

        val line = Line.between(start, end)

        stateManager.updateState { state ->
            state.updateDrawingState { drawingState ->
                drawingState.withLine(line)
            }
        }
    }

    private suspend fun handleCameraTransformed(event: GeometryEvent.CameraTransformed) {
        Logger.d { "→ Camera transform: pan=(${event.panDelta.x}, ${event.panDelta.y}), zoom=${event.zoomDelta}" }

        stateManager.updateState { state ->
            val drawingState = state.projectDrawingState
            if (drawingState == null) {
                Logger.w { "⚠ No active project drawing state for camera transform" }
                return@updateState state
            }

            val currentCamera = drawingState.cameraTransform

            // 1. Apply zoom delta
            val newZoom =
                (currentCamera.zoom * event.zoomDelta).coerceIn(
                    com.roomplanner.data.models.CameraTransform.MIN_ZOOM,
                    com.roomplanner.data.models.CameraTransform.MAX_ZOOM,
                )

            // 2. Adjust pan to zoom around zoomCenter (if provided)
            val (newPanX, newPanY) =
                if (event.zoomCenter != null && event.zoomDelta != 1f) {
                    // Convert zoom center to world coordinates BEFORE zoom
                    val centerWorld = screenToWorldStatic(event.zoomCenter, currentCamera)

                    // After zoom, convert back to screen - this is where it WOULD be
                    val centerScreenAfter =
                        androidx.compose.ui.geometry.Offset(
                            (centerWorld.x.toFloat() * newZoom) + currentCamera.panX,
                            (centerWorld.y.toFloat() * newZoom) + currentCamera.panY,
                        )

                    // Calculate pan adjustment to keep center fixed
                    val panAdjustX = event.zoomCenter.x - centerScreenAfter.x
                    val panAdjustY = event.zoomCenter.y - centerScreenAfter.y

                    Pair(
                        currentCamera.panX + event.panDelta.x + panAdjustX,
                        currentCamera.panY + event.panDelta.y + panAdjustY,
                    )
                } else {
                    // No zoom, just apply pan delta
                    Pair(
                        currentCamera.panX + event.panDelta.x,
                        currentCamera.panY + event.panDelta.y,
                    )
                }

            val newCamera =
                com.roomplanner.data.models.CameraTransform(
                    panX = newPanX,
                    panY = newPanY,
                    zoom = newZoom,
                )

            Logger.d { "  Camera updated: pan=($newPanX, $newPanY), zoom=$newZoom" }

            state.updateDrawingState { it.withCamera(newCamera) }
        }
    }

    /**
     * Static helper: convert screen to world (for use outside DrawScope).
     * Inverse transform: world = (screen - pan) / zoom
     */
    private fun screenToWorldStatic(
        screenOffset: androidx.compose.ui.geometry.Offset,
        camera: com.roomplanner.data.models.CameraTransform,
    ): Point2 {
        val worldX = ((screenOffset.x - camera.panX) / camera.zoom).toDouble()
        val worldY = ((screenOffset.y - camera.panY) / camera.zoom).toDouble()
        return Point2(worldX, worldY)
    }

    // Selection event handlers (Phase 1.4)

    private suspend fun handleVertexSelected(event: GeometryEvent.VertexSelected) {
        Logger.i { "✓ Vertex selected: ${event.vertexId}" }

        stateManager.updateState { state ->
            state.updateDrawingState { drawingState ->
                drawingState.selectVertex(event.vertexId)
            }
        }
    }

    private suspend fun handleSelectionCleared() {
        Logger.d { "→ Selection cleared" }

        stateManager.updateState { state ->
            state.updateDrawingState { drawingState ->
                drawingState.clearSelection()
            }
        }
    }

    private suspend fun handleVertexDragStarted(event: GeometryEvent.VertexDragStarted) {
        Logger.d { "→ Drag started: ${event.vertexId}" }
        // Drag state tracked in DrawingCanvas (local UI state)
    }

    private suspend fun handleVertexDragged(event: GeometryEvent.VertexDragged) {
        stateManager.updateState { state ->
            state.updateDrawingState { drawingState ->
                val vertex = drawingState.vertices[event.vertexId] ?: return@updateDrawingState drawingState
                val updatedVertex = vertex.copy(position = event.newPosition)

                drawingState.withVertex(updatedVertex)
            }
        }
    }

    private suspend fun handleVertexDragEnded(event: GeometryEvent.VertexDragEnded) {
        Logger.i { "✓ Vertex moved: ${event.vertexId} → (${event.finalPosition.x}, ${event.finalPosition.y})" }
        // Final position already applied in handleVertexDragged
    }

    private suspend fun handleVertexDeleted(event: GeometryEvent.VertexDeleted) {
        Logger.i { "✓ Vertex deleted: ${event.vertexId}" }

        stateManager.updateState { state ->
            state.updateDrawingState { drawingState ->
                // Remove vertex
                val remainingVertices = drawingState.vertices.filterKeys { it != event.vertexId }

                // Remove lines connected to deleted vertex
                val remainingLines =
                    drawingState.lines.filter { line ->
                        line.startVertexId != event.vertexId &&
                            line.endVertexId != event.vertexId
                    }

                drawingState.copy(
                    vertices = remainingVertices,
                    lines = remainingLines,
                    selectedVertexId = null, // Clear selection after delete
                )
            }
        }
    }

    private suspend fun handleToolModeChanged(event: GeometryEvent.ToolModeChanged) {
        Logger.i { "✓ Tool mode changed: ${event.mode}" }

        stateManager.updateState { state ->
            state.updateDrawingState { drawingState ->
                when (event.mode) {
                    com.roomplanner.data.models.ToolMode.DRAW -> {
                        // ✅ NEW: Transfer selection to active vertex when switching to DRAW
                        val newActiveVertexId = drawingState.selectedVertexId

                        if (newActiveVertexId != null) {
                            Logger.i {
                                "✓ Selection persisted: vertex $newActiveVertexId becomes active (origin point)"
                            }
                            drawingState
                                .copy(
                                    toolMode = com.roomplanner.data.models.ToolMode.DRAW,
                                    activeVertexId = newActiveVertexId, // ✅ Selected → Active
                                    selectedVertexId = null, // Clear selection (now active instead)
                                )
                        } else {
                            // No selection, just switch to DRAW mode
                            drawingState.withToolMode(com.roomplanner.data.models.ToolMode.DRAW)
                        }
                    }

                    com.roomplanner.data.models.ToolMode.SELECT -> {
                        // Switching to SELECT mode: clear active vertex
                        drawingState
                            .copy(
                                toolMode = com.roomplanner.data.models.ToolMode.SELECT,
                                activeVertexId = null, // No active vertex in SELECT mode
                            )
                    }
                }
            }
        }
    }

    // Line event handlers (Phase 1.5)

    private suspend fun handleLineSelected(event: GeometryEvent.LineSelected) {
        Logger.i { "✓ Line selected: ${event.lineId}" }

        stateManager.updateState { state ->
            state.updateDrawingState { drawingState ->
                drawingState.selectLine(event.lineId)
            }
        }
    }

    private suspend fun handleLineDeleted(event: GeometryEvent.LineDeleted) {
        Logger.i { "✓ Line deleted: ${event.lineId}" }

        stateManager.updateState { state ->
            state.updateDrawingState { drawingState ->
                // Remove line
                val remainingLines = drawingState.lines.filter { it.id != event.lineId }

                // Remove constraints associated with deleted line
                val remainingConstraints =
                    drawingState.constraints.filterValues { constraint ->
                        when (constraint) {
                            is com.roomplanner.data.models.Constraint.Distance ->
                                constraint.lineId != event.lineId
                            is com.roomplanner.data.models.Constraint.Angle ->
                                constraint.lineId1 != event.lineId && constraint.lineId2 != event.lineId
                            is com.roomplanner.data.models.Constraint.Parallel ->
                                constraint.lineId1 != event.lineId && constraint.lineId2 != event.lineId
                            is com.roomplanner.data.models.Constraint.Perpendicular ->
                                constraint.lineId1 != event.lineId && constraint.lineId2 != event.lineId
                        }
                    }

                drawingState.copy(
                    lines = remainingLines,
                    constraints = remainingConstraints,
                    selectedLineId = null, // Clear line selection after delete
                )
            }
        }
    }

    private suspend fun handleLineSplit(event: GeometryEvent.LineSplit) {
        Logger.w { "⚠ Line split not implemented yet (future feature)" }
        // Phase 2: Split line at point, creating new vertex and two new lines
    }
}
