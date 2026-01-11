package com.roomplanner.ui.gestures

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import kotlinx.coroutines.coroutineScope
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
 * - **Long Press**: Long hold (≥ 500ms, < 10px movement)
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
 * - Uses 500ms threshold for long press (iOS standard: 0.5s)
 * - Consumes pointer events to prevent conflicts
 */
suspend fun PointerInputScope.detectUnifiedGestures(callbacks: GestureCallbacks,) {
    coroutineScope {
        awaitPointerEventScope {
            while (true) {
                // Wait for press (finger down)
                val down = awaitPointerEvent(PointerEventPass.Initial)
                val downChange = down.changes.firstOrNull() ?: continue

                if (!downChange.pressed) continue

                val downPosition = downChange.position
                val downTime = down.changes.first().uptimeMillis
                var lastPosition = downPosition
                var totalDelta = Offset.Zero
                var isDragging = false

                // Notify press
                launch { callbacks.onPress(downPosition) }

                // Track movement until release
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)

                    // Check if pointer released (finger up)
                    if (event.changes.fastAny { !it.pressed }) {
                        val upChange = event.changes.first()
                        val upPosition = upChange.position
                        val upTime = upChange.uptimeMillis
                        val duration = upTime - downTime
                        val movement = totalDelta.getDistance()

                        // Consume the change
                        upChange.consume()

                        // Determine gesture type based on duration and movement
                        when {
                            // Tap: Quick release, minimal movement
                            duration < 200 && movement < 10f -> {
                                launch { callbacks.onTap(upPosition) }
                            }

                            // Long press: Long duration, minimal movement
                            duration >= 500 && movement < 10f -> {
                                launch { callbacks.onLongPress(upPosition) }
                            }

                            // Drag: Movement detected OR release after long press
                            isDragging || movement >= 10f -> {
                                launch { callbacks.onRelease(upPosition) }
                            }

                            // Edge case: Release without clear gesture (treat as release)
                            else -> {
                                launch { callbacks.onRelease(upPosition) }
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
                            }

                            // Emit drag events only after drag started
                            if (isDragging) {
                                launch { callbacks.onDrag(currentPosition, delta) }
                            }

                            lastPosition = currentPosition
                            change.consume()
                        }
                    }
                }
            }
        }
    }
}
