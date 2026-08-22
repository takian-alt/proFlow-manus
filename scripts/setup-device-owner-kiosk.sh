#!/usr/bin/env bash
set -euo pipefail

PACKAGE_NAME="com.neuroflow.app"
ADMIN_COMPONENT="${PACKAGE_NAME}/.receiver.DeviceAdminReceiver"

if ! command -v adb >/dev/null 2>&1; then
  echo "adb is required but not found in PATH"
  exit 1
fi

echo "Waiting for device..."
adb wait-for-device

if [ ! -f app/build/outputs/apk/debug/app-debug.apk ]; then
  echo "Debug APK not found. Building..."
  ./gradlew :app:assembleDebug
fi

echo "Installing app (debug APK expected at app/build/outputs/apk/debug/app-debug.apk)..."
adb install -r app/build/outputs/apk/debug/app-debug.apk

echo "Checking existing owner state..."
owners_before="$(adb shell dpm list owners 2>&1 || true)"
echo "$owners_before"

already_owner=0

if echo "$owners_before" | grep -q "Device Owner"; then
  if echo "$owners_before" | grep -q "${PACKAGE_NAME}"; then
    echo "This app is already Device Owner."
    already_owner=1
  else
    echo "Another app is already Device Owner on this device."
    echo "Factory reset is required before assigning a new Device Owner."
    exit 1
  fi
fi

if echo "$owners_before" | grep -q "Profile Owner"; then
  echo "A Profile Owner exists on this device."
  echo "Device Owner provisioning usually requires a clean, unmanaged device."
  echo "Factory reset the device and retry."
  exit 1
fi

echo "Setting device owner: ${ADMIN_COMPONENT}"
echo "Note: this only works on a factory-reset device with no existing accounts."
if [ "$already_owner" -eq 0 ]; then
  if ! set_owner_output="$(adb shell dpm set-device-owner "${ADMIN_COMPONENT}" 2>&1)"; then
    echo "$set_owner_output"
    echo ""
    echo "Failed to set Device Owner. Common causes:"
    echo "- Device already provisioned (setup completed with account)"
    echo "- Existing work profile / profile owner"
    echo "- App not installed for current user"
    echo ""
    echo "Fix: factory reset, skip account sign-in, enable USB debugging, then rerun this script."
    exit 1
  fi
  echo "$set_owner_output"
else
  echo "Skipping dpm set-device-owner because owner is already configured."
fi

echo "Device-owner status:"
owners_after="$(adb shell dpm list owners 2>&1 || true)"
echo "$owners_after"

if ! echo "$owners_after" | grep -q "${PACKAGE_NAME}"; then
  echo "Device Owner verification failed: ${PACKAGE_NAME} not listed in dpm owners output."
  exit 1
fi

echo "Launching HOME to enter kiosk launcher..."
adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME

echo "Done. Device owner kiosk should now be active."
