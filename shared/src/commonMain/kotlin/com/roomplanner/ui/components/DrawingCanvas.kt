package com.roomplanner.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import co.touchlab.kermit.Logger
import com.roomplanner.data.events.ConstraintEvent
import com.roomplanner.data.events.EventBus
import com.roomplanner.data.events.GeometryEvent
import com.roomplanner.data.models.AppState
import com.roomplanner.data.models.CameraTransform
import com.roomplanner.data.models.DrawingConfig
import com.roomplanner.data.models.MeasurementUnits
import com.roomplanner.data.models.ProjectDrawingState
import com.roomplanner.domain.geometry.Point2
import com.roomplanner.ui.gestures.canvasToolGestures
import com.roomplanner.ui.utils.CoordinateConversion
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlinx.coroutines.launch

/**
 * DrawingCanvas - Interactive canvas for CAD drawing with touch input.
 *
 * Architecture (Refactored):
 * - Tool-specific gesture handling via ToolGestureHandler pattern
 * - Pure utility functions for coordinate conversion and hit testing
 * - Unified gesture detector (tap vs drag vs transform)
 * - Clean separation: gestures → handlers → events → state
 *
 * Design rationale:
 * - Declarative rendering with Compose Canvas
 * - Event-driven: emits GeometryEvent, doesn't mutate state directly
 * - Skia-backed for hardware acceleration
 * - Immutable state for reactive rendering
 * - Compositional gesture handling (easy to extend)
 */
@Composable
fun DrawingCanvas(
    state: AppState,
    eventBus: EventBus,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    val drawingState = state.projectDrawingState
    val density = LocalDensity.current

    // Early return if no drawing state
    if (drawingState == null) {
        Logger.w { "⚠ DrawingCanvas: No drawing state available" }
        return
    }

    // Radial menu state
    val radialMenuPosition = remember { androidx.compose.runtime.mutableStateOf<Offset?>(null) }
    val radialMenuVertexId = remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val lineRadialMenuPosition = remember { androidx.compose.runtime.mutableStateOf<Offset?>(null) }
    val lineRadialMenuLineId = remember { androidx.compose.runtime.mutableStateOf<String?>(null) }

    // Dimension dialog state
    val showDimensionDialog = remember { androidx.compose.runtime.mutableStateOf(false) }
    val selectedLineForDimension = remember { androidx.compose.runtime.mutableStateOf<String?>(null) }

    // Preview state (local UI state for DRAW mode)
    val previewLineEnd = remember { androidx.compose.runtime.mutableStateOf<Point2?>(null) }
    val snapHintPosition = remember { androidx.compose.runtime.mutableStateOf<Point2?>(null) }

    Canvas(
        modifier =
            modifier
                .fillMaxSize()
                // Tool-specific gestures (unified detector routes to appropriate handler)
                .canvasToolGestures(
                    toolMode = drawingState.toolMode,
                    drawingState = drawingState,
                    camera = drawingState.cameraTransform,
                    density = density,
                    eventBus = eventBus,
                    onPreview = { previewLineEnd.value = it },
                    onSnapHint = { snapHintPosition.value = it },
                    onVertexTapped = { vertexId ->
                        val vertex = drawingState.vertices[vertexId] ?: return@canvasToolGestures
                        val vertexScreenPos =
                            CoordinateConversion.worldToScreen(
                                vertex.position,
                                drawingState.cameraTransform,
                            )

                        // Close line menu if open
                        lineRadialMenuPosition.value = null
                        lineRadialMenuLineId.value = null

                        // Show vertex radial menu
                        radialMenuPosition.value = vertexScreenPos
                        radialMenuVertexId.value = vertexId
                    },
                    onLineTapped = { lineId ->
                        val line = drawingState.lines.find { it.id == lineId } ?: return@canvasToolGestures
                        val lineMidpoint = line.getMidpoint(drawingState.vertices)
                        val lineMidpointScreen =
                            CoordinateConversion.worldToScreen(
                                lineMidpoint,
                                drawingState.cameraTransform,
                            )

                        // Close vertex menu if open
                        radialMenuPosition.value = null
                        radialMenuVertexId.value = null

                        // Show line radial menu
                        lineRadialMenuPosition.value = lineMidpointScreen
                        lineRadialMenuLineId.value = lineId
                    },
                    onEmptyTapped = {
                        // Dismiss radial menu if open
                        if (radialMenuPosition.value != null) {
                            radialMenuPosition.value = null
                            radialMenuVertexId.value = null
                        }
                        if (lineRadialMenuPosition.value != null) {
                            lineRadialMenuPosition.value = null
                            lineRadialMenuLineId.value = null
                        }
                    },
                )
                // Pan/zoom gestures (always active, global)
                .pointerInput(Unit) {
                    detectTransformGestures(
                        panZoomLock = true,
                    ) { centroid, pan, zoom, _ ->
                        coroutineScope.launch {
                            if (pan != Offset.Zero || zoom != 1f) {
                                eventBus.emit(
                                    GeometryEvent.CameraTransformed(
                                        panDelta = pan,
                                        zoomDelta = zoom,
                                        zoomCenter = centroid,
                                    ),
                                )
                            }
                        }
                    }
                },
    ) {
        // 1. Draw grid background
        drawGrid(
            drawingState.cameraTransform,
            drawingState.snapSettings.gridSize,
            drawingState.drawingConfig,
            density,
        )

        // 2. Draw lines (walls)
        drawLines(drawingState, drawingState.cameraTransform, drawingState.drawingConfig, density)

        // 2.5. Draw preview lines (DRAW mode only)
        drawPreviewLines(
            previewLineEnd.value,
            snapHintPosition.value,
            drawingState,
            density,
        )

        // 3. Draw vertices (snap points)
        drawVertices(drawingState, drawingState.cameraTransform, drawingState.drawingConfig, density)

        // 4. Draw dimension labels (if enabled)
        if (drawingState.showDimensions) {
            drawDimensionLabels(
                drawingState = drawingState,
                camera = drawingState.cameraTransform,
                config = drawingState.drawingConfig,
                density = density,
                units = state.settings.measurementUnits,
            )
        }
    }

    // Overlay radial menu (outside Canvas)
    if (radialMenuPosition.value != null && radialMenuVertexId.value != null) {
        VertexRadialMenu(
            anchorPosition = radialMenuPosition.value!!,
            onDelete = {
                val vertexIdToDelete = radialMenuVertexId.value
                if (vertexIdToDelete != null) {
                    coroutineScope.launch {
                        Logger.i { "✓ Delete requested for vertex: $vertexIdToDelete" }
                        eventBus.emit(GeometryEvent.VertexDeleted(vertexIdToDelete))
                    }
                } else {
                    Logger.w { "⚠ Delete requested but no vertex selected" }
                }
            },
            onDismiss = {
                Logger.d { "→ Vertex radial menu dismissed" }
                radialMenuPosition.value = null
                radialMenuVertexId.value = null
            },
        )
    }

    // Overlay line radial menu (outside Canvas)
    if (lineRadialMenuPosition.value != null && lineRadialMenuLineId.value != null) {
        LineRadialMenu(
            anchorPosition = lineRadialMenuPosition.value!!,
            onSetDimension = {
                val lineId = lineRadialMenuLineId.value

                // Close radial menu first
                lineRadialMenuPosition.value = null
                lineRadialMenuLineId.value = null

                // Show dimension input dialog
                if (lineId != null) {
                    Logger.i { "✓ Set Dimension requested for line: $lineId" }
                    selectedLineForDimension.value = lineId
                    showDimensionDialog.value = true
                } else {
                    Logger.w { "⚠ Set Dimension requested but no line selected" }
                }
            },
            onDelete = {
                val lineIdToDelete = lineRadialMenuLineId.value

                // Close radial menu first
                lineRadialMenuPosition.value = null
                lineRadialMenuLineId.value = null

                if (lineIdToDelete != null) {
                    coroutineScope.launch {
                        Logger.i { "✓ Delete requested for line: $lineIdToDelete" }
                        eventBus.emit(GeometryEvent.LineDeleted(lineIdToDelete))
                    }
                } else {
                    Logger.w { "⚠ Delete requested but no line selected" }
                }
            },
            onSplit = {
                Logger.w { "⚠ Split line not implemented yet" }
                lineRadialMenuPosition.value = null
                lineRadialMenuLineId.value = null
            },
            onDismiss = {
                Logger.d { "→ Line radial menu dismissed" }
                lineRadialMenuPosition.value = null
                lineRadialMenuLineId.value = null
            },
        )
    }

    // Overlay dimension input dialog
    if (showDimensionDialog.value && selectedLineForDimension.value != null) {
        val currentConstraint =
            drawingState
                .getConstraintsForLine(selectedLineForDimension.value!!)
                .filterIsInstance<com.roomplanner.data.models.Constraint.Distance>()
                .firstOrNull()

        val lineIdForConstraint = selectedLineForDimension.value!!

        DimensionInputDialog(
            initialValue = currentConstraint?.distance,
            units = state.settings.measurementUnits,
            onDismiss = {
                Logger.d { "→ Dimension dialog dismissed" }
                showDimensionDialog.value = false
                selectedLineForDimension.value = null
            },
            onConfirm = { dimension ->
                coroutineScope.launch {
                    val constraint =
                        com.roomplanner.data.models.Constraint.Distance(
                            lineId = lineIdForConstraint,
                            distance = dimension,
                        )

                    if (currentConstraint != null) {
                        Logger.i { "✓ Modifying constraint: ${currentConstraint.id} → $dimension" }
                        eventBus.emit(
                            ConstraintEvent.ConstraintModified(
                                constraintId = currentConstraint.id,
                                newConstraint = constraint.copy(id = currentConstraint.id),
                            ),
                        )
                    } else {
                        Logger.i { "✓ Adding constraint: line $lineIdForConstraint = $dimension" }
                        eventBus.emit(ConstraintEvent.ConstraintAdded(constraint))
                    }

                    showDimensionDialog.value = false
                    selectedLineForDimension.value = null
                }
            },
        )
    }
}

// ==================== Drawing Functions ====================

/**
 * Draw preview lines (DRAW mode only).
 * Shows:
 * 1. Continuous solid line (exact cursor position)
 * 2. Dotted orange line (snap hint when within tolerance)
 */
private fun DrawScope.drawPreviewLines(
    previewEnd: Point2?,
    snapHint: Point2?,
    drawingState: ProjectDrawingState,
    density: Density,
) {
    val activeVertexId = drawingState.activeVertexId ?: return
    val activeVertex = drawingState.vertices[activeVertexId] ?: return

    // 1. Draw continuous preview line (follows cursor exactly)
    previewEnd?.let { endPoint ->
        val start =
            CoordinateConversion.worldToScreen(
                activeVertex.position,
                drawingState.cameraTransform,
            )
        val end = CoordinateConversion.worldToScreen(endPoint, drawingState.cameraTransform)

        drawLine(
            color = Color(0xFF2196F3).copy(alpha = 0.4f), // Light blue
            start = start,
            end = end,
            strokeWidth = drawingState.drawingConfig.lineStrokeWidthPx(density),
        )
    }

    // 2. Draw snap hint line (dotted, shows where snap would occur)
    snapHint?.let { snapPoint ->
        val start =
            CoordinateConversion.worldToScreen(
                activeVertex.position,
                drawingState.cameraTransform,
            )
        val end = CoordinateConversion.worldToScreen(snapPoint, drawingState.cameraTransform)

        drawLine(
            color = Color(0xFFFF9800), // Orange
            start = start,
            end = end,
            strokeWidth = drawingState.drawingConfig.lineStrokeWidthPx(density) * 1.5f,
            pathEffect =
                androidx.compose.ui.graphics.PathEffect
                    .dashPathEffect(floatArrayOf(15f, 10f)),
        )
    }
}

/**
 * Draw grid background for visual reference.
 */
private fun DrawScope.drawGrid(
    camera: CameraTransform,
    gridSize: Double,
    config: DrawingConfig,
    density: Density,
) {
    val gridColor = config.gridColorCompose()
    val gridSizeScreen = (gridSize.toFloat() * camera.zoom)

    val gridOffsetX = ((camera.panX % gridSizeScreen) + gridSizeScreen) % gridSizeScreen
    val gridOffsetY = ((camera.panY % gridSizeScreen) + gridSizeScreen) % gridSizeScreen

    // Vertical lines
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

    // Horizontal lines
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
 */
private fun DrawScope.drawLines(
    drawingState: ProjectDrawingState,
    camera: CameraTransform,
    config: DrawingConfig,
    density: Density,
) {
    drawingState.lines.forEach { line ->
        val geometry = line.getGeometry(drawingState.vertices)
        val start = CoordinateConversion.worldToScreen(geometry.start, camera)
        val end = CoordinateConversion.worldToScreen(geometry.end, camera)

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
 */
private fun DrawScope.drawVertices(
    drawingState: ProjectDrawingState,
    camera: CameraTransform,
    config: DrawingConfig,
    density: Density,
) {
    drawingState.vertices.values.forEach { vertex ->
        val isActive = vertex.id == drawingState.activeVertexId
        val isSelected = drawingState.isVertexSelected(vertex.id)

        val color =
            when {
                vertex.fixed -> config.vertexColorFixedCompose()
                isSelected -> config.selectedVertexColorCompose()
                isActive -> config.vertexColorActiveCompose()
                else -> config.vertexColorNormalCompose()
            }

        val screenPosition = CoordinateConversion.worldToScreen(vertex.position, camera)

        // Draw selection glow ring
        if (isSelected) {
            drawCircle(
                color = config.selectedVertexColorCompose().copy(alpha = config.selectionGlowAlpha),
                radius = config.selectionIndicatorRadiusPx(density),
                center = screenPosition,
            )
        }

        // Draw vertex circle
        drawCircle(
            color = color,
            radius = config.vertexRadiusPx(density),
            center = screenPosition,
        )

        // Draw outline
        drawCircle(
            color = config.vertexOutlineColorCompose(),
            radius = config.vertexRadiusPx(density),
            center = screenPosition,
            style = Stroke(width = config.vertexStrokeWidthPx(density)),
        )

        // Draw anchor indicator (vertices with 2+ connections)
        val connectedLines = drawingState.getLinesConnectedToVertex(vertex.id)
        if (connectedLines.size >= 2 && !vertex.fixed) {
            val anchorOffset = config.vertexRadiusPx(density) * 0.7f
            drawCircle(
                color = config.anchorIndicatorColorCompose(),
                radius = config.anchorIndicatorRadiusPx(density),
                center =
                    Offset(
                        screenPosition.x + anchorOffset,
                        screenPosition.y - anchorOffset,
                    ),
            )
        }
    }
}

/**
 * Draw dimension labels for all constrained lines.
 */
private fun DrawScope.drawDimensionLabels(
    drawingState: ProjectDrawingState,
    camera: CameraTransform,
    config: DrawingConfig,
    density: Density,
    units: MeasurementUnits,
) {
    drawingState.constraints.values.forEach { constraint ->
        when (constraint) {
            is com.roomplanner.data.models.Constraint.Distance -> {
                drawDistanceDimension(
                    constraint = constraint,
                    drawingState = drawingState,
                    camera = camera,
                    config = config,
                    density = density,
                    units = units,
                )
            }

            else -> {
                // Phase 2: Other constraint types
            }
        }
    }
}

/**
 * Draw dimension label for a single distance constraint.
 */
private fun DrawScope.drawDistanceDimension(
    constraint: com.roomplanner.data.models.Constraint.Distance,
    drawingState: ProjectDrawingState,
    camera: CameraTransform,
    config: DrawingConfig,
    density: Density,
    units: MeasurementUnits,
) {
    val line = drawingState.lines.find { it.id == constraint.lineId } ?: return
    val v1 = drawingState.vertices[line.startVertexId] ?: return
    val v2 = drawingState.vertices[line.endVertexId] ?: return

    val midpoint =
        Point2(
            (v1.position.x + v2.position.x) / 2.0,
            (v1.position.y + v2.position.y) / 2.0,
        )

    val midpointScreen = CoordinateConversion.worldToScreen(midpoint, camera)

    val dx = v2.position.x - v1.position.x
    val dy = v2.position.y - v1.position.y
    val length = kotlin.math.sqrt(dx * dx + dy * dy)

    if (length < 0.001) return

    val perpX = -dy / length
    val perpY = dx / length

    val offsetDistanceWorld = 20.0
    val offsetDistanceScreen = (offsetDistanceWorld * camera.zoom).toFloat()

    val labelPosition =
        Offset(
            midpointScreen.x + (perpX * offsetDistanceScreen).toFloat(),
            midpointScreen.y + (perpY * offsetDistanceScreen).toFloat(),
        )

    val currentLength = v1.position.distanceTo(v2.position)
    val error = abs(currentLength - constraint.distance)
    val tolerance = 0.001

    val constraintColor =
        when {
            error < tolerance -> config.constraintSatisfiedColorCompose()
            error < 1.0 -> config.constraintSolvingColorCompose()
            else -> config.constraintViolatedColorCompose()
        }

    drawCircle(
        color = constraintColor,
        radius = config.constraintIndicatorRadiusPx(density),
        center = labelPosition,
    )
}

/**
 * Format dimension value as string with units.
 */
private fun formatDimension(
    value: Double,
    units: MeasurementUnits,
    precision: Int,
): String {
    require(precision >= 0) { "precision must be >= 0" }

    if (!value.isFinite()) {
        val unit =
            when (units) {
                MeasurementUnits.METRIC -> "cm"
                MeasurementUnits.IMPERIAL -> "\""
            }
        return "$value $unit"
    }

    val unit =
        when (units) {
            MeasurementUnits.METRIC -> "cm"
            MeasurementUnits.IMPERIAL -> "\""
        }

    if (precision == 0) {
        return "${value.roundToLong()} $unit"
    }

    val factor = 10.0.pow(precision)
    val scaled = (value * factor).roundToLong()
    val sign = if (scaled < 0) "-" else ""
    val scaledAbs = abs(scaled)
    val intPart = scaledAbs / factor.toLong()
    val fracPart = (scaledAbs % factor.toLong()).toString().padStart(precision, '0')

    return "$sign$intPart.$fracPart $unit"
}
