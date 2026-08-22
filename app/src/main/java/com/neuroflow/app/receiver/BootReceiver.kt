package com.neuroflow.app.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.neuroflow.app.presentation.launcher.hyperfocus.data.HyperFocusDataStore
import com.neuroflow.app.worker.DailyPlanWorker
import com.neuroflow.app.worker.DeadlineEscalationWorker
import com.neuroflow.app.worker.FocusWidgetUpdateWorker
import com.neuroflow.app.worker.StreakCheckWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
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

    @Inject
    lateinit var hyperFocusDataStore: HyperFocusDataStore

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val wm = WorkManager.getInstance(context)

        wm.enqueueUniquePeriodicWork(
            "daily_plan",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DailyPlanWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delayUntilHour(7), TimeUnit.MILLISECONDS)
                .build()
        )
        wm.enqueueUniquePeriodicWork(
            "streak_check",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<StreakCheckWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delayUntilHour(21), TimeUnit.MILLISECONDS)
                .build()
        )
        wm.enqueueUniquePeriodicWork(
            "deadline_escalation",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DeadlineEscalationWorker>(4, TimeUnit.HOURS).build()
        )
        wm.enqueueUniquePeriodicWork(
            "focus_widget_update",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<FocusWidgetUpdateWorker>(15, TimeUnit.MINUTES).build()
        )

        // HyperFocus watchdog: alert user if blocking service is compromised
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val hfPrefs = hyperFocusDataStore.current()
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

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        return com.neuroflow.app.presentation.launcher.hyperfocus.util.AccessibilityUtil
            .isAppBlockingServiceEnabled(context)
    }

    private fun showHyperFocusWatchdogNotification(context: Context) {
        val channelId = "hyperfocus_watchdog"
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            channelId,
            "Hyper Focus Watchdog",
            NotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Hyper Focus: Attention Required")
            .setContentText("Your focus session may be compromised. Tap to re-enable.")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .build()

        notificationManager.notify(2002, notification)
    }

    private fun delayUntilHour(hour: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.before(now)) target.add(Calendar.DAY_OF_YEAR, 1)
        return target.timeInMillis - now.timeInMillis
    }
}
