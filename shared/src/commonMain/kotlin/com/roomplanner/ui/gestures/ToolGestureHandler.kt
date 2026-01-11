package com.roomplanner.ui.gestures

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import com.roomplanner.data.events.EventBus
import com.roomplanner.data.models.CameraTransform
import com.roomplanner.data.models.ProjectDrawingState
import com.roomplanner.domain.geometry.Point2

/**
 * Interface for tool-specific gesture handling.
 * Each ToolMode has its own implementation.
 *
 * Design rationale:
 * - Strategy pattern: different tools handle gestures differently
 * - Testable: each handler can be tested in isolation
 * - Extensible: add new tool modes by implementing this interface
 * - Clean separation: gesture detection vs business logic
 *
 * Lifecycle:
 * 1. Press (finger down)
 * 2. Drag (finger moves) - optional, may not fire for taps
 * 3. Release (finger up)
 * OR
 * 1. Tap (quick press-release, < 200ms, < 10px movement)
 *
 * Note: Callbacks update local UI state (preview, snap hints),
 *       handlers emit events for committed actions.
 */
interface ToolGestureHandler {
    /**
     * Handle press/drag start.
     *
     * Called when user first touches the screen.
     * Use this to:
     * - Show preview UI (e.g., preview line in DRAW mode)
     * - Start drag operation (e.g., vertex drag in SELECT mode)
     * - Calculate initial snap hint
     *
     * @param screenPosition Touch position in screen coordinates
     * @param drawingState Current drawing state (read-only)
     * @param camera Camera transform
     * @param density Screen density
     * @param eventBus Event bus for emitting events
     * @param onPreview Callback to update preview state (local UI state)
     * @param onSnapHint Callback to update snap hint state (local UI state)
     */
    suspend fun handlePress(
        screenPosition: Offset,
        drawingState: ProjectDrawingState,
        camera: CameraTransform,
        density: Density,
        eventBus: EventBus,
        onPreview: (Point2?) -> Unit,
        onSnapHint: (Point2?) -> Unit,
    )

    /**
     * Handle drag movement.
     *
     * Called repeatedly as user moves finger across screen.
     * Use this to:
     * - Update preview UI (e.g., follow cursor in DRAW mode)
     * - Move vertex in SELECT mode
     * - Recalculate snap hint
     *
     * @param screenPosition Current touch position
     * @param dragDelta Delta since last drag event (screen pixels)
     */
    suspend fun handleDrag(
        screenPosition: Offset,
        dragDelta: Offset,
        drawingState: ProjectDrawingState,
        camera: CameraTransform,
        density: Density,
        eventBus: EventBus,
        onPreview: (Point2?) -> Unit,
        onSnapHint: (Point2?) -> Unit,
    )

    /**
     * Handle release/drag end.
     *
     * Called when user lifts finger after dragging.
     * Use this to:
     * - Commit operation (e.g., place vertex in DRAW mode)
     * - End drag operation (e.g., finalize vertex move in SELECT mode)
     * - Clear preview UI
     *
     * @param screenPosition Final touch position
     */
    suspend fun handleRelease(
        screenPosition: Offset,
        drawingState: ProjectDrawingState,
        camera: CameraTransform,
        density: Density,
        eventBus: EventBus,
        onPreview: (Point2?) -> Unit,
        onSnapHint: (Point2?) -> Unit,
    )

    /**
     * Handle tap (quick press-release without drag).
     *
     * Called when user taps screen (< 200ms, < 10px movement).
     * Use this to:
     * - Show radial menu (SELECT mode)
     * - Quick-place vertex without drag (future enhancement)
     *
     * @param screenPosition Tap position
     */
    suspend fun handleTap(
        screenPosition: Offset,
        drawingState: ProjectDrawingState,
        camera: CameraTransform,
        density: Density,
        eventBus: EventBus,
    )

    /**
     * Handle long press (optional, default no-op).
     *
     * Called when user holds finger down (> 500ms, < 10px movement).
     * Use this for context menus, special modes, etc.
     *
     * @param screenPosition Long press position
     */
    suspend fun handleLongPress(
        screenPosition: Offset,
        drawingState: ProjectDrawingState,
        camera: CameraTransform,
        density: Density,
        eventBus: EventBus,
    ) {
        // Default: no-op (optional for tools)
    }
}
