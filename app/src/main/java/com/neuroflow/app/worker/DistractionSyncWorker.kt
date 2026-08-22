package com.neuroflow.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.neuroflow.app.data.local.dao.TaskDao
import com.neuroflow.app.data.local.dao.TimeSessionDao
import com.neuroflow.app.domain.engine.DistractionEngine
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Runs once daily (or on-demand) to recompute distraction scores for all active tasks
 * using UsageStatsManager data, then persists the scores back to Room.
 *
 * Requires PACKAGE_USAGE_STATS permission — silently skips if not granted.
 */
@HiltWorker
class DistractionSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val taskDao: TaskDao,
    private val sessionDao: TimeSessionDao
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (!DistractionEngine.hasUsagePermission(applicationContext)) return Result.success()

        return try {
            val tasks = taskDao.getActiveTasks()
            val sessions = sessionDao.getAll()

            val results = DistractionEngine.rankByDistraction(
                tasks = tasks,
                sessions = sessions,
                context = applicationContext
            )

            val scoreMap = results.associate { it.task.id to it.distractionScore }

            tasks.forEach { task ->
                val newScore = scoreMap[task.id] ?: return@forEach
                if (newScore != task.distractionScore) {
                    taskDao.update(task.copy(distractionScore = newScore))
                }
            }

            Result.success()
        } catch (e: Exception) {
            // Retry on transient errors (DB locked, OOM, etc.)
            // After WorkManager's max attempts the job is abandoned gracefully
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "distraction_sync"

        /** Enqueue a periodic daily sync. Safe to call multiple times — deduped by name. */
        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<DistractionSyncWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        /** Run immediately (e.g. after user grants permission). */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<DistractionSyncWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "${WORK_NAME}_immediate",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
