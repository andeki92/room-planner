package com.roomplanner.ui.gestures

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import co.touchlab.kermit.Logger
import com.roomplanner.data.events.EventBus
import com.roomplanner.data.events.GeometryEvent
import com.roomplanner.data.models.CameraTransform
import com.roomplanner.data.models.ProjectDrawingState
import com.roomplanner.data.models.SnapResult
import com.roomplanner.domain.geometry.Point2
import com.roomplanner.domain.snapping.SmartSnapSystem
import com.roomplanner.ui.utils.CoordinateConversion

/**
 * Gesture handler for DRAW mode (press-drag-release vertex placement).
 *
 * Behavior:
 * 1. Press: Show preview line from active vertex to cursor
 * 2. Drag: Update preview line to follow cursor, show snap hint when within tolerance
 * 3. Release: Place vertex at cursor position (snapped if hint showing)
 * 4. Tap: Ignored (DRAW mode uses press-drag-release, not tap)
 *
 * Design rationale:
 * - Isolated from DrawingCanvas (single responsibility)
 * - Uses pure utilities (CoordinateConversion, SmartSnapSystem)
 * - Emits events for committed actions only (not intermediate states)
 * - Updates local UI state via callbacks (preview, snap hint)
 */
class DrawToolGestureHandler : ToolGestureHandler {
    override suspend fun handlePress(
        screenPosition: Offset,
        drawingState: ProjectDrawingState,
        camera: CameraTransform,
        density: Density,
        eventBus: EventBus,
        onPreview: (Point2?) -> Unit,
        onSnapHint: (Point2?) -> Unit,
    ) {
        val worldPoint = CoordinateConversion.screenToWorld(screenPosition, camera)

        // Only show preview if there's an active vertex to draw from
        if (drawingState.activeVertexId != null) {
            // Show preview at initial press position
            onPreview(worldPoint)

            // Calculate snap hint
            val snapResult =
                SmartSnapSystem.calculateSnap(
                    cursorWorld = worldPoint,
                    cursorScreen = screenPosition,
                    drawingState = drawingState,
                    camera = camera,
                    density = density,
                )

            // Update snap hint (only for "real" snaps, not grid/none)
            onSnapHint(
                when (snapResult) {
                    is SnapResult.RightAngle -> snapResult.position
                    is SnapResult.Vertex -> snapResult.position
                    is SnapResult.Edge -> snapResult.position
                    is SnapResult.Perpendicular -> snapResult.position
                    else -> null
                },
            )
        }

        Logger.d { "→ DRAW: Press at screen (${screenPosition.x}, ${screenPosition.y})" }
    }

    override suspend fun handleDrag(
        screenPosition: Offset,
        dragDelta: Offset,
        drawingState: ProjectDrawingState,
        camera: CameraTransform,
        density: Density,
        eventBus: EventBus,
        onPreview: (Point2?) -> Unit,
        onSnapHint: (Point2?) -> Unit,
    ) {
        // Only show preview/snap hints if there's an active vertex to draw from
        if (drawingState.activeVertexId == null) return

        val worldPoint = CoordinateConversion.screenToWorld(screenPosition, camera)

        // ALWAYS update preview to exact cursor position (continuous line)
        onPreview(worldPoint)

        // Calculate snap hint during drag
        val snapResult =
            SmartSnapSystem.calculateSnap(
                cursorWorld = worldPoint,
                cursorScreen = screenPosition,
                drawingState = drawingState,
                camera = camera,
                density = density,
            )

        // Show snap hint ONLY when snap would apply (dotted orange line)
        onSnapHint(
            when (snapResult) {
                is SnapResult.RightAngle -> snapResult.position
                is SnapResult.Vertex -> snapResult.position
                is SnapResult.Edge -> snapResult.position
                is SnapResult.Perpendicular -> snapResult.position
                else -> null
            },
        )
    }

    override suspend fun handleRelease(
        screenPosition: Offset,
        drawingState: ProjectDrawingState,
        camera: CameraTransform,
        density: Density,
        eventBus: EventBus,
        onPreview: (Point2?) -> Unit,
        onSnapHint: (Point2?) -> Unit,
    ) {
        val worldPoint = CoordinateConversion.screenToWorld(screenPosition, camera)

        // Calculate final snap (works even without active vertex)
        val snapResult =
            SmartSnapSystem.calculateSnap(
                cursorWorld = worldPoint,
                cursorScreen = screenPosition,
                drawingState = drawingState,
                camera = camera,
                density = density,
            )

        // Use snap result if available, otherwise use exact cursor position
        val finalPosition =
            when (snapResult) {
                is SnapResult.RightAngle -> snapResult.position
                is SnapResult.Vertex -> snapResult.position
                is SnapResult.Edge -> snapResult.position
                is SnapResult.Perpendicular -> snapResult.position
                else -> worldPoint // Exact position (no snap or grid snap)
            }

        val snapType =
            if (snapResult !is SnapResult.None && snapResult !is SnapResult.Grid) {
                "snapped"
            } else {
                "exact"
            }

        val isFirstVertex = drawingState.activeVertexId == null
        Logger.i {
            if (isFirstVertex) {
                "✓ DRAW: First vertex placed ($snapType): (${finalPosition.x}, ${finalPosition.y})"
            } else {
                "✓ DRAW: Vertex placed ($snapType): (${finalPosition.x}, ${finalPosition.y})"
            }
        }

        // Emit event (GeometryManager will handle state update)
        // This works for both first vertex and subsequent vertices
        eventBus.emit(
            GeometryEvent.PointPlaced(
                position = finalPosition,
                snappedTo = if (snapResult is SnapResult.Vertex) snapResult.vertexId else null,
            ),
        )

        // Clear preview states
        onPreview(null)
        onSnapHint(null)
    }

    override suspend fun handleTap(
        screenPosition: Offset,
        drawingState: ProjectDrawingState,
        camera: CameraTransform,
        density: Density,
        eventBus: EventBus,
    ) {
        // Tap places vertex immediately (same logic as handleRelease)
        val worldPoint = CoordinateConversion.screenToWorld(screenPosition, camera)

        Logger.d {
            "→ DRAW: Tap at screen (${screenPosition.x}, ${screenPosition.y}) → " +
                "world (${worldPoint.x.toInt()}, ${worldPoint.y.toInt()}) " +
                "(camera: pan=${camera.panX},${camera.panY}, zoom=${camera.zoom})"
        }

        // Calculate final snap
        val snapResult =
            SmartSnapSystem.calculateSnap(
                cursorWorld = worldPoint,
                cursorScreen = screenPosition,
                drawingState = drawingState,
                camera = camera,
                density = density,
            )

        // Use snap result if available, otherwise use exact cursor position
        val finalPosition =
            when (snapResult) {
                is SnapResult.RightAngle -> snapResult.position
                is SnapResult.Vertex -> snapResult.position
                is SnapResult.Edge -> snapResult.position
                is SnapResult.Perpendicular -> snapResult.position
                else -> worldPoint
            }

        val snapType =
            if (snapResult !is SnapResult.None && snapResult !is SnapResult.Grid) {
                "snapped"
            } else {
                "exact"
            }

        val isFirstVertex = drawingState.activeVertexId == null
        Logger.i {
            if (isFirstVertex) {
                "✓ DRAW: First vertex placed via tap ($snapType): (${finalPosition.x}, ${finalPosition.y})"
            } else {
                "✓ DRAW: Vertex placed via tap ($snapType): (${finalPosition.x}, ${finalPosition.y})"
            }
        }

        // Emit event
        eventBus.emit(
            GeometryEvent.PointPlaced(
                position = finalPosition,
                snappedTo = if (snapResult is SnapResult.Vertex) snapResult.vertexId else null,
            ),
        )
    }
}
