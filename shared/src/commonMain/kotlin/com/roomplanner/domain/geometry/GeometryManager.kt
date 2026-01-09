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
}
