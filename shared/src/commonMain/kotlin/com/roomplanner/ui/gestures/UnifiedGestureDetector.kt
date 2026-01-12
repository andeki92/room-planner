package com.roomplanner.ui.gestures

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Gesture callbacks for unified gesture detector.
 *
 * Design rationale:
 * - Data class for easy passing and testing
 * - Suspend functions for async operations (event emission, state updates)
 * - Optional callbacks (default empty) for flexibility
 */
data class GestureCallbacks(
    val onPress: suspend (Offset) -> Unit = {},
    val onDrag: suspend (Offset, Offset) -> Unit = { _, _ -> },
    val onRelease: suspend (Offset) -> Unit = {},
    val onTap: suspend (Offset) -> Unit = {},
    val onLongPress: suspend (Offset) -> Unit = {},
    val onCancel: suspend () -> Unit = {},
)

/**
 * Detect unified gestures (tap, long press, drag).
 * This replaces detectTapGestures + detectDragGestures with a single smart detector.
 *
 * Gesture detection logic:
 * - **Tap**: Quick release (< 200ms, < 10px movement)
 * - **Long Press**: Hold ≥ 300ms with < 10px movement (fires on timeout, not release)
 * - **Drag**: Finger moves (≥ 10px), then releases
 *
 * Design rationale:
 * - Solves conflict between detectTapGestures and detectDragGestures
 * - Single gesture detector → no ambiguity, no race conditions
 * - Uses `awaitPointerEventScope` for full control over pointer events
 * - Properly distinguishes tap vs drag (standard detectors can't)
 *
 * Implementation notes:
 * - Uses 10px threshold to distinguish tap from drag (iOS guideline: 10pt)
 * - Uses 200ms threshold for tap (iOS standard: 0.2s)
 * - Uses 300ms threshold for long press - fires immediately when timer expires
 * - Consumes pointer events to prevent conflicts
 */
suspend fun PointerInputScope.detectUnifiedGestures(callbacks: GestureCallbacks) {
    coroutineScope {
        awaitPointerEventScope {
            while (true) {
                // Wait for press (finger down)
                val down = awaitPointerEvent(PointerEventPass.Initial)

                // Skip multi-touch events - let transform gestures handle zoom/pan
                if (down.changes.size > 1) continue

                val downChange = down.changes.firstOrNull() ?: continue

                if (!downChange.pressed) continue

                val downPosition = downChange.position
                val downTime = down.changes.first().uptimeMillis
                var lastPosition = downPosition
                var totalDelta = Offset.Zero
                var isDragging = false
                var longPressTriggered = false

                // Notify press
                launch { callbacks.onPress(downPosition) }

                // Start long press timer - fires after 300ms if not cancelled
                var longPressJob: Job? =
                    launch {
                        delay(300)
                        // Only trigger if we haven't moved much and haven't already triggered
                        if (totalDelta.getDistance() < 10f && !isDragging && !longPressTriggered) {
                            longPressTriggered = true
                            callbacks.onLongPress(lastPosition)
                        }
                    }

                // Track movement until release
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)

                    // Bail out if multi-touch detected (user added second finger for zoom/pan)
                    if (event.changes.count { it.pressed } > 1) {
                        longPressJob?.cancel()
                        longPressJob = null
                        launch { callbacks.onCancel() }
                        break
                    }

                    // Check if pointer released (finger up)
                    if (event.changes.fastAny { !it.pressed }) {
                        longPressJob?.cancel()
                        longPressJob = null

                        val upChange = event.changes.first()
                        val upPosition = upChange.position
                        val upTime = upChange.uptimeMillis
                        val duration = upTime - downTime
                        val movement = totalDelta.getDistance()

                        // Only consume single-pointer events to allow multi-touch passthrough
                        if (event.changes.size == 1) {
                            upChange.consume()
                        }

                        // Determine gesture type based on duration and movement
                        when {
                            // Tap: Quick release, minimal movement
                            duration < 200 && movement < 10f -> {
                                launch { callbacks.onTap(upPosition) }
                            }

                            // Drag: Movement detected - always call onRelease to place vertex
                            isDragging || movement >= 10f -> {
                                launch { callbacks.onRelease(upPosition) }
                            }

                            // Long press triggered - still call onRelease if user was drawing
                            // (SELECT mode radial menu handles its own release logic)
                            longPressTriggered -> {
                                // In DRAW mode, we still want to place the vertex on release
                                // The handler will decide whether to act on it
                                launch { callbacks.onRelease(upPosition) }
                            }

                            // Edge case: Release without clear gesture (treat as tap if short)
                            else -> {
                                if (duration < 300) {
                                    launch { callbacks.onTap(upPosition) }
                                } else {
                                    // Held for a while but didn't move - call onRelease
                                    launch { callbacks.onRelease(upPosition) }
                                }
                            }
                        }

                        break // Exit tracking loop
                    }

                    // Track drag movement
                    event.changes.fastForEach { change ->
                        if (change.pressed && change.positionChange() != Offset.Zero) {
                            val delta = change.positionChange()
                            totalDelta += delta
                            val currentPosition = change.position

                            // Start dragging if threshold exceeded
                            if (!isDragging && totalDelta.getDistance() >= 10f) {
                                isDragging = true
                                // Cancel long press timer if we start dragging
                                longPressJob?.cancel()
                                longPressJob = null
                            }

                            // Emit drag events only after drag started
                            if (isDragging) {
                                launch { callbacks.onDrag(currentPosition, delta) }
                            }

                            lastPosition = currentPosition

                            // Only consume single-pointer events to allow multi-touch passthrough
                            if (event.changes.size == 1) {
                                change.consume()
                            }
                        }
                    }
                }
            }
        }
    }
}
