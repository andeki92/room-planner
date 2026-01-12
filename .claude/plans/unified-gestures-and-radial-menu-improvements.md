# Unified Gesture System & Radial Menu Improvements

## Overview

Remove the DRAW/SELECT tool mode distinction in favor of context-aware gestures, and improve radial menu visuals.

---

## Part 1: Unified Gesture System

### Current State (Problem)

Two separate tool modes with redundant code:
- `ToolMode.DRAW` - tap/drag places vertices
- `ToolMode.SELECT` - tap selects, drag moves, long press shows menu
- Users must manually switch between modes
- ~450 lines across two handlers with overlapping logic

### Proposed: Context-Aware Gestures

| Gesture | On Vertex | On Line | On Empty Space |
|---------|-----------|---------|----------------|
| **Tap** | Select/activate | (nothing) | Place new vertex |
| **Drag** | Move vertex | (nothing) | Preview line → place on release |
| **Long Press** | Vertex radial menu | Line radial menu | (nothing) |

No mode switching required - gesture meaning depends on touch target.

### Files to Modify

| File | Action |
|------|--------|
| `data/models/ToolMode.kt` | Delete (or deprecate) |
| `ui/gestures/DrawToolGestureHandler.kt` | Delete |
| `ui/gestures/SelectToolGestureHandler.kt` | Delete |
| `ui/gestures/ToolGestureHandler.kt` | Simplify or remove interface |
| `ui/gestures/UnifiedGestureHandler.kt` | **Create** - single handler |
| `ui/gestures/CanvasGestureModifier.kt` | Remove toolMode routing |
| `ui/components/DrawingCanvas.kt` | Remove tool switcher UI |
| `data/models/ProjectDrawingState.kt` | Remove `toolMode` field |
| `data/StateManager.kt` | Remove tool mode state updates |

### UnifiedGestureHandler Design

```kotlin
class UnifiedGestureHandler {
    private var draggedVertexId: String? = null
    private var isPlacingNewVertex: Boolean = false

    suspend fun handlePress(screenPosition, drawingState, ...) {
        val hitVertex = HitTesting.findVertexAt(screenPosition, ...)

        if (hitVertex != null) {
            // Start vertex drag
            draggedVertexId = hitVertex
            eventBus.emit(GeometryEvent.VertexDragStarted(...))
        } else if (drawingState.activeVertexId != null) {
            // Start new line preview
            isPlacingNewVertex = true
            onPreview(worldPoint)
            onSnapHint(calculateSnap(...))
        }
    }

    suspend fun handleDrag(...) {
        when {
            draggedVertexId != null -> moveVertex(...)
            isPlacingNewVertex -> updatePreview(...)
        }
    }

    suspend fun handleRelease(...) {
        when {
            draggedVertexId != null -> finalizeMove(...)
            isPlacingNewVertex -> placeVertex(...)
        }
        clearState()
    }

    suspend fun handleTap(...) {
        val hitVertex = HitTesting.findVertexAt(...)
        if (hitVertex != null) {
            eventBus.emit(GeometryEvent.VertexSelected(hitVertex))
        } else {
            eventBus.emit(GeometryEvent.PointPlaced(...))
        }
    }

    suspend fun handleLongPress(...) {
        val hitVertex = HitTesting.findVertexAt(...)
        if (hitVertex != null) { onVertexMenu(hitVertex); return }

        val hitLine = HitTesting.findLineAt(...)
        if (hitLine != null) { onLineMenu(hitLine) }
    }
}
```

### Estimated Code Reduction

```
Before:
  DrawToolGestureHandler.kt     ~206 lines
  SelectToolGestureHandler.kt   ~241 lines
  ToolGestureHandler.kt         ~145 lines
  CanvasGestureModifier routing  ~50 lines
  ToolMode enum + state          ~30 lines
  Tool UI in DrawingCanvas       ~40 lines
  ─────────────────────────────────────────
  Total:                        ~712 lines

After:
  UnifiedGestureHandler.kt      ~180 lines
  Simplified modifier            ~30 lines
  ─────────────────────────────────────────
  Total:                        ~210 lines

Net reduction: ~500 lines (70% less)
```

---

## Part 2: Radial Menu Visual Improvements

### Current State

- Basic Material3 styling
- Two nearly identical components (~650 lines total)
- Limited visual feedback
- No animation

### Proposed Improvements

#### 2.1 Extract Common Component

```kotlin
data class RadialMenuItem(
    val id: String,
    val icon: ImageVector,
    val label: String,
    val angle: Float,           // Position in degrees
    val isDestructive: Boolean = false,
)

@Composable
fun RadialMenu(
    anchorPosition: Offset,
    items: List<RadialMenuItem>,
    onItemSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    config: RadialMenuConfig = RadialMenuConfig.default(),
)
```

#### 2.2 Visual Enhancements

| Improvement | Description |
|-------------|-------------|
| **Blur backdrop** | iOS-style blur instead of solid dim |
| **Scale animation** | Items pop in with spring animation |
| **Selection glow** | Pulsing glow on hover/drag-over |
| **Haptic feedback** | Already implemented, ensure consistent |
| **Icon improvements** | Larger icons, better contrast |
| **Arc indicator** | Subtle arc showing menu bounds |

#### 2.3 Configuration

```kotlin
data class RadialMenuConfig(
    val radius: Dp = 60.dp,
    val itemSize: Dp = 52.dp,
    val backgroundColor: Color = Color.Black.copy(alpha = 0.4f),
    val useBlurBackdrop: Boolean = true,
    val animationDuration: Int = 200,
    val showLabels: Boolean = false,  // Show text labels below icons
)
```

### Files to Modify

| File | Action |
|------|--------|
| `ui/components/RadialMenu.kt` | **Create** - shared component |
| `ui/components/VertexRadialMenu.kt` | Refactor to use RadialMenu |
| `ui/components/LineRadialMenu.kt` | Refactor to use RadialMenu |
| `data/models/DrawingConfig.kt` | Add RadialMenuConfig |

### Estimated Code Reduction

```
Before:
  VertexRadialMenu.kt  ~329 lines
  LineRadialMenu.kt    ~325 lines
  ─────────────────────────────────
  Total:               ~654 lines

After:
  RadialMenu.kt        ~200 lines (shared)
  VertexRadialMenu.kt   ~40 lines (wrapper)
  LineRadialMenu.kt     ~40 lines (wrapper)
  ─────────────────────────────────
  Total:               ~280 lines

Net reduction: ~374 lines (57% less)
```

---

## Implementation Order

### Phase 1: Radial Menu Refactor (Lower Risk)
1. Create `RadialMenu.kt` shared component
2. Refactor `VertexRadialMenu.kt` to use it
3. Refactor `LineRadialMenu.kt` to use it
4. Add visual improvements (blur, animations)
5. Test on iOS

### Phase 2: Unified Gestures (Higher Impact)
1. Create `UnifiedGestureHandler.kt`
2. Update `CanvasGestureModifier.kt` to use single handler
3. Move long press menu callbacks into handler
4. Update `DrawingCanvas.kt` to remove tool UI
5. Remove old handlers and ToolMode
6. Test all gesture combinations
7. Clean up unused code

---

## Verification

### Gesture Testing
- [ ] Tap on empty → places vertex
- [ ] Tap on vertex → selects it (makes active)
- [ ] Drag from empty → preview line → place on release
- [ ] Drag on vertex → moves vertex
- [ ] Long press on vertex → shows vertex menu
- [ ] Long press on line → shows line menu
- [ ] Two-finger zoom/pan still works
- [ ] Snap guidelines show correctly

### Radial Menu Testing
- [ ] Menu appears at correct position
- [ ] Items at correct angles
- [ ] Tap on item triggers action
- [ ] Drag to item triggers action
- [ ] Tap outside dismisses
- [ ] Animations smooth (60fps)
- [ ] Haptic feedback on selection

---

## Risk Assessment

| Risk | Mitigation |
|------|------------|
| Breaking existing workflows | Phase radial menu first (isolated) |
| Gesture conflicts | Extensive testing of all combinations |
| Performance regression | Profile before/after on iOS |
| Edge cases in hit testing | Keep current tolerance values (44dp) |

---

## Design Decisions

| Decision | Choice | Notes |
|----------|--------|-------|
| ToolMode | Remove completely | May reintroduce later for plugins (tiling, electrician) |
| Menu backdrop | iOS-style blur | Frosted glass effect, native feel |
| Menu labels | Icons only | Cleaner look, current behavior |