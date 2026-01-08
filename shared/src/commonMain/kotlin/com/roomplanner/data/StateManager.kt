package com.roomplanner.data

import com.roomplanner.data.models.AppMode
import com.roomplanner.data.models.AppState
import com.roomplanner.data.models.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Central state manager using StateFlow for reactive updates.
 * Immutable state with copy-on-write semantics.
 */
class StateManager {
    private val _state = MutableStateFlow(AppState.initial())
    val state: StateFlow<AppState> = _state.asStateFlow()

    /**
     * Update state using reducer function.
     * Example: stateManager.updateState { it.copy(currentMode = AppMode.Settings) }
     */
    fun updateState(reducer: (AppState) -> AppState) {
        _state.update(reducer)
    }

    /**
     * Set current mode (convenience method)
     */
    fun setMode(mode: AppMode) {
        updateState { it.copy(currentMode = mode) }
    }

    /**
     * Update settings (convenience method)
     */
    fun updateSettings(settings: Settings) {
        updateState { it.copy(settings = settings) }
    }
}
