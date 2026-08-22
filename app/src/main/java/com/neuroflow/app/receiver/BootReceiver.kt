package com.neuroflow.app.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.UserManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.neuroflow.app.data.local.UserPreferencesDataStore
import com.neuroflow.app.presentation.launcher.hyperfocus.data.HyperFocusDataStore
import com.neuroflow.app.worker.FocusWidgetUpdateWorker
import com.neuroflow.app.kiosk.DeviceOwnerKioskManager
import com.neuroflow.app.worker.scheduleNotificationWorkers
import com.neuroflow.app.worker.scheduleSleepPressureRefreshWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Reschedules all periodic WorkManager jobs after a device reboot.
 * Without this, periodic workers are lost when the device restarts.
 * Also runs a HyperFocus watchdog to alert the user if the blocking service
 * is not running while a session is active.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
        private const val ACTION_QUICKBOOT_POWERON_HTC = "com.htc.intent.action.QUICKBOOT_POWERON"
        private const val WATCHDOG_NOTIFICATION_ID = 3202
    }

    @Inject
    lateinit var hyperFocusDataStore: HyperFocusDataStore

    @Inject
    lateinit var userPreferencesDataStore: UserPreferencesDataStore

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val supportedAction = action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == ACTION_QUICKBOOT_POWERON ||
            action == ACTION_QUICKBOOT_POWERON_HTC
        if (!supportedAction) return

        val wm = WorkManager.getInstance(context)

        DeviceOwnerKioskManager.migrateStrictModeDefault(context)
        DeviceOwnerKioskManager.enableHybridProtection(context)
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            DeviceOwnerKioskManager.onBootCompleted(context)
        }

        wm.enqueueUniquePeriodicWork(
            "focus_widget_update",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<FocusWidgetUpdateWorker>(15, TimeUnit.MINUTES).build()
        )

        val userUnlocked = isUserUnlocked(context)
        if (!userUnlocked) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = userPreferencesDataStore.preferencesFlow.first()
                scheduleNotificationWorkers(context, prefs)
                scheduleSleepPressureRefreshWorker(context, prefs)
                val hfPrefs = hyperFocusDataStore.current()
                DeviceOwnerKioskManager.setHyperFocusSelfProtection(context, hfPrefs.isActive)
                DeviceOwnerKioskManager.syncHyperFocusBlockedPackagesSuspension(
                    context,
                    hfPrefs.blockedPackages,
                    hfPrefs.isActive && DeviceOwnerKioskManager.isStrictKioskEnforcementEnabled(context)
                )
                if (hfPrefs.isActive) {
                    val serviceEnabled = isAccessibilityServiceEnabled(context)
                    val heartbeatStale = (System.currentTimeMillis() - hfPrefs.lastServiceHeartbeat) > 60_000L
                    if (!serviceEnabled || heartbeatStale) {
                        showHyperFocusWatchdogNotification(context)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun isUserUnlocked(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return true
        val userManager = context.getSystemService(UserManager::class.java) ?: return true
        return userManager.isUserUnlocked
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        return com.neuroflow.app.presentation.launcher.hyperfocus.util.AccessibilityUtil
            .isAppBlockingServiceEnabled(context)
    }

    private fun showHyperFocusWatchdogNotification(context: Context) {
        val channelId = "hyperfocus_watchdog_silent"
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            channelId,
            "Hyper Focus Watchdog",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            enableVibration(false)
            setSound(null, null)
        }
        notificationManager.createNotificationChannel(channel)

        val settingsIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val settingsPendingIntent = PendingIntent.getActivity(
            context,
            3202,
            settingsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Hyper Focus: Attention Required")
            .setContentText("Your focus session may be compromised. Tap to re-enable.")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(settingsPendingIntent)
            .addAction(android.R.drawable.ic_menu_preferences, "Open Accessibility", settingsPendingIntent)
            .build()

        notificationManager.notify(WATCHDOG_NOTIFICATION_ID, notification)
    }
}
