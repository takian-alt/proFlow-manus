package com.neuroflow.app.domain.engine

import android.content.Context
import com.neuroflow.app.data.local.entity.TaskEntity

object AutonomyNudgeEngine {
    private const val TAG_PREFIX = "autonomy_nudge_"
    private const val GLOBAL_TAG = "autonomy_nudge_all"
    private const val DELAY_HOURS = 2L

    fun uniqueWorkName(taskId: String): String = "$TAG_PREFIX$taskId"
    fun workTag(taskId: String): String = "$TAG_PREFIX$taskId"
    fun globalTag(): String = GLOBAL_TAG

    fun scheduleNudge(context: Context, task: TaskEntity) {
        // Check POST_NOTIFICATIONS permission (Android 13+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) return
        }

        val data = androidx.work.Data.Builder()
            .putString("taskId", task.id)
            .build()

        val request = androidx.work.OneTimeWorkRequestBuilder<com.neuroflow.app.worker.AutonomyNudgeWorker>()
            .setInitialDelay(DELAY_HOURS, java.util.concurrent.TimeUnit.HOURS)
            .setInputData(data)
            .addTag(workTag(task.id))
            .addTag(globalTag())
            .build()

        androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueWorkName(task.id),
            androidx.work.ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancelNudge(context: Context, taskId: String) {
        androidx.work.WorkManager.getInstance(context)
            .cancelAllWorkByTag(workTag(taskId))
        androidx.work.WorkManager.getInstance(context)
            .cancelUniqueWork(uniqueWorkName(taskId))
    }
}
