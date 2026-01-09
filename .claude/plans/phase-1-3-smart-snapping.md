# Phase 1.3: Smart Snapping System - Implementation Plan

**Goal**: Implement intelligent snapping system for CAD-quality precision drawing with vertex snapping, edge snapping, and perpendicular constraints.

**Current State**:
- ✅ Phase 1.1: Grid snapping works (fixed grid intervals)
- ✅ Phase 1.2: Pan/zoom camera controls
- ❌ No vertex snapping (can't snap to existing vertices)
- ❌ No edge snapping (can't snap to points on edges)
- ❌ No perpendicular/parallel snapping
- ❌ No visual snap indicators

**Target State**:
- Cursor snaps to nearby vertices (within snap radius)
- Cursor snaps to nearest point on edges
- Cursor snaps perpendicular to edges (90° constraint)
- Visual feedback (snap indicators) show what you're snapping to
- Priority system: vertex > edge > perpendicular > grid
- Configurable snap settings (radius, enabled types)

---

## Overview

Smart snapping is **critical for CAD workflows** - it enables:
1. **Closed shapes** - Snap to first vertex to close polygon
2. **Connected walls** - Snap to existing vertices for continuous drawing
3. **Perpendicular walls** - Snap at 90° for rectangular rooms
4. **Edge extensions** - Extend walls by snapping along edge direction

**Architecture Pattern**: Event-driven snap detection
- `DrawingCanvas` detects cursor position
- `SmartSnapSystem` calculates best snap target
- Snap result passed through `GeometryEvent.PointPlaced`
- Visual indicators rendered in canvas layer

---

## Phase 1.3.1: Snap Settings Model

### 1.1 Extend SnapSettings Data Model

**File**: `shared/src/commonMain/kotlin/com/roomplanner/data/models/SnapSettings.kt`

**Current state**: Basic model with grid size only

**Change**: Add smart snap configuration

```kotlin
package com.roomplanner.data.models

import kotlinx.serialization.Serializable

@Serializable
data class SnapSettings(
    // Grid snapping (Phase 1.1)
    val gridSize: Double = 12.0, // inches
    val gridEnabled: Boolean = true,

    // Smart snapping (Phase 1.3)
    val vertexSnapEnabled: Boolean = true,
    val edgeSnapEnabled: Boolean = true,
    val perpendicularSnapEnabled: Boolean = true,

    // Snap radius in screen pixels (not world units - stays constant when zoomed)
    val vertexSnapRadius: Float = 20f,  // 20px radius around vertices
    val edgeSnapRadius: Float = 15f,    // 15px distance to edge
    val perpendicularAngleTolerance: Double = 2.0, // degrees
) {
    companion object {
        fun defaultImperial() = SnapSettings(gridSize = 12.0) // 1 foot
        fun defaultMetric() = SnapSettings(gridSize = 10.0)   // 10 cm
    }
}

/**
 * Result of snap detection.
 * Describes what the cursor snapped to (if anything).
 */
@Serializable
sealed interface SnapResult {
    /** No snap - use cursor position as-is */
    @Serializable
    data object None : SnapResult

    /** Snapped to grid intersection */
    @Serializable
    data class Grid(val position: com.roomplanner.domain.geometry.Point2) : SnapResult

    /** Snapped to existing vertex */
    @Serializable
    data class Vertex(
        val vertexId: String,
        val position: com.roomplanner.domain.geometry.Point2
    ) : SnapResult

    /** Snapped to point on edge */
    @Serializable
    data class Edge(
        val lineId: String,
        val position: com.roomplanner.domain.geometry.Point2,
        val t: Double  // Parameter along edge (0.0 = start, 1.0 = end)
    ) : SnapResult

    /** Snapped perpendicular to edge */
    @Serializable
    data class Perpendicular(
        val lineId: String,
        val position: com.roomplanner.domain.geometry.Point2,
        val angle: Double  // Angle of perpendicular in degrees
    ) : SnapResult
}
```

**Rationale**:
- Screen-space snap radius (20px) stays constant regardless of zoom
- Priority implicit in sealed class ordering
- `t` parameter enables edge splitting later
- Separate enable flags for each snap type (user preferences)

---

## Phase 1.3.2: Smart Snap System

### 2.1 Create SmartSnapSystem Domain Logic

**File**: `shared/src/commonMain/kotlin/com/roomplanner/domain/snapping/SmartSnapSystem.kt` (NEW)

```kotlin
package com.roomplanner.domain.snapping

import co.touchlab.kermit.Logger
import com.roomplanner.data.models.AppState
import com.roomplanner.data.models.CameraTransform
import com.roomplanner.data.models.SnapResult
import com.roomplanner.data.models.SnapSettings
import com.roomplanner.domain.geometry.Point2
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.atan2

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
     * @param state current app state (vertices, lines, settings)
     * @param camera camera transform (for world↔screen conversion)
     * @return SnapResult describing what to snap to
     */
    fun calculateSnap(
        cursorWorld: Point2,
        cursorScreen: androidx.compose.ui.geometry.Offset,
        state: AppState,
        camera: CameraTransform,
    ): SnapResult {
        val settings = state.snapSettings

        // Priority 1: Vertex snap
        if (settings.vertexSnapEnabled) {
            val vertexSnap = findNearestVertex(
                cursorScreen,
                state,
                camera,
                settings.vertexSnapRadius
            )
            if (vertexSnap != null) {
                Logger.d { "→ Snap: Vertex ${vertexSnap.vertexId}" }
                return vertexSnap
            }
        }

        // Priority 2: Edge snap
        if (settings.edgeSnapEnabled) {
            val edgeSnap = findNearestEdgePoint(
                cursorScreen,
                state,
                camera,
                settings.edgeSnapRadius
            )
            if (edgeSnap != null) {
                Logger.d { "→ Snap: Edge ${edgeSnap.lineId} at t=${edgeSnap.t}" }
                return edgeSnap
            }
        }

        // Priority 3: Perpendicular snap (if active vertex exists)
        if (settings.perpendicularSnapEnabled && state.activeVertexId != null) {
            val perpSnap = findPerpendicularSnap(
                cursorWorld,
                state,
                settings.perpendicularAngleTolerance
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
        state: AppState,
        camera: CameraTransform,
        snapRadius: Float,
    ): SnapResult.Vertex? {
        var nearestVertex: SnapResult.Vertex? = null
        var minDistance = Float.MAX_VALUE

        state.vertices.values.forEach { vertex ->
            val vertexScreen = worldToScreen(vertex.position, camera)
            val distance = distanceScreen(cursorScreen, vertexScreen)

            if (distance < snapRadius && distance < minDistance) {
                minDistance = distance
                nearestVertex = SnapResult.Vertex(
                    vertexId = vertex.id,
                    position = vertex.position
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
        state: AppState,
        camera: CameraTransform,
        snapRadius: Float,
    ): SnapResult.Edge? {
        var nearestEdge: SnapResult.Edge? = null
        var minDistance = Float.MAX_VALUE

        state.lines.forEach { line ->
            // Convert line endpoints to screen space
            val startScreen = worldToScreen(line.geometry.start, camera)
            val endScreen = worldToScreen(line.geometry.end, camera)

            // Find nearest point on line segment (in screen space)
            val (nearestPoint, t) = nearestPointOnSegment(
                cursorScreen,
                startScreen,
                endScreen
            )

            val distance = distanceScreen(cursorScreen, nearestPoint)

            if (distance < snapRadius && distance < minDistance) {
                // Convert back to world space
                val worldPoint = screenToWorld(nearestPoint, camera)

                minDistance = distance
                nearestEdge = SnapResult.Edge(
                    lineId = line.id,
                    position = worldPoint,
                    t = t
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
        state: AppState,
        angleTolerance: Double,
    ): SnapResult.Perpendicular? {
        val activeVertex = state.getActiveVertex() ?: return null

        state.lines.forEach { line ->
            // Calculate perpendicular from active vertex to this line
            val edgeVector = Point2(
                line.geometry.end.x - line.geometry.start.x,
                line.geometry.end.y - line.geometry.start.y
            )
            val edgeAngle = atan2(edgeVector.y, edgeVector.x)
            val perpAngle = edgeAngle + (Math.PI / 2)  // 90° rotation

            // Vector from active vertex to cursor
            val toCursor = Point2(
                cursorWorld.x - activeVertex.position.x,
                cursorWorld.y - activeVertex.position.y
            )
            val cursorAngle = atan2(toCursor.y, toCursor.x)

            // Check if angles are close (within tolerance)
            val angleDiff = abs(angleDifference(cursorAngle, perpAngle))
            val angleDegreeDiff = Math.toDegrees(angleDiff)

            if (angleDegreeDiff < angleTolerance) {
                // Project cursor onto perpendicular ray
                val distance = sqrt(toCursor.x * toCursor.x + toCursor.y * toCursor.y)
                val snapPosition = Point2(
                    activeVertex.position.x + cos(perpAngle) * distance,
                    activeVertex.position.y + sin(perpAngle) * distance
                )

                return SnapResult.Perpendicular(
                    lineId = line.id,
                    position = snapPosition,
                    angle = Math.toDegrees(perpAngle)
                )
            }
        }

        return null
    }

    /**
     * Grid snap (existing logic from Phase 1.1).
     */
    private fun snapToGrid(point: Point2, gridSize: Double): SnapResult.Grid {
        val x = (point.x / gridSize).toInt() * gridSize
        val y = (point.y / gridSize).toInt() * gridSize
        return SnapResult.Grid(Point2(x.toDouble(), y.toDouble()))
    }

    // ==================== Geometry Helpers ====================

    private fun worldToScreen(point: Point2, camera: CameraTransform): androidx.compose.ui.geometry.Offset {
        val x = (point.x.toFloat() * camera.zoom) + camera.panX
        val y = (point.y.toFloat() * camera.zoom) + camera.panY
        return androidx.compose.ui.geometry.Offset(x, y)
    }

    private fun screenToWorld(offset: androidx.compose.ui.geometry.Offset, camera: CameraTransform): Point2 {
        val x = ((offset.x - camera.panX) / camera.zoom).toDouble()
        val y = ((offset.y - camera.panY) / camera.zoom).toDouble()
        return Point2(x, y)
    }

    private fun distanceScreen(a: androidx.compose.ui.geometry.Offset, b: androidx.compose.ui.geometry.Offset): Float {
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

        val nearestPoint = androidx.compose.ui.geometry.Offset(
            start.x + tClamped * dx,
            start.y + tClamped * dy
        )

        return Pair(nearestPoint, tClamped.toDouble())
    }

    /**
     * Calculate shortest angular difference between two angles.
     * Handles wrap-around (e.g., 359° and 1° are 2° apart, not 358°).
     */
    private fun angleDifference(a: Double, b: Double): Double {
        var diff = a - b
        while (diff > Math.PI) diff -= 2 * Math.PI
        while (diff < -Math.PI) diff += 2 * Math.PI
        return diff
    }
}
```

**Key Algorithms**:
- **Vertex snap**: Linear search (O(n) vertices), screen-space distance
- **Edge snap**: Linear search (O(n) edges), point-to-segment projection
- **Perpendicular snap**: Angle comparison with tolerance, ray projection
- **Grid snap**: Existing Phase 1.1 logic

---

## Phase 1.3.3: Integrate Snap System

### 3.1 Update DrawingCanvas to Use SmartSnapSystem

**File**: `shared/src/commonMain/kotlin/com/roomplanner/ui/components/DrawingCanvas.kt`

**Change**: Replace simple grid snap with smart snap

```kotlin
/**
 * Handle tap gesture: convert to world coordinates, apply smart snap, emit event.
 * Phase 1.3: Smart snapping with priority system
 */
private suspend fun handleTap(
    screenOffset: Offset,
    state: AppState,
    eventBus: EventBus,
) {
    Logger.d { "→ Tap detected at screen (${screenOffset.x}, ${screenOffset.y})" }

    // Convert screen coordinates to world coordinates
    val worldPoint = screenToWorld(screenOffset, state.cameraTransform)

    // Apply smart snap (vertex > edge > perpendicular > grid)
    val snapResult = SmartSnapSystem.calculateSnap(
        cursorWorld = worldPoint,
        cursorScreen = screenOffset,
        state = state,
        camera = state.cameraTransform
    )

    val (snappedPoint, snappedTo) = when (snapResult) {
        is SnapResult.None -> Pair(worldPoint, null)
        is SnapResult.Grid -> Pair(snapResult.position, null)
        is SnapResult.Vertex -> Pair(snapResult.position, snapResult.vertexId)
        is SnapResult.Edge -> Pair(snapResult.position, null)  // Edge snap creates new vertex
        is SnapResult.Perpendicular -> Pair(snapResult.position, null)
    }

    Logger.d { "  World: (${worldPoint.x}, ${worldPoint.y}) → Snapped: (${snappedPoint.x}, ${snappedPoint.y})" }
    if (snapResult is SnapResult.Vertex) {
        Logger.i { "✓ Snapped to vertex: ${snapResult.vertexId}" }
    }

    // Emit event (GeometryManager will handle state update)
    eventBus.emit(
        GeometryEvent.PointPlaced(
            position = snappedPoint,
            snappedTo = snappedTo,  // Pass vertex ID if snapped to existing vertex
        ),
    )
}
```

**Key Changes**:
- `SmartSnapSystem.calculateSnap()` replaces `snapToGrid()`
- `snappedTo` parameter passed to event (enables closed shapes)
- GeometryManager already handles vertex reuse (Phase 1.1 code)

---

## Phase 1.3.4: Visual Snap Indicators

### 4.1 Add Snap Indicator Rendering

**File**: `DrawingCanvas.kt`

**Change**: Add snap indicator layer to canvas

```kotlin
@Composable
fun DrawingCanvas(
    state: AppState,
    eventBus: EventBus,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    var cursorPosition by remember { mutableStateOf<Offset?>(null) }
    var snapPreview by remember { mutableStateOf<SnapResult>(SnapResult.None) }

    Canvas(
        modifier =
            modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { screenOffset ->
                            coroutineScope.launch {
                                handleTap(screenOffset, state, eventBus)
                            }
                        },
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        coroutineScope.launch {
                            handleTransform(pan, zoom, centroid, eventBus)
                        }
                    }
                }
                // NEW: Track cursor position for snap preview
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val position = event.changes.first().position
                            cursorPosition = position

                            // Calculate snap preview
                            val worldPoint = screenToWorld(position, state.cameraTransform)
                            snapPreview = SmartSnapSystem.calculateSnap(
                                cursorWorld = worldPoint,
                                cursorScreen = position,
                                state = state,
                                camera = state.cameraTransform
                            )
                        }
                    }
                },
    ) {
        // 1. Draw grid background
        drawGrid(state.cameraTransform, state.snapSettings.gridSize, state.drawingConfig)

        // 2. Draw lines (walls)
        drawLines(state, state.cameraTransform, state.drawingConfig)

        // 3. Draw vertices (snap points)
        drawVertices(state, state.cameraTransform, state.drawingConfig)

        // 4. NEW: Draw snap indicators
        if (cursorPosition != null) {
            drawSnapIndicator(snapPreview, state.cameraTransform, state.drawingConfig)
        }
    }
}
```

### 4.2 Implement Snap Indicator Drawing

Add new drawing function:

```kotlin
/**
 * Draw visual snap indicator.
 * Shows what the cursor will snap to (vertex, edge, perpendicular).
 */
private fun DrawScope.drawSnapIndicator(
    snapResult: SnapResult,
    camera: CameraTransform,
    config: com.roomplanner.data.models.DrawingConfig,
) {
    when (snapResult) {
        is SnapResult.None, is SnapResult.Grid -> {
            // No indicator for grid snap (grid already visible)
        }

        is SnapResult.Vertex -> {
            // Highlight the vertex being snapped to
            val screenPos = worldToScreen(snapResult.position, camera)
            drawCircle(
                color = config.snapIndicatorColorCompose(),
                radius = config.snapIndicatorRadius,
                center = screenPos,
                alpha = config.snapIndicatorAlpha
            )
        }

        is SnapResult.Edge -> {
            // Show point on edge
            val screenPos = worldToScreen(snapResult.position, camera)
            drawCircle(
                color = config.snapIndicatorColorCompose(),
                radius = config.snapIndicatorRadius * 0.7f,  // Slightly smaller
                center = screenPos,
                alpha = config.snapIndicatorAlpha
            )
        }

        is SnapResult.Perpendicular -> {
            // Show perpendicular constraint line
            val screenPos = worldToScreen(snapResult.position, camera)

            // Draw guide line from active vertex to snap point
            // (Requires getting active vertex position - omitted for brevity)

            // Draw snap indicator at perpendicular point
            drawCircle(
                color = config.snapIndicatorColorCompose(),
                radius = config.snapIndicatorRadius,
                center = screenPos,
                alpha = config.snapIndicatorAlpha
            )
        }
    }
}

/**
 * Helper: worldToScreen for drawing functions
 */
private fun worldToScreen(point: Point2, camera: CameraTransform): Offset {
    val x = (point.x.toFloat() * camera.zoom) + camera.panX
    val y = (point.y.toFloat() * camera.zoom) + camera.panY
    return Offset(x, y)
}
```

**Visual Design**:
- **Vertex snap**: Orange circle (10px radius) around target vertex
- **Edge snap**: Smaller orange circle (7px) on edge
- **Perpendicular snap**: Orange circle + dotted guide line
- **Grid snap**: No indicator (grid already visible)

---

## Phase 1.3.5: Testing & Validation

### 5.1 Manual Testing

**Test Case 1: Vertex Snapping**
- [ ] Place vertex A at (100, 100)
- [ ] Move cursor near A (within 20px screen distance)
- [ ] Orange snap indicator appears
- [ ] Tap → confirms snap to existing vertex (reuses vertex)
- [ ] Console: "✓ Snapped to vertex: {id}"
- [ ] Draw line to create vertex B
- [ ] Move cursor near A again → snaps to A (closes shape)

**Test Case 2: Edge Snapping**
- [ ] Create line A→B (horizontal)
- [ ] Move cursor near midpoint of line
- [ ] Snap indicator appears on edge
- [ ] Tap → creates new vertex on edge (not A or B)
- [ ] Console: "→ Snap: Edge {id} at t=0.5"

**Test Case 3: Perpendicular Snapping**
- [ ] Create vertex A, then line A→B (horizontal)
- [ ] From B, move cursor vertically (90° from A→B)
- [ ] Snap indicator shows perpendicular constraint
- [ ] Tap → creates vertex C perpendicular to A→B
- [ ] Angle B→C is exactly 90° from A→B

**Test Case 4: Priority System**
- [ ] Place vertices close together (25px apart)
- [ ] Move cursor equidistant from two vertices
- [ ] Snaps to nearest vertex (not both)
- [ ] Move cursor near edge far from vertices
- [ ] Snaps to edge (vertex priority passed)

**Test Case 5: Zoom Independence**
- [ ] Set zoom to 2x
- [ ] Vertex snap radius still 20px (constant)
- [ ] Snap behavior feels identical to 1x zoom

### 5.2 Console Output Validation

**Expected logs**:
```
→ Tap detected at screen (400.0, 300.0)
→ Snap: Vertex abc-123
  World: (395.0, 298.0) → Snapped: (400.0, 300.0)
✓ Snapped to vertex: abc-123
  Reusing existing vertex: abc-123
✓ Line created: def-456 → abc-123
```

### 5.3 Edge Cases

- [ ] Snap to first vertex → closes polygon (line created)
- [ ] Multiple vertices within radius → snaps to nearest
- [ ] Edge snap at t=0.0 or t=1.0 → reuses vertex (not new point)
- [ ] Perpendicular snap with no active vertex → falls back to grid
- [ ] Disable all snap types → cursor moves freely
- [ ] Zoom to 0.1x → snap radius still 20px (screen space)

---

## Success Criteria

✅ Phase 1.3 complete when:

- [ ] Vertex snapping works within 20px radius
- [ ] Edge snapping projects onto nearest edge
- [ ] Perpendicular snapping respects angle tolerance
- [ ] Priority system: vertex > edge > perp > grid
- [ ] Visual snap indicators render correctly
- [ ] Closed shapes possible (snap to first vertex)
- [ ] Snap radius constant in screen space (zoom independent)
- [ ] Console logs show snap type for each tap
- [ ] All manual tests pass
- [ ] No crashes or precision issues

---

## File Changes Summary

**New Files**:
1. `SmartSnapSystem.kt` - Snap detection logic

**Modified Files**:
1. `SnapSettings.kt` - Add smart snap configuration
2. `DrawingCanvas.kt` - Integrate snap system, add indicators
3. `AppEvent.kt` - (no changes, `snappedTo` parameter already exists)

---

## Architecture Notes

**Snap Detection Performance**:
- O(n) for vertex snap (linear search - acceptable for <1000 vertices)
- O(n) for edge snap (linear search - acceptable for <1000 edges)
- O(n) for perpendicular snap
- **Future optimization**: R-tree spatial index for O(log n) queries

**Screen-Space Snapping**:
- Snap radius in pixels (not world units)
- Feels consistent at all zoom levels
- User expects "20px" to mean "20 pixels on their screen"

**Priority System**:
- Vertex > Edge > Perpendicular > Grid
- User can close shapes (vertex snap)
- User can extend walls (edge snap)
- User can create perpendicular walls (perp snap)
- Grid is always available fallback

---

## Next Phase

**Phase 1.4**: Selection and Editing
- Select vertices/lines by tapping
- Drag selected vertices to move them
- Delete selected entities
- Multi-select with drag rectangle
- Selection indicators (highlight)

---

**Estimated Time**: 3-4 hours implementation + testing