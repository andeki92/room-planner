# Phase 1: Floor Plan Drawing Mode - Overview

**Goal**: Implement complete floor plan drawing functionality with CAD-quality precision and editing capabilities.

**Status**:
- ✅ Phase 1.1: Basic Drawing (COMPLETE)
- 📋 Phase 1.2: Pan/Zoom Navigation (PLANNED)
- 📋 Phase 1.3: Smart Snapping (PLANNED)
- 📋 Phase 1.4: Selection & Editing (PLANNED)

---

## Vision

Phase 1 transforms Room Planner from a **project browser** into a **functional 2D CAD tool** for floor plan creation. By the end of Phase 1, users can:

1. **Draw** - Place vertices and create walls with tap gestures
2. **Navigate** - Pan and zoom to explore large floor plans
3. **Snap** - Use intelligent snapping for precision (vertex, edge, perpendicular, grid)
4. **Edit** - Select, move, and delete geometry
5. **Close shapes** - Create enclosed rooms by snapping to first vertex

**NOT in Phase 1** (deferred to Phase 2):
- BREP topology (halfedge mesh)
- Face detection (automatic room recognition)
- Wall thickness (offset curves)
- Doors and windows
- Constraints (parallel, perpendicular preservation)

---

## Sub-Phase Breakdown

### Phase 1.1: Basic Drawing ✅ COMPLETE

**Capabilities**:
- Tap to place vertices
- Continuous line drawing (tap-tap-tap)
- Grid snapping (fixed intervals)
- Visual rendering (grid, vertices, lines)
- Camera transform model (unused until 1.2)

**Key Files**:
- `DrawingCanvas.kt` - Touch input and rendering
- `GeometryManager.kt` - Event handler for vertex/line creation
- `AppState.kt` - Immutable state (vertices, lines, active vertex)
- `Point2.kt`, `Line.kt`, `Vertex.kt` - Geometry primitives

**Architecture Validated**:
- ✅ Event-driven: Tools emit events, systems react
- ✅ Immutable state: Data classes with copy semantics
- ✅ Reactive rendering: Compose recomposes on state change
- ✅ Kermit logging: All operations logged

**Test Results**:
- Can draw simple floor plans (rectangular rooms)
- Grid snapping works at 12" intervals (imperial)
- Lines connect vertices automatically
- No crashes, precision issues, or visual glitches

---

### Phase 1.2: Pan/Zoom Navigation 📋 PLANNED

**Plan**: `.claude/plans/phase-1-2-pan-zoom.md`

**Capabilities**:
- Two-finger pan gesture (drag viewport)
- Pinch-to-zoom gesture (0.1x to 10x range)
- Zoom-to-point (zoom follows fingers, not canvas center)
- Coordinate transforms (world ↔ screen space)
- Grid scales with zoom

**Key Changes**:
- `CameraTransform` model activated (pan, zoom)
- `screenToWorld()` / `worldToScreen()` functions
- `detectTransformGestures()` in DrawingCanvas
- `CameraTransformed` event type
- All rendering applies camera transform

**Architecture Pattern**:
- Gestures emit `GeometryEvent.CameraTransformed`
- GeometryManager updates `AppState.cameraTransform`
- Canvas re-renders with transform applied
- Tap-to-place accounts for camera (world coordinates preserved)

**Success Criteria**:
- Smooth pan and zoom (60fps)
- Grid stays aligned with world origin
- Vertex positions precise in world coordinates
- Zoom radius constant (screen-space snapping)

**Estimated Time**: 2-3 hours

---

### Phase 1.3: Smart Snapping 📋 PLANNED

**Plan**: `.claude/plans/phase-1-3-smart-snapping.md`

**Capabilities**:
- Vertex snapping (20px radius, screen space)
- Edge snapping (project onto nearest edge)
- Perpendicular snapping (90° constraint from active vertex)
- Visual snap indicators (orange circles)
- Priority system: vertex > edge > perpendicular > grid

**Key Changes**:
- `SmartSnapSystem` domain logic (snap detection)
- `SnapSettings` extended (enable flags, radii)
- `SnapResult` sealed class (vertex/edge/perp/grid)
- DrawingCanvas shows snap indicators
- `handleTap()` uses smart snap (replaces simple grid snap)

**Architecture Pattern**:
- `SmartSnapSystem.calculateSnap()` pure function
- Takes cursor position + app state → returns SnapResult
- Screen-space distance (pixels, not world units)
- O(n) linear search (acceptable for <1000 entities)

**Success Criteria**:
- Can close shapes (snap to first vertex)
- Can create perpendicular walls (90° snapping)
- Can extend walls (edge snapping)
- Snap indicators show before tap
- Priority system feels intuitive

**Estimated Time**: 3-4 hours

---

### Phase 1.4: Selection & Editing 📋 PLANNED

**Plan**: `.claude/plans/phase-1-4-selection-editing.md`

**Capabilities**:
- Tap vertex/line to select (yellow highlight)
- Drag selected vertex to move (connected lines update)
- Delete key removes selected entities
- Clear selection by tapping empty space
- Hit testing with 30px tap radius

**Key Changes**:
- `AppState` selection fields (selectedVertices, selectedLines)
- `SelectionEvent` types (EntitySelected, VertexDragged, etc.)
- `SelectionManager` event handler
- `detectDragGestures()` in DrawingCanvas
- Visual indicators (yellow highlight, larger radius)

**Architecture Pattern**:
- Tap detection: findTappedEntity() hit test
- Mode-based behavior: select vs draw (context-sensitive)
- Drag events: DragStarted → Dragged → DragEnded
- Real-time updates: vertex position updates during drag
- Reactive lines: automatically reconnect to moved vertices

**Success Criteria**:
- Can modify existing floor plans (not just draw)
- Drag feels smooth (real-time updates)
- Delete works for vertices and lines
- Selection state persists during pan/zoom
- No crashes when deleting last entity

**Estimated Time**: 4-5 hours

---

## Phase 1 Complete: Capabilities Summary

At the end of Phase 1, users can:

1. **Create floor plans from scratch**
   - Tap to place vertices
   - Continuous line drawing
   - Close polygons (snap to first vertex)

2. **Navigate large drawings**
   - Pan with two-finger drag
   - Zoom with pinch (0.1x to 10x)
   - Smooth 60fps interactions

3. **Draw with precision**
   - Grid snapping (12" intervals)
   - Vertex snapping (close shapes)
   - Edge snapping (midpoints, subdivisions)
   - Perpendicular snapping (rectangular rooms)

4. **Edit existing geometry**
   - Select vertices and lines
   - Move vertices by dragging
   - Delete entities with Delete key
   - Connected lines update automatically

5. **Professional workflow**
   - Consistent 20px tap/snap targets (screen space)
   - Visual feedback (snap indicators, selection highlights)
   - Event-driven architecture (undo/redo ready)
   - Kermit logging (debugging and validation)

---

## What Phase 1 Does NOT Include

**Deferred to Phase 2** (BREP Topology):
- Halfedge mesh data structure
- Face detection (automatic rooms)
- Wall thickness (parallel offset curves)
- Doors and windows (opening entities)
- Constraint solver (preserve angles/lengths)

**Deferred to Phase 3** (Materials):
- Tile patterns
- Flooring materials
- Wall finishes
- Material libraries

**Deferred to Phase 4** (Estimation):
- Material quantity calculation
- Cost estimation
- Shopping lists

**Why the deferral?**
- Phase 1 validates core architecture (event-driven, immutable state)
- Topology is complex (halfedge mesh requires careful design)
- Users can create floor plans without fancy topology
- Iterative delivery: working software every phase

---

## Technical Architecture (Phase 1)

### Data Flow (Event-Driven)

```
User Input (tap/drag/pinch)
    ↓
DrawingCanvas (gesture detection)
    ↓
EventBus.emit(GeometryEvent / SelectionEvent / CameraEvent)
    ↓
Managers listen (GeometryManager, SelectionManager)
    ↓
StateManager.updateState { immutable copy }
    ↓
Compose recomposes
    ↓
DrawingCanvas re-renders with new state
```

### State Structure

```kotlin
@Serializable
data class AppState(
    // Phase 0: Navigation
    val currentMode: AppMode = AppMode.ProjectBrowser,
    val settings: Settings = Settings.default(),

    // Phase 1.1: Drawing
    val vertices: Map<String, Vertex> = emptyMap(),  // O(1) lookup by ID
    val lines: List<Line> = emptyList(),
    val activeVertexId: String? = null,

    // Phase 1.2: Camera
    val cameraTransform: CameraTransform = CameraTransform.default(),

    // Phase 1.3: Snapping
    val snapSettings: SnapSettings = SnapSettings.defaultImperial(),

    // Phase 1.4: Selection
    val selectedVertices: Set<String> = emptySet(),
    val selectedLines: Set<String> = emptySet(),
)
```

### Geometry Primitives

```kotlin
// Double precision for CAD accuracy
@Serializable
data class Point2(val x: Double, val y: Double)

@Serializable
data class Vertex(
    val id: String,
    val position: Point2,
    val fixed: Boolean = false  // Constraint solver (Phase 2)
)

@Serializable
data class Line(
    val id: String,
    val geometry: Line2,  // start/end vertices
    val style: LineStyle = LineStyle.WALL
)

// Geometry (no ID, just math)
@Serializable
data class Line2(
    val start: Point2,
    val end: Point2
) {
    fun length(): Double = start.distanceTo(end)
    fun midpoint(): Point2 = Point2((start.x + end.x) / 2, (start.y + end.y) / 2)
}
```

### Coordinate Systems

**World Space**:
- Origin: (0, 0) at top-left of canvas
- Units: Inches (imperial) or centimeters (metric)
- Precision: Double (64-bit float)
- Range: Infinite (virtual space)

**Screen Space**:
- Origin: (0, 0) at top-left of device screen
- Units: Pixels
- Precision: Float (32-bit)
- Range: Viewport size (e.g., 1920x1080)

**Transform Chain**:
```
World → Screen: screen = (world * zoom) + pan
Screen → World: world = (screen - pan) / zoom
```

### Event Types

```kotlin
sealed interface AppEvent

// Phase 1.1: Geometry
sealed interface GeometryEvent : AppEvent {
    data class PointPlaced(position: Point2, snappedTo: String?)
}

// Phase 1.2: Camera
sealed interface GeometryEvent : AppEvent {
    data class CameraTransformed(panDelta: Offset, zoomDelta: Float, zoomCenter: Offset?)
}

// Phase 1.4: Selection
sealed interface SelectionEvent : AppEvent {
    data class EntitySelected(vertexId: String?, lineId: String?)
    data object SelectionCleared
    data class VertexDragged(vertexId: String, newPosition: Point2)
    data object DeleteSelected
}
```

---

## Performance Characteristics (Phase 1)

### Time Complexity

| Operation | Complexity | Notes |
|-----------|-----------|-------|
| Place vertex | O(1) | Map insert |
| Create line | O(1) | List append |
| Find vertex by ID | O(1) | Map lookup |
| Snap to vertex | O(n) | Linear search, n = vertex count |
| Snap to edge | O(n) | Linear search, n = line count |
| Hit test (select) | O(n) | Check all vertices + lines |
| Render vertices | O(n) | Draw each vertex |
| Render lines | O(n) | Draw each line |
| Pan/zoom | O(1) | Transform update |

### Optimization Opportunities (Phase 2+)

**Not needed for Phase 1** (premature optimization):
- Spatial indexing (R-tree) for O(log n) snap queries
- Frustum culling (only render visible entities)
- DrawWithCache (cache paths between frames)
- Instanced rendering (batch draw calls)

**Current limits** (acceptable for Phase 1):
- ~1000 vertices: Snap feels instant (<16ms)
- ~1000 lines: Rendering 60fps
- ~10,000 entities: Starts to lag (Phase 2 optimization)

---

## Testing Strategy (Phase 1)

### Manual Test Plan

After each sub-phase, perform:

**Basic Functionality**:
- [ ] Create simple rectangular room (4 vertices, 4 lines)
- [ ] Close shape by snapping to first vertex
- [ ] Pan and zoom smoothly
- [ ] Select and move vertex
- [ ] Delete vertex → connected lines removed

**Edge Cases**:
- [ ] Zoom to 0.1x (extreme zoom out)
- [ ] Zoom to 10x (extreme zoom in)
- [ ] Pan to (10000, 10000) world coordinates
- [ ] Delete all entities → empty canvas
- [ ] Place 100 vertices → no lag

**Cross-Platform**:
- [ ] Test on iOS simulator (iPhone 15)
- [ ] Test on Android emulator (Pixel 7)
- [ ] Verify gestures feel native on both

**Console Validation**:
- [ ] All operations logged with Kermit
- [ ] No error logs during normal use
- [ ] Logs match expected format (✓/→/✗ prefixes)

### Automated Tests (Future)

**Unit Tests** (commonTest):
- Geometry math (Point2.distanceTo, Line2.length)
- Snap detection (SmartSnapSystem.calculateSnap)
- Coordinate transforms (screenToWorld, worldToScreen)
- State updates (AppState.withVertex, selectVertex)

**Integration Tests** (androidTest / iosTest):
- Event flow (emit event → state updates)
- Gesture detection (tap → PointPlaced event)
- Rendering (state change → canvas recomposes)

**Not in Phase 1** (too early):
- UI tests (Compose test framework)
- Screenshot tests (visual regression)
- Performance benchmarks

---

## File Structure (Phase 1)

```
shared/src/commonMain/kotlin/com/roomplanner/
├── domain/
│   ├── geometry/
│   │   ├── Point2.kt                 # Geometry primitives
│   │   ├── Line2.kt
│   │   ├── GeometryManager.kt        # Phase 1.1: Event handler
│   │   └── GeometryExtensions.kt     # Helper functions
│   ├── drawing/
│   │   ├── Vertex.kt                 # Entity models
│   │   └── Line.kt
│   ├── snapping/
│   │   └── SmartSnapSystem.kt        # Phase 1.3: Snap detection
│   └── selection/
│       └── SelectionManager.kt       # Phase 1.4: Selection handler
│
├── data/
│   ├── models/
│   │   ├── AppState.kt               # Global state
│   │   ├── CameraTransform.kt        # Phase 1.2: Pan/zoom
│   │   ├── SnapSettings.kt           # Phase 1.3: Snap config
│   │   └── DrawingConfig.kt          # Visual constants
│   ├── events/
│   │   ├── AppEvent.kt               # Event types
│   │   └── EventBus.kt               # Event distribution
│   └── StateManager.kt               # State updates
│
├── ui/
│   ├── components/
│   │   └── DrawingCanvas.kt          # Phase 1: Input & rendering
│   └── screens/
│       └── FloorPlanScreen.kt        # Phase 1: Main screen
│
└── di/
    └── AppModule.kt                   # Dependency injection
```

---

## Implementation Timeline

### Sequential Order (Dependencies)

**Must implement in this order**:
1. **Phase 1.1** - Foundation (drawing, state, events)
2. **Phase 1.2** - Camera (requires Phase 1.1 state)
3. **Phase 1.3** - Snapping (requires Phase 1.2 transforms)
4. **Phase 1.4** - Selection (requires Phase 1.1-1.3 complete)

**Cannot skip phases** - each builds on previous

### Estimated Timeline

- **Phase 1.1**: ✅ Complete (3 hours)
- **Phase 1.2**: 2-3 hours
- **Phase 1.3**: 3-4 hours
- **Phase 1.4**: 4-5 hours

**Total**: ~12-15 hours for complete Phase 1

**Includes**:
- Implementation
- Testing (manual + validation)
- Bug fixes
- Documentation

**Does NOT include**:
- Design iteration (requirements stable)
- Performance optimization (deferred to Phase 2)
- Automated tests (deferred)

---

## Success Criteria (Phase 1 Complete)

✅ Phase 1 is complete when:

**Functional Requirements**:
- [ ] Can draw floor plans (tap to place vertices)
- [ ] Can navigate (pan/zoom smoothly)
- [ ] Can snap precisely (vertex/edge/perpendicular/grid)
- [ ] Can edit (select, move, delete)
- [ ] Can close shapes (snap to first vertex)

**Quality Requirements**:
- [ ] No crashes during normal use
- [ ] 60fps rendering (up to 1000 entities)
- [ ] Precise coordinates (Double precision preserved)
- [ ] Consistent behavior across zoom levels
- [ ] Works on iOS and Android

**Architecture Requirements**:
- [ ] Event-driven (no direct state mutation)
- [ ] Immutable state (data classes + copy)
- [ ] Reactive rendering (Compose integration)
- [ ] Kermit logging (all operations logged)
- [ ] Follows CLAUDE.md patterns

**Documentation Requirements**:
- [ ] All code commented (rationale + examples)
- [ ] Phase plans complete (implementation details)
- [ ] Manual test results documented
- [ ] Known issues listed (deferred features)

---

## Handoff to Phase 2

**Phase 2: BREP Topology** requires:

**From Phase 1**:
- ✅ Vertex/line data structures
- ✅ Event-driven architecture
- ✅ Immutable state management
- ✅ Drawing canvas infrastructure

**New in Phase 2**:
- Halfedge mesh (replace simple vertex-edge graph)
- Face detection (automatic room boundaries)
- Wall thickness (parallel offset curves)
- Opening entities (doors, windows, cutouts)
- Constraint solver (preserve angles/distances)

**Migration Strategy**:
- Keep Phase 1 code working during Phase 2
- Introduce `HalfEdge`, `Face` entities alongside `Line`, `Vertex`
- Migrate geometry incrementally (dual representation)
- Feature flag: toggle between simple edges and BREP
- Remove Phase 1 code once Phase 2 validated

**Estimated Phase 2 Time**: 20-30 hours (more complex)

---

## Conclusion

Phase 1 establishes Room Planner as a **functional 2D CAD tool**. By focusing on core drawing and editing before tackling topology complexity, we:

1. **Validate architecture** - Event-driven, immutable state works
2. **Ship working software** - Users can create floor plans
3. **Learn from usage** - Discover real requirements before over-engineering
4. **Iterate safely** - Can refactor topology without breaking drawing

**Phase 1 is the foundation** for all future phases. Get it right, and the rest follows naturally.

---

**Next Steps**: Implement Phase 1.2 (Pan/Zoom Navigation)

**See**: `.claude/plans/phase-1-2-pan-zoom.md`