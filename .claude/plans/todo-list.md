# Room Planner - Implementation Todo List

Generated: 2026-01-08

## Phase 1: Cleanup
- [x] Delete empty opentile directories (none found)
- [ ] Update documentation comments (OpenTile → Room Planner)

## Phase 2: Dependencies
- [ ] Add kotlinx-datetime dependency
- [ ] Re-enable dependencies in shared/build.gradle.kts

## Phase 3: Core Architecture
- [ ] Create AppMode sealed interface
- [ ] Create EventBus and AppEvent classes
- [ ] Create StateManager

## Phase 4: Data Models
- [ ] Create Settings and Project data models
- [ ] Create AppState model
- [ ] Create UUID expect/actual implementations

## Phase 5: File Storage
- [ ] Create FileStorage expect/actual (iOS & Android)

## Phase 6: Dependency Injection
- [ ] Setup Koin dependency injection modules
- [ ] Update iOSApp.swift with Koin initialization

## Phase 7: UI Screens
- [ ] Create ProjectBrowserScreen
- [ ] Create ProjectCard component
- [ ] Create CreateProjectDialog component
- [ ] Create SettingsScreen
- [ ] Create FloorPlanScreen placeholder
- [ ] Update App.kt with navigation controller

## Phase 8: Testing
- [ ] Build and test on iOS simulator

---

**Progress**: 1/20 tasks completed (5%)

**Current Task**: Updating documentation comments
