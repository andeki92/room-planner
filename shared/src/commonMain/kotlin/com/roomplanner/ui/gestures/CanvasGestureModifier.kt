package com.roomplanner.ui.gestures

import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Density
import com.roomplanner.data.events.EventBus
import com.roomplanner.data.models.CameraTransform
import com.roomplanner.data.models.ProjectDrawingState
import com.roomplanner.data.models.ToolMode
import com.roomplanner.domain.geometry.Point2
import com.roomplanner.ui.utils.HitTesting
import kotlinx.coroutines.launch

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
    onSnapHint: (Point2?) -> Unit,
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

        this.pointerInput(toolMode, drawingState.activeVertexId, drawingState.selectedVertexId) {
            detectUnifiedGestures(
                callbacks =
                    GestureCallbacks(
                        onPress = { position ->
                            scope.launch {
                                handler.handlePress(
                                    screenPosition = position,
                                    drawingState = drawingState,
                                    camera = camera,
                                    density = density,
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
                                    drawingState = drawingState,
                                    camera = camera,
                                    density = density,
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
                                    drawingState = drawingState,
                                    camera = camera,
                                    density = density,
                                    eventBus = eventBus,
                                    onPreview = onPreview,
                                    onSnapHint = onSnapHint,
                                )
                            }
                        },
                        onTap = { position ->
                            scope.launch {
                                // Handle tool-specific tap logic
                                handler.handleTap(
                                    screenPosition = position,
                                    drawingState = drawingState,
                                    camera = camera,
                                    density = density,
                                    eventBus = eventBus,
                                )

                                // Handle radial menu (SELECT mode only)
                                if (toolMode == ToolMode.SELECT) {
                                    // Check for vertex tap
                                    val vertexId =
                                        HitTesting.findVertexAt(
                                            tapScreen = position,
                                            drawingState = drawingState,
                                            camera = camera,
                                            density = density,
                                            config = drawingState.drawingConfig,
                                        )
                                    if (vertexId != null) {
                                        onVertexTapped(vertexId)
                                        return@launch
                                    }

                                    // Check for line tap
                                    val lineId =
                                        HitTesting.findLineAt(
                                            tapScreen = position,
                                            drawingState = drawingState,
                                            camera = camera,
                                            density = density,
                                            config = drawingState.drawingConfig,
                                        )
                                    if (lineId != null) {
                                        onLineTapped(lineId)
                                        return@launch
                                    }

                                    // Empty tap
                                    onEmptyTapped()
                                }
                            }
                        },
                    ),
            )
        }
    }
