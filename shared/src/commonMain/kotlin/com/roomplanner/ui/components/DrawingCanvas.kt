package com.roomplanner.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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

    Canvas(
        modifier =
            modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { screenOffset ->
                            coroutineScope.launch {
                                handleTap(screenOffset, state, eventBus)
                            }
                        },
                    )
                },
    ) {
        // 1. Draw grid background
        drawGrid(state.cameraTransform, state.snapSettings.gridSize, state.drawingConfig)

        // 2. Draw lines (walls)
        drawLines(state, state.cameraTransform, state.drawingConfig)

        // 3. Draw vertices (snap points)
        drawVertices(state, state.cameraTransform, state.drawingConfig)
    }
}

/**
 * Handle tap gesture: convert to world coordinates, apply snap, emit event.
 */
private suspend fun handleTap(
    screenOffset: Offset,
    state: AppState,
    eventBus: EventBus,
) {
    Logger.d { "→ Tap detected at screen (${screenOffset.x}, ${screenOffset.y})" }

    // Convert screen coordinates to world coordinates
    val worldPoint = screenToWorld(screenOffset, state.cameraTransform)

    // Apply grid snap
    val snappedPoint = snapToGrid(worldPoint, state.snapSettings.gridSize)

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
 * Convert screen coordinates to world coordinates.
 * Inverse of world-to-screen transform: scale(zoom) -> translate(pan)
 *
 * Phase 1.1: No pan/zoom, so screen == world
 * Phase 1.2: Will account for camera transform
 */
private fun screenToWorld(
    screenOffset: Offset,
    camera: CameraTransform,
): Point2 {
    // For now, screen coordinates = world coordinates (no pan/zoom yet)
    // Future: apply inverse camera transform
    return Point2.fromOffset(screenOffset)
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
 */
private fun DrawScope.drawGrid(
    camera: CameraTransform,
    gridSize: Double,
    config: com.roomplanner.data.models.DrawingConfig,
) {
    val gridColor = config.gridColorCompose()
    val gridSizeFloat = gridSize.toFloat()

    // Draw vertical grid lines
    var x = 0f
    while (x < size.width) {
        drawLine(
            color = gridColor,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = config.gridLineWidth,
        )
        x += gridSizeFloat
    }

    // Draw horizontal grid lines
    var y = 0f
    while (y < size.height) {
        drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = config.gridLineWidth,
        )
        y += gridSizeFloat
    }
}

/**
 * Draw all lines (walls).
 */
private fun DrawScope.drawLines(
    state: AppState,
    camera: CameraTransform,
    config: com.roomplanner.data.models.DrawingConfig,
) {
    state.lines.forEach { line ->
        val start = line.geometry.start.toOffset()
        val end = line.geometry.end.toOffset()

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
 * Active vertex (last placed) is highlighted in blue.
 */
private fun DrawScope.drawVertices(
    state: AppState,
    camera: CameraTransform,
    config: com.roomplanner.data.models.DrawingConfig,
) {
    state.vertices.values.forEach { vertex ->
        val isActive = vertex.id == state.activeVertexId
        val color =
            when {
                vertex.fixed -> config.vertexColorFixedCompose()
                isActive -> config.vertexColorActiveCompose()
                else -> config.vertexColorNormalCompose()
            }

        drawCircle(
            color = color,
            radius = config.vertexRadius,
            center = vertex.position.toOffset(),
        )

        // Draw outline for better visibility
        drawCircle(
            color = config.vertexOutlineColorCompose(),
            radius = config.vertexRadius,
            center = vertex.position.toOffset(),
            style = Stroke(width = config.vertexStrokeWidth),
        )
    }
}
