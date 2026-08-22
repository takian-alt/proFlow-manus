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
import com.neuroflow.app.kiosk.DeviceOwnerKioskManager
import com.neuroflow.app.presentation.launcher.hyperfocus.data.HyperFocusDataStore
import com.neuroflow.app.presentation.launcher.hyperfocus.domain.HyperFocusManager
import com.neuroflow.app.presentation.launcher.service.LauncherKeepAliveScheduler
import com.neuroflow.app.presentation.launcher.service.LauncherKeepAliveService
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
    private var activeTamperReason: String? = null
    private var lastRecoveryAttemptAt: Long = 0L

    companion object {
        private const val CHANNEL_ID = "hyperfocus_monitor"
        private const val WARNING_CHANNEL_ID = "hyperfocus_monitor_warning_silent"
        private const val CHANNEL_NAME = "Hyper Focus Active"
        private const val NOTIFICATION_ID = 2002
        private const val WARNING_NOTIFICATION_ID = 2003
        private const val CHECK_INTERVAL_MS = 2_000L
        private const val HEARTBEAT_STALE_MS = 8_000L
        private const val RECOVERY_RETRY_INTERVAL_MS = 2_000L
        private const val TAMPER_REASON_ACCESSIBILITY = "Accessibility service disabled"
        private const val TAMPER_REASON_ACCESSIBILITY_UNRESPONSIVE = "Accessibility service unresponsive"
        private const val TAMPER_REASON_LAUNCHER = "Default launcher changed"
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

                val now = System.currentTimeMillis()
                val accessibilityEnabled = isAccessibilityServiceEnabled()
                val heartbeatStale = prefs.lastServiceHeartbeat > 0L &&
                    (now - prefs.lastServiceHeartbeat) > HEARTBEAT_STALE_MS
                val accessibilityHealthy = accessibilityEnabled && !heartbeatStale
                val isDefaultLauncher = isLauncherDefault()
                val requireLauncherHome = DeviceOwnerKioskManager.shouldRequireLauncherAsHome(this@HyperFocusMonitorService)
                val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

                val tamperReason = when {
                    !accessibilityEnabled -> TAMPER_REASON_ACCESSIBILITY
                    heartbeatStale -> TAMPER_REASON_ACCESSIBILITY_UNRESPONSIVE
                    requireLauncherHome && !isDefaultLauncher -> TAMPER_REASON_LAUNCHER
                    else -> null
                }

                if (!accessibilityHealthy && (now - lastRecoveryAttemptAt) >= RECOVERY_RETRY_INTERVAL_MS) {
                    lastRecoveryAttemptAt = now
                    runCatching { LauncherKeepAliveService.start(this@HyperFocusMonitorService) }
                    runCatching { LauncherKeepAliveScheduler.scheduleRecovery(this@HyperFocusMonitorService) }
                    if (DeviceOwnerKioskManager.isStrictKioskEnforcementEnabled(this@HyperFocusMonitorService)) {
                        runCatching { DeviceOwnerKioskManager.enableHybridProtection(this@HyperFocusMonitorService) }
                    }
                } else if (accessibilityHealthy) {
                    lastRecoveryAttemptAt = 0L
                }

                if (tamperReason != null) {
                    if (activeTamperReason != tamperReason) {
                        activeTamperReason = tamperReason
                        hyperFocusManager.reportTamper(tamperReason)
                        if (DeviceOwnerKioskManager.isStrictKioskEnforcementEnabled(this@HyperFocusMonitorService)) {
                            DeviceOwnerKioskManager.enableHybridProtection(this@HyperFocusMonitorService)
                        }
                        if (tamperReason == TAMPER_REASON_LAUNCHER) {
                            DeviceOwnerKioskManager.enableHybridProtection(this@HyperFocusMonitorService)
                            DeviceOwnerKioskManager.bringLauncherToFront(this@HyperFocusMonitorService)
                        } else if (
                            tamperReason == TAMPER_REASON_ACCESSIBILITY &&
                            DeviceOwnerKioskManager.isStrictKioskEnforcementEnabled(this@HyperFocusMonitorService)
                        ) {
                            DeviceOwnerKioskManager.bringLauncherToFront(this@HyperFocusMonitorService)
                        }
                        notificationManager.notify(
                            WARNING_NOTIFICATION_ID,
                            buildTamperNotification(tamperReason)
                        )
                    }
                } else if (activeTamperReason != null) {
                    activeTamperReason = null
                    notificationManager.cancel(WARNING_NOTIFICATION_ID)
                }

                delay(CHECK_INTERVAL_MS)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(WARNING_NOTIFICATION_ID)
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
            WARNING_CHANNEL_ID,
            "Hyper Focus Warning",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Alerts when Hyper Focus accessibility service is disabled"
            enableVibration(false)
            setBypassDnd(false)
            setSound(null, null)
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

    private fun buildWarningNotification() = NotificationCompat.Builder(this, WARNING_CHANNEL_ID)
        .setContentTitle("⚠️ Hyper Focus Compromised!")
        .setContentText("Accessibility service was disabled. Apps are NOT being blocked. Tap to fix.")
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setOngoing(true) // can't be swiped away
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setAutoCancel(false)
        .setOnlyAlertOnce(true)
        .setSilent(true)
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

    private fun buildTamperNotification(reason: String) = NotificationCompat.Builder(this, WARNING_CHANNEL_ID)
        .setContentTitle("⚠️ Hyper Focus Tamper Detected")
        .setContentText(reason)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setOngoing(false)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setAutoCancel(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                if (reason == TAMPER_REASON_LAUNCHER) 4 else 2,
                Intent(
                    if (reason == TAMPER_REASON_LAUNCHER) {
                        Settings.ACTION_HOME_SETTINGS
                    } else {
                        Settings.ACTION_ACCESSIBILITY_SETTINGS
                    }
                )
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE
            )
        )
        .addAction(
            R.drawable.ic_launcher_foreground,
            if (reason == TAMPER_REASON_LAUNCHER) "Set Default Launcher" else "Re-enable Accessibility",
            PendingIntent.getActivity(
                this,
                if (reason == TAMPER_REASON_LAUNCHER) 5 else 3,
                Intent(
                    if (reason == TAMPER_REASON_LAUNCHER) {
                        Settings.ACTION_HOME_SETTINGS
                    } else {
                        Settings.ACTION_ACCESSIBILITY_SETTINGS
                    }
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
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
