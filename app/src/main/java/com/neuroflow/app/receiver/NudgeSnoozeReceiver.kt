package com.neuroflow.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.neuroflow.app.worker.AutonomyNudgeWorker
import java.util.concurrent.TimeUnit

/**
 * Handles the "I'm not ready yet" snooze action from the autonomy nudge notification.
 * Re-enqueues [AutonomyNudgeWorker] with a 1-hour delay without requiring the app to open.
 */
class NudgeSnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra("taskId") ?: return
        val request = OneTimeWorkRequestBuilder<AutonomyNudgeWorker>()
            .setInitialDelay(1, TimeUnit.HOURS)
            .setInputData(workDataOf("taskId" to taskId))
            .addTag("autonomy_nudge_$taskId")
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
