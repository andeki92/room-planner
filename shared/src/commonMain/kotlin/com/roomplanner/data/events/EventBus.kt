package com.roomplanner.data.events

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Global event bus for decoupled communication.
 * Tools emit events, systems react.
 *
 * Usage:
 * ```
 * // Emit event
 * eventBus.emit(NavigationEvent.ModeChanged(AppMode.FloorPlan("project-123")))
 *
 * // Listen to events
 * eventBus.events
 *     .filterIsInstance<NavigationEvent>()
 *     .onEach { handleEvent(it) }
 *     .launchIn(scope)
 * ```
 */
class EventBus {
    private val _events = MutableSharedFlow<AppEvent>(
        replay = 0,                    // Don't replay past events
        extraBufferCapacity = 64,      // Buffer 64 events before dropping
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val events: SharedFlow<AppEvent> = _events.asSharedFlow()

    suspend fun emit(event: AppEvent) {
        _events.emit(event)
    }
}
