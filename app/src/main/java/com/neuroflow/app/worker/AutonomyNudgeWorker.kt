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
import com.neuroflow.app.data.repository.TaskRepository
import com.neuroflow.app.receiver.NudgeSnoozeReceiver
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class AutonomyNudgeWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val taskRepository: TaskRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getString("taskId") ?: return Result.success()

        return try {
            val task = taskRepository.getById(taskId)

            // Idempotent: skip if task gone or already started
            if (task == null || task.sessionCount > 0) return Result.success()

            val notificationId = taskId.hashCode()

            // "I'm not ready yet" — re-enqueue via BroadcastReceiver (no app open needed)
            val notReadyPendingIntent = PendingIntent.getBroadcast(
                appContext,
                notificationId + 1,
                Intent(appContext, NudgeSnoozeReceiver::class.java).apply {
                    putExtra("taskId", taskId)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // "It feels too big" — open MainActivity with SPLIT_TASK action
            val splitTaskPendingIntent = PendingIntent.getActivity(
                appContext,
                notificationId + 2,
                Intent(appContext, MainActivity::class.java).apply {
                    action = "SPLIT_TASK"
                    putExtra("taskId", taskId)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // "I'm avoiding it" — open MainActivity with WOOP_REFLECT action
            val woopReflectPendingIntent = PendingIntent.getActivity(
                appContext,
                notificationId + 3,
                Intent(appContext, MainActivity::class.java).apply {
                    action = "WOOP_REFLECT"
                    putExtra("taskId", taskId)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(appContext, "autonomy_nudge")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("You haven't started \"${task.title}\" yet")
                .setContentText("What's getting in the way? Tap an option below.")
                .setAutoCancel(true)
                .addAction(0, "I'm not ready yet", notReadyPendingIntent)
                .addAction(0, "It feels too big", splitTaskPendingIntent)
                .addAction(0, "I'm avoiding it", woopReflectPendingIntent)
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
