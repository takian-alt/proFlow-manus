package com.neuroflow.app.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.neuroflow.app.kiosk.DeviceOwnerKioskManager

/** Device admin receiver used by managed provisioning and device-owner kiosk mode. */
class DeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        Log.i(TAG, "Device admin enabled")
        DeviceOwnerKioskManager.enableHybridProtection(context)
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        Log.i(TAG, "Provisioning complete")
        DeviceOwnerKioskManager.enableHybridProtection(context)
        DeviceOwnerKioskManager.launchKioskHome(context)
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "Disabling this admin will break kiosk enforcement on managed devices."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Log.w(TAG, "Device admin disabled")
    }

    override fun onLockTaskModeEntering(context: Context, intent: Intent, pkg: String) {
        super.onLockTaskModeEntering(context, intent, pkg)
        Log.d(TAG, "Lock task mode entering for package: $pkg")
    }

    override fun onLockTaskModeExiting(context: Context, intent: Intent) {
        super.onLockTaskModeExiting(context, intent)
        Log.d(TAG, "Lock task mode exiting")
    }

    companion object {
        private const val TAG = "NFDeviceAdminReceiver"
    }
}
