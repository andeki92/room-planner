package com.roomplanner.ui.gestures

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Density
import com.roomplanner.data.events.EventBus
import com.roomplanner.data.models.CameraTransform
import com.roomplanner.data.models.ProjectDrawingState
import com.roomplanner.data.models.SnapResultWithGuidelines
import com.roomplanner.domain.geometry.Point2
import com.roomplanner.ui.utils.CoordinateConversion
import kotlinx.coroutines.launch

/**
 * Canvas gesture modifier with unified context-aware gestures.
 *
 * Design rationale:
 * - Single handler for all gestures (no mode switching)
 * - Context-aware: gesture meaning depends on what you touch
 * - Composition over inheritance: modifiers compose cleanly
 *
 * Gesture behavior:
 * | Gesture     | On Vertex           | On Line    | On Empty Space          |
 * |-------------|---------------------|------------|-------------------------|
 * | Tap         | Select (activate)   | (nothing)  | Place new vertex        |
 * | Drag        | Move vertex         | (nothing)  | Preview line → place    |
 * | Long Press  | Vertex radial menu  | Line menu  | (nothing)               |
 *
 * @param drawingState Current drawing state (passed to handler)
 * @param camera Camera transform (for coordinate conversion)
 * @param density Screen density (for dp→px conversion)
 * @param eventBus Event bus for emitting events
 * @param onPreview Callback to update preview line state
 * @param onSnapHint Callback to update snap hint state
 * @param onVertexLongPress Callback when vertex is long-pressed (radial menu)
 * @param onLineLongPress Callback when line is long-pressed (radial menu)
 * @param onDismissMenus Callback to dismiss radial menus (called when dragging starts)
 * @param onEmptyTapped Callback when empty space is tapped (close menus)
 */
fun Modifier.canvasGestures(
    drawingState: ProjectDrawingState,
    camera: CameraTransform,
    density: Density,
    eventBus: EventBus,
    onPreview: (Point2?) -> Unit,
    onSnapHint: (SnapResultWithGuidelines?) -> Unit,
    onVertexLongPress: (vertexId: String, screenPosition: Offset) -> Unit,
    onLineLongPress: (lineId: String, screenPosition: Offset) -> Unit,
    onDismissMenus: () -> Unit,
    onEmptyTapped: () -> Unit,
): Modifier =
    composed {
        val scope = rememberCoroutineScope()

        // Single unified handler (replaces DRAW/SELECT handlers)
        val handler = remember { UnifiedGestureHandler() }

        // Ref pattern: prevents stale state bugs in gesture handlers
        val drawingStateRef = remember { mutableStateOf(drawingState) }
        drawingStateRef.value = drawingState
        val cameraRef = remember { mutableStateOf(camera) }
        cameraRef.value = camera
        val densityRef = remember { mutableStateOf(density) }
        densityRef.value = density

        // Track last position for menu positioning
        val lastScreenPositionRef = remember { mutableStateOf(Offset.Zero) }

        // Track if we've dismissed menus for this gesture (avoid repeated calls)
        val menusDismissedRef = remember { mutableStateOf(false) }

        // Build gesture thresholds from config
        val gestureThresholds =
            GestureThresholds(
                tapMaxDurationMs = drawingState.drawingConfig.tapMaxDurationMs,
                longPressDelayMs = drawingState.drawingConfig.longPressDelayMs,
                dragThresholdPx = drawingState.drawingConfig.dragThresholdPx,
            )

        this.pointerInput(drawingState.activeVertexId, drawingState.selectedVertexId) {
            detectUnifiedGestures(
                thresholds = gestureThresholds,
                callbacks =
                    GestureCallbacks(
                        onPress = { position ->
                            lastScreenPositionRef.value = position
                            menusDismissedRef.value = false // Reset for new gesture
                            scope.launch {
                                handler.handlePress(
                                    screenPosition = position,
                                    drawingState = drawingStateRef.value,
                                    camera = cameraRef.value,
                                    density = densityRef.value,
                                    eventBus = eventBus,
                                    onPreview = onPreview,
                                    onSnapHint = onSnapHint,
                                )
                            }
                        },
                        onDrag = { position, delta ->
                            lastScreenPositionRef.value = position

                            // Dismiss menus on first drag event (e.g., user started moving after long press)
                            if (!menusDismissedRef.value) {
                                menusDismissedRef.value = true
                                onDismissMenus()
                            }

                            scope.launch {
                                handler.handleDrag(
                                    screenPosition = position,
                                    dragDelta = delta,
                                    drawingState = drawingStateRef.value,
                                    camera = cameraRef.value,
                                    density = densityRef.value,
                                    eventBus = eventBus,
                                    onPreview = onPreview,
                                    onSnapHint = onSnapHint,
                                )
                            }
                        },
                        onRelease = { position ->
                            scope.launch {
                                handler.handleRelease(
                                    screenPosition = position,
                                    drawingState = drawingStateRef.value,
                                    camera = cameraRef.value,
                                    density = densityRef.value,
                                    eventBus = eventBus,
                                    onPreview = onPreview,
                                    onSnapHint = onSnapHint,
                                )
                            }
                        },
                        onTap = { position ->
                            scope.launch {
                                val currentDrawingState = drawingStateRef.value
                                val currentCamera = cameraRef.value
                                val currentDensity = densityRef.value

                                handler.handleTap(
                                    screenPosition = position,
                                    drawingState = currentDrawingState,
                                    camera = currentCamera,
                                    density = currentDensity,
                                    eventBus = eventBus,
                                    onPreview = onPreview,
                                    onSnapHint = onSnapHint,
                                )

                                // Tap on empty space dismisses menus
                                val vertexId =
                                    com.roomplanner.ui.utils.HitTesting.findVertexAt(
                                        tapScreen = position,
                                        drawingState = currentDrawingState,
                                        camera = currentCamera,
                                        density = currentDensity,
                                        config = currentDrawingState.drawingConfig,
                                    )
                                val lineId =
                                    com.roomplanner.ui.utils.HitTesting.findLineAt(
                                        tapScreen = position,
                                        drawingState = currentDrawingState,
                                        camera = currentCamera,
                                        density = currentDensity,
                                        config = currentDrawingState.drawingConfig,
                                    )

                                if (vertexId == null && lineId == null) {
                                    onEmptyTapped()
                                }
                            }
                        },
                        onLongPress = { position ->
                            scope.launch {
                                handler.handleLongPress(
                                    screenPosition = position,
                                    drawingState = drawingStateRef.value,
                                    camera = cameraRef.value,
                                    density = densityRef.value,
                                    eventBus = eventBus,
                                    onVertexMenu = { vertexId ->
                                        // Convert vertex position to screen for menu anchor
                                        val vertex = drawingStateRef.value.vertices[vertexId]
                                        if (vertex != null) {
                                            val screenPos =
                                                CoordinateConversion.worldToScreen(
                                                    vertex.position,
                                                    cameraRef.value,
                                                )
                                            onVertexLongPress(vertexId, screenPos)
                                        }
                                    },
                                    onLineMenu = { lineId ->
                                        // Convert line midpoint to screen for menu anchor
                                        val line = drawingStateRef.value.lines.find { it.id == lineId }
                                        if (line != null) {
                                            val midpoint = line.getMidpoint(drawingStateRef.value.vertices)
                                            val screenPos =
                                                CoordinateConversion.worldToScreen(
                                                    midpoint,
                                                    cameraRef.value,
                                                )
                                            onLineLongPress(lineId, screenPos)
                                        }
                                    },
                                )
                            }
                        },
                        onCancel = {
                            scope.launch {
                                handler.handleCancel(
                                    onPreview = onPreview,
                                    onSnapHint = onSnapHint,
                                )
                            }
                        },
                    ),
            )
        }
    }
