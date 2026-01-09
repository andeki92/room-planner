# Phase 1.4: Selection and Editing - Implementation Plan

**Goal**: Implement selection and editing capabilities for vertices and lines, enabling users to modify existing geometry.

**Current State**:
- ✅ Phase 1.1: Drawing (tap to place, continuous lines)
- ✅ Phase 1.2: Pan/zoom navigation
- ✅ Phase 1.3: Smart snapping (vertex, edge, perpendicular)
- ❌ Cannot select vertices or lines
- ❌ Cannot move vertices after placement
- ❌ Cannot delete geometry
- ❌ No multi-select

**Target State**:
- Tap vertex/line to select it (visual highlight)
- Drag selected vertex to move it (connected lines update)
- Delete key removes selected entities
- Drag rectangle for multi-select
- Clear selection by tapping empty space
- Undo/redo support (via Command pattern)

---

## Overview

Selection and editing transforms the app from **draw-only** to **editable CAD**.

**Key Capabilities**:
1. **Select** - Identify entities to operate on
2. **Move** - Adjust vertex positions (parametric updates)
3. **Delete** - Remove entities (topology cleanup)
4. **Multi-select** - Batch operations

**Architecture Pattern**: Event-driven selection state
- `DrawingCanvas` emits `SelectionEvent.EntitySelected`
- `SelectionManager` maintains selection state
- `EditCommand` implements undo/redo
- Visual layer renders selection highlights

---

## Phase 1.4.1: Selection State Model

### 1.1 Add Selection State to AppState

**File**: `shared/src/commonMain/kotlin/com/roomplanner/data/models/AppState.kt`

**Change**: Add selection tracking

```kotlin
@Serializable
data class AppState(
    // ... existing fields ...

    // Drawing tool state
    val activeVertexId: String? = null, // Last placed vertex (for continuous drawing)

    // Selection state (Phase 1.4)
    val selectedVertices: Set<String> = emptySet(),
    val selectedLines: Set<String> = emptySet(),
    val selectionMode: SelectionMode = SelectionMode.Single,
) {
    // ... existing methods ...

    /**
     * Helper: Check if entity is selected.
     */
    fun isVertexSelected(vertexId: String) = vertexId in selectedVertices
    fun isLineSelected(lineId: String) = lineId in selectedLines
    fun hasSelection() = selectedVertices.isNotEmpty() || selectedLines.isNotEmpty()

    /**
     * Helper: Clear all selections.
     */
    fun clearSelection() = copy(
        selectedVertices = emptySet(),
        selectedLines = emptySet()
    )

    /**
     * Helper: Select single vertex (replaces selection in Single mode).
     */
    fun selectVertex(vertexId: String, mode: SelectionMode) = when (mode) {
        SelectionMode.Single -> copy(
            selectedVertices = setOf(vertexId),
            selectedLines = emptySet()
        )
        SelectionMode.Multi -> copy(
            selectedVertices = selectedVertices + vertexId
        )
    }

    /**
     * Helper: Select single line.
     */
    fun selectLine(lineId: String, mode: SelectionMode) = when (mode) {
        SelectionMode.Single -> copy(
            selectedLines = setOf(lineId),
            selectedVertices = emptySet()
        )
        SelectionMode.Multi -> copy(
            selectedLines = selectedLines + lineId
        )
    }
}

/**
 * Selection mode for multi-select operations.
 */
@Serializable
enum class SelectionMode {
    Single,  // Tap replaces selection
    Multi    // Tap adds to selection (Shift key held)
}
```

### 1.2 Add Selection Events

**File**: `shared/src/commonMain/kotlin/com/roomplanner/data/events/AppEvent.kt`

**Change**: Add selection event types

```kotlin
/**
 * Selection events (Phase 1.4)
 * User interactions with existing geometry.
 */
sealed interface SelectionEvent : AppEvent {
    /**
     * User tapped to select entity (vertex or line).
     */
    data class EntitySelected(
        val vertexId: String? = null,
        val lineId: String? = null,
        val mode: com.roomplanner.data.models.SelectionMode = com.roomplanner.data.models.SelectionMode.Single
    ) : SelectionEvent

    /**
     * User cleared selection (tapped empty space).
     */
    data object SelectionCleared : SelectionEvent

    /**
     * User started dragging selected vertex.
     */
    data class VertexDragStarted(
        val vertexId: String,
        val startPosition: com.roomplanner.domain.geometry.Point2
    ) : SelectionEvent

    /**
     * User moved vertex during drag.
     */
    data class VertexDragged(
        val vertexId: String,
        val newPosition: com.roomplanner.domain.geometry.Point2
    ) : SelectionEvent

    /**
     * User finished dragging vertex.
     */
    data class VertexDragEnded(
        val vertexId: String,
        val finalPosition: com.roomplanner.domain.geometry.Point2
    ) : SelectionEvent

    /**
     * User requested delete of selected entities.
     */
    data object DeleteSelected : SelectionEvent
}
```

---

## Phase 1.4.2: Selection Detection

### 2.1 Update DrawingCanvas Tap Handler

**File**: `shared/src/commonMain/kotlin/com/roomplanner/ui/components/DrawingCanvas.kt`

**Change**: Distinguish between select tap and draw tap

```kotlin
/**
 * Handle tap gesture: select entity or place vertex.
 * Phase 1.4: Mode-based behavior (select vs draw)
 */
private suspend fun handleTap(
    screenOffset: Offset,
    state: AppState,
    eventBus: EventBus,
) {
    Logger.d { "→ Tap detected at screen (${screenOffset.x}, ${screenOffset.y})" }

    // Check if tapping near existing entity (selection)
    val (tappedVertex, tappedLine) = findTappedEntity(
        screenOffset,
        state,
        state.cameraTransform,
        tapRadius = 30f  // 30px tap target
    )

    if (tappedVertex != null) {
        // Select vertex
        Logger.i { "✓ Selected vertex: $tappedVertex" }
        eventBus.emit(
            SelectionEvent.EntitySelected(
                vertexId = tappedVertex,
                mode = SelectionMode.Single  // TODO: Detect Shift key for multi-select
            )
        )
        return
    }

    if (tappedLine != null) {
        // Select line
        Logger.i { "✓ Selected line: $tappedLine" }
        eventBus.emit(
            SelectionEvent.EntitySelected(
                lineId = tappedLine,
                mode = SelectionMode.Single
            )
        )
        return
    }

    // No entity tapped - check if we have selection (clear it or place vertex)
    if (state.hasSelection()) {
        // Clear selection
        Logger.d { "  Cleared selection" }
        eventBus.emit(SelectionEvent.SelectionCleared)
        return
    }

    // No selection, no entity → place new vertex (existing Phase 1.1 logic)
    val worldPoint = screenToWorld(screenOffset, state.cameraTransform)
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
        is SnapResult.Edge -> Pair(snapResult.position, null)
        is SnapResult.Perpendicular -> Pair(snapResult.position, null)
    }

    Logger.d { "  World: (${worldPoint.x}, ${worldPoint.y}) → Snapped: (${snappedPoint.x}, ${snappedPoint.y})" }

    eventBus.emit(
        GeometryEvent.PointPlaced(
            position = snappedPoint,
            snappedTo = snappedTo,
        ),
    )
}
```

### 2.2 Implement Entity Hit Testing

Add helper function:

```kotlin
/**
 * Find entity (vertex or line) near tap position.
 * Uses screen-space distance for consistent behavior.
 *
 * @return Pair(vertexId, lineId) - at most one will be non-null
 */
private fun findTappedEntity(
    tapScreen: Offset,
    state: AppState,
    camera: CameraTransform,
    tapRadius: Float,
): Pair<String?, String?> {
    // Priority 1: Check vertices first (smaller targets, higher priority)
    state.vertices.values.forEach { vertex ->
        val vertexScreen = worldToScreen(vertex.position, camera)
        val distance = distanceScreen(tapScreen, vertexScreen)

        if (distance < tapRadius) {
            return Pair(vertex.id, null)
        }
    }

    // Priority 2: Check lines
    state.lines.forEach { line ->
        val startScreen = worldToScreen(line.geometry.start, camera)
        val endScreen = worldToScreen(line.geometry.end, camera)

        // Distance from tap point to line segment
        val distance = distanceToSegment(tapScreen, startScreen, endScreen)

        if (distance < tapRadius) {
            return Pair(null, line.id)
        }
    }

    // Nothing tapped
    return Pair(null, null)
}

/**
 * Calculate distance from point to line segment (screen space).
 */
private fun distanceToSegment(
    point: Offset,
    start: Offset,
    end: Offset,
): Float {
    val (nearestPoint, _) = nearestPointOnSegment(point, start, end)
    return distanceScreen(point, nearestPoint)
}

private fun distanceScreen(a: Offset, b: Offset): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    return sqrt(dx * dx + dy * dy)
}

// nearestPointOnSegment already exists from Phase 1.3
```

---

## Phase 1.4.3: Selection Manager

### 3.1 Create SelectionManager

**File**: `shared/src/commonMain/kotlin/com/roomplanner/domain/selection/SelectionManager.kt` (NEW)

```kotlin
package com.roomplanner.domain.selection

import co.touchlab.kermit.Logger
import com.roomplanner.data.StateManager
import com.roomplanner.data.events.EventBus
import com.roomplanner.data.events.SelectionEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * SelectionManager handles selection events and updates state.
 *
 * Responsibilities:
 * - Maintain selection state (which entities are selected)
 * - Handle selection mode (single vs multi)
 * - Emit delete commands when user requests deletion
 */
class SelectionManager(
    private val eventBus: EventBus,
    private val stateManager: StateManager,
) {
    init {
        Logger.i { "✓ SelectionManager initialized" }

        // Listen to selection events
        eventBus.events
            .filterIsInstance<SelectionEvent>()
            .onEach { event -> handleEvent(event) }
            .launchIn(CoroutineScope(Dispatchers.Default))
    }

    private suspend fun handleEvent(event: SelectionEvent) {
        when (event) {
            is SelectionEvent.EntitySelected -> handleEntitySelected(event)
            is SelectionEvent.SelectionCleared -> handleSelectionCleared()
            is SelectionEvent.VertexDragStarted -> handleVertexDragStarted(event)
            is SelectionEvent.VertexDragged -> handleVertexDragged(event)
            is SelectionEvent.VertexDragEnded -> handleVertexDragEnded(event)
            is SelectionEvent.DeleteSelected -> handleDeleteSelected()
        }
    }

    private suspend fun handleEntitySelected(event: SelectionEvent.EntitySelected) {
        stateManager.updateState { state ->
            when {
                event.vertexId != null -> {
                    Logger.d { "→ Selected vertex: ${event.vertexId}" }
                    state.selectVertex(event.vertexId, event.mode)
                }
                event.lineId != null -> {
                    Logger.d { "→ Selected line: ${event.lineId}" }
                    state.selectLine(event.lineId, event.mode)
                }
                else -> state
            }
        }
    }

    private suspend fun handleSelectionCleared() {
        Logger.d { "→ Selection cleared" }
        stateManager.updateState { it.clearSelection() }
    }

    private suspend fun handleVertexDragStarted(event: SelectionEvent.VertexDragStarted) {
        Logger.d { "→ Vertex drag started: ${event.vertexId}" }
        // Drag state handled in Phase 1.4.4
    }

    private suspend fun handleVertexDragged(event: SelectionEvent.VertexDragged) {
        // Real-time update during drag
        stateManager.updateState { state ->
            val vertex = state.vertices[event.vertexId] ?: return@updateState state
            val updatedVertex = vertex.copy(position = event.newPosition)
            state.copy(vertices = state.vertices + (event.vertexId to updatedVertex))
        }
    }

    private suspend fun handleVertexDragEnded(event: SelectionEvent.VertexDragEnded) {
        Logger.i { "✓ Vertex moved: ${event.vertexId} → (${event.finalPosition.x}, ${event.finalPosition.y})" }
        // Final position already applied in handleVertexDragged
        // Emit MoveCommand for undo/redo support (Phase 1.4.5)
    }

    private suspend fun handleDeleteSelected() {
        val state = stateManager.state.value

        Logger.i { "✓ Delete: ${state.selectedVertices.size} vertices, ${state.selectedLines.size} lines" }

        stateManager.updateState { state ->
            // Remove selected vertices
            val remainingVertices = state.vertices.filterKeys { it !in state.selectedVertices }

            // Remove selected lines + lines connected to deleted vertices
            val remainingLines = state.lines.filter { line ->
                line.id !in state.selectedLines &&
                    line.geometry.startVertexId !in state.selectedVertices &&
                    line.geometry.endVertexId !in state.selectedVertices
            }

            state.copy(
                vertices = remainingVertices,
                lines = remainingLines,
                selectedVertices = emptySet(),
                selectedLines = emptySet()
            )
        }
    }
}
```

### 3.2 Register SelectionManager in DI

**File**: `shared/src/commonMain/kotlin/com/roomplanner/di/AppModule.kt`

```kotlin
val commonModule = module {
    single { EventBus() }
    single { StateManager() }
    single { GeometryManager(get(), get()) }
    single { SelectionManager(get(), get()) }  // NEW
}
```

---

## Phase 1.4.4: Drag-to-Move Vertices

### 4.1 Add Drag Detection to DrawingCanvas

**File**: `DrawingCanvas.kt`

**Change**: Add drag gesture handler

```kotlin
@Composable
fun DrawingCanvas(
    state: AppState,
    eventBus: EventBus,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    var draggedVertexId by remember { mutableStateOf<String?>(null) }

    Canvas(
        modifier =
            modifier
                .fillMaxSize()
                // Existing tap gesture
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { screenOffset ->
                            coroutineScope.launch {
                                handleTap(screenOffset, state, eventBus)
                            }
                        },
                    )
                }
                // Existing transform gestures (pan/zoom)
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        coroutineScope.launch {
                            handleTransform(pan, zoom, centroid, eventBus)
                        }
                    }
                }
                // NEW: Drag gesture for moving vertices
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            // Check if dragging a selected vertex
                            val (vertexId, _) = findTappedEntity(
                                startOffset,
                                state,
                                state.cameraTransform,
                                tapRadius = 30f
                            )

                            if (vertexId != null && state.isVertexSelected(vertexId)) {
                                draggedVertexId = vertexId
                                val vertex = state.vertices[vertexId]!!
                                coroutineScope.launch {
                                    eventBus.emit(
                                        SelectionEvent.VertexDragStarted(
                                            vertexId = vertexId,
                                            startPosition = vertex.position
                                        )
                                    )
                                }
                            }
                        },
                        onDrag = { change, dragAmount ->
                            val vertexId = draggedVertexId ?: return@detectDragGestures

                            // Convert drag delta to world space
                            val dragWorld = Point2(
                                (dragAmount.x / state.cameraTransform.zoom).toDouble(),
                                (dragAmount.y / state.cameraTransform.zoom).toDouble()
                            )

                            val currentVertex = state.vertices[vertexId] ?: return@detectDragGestures
                            val newPosition = Point2(
                                currentVertex.position.x + dragWorld.x,
                                currentVertex.position.y + dragWorld.y
                            )

                            coroutineScope.launch {
                                eventBus.emit(
                                    SelectionEvent.VertexDragged(
                                        vertexId = vertexId,
                                        newPosition = newPosition
                                    )
                                )
                            }

                            change.consume()
                        },
                        onDragEnd = {
                            val vertexId = draggedVertexId ?: return@detectDragGestures
                            val finalVertex = state.vertices[vertexId] ?: return@detectDragGestures

                            coroutineScope.launch {
                                eventBus.emit(
                                    SelectionEvent.VertexDragEnded(
                                        vertexId = vertexId,
                                        finalPosition = finalVertex.position
                                    )
                                )
                            }

                            draggedVertexId = null
                        }
                    )
                },
    ) {
        // ... existing drawing code ...
    }
}
```

**Key Points**:
- `detectDragGestures` provides start/move/end events
- Only selected vertices can be dragged (check `isVertexSelected`)
- Drag delta converted to world space (accounts for zoom)
- Connected lines update automatically (reactive state)

---

## Phase 1.4.5: Visual Selection Indicators

### 5.1 Update Vertex Rendering

**File**: `DrawingCanvas.kt`

**Change**: Highlight selected vertices

```kotlin
private fun DrawScope.drawVertices(
    state: AppState,
    camera: CameraTransform,
    config: com.roomplanner.data.models.DrawingConfig,
) {
    state.vertices.values.forEach { vertex ->
        val isActive = vertex.id == state.activeVertexId
        val isSelected = state.isVertexSelected(vertex.id)

        val color =
            when {
                isSelected -> Color.Yellow  // Selected = yellow highlight
                vertex.fixed -> config.vertexColorFixedCompose()
                isActive -> config.vertexColorActiveCompose()
                else -> config.vertexColorNormalCompose()
            }

        val screenPosition = worldToScreen(vertex.position, camera)

        // Draw larger circle if selected
        val radius = if (isSelected) config.vertexRadius * 1.5f else config.vertexRadius

        drawCircle(
            color = color,
            radius = radius,
            center = screenPosition,
        )

        // Draw outline
        drawCircle(
            color = if (isSelected) Color.Black else config.vertexOutlineColorCompose(),
            radius = radius,
            center = screenPosition,
            style = Stroke(width = config.vertexStrokeWidth),
        )
    }
}
```

### 5.2 Update Line Rendering

**File**: Same file

**Change**: Highlight selected lines

```kotlin
private fun DrawScope.drawLines(
    state: AppState,
    camera: CameraTransform,
    config: com.roomplanner.data.models.DrawingConfig,
) {
    state.lines.forEach { line ->
        val isSelected = state.isLineSelected(line.id)

        val start = worldToScreen(line.geometry.start, camera)
        val end = worldToScreen(line.geometry.end, camera)

        val color = if (isSelected) Color.Yellow else config.lineColorCompose()
        val strokeWidth = if (isSelected) config.lineStrokeWidth * 2f else config.lineStrokeWidth

        drawLine(
            color = color,
            start = start,
            end = end,
            strokeWidth = strokeWidth,
        )
    }
}
```

---

## Phase 1.4.6: Keyboard Shortcuts

### 6.1 Delete Key Handler

**Platform-specific implementation** (Android/iOS differ)

**Android**: Use `onKeyEvent` in `MainActivity`

**File**: `androidApp/src/main/kotlin/com/roomplanner/android/MainActivity.kt`

```kotlin
@Composable
override fun Content() {
    Box(modifier = Modifier.fillMaxSize().onKeyEvent { event ->
        if (event.key == Key.Delete && event.type == KeyEventType.KeyDown) {
            // Emit delete event
            val eventBus: EventBus = koinInject()
            GlobalScope.launch {
                eventBus.emit(SelectionEvent.DeleteSelected)
            }
            true
        } else {
            false
        }
    }) {
        App()
    }
}
```

**iOS**: Handle in SwiftUI wrapper (Phase 1.4.7)

---

## Phase 1.4.7: Testing & Validation

### 7.1 Manual Testing

**Test Case 1: Select Vertex**
- [ ] Tap vertex → yellow highlight appears
- [ ] Tap again → stays selected (no toggle)
- [ ] Tap empty space → selection clears
- [ ] Console: "✓ Selected vertex: {id}"

**Test Case 2: Select Line**
- [ ] Tap line → yellow highlight + thicker stroke
- [ ] Tap empty space → selection clears
- [ ] Console: "✓ Selected line: {id}"

**Test Case 3: Drag Vertex**
- [ ] Select vertex, then drag → vertex follows cursor
- [ ] Connected lines update in real-time
- [ ] Release → vertex stays at new position
- [ ] Console: "✓ Vertex moved: {id} → ({x}, {y})"

**Test Case 4: Delete**
- [ ] Select vertex, press Delete → vertex removed
- [ ] Connected lines also removed
- [ ] Select line, press Delete → line removed
- [ ] Vertices remain (orphaned vertices OK for Phase 1.4)

**Test Case 5: Selection Priority**
- [ ] Tap overlapping vertex and line → vertex selected (higher priority)
- [ ] Clear selection, tap to draw → new vertex placed (not select)
- [ ] Select vertex, tap to draw → selection clears FIRST, then next tap draws

### 7.2 Edge Cases

- [ ] Drag vertex outside canvas → works (no bounds)
- [ ] Drag vertex onto another vertex → allowed (snapping disabled during drag)
- [ ] Delete last vertex → empty canvas (no crash)
- [ ] Select and delete while drawing → works (modes independent)
- [ ] Pan/zoom while vertex selected → selection stays on vertex

### 7.3 Console Output Validation

```
→ Tap detected at screen (400.0, 300.0)
✓ Selected vertex: abc-123
→ Vertex drag started: abc-123
→ Vertex moved: abc-123 → (450.0, 320.0)
✓ Vertex moved: abc-123 → (450.0, 320.0)
```

---

## Success Criteria

✅ Phase 1.4 complete when:

- [ ] Tap selects vertex or line (yellow highlight)
- [ ] Tap empty space clears selection
- [ ] Drag selected vertex moves it smoothly
- [ ] Connected lines update during drag
- [ ] Delete key removes selected entities
- [ ] Orphaned vertices handled gracefully
- [ ] Selection indicators render correctly
- [ ] All manual tests pass
- [ ] No crashes or state corruption

---

## File Changes Summary

**New Files**:
1. `SelectionManager.kt` - Selection event handler

**Modified Files**:
1. `AppState.kt` - Add selection state fields
2. `AppEvent.kt` - Add `SelectionEvent` types
3. `DrawingCanvas.kt` - Add drag gestures, selection rendering
4. `AppModule.kt` - Register SelectionManager

**Platform-Specific**:
1. `MainActivity.kt` (Android) - Delete key handler
2. `ContentView.swift` (iOS) - Delete gesture (Phase 1.4.7)

---

## Architecture Notes

**Selection vs Drawing Modes**:
- No explicit mode switch (smart context detection)
- Tap on entity → select
- Tap on empty + no selection → draw
- Tap on empty + has selection → clear selection

**Reactive Line Updates**:
- Lines reference vertices by ID
- Vertices store position
- When vertex moves → lines automatically recompute endpoints
- No manual line update needed (reactive Compose)

**Performance**:
- Hit testing: O(n) vertices + O(n) lines per tap
- Drag updates: O(1) state update (immutable copy)
- Rendering: O(n) entities (no optimization yet)

**Future Enhancements** (not this phase):
- Multi-select with Shift key
- Drag rectangle multi-select
- Undo/redo (Command pattern)
- Snap-while-dragging (align with grid/vertices)
- Constraint preservation (keep perpendicular angles)

---

## Next Phase

**Phase 2.0**: BREP Topology
- Replace simple edge graph with halfedge mesh
- Face detection (rooms)
- Wall thickness (offset curves)
- Opening entities (doors, windows)

**Phase 1 Complete!** 🎉
- Drawing: ✅
- Navigation: ✅
- Snapping: ✅
- Selection: ✅

---

**Estimated Time**: 4-5 hours implementation + testing