package com.neuroflow.app.kiosk

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.StrictMode
import android.os.UserManager
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.neuroflow.app.BuildConfig
import com.neuroflow.app.presentation.launcher.LauncherActivity
import com.neuroflow.app.presentation.launcher.service.LauncherKeepAliveScheduler
import com.neuroflow.app.presentation.launcher.service.LauncherKeepAliveService
import com.neuroflow.app.receiver.DeviceAdminReceiver
import com.neuroflow.app.worker.KioskPolicyWorker
import java.util.concurrent.TimeUnit

object DeviceOwnerKioskManager {
    private const val TAG = "DeviceOwnerKiosk"
    private const val KIOSK_POLICY_WORK_NAME = "kiosk_policy_watchdog"
    private const val PREFS_FILE = "kiosk_prefs"
    private const val KEY_STRICT_MODE = "strict_mode"
    private const val KEY_COMPANION_MODE = "companion_mode"
    private const val KEY_STRICT_DEFAULT_MIGRATION_V1_APPLIED = "strict_default_migration_v1_applied"
    private const val KEY_HYPERFOCUS_SUSPENDED_PACKAGES = "hyperfocus_suspended_packages"
    private const val RESTRICTION_DISALLOW_UNINSTALL_APPS = "no_uninstall_apps"
    private const val RESTRICTION_DISALLOW_APPS_CONTROL = "no_control_apps"

    fun isStrictModeEnabled(context: Context): Boolean {
        return withDiskReadAllowance {
            val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            prefs.getBoolean(KEY_STRICT_MODE, BuildConfig.KIOSK_STRICT_MODE)
        }
    }

    fun setStrictModeEnabled(context: Context, enabled: Boolean) {
        withDiskWriteAllowance {
            val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_STRICT_MODE, enabled).apply()
        }
    }

    fun isCompanionModeEnabled(context: Context): Boolean {
        return withDiskReadAllowance {
            val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            prefs.getBoolean(KEY_COMPANION_MODE, false)
        }
    }

    fun setCompanionModeEnabled(context: Context, enabled: Boolean) {
        withDiskWriteAllowance {
            val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_COMPANION_MODE, enabled).apply()
        }
    }

    /**
     * One-time migration: force strict mode default for existing installs.
     * Users can still disable it later from settings.
     */
    fun migrateStrictModeDefault(context: Context) {
        withDiskWriteAllowance {
            val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            val alreadyMigrated = prefs.getBoolean(KEY_STRICT_DEFAULT_MIGRATION_V1_APPLIED, false)
            if (alreadyMigrated) return

            prefs.edit()
                .putBoolean(KEY_STRICT_MODE, true)
                .putBoolean(KEY_STRICT_DEFAULT_MIGRATION_V1_APPLIED, true)
                .apply()
            Log.i(TAG, "Applied strict-mode default migration (v1)")
        }
    }

    /**
     * Mixed kiosk protection:
     * 1) DPC/device-owner policy (when enrolled)
     * 2) Alarm-based keepalive service schedule
     * 3) WorkManager periodic watchdog fallback
     */
    fun enableHybridProtection(context: Context) {
        try {
            LauncherKeepAliveService.start(context)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to start keepalive service", e)
        }

        LauncherKeepAliveScheduler.schedule(context)
        schedulePolicyWatchdog(context)
        applyDeviceOwnerPolicies(context)
    }

    fun onBootCompleted(context: Context) {
        enableHybridProtection(context)
        if (isDeviceOwner(context) && isStrictModeEnabled(context)) {
            launchKioskHome(context)
        }
    }

    fun isDeviceOwner(context: Context): Boolean {
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return false
        return dpm.isDeviceOwnerApp(context.packageName)
    }

    fun isAdminActive(context: Context): Boolean {
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return false
        return dpm.isAdminActive(adminComponent(context))
    }

    fun canUseStrictMode(context: Context): Boolean {
        return isDeviceOwner(context) || isAdminActive(context)
    }

    fun shouldRequireLauncherAsHome(context: Context): Boolean {
        return isStrictModeEnabled(context) &&
            isDeviceOwner(context) &&
            !isCompanionModeEnabled(context)
    }

    fun isStrictKioskEnforcementEnabled(context: Context): Boolean {
        return isStrictModeEnabled(context) && canUseStrictMode(context)
    }

    fun applyDeviceOwnerPolicies(context: Context): Boolean {
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return false
        if (!dpm.isDeviceOwnerApp(context.packageName)) return false

        val admin = adminComponent(context)
        val strictMode = isStrictModeEnabled(context)
        val companionMode = isCompanionModeEnabled(context)
        return try {
            dpm.setLockTaskPackages(admin, arrayOf(context.packageName))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val features = if (strictMode) {
                    DevicePolicyManager.LOCK_TASK_FEATURE_NONE
                } else {
                    DevicePolicyManager.LOCK_TASK_FEATURE_HOME or
                        DevicePolicyManager.LOCK_TASK_FEATURE_OVERVIEW or
                        DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS
                }
                dpm.setLockTaskFeatures(admin, features)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                dpm.setStatusBarDisabled(admin, strictMode)
                dpm.setKeyguardDisabled(admin, strictMode)
            }

            if (strictMode) {
                dpm.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
                dpm.addUserRestriction(admin, UserManager.DISALLOW_FACTORY_RESET)
                dpm.addUserRestriction(admin, UserManager.DISALLOW_ADD_USER)
            } else {
                dpm.clearUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
                dpm.clearUserRestriction(admin, UserManager.DISALLOW_FACTORY_RESET)
                dpm.clearUserRestriction(admin, UserManager.DISALLOW_ADD_USER)
            }

            if (companionMode) {
                dpm.clearPackagePersistentPreferredActivities(admin, context.packageName)
            } else {
                val homeFilter = IntentFilter(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    addCategory(Intent.CATEGORY_DEFAULT)
                }
                dpm.addPersistentPreferredActivity(
                    admin,
                    homeFilter,
                    ComponentName(context, LauncherActivity::class.java)
                )
            }

            Log.i(TAG, "Device owner kiosk policies applied (strict=$strictMode, companion=$companionMode)")
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to apply device-owner policies", e)
            false
        }
    }

    fun enforceLockTask(activity: Activity) {
        Log.i(TAG, "Lock task pinning disabled; skipping startLockTask")
    }

    fun exitLockTaskIfActive(activity: Activity) {
        if (!isInLockTaskMode(activity)) return

        try {
            activity.stopLockTask()
            Log.i(TAG, "Lock task stopped")
        } catch (e: SecurityException) {
            Log.e(TAG, "Unable to stop lock task", e)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Unable to stop lock task", e)
        }
    }

    fun syncLockTaskMode(activity: Activity) {
        exitLockTaskIfActive(activity)
    }

    fun launchKioskHome(context: Context) {
        if (!canUseStrictMode(context)) return
        if (isCompanionModeEnabled(context)) {
            Log.i(TAG, "Skipping launcher home launch: companion mode enabled")
            return
        }
        if (!isAppDefaultLauncher(context)) {
            Log.i(TAG, "Skipping launcher home launch: app is not default HOME")
            return
        }
        val homeIntent = Intent(context, LauncherActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        context.startActivity(homeIntent)
    }

    fun bringLauncherToFront(context: Context) {
        if (!canUseStrictMode(context)) return
        if (isCompanionModeEnabled(context)) {
            Log.i(TAG, "Skipping launcher foreground nudge: companion mode enabled")
            return
        }
        if (!isAppDefaultLauncher(context)) {
            Log.i(TAG, "Skipping launcher foreground nudge: app is not default HOME")
            return
        }
        val intent = Intent(context, LauncherActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        runCatching { context.startActivity(intent) }
            .onFailure { Log.w(TAG, "Unable to bring launcher to front", it) }
    }

    /**
     * Tighten app-management controls while Hyper Focus is active so users cannot
     * uninstall or force-stop via App Info paths on fully managed devices.
     *
     * Requires device-owner privileges; no-op otherwise.
     */
    fun setHyperFocusSelfProtection(context: Context, enabled: Boolean): Boolean {
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return false
        if (!dpm.isDeviceOwnerApp(context.packageName)) {
            Log.i(TAG, "Hyper Focus self-protection skipped: not device owner")
            return false
        }

        val admin = adminComponent(context)
        return try {
            dpm.setUninstallBlocked(admin, context.packageName, enabled)
            if (enabled) {
                dpm.addUserRestriction(admin, RESTRICTION_DISALLOW_UNINSTALL_APPS)
                dpm.addUserRestriction(admin, RESTRICTION_DISALLOW_APPS_CONTROL)
            } else {
                dpm.clearUserRestriction(admin, RESTRICTION_DISALLOW_UNINSTALL_APPS)
                dpm.clearUserRestriction(admin, RESTRICTION_DISALLOW_APPS_CONTROL)
            }
            Log.i(TAG, "Hyper Focus self-protection updated: enabled=$enabled")
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "Unable to update Hyper Focus self-protection", e)
            false
        }
    }

    /**
     * Suspends blocked app packages while Hyper Focus is active (device-owner only).
     * This is a stronger kiosk control than overlay-only blocking.
     */
    fun syncHyperFocusBlockedPackagesSuspension(
        context: Context,
        blockedPackages: Set<String>,
        enabled: Boolean
    ): Boolean {
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return false
        if (!dpm.isDeviceOwnerApp(context.packageName)) {
            Log.i(TAG, "Hyper Focus package suspension skipped: not device owner")
            return false
        }

        val admin = adminComponent(context)
        val normalizedBlocked = blockedPackages
            .filter { it.isNotBlank() && it != context.packageName }
            .toSet()
        val trackedSuspended = getTrackedHyperFocusSuspendedPackages(context)

        return try {
            if (enabled) {
                val stalePackages = trackedSuspended - normalizedBlocked
                if (stalePackages.isNotEmpty()) {
                    dpm.setPackagesSuspended(admin, stalePackages.toTypedArray(), false)
                }

                if (normalizedBlocked.isEmpty()) {
                    setTrackedHyperFocusSuspendedPackages(context, emptySet())
                    Log.i(TAG, "Hyper Focus package suspension enabled with empty blocked set")
                    return true
                }

                val failed = dpm.setPackagesSuspended(admin, normalizedBlocked.toTypedArray(), true).toSet()
                val applied = normalizedBlocked - failed
                setTrackedHyperFocusSuspendedPackages(context, applied)
                Log.i(TAG, "Hyper Focus package suspension applied=${applied.size}, requested=${normalizedBlocked.size}")
                true
            } else {
                val toUnsuspend = (trackedSuspended + normalizedBlocked)
                    .filter { it.isNotBlank() && it != context.packageName }
                    .toSet()
                if (toUnsuspend.isEmpty()) {
                    setTrackedHyperFocusSuspendedPackages(context, emptySet())
                    return true
                }

                val failed = dpm.setPackagesSuspended(admin, toUnsuspend.toTypedArray(), false).toSet()
                setTrackedHyperFocusSuspendedPackages(context, failed)
                Log.i(TAG, "Hyper Focus package unsuspend requested=${toUnsuspend.size}, remaining=${failed.size}")
                true
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Unable to update Hyper Focus package suspension", e)
            false
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Invalid package while updating Hyper Focus package suspension", e)
            false
        }
    }

    private fun adminComponent(context: Context): ComponentName {
        return ComponentName(context, DeviceAdminReceiver::class.java)
    }

    private fun schedulePolicyWatchdog(context: Context) {
        val request = PeriodicWorkRequestBuilder<KioskPolicyWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            KIOSK_POLICY_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun getTrackedHyperFocusSuspendedPackages(context: Context): Set<String> {
        return withDiskReadAllowance {
            val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            prefs.getStringSet(KEY_HYPERFOCUS_SUSPENDED_PACKAGES, emptySet())?.toSet() ?: emptySet()
        }
    }

    private fun setTrackedHyperFocusSuspendedPackages(context: Context, packages: Set<String>) {
        withDiskWriteAllowance {
            val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            if (packages.isEmpty()) {
                prefs.edit().remove(KEY_HYPERFOCUS_SUSPENDED_PACKAGES).apply()
            } else {
                prefs.edit().putStringSet(KEY_HYPERFOCUS_SUSPENDED_PACKAGES, packages).apply()
            }
        }
    }

    private fun isAppDefaultLauncher(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            roleManager?.isRoleHeld(RoleManager.ROLE_HOME) ?: false
        } else {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolveInfo = context.packageManager.resolveActivity(intent, 0)
            resolveInfo?.activityInfo?.packageName == context.packageName
        }
    }

    private fun isInLockTaskMode(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            activityManager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
        } else {
            @Suppress("DEPRECATION")
            activityManager.isInLockTaskMode
        }
    }

    private inline fun <T> withDiskReadAllowance(block: () -> T): T {
        val oldPolicy = StrictMode.allowThreadDiskReads()
        return try {
            block()
        } finally {
            StrictMode.setThreadPolicy(oldPolicy)
        }
    }

    private inline fun <T> withDiskWriteAllowance(block: () -> T): T {
        val oldPolicy = StrictMode.allowThreadDiskWrites()
        return try {
            block()
        } finally {
            StrictMode.setThreadPolicy(oldPolicy)
        }
    }
}
