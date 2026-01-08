package com.roomplanner.data.models

import kotlinx.serialization.Serializable

/**
 * Global application state.
 * Immutable data class with copy semantics.
 */
@Serializable
data class AppState(
    val currentMode: AppMode = AppMode.ProjectBrowser,
    val settings: Settings = Settings.default(),
    val currentProjectId: String? = null
) {
    companion object {
        fun initial() = AppState()
    }
}
