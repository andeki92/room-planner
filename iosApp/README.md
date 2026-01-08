# Room Planner iOS App

## Setup

The iOS app is configured to use the shared Kotlin Multiplatform framework.

### Prerequisites

1. Xcode 16.2 or later
2. iOS Simulator installed (Xcode > Settings > Platforms)
3. Gradle (managed by mise)

### Building the iOS App

1. **Build the shared framework first:**
   ```bash
   cd ..
   ./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
   ```

2. **Open the project in Xcode:**
   ```bash
   open iosApp.xcodeproj
   ```

3. **Select a simulator** (iPhone 15 Pro or any available device)

4. **Build and Run** (⌘R)

### Project Structure

- `iosApp/` - iOS application Swift code
  - `iOSApp.swift` - Main app entry point
  - `ContentView.swift` - SwiftUI view that hosts the Compose UI
  - `Info.plist` - iOS app configuration
- `project.yml` - XcodeGen configuration (used to generate the Xcode project)

### Regenerating the Xcode Project

If you need to regenerate the Xcode project:

```bash
cd iosApp
xcodegen generate
```

### Current Status

✅ Kotlin Multiplatform shared framework configured
✅ iOS app structure created
✅ Xcode project generated
✅ Simple "Hello, Room Planner!" UI implemented

### Next Steps

To test on your physical iPhone:
1. Connect your iPhone to your Mac
2. Open `iosApp.xcodeproj` in Xcode
3. Select your iPhone as the destination
4. Click Run (⌘R)

The app will display "Hello, Room Planner!" in the center of the screen using Compose Multiplatform UI.
