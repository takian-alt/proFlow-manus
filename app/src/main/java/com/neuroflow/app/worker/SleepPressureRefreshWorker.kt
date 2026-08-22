package com.neuroflow.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.neuroflow.app.data.local.UserPreferences
import com.neuroflow.app.domain.repository.SleepPressureRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Daily worker that refreshes sleep pressure and creates automatic fallback sleep logs.
 *
 * This ensures that even if the user doesn't open the app, automatic sleep logs
 * will be created based on their configured sleep/wake hours when appropriate.
 *
 * Runs once daily at a configured time (typically after wake-up time + 12 hours).
 */
@HiltWorker
class SleepPressureRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val sleepPressureRepository: SleepPressureRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            // Refresh current pressure, which will trigger automatic fallback
            // sleep log creation if conditions are met
            sleepPressureRepository.refreshCurrentPressure()
            Result.success()
        } catch (e: Exception) {
            // Retry on failure, but don't fail permanently
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    companion object {
        const val WORK_NAME = "sleep_pressure_refresh_work"
        const val WORK_TAG = "sleep_pressure_refresh"
    }
}
/**
 * Schedules the daily sleep pressure refresh worker.
 *
 * The worker runs daily at 7 PM (19:00) by default, which is typically 12+ hours
 * after most users' wake time, allowing the automatic fallback logic to trigger.
 *
 * @param context Android context
 * @param prefs User preferences (currently unused, but included for consistency with other schedulers)
 */
fun scheduleSleepPressureRefreshWorker(context: Context, prefs: UserPreferences) {
    val workManager = WorkManager.getInstance(context)

    // Schedule to run at 7 PM daily (19:00)
    // This is typically 12+ hours after wake time for most users,
    // allowing automatic fallback sleep log creation to trigger
    val targetHour = 19
    val initialDelayMs = delayUntilHour(targetHour)

    val sleepRefreshRequest = PeriodicWorkRequestBuilder<SleepPressureRefreshWorker>(1, TimeUnit.DAYS)
        .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
        .addTag(SleepPressureRefreshWorker.WORK_TAG)
        .build()

    workManager.enqueueUniquePeriodicWork(
        SleepPressureRefreshWorker.WORK_NAME,
        ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
        sleepRefreshRequest
    )
}
