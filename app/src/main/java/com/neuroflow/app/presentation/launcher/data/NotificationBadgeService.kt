package com.neuroflow.app.presentation.launcher.data

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * NotificationListenerService that tracks per-package unread notification counts.
 *
 * Updates NotificationBadgeManager within 500ms of notification events.
 * Emits empty map when access not granted.
 * Badge display capped at "99+".
 */
@AndroidEntryPoint
class NotificationBadgeService : NotificationListenerService() {

    @Inject
    lateinit var badgeManager: NotificationBadgeManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Pending update flag to batch updates within 500ms
    private var updatePending = false

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        // Rebuild badge counts from all active notifications
        rebuildBadgeCounts()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        // Emit empty map when disconnected
        badgeManager.updateBadgeCounts(emptyMap())
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        scheduleUpdate()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        scheduleUpdate()
    }

    /**
     * Schedule badge count update within 500ms.
     * Batches multiple notification events to avoid excessive updates.
     */
    private fun scheduleUpdate() {
        if (updatePending) return

        updatePending = true
        serviceScope.launch {
            delay(500)
            rebuildBadgeCounts()
            updatePending = false
        }
    }

    /**
     * Rebuild badge counts from all active notifications.
     */
    private fun rebuildBadgeCounts() {
        try {
            val activeNotifications = activeNotifications ?: run {
                badgeManager.updateBadgeCounts(emptyMap())
                return
            }

            val counts = mutableMapOf<String, Int>()

            for (sbn in activeNotifications) {
                val packageName = sbn.packageName
                if (packageName != null) {
                    counts[packageName] = (counts[packageName] ?: 0) + 1
                }
            }

            badgeManager.updateBadgeCounts(counts)
        } catch (e: Exception) {
            android.util.Log.e("NotificationBadgeService", "Error rebuilding badge counts", e)
            badgeManager.updateBadgeCounts(emptyMap())
        }
    }
}

/**
 * Extension function to format badge count with "99+" cap.
 */
fun Int.toBadgeString(): String {
    return when {
        this == 0 -> ""
        this > 99 -> "99+"
        else -> this.toString()
    }
}
