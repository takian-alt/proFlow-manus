package com.neuroflow.app.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.neuroflow.app.R
import com.neuroflow.app.presentation.launcher.hyperfocus.data.HyperFocusDataStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periodic worker that fires every 15 minutes.
 * If a Hyper Focus session is active but the accessibility service is disabled,
 * it fires a high-priority notification prompting the user to re-enable it.
 */
@HiltWorker
class AccessibilityWatchdogWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val hyperFocusDataStore: HyperFocusDataStore
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "accessibility_watchdog"
        private const val CHANNEL_ID = "accessibility_watchdog_silent"
        private const val NOTIFICATION_ID = 3001
    }

    override suspend fun doWork(): Result {
        val prefs = hyperFocusDataStore.current()
        if (!prefs.isActive) return Result.success()

        val accessibilityEnabled = isAccessibilityEnabled()
        if (!accessibilityEnabled) {
            showWarningNotification()
        }

        return Result.success()
    }

    private fun isAccessibilityEnabled(): Boolean {
        return com.neuroflow.app.presentation.launcher.hyperfocus.util.AccessibilityUtil
            .isAppBlockingServiceEnabled(appContext)
    }

    private fun showWarningNotification() {
        val notificationManager =
            appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Accessibility Watchdog",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            enableVibration(false)
            setBypassDnd(false)
            setSound(null, null)
        }
        notificationManager.createNotificationChannel(channel)

        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle("⚠️ Hyper Focus: Blocking Disabled!")
            .setContentText("Accessibility service is off — apps are not being blocked. Tap to re-enable.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setAutoCancel(false)
            .setContentIntent(
                PendingIntent.getActivity(appContext, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            )
            .addAction(
                R.drawable.ic_launcher_foreground,
                "Re-enable Now",
                PendingIntent.getActivity(appContext, 1, intent, PendingIntent.FLAG_IMMUTABLE)
            )
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
