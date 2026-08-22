package com.neuroflow.app.presentation.launcher.hyperfocus.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.neuroflow.app.MainActivity
import com.neuroflow.app.R
import com.neuroflow.app.presentation.launcher.hyperfocus.data.HyperFocusDataStore
import com.neuroflow.app.presentation.launcher.hyperfocus.domain.HyperFocusManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that keeps Hyper Focus active and monitors accessibility service health.
 * Runs only during active Hyper Focus sessions to ensure app blocking remains active.
 */
@AndroidEntryPoint
class HyperFocusMonitorService : Service() {

    @Inject
    lateinit var hyperFocusDataStore: HyperFocusDataStore

    @Inject
    lateinit var hyperFocusManager: HyperFocusManager

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private var monitorJob: Job? = null

    companion object {
        private const val CHANNEL_ID = "hyperfocus_monitor"
        private const val CHANNEL_NAME = "Hyper Focus Active"
        private const val NOTIFICATION_ID = 2002
        private const val CHECK_INTERVAL_MS = 5_000L // Check every 5 seconds
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            while (true) {
                val prefs = hyperFocusDataStore.flow.first()

                if (!prefs.isActive ||
                    prefs.state == com.neuroflow.app.domain.model.HyperFocusState.FULLY_UNLOCKED ||
                    prefs.state == com.neuroflow.app.domain.model.HyperFocusState.INACTIVE
                ) {
                    stopSelf()
                    break
                }

                val accessibilityEnabled = isAccessibilityServiceEnabled()
                val isDefaultLauncher = isLauncherDefault()
                val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

                if (!accessibilityEnabled) {
                    hyperFocusManager.reportTamper("Accessibility service disabled")
                    notificationManager.notify(NOTIFICATION_ID, buildTamperNotification("Accessibility service disabled"))
                } else if (!isDefaultLauncher) {
                    hyperFocusManager.reportTamper("Default launcher changed")
                    notificationManager.notify(NOTIFICATION_ID, buildTamperNotification("Default launcher changed"))
                } else {
                    notificationManager.notify(NOTIFICATION_ID, buildNotification())
                }

                delay(CHECK_INTERVAL_MS)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        // Normal channel for active session status
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when Hyper Focus is actively blocking apps"
        }
        // Warning channel — HIGH importance so it makes noise when accessibility is killed
        val warningChannel = NotificationChannel(
            "${CHANNEL_ID}_warning",
            "Hyper Focus Warning",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts when Hyper Focus accessibility service is disabled"
            enableVibration(true)
            setBypassDnd(true)
            setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI, null)
        }
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
        notificationManager.createNotificationChannel(warningChannel)
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("🔒 Hyper Focus Active")
        .setContentText("Apps are being blocked. Complete tasks to unlock.")
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setContentIntent(
            PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()

    private fun buildWarningNotification() = NotificationCompat.Builder(this, "${CHANNEL_ID}_warning")
        .setContentTitle("⚠️ Hyper Focus Compromised!")
        .setContentText("Accessibility service was disabled. Apps are NOT being blocked. Tap to fix.")
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setOngoing(true) // can't be swiped away
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .setAutoCancel(false)
        .setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
        .setContentIntent(
            PendingIntent.getActivity(
                this, 1,
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE
            )
        )
        .addAction(
            R.drawable.ic_launcher_foreground,
            "Re-enable Now",
            PendingIntent.getActivity(
                this, 2,
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()

    private fun buildTamperNotification(reason: String) = NotificationCompat.Builder(this, "${CHANNEL_ID}_warning")
        .setContentTitle("⚠️ Hyper Focus Tamper Detected")
        .setContentText(reason)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .setAutoCancel(false)
        .setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
        .setContentIntent(
            PendingIntent.getActivity(
                this, 2,
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()

    private fun isLauncherDefault(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            roleManager?.isRoleHeld(RoleManager.ROLE_HOME) ?: false
        } else {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolveInfo = packageManager.resolveActivity(intent, 0)
            resolveInfo?.activityInfo?.packageName == packageName
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        return com.neuroflow.app.presentation.launcher.hyperfocus.util.AccessibilityUtil
            .isAppBlockingServiceEnabled(this)
    }
}
