package com.neuroflow.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.neuroflow.app.domain.repository.EnergyMetricsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.coroutineScope

/**
 * Phase 2: Background worker for energy telemetry maintenance.
 * 
 * Periodically enforces retention policies on energy prediction data:
 * - Deletes raw telemetry older than 30 days
 * - Runs daily (low frequency to avoid battery drain)
 * - Automatically restarted by WorkManager on device reboot
 */
@HiltWorker
class EnergyTelemetryCleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val energyMetricsRepository: EnergyMetricsRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = coroutineScope {
        return@coroutineScope try {
            // Enforce retention policy: delete telemetry older than 30 days
            val deletedCount = energyMetricsRepository.enforceRetentionPolicy()
            
            // Log for debugging but don't spam logs
            if (deletedCount > 0) {
                android.util.Log.d(
                    "EnergyTelemetry",
                    "Cleanup: deleted $deletedCount prediction records older than 30 days"
                )
            }
            
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("EnergyTelemetry", "Cleanup worker failed", e)
            // Retry with exponential backoff on failures
            Result.retry()
        }
    }
}

/**
 * Enqueues the energy telemetry cleanup worker.
 * Should be called once on app startup (e.g., from a One-Time Work initializer).
 */
@Singleton
class EnergyTelemetryCleanupScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun scheduleCleanup() {
        val cleanupRequest = PeriodicWorkRequestBuilder<EnergyTelemetryCleanupWorker>(
            24, TimeUnit.HOURS,
            5, TimeUnit.MINUTES  // Flex interval: run anytime within 5 minutes of 24hr mark
        ).setConstraints(
            Constraints.Builder()
                .setRequiresBatteryNotLow(true)  // Only run when battery is sufficient
                .build()
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "energy_telemetry_cleanup",
            ExistingPeriodicWorkPolicy.KEEP,  // Don't reschedule if already exists
            cleanupRequest
        )
    }
}
