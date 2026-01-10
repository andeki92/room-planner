package com.roomplanner.data.models

import kotlinx.serialization.Serializable

/**
 * Drawing tool modes for FloorPlan mode.
 *
 * Design rationale:
 * - Explicit mode switching (not implicit/magical)
 * - Two modes: Draw (place vertices) and Select (move/delete vertices)
 * - Pan/zoom remain as gestures (no separate pan tool)
 * - Mode persisted per-project (part of ProjectDrawingState)
 */
@Serializable
enum class ToolMode {
    /**
     * Draw mode: Tap to place vertices, continuous line drawing.
     * Ignores existing vertices (tapping on vertex places new vertex at that location).
     */
    DRAW,

    /**
     * Select mode: Tap to select vertices, drag to move, long-press for context menu.
     * Selection features from Phase 1.4 only work in this mode.
     */
    SELECT
}
