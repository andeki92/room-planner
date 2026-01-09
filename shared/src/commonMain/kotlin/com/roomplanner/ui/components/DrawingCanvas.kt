package com.roomplanner.ui.components

import androidx.compose.foundation.Canvas
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
import co.touchlab.kermit.Logger
import com.roomplanner.data.events.EventBus
import com.roomplanner.data.events.GeometryEvent
import com.roomplanner.data.models.AppState
import com.roomplanner.data.models.CameraTransform
import com.roomplanner.domain.geometry.Point2
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
                                val snapSettings = snapSettingsRef.value

                                Logger.d { "→ Tap at screen (${screenOffset.x}, ${screenOffset.y})" }
                                Logger.d { "  Camera: pan=(${camera.panX}, ${camera.panY}), zoom=${camera.zoom}" }

                                // Convert screen → world using CURRENT camera
                                val worldPoint = screenToWorld(screenOffset, camera)
                                val snappedPoint = snapToGrid(worldPoint, snapSettings.gridSize)
                                Logger.d {
                                    "  World: (${worldPoint.x}, ${worldPoint.y}) → Snapped: (${snappedPoint.x}, ${snappedPoint.y})"
                                }

                                eventBus.emit(
                                    GeometryEvent.PointPlaced(
                                        position = snappedPoint,
                                        snappedTo = null,
                                    ),
                                )
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
                },
    ) {
        // 1. Draw grid background
        drawGrid(drawingState.cameraTransform, drawingState.snapSettings.gridSize, drawingState.drawingConfig)

        // 2. Draw lines (walls)
        drawLines(drawingState, drawingState.cameraTransform, drawingState.drawingConfig)

        // 3. Draw vertices (snap points)
        drawVertices(drawingState, drawingState.cameraTransform, drawingState.drawingConfig)
    }
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
 */
private fun DrawScope.drawGrid(
    camera: CameraTransform,
    gridSize: Double,
    config: com.roomplanner.data.models.DrawingConfig,
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
            strokeWidth = config.gridLineWidth,
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
            strokeWidth = config.gridLineWidth,
        )
        y += gridSizeScreen
    }
}

/**
 * Draw all lines (walls).
 * Phase 1.2: Transform world coordinates to screen space
 */
private fun DrawScope.drawLines(
    drawingState: com.roomplanner.data.models.ProjectDrawingState,
    camera: CameraTransform,
    config: com.roomplanner.data.models.DrawingConfig,
) {
    drawingState.lines.forEach { line ->
        // Convert world coordinates to screen coordinates
        val start = worldToScreen(line.geometry.start, camera)
        val end = worldToScreen(line.geometry.end, camera)

        drawLine(
            color = config.lineColorCompose(),
            start = start,
            end = end,
            strokeWidth = config.lineStrokeWidth,
        )
    }
}

/**
 * Draw all vertices (snap points).
 * Phase 1.2: Transform world coordinates to screen space
 * Active vertex (last placed) is highlighted in blue.
 */
private fun DrawScope.drawVertices(
    drawingState: com.roomplanner.data.models.ProjectDrawingState,
    camera: CameraTransform,
    config: com.roomplanner.data.models.DrawingConfig,
) {
    drawingState.vertices.values.forEach { vertex ->
        val isActive = vertex.id == drawingState.activeVertexId
        val color =
            when {
                vertex.fixed -> config.vertexColorFixedCompose()
                isActive -> config.vertexColorActiveCompose()
                else -> config.vertexColorNormalCompose()
            }

        // Convert world coordinates to screen coordinates
        val screenPosition = worldToScreen(vertex.position, camera)

        drawCircle(
            color = color,
            radius = config.vertexRadius,
            center = screenPosition,
        )

        // Draw outline for better visibility
        drawCircle(
            color = config.vertexOutlineColorCompose(),
            radius = config.vertexRadius,
            center = screenPosition,
            style = Stroke(width = config.vertexStrokeWidth),
        )
    }
}
