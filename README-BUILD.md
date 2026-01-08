# Building Room Planner

## Quick Start (iOS)

### Option 1: Using Task (Recommended)

```bash
# Install task runner (if not installed)
brew install go-task/tap/go-task

# Build shared framework and regenerate Xcode project
task build

# Or build and open Xcode in one command
task xcode
```

### Option 2: Manual Build

```bash
# Build the shared Kotlin framework
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64

# Regenerate Xcode project from project.yml
cd iosApp
xcodegen generate

# Open Xcode
open iosApp.xcodeproj
```

Then in Xcode:
1. Select "iosApp" scheme
2. Select an iPhone simulator (iPhone 17, iPhone 17 Pro, etc.)
3. Click Run (⌘R)

## Why This Build Step is Needed

The iOS app depends on the shared Kotlin Multiplatform framework. This framework must be built before Xcode can compile the iOS app. The `task build` command:

1. Compiles the Kotlin code to a native iOS framework
2. Places it in `shared/build/bin/iosSimulatorArm64/debugFramework/`
3. Regenerates the Xcode project to reference the framework

## Build Variants

### iOS Simulator (ARM64 - M1/M2/M3 Macs)
```bash
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```

### iOS Simulator (x64 - Intel Macs)
```bash
./gradlew :shared:linkDebugFrameworkIosX64
```

### iOS Device (Physical iPhone/iPad)
```bash
./gradlew :shared:linkDebugFrameworkIosArm64
```

## Troubleshooting

### "Cannot find 'KoinInitializerKt' in scope"

This means the Kotlin framework hasn't been built. Run:
```bash
task build:ios
# or
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```

Then clean and rebuild in Xcode (⇧⌘K, then ⌘B).

### Framework search path errors

The Xcode project is generated from `iosApp/project.yml`. If you modify it, regenerate:
```bash
cd iosApp && xcodegen generate
```

## Android Build

```bash
# Build debug APK
task build:android
# or
./gradlew :androidApp:assembleDebug

# Install on connected device/emulator
./gradlew :androidApp:installDebug
```

## Other Commands

```bash
task clean    # Clean all build artifacts
task test     # Run all tests
task lint     # Run ktlint + detekt
task format   # Auto-format code
```

## Continuous Development

When actively developing, keep the Kotlin framework up to date:

```bash
# Watch mode (rebuild on file changes) - not built-in, manual for now
# Just run this after making Kotlin changes:
task build:ios
```

Then use Xcode's normal build process (⌘B).
