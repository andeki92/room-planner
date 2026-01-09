# Phase 1.2: Pan/Zoom Camera Controls - Implementation Plan

**Goal**: Implement two-finger pan and pinch-to-zoom gestures for the drawing canvas, with proper coordinate transformation between world and screen space.

**Current State**:
- ✅ Phase 1.1 complete: Basic tap gestures, grid snapping, vertex/line rendering
- ✅ `CameraTransform` model exists but unused
- ✅ `screenToWorld()` is placeholder (no transform applied)
- ✅ Drawing functions don't apply camera transform yet

**Target State**:
- Two-finger pan gesture moves the viewport
- Pinch gesture zooms in/out (0.1x to 10x range)
- All geometry rendered with camera transform applied
- Tap-to-place works correctly in transformed space
- Grid moves/scales with camera

---

## Overview

This phase implements **viewport navigation** for the CAD canvas, allowing users to:
1. **Pan** - Move around the drawing with two-finger drag
2. **Zoom** - Zoom in/out with pinch gesture (centered on pinch point)
3. **Maintain coordinate precision** - Double precision in world space, Float for rendering

**Architecture Pattern**: Event-driven camera updates
- `DrawingCanvas` detects gestures → emits `GeometryEvent.CameraTransformed`
- `GeometryManager` listens → updates `AppState.cameraTransform` immutably
- Compose recomposes → canvas re-renders with new transform

---

## Phase 1.2.1: Camera Events

### 1.1 Add CameraTransformed Event

**File**: `shared/src/commonMain/kotlin/com/roomplanner/data/events/AppEvent.kt`

**Change**: Add new event type to `GeometryEvent` sealed interface

```kotlin
sealed interface GeometryEvent : AppEvent {
    // ... existing PointPlaced event ...

    /**
     * User transformed the camera view (pan/zoom).
     * Phase 1.2: Enables viewport navigation
     * @param panDelta change in pan offset (screen pixels)
     * @param zoomDelta multiplicative zoom change (1.0 = no change)
     * @param zoomCenter screen coordinates of zoom center (for zoom-to-point)
     */
    data class CameraTransformed(
        val panDelta: androidx.compose.ui.geometry.Offset = androidx.compose.ui.geometry.Offset.Zero,
        val zoomDelta: Float = 1f,
        val zoomCenter: androidx.compose.ui.geometry.Offset? = null,
    ) : GeometryEvent
}
```

**Rationale**:
- `panDelta`: Relative change (not absolute position) for smooth continuous pan
- `zoomDelta`: Multiplicative (1.5x zoom in, 0.5x zoom out) for intuitive pinch
- `zoomCenter`: Point to zoom towards (null = zoom to canvas center)

### 1.2 Update GeometryManager to Handle Camera Events

**File**: `shared/src/commonMain/kotlin/com/roomplanner/domain/geometry/GeometryManager.kt`

**Change**: Add handler for `CameraTransformed` events

```kotlin
private suspend fun handleEvent(event: GeometryEvent) {
    when (event) {
        is GeometryEvent.PointPlaced -> handlePointPlaced(event)
        is GeometryEvent.CameraTransformed -> handleCameraTransformed(event)  // NEW
    }
}

private suspend fun handleCameraTransformed(event: GeometryEvent.CameraTransformed) {
    Logger.d { "→ Camera transform: pan=(${event.panDelta.x}, ${event.panDelta.y}), zoom=${event.zoomDelta}" }

    stateManager.updateState { state ->
        val currentCamera = state.cameraTransform

        // Apply pan delta
        val newPanX = currentCamera.panX + event.panDelta.x
        val newPanY = currentCamera.panY + event.panDelta.y

        // Apply zoom delta with clamping
        val newZoom = (currentCamera.zoom * event.zoomDelta).coerceIn(
            CameraTransform.MIN_ZOOM,
            CameraTransform.MAX_ZOOM
        )

        // TODO Phase 1.2.3: Implement zoom-to-point using event.zoomCenter
        // For now, zoom to canvas center

        val newCamera = CameraTransform(
            panX = newPanX,
            panY = newPanY,
            zoom = newZoom
        )

        Logger.d { "  Camera updated: pan=($newPanX, $newPanY), zoom=$newZoom" }

        state.copy(cameraTransform = newCamera)
    }
}
```

**Testing**:
- [ ] Add log statement shows camera updates
- [ ] Multiple transforms accumulate correctly
- [ ] Zoom clamps to MIN_ZOOM/MAX_ZOOM range

---

## Phase 1.2.2: Gesture Detection

### 2.1 Add Transform Gestures to DrawingCanvas

**File**: `shared/src/commonMain/kotlin/com/roomplanner/ui/components/DrawingCanvas.kt`

**Change**: Add `detectTransformGestures` alongside existing tap gestures

```kotlin
@Composable
fun DrawingCanvas(
    state: AppState,
    eventBus: EventBus,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()

    Canvas(
        modifier =
            modifier
                .fillMaxSize()
                // Existing tap gesture (unchanged)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { screenOffset ->
                            coroutineScope.launch {
                                handleTap(screenOffset, state, eventBus)
                            }
                        },
                    )
                }
                // NEW: Transform gestures (pan/zoom)
                .pointerInput(Unit) {
                    detectTransformGestures(
                        panZoomLock = true  // Disable rotation
                    ) { centroid, pan, zoom, _ ->
                        coroutineScope.launch {
                            handleTransform(pan, zoom, centroid, eventBus)
                        }
                    }
                },
    ) {
        // ... existing drawing code ...
    }
}
```

### 2.2 Implement Transform Handler

Add after `handleTap` function in same file:

```kotlin
/**
 * Handle pan/zoom transform gesture: emit camera event.
 * Phase 1.2: Two-finger pan and pinch-to-zoom
 */
private suspend fun handleTransform(
    panDelta: Offset,
    zoomDelta: Float,
    centroid: Offset,
    eventBus: EventBus,
) {
    // Only emit if there's actual change
    if (panDelta != Offset.Zero || zoomDelta != 1f) {
        eventBus.emit(
            GeometryEvent.CameraTransformed(
                panDelta = panDelta,
                zoomDelta = zoomDelta,
                zoomCenter = centroid
            )
        )
    }
}
```

**Key Points**:
- `detectTransformGestures` provides continuous updates during drag/pinch
- `panZoomLock = true` disables rotation (not needed for CAD)
- `centroid` is screen coordinates of gesture center (for zoom-to-point)
- Separate `pointerInput` blocks allow tap and transform to coexist

**Testing**:
- [ ] Two-finger drag triggers pan
- [ ] Pinch triggers zoom
- [ ] Single tap still places vertices (not blocked)
- [ ] Console shows camera transform events

---

## Phase 1.2.3: Coordinate Transformation

### 3.1 Implement worldToScreen Function

**File**: `shared/src/commonMain/kotlin/com/roomplanner/ui/components/DrawingCanvas.kt`

Add after `screenToWorld` function:

```kotlin
/**
 * Convert world coordinates to screen coordinates.
 * Apply camera transform: translate(pan) -> scale(zoom)
 *
 * Formula: screen = (world * zoom) + pan
 */
private fun worldToScreen(
    worldPoint: Point2,
    camera: CameraTransform,
): Offset {
    val x = (worldPoint.x.toFloat() * camera.zoom) + camera.panX
    val y = (worldPoint.y.toFloat() * camera.zoom) + camera.panY
    return Offset(x, y)
}

/**
 * Overload for Offset input (convenience)
 */
private fun worldToScreen(
    worldOffset: Offset,
    camera: CameraTransform,
): Offset {
    val x = (worldOffset.x * camera.zoom) + camera.panX
    val y = (worldOffset.y * camera.zoom) + camera.panY
    return Offset(x, y)
}
```

### 3.2 Update screenToWorld Function

**File**: Same file, replace existing placeholder:

```kotlin
/**
 * Convert screen coordinates to world coordinates.
 * Inverse of worldToScreen: world = (screen - pan) / zoom
 *
 * Phase 1.1: No pan/zoom (identity transform)
 * Phase 1.2: Apply inverse camera transform
 */
private fun screenToWorld(
    screenOffset: Offset,
    camera: CameraTransform,
): Point2 {
    // Inverse transform: subtract pan, then divide by zoom
    val worldX = ((screenOffset.x - camera.panX) / camera.zoom).toDouble()
    val worldY = ((screenOffset.y - camera.panY) / camera.zoom).toDouble()
    return Point2(worldX, worldY)
}
```

**Mathematical Rationale**:

```
Forward (world → screen):
  screen_x = world_x * zoom + pan_x
  screen_y = world_y * zoom + pan_y

Inverse (screen → world):
  world_x = (screen_x - pan_x) / zoom
  world_y = (screen_y - pan_y) / zoom
```

**Testing**:
- [ ] Identity transform (zoom=1, pan=0): screen == world
- [ ] After zoom 2x: world coordinates half of screen
- [ ] After pan (100, 100): world coordinates offset by -100
- [ ] Round-trip: `screenToWorld(worldToScreen(p)) == p`

---

## Phase 1.2.4: Apply Transform to Rendering

### 4.1 Update drawGrid Function

**File**: `shared/src/commonMain/kotlin/com/roomplanner/ui/components/DrawingCanvas.kt`

**Change**: Transform grid to screen space

```kotlin
/**
 * Draw grid background for visual reference.
 * Phase 1.2: Apply camera transform (grid moves/scales with viewport)
 */
private fun DrawScope.drawGrid(
    camera: CameraTransform,
    gridSize: Double,
    config: com.roomplanner.data.models.DrawingConfig,
) {
    val gridColor = config.gridColorCompose()
    val gridSizeScreen = (gridSize.toFloat() * camera.zoom)  // Grid size in screen pixels

    // Calculate grid offset (so grid aligns with world origin)
    val gridOffsetX = camera.panX % gridSizeScreen
    val gridOffsetY = camera.panY % gridSizeScreen

    // Draw vertical grid lines
    var x = gridOffsetX
    while (x < size.width) {
        drawLine(
            color = gridColor,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = config.gridLineWidth,
        )
        x += gridSizeScreen
    }

    // Draw horizontal grid lines
    var y = gridOffsetY
    while (y < size.height) {
        drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = config.gridLineWidth,
        )
        y += gridSizeScreen
    }
}
```

**Key Changes**:
- Grid spacing scales with zoom: `gridSize * zoom`
- Grid offset aligns with world origin: `pan % gridSize`
- Grid cells stay fixed to world coordinates (don't drift when panning)

### 4.2 Update drawLines Function

**File**: Same file

**Change**: Transform line endpoints to screen space

```kotlin
/**
 * Draw all lines (walls).
 * Phase 1.2: Transform world coordinates to screen space
 */
private fun DrawScope.drawLines(
    state: AppState,
    camera: CameraTransform,
    config: com.roomplanner.data.models.DrawingConfig,
) {
    state.lines.forEach { line ->
        // Convert world coordinates to screen coordinates
        val start = worldToScreen(line.geometry.start, camera)
        val end = worldToScreen(line.geometry.end, camera)

        drawLine(
            color = config.lineColorCompose(),
            start = start,
            end = end,
            strokeWidth = config.lineStrokeWidth,
        )
    }
}
```

**Note**: Added `worldToScreen` helper that takes `Line2` directly:

```kotlin
/**
 * Extension: Convert Line2 geometry to screen space
 */
private fun Line2.start(camera: CameraTransform): Offset =
    worldToScreen(this.start, camera)

private fun Line2.end(camera: CameraTransform): Offset =
    worldToScreen(this.end, camera)
```

Actually, simpler to just call `line.geometry.start.toOffset()` and transform that. Keep it explicit.

### 4.3 Update drawVertices Function

**File**: Same file

**Change**: Transform vertex positions to screen space

```kotlin
/**
 * Draw all vertices (snap points).
 * Phase 1.2: Transform world coordinates to screen space
 * Active vertex (last placed) is highlighted in blue.
 */
private fun DrawScope.drawVertices(
    state: AppState,
    camera: CameraTransform,
    config: com.roomplanner.data.models.DrawingConfig,
) {
    state.vertices.values.forEach { vertex ->
        val isActive = vertex.id == state.activeVertexId
        val color =
            when {
                vertex.fixed -> config.vertexColorFixedCompose()
                isActive -> config.vertexColorActiveCompose()
                else -> config.vertexColorNormalCompose()
            }

        // Convert world coordinates to screen coordinates
        val screenPosition = worldToScreen(vertex.position, camera)

        drawCircle(
            color = color,
            radius = config.vertexRadius,
            center = screenPosition,
        )

        // Draw outline for better visibility
        drawCircle(
            color = config.vertexOutlineColorCompose(),
            radius = config.vertexRadius,
            center = screenPosition,
            style = Stroke(width = config.vertexStrokeWidth),
        )
    }
}
```

**Testing**:
- [ ] Vertices move when panning
- [ ] Vertices scale when zooming (position, not size)
- [ ] Lines connect correctly in screen space
- [ ] Grid aligns with world origin

---

## Phase 1.2.5: Extension - Zoom-to-Point

### 5.1 Implement Zoom Around Gesture Center

**File**: `GeometryManager.kt`

**Change**: Update `handleCameraTransformed` to zoom around `zoomCenter`

```kotlin
private suspend fun handleCameraTransformed(event: GeometryEvent.CameraTransformed) {
    stateManager.updateState { state ->
        val currentCamera = state.cameraTransform

        // 1. Apply zoom delta
        val newZoom = (currentCamera.zoom * event.zoomDelta).coerceIn(
            CameraTransform.MIN_ZOOM,
            CameraTransform.MAX_ZOOM
        )

        // 2. Adjust pan to zoom around zoomCenter (if provided)
        val (newPanX, newPanY) = if (event.zoomCenter != null && event.zoomDelta != 1f) {
            // Convert zoom center to world coordinates BEFORE zoom
            val centerWorld = screenToWorldStatic(event.zoomCenter, currentCamera)

            // After zoom, convert back to screen - this is where it WOULD be
            val centerScreenAfter = Offset(
                (centerWorld.x.toFloat() * newZoom) + currentCamera.panX,
                (centerWorld.y.toFloat() * newZoom) + currentCamera.panY
            )

            // Calculate pan adjustment to keep center fixed
            val panAdjustX = event.zoomCenter.x - centerScreenAfter.x
            val panAdjustY = event.zoomCenter.y - centerScreenAfter.y

            Pair(
                currentCamera.panX + event.panDelta.x + panAdjustX,
                currentCamera.panY + event.panDelta.y + panAdjustY
            )
        } else {
            // No zoom, just apply pan delta
            Pair(
                currentCamera.panX + event.panDelta.x,
                currentCamera.panY + event.panDelta.y
            )
        }

        val newCamera = CameraTransform(
            panX = newPanX,
            panY = newPanY,
            zoom = newZoom
        )

        Logger.d { "  Camera: pan=($newPanX, $newPanY), zoom=$newZoom" }

        state.copy(cameraTransform = newCamera)
    }
}

/**
 * Static helper: convert screen to world (for use outside DrawScope)
 */
private fun screenToWorldStatic(
    screenOffset: Offset,
    camera: CameraTransform,
): Point2 {
    val worldX = ((screenOffset.x - camera.panX) / camera.zoom).toDouble()
    val worldY = ((screenOffset.y - camera.panY) / camera.zoom).toDouble()
    return Point2(worldX, worldY)
}
```

**Zoom-to-Point Algorithm**:
1. User pinches at screen point `P_screen`
2. Convert `P_screen` to world coordinates: `P_world` (using old zoom)
3. Apply new zoom
4. Calculate where `P_world` would appear in screen space with new zoom
5. Adjust pan so `P_world` still appears at `P_screen`

**Result**: Zoom feels "attached" to fingers, not canvas center

---

## Phase 1.2.6: Testing & Validation

### 6.1 Manual Testing

**Setup**:
1. Open FloorPlan mode with existing vertices/lines
2. Enable Kermit debug logs

**Test Cases**:

**Pan Gesture**:
- [ ] Two-finger drag moves viewport smoothly
- [ ] Grid moves with viewport
- [ ] Vertices/lines move with viewport
- [ ] Tap-to-place still works after panning
- [ ] New vertex appears at correct world position

**Zoom Gesture**:
- [ ] Pinch-out zooms in (geometry gets larger)
- [ ] Pinch-in zooms out (geometry gets smaller)
- [ ] Zoom clamped at 0.1x (can't zoom out infinitely)
- [ ] Zoom clamped at 10x (can't zoom in infinitely)
- [ ] Grid spacing scales with zoom
- [ ] Vertex size stays constant (not scaled)

**Zoom-to-Point**:
- [ ] Pinch around a vertex keeps vertex under fingers
- [ ] Zoom feels "attached" to gesture center
- [ ] Zoom + pan work together (simultaneous pinch-drag)

**Coordinate Accuracy**:
- [ ] Place vertex at (100, 100) world coordinates
- [ ] Pan and zoom arbitrarily
- [ ] Place second vertex at (200, 100)
- [ ] Line between them is exactly 100 units (check logs)
- [ ] Grid snap still works correctly

### 6.2 Console Output Validation

**Expected logs after pan**:
```
→ Camera transform: pan=(25.3, -10.5), zoom=1.0
  Camera: pan=(25.3, -10.5), zoom=1.0
```

**Expected logs after zoom**:
```
→ Camera transform: pan=(0.0, 0.0), zoom=1.5
  Camera: pan=(0.0, 0.0), zoom=1.5
```

**Expected logs after tap (zoomed)**:
```
→ Tap detected at screen (400.0, 300.0)
  World: (266.67, 200.0) → Snapped: (260.0, 200.0)
✓ Vertex created: abc-123 at (260.0, 200.0)
```

### 6.3 Edge Cases

Test these scenarios:

- [ ] Pan to extreme positions (10000, 10000) - no crashes
- [ ] Zoom to MIN_ZOOM (0.1x) - geometry still renders
- [ ] Zoom to MAX_ZOOM (10x) - no precision loss
- [ ] Rapid pan+zoom simultaneously - smooth updates
- [ ] Place 100 vertices, zoom out - all render correctly

---

## Success Criteria

✅ Phase 1.2 complete when:

- [ ] Two-finger pan moves viewport smoothly
- [ ] Pinch gesture zooms in/out (0.1x to 10x)
- [ ] Grid moves and scales with camera
- [ ] Vertices/lines rendered in correct screen positions
- [ ] Tap-to-place works correctly in transformed space (world coordinates)
- [ ] Zoom-to-point feels natural (zooms around fingers)
- [ ] No coordinate drift or precision loss
- [ ] Console logs show correct camera transforms
- [ ] No crashes or visual glitches

---

## File Changes Summary

**Modified Files**:
1. `AppEvent.kt` - Add `GeometryEvent.CameraTransformed`
2. `GeometryManager.kt` - Add `handleCameraTransformed()` with zoom-to-point
3. `DrawingCanvas.kt` - Add gesture detection, coordinate transforms, update rendering

**No new files** - this phase only extends existing code

---

## Architecture Notes

**Event-Driven Camera**:
- ✅ Gestures emit events (not direct mutation)
- ✅ GeometryManager listens and updates state
- ✅ Immutable CameraTransform with copy semantics
- ✅ Reactive rendering (Compose recomposes on state change)

**Coordinate System Design**:
- **World space**: Double precision, infinite, CAD units (cm/inches)
- **Screen space**: Float precision, viewport-relative, pixels
- **Transform chain**: World → Scale (zoom) → Translate (pan) → Screen
- **Inverse chain**: Screen → Subtract pan → Divide by zoom → World

**Performance Considerations**:
- `detectTransformGestures` emits many events during drag/pinch (30-60 fps)
- `StateFlow.update` only triggers recompose if state actually changes
- Grid rendering is O(viewport_size / grid_spacing) - constant time
- Vertex/line rendering is O(n) where n = entities in world (no culling yet)

**Future Optimizations** (not this phase):
- Frustum culling: only render entities in viewport
- Spatial indexing: R-tree for fast viewport queries
- DrawWithCache: cache grid/geometry paths between frames

---

## Next Phase

**Phase 1.3**: Smart Snapping
- Vertex snapping (snap to existing vertices)
- Edge snapping (snap to nearest point on edge)
- Perpendicular snapping (snap perpendicular to edges)
- Snap indicators (visual feedback)

---

**Implementation Order**:
1. Phase 1.2.1 - Camera events (foundation)
2. Phase 1.2.2 - Gesture detection (input)
3. Phase 1.2.3 - Coordinate transforms (math)
4. Phase 1.2.4 - Rendering (output)
5. Phase 1.2.5 - Zoom-to-point (polish)
6. Phase 1.2.6 - Testing (validation)

**Estimated Time**: 2-3 hours of implementation + testing