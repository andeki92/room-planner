# Room Planner Main Menu & Navigation - Implementation Plan

**Goal**: Clean up opentile references and implement the main menu (Project Browser) with local file storage, settings, and mode-based navigation following the event-driven architecture from spec-mobile.md.

**User Decisions**:
- ✅ Local file storage from the start (not in-memory)
- ✅ Minimal project creation (just name)
- ✅ Include Settings screen with unit preference (Imperial/Metric)

---

## Overview

This implementation establishes the foundational architecture patterns that will be used throughout the app:
1. **Event-driven architecture** - EventBus with Kotlin Flows
2. **Mode-based navigation** - Sealed classes for type-safe mode switching
3. **Immutable state** - StateFlow with data classes
4. **Platform-specific I/O** - expect/actual for file storage

---

## Phase 1: Cleanup

### 1.1 Delete Empty Directories

Remove orphaned `com.opentile` package structure (all empty):

```bash
rm -rf shared/src/commonMain/kotlin/com/opentile
rm -rf shared/src/iosMain/kotlin/com/opentile
rm -rf shared/src/androidMain/kotlin/com/opentile
rm -rf shared/src/commonTest/kotlin/com/opentile
```

### 1.2 Update Documentation Comments

**Find and replace** "OpenTile Mobile" → "Room Planner" in:
- `README.md` (title, references)
- `CLAUDE.md` (header, package examples)
- `spec-mobile.md` (title, examples - optional)
- `build.gradle.kts` (line 1 comment)
- `shared/build.gradle.kts` (line 1 comment)
- `settings.gradle.kts` (line 1 comment)
- `androidApp/build.gradle.kts` (line 1 comment)
- `gradle/libs.versions.toml` (line 1 comment)

**Note**: Only update comments/docs. Code already uses `com.roomplanner`.

---

## Phase 2: Dependencies

### 2.1 Add Missing Dependency

**File**: `gradle/libs.versions.toml`

Add to `[versions]` section:
```toml
kotlinx-datetime = "0.6.1"
```

Add to `[libraries]` section:
```toml
kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version.ref = "kotlinx-datetime" }
```

### 2.2 Update shared/build.gradle.kts

Add to `commonMain.dependencies`:
```kotlin
implementation(libs.kotlinx.datetime)
implementation(libs.kotlinx.serialization.json)
implementation(libs.kotlinx.coroutines.core)
implementation(libs.kermit)
implementation(libs.uuid)
implementation(libs.koin.core)
implementation(libs.koin.compose)
```

Most are already there; just ensure all are present.

---

## Phase 3: Core Architecture

### 3.1 AppMode (Type-Safe Navigation)

**File**: `shared/src/commonMain/kotlin/com/roomplanner/data/models/AppMode.kt`

```kotlin
package com.roomplanner.data.models

import kotlinx.serialization.Serializable

@Serializable
sealed interface AppMode {
    @Serializable
    data object ProjectBrowser : AppMode

    @Serializable
    data class FloorPlan(val projectId: String) : AppMode

    @Serializable
    data object Settings : AppMode
}
```

### 3.2 Event System

**File**: `shared/src/commonMain/kotlin/com/roomplanner/data/events/EventBus.kt`

```kotlin
package com.roomplanner.data.events

import kotlinx.coroutines.flow.*

class EventBus {
    private val _events = MutableSharedFlow<AppEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<AppEvent> = _events.asSharedFlow()

    suspend fun emit(event: AppEvent) = _events.emit(event)
}
```

**File**: `shared/src/commonMain/kotlin/com/roomplanner/data/events/AppEvent.kt`

```kotlin
package com.roomplanner.data.events

import com.roomplanner.data.models.AppMode
import com.roomplanner.data.models.MeasurementUnits

sealed interface AppEvent

sealed interface NavigationEvent : AppEvent {
    data class ModeChanged(val mode: AppMode) : NavigationEvent
}

sealed interface ProjectEvent : AppEvent {
    data class Created(val project: com.roomplanner.data.models.Project) : ProjectEvent
    data class Deleted(val projectId: String) : ProjectEvent
}

sealed interface SettingsEvent : AppEvent {
    data class UnitsChanged(val units: MeasurementUnits) : SettingsEvent
}
```

### 3.3 State Management

**File**: `shared/src/commonMain/kotlin/com/roomplanner/data/StateManager.kt`

```kotlin
package com.roomplanner.data

import com.roomplanner.data.models.*
import kotlinx.coroutines.flow.*

class StateManager {
    private val _state = MutableStateFlow(AppState.initial())
    val state: StateFlow<AppState> = _state.asStateFlow()

    fun updateState(reducer: (AppState) -> AppState) {
        _state.update(reducer)
    }

    fun setMode(mode: AppMode) = updateState { it.copy(currentMode = mode) }
    fun updateSettings(settings: Settings) = updateState { it.copy(settings = settings) }
}
```

---

## Phase 4: Data Models

### 4.1 Settings

**File**: `shared/src/commonMain/kotlin/com/roomplanner/data/models/Settings.kt`

```kotlin
package com.roomplanner.data.models

import kotlinx.serialization.Serializable

@Serializable
data class Settings(
    val measurementUnits: MeasurementUnits = MeasurementUnits.IMPERIAL
) {
    companion object {
        fun default() = Settings()
    }
}

@Serializable
enum class MeasurementUnits {
    IMPERIAL,  // feet, inches
    METRIC     // meters, centimeters
}
```

### 4.2 Project

**File**: `shared/src/commonMain/kotlin/com/roomplanner/data/models/Project.kt`

```kotlin
package com.roomplanner.data.models

import com.roomplanner.common.generateUUID
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Project(
    val id: String,
    val name: String,
    val createdAt: Instant,
    val modifiedAt: Instant
) {
    companion object {
        fun create(name: String): Project {
            val now = Clock.System.now()
            return Project(
                id = generateUUID(),
                name = name,
                createdAt = now,
                modifiedAt = now
            )
        }
    }
}
```

### 4.3 AppState

**File**: `shared/src/commonMain/kotlin/com/roomplanner/data/models/AppState.kt`

```kotlin
package com.roomplanner.data.models

import kotlinx.serialization.Serializable

@Serializable
data class AppState(
    val currentMode: AppMode = AppMode.ProjectBrowser,
    val settings: Settings = Settings.default(),
    val currentProjectId: String? = null
) {
    companion object {
        fun initial() = AppState()
    }
}
```

### 4.4 UUID Generation (Platform-Specific)

**File**: `shared/src/commonMain/kotlin/com/roomplanner/common/UUID.kt`

```kotlin
package com.roomplanner.common

expect fun generateUUID(): String
```

**File**: `shared/src/iosMain/kotlin/com/roomplanner/common/UUID.ios.kt`

```kotlin
package com.roomplanner.common

import platform.Foundation.NSUUID

actual fun generateUUID(): String = NSUUID().UUIDString()
```

**File**: `shared/src/androidMain/kotlin/com/roomplanner/common/UUID.android.kt`

```kotlin
package com.roomplanner.common

actual fun generateUUID(): String = java.util.UUID.randomUUID().toString()
```

---

## Phase 5: File Storage (Platform-Specific)

### 5.1 Common Interface

**File**: `shared/src/commonMain/kotlin/com/roomplanner/data/storage/FileStorage.kt`

```kotlin
package com.roomplanner.data.storage

import com.roomplanner.data.models.Project
import com.roomplanner.data.models.Settings

expect class FileStorage {
    suspend fun saveProject(project: Project): Result<Unit>
    suspend fun loadProject(projectId: String): Result<Project>
    suspend fun listProjects(): Result<List<Project>>
    suspend fun deleteProject(projectId: String): Result<Unit>
    suspend fun saveSettings(settings: Settings): Result<Unit>
    suspend fun loadSettings(): Result<Settings>
}
```

### 5.2 iOS Implementation

**File**: `shared/src/iosMain/kotlin/com/roomplanner/data/storage/FileStorage.ios.kt`

Uses `NSFileManager` to store projects in Documents directory:
- Projects: `Documents/projects/<uuid>/metadata.json`
- Settings: `Documents/settings.json`

Key points:
- Use `NSFileManager.defaultManager`
- Use `URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)`
- Create directories with `createDirectoryAtURL`
- Write JSON with `NSString.writeToFile`
- Parse with `kotlinx.serialization`

**See detailed implementation in Plan agent output above** (lines ~570-650).

### 5.3 Android Implementation

**File**: `shared/src/androidMain/kotlin/com/roomplanner/data/storage/FileStorage.android.kt`

Uses `java.io.File` with app-private storage:
- Requires `Context` parameter (provided by Koin)
- Projects: `filesDir/projects/<uuid>/metadata.json`
- Settings: `filesDir/settings.json`

**See detailed implementation in Plan agent output above** (lines ~660-710).

---

## Phase 6: Dependency Injection

### 6.1 Common Module

**File**: `shared/src/commonMain/kotlin/com/roomplanner/di/AppModule.kt`

```kotlin
package com.roomplanner.di

import com.roomplanner.data.EventBus
import com.roomplanner.data.StateManager
import org.koin.dsl.module

val commonModule = module {
    single { EventBus() }
    single { StateManager() }
}
```

### 6.2 Platform Module (expect/actual)

**File**: `shared/src/commonMain/kotlin/com/roomplanner/di/PlatformModule.kt`

```kotlin
package com.roomplanner.di

import org.koin.core.module.Module

expect val platformModule: Module
```

**File**: `shared/src/iosMain/kotlin/com/roomplanner/di/PlatformModule.ios.kt`

```kotlin
package com.roomplanner.di

import com.roomplanner.data.storage.FileStorage
import org.koin.dsl.module

actual val platformModule = module {
    single { FileStorage() }
}
```

**File**: `shared/src/androidMain/kotlin/com/roomplanner/di/PlatformModule.android.kt`

```kotlin
package com.roomplanner.di

import com.roomplanner.data.storage.FileStorage
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

actual val platformModule = module {
    single { FileStorage(androidContext()) }
}
```

### 6.3 Koin Initializer

**File**: `shared/src/commonMain/kotlin/com/roomplanner/di/KoinInitializer.kt`

```kotlin
package com.roomplanner.di

import org.koin.core.context.startKoin

fun initKoin() {
    startKoin {
        modules(commonModule, platformModule)
    }
}
```

**File**: `shared/src/iosMain/kotlin/com/roomplanner/di/KoinInitializer.ios.kt`

```kotlin
package com.roomplanner.di

fun doInitKoin() = initKoin()
```

### 6.4 Initialize in iOS App

**File**: `iosApp/iosApp/iOSApp.swift`

```swift
import SwiftUI
import shared

@main
struct iOSApp: App {
    init() {
        KoinInitializerKt.doInitKoin()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
```

---

## Phase 7: UI Screens

### 7.1 Root Navigation

**File**: `shared/src/commonMain/kotlin/com/roomplanner/App.kt`

Replace existing "Hello, Room Planner!" with navigation controller:

```kotlin
package com.roomplanner

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import com.roomplanner.data.*
import com.roomplanner.data.events.NavigationEvent
import com.roomplanner.data.models.AppMode
import com.roomplanner.ui.screens.*
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import co.touchlab.kermit.Logger

@Composable
fun App() {
    val stateManager: StateManager = koinInject()
    val eventBus: EventBus = koinInject()
    val scope = rememberCoroutineScope()
    val appState by stateManager.state.collectAsState()

    // Listen to navigation events
    LaunchedEffect(Unit) {
        eventBus.events
            .filterIsInstance<NavigationEvent.ModeChanged>()
            .collect { event ->
                Logger.i { "Mode: ${appState.currentMode} → ${event.mode}" }
                stateManager.setMode(event.mode)
            }
    }

    MaterialTheme {
        AnimatedContent(
            targetState = appState.currentMode,
            transitionSpec = {
                fadeIn(tween(300)) togetherWith fadeOut(tween(300))
            }
        ) { mode ->
            when (mode) {
                is AppMode.ProjectBrowser -> ProjectBrowserScreen(
                    onNavigate = { scope.launch { eventBus.emit(NavigationEvent.ModeChanged(it)) } }
                )
                is AppMode.FloorPlan -> FloorPlanScreen(
                    projectId = mode.projectId,
                    onNavigate = { scope.launch { eventBus.emit(NavigationEvent.ModeChanged(it)) } }
                )
                is AppMode.Settings -> SettingsScreen(
                    onNavigate = { scope.launch { eventBus.emit(NavigationEvent.ModeChanged(it)) } }
                )
            }
        }
    }
}
```

### 7.2 Project Browser Screen

**File**: `shared/src/commonMain/kotlin/com/roomplanner/ui/screens/ProjectBrowserScreen.kt`

Features:
- Top bar with "Room Planner" title + Settings icon
- Grid of project cards (LazyVerticalGrid)
- Empty state: "No projects yet" message
- FAB (+) button → CreateProjectDialog
- Load projects from FileStorage on launch
- Handle project tap → navigate to FloorPlan mode

**See full implementation in Plan agent output** (lines ~880-980).

### 7.3 Project Card Component

**File**: `shared/src/commonMain/kotlin/com/roomplanner/ui/components/ProjectCard.kt`

Card layout:
- Thumbnail placeholder (folder icon for now)
- Project name (Material3 typography)
- Modified date (formatted from kotlinx.datetime)
- Delete icon → confirmation dialog

**See full implementation in Plan agent output** (lines ~990-1070).

### 7.4 Create Project Dialog

**File**: `shared/src/commonMain/kotlin/com/roomplanner/ui/components/CreateProjectDialog.kt`

Simple AlertDialog:
- Text input for project name
- "Create" button (disabled if name blank)
- "Cancel" button

**See full implementation in Plan agent output** (lines ~1080-1120).

### 7.5 Settings Screen

**File**: `shared/src/commonMain/kotlin/com/roomplanner/ui/screens/SettingsScreen.kt`

Features:
- Back button → ProjectBrowser
- Radio buttons for Imperial/Metric
- Save to FileStorage when changed
- Update StateManager settings

**See full implementation in Plan agent output** (lines ~1130-1210).

### 7.6 Floor Plan Screen (Placeholder)

**File**: `shared/src/commonMain/kotlin/com/roomplanner/ui/screens/FloorPlanScreen.kt`

For now:
- Load project from FileStorage
- Show project name in top bar
- Back button → ProjectBrowser
- Placeholder text: "Floor Plan Mode - Drawing canvas coming in Phase 1"

**See full implementation in Plan agent output** (lines ~1220-1280).

---

## Phase 8: Testing

### 8.1 Build & Run

```bash
# Open Xcode project
open iosApp/iosApp.xcodeproj

# In Xcode:
# 1. Select "iosApp" scheme
# 2. Select iPhone 15 simulator
# 3. Click Run (⌘R)
```

### 8.2 Manual Test Checklist

**First Launch**:
- [ ] App opens to Project Browser (empty state)
- [ ] "No projects yet" message visible
- [ ] Settings icon in top bar
- [ ] FAB (+) button visible

**Create Project**:
- [ ] Tap FAB → Dialog opens
- [ ] Enter name → "Create" enabled
- [ ] Tap "Create" → Project appears in grid
- [ ] Project card shows name + date

**Open Project**:
- [ ] Tap project → FloorPlan screen opens
- [ ] Back button returns to browser

**Settings**:
- [ ] Tap Settings → Settings screen
- [ ] Change units → Radio button updates
- [ ] Back → Returns to browser

**Persistence**:
- [ ] Force quit app
- [ ] Relaunch → Projects still there
- [ ] Settings preserved

**Delete**:
- [ ] Tap delete on card → Confirmation
- [ ] Confirm → Project removed

### 8.3 Expected Console Logs

```
✓ Phase 0: ProjectBrowser screen initialized
Loaded 0 projects
✓ Project created: Bathroom Renovation
✓ Project saved: Bathroom Renovation (uuid-123)
Mode: ProjectBrowser → FloorPlan(uuid-123)
✓ Project loaded: Bathroom Renovation
Mode: FloorPlan(uuid-123) → Settings
✓ Settings saved: Units = METRIC
```

---

## Architecture Highlights

### Event-Driven Pattern
- Tools/UI emit events via EventBus
- Systems listen and react (StateManager, FileStorage)
- Decoupled, testable, follows spec

### Mode-Based Navigation
- Sealed interface for type-safety
- Can carry data (e.g., FloorPlan(projectId))
- Exhaustive when expressions

### Immutable State
- Data classes with copy semantics
- StateFlow for reactive updates
- Compose integration via collectAsState()

### Platform-Specific I/O
- expect/actual for FileStorage
- iOS: NSFileManager (Documents directory)
- Android: java.io.File (app-private storage)
- JSON serialization with kotlinx.serialization

---

## Success Criteria

✅ Complete when:
- All opentile references removed
- Empty directories deleted
- Project Browser works with file persistence
- Settings screen with unit preference
- Mode-based navigation functional
- Event-driven architecture in place
- Kermit logging shows transitions
- No crashes or errors

---

## Critical Files Overview

**Architecture Foundation**:
- `App.kt` - Navigation controller demonstrating event-driven mode switching
- `AppMode.kt` - Sealed interface for type-safe modes
- `EventBus.kt` - Event system using Kotlin Flows
- `StateManager.kt` - Immutable state with StateFlow

**Data Models**:
- `Project.kt` - Project data with kotlinx.datetime
- `Settings.kt` - User preferences with MeasurementUnits enum
- `AppState.kt` - Global app state

**Platform-Specific**:
- `FileStorage.kt` (common) - expect class interface
- `FileStorage.ios.kt` - NSFileManager implementation
- `FileStorage.android.kt` - java.io.File implementation
- `UUID.kt` - expect/actual for UUID generation

**UI Screens**:
- `ProjectBrowserScreen.kt` - Main menu with project grid
- `SettingsScreen.kt` - Unit preference selector
- `FloorPlanScreen.kt` - Placeholder for Phase 1

**DI Setup**:
- `AppModule.kt` - Common dependencies (EventBus, StateManager)
- `PlatformModule.kt` - Platform-specific (FileStorage)
- `KoinInitializer.kt` - Startup initialization

---

## File Structure After Implementation

```
shared/src/
├── commonMain/kotlin/com/roomplanner/
│   ├── App.kt                          # Root navigation
│   ├── common/
│   │   └── UUID.kt                     # expect fun
│   ├── data/
│   │   ├── StateManager.kt
│   │   ├── events/
│   │   │   ├── EventBus.kt
│   │   │   └── AppEvent.kt
│   │   ├── models/
│   │   │   ├── AppMode.kt
│   │   │   ├── AppState.kt
│   │   │   ├── Project.kt
│   │   │   └── Settings.kt
│   │   └── storage/
│   │       └── FileStorage.kt          # expect class
│   ├── di/
│   │   ├── AppModule.kt
│   │   ├── PlatformModule.kt           # expect val
│   │   └── KoinInitializer.kt
│   └── ui/
│       ├── screens/
│       │   ├── ProjectBrowserScreen.kt
│       │   ├── FloorPlanScreen.kt
│       │   └── SettingsScreen.kt
│       └── components/
│           ├── ProjectCard.kt
│           └── CreateProjectDialog.kt
│
├── iosMain/kotlin/com/roomplanner/
│   ├── common/UUID.ios.kt              # NSUUID
│   ├── data/storage/FileStorage.ios.kt # NSFileManager
│   ├── di/
│   │   ├── PlatformModule.ios.kt
│   │   └── KoinInitializer.ios.kt
│   └── MainViewController.kt           # (existing)
│
└── androidMain/kotlin/com/roomplanner/
    ├── common/UUID.android.kt          # java.util.UUID
    ├── data/storage/FileStorage.android.kt # java.io.File
    └── di/PlatformModule.android.kt
```

---

## Next Steps After Implementation

**Phase 1** will build on this foundation:
- Implement drawing canvas in FloorPlanScreen
- Add geometry primitives (Point2, Line, Polygon)
- Implement smart snapping system
- Add wall/door/window tools
- Expand Project model to include vertices/edges/faces

This Phase 0 implementation establishes the architectural patterns that will be used throughout the app.
