# Baseline Profile for proFlow Launcher

## Overview

This directory contains the baseline profile generator for the proFlow Launcher. The baseline profile causes ART (Android Runtime) to AOT-compile critical launcher code paths, eliminating JIT warm-up jank on first launch and ensuring the home screen composes within 100ms on Snapdragon 6xx class devices.

## Critical Paths Covered

The baseline profile generator (`LauncherBaselineProfileGenerator.kt`) covers the following critical paths:

1. **LauncherActivity Cold Start**
   - Activity initialization
   - ViewModel creation
   - Initial state loading

2. **FocusTaskCard Composition**
   - Task data loading from TaskRepository
   - TaskScoringEngine computation
   - Card rendering with all task fields

3. **Icon Loading from LruCache**
   - AppRepository icon cache access
   - Coil image loading pipeline
   - AdaptiveIconProcessor shape masking

4. **AppDrawer Open**
   - Drawer slide-up animation
   - App list rendering
   - Search bar focus

5. **AppDrawer Scroll**
   - LazyVerticalGrid scrolling
   - Icon loading during scroll
   - Badge rendering

6. **QuickStatsPanel Open**
   - Panel slide-in animation
   - AnalyticsEngine data loading
   - Stats rendering

## Requirements

### Device Requirements
- Android device or emulator running API 26+
- **Recommended**: Snapdragon 6xx class device for accurate profiling
- Device should be rooted or using a userdebug build for best results
- Device should have the launcher set as the default home screen

### Build Requirements
- Gradle 8.7.3+
- Android Gradle Plugin 8.7.3+
- Baseline Profile Gradle Plugin 1.2.4+

## Generating the Baseline Profile

### Method 1: Using the Script (Recommended)

```bash
./scripts/generate-baseline-profile.sh
```

### Method 2: Manual Gradle Command

```bash
./gradlew :app:generateBaselineProfile
```

### Method 3: Android Studio

1. Open the project in Android Studio
2. Connect a device or start an emulator
3. Open the Gradle tool window
4. Navigate to: app → Tasks → other → generateBaselineProfile
5. Double-click to run

## What Happens During Generation

1. The test builds the app in release mode
2. Installs the app on the connected device
3. Runs the `LauncherBaselineProfileGenerator` test
4. The test performs the following actions:
   - Launches the launcher (cold start)
   - Waits for FocusTaskCard to render
   - Opens the AppDrawer (swipe up)
   - Scrolls through the AppDrawer
   - Closes the AppDrawer
   - Opens the QuickStatsPanel (swipe left)
   - Closes the QuickStatsPanel
5. Collects profiling data across 5 iterations (3 stable)
6. Generates `baseline-prof.txt` in `app/src/main/`

## Verifying the Profile

After generation, verify the profile is working:

```bash
# Build release APK
./gradlew :app:assembleRelease

# Install on device
adb install app/build/outputs/apk/release/app-release.apk

# Launch the launcher and observe startup time
# The home screen should compose within 100ms
```

## Troubleshooting

### "Could not find or load main class org.gradle.wrapper.GradleWrapperMain"
- The Gradle wrapper may be corrupted
- Regenerate the wrapper: `gradle wrapper --gradle-version 8.7.3`

### "No connected devices"
- Connect an Android device via USB with USB debugging enabled
- Or start an Android emulator

### "Baseline profile generation failed"
- Ensure the launcher is set as the default home screen
- Ensure the device is not locked
- Check logcat for errors: `adb logcat | grep BaselineProfile`

### "Profile is empty or incomplete"
- The test may have failed to interact with the UI
- Check that the launcher is properly installed and set as default
- Verify the device has sufficient apps installed for the AppDrawer to render

## Performance Targets

With the baseline profile in place, the launcher should meet these targets:

- **Home screen composition**: < 100ms on Snapdragon 6xx class devices
- **AppDrawer open**: < 200ms from swipe to fully rendered
- **Icon loading**: < 50ms per icon from LruCache
- **FocusTaskCard update**: < 1 second from task data change

## Maintenance

The baseline profile should be regenerated when:

- Major refactoring of launcher code paths
- New critical UI components are added
- Performance regressions are detected
- Android SDK version is updated

## References

- [Android Baseline Profiles Documentation](https://developer.android.com/topic/performance/baselineprofiles)
- [Macrobenchmark Library](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview)
- Requirements: 14.1, 14.8 in `requirements.md`

