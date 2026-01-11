package com.roomplanner.ui.utils

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import com.roomplanner.data.models.CameraTransform
import com.roomplanner.data.models.DrawingConfig
import com.roomplanner.data.models.ProjectDrawingState
import com.roomplanner.ui.utils.dpToPx

/**
 * Hit testing utilities for DrawingCanvas.
 * Pure functions - determine what user tapped/clicked.
 *
 * Design rationale:
 * - Extracted from DrawingCanvas to reduce complexity
 * - Pure functions → testable in isolation
 * - Consistent hit detection across all tool modes
 */
object HitTesting {
    /**
     * Find vertex near tap position (screen-space distance).
     * Uses density-independent selection radius (44dp).
     *
     * @param tapScreen Tap position in screen coordinates
     * @param drawingState Current drawing state (vertices)
     * @param camera Camera transform (for world→screen conversion)
     * @param density Screen density (for dp→px conversion)
     * @param config Drawing configuration (selection radius)
     * @return Vertex ID if found, null otherwise
     */
    fun findVertexAt(
        tapScreen: Offset,
        drawingState: ProjectDrawingState,
        camera: CameraTransform,
        density: Density,
        config: DrawingConfig,
    ): String? {
        val selectionRadius = config.selectionRadiusPx(density)
        var nearestVertexId: String? = null
        var minDistance = Float.MAX_VALUE

        drawingState.vertices.values.forEach { vertex ->
            val vertexScreen = CoordinateConversion.worldToScreen(vertex.position, camera)
            val distance = CoordinateConversion.distanceScreen(tapScreen, vertexScreen)

            if (distance < selectionRadius && distance < minDistance) {
                minDistance = distance
                nearestVertexId = vertex.id
            }
        }

        return nearestVertexId
    }

    /**
     * Find line near tap position (world-space distance).
     * Uses line stroke width + margin as selection tolerance.
     *
     * @param tapScreen Tap position in screen coordinates
     * @param drawingState Current drawing state (lines, vertices)
     * @param camera Camera transform (for screen→world conversion)
     * @param density Screen density (for dp→px conversion)
     * @param config Drawing configuration (line stroke width)
     * @return Line ID if found, null otherwise
     */
    fun findLineAt(
        tapScreen: Offset,
        drawingState: ProjectDrawingState,
        camera: CameraTransform,
        density: Density,
        config: DrawingConfig,
    ): String? {
        val tapWorld = CoordinateConversion.screenToWorld(tapScreen, camera)

        // Selection tolerance: line stroke width + extra margin (in world units)
        val strokeWidthWorld = config.lineStrokeWidthDp.dpToPx(density) / camera.zoom
        val selectionTolerance = strokeWidthWorld + (8f / camera.zoom)

        var nearestLineId: String? = null
        var minDistance = Double.MAX_VALUE

        drawingState.lines.forEach { line ->
            // Compute geometry from current vertex positions
            val geometry = line.getGeometry(drawingState.vertices)
            val distanceWorld = geometry.distanceToPoint(tapWorld)

            if (distanceWorld < selectionTolerance && distanceWorld < minDistance) {
                minDistance = distanceWorld
                nearestLineId = line.id
            }
        }

        return nearestLineId
    }
}
