package com.roomplanner.data.models

import kotlinx.serialization.Serializable

/**
 * Global application state.
 * Immutable data class with copy semantics.
 *
 * Design rationale:
 * - Single source of truth for entire app
 * - Immutable state enables time-travel debugging and undo/redo
 * - Per-project drawing state (isolated between projects)
 * - Structural sharing minimizes memory allocations
 *
 * State architecture:
 * - App-level: mode, settings, current project ID
 * - Project-level: drawing data (vertices, lines, camera) stored in ProjectDrawingState
 */
@Serializable
data class AppState(
    val currentMode: AppMode = AppMode.ProjectBrowser,
    val settings: Settings = Settings.default(),
    val currentProjectId: String? = null,
    // Per-project drawing state (null when not in FloorPlan mode)
    val projectDrawingState: ProjectDrawingState? = null,
) {
    companion object {
        fun initial() = AppState()
    }

    /**
     * Helper: Load drawing state for a project
     */
    fun withProjectDrawing(drawingState: ProjectDrawingState): AppState = copy(projectDrawingState = drawingState)

    /**
     * Helper: Clear drawing state (when leaving FloorPlan mode)
     */
    fun clearProjectDrawing(): AppState = copy(projectDrawingState = null)

    /**
     * Helper: Update drawing state immutably
     */
    fun updateDrawingState(reducer: (ProjectDrawingState) -> ProjectDrawingState): AppState =
        copy(
            projectDrawingState = projectDrawingState?.let(reducer),
        )
}
