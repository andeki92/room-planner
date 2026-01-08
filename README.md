# OpenTile Mobile

**Professional Renovation Planning CAD Tool for iOS & Android**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.0-blue.svg)](https://kotlinlang.org)
[![Compose Multiplatform](https://img.shields.io/badge/Compose%20Multiplatform-1.8.0-green.svg)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![iOS](https://img.shields.io/badge/iOS-15.0+-000000.svg)](https://www.apple.com/ios/)
[![Android](https://img.shields.io/badge/Android-24+-3DDC84.svg)](https://www.android.com/)

> **🚧 Project Status:** In Development - Phase 0 (Foundation)

OpenTile Mobile brings professional CAD-quality renovation planning to iOS and Android devices. Built with Kotlin Multiplatform and Compose Multiplatform, it enables contractors to create accurate floor plans, optimize tile layouts, generate material estimates, and produce professional quotes—all on-site from their phone or tablet.

## ✨ Key Features

### 🎨 Touch-First CAD Drawing
- **Natural Drawing:** Optimized for fingers and Apple Pencil/S Pen
- **Smart Snapping:** Automatic snap to grid, vertices, perpendicular lines
- **Multi-Touch Gestures:** Pinch-zoom, pan, rotate with fluid 60 FPS performance
- **Undo/Redo:** Three-finger swipe for quick undo/redo

### 🏗️ Professional Floor Plans
- **Auto Room Detection:** Halfedge mesh topology (BREP) for stable room identities
- **Parametric History:** Edit past operations, everything downstream recalculates
- **Building Code Validation:** Automatic IRC/IBC compliance checking
- **Constraints:** Parallel, perpendicular, distance constraints with auto-inference

### 🔲 Tile Layout Optimization
- **Minimize Waste:** AI-optimized starting positions (10-20% savings)
- **Multiple Patterns:** Straight, diagonal, running bond, herringbone, chevron
- **Live Preview:** See tile overlay on floor plan in real-time
- **Cut Lists:** Detailed cutting instructions for installers

### 💰 Instant Quotes
- **Material Estimates:** Accurate quantities within 5% of actual usage
- **Labor Calculation:** Area-based with complexity adjustments
- **PDF Export:** Professional quotes via iOS/Android share sheet
- **Offline-First:** Work without internet, sync later

## 🏛️ Architecture

OpenTile Mobile uses **industry-proven architectural patterns** adapted from the desktop version:

### Event-Driven Architecture (Kotlin Flows)
```
User Touch → Tool emits Event → Event Bus → Systems React → UI Updates
```

- **Decoupled:** Tools don't mutate state directly
- **Testable:** Emit events, verify responses
- **Undo/Redo:** Replay event history

### Mode-Based UI (Sealed Classes)
```kotlin
sealed interface AppMode {
    data object FloorPlan
    data object MaterialPlanning
    data object Estimation
    // ...
}
```

- **Performance:** Mode-specific systems only run when needed
- **Type-Safe:** Compile-time guarantees for navigation

### BREP Topology (Halfedge Mesh)
```
Vertex → HalfEdge → Face (Room)
```

- **Stable IDs:** Rooms persist across edits
- **Fast Queries:** O(1) boundary traversal
- **Professional Standard:** Used in CAD systems worldwide

### Immutable State (Data Classes)
```kotlin
data class AppState(
    val vertices: Map<String, Vertex>,
    val edges: Map<String, Edge>,
    // ... structural sharing for performance
)
```

- **Predictable:** No unexpected mutations
- **Composable:** Integrates seamlessly with Compose
- **Debuggable:** Easy to inspect and test

## 🛠️ Technology Stack

| Component | Technology | Why |
|-----------|-----------|-----|
| **Language** | Kotlin 2.2.0+ | Latest stable, K2 compiler, shared iOS/Android code |
| **UI Framework** | Compose Multiplatform 1.8.0+ | Declarative UI, iOS stable (May 2025), Skia rendering |
| **Rendering** | Skiko (Skia) | Hardware-accelerated 2D (Metal on iOS, OpenGL on Android) |
| **State** | Kotlin Flows + StateFlow | Reactive, coroutine-native |
| **Serialization** | kotlinx.serialization | Type-safe, multiplatform |
| **DI** | Koin | Simple, KMP-compatible |
| **Logging** | Kermit | Multiplatform logging |

### Why Kotlin Multiplatform?

✅ **100% Shared Business Logic:** Geometry, topology, constraints, optimization
✅ **Platform-Specific UI:** Native iOS/Android feel
✅ **Production-Ready:** iOS stable as of May 2025, Google official support
✅ **Performance:** Skia rendering (same as Flutter), 60 FPS on mid-range devices
✅ **Type-Safe:** Compile-time guarantees, no runtime surprises

## 📁 Project Structure

```
opentile-mobile/
├── shared/                      # Kotlin Multiplatform (100% shared)
│   ├── commonMain/              # All business logic
│   │   ├── domain/              # Geometry, topology, constraints
│   │   ├── data/                # State, events, commands
│   │   ├── presentation/        # ViewModels
│   │   └── ui/                  # Compose Multiplatform UI
│   ├── iosMain/                 # iOS-specific (Apple Pencil, etc.)
│   ├── androidMain/             # Android-specific (S Pen, etc.)
│   └── commonTest/              # Shared tests
├── androidApp/                  # Android app wrapper
└── iosApp/                      # iOS app wrapper (Xcode)
```

**Code Sharing:** ~95% shared (business logic + UI), ~5% platform-specific (sensors, permissions)

## 🚀 Getting Started

### Prerequisites

- **macOS** (for iOS development) or **Linux/Windows** (Android only)
- **Android Studio Meerkat** (2025.1.1+)
- **Xcode 16+** (for iOS, macOS only)

### Setup (Automated with mise + lefthook)

**Step 1: Install mise** (version manager)
```bash
# macOS
brew install mise

# Linux
curl https://mise.run | sh

# Add to shell (~/.zshrc or ~/.bashrc)
eval "$(mise activate zsh)"  # or bash
```

**Step 2: Install project tools**
```bash
cd opentile-mobile

# Install Java 17, Gradle 8.10.2, Node 22
mise install

# Verify versions
mise list
```

**Step 3: Install lefthook** (Git hooks)
```bash
# Install lefthook
brew install lefthook  # or: mise use -g lefthook

# Initialize Git hooks
lefthook install
```

**Step 4: Setup project**
```bash
# Run initial setup
mise run setup

# Verify everything works
mise run lint
mise run test
```

**Step 5: First build**
```bash
# Android
./gradlew :androidApp:installDebug

# iOS (macOS only)
cd iosApp
xcodebuild -workspace iosApp.xcworkspace -scheme iosApp -sdk iphonesimulator
```

### Running Tests

```bash
# Shared tests (unit tests)
./gradlew :shared:testDebugUnitTest

# Android instrumented tests
./gradlew :androidApp:connectedAndroidTest

# iOS tests (macOS only)
xcodebuild test -workspace iosApp.xcworkspace -scheme iosApp -destination 'platform=iOS Simulator,name=iPhone 15'
```

## 📖 Documentation

- **[spec-mobile.md](spec-mobile.md)** - Complete technical specification
- **[.claude/CLAUDE.md](.claude/CLAUDE.md)** - Development guidelines
- **[.claude/plans/](.claude/plans/)** - Phase-by-phase implementation plans

## 🛣️ Roadmap

### Phase 0: Foundation (Weeks 1-2) ← **Current Phase**
- [x] Project structure setup
- [ ] Mode system (sealed classes)
- [ ] Event bus (Flows)
- [ ] Basic Compose UI

### Phase 1: Drawing Mode (Weeks 3-5)
- [ ] Touch gesture detection
- [ ] Smart snapping system
- [ ] Drawing canvas (Skia)
- [ ] Wall/door/window tools

### Phase 2: Topology & BREP (Weeks 6-7)
- [ ] Halfedge mesh implementation
- [ ] Automatic room detection
- [ ] Room labeling

### Phase 3: Command Pattern (Weeks 8-9)
- [ ] Undo/redo system
- [ ] Feature tree
- [ ] Parametric history

### Phase 4: Material Planning (Weeks 10-13)
- [ ] Tile layout optimizer
- [ ] Live preview
- [ ] Cut list generation

### Phase 5-9: Constraints, Validation, Estimation, Polish
- See [spec-mobile.md § 8](spec-mobile.md#8-implementation-roadmap) for full roadmap

**MVP Target:** End of Phase 7 (Week 19)

## 🎯 Success Metrics

**Technical:**
- ✅ 60 FPS with 1000+ entities
- ✅ < 1 second tile optimization
- ✅ < 16ms touch latency
- ✅ 5% material estimate accuracy

**Business:**
- 🎯 < 15 minutes for on-site bathroom quote
- 🎯 10-15% waste reduction
- 🎯 90%+ code violations caught
- 🎯 Contractors willing to pay subscription

## 🤝 Contributing

This is a private project. See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## 📄 License

Proprietary - All Rights Reserved

---

**Built with ❤️ using Kotlin Multiplatform & Compose Multiplatform**

*Based on OpenTile Desktop (Bevy/Rust) - Adapted for mobile*
