package com.roomplanner.ui.gestures

import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Density
import com.roomplanner.data.events.EventBus
import com.roomplanner.data.models.CameraTransform
import com.roomplanner.data.models.ProjectDrawingState
import com.roomplanner.data.models.SnapResultWithGuidelines
import com.roomplanner.data.models.ToolMode
import com.roomplanner.domain.geometry.Point2
import com.roomplanner.ui.utils.HapticFeedback
import com.roomplanner.ui.utils.HitTesting
import kotlinx.coroutines.launch

// Note: Uses SnapResultWithGuidelines for multiple simultaneous snap guidelines

/**
 * Canvas gesture modifier that routes gestures to tool-specific handlers.
 *
 * Design rationale:
 * - Composition over inheritance: modifiers compose cleanly
 * - Single responsibility: routing gestures to appropriate handler
 * - Testable: behavior fully determined by tool handler
 * - Extensible: add new tools by implementing ToolGestureHandler
 *
 * Architecture:
 * 1. User touches screen → UnifiedGestureDetector detects gesture type
 * 2. Gesture routed to appropriate ToolGestureHandler (DRAW/SELECT)
 * 3. Handler updates local UI state (onPreview, onSnapHint) or emits events
 * 4. Radial menu logic handled via callbacks (UI concern, not gesture concern)
 *
 * @param toolMode Current tool mode (determines which handler to use)
 * @param drawingState Current drawing state (passed to handler)
 * @param camera Camera transform (for coordinate conversion)
 * @param density Screen density (for dp→px conversion)
 * @param eventBus Event bus for emitting events
 * @param onPreview Callback to update preview line state
 * @param onSnapHint Callback to update snap hint state
 * @param onVertexTapped Callback when vertex tapped (radial menu)
 * @param onLineTapped Callback when line tapped (radial menu)
 * @param onEmptyTapped Callback when empty space tapped (close menus)
 */
fun Modifier.canvasToolGestures(
    toolMode: ToolMode,
    drawingState: ProjectDrawingState,
    camera: CameraTransform,
    density: Density,
    eventBus: EventBus,
    onPreview: (Point2?) -> Unit,
    onSnapHint: (SnapResultWithGuidelines?) -> Unit,
    onVertexTapped: (String) -> Unit,
    onLineTapped: (String) -> Unit,
    onEmptyTapped: () -> Unit,
): Modifier =
    composed {
        val scope = rememberCoroutineScope()

        // Get tool-specific handler (factory pattern)
        val handler: ToolGestureHandler =
            when (toolMode) {
                ToolMode.DRAW -> DrawToolGestureHandler()
                ToolMode.SELECT -> SelectToolGestureHandler()
            }

        // Ref pattern: prevents stale state bugs in gesture handlers
        val drawingStateRef =
            androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateOf(
                    drawingState
                )
            }
        drawingStateRef.value = drawingState
        val cameraRef = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(camera) }
        cameraRef.value = camera
        val densityRef = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(density) }
        densityRef.value = density

        this.pointerInput(toolMode, drawingState.activeVertexId, drawingState.selectedVertexId) {
            detectUnifiedGestures(
                callbacks =
                    GestureCallbacks(
                        onPress = { position ->
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

                                // Handle tool-specific tap logic
                                handler.handleTap(
                                    screenPosition = position,
                                    drawingState = currentDrawingState,
                                    camera = currentCamera,
                                    density = currentDensity,
                                    eventBus = eventBus,
                                    onPreview = onPreview,
                                    onSnapHint = onSnapHint,
                                )

                                // Empty tap in SELECT mode dismisses menus
                                if (toolMode == ToolMode.SELECT) {
                                    // Check if tapped on empty space (not vertex/line)
                                    val vertexId =
                                        HitTesting.findVertexAt(
                                            tapScreen = position,
                                            drawingState = currentDrawingState,
                                            camera = currentCamera,
                                            density = currentDensity,
                                            config = currentDrawingState.drawingConfig,
                                        )
                                    val lineId =
                                        HitTesting.findLineAt(
                                            tapScreen = position,
                                            drawingState = currentDrawingState,
                                            camera = currentCamera,
                                            density = currentDensity,
                                            config = currentDrawingState.drawingConfig,
                                        )

                                    if (vertexId == null && lineId == null) {
                                        // Empty tap - dismiss menus
                                        onEmptyTapped()
                                    }
                                }
                            }
                        },
                        onLongPress = { position ->
                            scope.launch {
                                val currentDrawingState = drawingStateRef.value
                                val currentCamera = cameraRef.value
                                val currentDensity = densityRef.value

                                // Handle radial menu (SELECT mode only, on long press)
                                if (toolMode == ToolMode.SELECT) {
                                    // Check for vertex long press
                                    val vertexId =
                                        HitTesting.findVertexAt(
                                            tapScreen = position,
                                            drawingState = currentDrawingState,
                                            camera = currentCamera,
                                            density = currentDensity,
                                            config = currentDrawingState.drawingConfig,
                                        )
                                    if (vertexId != null) {
                                        HapticFeedback.medium() // Haptic feedback when menu opens
                                        onVertexTapped(vertexId)
                                        return@launch
                                    }

                                    // Check for line long press
                                    val lineId =
                                        HitTesting.findLineAt(
                                            tapScreen = position,
                                            drawingState = currentDrawingState,
                                            camera = currentCamera,
                                            density = currentDensity,
                                            config = currentDrawingState.drawingConfig,
                                        )
                                    if (lineId != null) {
                                        HapticFeedback.medium() // Haptic feedback when menu opens
                                        onLineTapped(lineId)
                                        return@launch
                                    }
                                }
                            }
                        },
                    ),
            )
        }
    }
