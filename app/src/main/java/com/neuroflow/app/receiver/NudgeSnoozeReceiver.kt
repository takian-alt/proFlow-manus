package com.neuroflow.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.neuroflow.app.domain.engine.AutonomyNudgeEngine
import com.neuroflow.app.R
import com.neuroflow.app.worker.AutonomyNudgeWorker
import java.util.concurrent.TimeUnit

/**
 * Handles the "I'm not ready yet" snooze action from the autonomy nudge notification.
 * Re-enqueues [AutonomyNudgeWorker] with a 1-hour delay without requiring the app to open.
 */
class NudgeSnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra("taskId") ?: return
        val notificationId = intent.getIntExtra("notificationId", Int.MIN_VALUE)
        if (notificationId != Int.MIN_VALUE) {
            NotificationManagerCompat.from(context).cancel(notificationId)
        }

        val uniqueWorkName = AutonomyNudgeEngine.uniqueWorkName(taskId)
        val request = OneTimeWorkRequestBuilder<AutonomyNudgeWorker>()
            .setInitialDelay(1, TimeUnit.HOURS)
            .setInputData(workDataOf("taskId" to taskId))
            .addTag(AutonomyNudgeEngine.workTag(taskId))
            .addTag(AutonomyNudgeEngine.globalTag())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueWorkName,
            ExistingWorkPolicy.REPLACE,
            request
        )

        val canNotify = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

        if (canNotify) {
            val confirmation = NotificationCompat.Builder(context, "autonomy_nudge")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(context.getString(R.string.autonomy_snooze_confirmation_title))
                .setContentText(context.getString(R.string.autonomy_snooze_confirmation_body))
                .setAutoCancel(true)
                .build()

            NotificationManagerCompat.from(context).notify(uniqueWorkName.hashCode(), confirmation)
        }
    }
}
