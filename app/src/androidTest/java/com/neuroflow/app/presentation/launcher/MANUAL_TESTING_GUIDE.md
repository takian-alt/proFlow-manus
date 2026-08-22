# Manual Testing Guide for Task 30: Final Integration and Testing

This guide provides comprehensive manual testing instructions for scenarios that cannot be fully automated or require specific device configurations.

## Overview

Task 30 covers four main testing areas:
1. **Multiple device configurations** (portrait/landscape, foldables, navigation modes, OEM skins)
2. **Work profile integration** (work badges, UserHandle launching)
3. **Icon pack and theming** (icon packs, shape changes, Dynamic Color, adaptive masking)
4. **Error handling and recovery** (failure recovery, crash handling)

## Prerequisites

### Required Test Devices/Emulators

- **Phone (Portrait/Landscape)**: Any Android device API 26+
- **Foldable**: Pixel Fold emulator or physical foldable device
- **Samsung Device**: Galaxy device with One UI (for Samsung-specific testing)
- **MIUI/HyperOS Device**: Xiaomi device with MIUI or HyperOS
- **Work Profile Device**: Device with work profile configured
- **API 31+ Device**: For Dynamic Color testing

### Test Data Setup

1. Install the proFlow Launcher app
2. Set as default launcher via Settings → Apps → Default apps → Home app
3. Create test tasks in the main app (at least 3 active tasks, 3 habits)
4. Install at least one icon pack (optional, for icon pack testing)
5. Configure work profile (optional, for work profile testing)

---

## Task 30.1: Multiple Device Configurations

### Test 30.1.1: Portrait Layout
**Requirements**: 2.3, 2.12

**Steps**:
1. Hold device in portrait orientation
2. Press Home button to open launcher
3. Verify components appear in correct order top-to-bottom:
   - DateTimeDisplay (time and date)
   - FocusTaskCard (highest priority task)
   - HabitQuickRow (up to 3 habits)
   - WidgetSlotRow (empty in Phase 1)
   - DockRow (4-5 pinned apps)

**Expected Result**: All components render correctly in vertical layout

---

### Test 30.1.2: Landscape Layout
**Requirements**: 2.12

**Steps**:
1. Rotate device to landscape orientation
2. Press Home button to open launcher
3. Verify layout reflows to side-by-side:
   - DateTimeDisplay spans full width at top
   - FocusTaskCard on left half
   - HabitQuickRow + DockRow on right half

**Expected Result**: Layout adapts smoothly without crash or black screen

---

### Test 30.1.3: Foldable Folded State
**Requirements**: 2.13, 2.14

**Device**: Pixel Fold emulator or physical foldable

**Steps**:
1. With device folded (outer display), press Home button
2. Verify launcher renders in portrait layout (same as Test 30.1.1)
3. Verify all components are visible and functional

**Expected Result**: Launcher works correctly on outer display

---

### Test 30.1.4: Foldable Unfolded State
**Requirements**: 2.13, 2.14

**Device**: Pixel Fold emulator or physical foldable

**Steps**:
1. Unfold device to inner display
2. Press Home button to open launcher
3. Verify two-column layout:
   - FocusTaskCard in left column
   - HabitQuickRow + QuickStatsPanel inline in right column
4. Fold device while launcher is open
5. Verify smooth transition to portrait layout

**Expected Result**: Layout adapts smoothly between folded/unfolded states without crash

---

### Test 30.1.5: Three-Button Navigation
**Requirements**: 9.2, 9.3, 9.4

**Steps**:
1. Set navigation mode to 3-button (Settings → System → Gestures → System navigation)
2. Open launcher
3. Swipe up from bottom of screen
4. Verify app drawer opens
5. Verify no gesture exclusion zone is applied (swipe works from any height)

**Expected Result**: Swipe-up gesture works correctly with 3-button navigation

---

### Test 30.1.6: Two-Button Navigation
**Requirements**: 9.2, 9.3, 9.4

**Steps**:
1. Set navigation mode to 2-button
2. Open launcher
3. Swipe up from bottom of screen
4. Verify app drawer opens
5. Verify no gesture exclusion zone is applied

**Expected Result**: Swipe-up gesture works correctly with 2-button navigation

---

### Test 30.1.7: Gesture Navigation (Non-Samsung)
**Requirements**: 9.2, 9.3, 9.4

**Device**: Non-Samsung device with gesture navigation

**Steps**:
1. Set navigation mode to gesture navigation
2. Open launcher
3. Swipe up from very bottom of screen (within 200dp exclusion zone)
4. Verify system gesture (recent apps) is triggered, NOT app drawer
5. Swipe up from above exclusion zone (middle of screen)
6. Verify app drawer opens

**Expected Result**: 200dp exclusion zone prevents conflict with system gestures

---

### Test 30.1.8: Gesture Navigation (Samsung)
**Requirements**: 9.4, 23.4

**Device**: Samsung Galaxy device with One UI

**Steps**:
1. Set navigation mode to gesture navigation
2. Open launcher
3. Swipe up from very bottom of screen (within 280dp exclusion zone)
4. Verify system gesture is triggered, NOT app drawer
5. Swipe up from above exclusion zone
6. Verify app drawer opens

**Expected Result**: 280dp exclusion zone (Samsung-specific) prevents gesture conflicts

---

### Test 30.1.9: MIUI/HyperOS Device
**Requirements**: 23.4

**Device**: Xiaomi device with MIUI or HyperOS

**Steps**:
1. Open launcher settings (long-press home screen)
2. Tap "Enable" button in onboarding card
3. Verify Settings → Apps → Default apps → Home app opens
4. Select proFlow Launcher
5. Verify "Recommended launcher" popup may appear (this is normal MIUI behavior)
6. Confirm selection
7. Press Home button
8. Verify launcher opens correctly

**Expected Result**: Launcher works on MIUI/HyperOS despite "Recommended launcher" popup

---

## Task 30.2: Work Profile Integration

### Test 30.2.1: Work Profile Badge Display
**Requirements**: 8.11, 11.10

**Device**: Device with work profile configured

**Steps**:
1. Open launcher
2. Swipe up to open app drawer
3. Locate work profile apps (should have work badge overlay)
4. Verify work badge is visible at bottom-right of icon
5. Verify work apps are mixed with personal apps in alphabetical order

**Expected Result**: Work apps display with work badge overlay

---

### Test 30.2.2: Work App Launching
**Requirements**: 8.11, 11.10

**Device**: Device with work profile configured

**Steps**:
1. Open app drawer
2. Tap a work profile app
3. Verify app launches correctly in work profile context
4. Return to launcher
5. Verify no crash or error

**Expected Result**: Work apps launch correctly with UserHandle

---

### Test 30.2.3: Work Apps in Dock
**Requirements**: 8.11

**Device**: Device with work profile configured

**Steps**:
1. Open app drawer
2. Long-press a work profile app
3. Select "Pin to Dock"
4. Verify work app appears in dock with work badge
5. Tap work app in dock
6. Verify app launches correctly

**Expected Result**: Work apps can be pinned to dock and launch correctly

---

### Test 30.2.4: Work Apps with Notification Badges
**Requirements**: 8.11, 8.12

**Device**: Device with work profile configured

**Steps**:
1. Ensure notification access is granted (Settings → Notification access)
2. Trigger notification from work profile app
3. Open launcher
4. Verify work app icon shows both work badge AND notification badge
5. Verify badges don't overlap

**Expected Result**: Both work badge and notification badge are visible

---

## Task 30.3: Icon Pack and Theming

### Test 30.3.1: Icon Pack Selection
**Requirements**: 12.6

**Prerequisites**: Install at least one icon pack (e.g., from Play Store)

**Steps**:
1. Open launcher settings (long-press home screen)
2. Scroll to "Icon pack" section
3. Tap icon pack selector
4. Select an installed icon pack
5. Verify icons in dock, app drawer, and home screen update immediately
6. Verify no launcher restart is required

**Expected Result**: Icon pack applies without restart

---

### Test 30.3.2: Icon Shape Changes
**Requirements**: 10.5

**Steps**:
1. Open launcher settings
2. Scroll to "Icon shape" section
3. Select "Circle"
4. Verify all icons update to circular shape immediately
5. Select "Squircle"
6. Verify all icons update to squircle shape
7. Test other shapes: Rounded Square, Teardrop, System Default
8. Verify no launcher restart is required

**Expected Result**: Icon shape changes apply immediately without restart

---

### Test 30.3.3: Dynamic Color (API 31+)
**Requirements**: 17.1, 17.2

**Device**: Android 12+ (API 31+)

**Steps**:
1. Change wallpaper to one with distinct colors (e.g., blue wallpaper)
2. Open launcher
3. Verify FocusTaskCard, DockRow, AppDrawer use colors from wallpaper
4. Change wallpaper to different color (e.g., red wallpaper)
5. Reopen launcher
6. Verify colors update to match new wallpaper

**Expected Result**: Launcher adopts wallpaper color palette (Material You)

---

### Test 30.3.4: Adaptive Icon Masking
**Requirements**: 10.1, 10.4

**Steps**:
1. Open app drawer
2. Observe icons from different apps:
   - Apps with AdaptiveIconDrawable (modern apps)
   - Apps with legacy icons (older apps)
3. Verify all icons have consistent shape mask applied
4. Verify legacy icons have background applied before masking
5. Change icon shape in settings
6. Verify all icons update with new mask

**Expected Result**: All icon types render with consistent shape masking

---

## Task 30.4: Error Handling and Recovery

### Test 30.4.1: AppRepository Failure Recovery
**Requirements**: 14.10

**Steps**:
1. Enable airplane mode (to simulate network issues)
2. Force stop launcher app
3. Clear launcher app cache (Settings → Apps → proFlow → Storage → Clear cache)
4. Open launcher
5. Verify launcher renders without crash
6. Verify app drawer opens and shows apps (from cache or LauncherApps)
7. Disable airplane mode

**Expected Result**: Launcher handles AppRepository failures gracefully

---

### Test 30.4.2: NotificationBadgeService Disconnection
**Requirements**: 13.3

**Steps**:
1. Grant notification access (Settings → Notification access → proFlow)
2. Open launcher, verify badges appear
3. Revoke notification access
4. Return to launcher
5. Verify dock and app drawer render without crash
6. Verify no badges are shown (empty map emitted)

**Expected Result**: Launcher handles badge service disconnection gracefully

---

### Test 30.4.3: BiometricPrompt Failure Cases
**Requirements**: 15.3, 15.4

**Prerequisites**: Device with biometric hardware

**Steps**:
1. Open app drawer
2. Long-press an app
3. Select "Lock app"
4. Tap the locked app
5. When BiometricPrompt appears, tap "Cancel"
6. Verify app does NOT launch
7. Tap locked app again
8. Fail authentication 3 times
9. Verify "Too many attempts" toast appears
10. Verify app does NOT launch

**Expected Result**: Biometric failures are handled gracefully with appropriate messages

---

### Test 30.4.4: Backup Import Validation
**Requirements**: 18.3, 18.4

**Steps**:
1. Open launcher settings
2. Tap "Export configuration"
3. Save backup file
4. Open backup file in text editor
5. Corrupt JSON (e.g., remove closing brace)
6. Return to launcher settings
7. Tap "Import configuration"
8. Select corrupted backup file
9. Verify error message is shown
10. Verify launcher doesn't crash

**Expected Result**: Invalid JSON is caught and error message shown

---

### Test
cher app while it's open
3. Press Home button
4. Verify launcher restarts and renders SafeHomeScreen or normal home screen
5. Verify no black screen or ANR
6. Verify dock is visible

**Expected Result**: Launcher recovers from crash within 2 seconds

---

### Test 30.4.7: Package Changes
**Requirements**: 11.5, 4.8, 7.5

**Steps**:
1. Pin an app to dock
2. Add app to a folder
3. Uninstall the app
4. Wait 2 seconds
5. Verify app is removed from dock
6. Open folder
7. Verify app is removed from folder
8. Reinstall the app
9. Wait 2 seconds
10. Open app drawer
11. Verify app appears in drawer

**Expected Result**: Package changes are handled within 2 seconds

---

### Test 30.4.8: Memory Pressure
**Requirements**: 1.13, 1.14, 11.11

**Steps**:
1. Open launcher
2. Open and close app drawer 10 times rapidly
3. Open 5 different apps
4. Return to launcher each time
5. Verify launcher remains responsive
6. Verify no crash or ANR
7. Verify icons still load correctly

**Expected Result**: Launcher handles memory pressure gracefully

---

### Test 30.4.9: Back Press Handling
**Requirements**: 14.12

**Steps**:
1. Open launcher
2. Swipe up to open app drawer
3. Press back button
4. Verify app drawer closes
5. Verify launcher home screen is visible
6. Press back button again
7. Verify launcher does NOT finish (stays on home screen)

**Expected Result**: Back press closes drawer/panel but never finishes launcher

---

### Test 30.4.10: Composition Performance
**Requirements**: 14.1

**Steps**:
1. Force stop launcher app
2. Press Home button
3. Observe launcher startup time
4. Verify home screen appears within 100ms (on mid-range device)
5. Verify no visible jank or stutter
6. Verify all components render smoothly

**Expected Result**: Launcher composes within 100ms on mid-range device

---

## Automated Test Execution

To run the automated integration tests:

```bash
# Run all launcher integration tests
./gradlew :app:connectedDebugAndroidTest --tests "com.neuroflow.app.presentation.launcher.*"

# Run specific test class
./gradlew :app:connectedDebugAndroidTest --tests "com.neuroflow.app.presentation.launcher.LauncherIntegrationTest"

# Run specific test method
./gradlew :app:connectedDebugAndroidTest --tests "com.neuroflow.app.presentation.launcher.LauncherIntegrationTest.testPortraitLayout_displaysComponentsInCorrectOrder"
```

## Test Results Documentation

After completing manual tests, document results in the following format:

```
Test ID: 30.1.1
Test Name: Portrait Layout
Device: Pixel 6 (Android 14)
Result: PASS/FAIL
Notes: [Any observations or issues]
Date: YYYY-MM-DD
Tester: [Name]
```

## Known Limitations

1. **Work Profile Tests**: Require physical device or emulator with work profile configured
2. **Foldable Tests**: Require Pixel Fold emulator or physical foldable device
3. **Samsung Tests**: Require physical Samsung device with One UI
4. **MIUI Tests**: Require physical Xiaomi device with MIUI/HyperOS
5. **Dynamic Color Tests**: Require Android 12+ (API 31+)
6. **Biometric Tests**: Require device with biometric hardware

## Troubleshooting

### Issue: Tests fail with "testTag not found"
**Solution**: Ensure test tags are added to composables (completed in this task)

### Issue: Work profile tests are skipped
**Solution**: Configure work profile on device (Settings → Users & accounts → Work profile)

### Issue: Biometric tests fail
**Solution**: Ensure biometric authentication is set up (Settings → Security → Fingerprint/Face unlock)

### Issue: Icon pack tests fail
**Solution**: Install at least one icon pack from Play Store

### Issue: Gradle build fails
**Solution**: Run `./gradlew clean` and retry

---

## Completion Checklist

- [ ] All automated tests pass
- [ ] All manual tests completed and documented
- [ ] Test results reviewed with team
- [ ] Known issues documented
- [ ] Task 30 marked as complete

