package com.neuroflow.app

import android.app.Application
import android.os.StrictMode
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.*
import com.neuroflow.app.presentation.launcher.data.AppRepository
import com.neuroflow.app.worker.DailyPlanWorker
import com.neuroflow.app.worker.DeadlineEscalationWorker
import com.neuroflow.app.worker.DistractionSyncWorker
import com.neuroflow.app.worker.StreakCheckWorker
import com.neuroflow.app.worker.createNotificationChannels
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class NeuroFlowApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var appRepository: AppRepository

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
    override fun onCreate() {
        super.onCreate()

        // Enable StrictMode in debug builds to catch main-thread IO violations
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .penaltyDeath()  // Crash on violations to ensure they're fixed
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .build()
            )
        }

        createNotificationChannels(this)
        scheduleDailyWorkers()

        // Pre-warm AppRepository on IO thread
        applicationScope.launch {
            appRepository.loadAll()
        }
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

        // FocusWidgetUpdateWorker — runs every 15 minutes to keep the home screen widget fresh
        val widgetUpdateRequest = PeriodicWorkRequestBuilder<com.neuroflow.app.worker.FocusWidgetUpdateWorker>(
            15, TimeUnit.MINUTES
        )
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build())
            .build()
        workManager.enqueueUniquePeriodicWork(
            "focus_widget_update",
            ExistingPeriodicWorkPolicy.KEEP,
            widgetUpdateRequest
        )

        // DistractionSyncWorker — runs once daily to refresh per-task distraction scores
        // Silently skips if PACKAGE_USAGE_STATS permission is not granted
        DistractionSyncWorker.schedulePeriodic(this)
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
