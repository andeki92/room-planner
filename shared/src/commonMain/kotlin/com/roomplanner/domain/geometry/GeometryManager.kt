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
        }
    }

    private suspend fun handlePointPlaced(event: GeometryEvent.PointPlaced) {
        Logger.d { "→ PointPlaced at (${event.position.x}, ${event.position.y}), snappedTo=${event.snappedTo}" }

        val state = stateManager.state.value

        // Check if snapping to existing vertex
        val vertex =
            if (event.snappedTo != null) {
                // Reuse existing vertex
                state.vertices[event.snappedTo]?.also {
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
                state.withVertex(vertex)
            }
        }

        // If continuing from previous vertex, create line
        val previousVertex = state.getActiveVertex()
        if (previousVertex != null && previousVertex.id != vertex.id) {
            createLine(previousVertex, vertex)
        } else if (event.snappedTo == null) {
            // First vertex or same vertex - just set as active
            stateManager.updateState { state ->
                state.copy(activeVertexId = vertex.id)
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
            state.withLine(line)
        }
    }
}
