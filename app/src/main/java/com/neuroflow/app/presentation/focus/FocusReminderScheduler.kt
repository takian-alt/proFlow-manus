package com.neuroflow.app.presentation.focus

import android.content.Context
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.neuroflow.app.data.local.entity.TaskEntity
import com.neuroflow.app.data.local.entity.effectiveReminderTargetMillis
import com.neuroflow.app.worker.TaskReminderWorker
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FocusReminderScheduler @Inject constructor() {

    fun schedule(task: TaskEntity, notificationsEnabled: Boolean, applicationContext: Context) {
        val workManager = WorkManager.getInstance(applicationContext)
        val allReminderTags = listOf(1, 2, 4, 8).map { flag -> "reminder_${task.id}_$flag" }
        allReminderTags.forEach { tag -> workManager.cancelAllWorkByTag(tag) }

        if (!notificationsEnabled) return

        val targetMs = task.effectiveReminderTargetMillis() ?: return

        val now = System.currentTimeMillis()
        val flags = task.reminderFlags
        val offsets = listOf(1 to 15L, 2 to 30L, 4 to 60L, 8 to 1440L)

        offsets.forEach { (flag, minutesBefore) ->
            if (flags and flag == 0) return@forEach

            val fireAt = targetMs - minutesBefore * 60_000L
            val delayMs = fireAt - now
            if (delayMs <= 0) return@forEach

            val data = Data.Builder()
                .putString("taskId", task.id)
                .putString("taskTitle", task.title)
                .putLong("targetMs", targetMs)
                .putLong("minutesBefore", minutesBefore)
                .build()

            val request = OneTimeWorkRequestBuilder<TaskReminderWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .addTag("reminder_${task.id}_${flag}")
                .addTag("task_reminder_all")
                .build()

            workManager.enqueue(request)
        }
    }
}
