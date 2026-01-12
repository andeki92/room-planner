package com.roomplanner.domain.geometry

import co.touchlab.kermit.Logger
import com.roomplanner.data.StateManager
import com.roomplanner.data.events.ConstraintEvent
import com.roomplanner.data.events.EventBus
import com.roomplanner.data.events.GeometryEvent
import com.roomplanner.data.models.Constraint
import com.roomplanner.domain.drawing.Line
import com.roomplanner.domain.drawing.Vertex
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
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

        // Phase 1.6: Auto-detect 90° angles and create perpendicular constraints
        // This makes rectangular shapes "sticky" - they stay rectangular when editing
        autoDetectRightAngles(line)
    }

    /**
     * Auto-detect if newly created line forms a 90° angle with adjacent lines.
     * If so, create automatic Perpendicular constraint to preserve the right angle.
     *
     * Phase 1.6: Angle preservation feature
     *
     * Algorithm:
     * 1. Find lines connected to the start vertex of new line
     * 2. For each connected line, calculate angle between them
     * 3. If angle is 85°-95° → snap to exactly 90°, create Perpendicular constraint
     * 4. Repeat for end vertex
     *
     * Design rationale:
     * - 85°-95° tolerance allows for imprecise touch input
     * - Auto-constraints are marked userSet=false (can be disabled in settings)
     * - Only creates constraint for FIRST right angle at each vertex (avoids duplicates)
     */
    private suspend fun autoDetectRightAngles(newLine: Line) {
        val state = stateManager.state.value
        val drawingState = state.projectDrawingState ?: return

        // Find lines connected to start vertex (excluding the new line itself)
        val startConnectedLines =
            drawingState.lines
                .filter { it.id != newLine.id }
                .filter { it.startVertexId == newLine.startVertexId || it.endVertexId == newLine.startVertexId }

        // Find lines connected to end vertex (excluding the new line itself)
        val endConnectedLines =
            drawingState.lines
                .filter { it.id != newLine.id }
                .filter { it.startVertexId == newLine.endVertexId || it.endVertexId == newLine.endVertexId }

        // Check angle at start vertex
        startConnectedLines.firstOrNull()?.let { adjacentLine ->
            checkAndCreatePerpendicularConstraint(newLine, adjacentLine, drawingState)
        }

        // Check angle at end vertex
        endConnectedLines.firstOrNull()?.let { adjacentLine ->
            checkAndCreatePerpendicularConstraint(newLine, adjacentLine, drawingState)
        }
    }

    /**
     * Check if two lines form a right angle (85°-95°). If so, create Perpendicular constraint.
     */
    private suspend fun checkAndCreatePerpendicularConstraint(
        line1: Line,
        line2: Line,
        drawingState: com.roomplanner.data.models.ProjectDrawingState
    ) {
        val geom1 = line1.getGeometry(drawingState.vertices)
        val geom2 = line2.getGeometry(drawingState.vertices)

        // Calculate angles (in radians)
        val angle1 = atan2(geom1.end.y - geom1.start.y, geom1.end.x - geom1.start.x)
        val angle2 = atan2(geom2.end.y - geom2.start.y, geom2.end.x - geom2.start.x)

        // Calculate relative angle (normalize to -π to π)
        var relativeAngle = angle2 - angle1
        while (relativeAngle > PI) relativeAngle -= 2 * PI
        while (relativeAngle < -PI) relativeAngle += 2 * PI

        // Check if close to 90° (±5°)
        val angleDegrees = abs(relativeAngle) * 180.0 / PI
        val isRightAngle =
            (angleDegrees >= 85.0 && angleDegrees <= 95.0) ||
                (angleDegrees >= 265.0 && angleDegrees <= 275.0) // 270° = -90°

        if (isRightAngle) {
            Logger.i { "✓ Right angle detected: $angleDegrees° → creating Perpendicular constraint" }

            // Check if perpendicular constraint already exists for these lines
            val existingConstraint =
                drawingState.constraints.values
                    .filterIsInstance<Constraint.Perpendicular>()
                    .any {
                        (it.lineId1 == line1.id && it.lineId2 == line2.id) ||
                            (it.lineId1 == line2.id && it.lineId2 == line1.id)
                    }

            if (!existingConstraint) {
                val perpendicularConstraint =
                    Constraint.Perpendicular(
                        lineId1 = line1.id,
                        lineId2 = line2.id,
                        enabled = true,
                        userSet = false, // Auto-generated
                    )

                // Emit event to add constraint
                eventBus.emit(ConstraintEvent.ConstraintAdded(perpendicularConstraint))
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
            val config = drawingState.drawingConfig

            // 1. Apply zoom delta with config-based limits
            val minZoom = config.minZoom(event.screenWidthPx)
            val maxZoom = config.maxZoom(event.screenWidthPx)
            val newZoom =
                (currentCamera.zoom * event.zoomDelta).coerceIn(minZoom, maxZoom)

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

            // 3. Clamp pan to project boundaries
            // The boundary is centered at (0, 0) in world space
            val boundary = config.projectBoundaryCm
            val halfBoundary = boundary / 2.0

            // Calculate the viewport size in world coordinates
            val viewportWidthWorld = event.screenWidthPx / newZoom
            val viewportHeightWorld = event.screenHeightPx / newZoom

            // Only clamp if viewport is smaller than boundary
            // If viewport is larger than boundary, center the boundary in the viewport
            val clampedPanX: Float
            val clampedPanY: Float

            if (viewportWidthWorld <= boundary) {
                // Viewport fits within boundary - clamp to edges
                // World coordinates visible: [(-panX/zoom) to (-panX/zoom + viewportWidth)]
                // Constraint: -panX/zoom >= -halfBoundary AND -panX/zoom + viewportWidth <= halfBoundary
                val minWorldX = -halfBoundary
                val maxWorldX = halfBoundary - viewportWidthWorld

                // Convert world constraints to pan constraints: pan = -world * zoom
                val minPanX = -maxWorldX * newZoom
                val maxPanX = -minWorldX * newZoom

                clampedPanX = newPanX.toDouble().coerceIn(minPanX, maxPanX).toFloat()
            } else {
                // Viewport is larger than boundary - center the boundary
                clampedPanX = (event.screenWidthPx / 2f)
            }

            if (viewportHeightWorld <= boundary) {
                // Viewport fits within boundary - clamp to edges
                val minWorldY = -halfBoundary
                val maxWorldY = halfBoundary - viewportHeightWorld

                // Convert world constraints to pan constraints: pan = -world * zoom
                val minPanY = -maxWorldY * newZoom
                val maxPanY = -minWorldY * newZoom

                clampedPanY = newPanY.toDouble().coerceIn(minPanY, maxPanY).toFloat()
            } else {
                // Viewport is larger than boundary - center the boundary
                clampedPanY = (event.screenHeightPx / 2f)
            }

            val newCamera =
                com.roomplanner.data.models.CameraTransform(
                    panX = clampedPanX,
                    panY = clampedPanY,
                    zoom = newZoom,
                )

            Logger.d { "  Camera updated: pan=($clampedPanX, $clampedPanY), zoom=$newZoom" }

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

    private fun handleVertexSelected(event: GeometryEvent.VertexSelected) {
        Logger.i { "✓ Vertex selected: ${event.vertexId} (now active for drawing)" }

        stateManager.updateState { state ->
            state.updateDrawingState { drawingState ->
                // Make this vertex active (blue) instead of selected (yellow)
                // This allows continuing to draw from this vertex
                drawingState.copy(
                    activeVertexId = event.vertexId,
                    selectedVertexId = null, // Clear yellow selection
                )
            }
        }
    }

    private fun handleSelectionCleared() {
        Logger.d { "→ Selection cleared" }

        stateManager.updateState { state ->
            state.updateDrawingState { drawingState ->
                drawingState.clearSelection()
            }
        }
    }

    private fun handleVertexDragStarted(event: GeometryEvent.VertexDragStarted) {
        Logger.d { "→ Drag started: ${event.vertexId}" }
        // Drag state tracked in DrawingCanvas (local UI state)
    }

    private fun handleVertexDragged(event: GeometryEvent.VertexDragged) {
        stateManager.updateState { state ->
            state.updateDrawingState { drawingState ->
                val vertex = drawingState.vertices[event.vertexId] ?: return@updateDrawingState drawingState
                val updatedVertex = vertex.copy(position = event.newPosition)

                drawingState.withVertex(updatedVertex)
            }
        }
    }

    private fun handleVertexDragEnded(event: GeometryEvent.VertexDragEnded) {
        Logger.i { "✓ Vertex moved: ${event.vertexId} → (${event.finalPosition.x}, ${event.finalPosition.y})" }
        // Final position already applied in handleVertexDragged
    }

    private fun handleVertexDeleted(event: GeometryEvent.VertexDeleted) {
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

    private fun handleLineSelected(event: GeometryEvent.LineSelected) {
        Logger.i { "✓ Line selected: ${event.lineId}" }

        stateManager.updateState { state ->
            state.updateDrawingState { drawingState ->
                drawingState.selectLine(event.lineId)
            }
        }
    }

    private fun handleLineDeleted(event: GeometryEvent.LineDeleted) {
        Logger.i { "✓ Line deleted: ${event.lineId}" }

        stateManager.updateState { state ->
            state.updateDrawingState { drawingState ->
                // Remove line
                val remainingLines = drawingState.lines.filter { it.id != event.lineId }

                // Remove constraints associated with deleted line
                val remainingConstraints =
                    drawingState.constraints.filterValues { constraint ->
                        when (constraint) {
                            is Constraint.Distance ->
                                constraint.lineId != event.lineId

                            is Constraint.Angle ->
                                constraint.lineId1 != event.lineId && constraint.lineId2 != event.lineId

                            is Constraint.Parallel ->
                                constraint.lineId1 != event.lineId && constraint.lineId2 != event.lineId

                            is Constraint.Perpendicular ->
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

    private fun handleLineSplit(event: GeometryEvent.LineSplit) {
        Logger.w { "⚠ Line split not implemented yet (future feature)" }
        // Phase 2: Split line at point, creating new vertex and two new lines
    }
}
