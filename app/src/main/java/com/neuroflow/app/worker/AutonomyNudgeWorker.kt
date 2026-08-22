package com.neuroflow.app.worker

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.neuroflow.app.MainActivity
import com.neuroflow.app.R
import com.neuroflow.app.data.local.UserPreferencesDataStore
import com.neuroflow.app.data.local.entity.effectiveReminderTargetMillis
import com.neuroflow.app.data.repository.TaskRepository
import com.neuroflow.app.domain.model.TaskStatus
import com.neuroflow.app.receiver.NudgeSnoozeReceiver
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class AutonomyNudgeWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val taskRepository: TaskRepository,
    private val preferencesDataStore: UserPreferencesDataStore
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val prefs = preferencesDataStore.preferencesFlow.first()
        if (!prefs.autonomyNudgeNotificationsEnabled) return Result.success()

        val taskId = inputData.getString("taskId") ?: return Result.success()

        return try {
            val task = taskRepository.getById(taskId)

            // Idempotent: skip if task gone or already started
            if (task == null || task.status != TaskStatus.ACTIVE || task.sessionCount > 0) return Result.success()

            val now = System.currentTimeMillis()
            val dueAt = task.effectiveReminderTargetMillis()
            // Don't nudge tasks that are clearly in the future.
            if (dueAt != null && dueAt > now + 10 * 60_000L) return Result.success()

            val notificationId = taskId.hashCode()

            // "I'm not ready yet" — re-enqueue via BroadcastReceiver (no app open needed)
            val notReadyPendingIntent = PendingIntent.getBroadcast(
                appContext,
                notificationId + 1,
                Intent(appContext, NudgeSnoozeReceiver::class.java).apply {
                    putExtra("taskId", taskId)
                    putExtra("notificationId", notificationId)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // "I'm avoiding it" — open MainActivity with WOOP_REFLECT action
            val woopReflectPendingIntent = PendingIntent.getActivity(
                appContext,
                notificationId + 2,
                Intent(appContext, MainActivity::class.java).apply {
                    action = "WOOP_REFLECT"
                    putExtra("taskId", taskId)
                    putExtra("notificationId", notificationId)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(appContext, "autonomy_nudge")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(appContext.getString(R.string.autonomy_nudge_title))
                .setContentText(appContext.getString(R.string.autonomy_nudge_summary))
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        appContext.getString(R.string.autonomy_nudge_bigtext_task_pending, task.title) + "\n" +
                            appContext.getString(R.string.autonomy_nudge_bigtext_not_ready) + "\n" +
                            appContext.getString(R.string.autonomy_nudge_bigtext_woop)
                    )
                )
                .setAutoCancel(true)
                .addAction(0, appContext.getString(R.string.autonomy_nudge_action_not_ready), notReadyPendingIntent)
                .addAction(0, appContext.getString(R.string.autonomy_nudge_action_woop), woopReflectPendingIntent)
                .build()

            NotificationManagerCompat.from(appContext).apply {
                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
                    androidx.core.content.ContextCompat.checkSelfPermission(
                        appContext, android.Manifest.permission.POST_NOTIFICATIONS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    notify(notificationId, notification)
                }
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
