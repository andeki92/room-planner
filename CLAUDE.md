# Claude Assistant Instructions for OpenTile Mobile

> **Project:** OpenTile Mobile - Professional Renovation Planning CAD Tool for iOS/Android
> **Architecture:** Reactive Event-Driven using Kotlin Multiplatform + Compose Multiplatform
> **Kotlin Version:** 2.2.0+
> **Compose Multiplatform:** 1.8.0+ (iOS Stable)
> **Philosophy:** Touch-first CAD, Mode-based UI, Parametric History, BREP Topology
> **Target Users:** Professional contractors (speed + precision over aesthetics)

---

## 📋 Quick Reference

### Authoritative Documents
1. **[spec-mobile.md](../spec-mobile.md)** - Complete technical specification (READ THIS FIRST)
2. **[README.md](../README.md)** - Project overview and status
3. **[Implementation Roadmap](../spec-mobile.md#8-implementation-roadmap)** - Phase-by-phase plan

### Key Architecture Patterns
- **Event-Driven:** Tools emit events via Kotlin Flows → Systems react (never mutate directly)
- **Mode-Based:** Sealed classes for AppMode (FloorPlan, MaterialPlanning, Utilities, etc.)
- **Feature Tree:** Parametric history (like OnShape) - rollback, edit, recalculate
- **BREP Topology:** Halfedge mesh (not simple edge graph)
- **Immutable State:** Data classes with structural sharing

---

## 🎯 Core Development Principles

### 1. **ALWAYS Follow spec-mobile.md**
The spec is the source of truth. Before implementing any feature:
1. Read the relevant section in spec-mobile.md
2. Follow the architectural patterns defined there
3. If pattern unclear, ask user before deviating

### 2. **Event-Driven Architecture (CRITICAL)**

**❌ WRONG: Direct state mutation**
```kotlin
fun onTap(position: Offset, state: MutableState<AppState>) {
    // WRONG: Directly mutating state
    state.value = state.value.copy(
        vertices = state.value.vertices + (id to Vertex(position))
    )
}
```

**✅ CORRECT: Event-driven with Flows**
```kotlin
// Tool emits events
class WallToolViewModel(private val eventBus: EventBus) {
    fun onTap(position: Offset) {
        viewModelScope.launch {
            // Emit event
            eventBus.emit(GeometryEvent.PointPlaced(
                position = position.toPoint2(),
                snappedTo = null
            ))
        }
    }
}

// Separate system listens and mutates
class GeometryManager(
    private val eventBus: EventBus,
    private val stateManager: StateManager
) {
    init {
        eventBus.events
            .filterIsInstance<GeometryEvent>()
            .onEach { event ->
                when (event) {
                    is GeometryEvent.PointPlaced -> {
                        val vertex = Vertex(position = event.position)
                        stateManager.updateState { state ->
                            state.withVertex(vertex)
                        }
                    }
                }
            }
            .launchIn(CoroutineScope(Dispatchers.Default))
    }
}
```

**Why:** Decoupling, testability, undo/redo support, feature tree integration

### 3. **Mode-Based Systems (Sealed Classes + Compose Navigation)**

Use sealed classes for type-safe mode management:

```kotlin
// ✅ CORRECT: Sealed class for modes
sealed interface AppMode {
    data object ProjectBrowser : AppMode
    data object FloorPlan : AppMode
    data object MaterialPlanning : AppMode
    data object Utilities : AppMode
    data object Estimation : AppMode
    data object Export : AppMode
}

// Compose navigation with mode
@Composable
fun AppNavigation() {
    var currentMode by remember { mutableStateOf<AppMode>(AppMode.ProjectBrowser) }

    AnimatedContent(
        targetState = currentMode,
        transitionSpec = {
            fadeIn(tween(300)) togetherWith fadeOut(tween(300))
        }
    ) { mode ->
        when (mode) {
            AppMode.FloorPlan -> FloorPlanScreen(
                onNavigate = { currentMode = it }
            )
            AppMode.MaterialPlanning -> MaterialPlanningScreen(
                onNavigate = { currentMode = it }
            )
            // ...
        }
    }
}

// ❌ WRONG: String-based navigation
fun navigateTo(mode: String) { /* Error-prone! */ }
```

### 4. **Feature Tree Integration**

Every user operation must go through Command pattern:

```kotlin
interface Command {
    suspend fun execute(state: AppState): Result<AppState>
    suspend fun undo(state: AppState): Result<AppState>
    val name: String
}

// Add to feature tree
class FeatureTree {
    suspend fun addFeature(feature: Feature) {
        features.add(feature)
        feature.command.execute(stateManager.state.value)
    }
}
```

### 5. **Component Design (Immutable Data Classes)**

**Topology components (BREP):**
```kotlin
@Serializable
data class Vertex(
    val id: String = UUID.randomUUID().toString(),
    val position: Point2,           // Double for CAD precision
    val fixed: Boolean = false,
    val outgoingHalfedge: String? = null
)

@Serializable
data class HalfEdge(
    val id: String = UUID.randomUUID().toString(),
    val vertex: String,      // Points TO this vertex
    val next: String,        // Next halfedge in loop
    val prev: String,        // Previous halfedge
    val twin: String,        // Opposite halfedge
    val face: String?        // Left face (null = exterior)
)
```

**Building components:**
```kotlin
@Serializable
data class Wall(
    val id: String = UUID.randomUUID().toString(),
    val edge: String,        // References Edge (not HalfEdge directly)
    val thickness: Float,
    val height: Float,
    val material: WallMaterial
)
```

### 6. **Precision & Units**

- Use **Double** for geometry (not Float) - CAD precision required
- Internal units: **centimeters** (consistent throughout)
- Display units: configurable (imperial/metric)

```kotlin
// shared/commonMain/kotlin/com/opentile/domain/geometry/primitives.kt
@Serializable
data class Point2(
    val x: Double,  // Double for precision
    val y: Double
)
```

### 7. **Testing Requirements**

Every module needs:
- **Unit tests** for pure functions (geometry operations) in `commonTest`
- **Integration tests** for event flows
- **UI tests** for Compose screens (androidInstrumentedTest / iosTest)

```kotlin
// shared/commonTest/kotlin/com/opentile/domain/geometry/PolygonTest.kt
class PolygonTest {
    @Test
    fun `polygon area calculation`() {
        val square = Polygon(listOf(
            Point2(0.0, 0.0),
            Point2(10.0, 0.0),
            Point2(10.0, 10.0),
            Point2(0.0, 10.0)
        ))

        assertEquals(100.0, square.area(), 0.001)
    }
}
```

### 8. **Logging Requirements (CRITICAL)**

**ALWAYS use Kermit for multiplatform logging:**

```kotlin
import co.touchlab.kermit.Logger

// ✅ CORRECT
Logger.i { "✓ Phase 0.1: AppMode state system initialized" }
Logger.w { "⚠ Transition denied: $reason" }
Logger.d { "Panel entity spawned: $entity" }
Logger.e { "✗ Fatal error: Failed to load project" }

// ❌ WRONG
println("Mode changed")        // Don't use
System.out.println("Error")    // Don't use
```

**Log levels:**
- `Logger.i` - Standard operational messages (mode changes, feature creation, system initialization)
- `Logger.w` - Warnings, validation failures, non-fatal errors
- `Logger.d` - Verbose debugging info (can be filtered)
- `Logger.e` - Fatal errors, exceptions, critical failures

**Formatting conventions:**
- Use `✓` prefix for success: `Logger.i { "✓ Feature initialized" }`
- Use `⚠` prefix for warnings: `Logger.w { "⚠ Code violation detected" }`
- Use `✗` prefix for errors: `Logger.e { "✗ Failed to load: $path" }`
- Use `→` for transitions: `Logger.i { "Mode: $from → $to" }`
- Use `━━━` for section dividers in multi-line logs

**Example startup logging:**
```kotlin
fun initApp() {
    Logger.i { "✓ Phase 0.1: AppMode state system initialized" }
    Logger.i { "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" }
    Logger.i { "  Mode Switching: Swipe gestures" }
    Logger.i { "  Default mode: Project Browser" }
    Logger.i { "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" }
}
```

---

## 🔧 Technology Stack & Versions

### Required Versions
- **Kotlin:** 2.2.0+ (latest stable)
- **Compose Multiplatform:** 1.8.0+ (iOS stable)
- **Gradle:** 8.7.3+
- **Android Target SDK:** 35
- **iOS Deployment Target:** 17.2+ (Compose Multiplatform requirement)

### build.gradle.kts Setup

**CRITICAL: Module structure for KMP**

```kotlin
// shared/build.gradle.kts

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlinx.serialization)
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            // Compose Multiplatform
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)

            // Kotlin coroutines
            implementation(libs.kotlinx.coroutines.core)

            // Serialization
            implementation(libs.kotlinx.serialization.json)

            // Logging
            implementation(libs.kermit)

            // DI
            implementation(libs.koin.core)
        }

        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.kotlinx.coroutines.android)
        }

        iosMain.dependencies {
            // iOS-specific
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

android {
    namespace = "com.opentile.mobile"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
```

### Compose Multiplatform 1.8.0+ Features We Use

#### 1. **Skia Rendering (Built-in)**
Hardware-accelerated via Metal (iOS) / OpenGL (Android):

```kotlin
@Composable
fun DrawingCanvas(state: AppState) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        // Skia-backed drawing - hardware accelerated
        drawLine(
            color = Color.Black,
            start = Offset(0f, 0f),
            end = Offset(100f, 100f),
            strokeWidth = 2f
        )
    }
}
```

#### 2. **DrawWithCache for Performance**
Avoid reallocating objects:

```kotlin
Canvas(modifier = Modifier.fillMaxSize()) {
    drawWithCache {
        val path = Path().apply {
            // Build complex path once
            moveTo(0f, 0f)
            lineTo(100f, 0f)
            // ...
        }

        onDrawBehind {
            drawPath(path, Color.Black)
        }
    }
}
```

#### 3. **Kotlin Flows for State**
Reactive state management:

```kotlin
class StateManager {
    private val _state = MutableStateFlow(AppState.initial())
    val state: StateFlow<AppState> = _state.asStateFlow()

    fun updateState(reducer: (AppState) -> AppState) {
        _state.update(reducer)
    }
}

// Compose integration
@Composable
fun FloorPlanScreen(stateManager: StateManager) {
    val state by stateManager.state.collectAsState()

    DrawingCanvas(state = state)
}
```

#### 4. **Gesture Detection (Touch-Optimized)**
Multi-touch gestures:

```kotlin
Canvas(
    modifier = Modifier
        .fillMaxSize()
        .pointerInput(Unit) {
            detectTapGestures(
                onTap = { offset -> /* Single tap */ },
                onLongPress = { offset -> /* Long press */ }
            )
        }
        .pointerInput(Unit) {
            detectTransformGestures { _, pan, zoom, _ ->
                // Two-finger pan/zoom
                cameraTransform = cameraTransform.copy(
                    panX = cameraTransform.panX + pan.x,
                    panY = cameraTransform.panY + pan.y,
                    zoom = (cameraTransform.zoom * zoom).coerceIn(0.1f, 10f)
                )
            }
        }
) {
    // Drawing...
}
```

#### 5. **AnimatedContent for Mode Transitions**
Smooth transitions between modes:

```kotlin
AnimatedContent(
    targetState = currentMode,
    transitionSpec = {
        fadeIn(animationSpec = tween(300)) togetherWith
            fadeOut(animationSpec = tween(300))
    }
) { mode ->
    when (mode) {
        AppMode.FloorPlan -> FloorPlanScreen()
        AppMode.MaterialPlanning -> MaterialPlanningScreen()
    }
}
```

#### 6. **expect/actual for Platform-Specific Code**
Platform abstractions:

```kotlin
// commonMain
expect class ApplePencilHandler {
    fun onStylusEvent(event: StylusEvent)
}

// iosMain
actual class ApplePencilHandler {
    actual fun onStylusEvent(event: StylusEvent) {
        // Use UIKit APIs for Apple Pencil
    }
}

// androidMain
actual class ApplePencilHandler {
    actual fun onStylusEvent(event: StylusEvent) {
        // Use Android APIs for S Pen
    }
}
```

### KMP-Specific Patterns

**Shared Event Bus (Kotlin Flows):**
```kotlin
// shared/commonMain/kotlin/com/opentile/data/events/EventBus.kt

class EventBus {
    private val _events = MutableSharedFlow<AppEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<AppEvent> = _events.asSharedFlow()

    suspend fun emit(event: AppEvent) = _events.emit(event)
}

// Usage
eventBus.events
    .filterIsInstance<GeometryEvent>()
    .onEach { handleEvent(it) }
    .launchIn(scope)
```

**Immutable State with Data Classes:**
```kotlin
@Serializable
data class AppState(
    val project: Project,
    val mode: AppMode,
    val vertices: Map<String, Vertex> = emptyMap(),
    val edges: Map<String, Edge> = emptyMap(),
    // ...
) {
    fun withVertex(vertex: Vertex) = copy(
        vertices = vertices + (vertex.id to vertex)
    )

    fun withEdge(edge: Edge) = copy(
        edges = edges + (edge.id to edge)
    )
}
```

**Multiplatform UUID Generation:**
```kotlin
// shared/commonMain/kotlin/com/opentile/common/UUID.kt

expect object UUIDGenerator {
    fun generate(): String
}

// iosMain
actual object UUIDGenerator {
    actual fun generate(): String {
        return NSUUID().UUIDString()
    }
}

// androidMain
actual object UUIDGenerator {
    actual fun generate(): String {
        return java.util.UUID.randomUUID().toString()
    }
}
```

---

## 📁 Project Structure

```
opentile-mobile/
├── shared/                      # Kotlin Multiplatform shared code
│   ├── commonMain/
│   │   ├── kotlin/
│   │   │   └── com/opentile/
│   │   │       ├── domain/      # Business logic (platform-agnostic)
│   │   │       │   ├── geometry/    # Pure geometry (Point2, Polygon, etc.)
│   │   │       │   ├── topology/    # Halfedge mesh
│   │   │       │   ├── building/    # Wall, Door, Room entities
│   │   │       │   ├── materials/   # Tile, flooring entities
│   │   │       │   └── constraints/ # Constraint solver
│   │   │       ├── data/        # State management
│   │   │       │   ├── models/      # Immutable data classes
│   │   │       │   ├── events/      # Event definitions
│   │   │       │   └── commands/    # Command pattern implementations
│   │   │       ├── presentation/ # ViewModels
│   │   │       │   ├── drawing/
│   │   │       │   ├── materials/
│   │   │       │   └── estimation/
│   │   │       └── ui/          # Compose Multiplatform UI
│   │   │           ├── screens/
│   │   │           ├── components/
│   │   │           └── theme/
│   │   └── resources/           # Shared resources
│   ├── iosMain/                 # iOS-specific
│   │   └── kotlin/
│   │       └── com/opentile/platform/
│   ├── androidMain/             # Android-specific
│   │   └── kotlin/
│   │       └── com/opentile/platform/
│   └── commonTest/              # Shared tests
│       └── kotlin/
├── androidApp/                  # Android application
│   └── src/main/
│       └── kotlin/
│           └── com/opentile/android/
├── iosApp/                      # iOS application (Xcode project)
│   └── iosApp/
│       ├── ContentView.swift
│       └── iOSApp.swift
├── build.gradle.kts             # Root build config
├── gradle/
│   └── libs.versions.toml       # Version catalog
└── settings.gradle.kts
```

### Module Dependencies (Allowed)
- **commonMain** depends on nothing (pure Kotlin + KMP stdlib)
- **iosMain** depends on commonMain + iOS platform APIs
- **androidMain** depends on commonMain + Android platform APIs
- **androidApp** depends on shared (androidMain)
- **iosApp** depends on shared framework (iosMain)

---

## 🚦 Code Review Checklist

Before marking implementation complete, verify:

**Tooling & Setup:**
- [ ] **mise.toml:** Tool versions defined (Java 17, Gradle 8.10.2, Node 22) ⚠️ CRITICAL
- [ ] **lefthook.yml:** Git hooks configured (ktlint, detekt, tests) ⚠️ CRITICAL
- [ ] **libs.versions.toml:** All dependencies in version catalog ⚠️ CRITICAL
- [ ] **.editorconfig:** Code style rules defined ⚠️ CRITICAL
- [ ] **mise install:** All tools installed via mise (NOT global installs)
- [ ] **lefthook install:** Git hooks initialized

**Architecture & Code:**
- [ ] **Kotlin 2.2.0+:** Using latest stable
- [ ] **Compose Multiplatform 1.8.0+:** iOS stable version
- [ ] **Events not direct mutations:** Tools emit events, systems mutate
- [ ] **Immutable state:** All state classes are data classes with `copy()`
- [ ] **Sealed classes for modes:** Use sealed interface for AppMode
- [ ] **Command pattern:** Operations reversible (undo/redo)
- [ ] **Feature tree integration:** Operations logged as features
- [ ] **Double precision:** Geometry uses Double, not Float
- [ ] **Flows for reactivity:** Use StateFlow/SharedFlow, not LiveData
- [ ] **Kermit logging:** ALWAYS use Logger.i/w/d/e (NOT println()) ⚠️ CRITICAL
- [ ] **Plans:** Use local `.claude/plans/` folder (NOT global) ⚠️ CRITICAL
- [ ] **Tests included:** Unit tests (commonTest), UI tests (platform-specific)
- [ ] **Follows spec-mobile.md:** Implementation matches specification
- [ ] **expect/actual for platform code:** Use for iOS/Android differences
- [ ] **Serializable data classes:** Use @Serializable for all state models
- [ ] **No TODOs in production code:** Resolve or document as issues

**Code Quality:**
- [ ] **ktlint:** Passes `./gradlew ktlintCheck` ⚠️ CRITICAL
- [ ] **detekt:** Passes `./gradlew detekt` ⚠️ CRITICAL
- [ ] **Tests:** All tests pass `./gradlew test`
- [ ] **Build:** Project builds `./gradlew assembleDebug`

---

## 🐛 Common Mistakes to Avoid

### ❌ Mistake 1: Not Using Flows for Events
**Wrong:**
```kotlin
class GeometryManager {
    fun handleTap(position: Offset) {
        // Directly mutating state
        state.value = state.value.withVertex(Vertex(position))
    }
}
```
**Right:**
```kotlin
class GeometryManager(private val eventBus: EventBus) {
    init {
        eventBus.events
            .filterIsInstance<GeometryEvent>()
            .onEach { handleEvent(it) }
            .launchIn(scope)
    }
}
```

### ❌ Mistake 2: Direct State Mutation
**Wrong:**
```kotlin
fun addVertex(vertex: Vertex) {
    state.vertices[vertex.id] = vertex  // Mutable!
}
```
**Right:**
```kotlin
fun addVertex(vertex: Vertex) {
    stateManager.updateState { state ->
        state.copy(vertices = state.vertices + (vertex.id to vertex))
    }
}
```

### ❌ Mistake 3: Float for Geometry
**Wrong:**
```kotlin
data class Point2(
    val x: Float,  // Not precise enough for CAD
    val y: Float
)
```
**Right:**
```kotlin
data class Point2(
    val x: Double,  // CAD precision
    val y: Double
)
```

### ❌ Mistake 4: String-Based Mode Navigation
**Wrong:**
```kotlin
fun navigateTo(mode: String) {
    when (mode) {
        "FloorPlan" -> { /* ... */ }  // Error-prone!
    }
}
```
**Right:**
```kotlin
sealed interface AppMode {
    data object FloorPlan : AppMode
}

fun navigateTo(mode: AppMode) {
    when (mode) {
        AppMode.FloorPlan -> { /* ... */ }  // Type-safe!
    }
}
```

### ❌ Mistake 5: Simple Edge Graph Instead of BREP
**Wrong:**
```kotlin
// Using simple vertex-edge graph
data class Edge(
    val start: String,
    val end: String
)
// Finding rooms requires expensive DFS every frame
```
**Right:**
```kotlin
// Using halfedge mesh (BREP)
data class HalfEdge(
    val vertex: String,
    val next: String,
    val twin: String,
    val face: String?
)
// Rooms found by traversing next pointers (O(1) per face)
```

### ❌ Mistake 6: Not Using Spatial Indexing
**Wrong:**
```kotlin
// Linear search for snapping
vertices.values.forEach { vertex ->
    if (vertex.position.distanceTo(cursor) < 10.0) {
        // Found snap target
    }
}
// O(n) every frame
```
**Right:**
```kotlin
// Use R-tree spatial index
val nearby = spatialIndex.locateWithinDistance(cursor, 10.0)
// O(log n)
```

### ❌ Mistake 7: Not Using @Serializable
**Wrong:**
```kotlin
data class Vertex(
    val id: String,
    val position: Point2
)
// Can't serialize for persistence!
```
**Right:**
```kotlin
@Serializable
data class Vertex(
    val id: String,
    val position: Point2
)
// Can save/load from storage
```

---

## 🎨 UI Conventions (Compose Multiplatform)

### Touch Gesture Mapping

**Floor Plan Drawing Mode:**

| Gesture | Action | Implementation |
|---------|--------|----------------|
| Single tap | Place vertex | `detectTapGestures { onTap -> }` |
| Long press | Context menu | `detectTapGestures { onLongPress -> }` |
| Two-finger pan | Pan canvas | `detectTransformGestures` |
| Pinch | Zoom in/out | `detectTransformGestures` |
| Three-finger swipe | Undo/Redo | Platform-specific gesture |

**Implementation Example:**
```kotlin
@Composable
fun DrawingCanvas() {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset -> onTap(offset) },
                    onLongPress = { offset -> showContextMenu(offset) }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    updateCamera(pan, zoom)
                }
            }
    ) {
        // Drawing...
    }
}
```

### Color Scheme (Material 3)
```kotlin
val LightColorScheme = lightColorScheme(
    primary = Color(0xFF2563EB),    // Blue
    onPrimary = Color.White,
    secondary = Color(0xFF64748B),  // Slate
    error = Color(0xFFEF4444),      // Red
    background = Color(0xFFF8FAFC),
    surface = Color.White
)

val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF3B82F6),
    onPrimary = Color.White,
    secondary = Color(0xFF94A3B8),
    error = Color(0xFFF87171),
    background = Color(0xFF0F172A),
    surface = Color(0xFF1E293B)
)
```

---

## 📝 Commit Message Convention

```
<type>(<scope>): <subject>

<body>
```

**Types:**
- `feat`: New feature
- `fix`: Bug fix
- `refactor`: Code refactoring
- `test`: Adding tests
- `docs`: Documentation
- `chore`: Maintenance

**Scopes:**
- `drawing`, `materials`, `constraints`, `ui`, `export`, `ios`, `android`, `common`, etc.

**Example:**
```
feat(drawing): implement touch-optimized smart snapping

- Added perpendicular snap detection in SmartSnapSystem
- Projects cursor onto nearby edges
- Shows snap indicator when within 2° of perpendicular
- Uses Compose Multiplatform Canvas for visual feedback
- Refs spec-mobile.md § 6.1 Smart Snapping System
```

---

## 🔄 Workflow for New Features

### Before Starting Development

1. **Verify mise setup:**
   ```bash
   mise install  # Install all tools from .mise.toml
   mise list     # Verify versions (Java 17, Gradle 8.10.2)
   ```

2. **Verify lefthook setup:**
   ```bash
   lefthook install       # Initialize Git hooks
   lefthook run pre-commit  # Test hooks
   ```

### Development Workflow

1. **Check `.claude/plans/`** for detailed implementation plans (local to project)
2. **Read spec-mobile.md section** for the feature (§ 8 for scaffolding)
3. **Create feature branch:**
   ```bash
   git checkout -b feat/your-feature-name
   ```

4. **Create event types** (sealed classes) if needed
5. **Implement pure logic** in domain modules (testable in commonTest)
6. **Wrap in event listeners** (reactive, Flow-based)
7. **Implement UI** in Compose Multiplatform
8. **Add to feature tree** (Command pattern)
9. **Write tests** (unit in commonTest, UI in androidInstrumentedTest/iosTest)

10. **Run quality checks:**
    ```bash
    mise run lint    # ktlint + detekt
    mise run test    # All tests
    mise run format  # Auto-format (if needed)
    ```

11. **Commit changes:**
    ```bash
    git add .
    git commit -m "feat(scope): your change"
    # lefthook automatically runs pre-commit checks
    ```

12. **Push to remote:**
    ```bash
    git push origin feat/your-feature-name
    # lefthook automatically runs pre-push checks (full tests + build)
    ```

13. **Test on both platforms** (iOS Simulator + Android Emulator)
14. **Document** in code comments (not separate docs)
15. **Create PR** (CI runs additional checks)

### Emergency Bypass (Use Sparingly!)

If you need to bypass hooks in emergency:
```bash
LEFTHOOK=0 git commit -m "emergency: critical fix"
# Document in commit message WHY hooks were skipped
```

---

## 📋 Implementation Plans (CRITICAL)

**ALWAYS use the local `.claude/plans/` folder for implementation plans:**

**Location:** `.claude/plans/` (project-specific, NOT global `~/.claude/plans/`)

**Plan structure:**
- `phase-X-overview.md` - Overview for major phase (e.g., `phase-0-overview.md`)
- `phase-X-Y-name.md` - Individual sub-phase plans (e.g., `phase-0-1-kmp-setup.md`)

**Each plan includes:**
- **Context:** Why this phase exists, references to spec-mobile.md
- **Implementation Steps:** Detailed code changes with complete examples
- **File Structure:** What files change, what's added/removed
- **Testing & Validation:** Manual test procedures with expected output
- **Success Criteria:** Checklist for completion
- **Next Phase:** What comes after

**Using plans:**
1. Start with the overview: `phase-X-overview.md`
2. Execute sub-phases sequentially: `phase-X-1-*.md`, `phase-X-2-*.md`, etc.
3. Test after each sub-phase (manual validation + automated tests)
4. Verify success criteria before moving to next phase
5. Commit when phase complete

**Why local plans?**
- Project-specific implementation details
- Version controlled with codebase
- Team collaboration (everyone sees same plans)
- No dependency on global Claude config

**Example workflow:**
```bash
# 1. Read the plan
cat .claude/plans/phase-0-1-kmp-setup.md

# 2. Implement changes as specified

# 3. Test on Android
./gradlew :androidApp:installDebug

# 4. Test on iOS
xcodebuild -workspace iosApp/iosApp.xcworkspace -scheme iosApp -sdk iphonesimulator

# 5. Verify success criteria
# (Check console output matches expected)

# 6. Commit
git add .
git commit -m "feat(phase-0.1): setup KMP project structure"

# 7. Move to next phase
cat .claude/plans/phase-0-2-mode-system.md
```

---

## 🆘 When in Doubt

1. **Check spec-mobile.md** first
2. **Leverage KMP features** (expect/actual, sealed classes, Flows)
3. **Ask user** if spec unclear
4. **Follow KMP conventions** for multiplatform code
5. **Prioritize contractor needs** (speed, precision) over polish
6. **Test on both platforms** (iOS + Android)

---

## 📚 Reference Links

**Project Docs:**
- [spec-mobile.md](../spec-mobile.md) - Complete technical specification
- [README.md](../README.md) - Project overview

**Kotlin Multiplatform Docs:**
- [KMP Documentation](https://kotlinlang.org/docs/multiplatform.html)
- [Kotlin 2.2.0 Release](https://blog.jetbrains.com/kotlin/2025/06/kotlin-2-2-0-released/)
- [Google KMP Support](https://android-developers.googleblog.com/2025/05/android-kotlin-multiplatform-google-io-kotlinconf-2025.html)

**Compose Multiplatform Docs:**
- [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)
- [Compose Multiplatform 1.8.0](https://blog.jetbrains.com/kotlin/2025/05/compose-multiplatform-1-8-0-released-compose-multiplatform-for-ios-is-stable-and-production-ready/)
- [Compose for iOS Tutorial](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-multiplatform-create-first-app.html)

**External Docs:**
- [Kermit Logging](https://github.com/touchlab/Kermit)
- [Koin DI](https://insert-koin.io/docs/reference/koin-mp/kmp/)
- [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization)

**Research References:**
- [OnShape CAD](https://cad.onshape.com/help/Content/ui-basics.htm)
- [Event-Driven CAD](https://novedge.com/blogs/design-news/deterministic-event-sourced-architecture-for-real-time-collaborative-cad)
- [BREP Topology](https://en.wikipedia.org/wiki/Boundary_representation)

---

**Last Updated:** 2026-01-03
**Kotlin Version:** 2.2.0+
**Compose Multiplatform:** 1.8.0+
**Platform:** iOS + Android (shared codebase)
