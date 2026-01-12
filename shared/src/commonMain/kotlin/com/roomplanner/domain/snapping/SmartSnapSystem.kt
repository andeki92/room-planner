package com.roomplanner.domain.snapping

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import co.touchlab.kermit.Logger
import com.roomplanner.data.models.CameraTransform
import com.roomplanner.data.models.ProjectDrawingState
import com.roomplanner.data.models.SnapResult
import com.roomplanner.data.models.SnapResultWithGuidelines
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
 * 3. Perpendicular snap (90° constraint from last placed line)
 * 4. Right angle snap (0°, 90°, 180°, 270° from active vertex)
 * 5. Grid snap (fallback)
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
     * @param enableGridSnap whether to enable grid snapping (default true)
     * @return SnapResult describing what to snap to
     */
    fun calculateSnap(
        cursorWorld: Point2,
        cursorScreen: Offset,
        drawingState: ProjectDrawingState,
        camera: CameraTransform,
        density: Density,
        enableGridSnap: Boolean = true,
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

        // Priority 3: Axis alignment snap (horizontal/vertical from active vertex, world axes)
        if (settings.axisAlignmentSnapEnabled && drawingState.activeVertexId != null) {
            val axisSnap =
                findAxisAlignmentSnap(
                    cursorWorld,
                    drawingState,
                    settings.axisAlignmentToleranceDegrees,
                )
            if (axisSnap != null) {
                Logger.d {
                    "→ Snap: ${if (axisSnap.isHorizontal) "Horizontal" else "Vertical"} axis alignment"
                }
                return axisSnap
            }
        }

        // Priority 4: Vertex alignment (horizontal/vertical alignment with other vertices)
        val alignmentSnap =
            findVertexAlignmentSnap(
                cursorWorld,
                cursorScreen,
                drawingState,
                camera,
                density,
                settings.alignmentSnapRadius(density),
            )
        if (alignmentSnap != null) {
            Logger.d {
                "→ Snap: ${if (alignmentSnap.isHorizontal) "Horizontal" else "Vertical"} " +
                    "alignment with vertex ${alignmentSnap.alignedVertexId}"
            }
            return alignmentSnap
        }

        // Priority 5: Perpendicular snap (if active vertex exists) - disabled by default
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

        // Priority 6: Right angle snap to ACTIVE VERTEX (90° guidance) - disabled by default
        if (settings.rightAngleSnapEnabled && drawingState.activeVertexId != null) {
            val rightAngleSnap =
                findRightAngleSnap(
                    cursorWorld,
                    drawingState,
                    settings.rightAngleSnapTolerance,
                )
            if (rightAngleSnap != null) {
                Logger.d {
                    "→ Snap: Right angle ${rightAngleSnap.actualAngleDegrees.toInt()}° → " +
                        "${rightAngleSnap.snappedAngleDegrees.toInt()}°"
                }
                return rightAngleSnap
            }
        }

        // Priority 6: Grid snap (fallback)
        if (settings.gridEnabled && enableGridSnap) {
            val gridSnap = snapToGrid(cursorWorld, settings.gridSize)
            Logger.d { "→ Snap: Grid at (${gridSnap.position.x}, ${gridSnap.position.y})" }
            return gridSnap
        }

        return SnapResult.None
    }

    /**
     * Calculate snap with support for multiple simultaneous guidelines.
     * This allows showing both horizontal AND vertical guidelines when near a cross point,
     * with the snap position at their intersection.
     *
     * This follows standard CAD behavior (like AutoCAD, QCad) where orthogonal
     * constraints combine to create intersection snaps.
     *
     * @return SnapResultWithGuidelines containing snap position and all active guidelines
     */
    fun calculateSnapWithGuidelines(
        cursorWorld: Point2,
        cursorScreen: Offset,
        drawingState: ProjectDrawingState,
        camera: CameraTransform,
        density: Density,
        enableGridSnap: Boolean = true,
    ): SnapResultWithGuidelines {
        val settings = drawingState.snapSettings
        val guidelines = mutableListOf<SnapResult>()

        // Priority 1: Vertex snap (takes precedence over everything)
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
                return SnapResultWithGuidelines(vertexSnap.position, listOf(vertexSnap))
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
                return SnapResultWithGuidelines(edgeSnap.position, listOf(edgeSnap))
            }
        }

        // Priority 3 & 4: Check ALL alignment types and combine them
        // This allows showing cross guidelines (H from one source + V from another)
        var horizontalSnap: SnapResult? = null
        var verticalSnap: SnapResult? = null

        // Check axis alignment from active vertex (world axes)
        if (settings.axisAlignmentSnapEnabled && drawingState.activeVertexId != null) {
            val activeVertex = drawingState.getActiveVertex()
            if (activeVertex != null) {
                val dx = cursorWorld.x - activeVertex.position.x
                val dy = cursorWorld.y - activeVertex.position.y
                val distance = sqrt(dx * dx + dy * dy)

                if (distance >= 1.0) {
                    val angleRad = atan2(dy, dx)
                    val angleDeg = angleRad * 180.0 / PI
                    val normalizedAngle = ((angleDeg % 360) + 360) % 360
                    val tolerance = settings.axisAlignmentToleranceDegrees

                    // Check horizontal (0° or 180°)
                    val diffTo0 = angleDifference(normalizedAngle, 0.0)
                    val diffTo180 = angleDifference(normalizedAngle, 180.0)
                    val horizontalDiff = minOf(diffTo0, diffTo180)

                    if (horizontalDiff < tolerance) {
                        val snappedX =
                            if (diffTo0 < diffTo180) {
                                activeVertex.position.x + distance
                            } else {
                                activeVertex.position.x - distance
                            }
                        horizontalSnap =
                            SnapResult.AxisAlignment(
                                position = Point2(snappedX, activeVertex.position.y),
                                isHorizontal = true,
                            )
                    }

                    // Check vertical (90° or 270°)
                    val diffTo90 = angleDifference(normalizedAngle, 90.0)
                    val diffTo270 = angleDifference(normalizedAngle, 270.0)
                    val verticalDiff = minOf(diffTo90, diffTo270)

                    if (verticalDiff < tolerance) {
                        val snappedY =
                            if (diffTo90 < diffTo270) {
                                activeVertex.position.y + distance
                            } else {
                                activeVertex.position.y - distance
                            }
                        verticalSnap =
                            SnapResult.AxisAlignment(
                                position = Point2(activeVertex.position.x, snappedY),
                                isHorizontal = false,
                            )
                    }
                }
            }
        }

        // Check vertex alignment with OTHER vertices (can fill in missing H or V)
        val alignmentSnapRadius = settings.alignmentSnapRadius(density)
        val activeVertex = drawingState.getActiveVertex()

        drawingState.vertices.values.forEach { vertex ->
            // Skip the active vertex
            if (activeVertex != null && vertex.id == activeVertex.id) return@forEach

            val vertexScreen = worldToScreen(vertex.position, camera)

            // Check horizontal alignment (same Y) - only if we don't have a horizontal snap yet
            if (horizontalSnap == null) {
                val horizontalDistancePx = abs(cursorScreen.y - vertexScreen.y)
                if (horizontalDistancePx < alignmentSnapRadius) {
                    horizontalSnap =
                        SnapResult.VertexAlignment(
                            alignedVertexId = vertex.id,
                            position = Point2(cursorWorld.x, vertex.position.y),
                            isHorizontal = true,
                        )
                }
            }

            // Check vertical alignment (same X) - only if we don't have a vertical snap yet
            if (verticalSnap == null) {
                val verticalDistancePx = abs(cursorScreen.x - vertexScreen.x)
                if (verticalDistancePx < alignmentSnapRadius) {
                    verticalSnap =
                        SnapResult.VertexAlignment(
                            alignedVertexId = vertex.id,
                            position = Point2(vertex.position.x, cursorWorld.y),
                            isHorizontal = false,
                        )
                }
            }
        }

        // Build guidelines list and determine snap position
        if (horizontalSnap != null) guidelines.add(horizontalSnap!!)
        if (verticalSnap != null) guidelines.add(verticalSnap!!)

        if (guidelines.isNotEmpty()) {
            val snapPosition =
                when {
                    // Both horizontal and vertical: snap to cross intersection
                    horizontalSnap != null && verticalSnap != null -> {
                        Logger.d { "→ Snap: Cross intersection (H + V alignment)" }
                        // The intersection point: X from vertical snap, Y from horizontal snap
                        val hPos =
                            when (val h = horizontalSnap) {
                                is SnapResult.AxisAlignment -> h.position
                                is SnapResult.VertexAlignment -> h.position
                                else -> cursorWorld
                            }
                        val vPos =
                            when (val v = verticalSnap) {
                                is SnapResult.AxisAlignment -> v.position
                                is SnapResult.VertexAlignment -> v.position
                                else -> cursorWorld
                            }
                        Point2(vPos.x, hPos.y)
                    }
                    // Only horizontal
                    horizontalSnap != null -> {
                        Logger.d { "→ Snap: Horizontal alignment" }
                        when (val h = horizontalSnap) {
                            is SnapResult.AxisAlignment -> h.position
                            is SnapResult.VertexAlignment -> h.position
                            else -> cursorWorld
                        }
                    }
                    // Only vertical
                    verticalSnap != null -> {
                        Logger.d { "→ Snap: Vertical alignment" }
                        when (val v = verticalSnap) {
                            is SnapResult.AxisAlignment -> v.position
                            is SnapResult.VertexAlignment -> v.position
                            else -> cursorWorld
                        }
                    }
                    else -> cursorWorld
                }
            return SnapResultWithGuidelines(snapPosition, guidelines)
        }

        // Priority 5: Grid snap (fallback, no guidelines)
        if (settings.gridEnabled && enableGridSnap) {
            val gridSnap = snapToGrid(cursorWorld, settings.gridSize)
            return SnapResultWithGuidelines(gridSnap.position, emptyList())
        }

        return SnapResultWithGuidelines(cursorWorld, emptyList())
    }

    /**
     * Find nearest vertex within snap radius (screen space).
     */
    private fun findNearestVertex(
        cursorScreen: Offset,
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
        cursorScreen: Offset,
        drawingState: ProjectDrawingState,
        camera: CameraTransform,
        snapRadius: Float,
    ): SnapResult.Edge? {
        var nearestEdge: SnapResult.Edge? = null
        var minDistance = Float.MAX_VALUE

        drawingState.lines.forEach { line ->
            // ✅ Compute geometry from current vertex positions
            val geometry = line.getGeometry(drawingState.vertices)

            // Convert line endpoints to screen space
            val startScreen = worldToScreen(geometry.start, camera)
            val endScreen = worldToScreen(geometry.end, camera)

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
     * Find right angle snap during vertex placement.
     * Checks if cursor angle relative to active vertex is close to 0°, 90°, 180°, or 270°.
     *
     * @param cursorWorld cursor position in world coordinates
     * @param drawingState current drawing state
     * @param angleTolerance degrees tolerance for snapping (typically 5°)
     * @return SnapResult.RightAngle if within tolerance of cardinal angle, null otherwise
     */
    private fun findRightAngleSnap(
        cursorWorld: Point2,
        drawingState: ProjectDrawingState,
        angleTolerance: Double,
    ): SnapResult.RightAngle? {
        val activeVertex = drawingState.getActiveVertex() ?: return null

        // Calculate angle from active vertex to cursor
        val dx = cursorWorld.x - activeVertex.position.x
        val dy = cursorWorld.y - activeVertex.position.y
        val distance = sqrt(dx * dx + dy * dy)

        if (distance < 0.1) return null // Too close to active vertex

        val angleRad = atan2(dy, dx)
        val angleDeg = angleRad * 180.0 / PI

        // Cardinal angles (0°, 90°, 180°, 270°)
        val cardinalAngles = listOf(0.0, 90.0, 180.0, 270.0, -90.0, -180.0)

        // Find closest cardinal angle (use degree-based difference)
        val closestCardinal =
            cardinalAngles.minByOrNull { cardinalAngle ->
                val diff = abs(angleDeg - cardinalAngle)
                val wrappedDiff = minOf(diff, abs(diff - 360.0), abs(diff + 360.0))
                wrappedDiff
            } ?: return null

        // Calculate final difference
        val angleDiff =
            listOf(
                abs(angleDeg - closestCardinal),
                abs(angleDeg - closestCardinal - 360.0),
                abs(angleDeg - closestCardinal + 360.0),
            ).minOrNull() ?: return null

        if (angleDiff < angleTolerance) {
            // Snap cursor to exact cardinal angle
            // Note: Y increases downward in world coordinates, so we use -dy for angle calculation
            // but the snap position should maintain the same visual direction as the cursor
            val snappedAngleRad = closestCardinal * PI / 180.0
            val snappedPosition =
                Point2(
                    x = activeVertex.position.x + distance * cos(snappedAngleRad),
                    y = activeVertex.position.y + distance * sin(snappedAngleRad),
                )

            Logger.d {
                "→ Right angle snap: cursor at (${cursorWorld.x.toInt()}, ${cursorWorld.y.toInt()}), " +
                    "snapped to (${snappedPosition.x.toInt()}, ${snappedPosition.y.toInt()}), " +
                    "angle: ${angleDeg.toInt()}° → ${closestCardinal.toInt()}°"
            }

            return SnapResult.RightAngle(
                position = snappedPosition,
                actualAngleDegrees = angleDeg,
                snappedAngleDegrees = closestCardinal,
            )
        }

        return null
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
            // ✅ Compute geometry from current vertex positions
            val geometry = line.getGeometry(drawingState.vertices)

            // Calculate perpendicular from active vertex to this line
            val edgeVector =
                Point2(
                    geometry.end.x - geometry.start.x,
                    geometry.end.y - geometry.start.y,
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
     * Helper to find minimum angular difference, accounting for wraparound at 360°.
     */
    private fun angleDifference(
        angle1: Double,
        angle2: Double
    ): Double {
        val diff = abs(angle1 - angle2)
        return minOf(diff, 360.0 - diff)
    }

    /**
     * Find axis alignment snap (horizontal/vertical from active vertex, world axes).
     * Snaps to perfect horizontal or vertical lines from the active vertex.
     *
     * @param cursorWorld cursor position in world coordinates
     * @param drawingState current drawing state
     * @param toleranceDegrees angle tolerance in degrees from perfect horizontal/vertical
     * @return SnapResult.AxisAlignment if within tolerance, null otherwise
     */
    private fun findAxisAlignmentSnap(
        cursorWorld: Point2,
        drawingState: ProjectDrawingState,
        toleranceDegrees: Double,
    ): SnapResult.AxisAlignment? {
        val activeVertex = drawingState.getActiveVertex() ?: return null

        // Calculate vector from active vertex to cursor
        val dx = cursorWorld.x - activeVertex.position.x
        val dy = cursorWorld.y - activeVertex.position.y

        // Don't snap if cursor is too close to active vertex
        val distance = sqrt(dx * dx + dy * dy)
        if (distance < 1.0) return null

        val angleRad = atan2(dy, dx)
        val angleDeg = angleRad * 180.0 / PI

        // Normalize to 0-360
        val normalizedAngle = ((angleDeg % 360) + 360) % 360

        // Check for horizontal (0° = right, 180° = left)
        val diffTo0 = angleDifference(normalizedAngle, 0.0)
        val diffTo180 = angleDifference(normalizedAngle, 180.0)
        val horizontalDiff = minOf(diffTo0, diffTo180)

        if (horizontalDiff < toleranceDegrees) {
            // Snap to horizontal (same Y as active vertex)
            // Determine direction: right if closer to 0°, left if closer to 180°
            val snappedX =
                if (diffTo0 < diffTo180) {
                    activeVertex.position.x + distance // Right
                } else {
                    activeVertex.position.x - distance // Left
                }
            return SnapResult.AxisAlignment(
                position = Point2(snappedX, activeVertex.position.y),
                isHorizontal = true,
            )
        }

        // Check for vertical (90° = down, 270° = up)
        val diffTo90 = angleDifference(normalizedAngle, 90.0)
        val diffTo270 = angleDifference(normalizedAngle, 270.0)
        val verticalDiff = minOf(diffTo90, diffTo270)

        if (verticalDiff < toleranceDegrees) {
            // Snap to vertical (same X as active vertex)
            // Determine direction: down if closer to 90°, up if closer to 270°
            val snappedY =
                if (diffTo90 < diffTo270) {
                    activeVertex.position.y + distance // Down
                } else {
                    activeVertex.position.y - distance // Up
                }
            return SnapResult.AxisAlignment(
                position = Point2(activeVertex.position.x, snappedY),
                isHorizontal = false,
            )
        }

        return null
    }

    /**
     * Find horizontal or vertical alignment with other vertices.
     * Checks if cursor is horizontally or vertically aligned (within tolerance) with any other vertex.
     *
     * This helps create rectangular shapes by showing guidelines when you're aligned with existing vertices.
     *
     * @param cursorWorld cursor position in world coordinates
     * @param cursorScreen cursor position in screen coordinates
     * @param drawingState current drawing state
     * @param camera camera transform
     * @param density screen density
     * @param snapRadiusPx tolerance in pixels for alignment detection
     * @return SnapResult.VertexAlignment if aligned, null otherwise
     */
    private fun findVertexAlignmentSnap(
        cursorWorld: Point2,
        cursorScreen: Offset,
        drawingState: ProjectDrawingState,
        camera: CameraTransform,
        density: Density,
        snapRadiusPx: Float,
    ): SnapResult.VertexAlignment? {
        val activeVertex = drawingState.getActiveVertex()

        // Check alignment with all other vertices
        drawingState.vertices.values.forEach { vertex ->
            // Skip the active vertex (we're drawing from it)
            if (activeVertex != null && vertex.id == activeVertex.id) return@forEach

            val vertexScreen = worldToScreen(vertex.position, camera)

            // Check horizontal alignment (same Y coordinate within tolerance)
            val horizontalDistancePx = abs(cursorScreen.y - vertexScreen.y)
            if (horizontalDistancePx < snapRadiusPx) {
                // Snap cursor to same Y as this vertex (horizontal alignment)
                val snappedPosition =
                    Point2(
                        x = cursorWorld.x,
                        y = vertex.position.y,
                    )

                return SnapResult.VertexAlignment(
                    alignedVertexId = vertex.id,
                    position = snappedPosition,
                    isHorizontal = true,
                )
            }

            // Check vertical alignment (same X coordinate within tolerance)
            val verticalDistancePx = abs(cursorScreen.x - vertexScreen.x)
            if (verticalDistancePx < snapRadiusPx) {
                // Snap cursor to same X as this vertex (vertical alignment)
                val snappedPosition =
                    Point2(
                        x = vertex.position.x,
                        y = cursorWorld.y,
                    )

                return SnapResult.VertexAlignment(
                    alignedVertexId = vertex.id,
                    position = snappedPosition,
                    isHorizontal = false,
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
    ): Offset {
        val x = (point.x.toFloat() * camera.zoom) + camera.panX
        val y = (point.y.toFloat() * camera.zoom) + camera.panY
        return androidx.compose.ui.geometry
            .Offset(x, y)
    }

    private fun screenToWorld(
        offset: Offset,
        camera: CameraTransform,
    ): Point2 {
        val x = ((offset.x - camera.panX) / camera.zoom).toDouble()
        val y = ((offset.y - camera.panY) / camera.zoom).toDouble()
        return Point2(x, y)
    }

    private fun distanceScreen(
        a: Offset,
        b: Offset,
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
        point: Offset,
        start: Offset,
        end: Offset,
    ): Pair<Offset, Double> {
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
}
