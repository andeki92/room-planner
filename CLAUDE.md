# Claude Instructions for RoomPlanner

**IMPORTANT: Always use project-local `.claude/plans/` folder for plans, NEVER the global `~/.claude/` folder.**

> **Project:** RoomPlanner - Professional Renovation Planning CAD Tool
> **Stack:** Kotlin 2.2.0+ / Compose Multiplatform 1.8.0+ / iOS focus
> **Architecture:** Reactive Event-Driven, Mode-based UI, BREP Topology
> **Docs:** [spec.md](../spec.md) (source of truth) | [README.md](../README.md)

---

## ⚠️ Critical Rules

### 1. Events = Results, NOT Processes

```kotlin
// ✅ CORRECT: Event for committed action
eventBus.emit(GeometryEvent.PointPlaced(position = snapResult.position))

// ❌ WRONG: Events for intermediate state
eventBus.emit(DragMoved(change.position))  // Floods event bus, causes races
```

**Use local `remember { mutableStateOf() }` for:** hover, drag, preview, snap indicators.
**Use events for:** committed actions that go in feature tree (undo/redo).

### 2. Fresh State in Gesture Handlers

```kotlin
// ✅ Ref pattern: prevents stale state bugs
val drawingStateRef = remember { mutableStateOf(drawingState) }
drawingStateRef.value = drawingState  // Update every recomposition

.pointerInput(Unit) {
    detectTapGestures { offset ->
        val currentState = drawingStateRef.value  // ✅ Fresh at event time
    }
}
```

### 3. Configuration Classes (No Magic Numbers)

```kotlin
// ✅ Centralize in DrawingConfig, SnapSettings, etc.
drawCircle(radius = config.vertexRadius, color = config.vertexColorNormalCompose())

// ❌ Scattered magic numbers
drawCircle(radius = 6f, color = Color.Green)
```

### 4. Logging: Kermit Only

```kotlin
Logger.i { "✓ Feature initialized" }  // ✅
Logger.w { "⚠ Validation failed" }
Logger.e { "✗ Fatal error" }
println("debug")  // ❌ Never use
```

### 5. i18n: Compile-Time Safe

All user-facing text → `Strings` interface → implement in `EnglishStrings` + `NorwegianStrings`.
Missing translation = compile error. See `localization/` folder.

---

## Architecture Patterns

| Pattern | Location | Notes |
|---------|----------|-------|
| Event Bus | `data/events/EventBus.kt` | SharedFlow, filter by type |
| State Management | `StateManager` | StateFlow + immutable data classes |
| Mode System | `AppMode` sealed interface | Type-safe navigation |
| Geometry | `Point2(Double, Double)` | CAD precision, centimeters |
| BREP Topology | `HalfEdge`, `Vertex`, `Face` | See spec.md § 5 |
| Feature Tree | Command pattern | Undo/redo support |
| DI | Koin | `AppModule.kt` |

---

## Compose Coordinates

- Origin (0,0) = **top-left**
- Y increases **downward** (opposite of math)
- For radial positioning: `y = anchor.y - radius * sin(angleRad)` (invert Y)

---

## Code Review Checklist

**Before committing:**

- [ ] Events for results only (not intermediate states)
- [ ] Gesture handlers use ref pattern for fresh state
- [ ] No magic numbers (use config classes)
- [ ] Kermit logging (no println)
- [ ] All strings in `Strings` interface
- [ ] `@Serializable` on data classes
- [ ] Double for geometry (not Float)
- [ ] Follows spec.md patterns

**DO NOT:**
- Run `./gradlew` commands (user/IDE handles builds)
- Fix Android build issues (iOS focus, Android disabled)
- Create `local.properties` with Android SDK

---

## Common Mistakes

1. **Direct state mutation** → Use `stateManager.updateState { state.copy(...) }`
2. **Float for geometry** → Use `Double` for CAD precision
3. **String-based navigation** → Use `sealed interface AppMode`
4. **Simple edge graph** → Use BREP halfedge mesh (rooms = O(1))
5. **Captured stale state** → Use ref pattern in gesture handlers
6. **Events for drag/hover** → Use local Compose state

---

## Project Structure

```
shared/commonMain/kotlin/com/roomplanner/
├── data/          # State, events, models
├── domain/        # Geometry, constraints, snapping
├── ui/            # Screens, components
└── localization/  # i18n (Strings interface)
```

Plans: `.claude/plans/` (project-local, version controlled)

---

## Reference

- [spec.md](../spec.md) - Complete technical specification
- [KMP Docs](https://kotlinlang.org/docs/multiplatform.html)
- [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)
- [Kermit](https://github.com/touchlab/Kermit) | [Koin](https://insert-koin.io/)

---

**Last Updated:** 2026-01-11 | Kotlin 2.2.0+ | Compose 1.8.0+ | iOS focus
