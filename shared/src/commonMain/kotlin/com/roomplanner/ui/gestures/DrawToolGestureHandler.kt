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
        onSnapHint: (SnapResultWithGuidelines?) -> Unit,
    ) {
        val worldPoint = CoordinateConversion.screenToWorld(screenPosition, camera)

        // Only show preview if there's an active vertex to draw from
        if (drawingState.activeVertexId != null) {
            // Show preview at initial press position
            onPreview(worldPoint)

            // Calculate snap with guidelines (supports multiple simultaneous snaps)
            val snapResult =
                SmartSnapSystem.calculateSnapWithGuidelines(
                    cursorWorld = worldPoint,
                    cursorScreen = screenPosition,
                    drawingState = drawingState,
                    camera = camera,
                    density = density,
                )

            // Update snap hint (show guidelines if any)
            onSnapHint(if (snapResult.guidelines.isNotEmpty()) snapResult else null)
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
        onSnapHint: (SnapResultWithGuidelines?) -> Unit,
    ) {
        // Only show preview/snap hints if there's an active vertex to draw from
        if (drawingState.activeVertexId == null) return

        val worldPoint = CoordinateConversion.screenToWorld(screenPosition, camera)

        // ALWAYS update preview to exact cursor position (continuous line)
        onPreview(worldPoint)

        // Calculate snap with guidelines (supports multiple simultaneous snaps)
        val snapResult =
            SmartSnapSystem.calculateSnapWithGuidelines(
                cursorWorld = worldPoint,
                cursorScreen = screenPosition,
                drawingState = drawingState,
                camera = camera,
                density = density,
            )

        // Show snap hint (guidelines) if any
        onSnapHint(if (snapResult.guidelines.isNotEmpty()) snapResult else null)
    }

    override suspend fun handleRelease(
        screenPosition: Offset,
        drawingState: ProjectDrawingState,
        camera: CameraTransform,
        density: Density,
        eventBus: EventBus,
        onPreview: (Point2?) -> Unit,
        onSnapHint: (SnapResultWithGuidelines?) -> Unit,
    ) {
        val worldPoint = CoordinateConversion.screenToWorld(screenPosition, camera)

        // Calculate final snap with guidelines
        val snapResult =
            SmartSnapSystem.calculateSnapWithGuidelines(
                cursorWorld = worldPoint,
                cursorScreen = screenPosition,
                drawingState = drawingState,
                camera = camera,
                density = density,
            )

        // Use snap position (which may be intersection of multiple guidelines)
        val finalPosition =
            if (snapResult.guidelines.isNotEmpty()) {
                snapResult.snapPosition
            } else {
                worldPoint
            }

        val snapType = if (snapResult.guidelines.isNotEmpty()) "snapped" else "exact"

        val isFirstVertex = drawingState.activeVertexId == null
        Logger.i {
            if (isFirstVertex) {
                "✓ DRAW: First vertex placed ($snapType): (${finalPosition.x}, ${finalPosition.y})"
            } else {
                "✓ DRAW: Vertex placed ($snapType): (${finalPosition.x}, ${finalPosition.y})"
            }
        }

        // Check if snapped to vertex for closing shapes
        val snappedToVertexId =
            snapResult.guidelines
                .filterIsInstance<SnapResult.Vertex>()
                .firstOrNull()
                ?.vertexId

        // Emit event (GeometryManager will handle state update)
        eventBus.emit(
            GeometryEvent.PointPlaced(
                position = finalPosition,
                snappedTo = snappedToVertexId,
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
        onPreview: (Point2?) -> Unit,
        onSnapHint: (SnapResultWithGuidelines?) -> Unit,
    ) {
        // Tap places vertex immediately (same logic as handleRelease)
        val worldPoint = CoordinateConversion.screenToWorld(screenPosition, camera)

        // Calculate final snap with guidelines
        val snapResult =
            SmartSnapSystem.calculateSnapWithGuidelines(
                cursorWorld = worldPoint,
                cursorScreen = screenPosition,
                drawingState = drawingState,
                camera = camera,
                density = density,
            )

        // Use snap position (which may be intersection of multiple guidelines)
        val finalPosition =
            if (snapResult.guidelines.isNotEmpty()) {
                snapResult.snapPosition
            } else {
                worldPoint
            }

        val snapType = if (snapResult.guidelines.isNotEmpty()) "snapped" else "exact"

        val isFirstVertex = drawingState.activeVertexId == null
        Logger.i {
            if (isFirstVertex) {
                "✓ DRAW: First vertex placed via tap ($snapType): (${finalPosition.x}, ${finalPosition.y})"
            } else {
                "✓ DRAW: Vertex placed via tap ($snapType): (${finalPosition.x}, ${finalPosition.y})"
            }
        }

        // Check if snapped to vertex for closing shapes
        val snappedToVertexId =
            snapResult.guidelines
                .filterIsInstance<SnapResult.Vertex>()
                .firstOrNull()
                ?.vertexId

        // Emit event
        eventBus.emit(
            GeometryEvent.PointPlaced(
                position = finalPosition,
                snappedTo = snappedToVertexId,
            ),
        )

        // Clear preview states (matches handleRelease pattern)
        onPreview(null)
        onSnapHint(null)
    }
}
