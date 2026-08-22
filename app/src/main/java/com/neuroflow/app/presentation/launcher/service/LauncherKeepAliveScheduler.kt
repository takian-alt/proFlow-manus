package com.neuroflow.app.presentation.launcher.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock

object LauncherKeepAliveScheduler {
    private const val KEEP_ALIVE_INTERVAL_MS = 60_000L
    private const val QUICK_RECOVERY_DELAY_MS = 2_000L
    private const val REQUEST_CODE = 3901

    fun schedule(context: Context) {
        schedule(context, KEEP_ALIVE_INTERVAL_MS)
    }

    fun scheduleRecovery(context: Context) {
        schedule(context, QUICK_RECOVERY_DELAY_MS)
    }

    private fun schedule(context: Context, delayMs: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val triggerAtMillis = SystemClock.elapsedRealtime() + delayMs
        val pendingIntent = createPendingIntent(context)

        alarmManager.cancel(pendingIntent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarmManager.cancel(createPendingIntent(context))
    }

    private fun createPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, LauncherKeepAliveAlarmReceiver::class.java).apply {
            action = LauncherKeepAliveAlarmReceiver.ACTION_KEEP_ALIVE
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}