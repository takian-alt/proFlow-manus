package com.neuroflow.app.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.neuroflow.app.kiosk.DeviceOwnerKioskManager

/**
 * Periodic watchdog that re-applies kiosk protections.
 * This complements alarm-based scheduling and boot-time initialization.
 */
class KioskPolicyWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            DeviceOwnerKioskManager.enableHybridProtection(applicationContext)
            Log.d(TAG, "Hybrid kiosk protection refreshed")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh kiosk protection", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "KioskPolicyWorker"
    }
}
