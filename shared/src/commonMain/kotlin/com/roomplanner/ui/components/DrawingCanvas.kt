package com.roomplanner.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import com.roomplanner.data.events.ConstraintEvent
import com.roomplanner.data.events.EventBus
import com.roomplanner.data.events.GeometryEvent
import com.roomplanner.data.models.AppState
import com.roomplanner.data.models.CameraTransform
import com.roomplanner.data.models.DrawingConfig
import com.roomplanner.data.models.MeasurementUnits
import com.roomplanner.data.models.ProjectDrawingState
import com.roomplanner.data.models.SnapResult
import com.roomplanner.data.models.SnapResultWithGuidelines
import com.roomplanner.data.models.ToolMode
import com.roomplanner.domain.drawing.Vertex
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

    // Angle dialog state (Phase 1.6.3)
    val showAngleDialog = remember { androidx.compose.runtime.mutableStateOf(false) }
    val selectedLineForAngle = remember { androidx.compose.runtime.mutableStateOf<String?>(null) }

    // Preview state (local UI state for DRAW mode)
    val previewLineEnd = remember { androidx.compose.runtime.mutableStateOf<Point2?>(null) }
    val snapHintResult = remember { androidx.compose.runtime.mutableStateOf<SnapResultWithGuidelines?>(null) }

    // Clear preview states when tool mode changes (prevents ghost guides)
    androidx.compose.runtime.LaunchedEffect(drawingState.toolMode) {
        previewLineEnd.value = null
        snapHintResult.value = null
    }

    // Wrap in BoxWithConstraints to get canvas size for zoom initialization
    androidx.compose.foundation.layout.BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenWidthPx = constraints.maxWidth.toFloat()

        // Initialize camera zoom to default if still at 1.0f (uninitialized)
        androidx.compose.runtime.LaunchedEffect(Unit) {
            if (drawingState.cameraTransform.zoom == 1f) {
                val defaultZoom = drawingState.drawingConfig.defaultZoom(screenWidthPx)
                val centeredPanX = screenWidthPx / 2f
                val centeredPanY = constraints.maxHeight.toFloat() / 2f

                eventBus.emit(
                    com.roomplanner.data.events.GeometryEvent.CameraTransformed(
                        panDelta =
                            androidx.compose.ui.geometry
                                .Offset(centeredPanX, centeredPanY),
                        zoomDelta = defaultZoom,
                        zoomCenter = null,
                        screenWidthPx = screenWidthPx,
                        screenHeightPx = constraints.maxHeight.toFloat(),
                    ),
                )
                Logger.i { "✓ Camera initialized: zoom=$defaultZoom, pan=($centeredPanX, $centeredPanY)" }
            }
        }

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
                        onSnapHint = { snapHintResult.value = it },
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
                                            screenWidthPx = size.width.toFloat(),
                                            screenHeightPx = size.height.toFloat(),
                                        ),
                                    )
                                }
                            }
                        }
                    },
        ) {
            // 1. Draw project boundary
            drawProjectBoundary(
                drawingState.cameraTransform,
                drawingState.drawingConfig,
                density,
            )

            // 2. Draw grid background
            drawGrid(
                drawingState.cameraTransform,
                drawingState.snapSettings.gridSize,
                drawingState.drawingConfig,
                density,
            )

            // 3. Draw lines (walls)
            drawLines(drawingState, drawingState.cameraTransform, drawingState.drawingConfig, density)

            // 2.5. Draw preview lines (DRAW mode only)
            drawPreviewLines(
                previewLineEnd.value,
                snapHintResult.value,
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

        // Helpful instruction text (top-center overlay)
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = androidx.compose.ui.Alignment.TopCenter,
        ) {
            if (drawingState.toolMode == ToolMode.SELECT) {
                val instructionText =
                    when {
                        drawingState.activeVertexId != null ->
                            "Vertex selected • Switch to DRAW to continue drawing"

                        drawingState.vertices.isNotEmpty() ->
                            "Tap vertex to select • Long press for options"

                        else ->
                            null
                    }

                if (instructionText != null) {
                    androidx.compose.material3.Surface(
                        modifier = Modifier.padding(16.dp),
                        shape =
                            androidx.compose.foundation.shape
                                .RoundedCornerShape(8.dp),
                        color =
                            androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
                                .copy(alpha = 0.9f),
                        shadowElevation = 4.dp,
                    ) {
                        androidx.compose.material3.Text(
                            text = instructionText,
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }

        // Overlay radial menu (outside Canvas)
        if (radialMenuPosition.value != null && radialMenuVertexId.value != null) {
            VertexRadialMenu(
                anchorPosition = radialMenuPosition.value!!,
                onSetAngle = {
                    val vertexId = radialMenuVertexId.value

                    // Close radial menu first
                    radialMenuPosition.value = null
                    radialMenuVertexId.value = null

                    // Show angle input dialog
                    if (vertexId != null) {
                        Logger.i { "✓ Set Angle requested for vertex: $vertexId" }
                        selectedLineForAngle.value = vertexId // Store vertex ID
                        showAngleDialog.value = true
                    } else {
                        Logger.w { "⚠ Set Angle requested but no vertex selected" }
                    }
                },
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

        // Overlay angle input dialog (Phase 1.6.3)
        if (showAngleDialog.value && selectedLineForAngle.value != null) {
            val vertexId = selectedLineForAngle.value!!

            // Find lines connected to this vertex
            val connectedLines = drawingState.getLinesConnectedToVertex(vertexId)

            if (connectedLines.size >= 2) {
                // Get first two lines for angle constraint
                val line1 = connectedLines[0]
                val line2 = connectedLines[1]

                // Calculate current angle between lines
                val v1Start = drawingState.vertices[line1.startVertexId]
                val v1End = drawingState.vertices[line1.endVertexId]
                val v2Start = drawingState.vertices[line2.startVertexId]
                val v2End = drawingState.vertices[line2.endVertexId]

                val currentAngle =
                    if (v1Start != null && v1End != null && v2Start != null && v2End != null) {
                        val dx1 =
                            if (line1.startVertexId == vertexId) {
                                v1End.position.x - v1Start.position.x
                            } else {
                                v1Start.position.x - v1End.position.x
                            }
                        val dy1 =
                            if (line1.startVertexId == vertexId) {
                                v1End.position.y - v1Start.position.y
                            } else {
                                v1Start.position.y - v1End.position.y
                            }

                        val dx2 =
                            if (line2.startVertexId == vertexId) {
                                v2End.position.x - v2Start.position.x
                            } else {
                                v2Start.position.x - v2End.position.x
                            }
                        val dy2 =
                            if (line2.startVertexId == vertexId) {
                                v2End.position.y - v2Start.position.y
                            } else {
                                v2Start.position.y - v2End.position.y
                            }

                        val angle1 = kotlin.math.atan2(dy1, dx1)
                        val angle2 = kotlin.math.atan2(dy2, dx2)
                        kotlin.math.abs((angle2 - angle1) * 180.0 / kotlin.math.PI)
                    } else {
                        90.0
                    }

                // Check for existing angle constraint
                val existingConstraint =
                    drawingState.constraints.values
                        .filterIsInstance<com.roomplanner.data.models.Constraint.Angle>()
                        .find {
                            (it.lineId1 == line1.id && it.lineId2 == line2.id) ||
                                (it.lineId1 == line2.id && it.lineId2 == line1.id)
                        }

                AngleInputDialog(
                    currentAngle = currentAngle,
                    initialValue = existingConstraint?.angle,
                    onDismiss = {
                        Logger.d { "→ Angle dialog dismissed" }
                        showAngleDialog.value = false
                        selectedLineForAngle.value = null
                    },
                    onConfirm = { angle ->
                        coroutineScope.launch {
                            val constraint =
                                com.roomplanner.data.models.Constraint.Angle(
                                    lineId1 = line1.id,
                                    lineId2 = line2.id,
                                    angle = angle,
                                )

                            if (existingConstraint != null) {
                                Logger.i { "✓ Modifying angle constraint: ${existingConstraint.id} → $angle°" }
                                eventBus.emit(
                                    ConstraintEvent.ConstraintModified(
                                        constraintId = existingConstraint.id,
                                        newConstraint = constraint.copy(id = existingConstraint.id),
                                    ),
                                )
                            } else {
                                Logger.i { "✓ Adding angle constraint: ${line1.id} ↔ ${line2.id} = $angle°" }
                                eventBus.emit(ConstraintEvent.ConstraintAdded(constraint))
                            }

                            showAngleDialog.value = false
                            selectedLineForAngle.value = null
                        }
                    },
                )
            } else {
                // Not enough lines connected - dismiss dialog
                Logger.w { "⚠ Vertex has < 2 lines, cannot set angle" }
                showAngleDialog.value = false
                selectedLineForAngle.value = null
            }
        }
    } // End BoxWithConstraints
}

// ==================== Drawing Functions ====================

/**
 * Draw project boundary rectangle (50m x 50m centered at origin).
 */
private fun DrawScope.drawProjectBoundary(
    camera: CameraTransform,
    config: DrawingConfig,
    density: Density,
) {
    val boundary = config.projectBoundaryCm
    val halfBoundary = boundary / 2.0

    // Convert boundary corners to screen coordinates
    val topLeft =
        CoordinateConversion.worldToScreen(
            Point2(-halfBoundary, -halfBoundary),
            camera,
        )
    val bottomRight =
        CoordinateConversion.worldToScreen(
            Point2(halfBoundary, halfBoundary),
            camera,
        )

    // Draw boundary rectangle
    drawRect(
        color = Color(0xFF9E9E9E), // Gray
        topLeft = topLeft,
        size =
            androidx.compose.ui.geometry.Size(
                width = bottomRight.x - topLeft.x,
                height = bottomRight.y - topLeft.y,
            ),
        style =
            androidx.compose.ui.graphics.drawscope.Stroke(
                width = 2f * density.density,
                pathEffect =
                    PathEffect
                        .dashPathEffect(floatArrayOf(20f, 10f)),
            ),
    )
}

/**
 * Draw preview lines (DRAW mode only).
 * Shows:
 * 1. Continuous solid line (exact cursor position)
 * 2. Snap guidelines for each active snap (may be multiple for cross-point snaps)
 * 3. Alignment guidelines (for VertexAlignment/AxisAlignment snaps)
 */
private fun DrawScope.drawPreviewLines(
    previewEnd: Point2?,
    snapResultWithGuidelines: SnapResultWithGuidelines?,
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

    // 2. Draw all snap guidelines from the result
    val config = drawingState.drawingConfig
    val guidelineStroke = config.guidelineStrokeWidthPx(density)
    val guidelineDash = PathEffect.dashPathEffect(config.guidelineDashPattern())

    if (snapResultWithGuidelines == null || snapResultWithGuidelines.guidelines.isEmpty()) return

    // Draw the snap position indicator (small circle at intersection point)
    val snapPointScreen =
        CoordinateConversion.worldToScreen(
            snapResultWithGuidelines.snapPosition,
            drawingState.cameraTransform,
        )
    drawCircle(
        color = config.alignmentGuidelineColorCompose(),
        radius = 6f * density.density,
        center = snapPointScreen,
    )

    // Draw each guideline
    for (snapResult in snapResultWithGuidelines.guidelines) {
        drawSingleGuideline(
            snapResult = snapResult,
            activeVertex = activeVertex,
            drawingState = drawingState,
            config = config,
            guidelineStroke = guidelineStroke,
            guidelineDash = guidelineDash,
        )
    }
}

/**
 * Draw a single snap guideline based on its type.
 */
private fun DrawScope.drawSingleGuideline(
    snapResult: SnapResult,
    activeVertex: Vertex,
    drawingState: ProjectDrawingState,
    config: DrawingConfig,
    guidelineStroke: Float,
    guidelineDash: PathEffect,
) {
    val activeStart =
        CoordinateConversion.worldToScreen(
            activeVertex.position,
            drawingState.cameraTransform,
        )

    when (snapResult) {
        is SnapResult.VertexAlignment -> {
            // Draw alignment guideline from aligned vertex through snap position
            val alignedVertex = drawingState.vertices[snapResult.alignedVertexId] ?: return
            val alignedVertexScreen =
                CoordinateConversion.worldToScreen(
                    alignedVertex.position,
                    drawingState.cameraTransform,
                )
            val snapPointScreen =
                CoordinateConversion.worldToScreen(
                    snapResult.position,
                    drawingState.cameraTransform,
                )

            // Extend guideline beyond snap point for visibility
            val guidelineExtension = 50f
            val endScreen =
                if (snapResult.isHorizontal) {
                    Offset(snapPointScreen.x + guidelineExtension, alignedVertexScreen.y)
                } else {
                    Offset(alignedVertexScreen.x, snapPointScreen.y + guidelineExtension)
                }

            // Draw guideline from aligned vertex through snap point
            drawLine(
                color = config.alignmentGuidelineColorCompose(),
                start = alignedVertexScreen,
                end = endScreen,
                strokeWidth = guidelineStroke,
                pathEffect = guidelineDash,
            )
        }

        is SnapResult.AxisAlignment -> {
            // Draw axis alignment guideline from active vertex
            val snapPointScreen =
                CoordinateConversion.worldToScreen(
                    snapResult.position,
                    drawingState.cameraTransform,
                )

            // Extend guideline beyond snap point for visibility
            val guidelineExtension = 50f
            val endScreen =
                if (snapResult.isHorizontal) {
                    // Horizontal: extend along X axis from active vertex Y
                    val direction = if (snapPointScreen.x > activeStart.x) 1f else -1f
                    Offset(snapPointScreen.x + guidelineExtension * direction, activeStart.y)
                } else {
                    // Vertical: extend along Y axis from active vertex X
                    val direction = if (snapPointScreen.y > activeStart.y) 1f else -1f
                    Offset(activeStart.x, snapPointScreen.y + guidelineExtension * direction)
                }

            // Draw the alignment guideline (green)
            drawLine(
                color = config.alignmentGuidelineColorCompose(),
                start = activeStart,
                end = endScreen,
                strokeWidth = guidelineStroke,
                pathEffect = guidelineDash,
            )
        }

        is SnapResult.RightAngle -> {
            val end = CoordinateConversion.worldToScreen(snapResult.position, drawingState.cameraTransform)
            drawLine(
                color = config.snapGuidelineColorCompose(),
                start = activeStart,
                end = end,
                strokeWidth = guidelineStroke,
                pathEffect = guidelineDash,
            )
        }

        is SnapResult.Vertex -> {
            val end = CoordinateConversion.worldToScreen(snapResult.position, drawingState.cameraTransform)
            drawLine(
                color = config.snapGuidelineColorCompose(),
                start = activeStart,
                end = end,
                strokeWidth = guidelineStroke,
                pathEffect = guidelineDash,
            )
        }

        is SnapResult.Edge -> {
            val end = CoordinateConversion.worldToScreen(snapResult.position, drawingState.cameraTransform)
            drawLine(
                color = config.snapGuidelineColorCompose(),
                start = activeStart,
                end = end,
                strokeWidth = guidelineStroke,
                pathEffect = guidelineDash,
            )
        }

        is SnapResult.Perpendicular -> {
            val end = CoordinateConversion.worldToScreen(snapResult.position, drawingState.cameraTransform)
            drawLine(
                color = config.snapGuidelineColorCompose(),
                start = activeStart,
                end = end,
                strokeWidth = guidelineStroke,
                pathEffect = guidelineDash,
            )
        }

        is SnapResult.Grid, SnapResult.None -> {
            // No guideline to draw
        }
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

    // Calculate world coordinate offset to determine which lines are at 50cm marks
    val worldOffsetX = -camera.panX / camera.zoom
    val worldOffsetY = -camera.panY / camera.zoom

    val thinLineWidth = config.gridLineWidthPx(density)
    val thickLineWidth = thinLineWidth * 2f

    // Calculate the starting grid index in world space (which grid line we start with)
    val startIndexX = kotlin.math.floor(worldOffsetX / gridSize).toInt()
    val startIndexY = kotlin.math.floor(worldOffsetY / gridSize).toInt()

    // Vertical lines
    var x = gridOffsetX
    var index = 0
    while (x < size.width) {
        // Calculate actual grid line index in world space
        val gridLineIndexX = startIndexX + index
        // Every 5th grid line (0, 5, 10, 15...) gets a thicker line
        // Use Math.floorMod to handle negative indices correctly
        val isThickLine = kotlin.math.abs(gridLineIndexX) % 5 == 0

        drawLine(
            color = gridColor,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = if (isThickLine) thickLineWidth else thinLineWidth,
        )
        x += gridSizeScreen
        index++
    }

    // Horizontal lines
    var y = gridOffsetY
    index = 0
    while (y < size.height) {
        // Calculate actual grid line index in world space
        val gridLineIndexY = startIndexY + index
        // Every 5th grid line (0, 5, 10, 15...) gets a thicker line
        val isThickLine = kotlin.math.abs(gridLineIndexY) % 5 == 0

        drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = if (isThickLine) thickLineWidth else thinLineWidth,
        )
        y += gridSizeScreen
        index++
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

            is com.roomplanner.data.models.Constraint.Angle -> {
                // Phase 1.6.3: Angle constraints
                drawAngleConstraint(
                    constraint = constraint,
                    drawingState = drawingState,
                    camera = camera,
                    config = config,
                    density = density,
                )
            }

            is com.roomplanner.data.models.Constraint.Perpendicular -> {
                // Phase 1.6.3: Perpendicular constraints (90° angles)
                drawPerpendicularConstraint(
                    constraint = constraint,
                    drawingState = drawingState,
                    camera = camera,
                    config = config,
                    density = density,
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
 * Draw angle constraint indicator (arc + label).
 * Phase 1.6.3: Shows angle constraints visually.
 */
private fun DrawScope.drawAngleConstraint(
    constraint: com.roomplanner.data.models.Constraint.Angle,
    drawingState: ProjectDrawingState,
    camera: CameraTransform,
    config: DrawingConfig,
    density: Density,
) {
    val line1 = drawingState.lines.find { it.id == constraint.lineId1 } ?: return
    val line2 = drawingState.lines.find { it.id == constraint.lineId2 } ?: return

    // Find shared vertex (where lines meet)
    val sharedVertexId =
        when {
            line1.startVertexId == line2.startVertexId -> line1.startVertexId
            line1.startVertexId == line2.endVertexId -> line1.startVertexId
            line1.endVertexId == line2.startVertexId -> line1.endVertexId
            line1.endVertexId == line2.endVertexId -> line1.endVertexId
            else -> return // Lines don't share a vertex
        }

    val vertex = drawingState.vertices[sharedVertexId] ?: return
    val v1Start = drawingState.vertices[line1.startVertexId] ?: return
    val v1End = drawingState.vertices[line1.endVertexId] ?: return
    val v2Start = drawingState.vertices[line2.startVertexId] ?: return
    val v2End = drawingState.vertices[line2.endVertexId] ?: return

    // Calculate angle between lines
    val dx1 =
        if (line1.startVertexId == sharedVertexId) {
            v1End.position.x - v1Start.position.x
        } else {
            v1Start.position.x - v1End.position.x
        }
    val dy1 =
        if (line1.startVertexId == sharedVertexId) {
            v1End.position.y - v1Start.position.y
        } else {
            v1Start.position.y - v1End.position.y
        }

    val dx2 =
        if (line2.startVertexId == sharedVertexId) {
            v2End.position.x - v2Start.position.x
        } else {
            v2Start.position.x - v2End.position.x
        }
    val dy2 =
        if (line2.startVertexId == sharedVertexId) {
            v2End.position.y - v2Start.position.y
        } else {
            v2Start.position.y - v2End.position.y
        }

    val angle1 = kotlin.math.atan2(dy1, dx1)
    val angle2 = kotlin.math.atan2(dy2, dx2)
    val currentAngleDegrees = kotlin.math.abs((angle2 - angle1) * 180.0 / kotlin.math.PI)

    val error = kotlin.math.abs(currentAngleDegrees - constraint.angle)
    val tolerance = 1.0 // 1 degree tolerance

    val constraintColor =
        when {
            error < tolerance -> config.constraintSatisfiedColorCompose()
            error < 5.0 -> config.constraintSolvingColorCompose()
            else -> config.constraintViolatedColorCompose()
        }

    // Draw arc indicator at vertex
    val vertexScreen = CoordinateConversion.worldToScreen(vertex.position, camera)
    val arcRadius = config.constraintIndicatorRadiusPx(density) * 2.0f

    drawCircle(
        color = constraintColor,
        radius = config.constraintIndicatorRadiusPx(density),
        center = vertexScreen,
        style =
            androidx.compose.ui.graphics.drawscope
                .Stroke(width = 2f),
    )
}

/**
 * Draw perpendicular constraint indicator (90° angle arc).
 * Phase 1.6.3: Shows auto-detected and user-set perpendicular constraints.
 */
private fun DrawScope.drawPerpendicularConstraint(
    constraint: com.roomplanner.data.models.Constraint.Perpendicular,
    drawingState: ProjectDrawingState,
    camera: CameraTransform,
    config: DrawingConfig,
    density: Density,
) {
    val line1 = drawingState.lines.find { it.id == constraint.lineId1 } ?: return
    val line2 = drawingState.lines.find { it.id == constraint.lineId2 } ?: return

    // Find shared vertex (where lines meet)
    val sharedVertexId =
        when {
            line1.startVertexId == line2.startVertexId -> line1.startVertexId
            line1.startVertexId == line2.endVertexId -> line1.startVertexId
            line1.endVertexId == line2.startVertexId -> line1.endVertexId
            line1.endVertexId == line2.endVertexId -> line1.endVertexId
            else -> return // Lines don't share a vertex
        }

    val vertex = drawingState.vertices[sharedVertexId] ?: return
    val v1Start = drawingState.vertices[line1.startVertexId] ?: return
    val v1End = drawingState.vertices[line1.endVertexId] ?: return
    val v2Start = drawingState.vertices[line2.startVertexId] ?: return
    val v2End = drawingState.vertices[line2.endVertexId] ?: return

    // Calculate angle between lines
    val dx1 =
        if (line1.startVertexId == sharedVertexId) {
            v1End.position.x - v1Start.position.x
        } else {
            v1Start.position.x - v1End.position.x
        }
    val dy1 =
        if (line1.startVertexId == sharedVertexId) {
            v1End.position.y - v1Start.position.y
        } else {
            v1Start.position.y - v1End.position.y
        }

    val dx2 =
        if (line2.startVertexId == sharedVertexId) {
            v2End.position.x - v2Start.position.x
        } else {
            v2Start.position.x - v2End.position.x
        }
    val dy2 =
        if (line2.startVertexId == sharedVertexId) {
            v2End.position.y - v2Start.position.y
        } else {
            v2Start.position.y - v2End.position.y
        }

    val angle1 = kotlin.math.atan2(dy1, dx1)
    val angle2 = kotlin.math.atan2(dy2, dx2)

    // Calculate relative angle
    var relativeAngle = angle2 - angle1
    while (relativeAngle < 0) relativeAngle += 2 * kotlin.math.PI
    while (relativeAngle >= 2 * kotlin.math.PI) relativeAngle -= 2 * kotlin.math.PI
    val currentAngleDegrees = kotlin.math.abs(relativeAngle * 180.0 / kotlin.math.PI)

    // Check if it's actually 90° (within tolerance)
    val is90Deg =
        (currentAngleDegrees >= 85.0 && currentAngleDegrees <= 95.0) ||
            (currentAngleDegrees >= 265.0 && currentAngleDegrees <= 275.0)

    val constraintColor =
        if (is90Deg) {
            config.constraintSatisfiedColorCompose()
        } else {
            config.constraintViolatedColorCompose()
        }

    // Draw right-angle indicator (small square in corner)
    val vertexScreen = CoordinateConversion.worldToScreen(vertex.position, camera)
    val squareSize = config.constraintIndicatorRadiusPx(density) * 1.5f

    drawCircle(
        color = constraintColor,
        radius = config.constraintIndicatorRadiusPx(density) * 0.7f,
        center = vertexScreen,
        style =
            androidx.compose.ui.graphics.drawscope
                .Stroke(width = 2f),
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
