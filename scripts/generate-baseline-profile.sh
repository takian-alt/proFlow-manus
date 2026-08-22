#!/bin/bash

# Script to generate Baseline Profile for proFlow Launcher
# This script runs the baseline profile generator test and places the result in app/src/main/

set -e

echo "=========================================="
echo "Baseline Profile Generation for proFlow Launcher"
echo "=========================================="
echo ""
echo "This will:"
echo "1. Build the app in release mode"
echo "2. Run the baseline profile generator test"
echo "3. Generate baseline-prof.txt in app/src/main/"
echo ""
echo "Requirements:"
echo "- Android device or emulator running (API 26+)"
echo "- Device should be rooted or using a userdebug build"
echo "- Recommended: Snapdragon 6xx class device for accurate profiling"
echo ""
echo "Starting baseline profile generation..."
echo ""

# Clean previous builds
./gradlew clean

# Generate the baseline profile
./gradlew :app:generateBaselineProfile --stacktrace

echo ""
echo "=========================================="
echo "Baseline Profile Generation Complete!"
echo "=========================================="
echo ""
echo "The baseline-prof.txt file has been generated at:"
echo "  app/src/main/baseline-prof.txt"
echo ""
echo "Next steps:"
echo "1. Review the generated profile"
echo "2. Commit the file to the repository"
echo "3. Build a release APK to verify the profile is included"
echo ""
echo "To verify the profile is working:"
echo "  ./gradlew :app:assembleRelease"
echo "  adb install app/build/outputs/apk/release/app-release.apk"
echo ""
