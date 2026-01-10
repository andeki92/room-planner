package com.roomplanner.domain.snapping

import androidx.compose.ui.unit.Density
import co.touchlab.kermit.Logger
import com.roomplanner.data.models.CameraTransform
import com.roomplanner.data.models.ProjectDrawingState
import com.roomplanner.data.models.SnapResult
import com.roomplanner.domain.geometry.Point2
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * SmartSnapSystem calculates optimal snap target for cursor position.
 *
 * Priority order:
 * 1. Vertex snap (highest priority - closes shapes)
 * 2. Edge snap (snap to points on edges)
 * 3. Perpendicular snap (90° constraint from active vertex)
 * 4. Grid snap (fallback)
 *
 * All snap detection uses screen-space distance (pixels) for
 * consistent behavior regardless of zoom level.
 */
object SmartSnapSystem {
    /**
     * Calculate best snap target for given cursor position.
     *
     * @param cursorWorld cursor position in world coordinates
     * @param cursorScreen cursor position in screen coordinates
     * @param drawingState current drawing state (vertices, lines, settings)
     * @param camera camera transform (for world↔screen conversion)
     * @param density screen density for dp→px conversion
     * @return SnapResult describing what to snap to
     */
    fun calculateSnap(
        cursorWorld: Point2,
        cursorScreen: androidx.compose.ui.geometry.Offset,
        drawingState: ProjectDrawingState,
        camera: CameraTransform,
        density: Density,
    ): SnapResult {
        val settings = drawingState.snapSettings

        // Priority 1: Vertex snap
        if (settings.vertexSnapEnabled) {
            val vertexSnap =
                findNearestVertex(
                    cursorScreen,
                    drawingState,
                    camera,
                    settings.vertexSnapRadius(density),
                )
            if (vertexSnap != null) {
                Logger.d { "→ Snap: Vertex ${vertexSnap.vertexId}" }
                return vertexSnap
            }
        }

        // Priority 2: Edge snap
        if (settings.edgeSnapEnabled) {
            val edgeSnap =
                findNearestEdgePoint(
                    cursorScreen,
                    drawingState,
                    camera,
                    settings.edgeSnapRadius(density),
                )
            if (edgeSnap != null) {
                Logger.d { "→ Snap: Edge ${edgeSnap.lineId} at t=${edgeSnap.t}" }
                return edgeSnap
            }
        }

        // Priority 3: Perpendicular snap (if active vertex exists)
        if (settings.perpendicularSnapEnabled && drawingState.activeVertexId != null) {
            val perpSnap =
                findPerpendicularSnap(
                    cursorWorld,
                    drawingState,
                    settings.perpendicularAngleTolerance,
                )
            if (perpSnap != null) {
                Logger.d { "→ Snap: Perpendicular to ${perpSnap.lineId}" }
                return perpSnap
            }
        }

        // Priority 4: Grid snap (fallback)
        if (settings.gridEnabled) {
            val gridSnap = snapToGrid(cursorWorld, settings.gridSize)
            Logger.d { "→ Snap: Grid at (${gridSnap.position.x}, ${gridSnap.position.y})" }
            return gridSnap
        }

        return SnapResult.None
    }

    /**
     * Find nearest vertex within snap radius (screen space).
     */
    private fun findNearestVertex(
        cursorScreen: androidx.compose.ui.geometry.Offset,
        drawingState: ProjectDrawingState,
        camera: CameraTransform,
        snapRadius: Float,
    ): SnapResult.Vertex? {
        var nearestVertex: SnapResult.Vertex? = null
        var minDistance = Float.MAX_VALUE

        drawingState.vertices.values.forEach { vertex ->
            val vertexScreen = worldToScreen(vertex.position, camera)
            val distance = distanceScreen(cursorScreen, vertexScreen)

            if (distance < snapRadius && distance < minDistance) {
                minDistance = distance
                nearestVertex =
                    SnapResult.Vertex(
                        vertexId = vertex.id,
                        position = vertex.position,
                    )
            }
        }

        return nearestVertex
    }

    /**
     * Find nearest point on any edge within snap radius.
     */
    private fun findNearestEdgePoint(
        cursorScreen: androidx.compose.ui.geometry.Offset,
        drawingState: ProjectDrawingState,
        camera: CameraTransform,
        snapRadius: Float,
    ): SnapResult.Edge? {
        var nearestEdge: SnapResult.Edge? = null
        var minDistance = Float.MAX_VALUE

        drawingState.lines.forEach { line ->
            // Convert line endpoints to screen space
            val startScreen = worldToScreen(line.geometry.start, camera)
            val endScreen = worldToScreen(line.geometry.end, camera)

            // Find nearest point on line segment (in screen space)
            val (nearestPoint, t) =
                nearestPointOnSegment(
                    cursorScreen,
                    startScreen,
                    endScreen,
                )

            val distance = distanceScreen(cursorScreen, nearestPoint)

            if (distance < snapRadius && distance < minDistance) {
                // Convert back to world space
                val worldPoint = screenToWorld(nearestPoint, camera)

                minDistance = distance
                nearestEdge =
                    SnapResult.Edge(
                        lineId = line.id,
                        position = worldPoint,
                        t = t,
                    )
            }
        }

        return nearestEdge
    }

    /**
     * Find perpendicular snap from active vertex.
     * Checks if cursor is within angle tolerance of perpendicular to any edge.
     */
    private fun findPerpendicularSnap(
        cursorWorld: Point2,
        drawingState: ProjectDrawingState,
        angleTolerance: Double,
    ): SnapResult.Perpendicular? {
        val activeVertex = drawingState.getActiveVertex() ?: return null

        drawingState.lines.forEach { line ->
            // Calculate perpendicular from active vertex to this line
            val edgeVector =
                Point2(
                    line.geometry.end.x - line.geometry.start.x,
                    line.geometry.end.y - line.geometry.start.y,
                )
            val edgeAngle = atan2(edgeVector.y, edgeVector.x)
            val perpAngle = edgeAngle + (PI / 2) // 90° rotation

            // Vector from active vertex to cursor
            val toCursor =
                Point2(
                    cursorWorld.x - activeVertex.position.x,
                    cursorWorld.y - activeVertex.position.y,
                )
            val cursorAngle = atan2(toCursor.y, toCursor.x)

            // Check if angles are close (within tolerance)
            val angleDiff = abs(angleDifference(cursorAngle, perpAngle))
            val angleDegreeDiff = angleDiff * 180.0 / PI

            if (angleDegreeDiff < angleTolerance) {
                // Project cursor onto perpendicular ray
                val distance = sqrt(toCursor.x * toCursor.x + toCursor.y * toCursor.y)
                val snapPosition =
                    Point2(
                        activeVertex.position.x + cos(perpAngle) * distance,
                        activeVertex.position.y + sin(perpAngle) * distance,
                    )

                return SnapResult.Perpendicular(
                    lineId = line.id,
                    position = snapPosition,
                    angle = perpAngle * 180.0 / PI,
                )
            }
        }

        return null
    }

    /**
     * Grid snap (existing logic from Phase 1.1).
     */
    private fun snapToGrid(
        point: Point2,
        gridSize: Double,
    ): SnapResult.Grid {
        val x = (point.x / gridSize).toInt() * gridSize
        val y = (point.y / gridSize).toInt() * gridSize
        return SnapResult.Grid(Point2(x.toDouble(), y.toDouble()))
    }

    // ==================== Geometry Helpers ====================

    private fun worldToScreen(
        point: Point2,
        camera: CameraTransform,
    ): androidx.compose.ui.geometry.Offset {
        val x = (point.x.toFloat() * camera.zoom) + camera.panX
        val y = (point.y.toFloat() * camera.zoom) + camera.panY
        return androidx.compose.ui.geometry
            .Offset(x, y)
    }

    private fun screenToWorld(
        offset: androidx.compose.ui.geometry.Offset,
        camera: CameraTransform,
    ): Point2 {
        val x = ((offset.x - camera.panX) / camera.zoom).toDouble()
        val y = ((offset.y - camera.panY) / camera.zoom).toDouble()
        return Point2(x, y)
    }

    private fun distanceScreen(
        a: androidx.compose.ui.geometry.Offset,
        b: androidx.compose.ui.geometry.Offset,
    ): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        return sqrt(dx * dx + dy * dy)
    }

    /**
     * Find nearest point on line segment to given point.
     * @return Pair(nearest point, parameter t where 0=start, 1=end)
     */
    private fun nearestPointOnSegment(
        point: androidx.compose.ui.geometry.Offset,
        start: androidx.compose.ui.geometry.Offset,
        end: androidx.compose.ui.geometry.Offset,
    ): Pair<androidx.compose.ui.geometry.Offset, Double> {
        val dx = end.x - start.x
        val dy = end.y - start.y
        val lengthSquared = dx * dx + dy * dy

        if (lengthSquared == 0f) {
            // Degenerate line (start == end)
            return Pair(start, 0.0)
        }

        // Project point onto line: t = dot(point - start, end - start) / ||end - start||^2
        val t = ((point.x - start.x) * dx + (point.y - start.y) * dy) / lengthSquared

        // Clamp t to [0, 1] to stay on segment
        val tClamped = t.coerceIn(0f, 1f)

        val nearestPoint =
            androidx.compose.ui.geometry.Offset(
                start.x + tClamped * dx,
                start.y + tClamped * dy,
            )

        return Pair(nearestPoint, tClamped.toDouble())
    }

    /**
     * Calculate shortest angular difference between two angles.
     * Handles wrap-around (e.g., 359° and 1° are 2° apart, not 358°).
     */
    private fun angleDifference(
        a: Double,
        b: Double,
    ): Double {
        var diff = a - b
        while (diff > PI) diff -= 2 * PI
        while (diff < -PI) diff += 2 * PI
        return diff
    }
}
