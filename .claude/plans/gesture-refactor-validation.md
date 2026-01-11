# Gesture Refactoring Validation Plan

## Overview

This document provides step-by-step manual testing procedures to validate the refactored gesture system in DrawingCanvas.

**Refactoring Summary:**
- Reduced from 1141 lines → 617 lines (46% reduction)
- Reduced from 4 nested `.pointerInput()` blocks → 2 clean modifiers
- Extracted utilities: CoordinateConversion, HitTesting
- Created strategy pattern: ToolGestureHandler interface
- Implemented tool handlers: DrawToolGestureHandler, SelectToolGestureHandler
- Created unified gesture detector: UnifiedGestureDetector
- Created compositional modifier: CanvasGestureModifier

**Compilation Status:** ✅ PASSED (Build successful)

---

## Test 1: DRAW Mode - Basic Vertex Placement

**Objective:** Verify press-drag-release gesture places vertices correctly.

### Steps:
1. Launch app on iOS Simulator
2. Navigate to FloorPlanScreen
3. Ensure DRAW mode is active (blue pencil icon in FAB)
4. Press finger down on canvas (don't release)
5. Drag finger to desired position
6. Release finger

### Expected Results:
- ✅ On press: Preview line appears from active vertex to cursor (if active vertex exists)
- ✅ On drag: Preview line follows cursor position smoothly
- ✅ On release: Vertex placed at release position, preview line disappears
- ✅ Console log: `"✓ DRAW: Vertex placed (exact/snapped): (x, y)"`

### Files Involved:
- `DrawToolGestureHandler.kt` - handlePress/Drag/Release
- `UnifiedGestureDetector.kt` - Distinguishes tap vs drag
- `CanvasGestureModifier.kt` - Routes to DRAW handler

---

## Test 2: DRAW Mode - Snap Hints

**Objective:** Verify snap hints appear when cursor near snap targets.

### Steps:
1. Place first vertex (V1)
2. Press down to start second vertex
3. Drag cursor near 90° angle from V1
4. Hold position (don't release)

### Expected Results:
- ✅ Orange dotted line appears when within 5° of 90° (snap hint)
- ✅ Preview line (solid) shows exact cursor position
- ✅ Snap hint line shows snapped position
- ✅ When released, vertex snaps to exact 90° position
- ✅ Console log: `"→ Right angle snap: 87° → 90° (diff: 3°)"`

### Files Involved:
- `DrawToolGestureHandler.kt` - handleDrag (calls SmartSnapSystem)
- `SmartSnapSystem.kt` - calculateSnap returns SnapResult.RightAngle
- `DrawingCanvas.kt` - drawPreviewLines renders snap hint

---

## Test 3: DRAW Mode - Tap vs Drag Distinction

**Objective:** Verify unified gesture detector properly distinguishes tap from drag.

### Steps:
1. **Quick Tap Test:**
   - Tap canvas quickly (< 200ms, < 10px movement)
   - Expected: Vertex placed immediately (tap behavior)

2. **Drag Test:**
   - Press, drag 20px, release
   - Expected: Preview line follows, vertex placed on release (drag behavior)

3. **Micro Movement Test:**
   - Press, move 5px (< 10px threshold), release quickly
   - Expected: Treated as tap (vertex placed immediately)

### Expected Results:
- ✅ Tap (< 200ms, < 10px): Vertex placed via handleTap
- ✅ Drag (≥ 10px movement): Vertex placed via handleRelease
- ✅ No gesture conflicts between tap and drag detectors
- ✅ Console logs show correct handler: `"→ DRAW: Press at..."` or tap message

### Files Involved:
- `UnifiedGestureDetector.kt` - Gesture classification logic (lines 86-107)
- `DrawToolGestureHandler.kt` - handleTap vs handleRelease

---

## Test 4: SELECT Mode - Vertex Tap for Radial Menu

**Objective:** Verify tapping vertex shows radial menu.

### Steps:
1. Switch to SELECT mode (hand icon in FAB radial menu)
2. Tap on an existing vertex
3. Observe radial menu appearance

### Expected Results:
- ✅ Radial menu appears at vertex position
- ✅ Menu shows options: Delete, Connect, Cancel
- ✅ Vertex highlighted (yellow glow)
- ✅ Console log: `"→ SELECT: Tapped vertex [ID]"`

### Files Involved:
- `SelectToolGestureHandler.kt` - handleTap
- `HitTesting.kt` - findVertexAt
- `CanvasGestureModifier.kt` - onVertexTapped callback (lines 110-134)
- `DrawingCanvas.kt` - RadialMenu composable

---

## Test 5: SELECT Mode - Vertex Drag

**Objective:** Verify dragging selected vertex moves it.

### Steps:
1. In SELECT mode, tap vertex to select (yellow highlight)
2. Press down on selected vertex
3. Drag to new position
4. Release

### Expected Results:
- ✅ On press: `VertexDragStarted` event emitted
- ✅ On drag: Vertex follows cursor smoothly
- ✅ On release: `VertexDragEnded` event emitted, vertex stays at new position
- ✅ Connected lines update positions in real-time
- ✅ Console logs:
   - `"→ SELECT: Drag started for vertex [ID]"`
   - `"✓ SELECT: Vertex drag ended: [ID]"`

### Files Involved:
- `SelectToolGestureHandler.kt` - handlePress/Drag/Release (lines 32-122)
- `GeometryManager.kt` - Reacts to VertexDragged events
- `CoordinateConversion.kt` - screenToWorld for drag delta

---

## Test 6: SELECT Mode - Line Tap for Radial Menu

**Objective:** Verify tapping line shows radial menu.

### Steps:
1. In SELECT mode, tap on a line (not near vertices)
2. Observe radial menu appearance

### Expected Results:
- ✅ Radial menu appears at tap position
- ✅ Menu shows options: Insert Vertex, Delete Line, Cancel
- ✅ Line highlighted (thicker stroke)
- ✅ Console log: `"→ SELECT: Tapped line [ID]"`

### Files Involved:
- `HitTesting.kt` - findLineAt (lines 38-76)
- `CanvasGestureModifier.kt` - onLineTapped callback (lines 137-148)
- `DrawingCanvas.kt` - LineRadialMenu composable

---

## Test 7: SELECT Mode - Empty Tap Closes Menus

**Objective:** Verify tapping empty space closes radial menus.

### Steps:
1. In SELECT mode, tap vertex to open radial menu
2. Tap on empty canvas area (not vertex, not line)

### Expected Results:
- ✅ Radial menu closes
- ✅ Selection cleared (no yellow highlight)
- ✅ Console log: `"→ SELECT: Tapped empty space"`

### Files Involved:
- `CanvasGestureModifier.kt` - onEmptyTapped callback (lines 151-152)
- `DrawingCanvas.kt` - closeRadialMenu state update

---

## Test 8: Pan/Zoom - Two-Finger Gestures

**Objective:** Verify pan/zoom works in both DRAW and SELECT modes.

### Steps:
1. **Pan Test:**
   - Place two fingers on canvas
   - Drag together (no pinch)
   - Expected: Canvas pans, grid moves

2. **Zoom Test:**
   - Place two fingers on canvas
   - Pinch outward (zoom in) / inward (zoom out)
   - Expected: Canvas zooms, vertices scale

3. **Combined Test:**
   - Pan and zoom simultaneously
   - Expected: Both work smoothly

### Expected Results:
- ✅ Pan works in DRAW mode (doesn't interfere with vertex placement)
- ✅ Pan works in SELECT mode (doesn't interfere with vertex selection)
- ✅ Zoom works in both modes
- ✅ Grid updates position/scale correctly
- ✅ Vertices and lines update positions correctly
- ✅ Console log: `CameraTransformed` events emitted

### Files Involved:
- `DrawingCanvas.kt` - detectTransformGestures modifier (lines 258-276)
- `GeometryManager.kt` - Reacts to CameraTransformed events

---

## Test 9: Gesture Conflicts - No Interference

**Objective:** Verify no conflicts between gesture detectors.

### Test Scenarios:

**Scenario A: DRAW mode tap vs pan/zoom**
- Quick tap → Should place vertex (not trigger pan)
- Two-finger gesture → Should pan/zoom (not place vertex)

**Scenario B: SELECT mode drag vs pan/zoom**
- Single finger drag on vertex → Should move vertex (not pan)
- Two-finger drag → Should pan (not move vertex)

**Scenario C: Fast gestures**
- Rapid tap-tap-tap → Should place 3 vertices
- Rapid drag-release → Should not trigger multiple events

### Expected Results:
- ✅ No double vertex placements
- ✅ No "ghost" preview lines
- ✅ No stuck drag state (draggedVertexId cleared on release)
- ✅ Clean event logs (no duplicate events)

### Files Involved:
- `UnifiedGestureDetector.kt` - Gesture classification (prevents conflicts)
- `CanvasGestureModifier.kt` - pointerInput keys prevent stale closures (line 66)

---

## Test 10: Tool Mode Switching

**Objective:** Verify switching between DRAW and SELECT modes works cleanly.

### Steps:
1. **DRAW → SELECT:**
   - Place vertex in DRAW mode (press-drag-release in progress)
   - Switch to SELECT mode via FAB
   - Expected: Gesture cancelled cleanly, no stuck state

2. **SELECT → DRAW:**
   - Start dragging vertex in SELECT mode
   - Switch to DRAW mode via FAB
   - Expected: Drag cancelled, vertex stays at last position

3. **Rapid Switching:**
   - Switch DRAW → SELECT → DRAW rapidly
   - Expected: No crashes, no stuck gestures

### Expected Results:
- ✅ No stuck preview lines when switching modes
- ✅ No stuck drag state (draggedVertexId cleared)
- ✅ Correct handler invoked after switch (factory pattern)
- ✅ Console logs show mode changes: `"Tool mode changed: SELECT"`

### Files Involved:
- `CanvasGestureModifier.kt` - pointerInput keys (line 66) trigger recomposition on mode change
- `GeometryManager.kt` - ToolModeChanged event handler

---

## Test 11: Coordinate Conversion Accuracy

**Objective:** Verify screen ↔ world coordinate conversion is accurate.

### Steps:
1. Place vertex at specific screen position (e.g., tap at 100, 100)
2. Zoom in/out
3. Pan canvas
4. Tap the same vertex (hit testing)

### Expected Results:
- ✅ Vertex placed at correct world coordinates (not affected by camera)
- ✅ Hit testing finds vertex after zoom/pan
- ✅ Dragged vertex moves correctly in world space
- ✅ Grid aligns with world coordinates

### Files Involved:
- `CoordinateConversion.kt` - screenToWorld / worldToScreen
- `HitTesting.kt` - Uses screenToWorld for hit testing

---

## Test 12: Edge Cases

**Objective:** Verify edge cases don't cause crashes.

### Test Cases:

**Case A: First vertex placement (no active vertex)**
- Tap canvas in DRAW mode when no vertices exist
- Expected: First vertex placed, becomes active (blue)

**Case B: Delete vertex while dragging**
- Start dragging vertex in SELECT mode
- Delete vertex via radial menu (edge case - shouldn't happen normally)
- Expected: Drag cancelled gracefully, no crash

**Case C: Rapid taps**
- Tap 10 times rapidly in DRAW mode
- Expected: 10 vertices placed, no crashes, no duplicate IDs

**Case D: Extreme zoom**
- Zoom way in (10x)
- Place vertex
- Zoom way out (0.1x)
- Expected: Hit testing still works, coordinates correct

### Expected Results:
- ✅ No crashes on edge cases
- ✅ Graceful handling of invalid states
- ✅ Logging shows warnings for edge cases (not errors)

---

## Success Criteria

### Code Quality:
- ✅ Compilation successful (verified)
- ✅ No ktlint violations
- ✅ No detekt violations
- ✅ 46% code reduction (1141 → 617 lines)

### Architecture Quality:
- ✅ Pure functions (CoordinateConversion, HitTesting) - testable
- ✅ Strategy pattern (ToolGestureHandler) - extensible
- ✅ Unified gesture detector - no conflicts
- ✅ Clean separation: utilities → handlers → events → state

### Functional Quality:
- ✅ All DRAW mode gestures work (press-drag-release, snap hints)
- ✅ All SELECT mode gestures work (tap for menu, drag vertices)
- ✅ Pan/zoom works in both modes
- ✅ No gesture conflicts
- ✅ Tool mode switching works cleanly
- ✅ Coordinate conversion accurate
- ✅ Edge cases handled gracefully

---

## Testing Checklist

Copy this checklist for manual testing:

```
DRAW Mode:
[ ] Test 1: Basic vertex placement (press-drag-release)
[ ] Test 2: Snap hints appear correctly
[ ] Test 3: Tap vs drag distinction works

SELECT Mode:
[ ] Test 4: Vertex tap shows radial menu
[ ] Test 5: Vertex drag moves vertex
[ ] Test 6: Line tap shows radial menu
[ ] Test 7: Empty tap closes menus

Global:
[ ] Test 8: Pan/zoom works in both modes
[ ] Test 9: No gesture conflicts
[ ] Test 10: Tool mode switching works
[ ] Test 11: Coordinate conversion accurate
[ ] Test 12: Edge cases handled

Code Quality:
[ ] ktlint passes
[ ] detekt passes
[ ] No console errors during testing
```

---

## Regression Testing

If any issues found, verify against old implementation:

**Files to compare:**
- Old: DrawingCanvas.kt (1141 lines, 4 pointerInput blocks)
- New: DrawingCanvas.kt (617 lines, 2 modifiers) + utilities + handlers

**Rollback procedure** (if needed):
```bash
git checkout HEAD~1 -- shared/src/commonMain/kotlin/com/roomplanner/ui/components/DrawingCanvas.kt
```

---

## Next Steps After Validation

1. **If all tests pass:**
   - ✅ Mark refactoring complete
   - ✅ Update README with new architecture
   - ✅ Write unit tests for utilities
   - ✅ Write integration tests for handlers

2. **If issues found:**
   - Document specific failing test
   - Identify which file/handler is responsible
   - Fix in isolation (thanks to clean architecture!)
   - Re-test specific scenario

---

**Status:** Ready for manual testing on iOS Simulator
**Last Updated:** 2026-01-11
**Estimated Test Time:** 30-45 minutes for full validation