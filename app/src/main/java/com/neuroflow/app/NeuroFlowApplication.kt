package com.neuroflow.app

import android.app.Application
import androidx.work.*
import com.neuroflow.app.worker.DailyPlanWorker
import com.neuroflow.app.worker.DeadlineEscalationWorker
import com.neuroflow.app.worker.StreakCheckWorker
import com.neuroflow.app.worker.createNotificationChannels
import dagger.hilt.android.HiltAndroidApp
import java.util.Calendar
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class NeuroFlowApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannels(this)
        scheduleDailyWorkers()
    }

    private fun scheduleDailyWorkers() {
        val workManager = WorkManager.getInstance(this)

        // DailyPlanWorker — fires every morning at 7am
        val dailyPlanRequest = PeriodicWorkRequestBuilder<DailyPlanWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delayUntilHour(7), TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build())
            .build()
        workManager.enqueueUniquePeriodicWork(
            "daily_plan",
            ExistingPeriodicWorkPolicy.KEEP,
            dailyPlanRequest
        )

        // StreakCheckWorker — fires every evening at 9pm
        val streakCheckRequest = PeriodicWorkRequestBuilder<StreakCheckWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delayUntilHour(21), TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build())
            .build()
        workManager.enqueueUniquePeriodicWork(
            "streak_check",
            ExistingPeriodicWorkPolicy.KEEP,
            streakCheckRequest
        )

        // DeadlineEscalationWorker — runs every 4 hours, promotes SCHEDULE → DO_FIRST when deadline ≤ 48h
        val escalationRequest = PeriodicWorkRequestBuilder<DeadlineEscalationWorker>(4, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build())
            .build()
        workManager.enqueueUniquePeriodicWork(
            "deadline_escalation",
            ExistingPeriodicWorkPolicy.KEEP,
            escalationRequest
        )
    }

    /** Returns milliseconds until the next occurrence of the given hour (0-23) */
    private fun delayUntilHour(hour: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.before(now)) target.add(Calendar.DAY_OF_YEAR, 1)
        return target.timeInMillis - now.timeInMillis
    }
}
