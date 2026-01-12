package com.roomplanner.ui.gestures

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import co.touchlab.kermit.Logger
import com.roomplanner.data.events.EventBus
import com.roomplanner.data.events.GeometryEvent
import com.roomplanner.data.models.CameraTransform
import com.roomplanner.data.models.ProjectDrawingState
import com.roomplanner.data.models.SnapResult
import com.roomplanner.data.models.SnapResultWithGuidelines
import com.roomplanner.domain.geometry.Point2
import com.roomplanner.domain.snapping.SmartSnapSystem
import com.roomplanner.ui.utils.CoordinateConversion
import com.roomplanner.ui.utils.HapticFeedback
import com.roomplanner.ui.utils.HitTesting

/**
 * Unified gesture handler for context-aware gestures.
 *
 * Replaces separate DRAW/SELECT tool modes with intelligent gesture detection:
 *
 * | Gesture     | On Vertex (no active)  | On Vertex (has active) | On Empty (no active) | On Empty (has active)   |
 * |-------------|------------------------|------------------------|----------------------|-------------------------|
 * | Tap         | Select (activate)      | Connect (create line)  | Place first vertex   | Deselect                |
 * | Drag        | Move vertex            | Move vertex            | (nothing)            | Preview line → place    |
 * | Long Press  | Vertex radial menu     | Vertex radial menu     | (nothing)            | (nothing)               |
 *
 * On Line:
 * | Gesture     | Behavior               |
 * |-------------|------------------------|
 * | Long Press  | Line radial menu       |
 *
 * Design rationale:
 * - No mode switching required - gesture meaning depends on touch target + state
 * - Tap to connect vertices makes closing shapes intuitive
 * - Tap on empty deselects (drag required to place subsequent vertices)
 * - Single source of truth for gesture behavior
 */
class UnifiedGestureHandler {
    // Track drag state
    private var draggedVertexId: String? = null
    private var isDrawingNewLine: Boolean = false

    /**
     * Handle press (finger down).
     *
     * Context-aware behavior:
     * - On vertex: Start vertex drag
     * - On empty space with active vertex: Start line preview
     * - On empty space without active vertex: Wait for release to place first vertex
     */
    suspend fun handlePress(
        screenPosition: Offset,
        drawingState: ProjectDrawingState,
        camera: CameraTransform,
        density: Density,
        eventBus: EventBus,
        onPreview: (Point2?) -> Unit,
        onSnapHint: (SnapResultWithGuidelines?) -> Unit,
    ) {
        val worldPoint = CoordinateConversion.screenToWorld(screenPosition, camera)

        // Check if pressing on a vertex
        val hitVertexId =
            HitTesting.findVertexAt(
                tapScreen = screenPosition,
                drawingState = drawingState,
                camera = camera,
                density = density,
                config = drawingState.drawingConfig,
            )

        if (hitVertexId != null) {
            // Start dragging this vertex
            draggedVertexId = hitVertexId
            isDrawingNewLine = false

            val vertex = drawingState.vertices[hitVertexId]!!
            eventBus.emit(
                GeometryEvent.VertexDragStarted(
                    vertexId = hitVertexId,
                    startPosition = vertex.position,
                ),
            )

            Logger.d { "→ Press on vertex $hitVertexId - starting drag" }
            return
        }

        // Pressing on empty space
        if (drawingState.activeVertexId != null) {
            // Has active vertex - prepare for potential line drawing
            // Don't show preview yet - wait for drag to start (avoids flash on tap-to-deselect)
            isDrawingNewLine = true
            draggedVertexId = null

            Logger.d { "→ Press on empty (has active vertex) - ready for line drawing" }
        } else {
            // No active vertex - will place first vertex on release/tap
            isDrawingNewLine = false
            draggedVertexId = null
            Logger.d { "→ Press on empty (no active vertex)" }
        }
    }

    /**
     * Handle drag (finger moves).
     *
     * Context-aware behavior:
     * - Dragging vertex: Move vertex with snap
     * - Drawing new line: Update preview with snap
     */
    suspend fun handleDrag(
        screenPosition: Offset,
        dragDelta: Offset,
        drawingState: ProjectDrawingState,
        camera: CameraTransform,
        density: Density,
        eventBus: EventBus,
        onPreview: (Point2?) -> Unit,
        onSnapHint: (SnapResultWithGuidelines?) -> Unit,
    ) {
        val worldPoint = CoordinateConversion.screenToWorld(screenPosition, camera)

        // Case 1: Dragging a vertex
        val vertexId = draggedVertexId
        if (vertexId != null) {
            handleVertexDrag(
                vertexId = vertexId,
                screenPosition = screenPosition,
                dragDelta = dragDelta,
                drawingState = drawingState,
                camera = camera,
                density = density,
                eventBus = eventBus,
                onSnapHint = onSnapHint,
            )
            return
        }

        // Case 2: Drawing new line (preview)
        if (isDrawingNewLine && drawingState.activeVertexId != null) {
            // Update preview to follow cursor
            onPreview(worldPoint)

            // Calculate snap with guidelines (grid snap disabled - only smart snapping)
            val snapResult =
                SmartSnapSystem.calculateSnapWithGuidelines(
                    cursorWorld = worldPoint,
                    cursorScreen = screenPosition,
                    drawingState = drawingState,
                    camera = camera,
                    density = density,
                    enableGridSnap = false,
                )

            onSnapHint(if (snapResult.guidelines.isNotEmpty()) snapResult else null)
        }
    }

    /**
     * Handle vertex drag with constraint propagation.
     */
    private suspend fun handleVertexDrag(
        vertexId: String,
        screenPosition: Offset,
        dragDelta: Offset,
        drawingState: ProjectDrawingState,
        camera: CameraTransform,
        density: Density,
        eventBus: EventBus,
        onSnapHint: (SnapResultWithGuidelines?) -> Unit,
    ) {
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

        // Calculate snap for vertex being dragged (grid snap disabled - only smart snapping)
        val snapResult =
            SmartSnapSystem.calculateSnapWithGuidelines(
                cursorWorld = newPosition,
                cursorScreen = screenPosition,
                drawingState = drawingState,
                camera = camera,
                density = density,
                enableGridSnap = false,
            )

        onSnapHint(if (snapResult.guidelines.isNotEmpty()) snapResult else null)

        // Use snap position if available
        val finalPosition =
            if (snapResult.guidelines.isNotEmpty()) {
                snapResult.snapPosition
            } else {
                newPosition
            }

        // Calculate drag delta for constraint propagation
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

        // Propagate to constrained connected vertices
        propagateConstraints(
            vertexId = vertexId,
            actualDelta = actualDelta,
            drawingState = drawingState,
            eventBus = eventBus,
        )
    }

    /**
     * Propagate drag to connected vertices with distance constraints.
     */
    private suspend fun propagateConstraints(
        vertexId: String,
        actualDelta: Point2,
        drawingState: ProjectDrawingState,
        eventBus: EventBus,
    ) {
        val connectedLines = drawingState.getLinesConnectedToVertex(vertexId)

        connectedLines.forEach { line ->
            val distanceConstraint =
                drawingState.constraints.values
                    .filterIsInstance<com.roomplanner.data.models.Constraint.Distance>()
                    .find { it.lineId == line.id && it.enabled }

            if (distanceConstraint != null) {
                val otherVertexId =
                    if (line.startVertexId == vertexId) {
                        line.endVertexId
                    } else {
                        line.startVertexId
                    }

                val otherVertex = drawingState.vertices[otherVertexId]
                if (otherVertex != null && !otherVertex.fixed) {
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

    /**
     * Handle release (finger up after drag).
     *
     * Context-aware behavior:
     * - Was dragging vertex: Finalize vertex position
     * - Was drawing line: Place new vertex
     */
    suspend fun handleRelease(
        screenPosition: Offset,
        drawingState: ProjectDrawingState,
        camera: CameraTransform,
        density: Density,
        eventBus: EventBus,
        onPreview: (Point2?) -> Unit,
        onSnapHint: (SnapResultWithGuidelines?) -> Unit,
    ) {
        // Case 1: Was dragging a vertex
        val vertexId = draggedVertexId
        if (vertexId != null) {
            val finalVertex = drawingState.vertices[vertexId]
            if (finalVertex != null) {
                eventBus.emit(
                    GeometryEvent.VertexDragEnded(
                        vertexId = vertexId,
                        finalPosition = finalVertex.position,
                    ),
                )
                Logger.i { "✓ Vertex drag ended: $vertexId" }
            }

            draggedVertexId = null
            onSnapHint(null)
            return
        }

        // Case 2: Was drawing new line
        if (isDrawingNewLine) {
            placeVertex(
                screenPosition = screenPosition,
                drawingState = drawingState,
                camera = camera,
                density = density,
                eventBus = eventBus,
                isFromTap = false,
            )

            isDrawingNewLine = false
            onPreview(null)
            onSnapHint(null)
        }
    }

    /**
     * Handle tap (quick press-release, < 200ms, < 10px movement).
     *
     * Context-aware behavior:
     * - On vertex (no active): Select it (make active for drawing)
     * - On vertex (has active): Create line to complete/connect shape
     * - On empty space (no active): Place first vertex
     * - On empty space (has active): Deselect active vertex
     */
    suspend fun handleTap(
        screenPosition: Offset,
        drawingState: ProjectDrawingState,
        camera: CameraTransform,
        density: Density,
        eventBus: EventBus,
        onPreview: (Point2?) -> Unit,
        onSnapHint: (SnapResultWithGuidelines?) -> Unit,
    ) {
        val hasActiveVertex = drawingState.activeVertexId != null

        // Check if tapped on a vertex
        val tappedVertexId =
            HitTesting.findVertexAt(
                tapScreen = screenPosition,
                drawingState = drawingState,
                camera = camera,
                density = density,
                config = drawingState.drawingConfig,
            )

        if (tappedVertexId != null) {
            if (hasActiveVertex && tappedVertexId != drawingState.activeVertexId) {
                // Has active vertex and tapped different vertex - create line to connect
                val tappedVertex = drawingState.vertices[tappedVertexId]
                if (tappedVertex != null) {
                    HapticFeedback.light()
                    eventBus.emit(
                        GeometryEvent.PointPlaced(
                            position = tappedVertex.position,
                            snappedTo = tappedVertexId,
                        ),
                    )
                    Logger.i { "✓ Tap on vertex: connected to $tappedVertexId" }
                }
            } else {
                // No active vertex - select this vertex
                HapticFeedback.light()
                eventBus.emit(GeometryEvent.VertexSelected(vertexId = tappedVertexId))
                Logger.i { "✓ Tap on vertex: $tappedVertexId (now active)" }
            }
            return
        }

        // Tap on empty space
        if (hasActiveVertex) {
            // Has active vertex - deselect it
            HapticFeedback.light()
            eventBus.emit(GeometryEvent.SelectionCleared)
            Logger.i { "✓ Tap on empty: deselected active vertex" }
        } else {
            // No active vertex - place first vertex
            placeVertex(
                screenPosition = screenPosition,
                drawingState = drawingState,
                camera = camera,
                density = density,
                eventBus = eventBus,
                isFromTap = true,
            )
        }

        onPreview(null)
        onSnapHint(null)
    }

    /**
     * Handle long press (> 300ms, < 10px movement).
     *
     * Context-aware behavior:
     * - On vertex: Show vertex radial menu
     * - On line: Show line radial menu
     * - On empty space: Nothing
     *
     * @return Pair of (vertexId, lineId) - one will be set if menu should show
     */
    suspend fun handleLongPress(
        screenPosition: Offset,
        drawingState: ProjectDrawingState,
        camera: CameraTransform,
        density: Density,
        eventBus: EventBus,
        onVertexMenu: (String) -> Unit,
        onLineMenu: (String) -> Unit,
    ) {
        // Check for vertex long press
        val vertexId =
            HitTesting.findVertexAt(
                tapScreen = screenPosition,
                drawingState = drawingState,
                camera = camera,
                density = density,
                config = drawingState.drawingConfig,
            )

        if (vertexId != null) {
            HapticFeedback.medium()
            onVertexMenu(vertexId)
            Logger.d { "→ Long press on vertex $vertexId - showing menu" }
            return
        }

        // Check for line long press
        val lineId =
            HitTesting.findLineAt(
                tapScreen = screenPosition,
                drawingState = drawingState,
                camera = camera,
                density = density,
                config = drawingState.drawingConfig,
            )

        if (lineId != null) {
            HapticFeedback.medium()
            onLineMenu(lineId)
            Logger.d { "→ Long press on line $lineId - showing menu" }
            return
        }

        // Long press on empty space - do nothing
        Logger.d { "→ Long press on empty space" }
    }

    /**
     * Handle gesture cancellation (e.g., multi-touch detected).
     */
    suspend fun handleCancel(
        onPreview: (Point2?) -> Unit,
        onSnapHint: (SnapResultWithGuidelines?) -> Unit,
    ) {
        draggedVertexId = null
        isDrawingNewLine = false
        onPreview(null)
        onSnapHint(null)
    }

    /**
     * Place a new vertex at the given position with snap.
     */
    private suspend fun placeVertex(
        screenPosition: Offset,
        drawingState: ProjectDrawingState,
        camera: CameraTransform,
        density: Density,
        eventBus: EventBus,
        isFromTap: Boolean,
    ) {
        val worldPoint = CoordinateConversion.screenToWorld(screenPosition, camera)

        // Only enable grid snap for first vertex placement
        val isFirstVertex = drawingState.activeVertexId == null

        // Calculate snap with guidelines (grid snap only for first vertex)
        val snapResult =
            SmartSnapSystem.calculateSnapWithGuidelines(
                cursorWorld = worldPoint,
                cursorScreen = screenPosition,
                drawingState = drawingState,
                camera = camera,
                density = density,
                enableGridSnap = isFirstVertex,
            )

        val finalPosition =
            if (snapResult.guidelines.isNotEmpty()) {
                snapResult.snapPosition
            } else {
                worldPoint
            }

        val snapType = if (snapResult.guidelines.isNotEmpty()) "snapped" else "exact"
        val source = if (isFromTap) "tap" else "drag"

        Logger.i {
            if (isFirstVertex) {
                "✓ First vertex placed via $source ($snapType): (${finalPosition.x}, ${finalPosition.y})"
            } else {
                "✓ Vertex placed via $source ($snapType): (${finalPosition.x}, ${finalPosition.y})"
            }
        }

        // Check if snapped to vertex for closing shapes
        val snappedToVertexId =
            snapResult.guidelines
                .filterIsInstance<SnapResult.Vertex>()
                .firstOrNull()
                ?.vertexId

        eventBus.emit(
            GeometryEvent.PointPlaced(
                position = finalPosition,
                snappedTo = snappedToVertexId,
            ),
        )
    }
}
