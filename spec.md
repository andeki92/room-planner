# RoomPlanner - Complete Technical Specification

> **Project:** RoomPlanner - Professional Renovation Planning CAD Tool for iOS/Android
> **Architecture:** Reactive Event-Driven Architecture using Kotlin Multiplatform + Compose Multiplatform
> **Kotlin Version:** 2.2.0+
> **Compose Multiplatform:** 1.8.0+ (iOS Stable)
> **Philosophy:** Touch-first CAD, Mode-based UI, Parametric History, BREP Topology
> **Target Users:** Professional contractors (primary), Advanced DIYers (secondary)
> **Platform:** iOS + Android (shared codebase), future Web via Compose for Web

**Document Version:** 1.0
**Last Updated:** 2026-01-03
**Status:** Authoritative Technical Specification for Mobile

---

## Table of Contents

1. [Vision & Goals](#1-vision--goals)
2. [Architecture Overview](#2-architecture-overview)
3. [Application Modes](#3-application-modes)
4. [Domain Model](#4-domain-model)
5. [Core Systems](#5-core-systems)
6. [Feature Implementation Details](#6-feature-implementation-details)
7. [Technology Stack](#7-technology-stack)
8. [Project Scaffolding & Setup](#8-project-scaffolding--setup)
9. [Implementation Roadmap](#9-implementation-roadmap)
10. [Performance & Testing](#10-performance--testing)
11. [References](#11-references)

---

## 1. Vision & Goals

### 1.1 Project Vision

RoomPlanner Mobile brings **professional CAD renovation planning** to iOS and Android through Kotlin Multiplatform, solving real contractor pain points on-site:

**Primary Value Proposition:**
- **On-Site Drawing:** Measure and draw rooms directly at job site (tablet + phone support)
- **Tile Layout Optimization:** Minimize material waste (10-20% savings) through intelligent starting position calculation
- **Accurate Material Estimates:** Generate precise shopping lists to eliminate over/under-ordering
- **Instant Quotes:** Complete professional quotes in 15 minutes on-site
- **Building Code Compliance:** Automatic validation (clearances, minimums, accessibility)

**Mobile-Specific Advantages:**
- **Apple Pencil Support (iOS):** Natural drawing experience for contractors
- **Multi-Touch Gestures:** Pinch-zoom, pan, rotate with fluid response
- **Offline-First:** Work without internet, sync later
- **Camera Integration:** Photo documentation, AR measurement (future)
- **Always Available:** Phone/tablet always in pocket/truck

**Design Principles:**
1. **Touch-First CAD:** Optimized for fingers and stylus (not mouse/keyboard)
2. **Reactive & Immediate:** Changes update instantly (no waiting for recalculation)
3. **Parametric:** Edit past operations, everything downstream recalculates automatically
4. **Contractor-Focused:** Speed and precision over aesthetic polish
5. **Cross-Platform:** Single codebase for iOS + Android

### 1.2 Success Metrics

**Technical:**
- 60 FPS with 1000+ entities on mid-range devices
- < 1 second tile layout optimization
- < 50ms mode transitions
- < 16ms touch latency (imperceptible lag)
- Material estimates within 5% of actual usage

**Business:**
- Time to quote: < 15 minutes for standard bathroom (on-site)
- Waste reduction: 10-15% savings vs manual layout
- Contractor adoption: Would pay subscription
- Accuracy: 90%+ building code violations caught
- Cross-platform: 100% feature parity iOS/Android

---

## 2. Architecture Overview

### 2.1 Core Architectural Patterns

RoomPlanner translates the desktop's Bevy ECS architecture to Kotlin Multiplatform using industry-proven patterns:

#### 1. **Event-Driven Architecture (Kotlin Flows)**

**From Desktop (Bevy Events):**
```rust
// Desktop: Bevy Event System
events.send(GeometryEvent::PointPlaced { position, snapped_to });
```

**To Mobile (Kotlin Flows):**
```kotlin
// Mobile: Kotlin Flow Event Bus
sealed interface GeometryEvent {
    data class PointPlaced(
        val position: Offset,
        val snappedTo: String?
    ) : GeometryEvent
}

class EventBus {
    private val _events = MutableSharedFlow<GeometryEvent>()
    val events: SharedFlow<GeometryEvent> = _events.asSharedFlow()

    suspend fun emit(event: GeometryEvent) = _events.emit(event)
}
```

**Implementation Pattern:**
```
User Touch/Gesture
    ↓
Tool emits Event (PointPlaced, EdgeCreated, etc.)
    ↓
Event Bus (Kotlin SharedFlow)
    ↓
Multiple Listeners React:
    ├─ GeometryManager (updates state)
    ├─ TopologyBuilder (maintains halfedge mesh)
    ├─ ConstraintSolver (re-solves affected constraints)
    ├─ FeatureTree (logs operation for history)
    └─ UI Layer (Compose recomposition)
```

**Benefits:**
- Decoupled components (tools don't mutate directly)
- Easy to add features (just add new event listeners)
- Natural undo/redo (replay events)
- Testable (emit events, verify responses)
- Works great with Kotlin coroutines

#### 2. **Mode-Based State Management (Sealed Classes + State Pattern)**

**From Desktop (Bevy States):**
```rust
#[derive(States)]
pub enum AppMode {
    FloorPlan,
    MaterialPlanning,
    // ...
}
```

**To Mobile (Kotlin Sealed Classes):**
```kotlin
sealed interface AppMode {
    data object ProjectBrowser : AppMode
    data object FloorPlan : AppMode
    data object MaterialPlanning : AppMode
    data object Utilities : AppMode
    data object Estimation : AppMode
    data object Export : AppMode
}

// Compose Navigation with Mode
@Composable
fun AppNavigation(mode: AppMode) {
    when (mode) {
        AppMode.FloorPlan -> FloorPlanScreen()
        AppMode.MaterialPlanning -> MaterialPlanningScreen()
        // ...
    }
}
```

**Benefits:**
- Performance (tile optimizer doesn't run while drawing)
- Clarity (obvious which code affects which mode)
- Modularity (easy to add modes)
- UI organization (mode-specific screens)
- Type-safe navigation

#### 3. **Feature Tree / Parametric History (Command Pattern)**

**From Desktop (Bevy Command Pattern):**
```rust
pub trait Command {
    fn execute(&mut self, world: &mut World) -> Result<(), String>;
    fn undo(&mut self, world: &mut World) -> Result<(), String>;
}
```

**To Mobile (Kotlin Command Pattern):**
```kotlin
interface Command {
    suspend fun execute(state: AppState): Result<AppState>
    suspend fun undo(state: AppState): Result<AppState>
    val name: String
    val timestamp: Instant
}

data class Feature(
    val id: String,
    val name: String,
    val command: Command,
    val suppressed: Boolean = false,
    val status: FeatureStatus
)

class FeatureTree {
    private val features = mutableListOf<Feature>()
    private var rollbackPosition: Int? = null

    suspend fun addFeature(feature: Feature) {
        features.add(feature)
        feature.command.execute(currentState)
    }

    suspend fun rollbackTo(position: Int) {
        rollbackPosition = position
        rebuildFromFeatures()
    }
}
```

**Concept:**
- Every operation is a "Feature" (Draw Wall, Add Door, Tile Layout, Place Outlet)
- Features stored in order in Feature Tree
- User can **rollback** to any point, **edit** past features (everything after recalculates)
- Features can be **suppressed** (hide) or **grouped** in folders

#### 4. **Boundary Representation (BREP) Topology**

**Same as Desktop - Halfedge Mesh:**
```kotlin
// Shared in commonMain (same across iOS/Android)
data class Vertex(
    val id: String,
    val position: Offset,  // Double precision internally
    val fixed: Boolean = false,
    val outgoingHalfedge: String? = null
)

data class HalfEdge(
    val id: String,
    val vertex: String,      // Points TO this vertex
    val next: String,        // Next halfedge in loop
    val prev: String,        // Previous halfedge
    val twin: String,        // Opposite halfedge
    val face: String?        // Face on left (null = exterior)
)

data class Face(
    val id: String,
    val halfedge: String,    // Any halfedge on boundary
    val boundary: Polygon    // Cached polygon (for fast queries)
)
```

**Benefits:**
- Stable room identities (Face IDs persist)
- Fast boundary traversal (follow next pointers)
- Local operations (splitting edge only updates adjacent halfedges)
- Professional CAD standard
- Shared code between iOS/Android

### 2.2 System Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                     MOBILE APPLICATION                          │
│                                                                 │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │  Compose Multiplatform UI Layer                           │ │
│  │  (iOS + Android - declarative UI)                          │ │
│  └────────────────────────────────────────────────────────────┘ │
│                              ↓                                   │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │  Navigation + Mode Management (AppMode sealed class)      │ │
│  └────────────────────────────────────────────────────────────┘ │
│                              ↓                                   │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │  Tool Layer (Wall Tool, Tile Tool, etc.)                  │ │
│  │  → Emits Events via Flow (not direct mutation)            │ │
│  └────────────────────────────────────────────────────────────┘ │
│                              ↓                                   │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │  Event Bus (Kotlin SharedFlow)                            │ │
│  │  - GeometryEvent, MaterialEvent, ConstraintEvent, etc.    │ │
│  └────────────────────────────────────────────────────────────┘ │
│                              ↓                                   │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │  Core Systems (Event Listeners - commonMain)               ││
│  │  ┌──────────────────┐  ┌──────────────────┐               ││
│  │  │ GeometryManager  │  │ TopologyBuilder  │               ││
│  │  │ (State updates)  │  │ (Halfedge mesh)  │               ││
│  │  └──────────────────┘  └──────────────────┘               ││
│  │  ┌──────────────────┐  ┌──────────────────┐               ││
│  │  │ ConstraintSolver │  │ FeatureTree      │               ││
│  │  │ (Parametric)     │  │ (History)        │               ││
│  │  └──────────────────┘  └──────────────────┘               ││
│  │  ┌──────────────────┐  ┌──────────────────┐               ││
│  │  │ MaterialPlanner  │  │ CodeValidator    │               ││
│  │  │ (Tile optimizer) │  │ (Building codes) │               ││
│  │  └──────────────────┘  └──────────────────┘               ││
│  └─────────────────────────────────────────────────────────────┘│
│                              ↓                                   │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │  App State (Immutable Data Classes - commonMain)          │ │
│  │  Vertex, HalfEdge, Face, Wall, Door, Tile, Outlet, etc.   │ │
│  └────────────────────────────────────────────────────────────┘ │
│                              ↓                                   │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │  Rendering Layer (Compose Canvas + DrawScope)             │ │
│  │  Custom Painter for CAD geometry (Skia backend)           │ │
│  └────────────────────────────────────────────────────────────┘ │
│                                                                 │
│  ┌───────────────────────┬────────────────────────────────────┐ │
│  │  iOS-Specific (iosMain)│  Android-Specific (androidMain)  │ │
│  │  - UIKit integration   │  - Android Canvas integration    │ │
│  │  - Metal rendering     │  - Jetpack Compose extensions    │ │
│  │  - Apple Pencil        │  - S Pen support                 │ │
│  └───────────────────────┴────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

### 2.3 Kotlin Multiplatform Module Structure

```
shared/
├── commonMain/          # Shared code (iOS + Android)
│   ├── domain/          # Business logic
│   │   ├── geometry/    # Pure geometry (Point2, Polygon, etc.)
│   │   ├── topology/    # Halfedge mesh
│   │   ├── building/    # Wall, Door, Room entities
│   │   ├── materials/   # Tile, flooring entities
│   │   └── constraints/ # Constraint solver
│   ├── data/            # State management
│   │   ├── models/      # Immutable data classes
│   │   ├── events/      # Event definitions
│   │   └── commands/    # Command pattern implementations
│   ├── presentation/    # ViewModels (shared business logic)
│   │   ├── drawing/
│   │   ├── materials/
│   │   └── estimation/
│   └── ui/              # Compose Multiplatform UI
│       ├── screens/
│       ├── components/
│       └── theme/
├── iosMain/             # iOS-specific
│   ├── platform/        # Platform APIs
│   └── interop/         # Swift/Objective-C interop
├── androidMain/         # Android-specific
│   ├── platform/        # Platform APIs
│   └── compose/         # Android-specific Compose
└── commonTest/          # Shared tests
```

---

## 3. Application Modes

### 3.1 Mode Structure (Mobile-Optimized)

```
RoomPlanner
│
├── Project Browser Mode
│   └── Grid of recent projects, create new, cloud sync
│
├── Floor Plan Mode ★ Primary mode (Touch-Optimized)
│   ├── Sketch Submode (default) - Draw walls, doors, windows
│   │   ├── Single-touch: Place points
│   │   ├── Two-finger: Pan/zoom
│   │   └── Apple Pencil/S Pen: Precision drawing
│   ├── Annotate Submode - Dimensions, labels, notes
│   └── Constrain Submode - Add/edit geometric constraints
│
├── Material Planning Mode
│   ├── Tile Layout Submode - Optimize tile patterns
│   ├── Flooring Submode - Hardwood, LVP layouts
│   └── Paint Submode - Wall/ceiling paint planning
│
├── Utilities Mode
│   ├── Electrical Submode - Outlets, switches, circuits
│   └── Plumbing Submode - Fixtures, pipes, drains
│
├── Estimation Mode (Mobile-Optimized)
│   ├── Material Review - Quantities and costs
│   ├── Labor Estimation - Hours and rates
│   └── Quote Generation - PDF sharing via email/print
│
└── Export Mode
    ├── PDF Export - Share via iOS/Android share sheet
    ├── Image Export - High-res PNG for documentation
    └── Cloud Sync - Backup to iCloud/Google Drive
```

### 3.2 Touch Gesture Mapping

**Floor Plan Drawing Mode:**

| Gesture | Action | Notes |
|---------|--------|-------|
| Single tap | Place vertex | Smart snap to grid/vertices |
| Tap + hold | Show precision input | Numeric keyboard for exact coords |
| Two-finger pan | Pan canvas | Standard pan gesture |
| Pinch | Zoom in/out | Smooth, continuous zoom |
| Apple Pencil/S Pen | Precision draw | 1:1 pressure-sensitive |
| Three-finger swipe | Undo/Redo | Native mobile gesture |
| Long press | Context menu | Wall properties, delete, etc. |
| Double-tap | Select entity | Highlight for editing |

**Material Planning Mode:**

| Gesture | Action |
|---------|--------|
| Tap room | Select room for material assignment |
| Pinch/zoom | Zoom into tile detail |
| Two-finger rotate | Rotate tile pattern (visual preview) |
| Swipe left/right | Cycle through layout options |

### 3.3 Mode-Specific UI Layouts (Mobile)

**Floor Plan Mode (Portrait - Phone):**
```
┌─────────────────────────────────┐
│ [<] Floor Plan    [☰] [Export]  │ ← Top bar (48dp)
├─────────────────────────────────┤
│                                 │
│         Drawing Canvas          │ ← Fullscreen canvas
│         (Pan, Zoom, Draw)       │
│                                 │
│                                 │
│                                 │
├─────────────────────────────────┤
│ [Wall] [Door] [Window] [Select] │ ← Tool bar (72dp)
├─────────────────────────────────┤
│ Snap: Grid | Pos: (120.5, 85.0) │ ← Status bar (32dp)
└─────────────────────────────────┘
```

**Floor Plan Mode (Landscape - Tablet):**
```
┌────────┬────────────────────────────────┬──────────┐
│Feature │                                │Properties│
│Tree    │       Drawing Canvas           │ Panel    │
│(240dp) │       (Pan, Zoom, Draw)        │ (320dp)  │
│        │                                │          │
│1. Wall │                                │Selected: │
│2. Door │                                │Wall #2   │
│3. Room │                                │Length:   │
│        │                                │240 cm    │
├────────┴────────────────────────────────┴──────────┤
│ [Wall] [Door] [Window] [Select] [Undo] [Redo]     │
└────────────────────────────────────────────────────┘
```

**Material Planning Mode:**
```
┌─────────────────────────────────┐
│ Material Planning - Bathroom    │
├─────────────────────────────────┤
│  Floor Plan + Tile Overlay      │
│  ┌─┬─┬─┬─┬─┐                    │
│  ├─┼─┼─┼─┼─┤                    │
│  ├─┼─┼─┼─┼─┤                    │
│  └─┴─┴─┴─┴─┘                    │
│                                 │
│  [<] Option 1: 12% waste        │ ← Swipe to change
│  [·] Option 2: 15% waste        │
│  [>] Option 3: 14% waste        │
│                                 │
├─────────────────────────────────┤
│ Porcelain 12×24" | $2.50/pc    │
│ 164 tiles needed | $410 total   │
│ [Optimize] [Change Material]    │
└─────────────────────────────────┘
```

---

## 4. Domain Model

### 4.1 Core Geometric Primitives (commonMain)

**Pure Kotlin (platform-agnostic):**
```kotlin
// shared/commonMain/kotlin/com/opentile/domain/geometry/primitives.kt

@Serializable
data class Point2(
    val x: Double,  // Double for CAD precision (not Float!)
    val y: Double
) {
    operator fun plus(other: Point2) = Point2(x + other.x, y + other.y)
    operator fun minus(other: Point2) = Point2(x - other.x, y - other.y)
    operator fun times(scalar: Double) = Point2(x * scalar, y * scalar)

    fun distanceTo(other: Point2): Double {
        val dx = x - other.x
        val dy = y - other.y
        return sqrt(dx * dx + dy * dy)
    }

    // Convert to Compose Offset for rendering
    fun toOffset(): Offset = Offset(x.toFloat(), y.toFloat())
}

@Serializable
data class LineSegment(
    val start: Point2,
    val end: Point2
) {
    val length: Double get() = start.distanceTo(end)
    val midpoint: Point2 get() = Point2(
        (start.x + end.x) / 2.0,
        (start.y + end.y) / 2.0
    )

    fun angle(): Double = atan2(end.y - start.y, end.x - start.x)
}

@Serializable
data class Polygon(
    val vertices: List<Point2>
) {
    fun area(): Double {
        // Shoelace formula
        var sum = 0.0
        for (i in vertices.indices) {
            val j = (i + 1) % vertices.size
            sum += vertices[i].x * vertices[j].y
            sum -= vertices[j].x * vertices[i].y
        }
        return abs(sum) / 2.0
    }

    fun centroid(): Point2 {
        var cx = 0.0
        var cy = 0.0
        for (v in vertices) {
            cx += v.x
            cy += v.y
        }
        return Point2(cx / vertices.size, cy / vertices.size)
    }

    fun contains(point: Point2): Boolean {
        // Ray casting algorithm
        var inside = false
        var j = vertices.size - 1
        for (i in vertices.indices) {
            val vi = vertices[i]
            val vj = vertices[j]
            if ((vi.y > point.y) != (vj.y > point.y) &&
                point.x < (vj.x - vi.x) * (point.y - vi.y) / (vj.y - vi.y) + vi.x) {
                inside = !inside
            }
            j = i
        }
        return inside
    }
}
```

### 4.2 Topology Components (BREP - commonMain)

```kotlin
// shared/commonMain/kotlin/com/opentile/domain/topology/halfedge.kt

@Serializable
data class Vertex(
    val id: String = UUID.randomUUID().toString(),
    val position: Point2,
    val fixed: Boolean = false,           // Locked by constraints
    val outgoingHalfedge: String? = null  // References HalfEdge ID
)

@Serializable
data class HalfEdge(
    val id: String = UUID.randomUUID().toString(),
    val vertex: String,      // Vertex ID this halfedge points TO
    val next: String,        // Next HalfEdge ID in loop
    val prev: String,        // Previous HalfEdge ID
    val twin: String,        // Opposite HalfEdge ID
    val face: String?        // Face ID on left (null = exterior)
)

@Serializable
data class Edge(
    val id: String = UUID.randomUUID().toString(),
    val halfedge: String,    // One of two halfedge IDs
    val geometry: LineSegment // Cached for fast queries
)

@Serializable
data class Face(
    val id: String = UUID.randomUUID().toString(),
    val halfedge: String,    // Any halfedge ID on boundary
    val boundary: Polygon    // Cached polygon
) {
    fun area(): Double = boundary.area()
    fun centroid(): Point2 = boundary.centroid()
}
```

### 4.3 Building Components (commonMain)

```kotlin
// shared/commonMain/kotlin/com/opentile/domain/building/components.kt

@Serializable
data class Wall(
    val id: String = UUID.randomUUID().toString(),
    val edge: String,        // Edge ID
    val thickness: Float,    // Inches or cm
    val height: Float,
    val material: WallMaterial
)

@Serializable
enum class WallMaterial {
    FRAMING_2X4,    // 3.5" thick
    FRAMING_2X6,    // 5.5" thick
    CONCRETE,
    EXISTING
}

@Serializable
data class Door(
    val id: String = UUID.randomUUID().toString(),
    val wall: String,        // Wall ID
    val position: Float,     // 0.0 to 1.0 along wall
    val width: Float,
    val swing: DoorSwing,
    val doorType: DoorType
)

@Serializable
enum class DoorSwing {
    LEFT_IN, LEFT_OUT, RIGHT_IN, RIGHT_OUT
}

@Serializable
enum class DoorType {
    STANDARD,
    POCKET,
    SLIDING,
    BIFOLD
}

@Serializable
data class Window(
    val id: String = UUID.randomUUID().toString(),
    val wall: String,
    val position: Float,
    val width: Float,
    val height: Float,
    val sillHeight: Float  // Height above floor
)

@Serializable
data class Room(
    val id: String = UUID.randomUUID().toString(),
    val face: String,      // Face ID
    val name: String,      // "Master Bedroom"
    val roomType: RoomType,
    val floorHeight: Float = 0f,
    val ceilingHeight: Float = 96f  // 8 feet default
)

@Serializable
enum class RoomType {
    BEDROOM, BATHROOM, KITCHEN, LIVING_ROOM,
    DINING_ROOM, HALLWAY, CLOSET, GARAGE, OFFICE
}
```

### 4.4 Material Components (commonMain)

```kotlin
// shared/commonMain/kotlin/com/opentile/domain/materials/components.kt

@Serializable
data class TileLayout(
    val id: String = UUID.randomUUID().toString(),
    val room: String,            // Room ID
    val tileSize: Pair<Float, Float>, // Width × height
    val groutSpacing: Float,
    val pattern: TilePattern,
    val rotation: Float = 0f,    // Degrees
    val startingPoint: Point2,
    val startingEdge: String? = null
)

@Serializable
sealed interface TilePattern {
    @Serializable
    data object Straight : TilePattern

    @Serializable
    data class RunningBond(val offset: RunningOffset) : TilePattern

    @Serializable
    data object Diagonal : TilePattern

    @Serializable
    data object Herringbone : TilePattern

    @Serializable
    data object Chevron : TilePattern
}

@Serializable
enum class RunningOffset {
    FIFTY_PERCENT,
    THIRTY_THREE_PERCENT,
    RANDOM
}

@Serializable
data class Tile(
    val id: String = UUID.randomUUID().toString(),
    val layout: String,      // TileLayout ID
    val position: Point2,
    val rotation: Float,
    val cut: TileCut,
    val number: Int          // Install sequence
)

@Serializable
sealed interface TileCut {
    @Serializable
    data object Whole : TileCut

    @Serializable
    data class Straight(val edge: Int, val cutSize: Float) : TileCut

    @Serializable
    data class LCut(val edges: Pair<Int, Int>, val cutSizes: Pair<Float, Float>) : TileCut
}
```

### 4.5 App State (Immutable - commonMain)

```kotlin
// shared/commonMain/kotlin/com/opentile/data/models/AppState.kt

@Serializable
data class AppState(
    val project: Project,
    val mode: AppMode,

    // Topology
    val vertices: Map<String, Vertex> = emptyMap(),
    val halfEdges: Map<String, HalfEdge> = emptyMap(),
    val edges: Map<String, Edge> = emptyMap(),
    val faces: Map<String, Face> = emptyMap(),

    // Building
    val walls: Map<String, Wall> = emptyMap(),
    val doors: Map<String, Door> = emptyMap(),
    val windows: Map<String, Window> = emptyMap(),
    val rooms: Map<String, Room> = emptyMap(),

    // Materials
    val tileLayouts: Map<String, TileLayout> = emptyMap(),
    val tiles: Map<String, Tile> = emptyMap(),

    // Constraints
    val constraints: Map<String, Constraint> = emptyMap(),

    // UI State
    val selectedEntity: String? = null,
    val snapSettings: SnapSettings = SnapSettings(),
    val cameraTransform: CameraTransform = CameraTransform()
) {
    // Helper functions
    fun withVertex(vertex: Vertex) = copy(
        vertices = vertices + (vertex.id to vertex)
    )

    fun withEdge(edge: Edge) = copy(
        edges = edges + (edge.id to edge)
    )

    // ... other helpers
}

@Serializable
data class CameraTransform(
    val panX: Float = 0f,
    val panY: Float = 0f,
    val zoom: Float = 1f
)

@Serializable
data class SnapSettings(
    val gridEnabled: Boolean = true,
    val gridSize: Float = 12f,  // Inches
    val snapToVertices: Boolean = true,
    val snapRadius: Float = 10f // Pixels
)
```

---

## 5. Core Systems

### 5.1 Drawing System (Touch-Optimized)

**Event-Driven Flow:**
```
User taps with Wall Tool
    ↓
GestureHandler detects tap, emits GeometryEvent::PointPlaced
    ↓
GeometryManager listens:
    - Creates Vertex
    - If previous point exists, creates Edge + two HalfEdges
    - Emits TopologyEvent::EdgeCreated
    ↓
TopologyBuilder listens:
    - Links halfedges (next/prev pointers)
    - Detects closed loops
    - Emits TopologyEvent::FaceDetected
    ↓
FaceDetection listens:
    - Creates Face (Room)
    - Calculates area, perimeter
    - Emits RoomEvent::RoomCreated
    ↓
FeatureTree listens:
    - Logs "Draw Wall #5" feature
    - Stores command for undo/redo
    ↓
UI recomposes with new state
```

**Key Code (Kotlin):**
```kotlin
// shared/commonMain/kotlin/com/opentile/presentation/drawing/WallToolViewModel.kt

class WallToolViewModel(
    private val eventBus: EventBus,
    private val stateManager: StateManager
) : ViewModel() {

    fun onTap(position: Offset) {
        viewModelScope.launch {
            // Convert to world coordinates
            val worldPos = screenToWorld(position)

            // Smart snap
            val snapped = snapToGrid(worldPos)
            val snappedVertex = findNearbyVertex(snapped)

            // Emit event (DON'T mutate state directly!)
            eventBus.emit(GeometryEvent.PointPlaced(
                position = snapped,
                snappedTo = snappedVertex?.id
            ))
        }
    }

    private fun snapToGrid(point: Point2): Point2 {
        val snapSettings = stateManager.state.value.snapSettings
        if (!snapSettings.gridEnabled) return point

        val gridSize = snapSettings.gridSize
        return Point2(
            x = (point.x / gridSize).roundToInt() * gridSize,
            y = (point.y / gridSize).roundToInt() * gridSize
        )
    }

    private fun findNearbyVertex(point: Point2): Vertex? {
        val state = stateManager.state.value
        val snapRadius = state.snapSettings.snapRadius

        return state.vertices.values
            .filter { it.position.distanceTo(point) < snapRadius }
            .minByOrNull { it.position.distanceTo(point) }
    }
}

// shared/commonMain/kotlin/com/opentile/domain/geometry/GeometryManager.kt

class GeometryManager(
    private val eventBus: EventBus,
    private val stateManager: StateManager
) {
    init {
        // Listen to geometry events
        eventBus.events
            .filterIsInstance<GeometryEvent>()
            .onEach { event -> handleEvent(event) }
            .launchIn(CoroutineScope(Dispatchers.Default))
    }

    private suspend fun handleEvent(event: GeometryEvent) {
        when (event) {
            is GeometryEvent.PointPlaced -> {
                val vertex = if (event.snappedTo != null) {
                    // Reuse existing vertex
                    stateManager.state.value.vertices[event.snappedTo]!!
                } else {
                    // Create new vertex
                    Vertex(position = event.position)
                }

                // Update state immutably
                stateManager.updateState { state ->
                    state.withVertex(vertex)
                }

                // If continuing from previous point, create edge
                val previousVertex = getPreviousVertex()
                if (previousVertex != null) {
                    createEdge(previousVertex, vertex)
                    eventBus.emit(TopologyEvent.EdgeCreated(
                        start = previousVertex.id,
                        end = vertex.id
                    ))
                }
            }
            // ... other events
        }
    }

    private suspend fun createEdge(v1: Vertex, v2: Vertex) {
        // Create halfedges
        val he1 = HalfEdge(
            vertex = v2.id,
            next = "", // Will be linked by TopologyBuilder
            prev = "",
            twin = "", // Will be set below
            face = null
        )

        val he2 = HalfEdge(
            vertex = v1.id,
            next = "",
            prev = "",
            twin = he1.id,
            face = null
        )

        val he1Updated = he1.copy(twin = he2.id)

        val edge = Edge(
            halfedge = he1Updated.id,
            geometry = LineSegment(v1.position, v2.position)
        )

        // Update state
        stateManager.updateState { state ->
            state.copy(
                halfEdges = state.halfEdges +
                    (he1Updated.id to he1Updated) +
                    (he2.id to he2),
                edges = state.edges + (edge.id to edge)
            )
        }
    }
}
```

### 5.2 Touch Gesture System (Mobile-Specific)

```kotlin
// shared/commonMain/kotlin/com/opentile/ui/gestures/GestureHandler.kt

@Composable
fun DrawingCanvas(
    state: AppState,
    onEvent: (GeometryEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    var cameraTransform by remember { mutableStateOf(state.cameraTransform) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        // Single tap = place point
                        onEvent(GeometryEvent.PointPlaced(
                            position = screenToWorld(offset, cameraTransform),
                            snappedTo = null
                        ))
                    },
                    onLongPress = { offset ->
                        // Long press = context menu
                        showContextMenu(offset)
                    }
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
        // Render geometry (see section 5.3)
        drawGeometry(state, cameraTransform)
    }
}

// iOS-specific: Apple Pencil support
// shared/iosMain/kotlin/com/opentile/platform/ApplePencilHandler.kt
actual class StylusHandler {
    actual fun onStylusEvent(event: StylusEvent) {
        // Handle Apple Pencil with pressure/tilt
        when (event.type) {
            StylusEventType.DOWN -> { /* Start drawing */ }
            StylusEventType.MOVE -> {
                // Use pressure for line width, tilt for shading
            }
            StylusEventType.UP -> { /* Finish drawing */ }
        }
    }
}
```

### 5.3 Rendering System (Skia-Backed Canvas)

```kotlin
// shared/commonMain/kotlin/com/opentile/ui/rendering/GeometryRenderer.kt

fun DrawScope.drawGeometry(
    state: AppState,
    camera: CameraTransform
) {
    // Apply camera transform
    scale(camera.zoom) {
        translate(camera.panX, camera.panY) {

            // Draw edges (walls)
            state.edges.values.forEach { edge ->
                val start = edge.geometry.start.toOffset()
                val end = edge.geometry.end.toOffset()

                drawLine(
                    color = Color.Black,
                    start = start,
                    end = end,
                    strokeWidth = 2f / camera.zoom // Scale-invariant width
                )
            }

            // Draw vertices (snap points)
            state.vertices.values.forEach { vertex ->
                drawCircle(
                    color = if (vertex.fixed) Color.Red else Color.Green,
                    radius = 5f / camera.zoom,
                    center = vertex.position.toOffset()
                )
            }

            // Draw faces (rooms) with fill
            state.faces.values.forEach { face ->
                val path = Path().apply {
                    val vertices = face.boundary.vertices
                    if (vertices.isNotEmpty()) {
                        moveTo(vertices[0].x.toFloat(), vertices[0].y.toFloat())
                        for (i in 1 until vertices.size) {
                            lineTo(vertices[i].x.toFloat(), vertices[i].y.toFloat())
                        }
                        close()
                    }
                }

                drawPath(
                    path = path,
                    color = Color.LightGray.copy(alpha = 0.3f),
                    style = Fill
                )
            }
        }
    }
}

// Performance optimization: drawWithCache
@Composable
fun OptimizedDrawingCanvas(state: AppState) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        // Cache expensive drawing operations
        val cachedPath = remember(state.faces) {
            // Rebuild path only when faces change
            buildFacePaths(state.faces)
        }

        drawWithCache {
            onDrawBehind {
                drawCachedPath(cachedPath)
            }
        }
    }
}
```

### 5.4 Constraint System (Same as Desktop)

```kotlin
// shared/commonMain/kotlin/com/opentile/domain/constraints/ConstraintSolver.kt

class ConstraintSolver(
    private val stateManager: StateManager
) {
    suspend fun solveConstraints() {
        val state = stateManager.state.value
        val constraints = state.constraints.values.toList()

        val maxIterations = 100
        val tolerance = 0.001

        repeat(maxIterations) { iteration ->
            var maxError = 0.0

            constraints.forEach { constraint ->
                val error = applyConstraint(constraint, state)
                maxError = max(maxError, error)
            }

            if (maxError < tolerance) {
                // Converged!
                return
            }
        }
    }

    private fun applyConstraint(
        constraint: Constraint,
        state: AppState
    ): Double {
        return when (constraint) {
            is Constraint.Distance -> {
                val v1 = state.vertices[constraint.entity1]!!
                val v2 = state.vertices[constraint.entity2]!!
                val currentDist = v1.position.distanceTo(v2.position)
                val error = currentDist - constraint.distance

                if (!v1.fixed && !v2.fixed) {
                    // Move both vertices
                    val direction = (v2.position - v1.position).normalize()
                    val correction = direction * (error * 0.25)

                    stateManager.updateState { state ->
                        state.copy(
                            vertices = state.vertices +
                                (v1.id to v1.copy(position = v1.position + correction)) +
                                (v2.id to v2.copy(position = v2.position - correction))
                        )
                    }
                }

                abs(error)
            }

            is Constraint.Perpendicular -> {
                // Similar logic for perpendicularity
                0.0
            }

            // ... other constraint types
        }
    }
}

// Constraint definitions
@Serializable
sealed interface Constraint {
    @Serializable
    data class Distance(
        val id: String = UUID.randomUUID().toString(),
        val entity1: String,
        val entity2: String,
        val distance: Double
    ) : Constraint

    @Serializable
    data class Perpendicular(
        val id: String = UUID.randomUUID().toString(),
        val edge1: String,
        val edge2: String
    ) : Constraint

    @Serializable
    data class Parallel(
        val id: String = UUID.randomUUID().toString(),
        val edge1: String,
        val edge2: String
    ) : Constraint
}
```

---

## 6. Feature Implementation Details

### 6.1 Smart Snapping System (Touch-Optimized)

```kotlin
// shared/commonMain/kotlin/com/opentile/domain/snapping/SmartSnapSystem.kt

data class SnapCandidate(
    val position: Point2,
    val snapType: SnapMode,
    val priority: Int,
    val visual: SnapVisual
)

enum class SnapMode {
    GRID,
    VERTEX,
    MIDPOINT,
    CENTER,
    PERPENDICULAR,
    PARALLEL,
    INTERSECTION,
    EXTENSION,
    ON_EDGE
}

sealed interface SnapVisual {
    data class Circle(val radius: Float, val color: Color) : SnapVisual
    data class Cross(val size: Float, val color: Color) : SnapVisual
    data class Line(val start: Point2, val end: Point2) : SnapVisual
}

class SmartSnapSystem(
    private val state: AppState,
    private val spatialIndex: SpatialIndex // R-tree for fast queries
) {
    fun findSnap(cursor: Point2): SnapCandidate? {
        val candidates = mutableListOf<SnapCandidate>()

        // Grid snap (lowest priority)
        if (state.snapSettings.gridEnabled) {
            candidates.add(snapToGrid(cursor))
        }

        // Vertex snap (high priority) - Use spatial index!
        val nearbyVertices = spatialIndex.locateWithinDistance(
            cursor,
            state.snapSettings.snapRadius
        )
        nearbyVertices.firstOrNull()?.let { vertex ->
            candidates.add(SnapCandidate(
                position = vertex.position,
                snapType = SnapMode.VERTEX,
                priority = 10,
                visual = SnapVisual.Circle(6f, Color.Green)
            ))
        }

        // Midpoint snap
        state.edges.values.forEach { edge ->
            val midpoint = edge.geometry.midpoint
            if (cursor.distanceTo(midpoint) < state.snapSettings.snapRadius) {
                candidates.add(SnapCandidate(
                    position = midpoint,
                    snapType = SnapMode.MIDPOINT,
                    priority = 8,
                    visual = SnapVisual.Cross(8f, Color.Blue)
                ))
            }
        }

        // Sort by priority, then distance
        return candidates
            .sortedWith(compareBy({ -it.priority }, { it.position.distanceTo(cursor) }))
            .firstOrNull()
    }

    private fun snapToGrid(point: Point2): SnapCandidate {
        val gridSize = state.snapSettings.gridSize.toDouble()
        val snapped = Point2(
            x = (point.x / gridSize).roundToInt() * gridSize,
            y = (point.y / gridSize).roundToInt() * gridSize
        )

        return SnapCandidate(
            position = snapped,
            snapType = SnapMode.GRID,
            priority = 1,
            visual = SnapVisual.Circle(4f, Color.Gray)
        )
    }
}

// Spatial index for O(log n) queries (instead of O(n))
class SpatialIndex {
    private val vertexTree = RTree<Vertex>()

    fun insert(vertex: Vertex) {
        vertexTree.insert(vertex, vertex.position)
    }

    fun locateWithinDistance(point: Point2, radius: Double): List<Vertex> {
        return vertexTree.search(
            Rectangle(
                point.x - radius,
                point.y - radius,
                point.x + radius,
                point.y + radius
            )
        ).filter { it.position.distanceTo(point) <= radius }
    }
}
```

### 6.2 Undo/Redo with Command Pattern

```kotlin
// shared/commonMain/kotlin/com/opentile/domain/commands/Command.kt

interface Command {
    suspend fun execute(state: AppState): Result<AppState>
    suspend fun undo(state: AppState): Result<AppState>
    val name: String

    // Optional: Commands that can merge (e.g., drag operations)
    fun merge(other: Command): Command? = null
}

// shared/commonMain/kotlin/com/opentile/domain/commands/CommandHistory.kt

class CommandHistory(
    private val stateManager: StateManager,
    private val maxHistory: Int = 100
) {
    private val undoStack = mutableListOf<Command>()
    private val redoStack = mutableListOf<Command>()

    suspend fun execute(command: Command) {
        val result = command.execute(stateManager.state.value)

        result.onSuccess { newState ->
            stateManager.setState(newState)

            // Clear redo stack on new command
            redoStack.clear()

            // Try to merge with previous command
            val merged = undoStack.lastOrNull()?.merge(command)
            if (merged != null) {
                undoStack[undoStack.lastIndex] = merged
            } else {
                undoStack.add(command)
            }

            // Limit stack size
            if (undoStack.size > maxHistory) {
                undoStack.removeAt(0)
            }
        }
    }

    suspend fun undo() {
        val command = undoStack.removeLastOrNull() ?: return

        val result = command.undo(stateManager.state.value)
        result.onSuccess { newState ->
            stateManager.setState(newState)
            redoStack.add(command)
        }
    }

    suspend fun redo() {
        val command = redoStack.removeLastOrNull() ?: return

        val result = command.execute(stateManager.state.value)
        result.onSuccess { newState ->
            stateManager.setState(newState)
            undoStack.add(command)
        }
    }
}

// Example: CreateWallCommand
class CreateWallCommand(
    private val start: Point2,
    private val end: Point2
) : Command {
    override val name = "Create Wall"

    private var createdVertices: Pair<String, String>? = null
    private var createdEdge: String? = null
    private var createdHalfedges: Pair<String, String>? = null

    override suspend fun execute(state: AppState): Result<AppState> {
        // Create vertices
        val v1 = Vertex(position = start)
        val v2 = Vertex(position = end)

        // Create halfedges
        val he1 = HalfEdge(
            vertex = v2.id,
            next = "",
            prev = "",
            twin = "",
            face = null
        )
        val he2 = HalfEdge(
            vertex = v1.id,
            next = "",
            prev = "",
            twin = he1.id,
            face = null
        )
        val he1Updated = he1.copy(twin = he2.id)

        // Create edge
        val edge = Edge(
            halfedge = he1Updated.id,
            geometry = LineSegment(start, end)
        )

        // Store for undo
        createdVertices = v1.id to v2.id
        createdEdge = edge.id
        createdHalfedges = he1Updated.id to he2.id

        // Return new state
        return Result.success(
            state.copy(
                vertices = state.vertices + (v1.id to v1) + (v2.id to v2),
                halfEdges = state.halfEdges + (he1Updated.id to he1Updated) + (he2.id to he2),
                edges = state.edges + (edge.id to edge)
            )
        )
    }

    override suspend fun undo(state: AppState): Result<AppState> {
        // Remove created entities
        val newVertices = state.vertices - listOfNotNull(
            createdVertices?.first,
            createdVertices?.second
        )
        val newHalfEdges = state.halfEdges - listOfNotNull(
            createdHalfedges?.first,
            createdHalfedges?.second
        )
        val newEdges = state.edges - listOfNotNull(createdEdge)

        return Result.success(
            state.copy(
                vertices = newVertices,
                halfEdges = newHalfEdges,
                edges = newEdges
            )
        )
    }
}
```

---

## 7. Technology Stack

### 7.1 Core Technologies

| Component | Technology | Version | Rationale |
|-----------|-----------|---------|-----------|
| **Language** | Kotlin | 2.2.0+ | Latest stable, K2 compiler |
| **Multiplatform** | Kotlin Multiplatform | 2.2.0+ | iOS + Android from single codebase |
| **UI Framework** | Compose Multiplatform | 1.8.0+ | Declarative UI, iOS stable, Skia backend |
| **Rendering** | Skiko (Skia for Kotlin) | Built-in | Hardware-accelerated 2D (Metal/OpenGL) |
| **State Management** | Kotlin Flows + StateFlow | Built-in | Reactive streams, coroutine-native |
| **Serialization** | kotlinx.serialization | 1.7.3+ | Type-safe, multiplatform |
| **Geometry Kernel** | Custom + androidx.compose.ui.geometry | Built-in | CAD precision + Compose integration |
| **Spatial Index** | Custom R-tree (KMP) | - | Fast O(log n) queries |
| **Coroutines** | kotlinx.coroutines | 1.9.0+ | Async/threading |
| **Date/Time** | kotlinx-datetime | 0.6.1+ | Multiplatform datetime |
| **UUID** | Custom UUID impl | - | Multiplatform UUID generation |
| **Logging** | Kermit | 2.0.4+ | Multiplatform logging |
| **Dependency Injection** | Koin (KMP) | 4.0.0+ | Simple DI for KMP |

### 7.2 Platform-Specific Libraries

**iOS (iosMain):**
- `platform.UIKit.*` - iOS UI integration
- `platform.Metal.*` - GPU rendering (via Skia)
- `platform.CoreGraphics.*` - Additional geometry ops
- `ApplePencilKit` - Stylus support (future)

**Android (androidMain):**
- `androidx.compose.ui.*` - Android Compose extensions
- `androidx.activity.compose.*` - Activity integration
- `com.google.android.material.*` - Material Design components

### 7.3 Gradle Configuration (build.gradle.kts)

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
            implementation(compose.components.resources)

            // Kotlin coroutines
            implementation(libs.kotlinx.coroutines.core)

            // Serialization
            implementation(libs.kotlinx.serialization.json)

            // DateTime
            implementation(libs.kotlinx.datetime)

            // Logging
            implementation(libs.kermit)

            // DI
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
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

### 7.4 Version Catalog (gradle/libs.versions.toml)

```toml
[versions]
kotlin = "2.2.0"
compose = "1.8.0"
coroutines = "1.9.0"
serialization = "1.7.3"
datetime = "0.6.1"
kermit = "2.0.4"
koin = "4.0.0"

[libraries]
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version.ref = "datetime" }
kermit = { module = "co.touchlab:kermit", version.ref = "kermit" }
koin-core = { module = "io.insert-koin:koin-core", version.ref = "koin" }
koin-compose = { module = "io.insert-koin:koin-compose", version.ref = "koin" }

[plugins]
kotlinMultiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
androidLibrary = { id = "com.android.library", version = "8.7.3" }
jetbrainsCompose = { id = "org.jetbrains.compose", version.ref = "compose" }
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlinx-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

### 7.5 Architecture Patterns (KMP + Compose)

**Event-Driven with Flows:**
```kotlin
// Event bus using SharedFlow
class EventBus {
    private val _events = MutableSharedFlow<AppEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<AppEvent> = _events.asSharedFlow()

    suspend fun emit(event: AppEvent) = _events.emit(event)
}

// System that listens to events
class GeometryManager(private val eventBus: EventBus) {
    init {
        eventBus.events
            .filterIsInstance<GeometryEvent>()
            .onEach { handleEvent(it) }
            .launchIn(CoroutineScope(Dispatchers.Default))
    }
}
```

**State Management with StateFlow:**
```kotlin
class StateManager {
    private val _state = MutableStateFlow(AppState.initial())
    val state: StateFlow<AppState> = _state.asStateFlow()

    fun updateState(reducer: (AppState) -> AppState) {
        _state.update(reducer)
    }

    fun setState(newState: AppState) {
        _state.value = newState
    }
}

// Compose integration
@Composable
fun FloorPlanScreen(stateManager: StateManager) {
    val state by stateManager.state.collectAsState()

    DrawingCanvas(
        state = state,
        onEvent = { /* handle */ }
    )
}
```

**Mode-Based Navigation:**
```kotlin
@Composable
fun AppNavigation() {
    var currentMode by remember { mutableStateOf<AppMode>(AppMode.ProjectBrowser) }

    AnimatedContent(
        targetState = currentMode,
        transitionSpec = {
            fadeIn(animationSpec = tween(300)) togetherWith
                fadeOut(animationSpec = tween(300))
        }
    ) { mode ->
        when (mode) {
            AppMode.ProjectBrowser -> ProjectBrowserScreen(
                onNavigate = { currentMode = it }
            )
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
```

**Performance: DrawWithCache for Complex Rendering:**
```kotlin
@Composable
fun DrawingCanvas(state: AppState) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        // Cache expensive path operations
        val cachedPaths = remember(state.faces) {
            state.faces.values.map { face ->
                Path().apply {
                    face.boundary.vertices.forEachIndexed { i, vertex ->
                        if (i == 0) moveTo(vertex.x.toFloat(), vertex.y.toFloat())
                        else lineTo(vertex.x.toFloat(), vertex.y.toFloat())
                    }
                    close()
                }
            }
        }

        // Draw cached paths
        cachedPaths.forEach { path ->
            drawPath(
                path = path,
                color = Color.LightGray.copy(alpha = 0.3f)
            )
        }
    }
}
```

---

## 8. Project Scaffolding & Setup

This section defines the complete project setup, tooling configuration, and best practices for development.

### 8.1 Version Management (mise)

OpenTile Mobile uses **[mise](https://mise.jdx.dev/)** (formerly rtx) for managing tool versions. mise is a [polyglot tool version manager](https://github.com/jdx/mise) written in Rust that replaces asdf, nvm, pyenv, and similar tools with [better performance](https://mise.jdx.dev/dev-tools/comparison-to-asdf.html).

**Why mise?**
- ✅ **Fast:** Written in Rust, [faster than asdf](https://blog.logrocket.com/mise-vs-asdf-javascript-project-environment-management/)
- ✅ **No shims:** Directly modifies PATH (no performance overhead)
- ✅ **Compatible:** Works with `.tool-versions` and idiomatic files (`.node-version`, `.java-version`)
- ✅ **Single tool:** Manages Java, Gradle, Node, Python, etc.
- ✅ **Local config:** `.mise.toml` in project root (version controlled)

**Installation:**
```bash
# macOS
brew install mise

# Linux
curl https://mise.run | sh

# Add to shell profile (~/.zshrc or ~/.bashrc)
eval "$(mise activate zsh)"  # or bash/fish
```

**Configuration File (`.mise.toml`):**
```toml
# OpenTile Mobile - Tool Version Management
# See: https://mise.jdx.dev/configuration.html

[tools]
# Java 17 (LTS) - Required for Android Gradle Plugin 8.x
java = "temurin-17.0.13"

# Gradle 8.10.2 - Latest stable for KMP projects
# Note: Gradle 9.x is available but KMP ecosystem standardizing on 8.x
gradle = "8.10.2"

# Kotlin managed by Gradle plugin (see gradle/libs.versions.toml)
# kotlin = "2.2.0" # Don't install globally - use Gradle version

# Node.js 22.x - For Compose for Web (future)
node = "22.12.0"

# Optional: Android command-line tools (if not using Android Studio)
# android-sdk = "11076708"  # cmdline-tools version

[env]
# Android SDK location (adjust for your system)
# ANDROID_HOME = "{{ env.HOME }}/Library/Android/sdk"  # macOS
# ANDROID_HOME = "{{ env.HOME }}/Android/Sdk"          # Linux

# Java home (managed by mise)
JAVA_HOME = "{{ env.MISE_DATA_DIR }}/installs/java/temurin-17.0.13"

# Gradle options
GRADLE_OPTS = "-Xmx4g -XX:+UseParallelGC"

# Kotlin compiler options
KOTLIN_COMPILER_OPTS = "-Xjvm-default=all"

[tasks]
# Custom tasks (run with: mise run <task>)

[tasks.setup]
description = "Initial project setup"
run = """
  echo "Setting up OpenTile Mobile..."
  ./gradlew --version
  ./gradlew tasks
  echo "✓ Setup complete! Run './gradlew :androidApp:installDebug' to build Android."
"""

[tasks.clean]
description = "Clean all build artifacts"
run = "./gradlew clean"

[tasks.test]
description = "Run all tests"
run = "./gradlew test"

[tasks.lint]
description = "Run ktlint and detekt"
run = "./gradlew ktlintCheck detekt"

[tasks.format]
description = "Auto-format code with ktlint"
run = "./gradlew ktlintFormat"
```

**Usage:**
```bash
# Install all tools from .mise.toml
mise install

# Check versions
mise list

# Run tasks
mise run setup
mise run test
mise run lint

# Update tools
mise upgrade
```

### 8.2 Git Hooks (Lefthook)

OpenTile Mobile uses **[Lefthook](https://github.com/evilmartians/lefthook)** for managing Git hooks. Lefthook is a [fast Git hooks manager](https://lefthook.dev/) written in Go by Evil Martians.

**Why Lefthook?**
- ✅ **Fast:** Parallel execution, written in Go
- ✅ **Simple:** Single YAML config file
- ✅ **Cross-platform:** Works on macOS, Linux, Windows
- ✅ **No dependencies:** Single binary
- ✅ **Gradle integration:** [Gradle plugin available](https://plugins.gradle.org/plugin/com.fizzpod.lefthook)

**Installation:**
```bash
# macOS
brew install lefthook

# Linux
curl -1sLf 'https://dl.cloudsmith.io/public/evilmartians/lefthook/setup.deb.sh' | sudo -E bash
sudo apt install lefthook

# Or via mise
mise use -g lefthook@latest
```

**Configuration File (`lefthook.yml`):**
```yaml
# OpenTile Mobile - Git Hooks Configuration
# See: https://github.com/evilmartians/lefthook/blob/master/docs/configuration.md

# Minimum lefthook version
min_version: 1.9.2

# Output options
output:
  - summary
  - success

# Skip hooks on merge commits
skip_output:
  - meta
  - execution

# Pre-commit hook: Run before committing
pre-commit:
  parallel: true
  commands:
    # 1. Kotlin linting with ktlint
    ktlint:
      glob: "*.{kt,kts}"
      run: ./gradlew ktlintCheck --daemon
      stage_fixed: true
      fail_text: "❌ ktlint failed. Run './gradlew ktlintFormat' to fix."

    # 2. Static analysis with detekt
    detekt:
      glob: "*.{kt,kts}"
      run: ./gradlew detekt --daemon
      fail_text: "❌ Detekt found issues. Check build/reports/detekt/"

    # 3. Unit tests (shared module only - fast)
    test-shared:
      glob: "shared/**/*.kt"
      run: ./gradlew :shared:testDebugUnitTest --daemon
      fail_text: "❌ Shared tests failed. Fix before committing."

    # 4. Check for TODO/FIXME in staged files
    check-todos:
      glob: "*.{kt,kts}"
      run: |
        if git diff --cached --name-only | xargs grep -n "TODO\|FIXME" 2>/dev/null; then
          echo "⚠️  Warning: TODOs/FIXMEs found in staged files"
          echo "Consider resolving before committing"
          exit 0  # Don't fail, just warn
        fi

# Pre-push hook: Run before pushing to remote
pre-push:
  parallel: false  # Run sequentially
  commands:
    # 1. Full test suite (slower, but comprehensive)
    test-all:
      run: ./gradlew test --daemon
      fail_text: "❌ Tests failed. Fix before pushing."

    # 2. Build check (ensure project compiles)
    build-check:
      run: ./gradlew assembleDebug --daemon
      fail_text: "❌ Build failed. Fix compilation errors before pushing."

# Commit message linting (optional)
commit-msg:
  commands:
    conventional-commits:
      run: |
        # Check commit message follows conventional commits format
        # Example: "feat(drawing): add smart snapping"
        MSG_FILE=$1
        MSG=$(cat "$MSG_FILE")
        PATTERN="^(feat|fix|docs|style|refactor|test|chore|perf)(\(.+\))?: .{1,50}"

        if ! echo "$MSG" | grep -qE "$PATTERN"; then
          echo "❌ Commit message must follow Conventional Commits format:"
          echo "   <type>(<scope>): <subject>"
          echo ""
          echo "Types: feat, fix, docs, style, refactor, test, chore, perf"
          echo "Example: feat(drawing): add smart snapping"
          exit 1
        fi

# Skip hooks for certain scenarios
skip_ref:
  - main      # Don't run hooks on main branch (CI handles it)
  - master    # Don't run hooks on master branch
  - develop   # Don't run hooks on develop branch
```

**Setup:**
```bash
# Initialize lefthook (creates .git/hooks/)
lefthook install

# Test hooks without committing
lefthook run pre-commit

# Skip hooks for one commit (emergency only!)
LEFTHOOK=0 git commit -m "emergency fix"

# Update lefthook
lefthook update
```

**Gradle Integration (Optional):**
```kotlin
// build.gradle.kts (root)
plugins {
    id("com.fizzpod.lefthook") version "0.2.4"
}

lefthook {
    // Auto-install on first build
    autoInstall = true
}
```

### 8.3 Gradle Configuration

**Version Catalog (`gradle/libs.versions.toml`):**
```toml
# OpenTile Mobile - Dependency Version Catalog
# See: https://docs.gradle.org/current/userguide/platforms.html

[versions]
# Kotlin & Compose
kotlin = "2.2.0"  # Latest stable with K2 compiler
compose = "1.8.0"  # iOS stable (May 2025)
compose-compiler = "1.5.14"  # Bundled with Kotlin 2.2.0

# Android
android-sdk-compile = "35"
android-sdk-min = "24"  # Android 7.0 (Nougat)
android-sdk-target = "35"  # Latest stable
agp = "8.7.3"  # Android Gradle Plugin

# Coroutines & Serialization
coroutines = "1.9.0"
serialization = "1.7.3"
datetime = "0.6.1"
collections-immutable = "0.3.8"

# Dependency Injection
koin = "4.0.1"

# Logging
kermit = "2.0.4"

# Testing
junit = "4.13.2"
androidx-test-junit = "1.2.1"
androidx-test-espresso = "3.6.1"
kotlin-test = "2.2.0"
turbine = "1.2.0"  # Flow testing

# Code Quality
ktlint = "12.1.2"  # Ktlint Gradle plugin
detekt = "1.23.7"
kover = "0.9.1"  # Code coverage

# AndroidX
androidx-core = "1.15.0"
androidx-lifecycle = "2.8.7"
androidx-activity-compose = "1.9.3"

[libraries]
# Kotlin stdlib
kotlin-stdlib = { module = "org.jetbrains.kotlin:kotlin-stdlib", version.ref = "kotlin" }
kotlin-test = { module = "org.jetbrains.kotlin:kotlin-test", version.ref = "kotlin-test" }

# Coroutines
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }

# Serialization
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version.ref = "datetime" }
kotlinx-collections-immutable = { module = "org.jetbrains.kotlinx:kotlinx-collections-immutable", version.ref = "collections-immutable" }

# Dependency Injection
koin-core = { module = "io.insert-koin:koin-core", version.ref = "koin" }
koin-android = { module = "io.insert-koin:koin-android", version.ref = "koin" }
koin-compose = { module = "io.insert-koin:koin-compose", version.ref = "koin" }
koin-test = { module = "io.insert-koin:koin-test", version.ref = "koin" }

# Logging
kermit = { module = "co.touchlab:kermit", version.ref = "kermit" }

# AndroidX
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "androidx-core" }
androidx-lifecycle-runtime = { module = "androidx.lifecycle:lifecycle-runtime-ktx", version.ref = "androidx-lifecycle" }
androidx-lifecycle-viewmodel = { module = "androidx.lifecycle:lifecycle-viewmodel-ktx", version.ref = "androidx-lifecycle" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "androidx-activity-compose" }

# Testing
junit = { module = "junit:junit", version.ref = "junit" }
androidx-test-junit = { module = "androidx.test.ext:junit", version.ref = "androidx-test-junit" }
androidx-test-espresso = { module = "androidx.test.espresso:espresso-core", version.ref = "androidx-test-espresso" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }

[plugins]
# Kotlin plugins
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
kotlin-parcelize = { id = "org.jetbrains.kotlin.plugin.parcelize", version.ref = "kotlin" }

# Android plugins
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }

# Compose plugins
jetbrains-compose = { id = "org.jetbrains.compose", version.ref = "compose" }
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }

# Code quality plugins
ktlint = { id = "org.jlleitschuh.gradle.ktlint", version.ref = "ktlint" }
detekt = { id = "io.gitlab.arturbosch.detekt", version.ref = "detekt" }
kover = { id = "org.jetbrains.kotlinx.kover", version.ref = "kover" }

# Lefthook (optional)
lefthook = { id = "com.fizzpod.lefthook", version = "0.2.4" }
```

**Root `build.gradle.kts`:**
```kotlin
// Root build.gradle.kts
plugins {
    // Kotlin plugins (don't apply)
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.parcelize) apply false

    // Android plugins
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false

    // Compose plugins
    alias(libs.plugins.jetbrains.compose) apply false
    alias(libs.plugins.compose.compiler) apply false

    // Code quality
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
}

allprojects {
    // Apply ktlint to all subprojects
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.5.0")
        android.set(true)
        outputToConsole.set(true)
        ignoreFailures.set(false)

        filter {
            exclude("**/generated/**")
            exclude("**/build/**")
        }
    }
}

// Detekt configuration
detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("$rootDir/detekt.yml"))
    source.setFrom(files("shared/src", "androidApp/src"))
    parallel = true
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
```

**`settings.gradle.kts`:**
```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "OpenTileMobile"

include(":shared")
include(":androidApp")

// Enable Gradle build cache
buildCache {
    local {
        isEnabled = true
        directory = File(rootDir, ".gradle/build-cache")
        removeUnusedEntriesAfterDays = 30
    }
}

// Enable configuration cache (Gradle 8.x)
enableFeaturePreview("STABLE_CONFIGURATION_CACHE")
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
```

**`gradle.properties`:**
```properties
# Gradle configuration
org.gradle.jvmargs=-Xmx4g -XX:+UseParallelGC -XX:MaxMetaspaceSize=1g
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=true
org.gradle.daemon=true
org.gradle.configureondemand=true

# Kotlin configuration
kotlin.code.style=official
kotlin.mpp.stability.nowarn=true
kotlin.mpp.androidSourceSetLayoutVersion=2

# Android configuration
android.useAndroidX=true
android.enableJetifier=false
android.nonTransitiveRClass=true

# Compose configuration
compose.kotlinCompilerPlugin=2.2.0

# KMP configuration
kotlin.native.cacheKind=none
kotlin.native.binary.memoryModel=experimental
```

### 8.4 Code Quality Configuration

**EditorConfig (`.editorconfig`):**
```ini
# EditorConfig for OpenTile Mobile
# See: https://editorconfig.org

root = true

[*]
charset = utf-8
end_of_line = lf
insert_final_newline = true
trim_trailing_whitespace = true
indent_style = space
indent_size = 4

[*.{kt,kts}]
indent_size = 4
max_line_length = 120
ij_kotlin_allow_trailing_comma = true
ij_kotlin_allow_trailing_comma_on_call_site = true

[*.{xml,gradle,gradle.kts}]
indent_size = 4

[*.{yml,yaml,toml}]
indent_size = 2

[*.md]
trim_trailing_whitespace = false
max_line_length = off

[*.json]
indent_size = 2
```

**Detekt Configuration (`detekt.yml`):**
```yaml
# Detekt configuration for OpenTile Mobile
# See: https://detekt.dev/docs/introduction/configurations

build:
  maxIssues: 0
  excludeCorrectable: false

config:
  validation: true
  warningsAsErrors: false

console-reports:
  active: true

output-reports:
  active: true
  exclude:
    - 'HtmlOutputReport'

complexity:
  active: true
  LongMethod:
    active: true
    threshold: 40
  LongParameterList:
    active: true
    functionThreshold: 6
  ComplexMethod:
    active: true
    threshold: 15

formatting:
  active: true
  android: true
  autoCorrect: true

naming:
  active: true
  FunctionNaming:
    active: true
  ClassNaming:
    active: true

performance:
  active: true

potential-bugs:
  active: true

style:
  active: true
  MagicNumber:
    active: true
    ignoreNumbers:
      - '-1'
      - '0'
      - '1'
      - '2'
  MaxLineLength:
    active: true
    maxLineLength: 120
```

**`.gitignore`:**
```gitignore
# Gradle
.gradle/
build/
!gradle/wrapper/gradle-wrapper.jar

# Android
local.properties
*.apk
*.aab
*.ap_
*.dex
captures/
.externalNativeBuild/
.cxx/

# iOS
*.xcodeproj/*
!*.xcodeproj/project.pbxproj
!*.xcodeproj/xcshareddata/
DerivedData/
*.xcworkspace/
!*.xcworkspace/contents.xcworkspacedata
*.hmap
*.ipa
*.dSYM.zip
*.dSYM
.swiftpm/

# IDEs
.idea/
*.iml
.vscode/
*.swp
*.swo
*~

# Kotlin
*.class
*.log
*.jar
*.war
*.ear

# Misc
.DS_Store
*.orig
*.rej
*.bak
*.tmp
*.temp

# mise
.mise.local.toml

# Lefthook
.lefthook-local.yml

# Test results
test-results/
*.test
```

### 8.5 Project Initialization Checklist

**Step 1: Install Tools**
```bash
# Install mise
brew install mise  # or curl https://mise.run | sh

# Install lefthook
brew install lefthook  # or mise use -g lefthook

# Configure mise
mise install
mise run setup
```

**Step 2: Initialize Git Hooks**
```bash
# Install lefthook hooks
lefthook install

# Test hooks
lefthook run pre-commit
```

**Step 3: Verify Setup**
```bash
# Check tool versions
mise list
gradle --version
java -version

# Check Gradle configuration
./gradlew --version
./gradlew tasks

# Run linters
./gradlew ktlintCheck
./gradlew detekt

# Run tests
./gradlew test
```

**Step 4: First Build**
```bash
# Build Android
./gradlew :androidApp:assembleDebug

# Build iOS (macOS only)
cd iosApp
xcodebuild -workspace iosApp.xcworkspace -scheme iosApp -sdk iphonesimulator
```

**Step 5: IDE Setup**

**Android Studio:**
1. Open project root
2. Wait for Gradle sync
3. Trust `.editorconfig` settings
4. Install plugins:
   - Kotlin Multiplatform Mobile (KMM)
   - Detekt
   - EditorConfig

**Xcode (iOS):**
1. Open `iosApp/iosApp.xcworkspace`
2. Select simulator/device
3. Build & Run

### 8.6 Best Practices Summary

**Version Management:**
- ✅ Use mise for all tool versions (Java, Gradle, Node)
- ✅ Commit `.mise.toml` to version control
- ✅ Never install tools globally (use mise)
- ✅ Document tool versions in README

**Git Hooks:**
- ✅ Use lefthook for all pre-commit/pre-push hooks
- ✅ Run fast checks on pre-commit (lint, unit tests)
- ✅ Run full suite on pre-push (integration tests, build)
- ✅ Allow emergency bypass with `LEFTHOOK=0` (document usage)

**Gradle:**
- ✅ Use version catalog (`libs.versions.toml`) for all dependencies
- ✅ Enable Gradle cache and configuration cache
- ✅ Use Kotlin DSL (`.gradle.kts`) not Groovy
- ✅ Parallelize builds (`org.gradle.parallel=true`)

**Code Quality:**
- ✅ Auto-format on save (ktlint)
- ✅ Run detekt before committing
- ✅ Maintain test coverage >80% (use Kover)
- ✅ Follow Conventional Commits format

**Development Workflow:**
1. Create feature branch
2. Write code + tests
3. Run `mise run lint` (ktlint + detekt)
4. Commit (lefthook runs checks)
5. Push (lefthook runs full suite)
6. Create PR (CI runs additional checks)

---

## 9. Implementation Roadmap

### Phase 0: Foundation (Weeks 1-2) ✅ COMPLETED 2026-01-08

**Goal:** Setup KMP project, mode system, basic Compose UI

- [x] Create KMP project structure (shared, androidApp, iosApp)
- [x] Setup Gradle configuration (version catalog, dependencies)
- [x] Implement AppMode sealed class + navigation
- [x] Basic Compose UI layouts for each mode
- [x] Mode transition animations (fade in/out)
- [x] Event bus (SharedFlow) infrastructure
- [x] State management (StateFlow)
- [x] Logging setup (Kermit)
- [x] DI setup (Koin)

**Deliverable:** ✅ Can switch between modes, UI layouts change, logging works

**Additional achievements:**
- [x] Project Browser with file persistence (iOS: NSFileManager, Android: java.io.File)
- [x] Settings screen with Imperial/Metric unit preference
- [x] expect/actual pattern for platform-specific code (UUID, FileStorage)
- [x] Taskfile.yml for build automation
- [x] iOS deployment target: 17.2 (Compose Multiplatform requirement)
- [x] CADisableMinimumFrameDurationOnPhone for high refresh rate support

**Implementation Details:** See `.claude/plans/main-menu-implementation.md`

### Phase 1: Drawing Mode - Touch Gestures (Weeks 3-5)

**Goal:** Touch-optimized CAD drawing with smart snapping

- [ ] Event system (GeometryEvent, TopologyEvent sealed classes)
- [ ] Gesture detection (tap, long press, pan, pinch)
- [ ] Wall tool (tap to place points, continuous drawing)
- [ ] GeometryManager (listen to events, update state)
- [ ] Smart snapping (grid, vertex, perpendicular)
- [ ] Spatial index (R-tree for fast queries)
- [ ] Drawing canvas (Skia-based rendering)
- [ ] Camera transform (pan, zoom with gestures)
- [ ] Door/window tools (place on walls)
- [ ] Visual feedback (snap indicators, selection highlights)

**Deliverable:** Can draw rooms with walls, doors, windows using touch gestures

### Phase 2: Topology & BREP (Weeks 6-7)

**Goal:** Automatic room detection with halfedge mesh

- [ ] Halfedge mesh data structures (Vertex, HalfEdge, Face)
- [ ] TopologyBuilder (convert edges → halfedge mesh)
- [ ] Face detection (traverse halfedges to find rooms)
- [ ] Room component (name, type, area, perimeter)
- [ ] Room auto-labeling
- [ ] Wall auto-connection (T-joints, L-corners)
- [ ] Immutable state updates for topology

**Deliverable:** Rooms auto-detected, stable across edits

### Phase 3: Command Pattern & Undo/Redo (Weeks 8-9)

**Goal:** Full parametric history with feature tree

- [ ] Command interface (execute, undo, name)
- [ ] CommandHistory (undo/redo stacks)
- [ ] CreateWallCommand implementation
- [ ] Gesture-based undo/redo (three-finger swipe)
- [ ] Feature tree data structure
- [ ] Feature tree UI (list of operations)
- [ ] Rollback functionality
- [ ] Feature suppression (hide without deleting)

**Deliverable:** Undo/redo works, feature tree displays operations

### Phase 4: Material Planning Mode (Weeks 10-13)

**Goal:** Tile layout optimization with live preview

- [ ] Material database (tiles, flooring, paint)
- [ ] Room-to-material assignment UI
- [ ] Tile layout generator (straight, diagonal, herringbone)
- [ ] Layout optimizer (score by waste, cuts, symmetry)
- [ ] Tile entities (position, rotation, cut type)
- [ ] Live preview rendering (tile overlay on room)
- [ ] Material quantity rollup
- [ ] Cut list generation
- [ ] Swipe gestures to cycle through layout options

**Deliverable:** Can assign tile pattern to room, optimize layout, see waste %

### Phase 5: Constraint System (Weeks 14-15)

**Goal:** Parametric constraints with inference

- [ ] Constraint data classes (Distance, Perpendicular, Parallel)
- [ ] ConstraintSolver (iterative solver)
- [ ] Constraint inference engine (auto-detect patterns)
- [ ] Constraint UI (visual indicators, suggestion panel)
- [ ] Apply/dismiss suggestions

**Deliverable:** Constraints auto-suggested and applied

### Phase 6: Building Code Validation (Weeks 16-17)

**Goal:** Automatic code compliance checking

- [ ] Building code rules database (IRC/IBC)
- [ ] Validation systems (door clearance, toilet clearance, egress, etc.)
- [ ] Code violation data structures
- [ ] Violation UI panel (errors, warnings, suggestions)
- [ ] Visual indicators (red highlights on violations)

**Deliverable:** Code violations show in real-time

### Phase 7: Estimation & Export (Weeks 18-19)

**Goal:** Professional quote generation

- [ ] Estimation mode UI
- [ ] Material cost database
- [ ] Labor rate database
- [ ] Labor estimation (area × rate × complexity)
- [ ] Quote generation (line items, subtotals, markup)
- [ ] PDF export (shared library for PDF generation)
- [ ] iOS Share Sheet integration
- [ ] Android Share integration
- [ ] Professional formatting

**Deliverable:** Can generate client-ready quote PDF, share via email/print

### Phase 8: Platform Polish (Weeks 20-22)

**iOS-Specific:**
- [ ] Apple Pencil integration (pressure, tilt)
- [ ] iOS navigation patterns (swipe back, etc.)
- [ ] iCloud sync setup
- [ ] Dark mode support
- [ ] iPad split-screen optimization

**Android-Specific:**
- [ ] S Pen support (Samsung)
- [ ] Material Design 3 theming
- [ ] Google Drive sync setup
- [ ] Android permissions (camera, storage)
- [ ] Foldable device optimization

**Deliverable:** Platform-specific features polished

### Phase 9: Performance & Testing (Weeks 23-24)

**Goal:** Production-ready MVP

- [ ] Performance optimization (spatial indexing, rendering LOD)
- [ ] Comprehensive testing (unit, integration, UI tests)
- [ ] User testing with contractor
- [ ] Bug fixes
- [ ] Documentation (API docs, user guide)
- [ ] CI/CD setup (GitHub Actions)
- [ ] App Store submission (iOS)
- [ ] Play Store submission (Android)

**Deliverable:** Shippable MVP for contractor testing

### MVP Definition (End of Phase 7)

**Must Have:**
1. Draw room layouts (walls, doors, windows) with touch
2. Automatic room detection
3. Tile layout optimization (minimize waste)
4. Material quantity calculation
5. Cost estimation
6. PDF export (floor plan + material list + quote)
7. Offline-first (works without internet)

**Success Criteria:**
- Contractor can complete bathroom quote in < 15 minutes on-site
- Material estimates within 5% of actual
- Code violations caught (90%+ of common issues)
- 60 FPS on mid-range devices
- Contractor would pay subscription

---

## 9. Performance & Testing

### 9.1 Performance Targets

| Metric | Target | Strategy |
|--------|--------|----------|
| Frame Rate | 60 FPS | Spatial indexing, drawWithCache, immutable state |
| Entity Count | 1000+ | R-tree for queries, optimized rendering |
| Tile Optimization | < 1 sec | Limit candidates, early exit, suspend functions |
| Mode Transition | < 50 ms | Animated navigation, lazy loading |
| Touch Latency | < 16 ms | Direct pointer input, no intermediate layers |
| Model Rebuild | < 500 ms | Cache topology, incremental updates |
| App Launch | < 2 sec | Lazy initialization, deferred loading |

### 9.2 Optimization Strategies

**Spatial Indexing (R-tree):**
```kotlin
// O(log n) vs O(n) linear search
class SpatialIndex {
    private val rtree = RTree<Vertex>()

    fun nearbyVertices(point: Point2, radius: Double): List<Vertex> {
        return rtree.search(Rectangle.around(point, radius))
            .filter { it.position.distanceTo(point) <= radius }
    }
}
```

**Immutable State + Structural Sharing:**
```kotlin
// Only changed parts of state tree recompose
data class AppState(
    val vertices: PersistentMap<String, Vertex>, // kotlinx.collections.immutable
    val edges: PersistentMap<String, Edge>,
    // ...
) {
    fun withVertex(vertex: Vertex) = copy(
        vertices = vertices.put(vertex.id, vertex) // Structural sharing
    )
}
```

**DrawWithCache for Complex Rendering:**
```kotlin
@Composable
fun FloorPlanCanvas(state: AppState) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        // Rebuild paths only when faces change
        val cachedPaths = remember(state.faces) {
            buildFacePaths(state.faces)
        }

        drawWithCache {
            val pathPaint = Paint().apply {
                color = Color.LightGray
                style = PaintingStyle.Fill
            }

            onDrawBehind {
                cachedPaths.forEach { drawPath(it, pathPaint) }
            }
        }
    }
}
```

**Lazy Loading & Pagination:**
```kotlin
// Load projects lazily
@Composable
fun ProjectList(projects: List<Project>) {
    LazyColumn {
        items(
            items = projects,
            key = { it.id }
        ) { project ->
            ProjectCard(project)
        }
    }
}
```

### 9.3 Testing Strategy

**Unit Tests (commonTest):**
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

    @Test
    fun `point in polygon`() {
        val square = Polygon(listOf(
            Point2(0.0, 0.0),
            Point2(10.0, 0.0),
            Point2(10.0, 10.0),
            Point2(0.0, 10.0)
        ))

        assertTrue(square.contains(Point2(5.0, 5.0)))
        assertFalse(square.contains(Point2(15.0, 15.0)))
    }
}
```

**Flow Tests (commonTest):**
```kotlin
// Test event-driven architecture
class GeometryManagerTest {
    @Test
    fun `point placed event creates vertex`() = runTest {
        val eventBus = EventBus()
        val stateManager = StateManager()
        val geometryManager = GeometryManager(eventBus, stateManager)

        // Emit event
        eventBus.emit(GeometryEvent.PointPlaced(
            position = Point2(10.0, 20.0),
            snappedTo = null
        ))

        // Wait for processing
        advanceUntilIdle()

        // Verify vertex created
        assertEquals(1, stateManager.state.value.vertices.size)
    }
}
```

**UI Tests (androidInstrumentedTest / iosTest):**
```kotlin
// Android
@Test
fun testDrawWall() {
    composeTestRule.setContent {
        FloorPlanScreen()
    }

    // Tap to place first point
    composeTestRule.onNodeWithTag("drawing-canvas")
        .performTouchInput { click(Offset(100f, 100f)) }

    // Tap to place second point
    composeTestRule.onNodeWithTag("drawing-canvas")
        .performTouchInput { click(Offset(200f, 100f)) }

    // Verify wall created
    composeTestRule.onNodeWithText("Wall #1").assertExists()
}
```

**Workflow Tests:**
```kotlin
@Test
fun `complete room drawing workflow`() = runTest {
    val app = TestApp()

    // Navigate to Floor Plan mode
    app.navigateTo(AppMode.FloorPlan)

    // Draw 4 walls (rectangle)
    val points = listOf(
        Point2(0.0, 0.0),
        Point2(120.0, 0.0),
        Point2(120.0, 96.0),
        Point2(0.0, 96.0)
    )

    points.forEach { point ->
        app.tapAt(point)
    }

    // Close the loop
    app.tapAt(points.first())

    // Verify room detected
    assertEquals(1, app.state.rooms.size)
    assertEquals(120.0 * 96.0, app.state.rooms.values.first().area, 0.1)
}
```

---

## 10. References

### CAD Architecture
- [Event-Sourced Architecture for CAD](https://novedge.com/blogs/design-news/deterministic-event-sourced-architecture-for-real-time-collaborative-cad) - Deterministic, intent-aware modeling
- [BREP Topology](https://en.wikipedia.org/wiki/Boundary_representation) - Boundary representation fundamentals

### Kotlin Multiplatform
- [Kotlin Multiplatform in 2025](https://medium.com/design-bootcamp/kotlin-multiplatform-in-2025-how-android-developers-can-build-for-ios-too-0b63490d8b1c) - Overview of KMP ecosystem
- [KMP Roadmap Aug 2025](https://blog.jetbrains.com/kotlin/2025/08/kmp-roadmap-aug-2025/) - Future of KMP
- [Google KMP Support](https://android-developers.googleblog.com/2025/05/android-kotlin-multiplatform-google-io-kotlinconf-2025.html) - Official Google support

### Compose Multiplatform
- [Compose Multiplatform 1.8.0 Release](https://blog.jetbrains.com/kotlin/2025/05/compose-multiplatform-1-8-0-released-compose-multiplatform-for-ios-is-stable-and-production-ready/) - iOS stable release
- [Compose Multiplatform Rendering](https://medium.com/@sharmapraveen91/mastering-compose-multi-platform-rendering-under-the-hood-performance-and-best-practices-c3a8c785a0c9) - Performance best practices
- [Skia in Compose](https://medium.com/@sandeepkella23/skia-in-jetpack-compose-the-core-graphics-engine-77e4f34b6695) - Rendering architecture

### Constraint Systems
- [Constraint Generation & Design Intent](https://arxiv.org/html/2504.13178v1) - AI-driven constraint inference

### Command Pattern
- [Command Pattern for Undo/Redo](https://codezup.com/command-pattern-undo-redo-software/) - Implementation guide

### Mobile UI Patterns
- [Material Design 3](https://m3.material.io/) - Android design system
- [Human Interface Guidelines](https://developer.apple.com/design/human-interface-guidelines/) - iOS design principles

---

**END OF SPECIFICATION**

*This document is the authoritative technical reference for OpenTile Mobile development.*
*All implementation should follow these patterns and decisions.*
*Document maintained by: Technical Lead*
*Based on OpenTile Desktop spec (Bevy/Rust) - Adapted for KMP*
*Review cadence: Weekly during active development*
