package com.roomplanner.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import co.touchlab.kermit.Logger
import com.roomplanner.data.events.EventBus
import com.roomplanner.data.events.GeometryEvent
import com.roomplanner.data.models.AppState
import com.roomplanner.data.models.CameraTransform
import com.roomplanner.data.models.SnapResult
import com.roomplanner.domain.geometry.Point2
import com.roomplanner.domain.snapping.SmartSnapSystem
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * DrawingCanvas - Interactive canvas for CAD drawing with touch input.
 *
 * Features (Phase 1.1):
 * - Touch gesture detection (tap to place points)
 * - Grid snapping
 * - Grid background rendering
 * - Vertex rendering (green circles)
 * - Line rendering (black lines between vertices)
 * - Screen-to-world coordinate conversion
 *
 * Future phases:
 * - Pan/zoom gestures (Phase 1.2)
 * - Smart snapping (vertex, perpendicular) (Phase 1.3)
 * - Selection and editing (Phase 1.4)
 *
 * Design rationale:
 * - Declarative rendering with Compose Canvas
 * - Event-driven: emits GeometryEvent, doesn't mutate state directly
 * - Skia-backed for hardware acceleration
 * - Immutable state for reactive rendering
 */
@Composable
fun DrawingCanvas(
    state: AppState,
    eventBus: EventBus,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    val drawingState = state.projectDrawingState
    val density = LocalDensity.current // Extract density for dp→px conversion

    // Early return if no drawing state
    if (drawingState == null) {
        Logger.w { "⚠ DrawingCanvas: No drawing state available" }
        return
    }

    // CRITICAL: Store camera in a mutable ref that we update on every recomposition
    // This allows the gesture handler to read the LATEST camera value
    val cameraRef = remember { androidx.compose.runtime.mutableStateOf(drawingState.cameraTransform) }
    val snapSettingsRef = remember { androidx.compose.runtime.mutableStateOf(drawingState.snapSettings) }

    // Update refs on every recomposition (when state changes)
    cameraRef.value = drawingState.cameraTransform
    snapSettingsRef.value = drawingState.snapSettings

    // Context menu state (Phase 1.4)
    val contextMenuPosition = remember { androidx.compose.runtime.mutableStateOf<Offset?>(null) }
    val contextMenuVertexId = remember { androidx.compose.runtime.mutableStateOf<String?>(null) }

    Canvas(
        modifier =
            modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { screenOffset ->
                            coroutineScope.launch {
                                // Read from ref (always has latest value)
                                val camera = cameraRef.value

                                // Dismiss context menu if open
                                if (contextMenuPosition.value != null) {
                                    contextMenuPosition.value = null
                                    contextMenuVertexId.value = null
                                    return@launch
                                }

                                Logger.d { "→ Tap at screen (${screenOffset.x}, ${screenOffset.y})" }
                                Logger.d { "  Camera: pan=(${camera.panX}, ${camera.panY}), zoom=${camera.zoom}" }
                                Logger.d { "  Tool mode: ${drawingState.toolMode}" }

                                // Tool mode-based behavior (Phase 1.4b)
                                when (drawingState.toolMode) {
                                    com.roomplanner.data.models.ToolMode.DRAW -> {
                                        // DRAW MODE: Always place new vertex (ignore existing vertices)
                                        val worldPoint = screenToWorld(screenOffset, camera)

                                        // Apply smart snap (vertex > edge > perpendicular > grid)
                                        val snapResult =
                                            SmartSnapSystem.calculateSnap(
                                                cursorWorld = worldPoint,
                                                cursorScreen = screenOffset,
                                                drawingState = drawingState,
                                                camera = camera,
                                                density = density,
                                            )

                                        val (snappedPoint, snappedTo) =
                                            when (snapResult) {
                                                is SnapResult.None -> Pair(worldPoint, null)
                                                is SnapResult.Grid -> Pair(snapResult.position, null)
                                                is SnapResult.Vertex -> Pair(snapResult.position, snapResult.vertexId)
                                                is SnapResult.Edge -> Pair(snapResult.position, null)
                                                is SnapResult.Perpendicular -> Pair(snapResult.position, null)
                                            }

                                        Logger.d {
                                            "  World: (${worldPoint.x}, ${worldPoint.y}) → Snapped: (${snappedPoint.x}, ${snappedPoint.y})"
                                        }
                                        if (snapResult is SnapResult.Vertex) {
                                            Logger.i { "✓ Snapped to vertex: ${snapResult.vertexId}" }
                                        }

                                        eventBus.emit(
                                            GeometryEvent.PointPlaced(
                                                position = snappedPoint,
                                                snappedTo = snappedTo,
                                            ),
                                        )
                                    }

                                    com.roomplanner.data.models.ToolMode.SELECT -> {
                                        // SELECT MODE: Check for vertex selection
                                        val tappedVertexId =
                                            findTappedVertex(
                                                tapScreen = screenOffset,
                                                drawingState = drawingState,
                                                camera = camera,
                                                density = density,
                                                config = drawingState.drawingConfig,
                                            )

                                        if (tappedVertexId != null) {
                                            // Select vertex
                                            Logger.d { "→ Tapped vertex: $tappedVertexId" }
                                            eventBus.emit(GeometryEvent.VertexSelected(tappedVertexId))
                                        } else if (drawingState.selectedVertexId != null) {
                                            // Clear selection
                                            Logger.d { "→ Cleared selection" }
                                            eventBus.emit(GeometryEvent.SelectionCleared)
                                        }
                                        // Else: tap on empty space with no selection → do nothing
                                    }
                                }
                            }
                        },
                        onLongPress = { screenOffset ->
                            // Phase 1.4b: Long-press only works in SELECT mode
                            if (drawingState.toolMode !=
                                com.roomplanner.data.models.ToolMode.SELECT
                            ) {
                                return@detectTapGestures
                            }

                            val camera = cameraRef.value
                            val longPressedVertexId =
                                findTappedVertex(
                                    tapScreen = screenOffset,
                                    drawingState = drawingState,
                                    camera = camera,
                                    density = density,
                                    config = drawingState.drawingConfig,
                                )

                            if (longPressedVertexId != null && drawingState.isVertexSelected(longPressedVertexId)) {
                                Logger.d { "→ Long-press on selected vertex: $longPressedVertexId" }
                                contextMenuPosition.value = screenOffset
                                contextMenuVertexId.value = longPressedVertexId
                            }
                        },
                    )
                }.pointerInput(Unit) {
                    detectTransformGestures(
                        panZoomLock = true, // Disable rotation
                    ) { centroid, pan, zoom, _ ->
                        coroutineScope.launch {
                            handleTransform(pan, zoom, centroid, eventBus)
                        }
                    }
                }.pointerInput(Unit) {
                    // Phase 1.4b: Drag gesture only works in SELECT mode
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            // Drag only works in SELECT mode
                            if (drawingState.toolMode !=
                                com.roomplanner.data.models.ToolMode.SELECT
                            ) {
                                return@detectDragGestures
                            }

                            // Check if dragging a selected vertex
                            val camera = cameraRef.value
                            val draggedVertexId =
                                findTappedVertex(
                                    tapScreen = startOffset,
                                    drawingState = drawingState,
                                    camera = camera,
                                    density = density,
                                    config = drawingState.drawingConfig,
                                )

                            if (draggedVertexId != null && drawingState.isVertexSelected(draggedVertexId)) {
                                // Start dragging selected vertex
                                val vertex = drawingState.vertices[draggedVertexId]!!
                                coroutineScope.launch {
                                    eventBus.emit(
                                        GeometryEvent.VertexDragStarted(
                                            vertexId = draggedVertexId,
                                            startPosition = vertex.position,
                                        ),
                                    )
                                }
                            }
                        },
                        onDrag = { change, dragAmount ->
                            val camera = cameraRef.value
                            val selectedVertexId = drawingState.selectedVertexId ?: return@detectDragGestures

                            // Convert screen drag delta → world space
                            val dragWorld =
                                Point2(
                                    x = (dragAmount.x / camera.zoom).toDouble(),
                                    y = (dragAmount.y / camera.zoom).toDouble(),
                                )

                            val currentVertex = drawingState.vertices[selectedVertexId] ?: return@detectDragGestures
                            val newPosition =
                                Point2(
                                    x = currentVertex.position.x + dragWorld.x,
                                    y = currentVertex.position.y + dragWorld.y,
                                )

                            coroutineScope.launch {
                                eventBus.emit(
                                    GeometryEvent.VertexDragged(
                                        vertexId = selectedVertexId,
                                        newPosition = newPosition,
                                    ),
                                )
                            }
                        },
                        onDragEnd = {
                            val selectedVertexId = drawingState.selectedVertexId ?: return@detectDragGestures
                            val finalVertex = drawingState.vertices[selectedVertexId] ?: return@detectDragGestures

                            coroutineScope.launch {
                                eventBus.emit(
                                    GeometryEvent.VertexDragEnded(
                                        vertexId = selectedVertexId,
                                        finalPosition = finalVertex.position,
                                    ),
                                )
                            }
                        },
                    )
                },
    ) {
        // 1. Draw grid background
        drawGrid(drawingState.cameraTransform, drawingState.snapSettings.gridSize, drawingState.drawingConfig, density)

        // 2. Draw lines (walls)
        drawLines(drawingState, drawingState.cameraTransform, drawingState.drawingConfig, density)

        // 3. Draw vertices (snap points)
        drawVertices(drawingState, drawingState.cameraTransform, drawingState.drawingConfig, density)
    }

    // Overlay context menu (outside Canvas - Phase 1.4)
    if (contextMenuPosition.value != null && contextMenuVertexId.value != null) {
        VertexContextMenu(
            position = contextMenuPosition.value!!,
            onDelete = {
                coroutineScope.launch {
                    Logger.d { "→ Delete requested for vertex: ${contextMenuVertexId.value}" }
                    eventBus.emit(GeometryEvent.VertexDeleted(contextMenuVertexId.value!!))
                    contextMenuPosition.value = null
                    contextMenuVertexId.value = null
                }
            },
            onDismiss = {
                Logger.d { "→ Context menu dismissed" }
                contextMenuPosition.value = null
                contextMenuVertexId.value = null
            },
        )
    }
}

/**
 * Find vertex near tap position (screen-space distance).
 * Uses density-independent selection radius (44dp).
 * Phase 1.4: Selection hit testing
 *
 * @return Vertex ID if found, null otherwise
 */
private fun findTappedVertex(
    tapScreen: Offset,
    drawingState: com.roomplanner.data.models.ProjectDrawingState,
    camera: CameraTransform,
    density: Density,
    config: com.roomplanner.data.models.DrawingConfig,
): String? {
    val selectionRadius = config.selectionRadiusPx(density)

    var nearestVertexId: String? = null
    var minDistance = Float.MAX_VALUE

    drawingState.vertices.values.forEach { vertex ->
        val vertexScreen = worldToScreen(vertex.position, camera)
        val distance = distanceScreen(tapScreen, vertexScreen)

        if (distance < selectionRadius && distance < minDistance) {
            minDistance = distance
            nearestVertexId = vertex.id
        }
    }

    return nearestVertexId
}

/**
 * Calculate screen-space distance between two points.
 * Used for consistent tap detection across zoom levels.
 */
private fun distanceScreen(
    a: Offset,
    b: Offset,
): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    return kotlin.math.sqrt(dx * dx + dy * dy)
}

/**
 * Handle tap gesture: convert to world coordinates, apply snap, emit event.
 */
private suspend fun handleTap(
    screenOffset: Offset,
    drawingState: com.roomplanner.data.models.ProjectDrawingState,
    eventBus: EventBus,
) {
    Logger.d { "→ Tap detected at screen (${screenOffset.x}, ${screenOffset.y})" }

    // Convert screen coordinates to world coordinates
    val worldPoint = screenToWorld(screenOffset, drawingState.cameraTransform)

    // Apply grid snap
    val snappedPoint = snapToGrid(worldPoint, drawingState.snapSettings.gridSize)

    Logger.d {
        "  Camera: pan=(${drawingState.cameraTransform.panX}, ${drawingState.cameraTransform.panY}), zoom=${drawingState.cameraTransform.zoom}"
    }
    Logger.d { "  World: (${worldPoint.x}, ${worldPoint.y}) → Snapped: (${snappedPoint.x}, ${snappedPoint.y})" }

    // Emit event (GeometryManager will handle state update)
    eventBus.emit(
        GeometryEvent.PointPlaced(
            position = snappedPoint,
            snappedTo = null, // Phase 1.3 will add vertex snapping
        ),
    )
}

/**
 * Handle pan/zoom transform gesture: emit camera event.
 * Phase 1.2: Two-finger pan and pinch-to-zoom
 */
private suspend fun handleTransform(
    panDelta: Offset,
    zoomDelta: Float,
    centroid: Offset,
    eventBus: EventBus,
) {
    // Only emit if there's actual change
    if (panDelta != Offset.Zero || zoomDelta != 1f) {
        eventBus.emit(
            GeometryEvent.CameraTransformed(
                panDelta = panDelta,
                zoomDelta = zoomDelta,
                zoomCenter = centroid,
            ),
        )
    }
}

/**
 * Convert screen coordinates to world coordinates.
 * Inverse of worldToScreen: world = (screen - pan) / zoom
 *
 * Phase 1.1: No pan/zoom (identity transform)
 * Phase 1.2: Apply inverse camera transform
 */
private fun screenToWorld(
    screenOffset: Offset,
    camera: CameraTransform,
): Point2 {
    // Inverse transform: subtract pan, then divide by zoom
    val worldX = ((screenOffset.x - camera.panX) / camera.zoom).toDouble()
    val worldY = ((screenOffset.y - camera.panY) / camera.zoom).toDouble()
    return Point2(worldX, worldY)
}

/**
 * Convert world coordinates to screen coordinates.
 * Apply camera transform: translate(pan) -> scale(zoom)
 *
 * Formula: screen = (world * zoom) + pan
 */
private fun worldToScreen(
    worldPoint: Point2,
    camera: CameraTransform,
): Offset {
    val x = (worldPoint.x.toFloat() * camera.zoom) + camera.panX
    val y = (worldPoint.y.toFloat() * camera.zoom) + camera.panY
    return Offset(x, y)
}

/**
 * Overload for Offset input (convenience)
 */
private fun worldToScreen(
    worldOffset: Offset,
    camera: CameraTransform,
): Offset {
    val x = (worldOffset.x * camera.zoom) + camera.panX
    val y = (worldOffset.y * camera.zoom) + camera.panY
    return Offset(x, y)
}

/**
 * Snap point to grid.
 * Rounds coordinates to nearest grid increment.
 */
private fun snapToGrid(
    point: Point2,
    gridSize: Double,
): Point2 {
    val x = (point.x / gridSize).roundToInt() * gridSize
    val y = (point.y / gridSize).roundToInt() * gridSize
    return Point2(x, y)
}

/**
 * Draw grid background for visual reference.
 * Phase 1.2: Apply camera transform (grid moves/scales with viewport)
 * Phase 1.3: Apply density for consistent line width across devices
 */
private fun DrawScope.drawGrid(
    camera: CameraTransform,
    gridSize: Double,
    config: com.roomplanner.data.models.DrawingConfig,
    density: Density,
) {
    val gridColor = config.gridColorCompose()
    val gridSizeScreen = (gridSize.toFloat() * camera.zoom) // Grid size in screen pixels

    // Calculate grid offset (so grid aligns with world origin)
    // World origin (0,0) maps to screen (panX, panY)
    // We want grid lines at world 0, ±gridSize, ±2*gridSize, ...
    // which map to screen panX, panX±gridSizeScreen, panX±2*gridSizeScreen, ...
    // Find first visible grid line (screen x >= 0)
    val gridOffsetX = ((camera.panX % gridSizeScreen) + gridSizeScreen) % gridSizeScreen
    val gridOffsetY = ((camera.panY % gridSizeScreen) + gridSizeScreen) % gridSizeScreen

    // Draw vertical grid lines
    var x = gridOffsetX
    while (x < size.width) {
        drawLine(
            color = gridColor,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = config.gridLineWidthPx(density),
        )
        x += gridSizeScreen
    }

    // Draw horizontal grid lines
    var y = gridOffsetY
    while (y < size.height) {
        drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = config.gridLineWidthPx(density),
        )
        y += gridSizeScreen
    }
}

/**
 * Draw all lines (walls).
 * Phase 1.2: Transform world coordinates to screen space
 * Phase 1.3: Apply density for consistent line width across devices
 */
private fun DrawScope.drawLines(
    drawingState: com.roomplanner.data.models.ProjectDrawingState,
    camera: CameraTransform,
    config: com.roomplanner.data.models.DrawingConfig,
    density: Density,
) {
    drawingState.lines.forEach { line ->
        // Convert world coordinates to screen coordinates
        val start = worldToScreen(line.geometry.start, camera)
        val end = worldToScreen(line.geometry.end, camera)

        drawLine(
            color = config.lineColorCompose(),
            start = start,
            end = end,
            strokeWidth = config.lineStrokeWidthPx(density),
        )
    }
}

/**
 * Draw all vertices (snap points).
 * Phase 1.2: Transform world coordinates to screen space
 * Phase 1.4: Selection indicators (yellow highlight + glow ring)
 * Active vertex (last placed) is highlighted in blue.
 * Selected vertex (Phase 1.4) is highlighted in yellow.
 */
private fun DrawScope.drawVertices(
    drawingState: com.roomplanner.data.models.ProjectDrawingState,
    camera: CameraTransform,
    config: com.roomplanner.data.models.DrawingConfig,
    density: Density,
) {
    drawingState.vertices.values.forEach { vertex ->
        val isActive = vertex.id == drawingState.activeVertexId
        val isSelected = drawingState.isVertexSelected(vertex.id)

        // Color priority: fixed > selected > active > normal
        val color =
            when {
                vertex.fixed -> config.vertexColorFixedCompose()
                isSelected -> config.selectedVertexColorCompose() // Phase 1.4: Yellow
                isActive -> config.vertexColorActiveCompose() // Blue
                else -> config.vertexColorNormalCompose() // Green
            }

        // Convert world coordinates to screen coordinates
        val screenPosition = worldToScreen(vertex.position, camera)

        // Draw selection glow ring (if selected - Phase 1.4)
        if (isSelected) {
            drawCircle(
                color = config.selectedVertexColorCompose().copy(alpha = config.selectionGlowAlpha),
                radius = config.selectionIndicatorRadiusPx(density),
                center = screenPosition,
            )
        }

        drawCircle(
            color = color,
            radius = config.vertexRadiusPx(density),
            center = screenPosition,
        )

        // Draw outline for better visibility
        drawCircle(
            color = config.vertexOutlineColorCompose(),
            radius = config.vertexRadiusPx(density),
            center = screenPosition,
            style = Stroke(width = config.vertexStrokeWidthPx(density)),
        )
    }
}
