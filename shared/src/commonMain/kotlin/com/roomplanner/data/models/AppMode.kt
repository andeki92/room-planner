package com.roomplanner.data.models

import kotlinx.serialization.Serializable

/**
 * Application modes for Room Planner.
 * Each mode represents a distinct UI state with specific features.
 *
 * Mode Transitions:
 * - ProjectBrowser → FloorPlan (when project selected)
 * - FloorPlan → Settings (settings button)
 * - Settings → ProjectBrowser (back button)
 */
@Serializable
sealed interface AppMode {
    /**
     * Project Browser: Grid of recent projects, create new project
     */
    @Serializable
    data object ProjectBrowser : AppMode

    /**
     * Floor Plan Mode: CAD drawing (Phase 1+ implementation)
     */
    @Serializable
    data class FloorPlan(val projectId: String) : AppMode

    /**
     * Settings: User preferences (units, theme, etc.)
     */
    @Serializable
    data object Settings : AppMode
}
