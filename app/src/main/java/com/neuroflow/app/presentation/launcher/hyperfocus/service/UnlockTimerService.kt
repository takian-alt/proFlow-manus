package com.neuroflow.app.presentation.launcher.hyperfocus.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.neuroflow.app.presentation.launcher.hyperfocus.data.HyperFocusDataStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class UnlockTimerService : Service() {

    @Inject
    lateinit var hyperFocusDataStore: HyperFocusDataStore

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private var pollingJob: Job? = null

    private val notificationManager by lazy {
        getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    }

    companion object {
        const val ACTION_UNLOCK_EXPIRED = "com.neuroflow.app.UNLOCK_EXPIRED"
        private const val CHANNEL_ID = "hyperfocus_timer"
        private const val CHANNEL_NAME = "Hyper Focus Timer"
        private const val NOTIFICATION_ID = 2001
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Apps unlocked"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        pollingJob = serviceScope.launch {
            while (true) {
                delay(1_000L) // Check every second for accurate expiry
                val prefs = hyperFocusDataStore.current()
                val expiresAt = prefs.activeUnlockExpiresAt
                if (expiresAt == null || expiresAt <= System.currentTimeMillis()) {
                    // Clear the unlock and broadcast expiry so AppBlockingService re-blocks immediately
                    hyperFocusDataStore.update { it.copy(activeUnlockExpiresAt = null) }
                    sendBroadcast(Intent(ACTION_UNLOCK_EXPIRED).setPackage(packageName))
                    stopSelf()
                    break
                } else {
                    val remainingMillis = expiresAt - System.currentTimeMillis()
                    val minutes = (remainingMillis / 60_000).toInt()
                    val seconds = ((remainingMillis % 60_000) / 1_000).toInt()
                    val text = "Apps unlocked — ${minutes}m ${seconds}s remaining"
                    notificationManager.notify(NOTIFICATION_ID, buildNotification(text))
                }
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
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW)
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Hyper Focus")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .build()
}
