package com.neuroflow.app

import android.app.Application
import android.os.StrictMode
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.*
import com.neuroflow.app.data.local.DatabaseCleaner
import com.neuroflow.app.data.repository.TaskRepository
import com.neuroflow.app.presentation.launcher.data.AppRepository
import com.neuroflow.app.data.local.UserPreferencesDataStore
import com.neuroflow.app.kiosk.DeviceOwnerKioskManager
import com.neuroflow.app.presentation.launcher.hyperfocus.data.HyperFocusDataStore
import com.neuroflow.app.presentation.launcher.work.ScheduleAutoTasksWorker
import com.neuroflow.app.worker.DistractionSyncWorker
import com.neuroflow.app.worker.createNotificationChannels
import com.neuroflow.app.worker.scheduleNotificationWorkers
import com.neuroflow.app.worker.scheduleSleepPressureRefreshWorker
import com.neuroflow.app.worker.EnergyTelemetryCleanupScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class NeuroFlowApplication : Application(), Configuration.Provider {

    companion object {
        private const val INSTALL_MARKER_FILE = "install_marker_v1"
    }

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var appRepository: AppRepository
    @Inject lateinit var taskRepository: TaskRepository
    @Inject lateinit var userPreferencesDataStore: UserPreferencesDataStore
    @Inject lateinit var hyperFocusDataStore: HyperFocusDataStore
    @Inject lateinit var databaseCleaner: DatabaseCleaner
    @Inject lateinit var energyTelemetryCleanupScheduler: EnergyTelemetryCleanupScheduler

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

        enforceRestoreVersionPolicy()

        createNotificationChannels(this)
        scheduleDailyWorkers()

        // Pre-warm AppRepository on IO thread
        applicationScope.launch {
            appRepository.loadAll()
        }

        DeviceOwnerKioskManager.migrateStrictModeDefault(this)
        DeviceOwnerKioskManager.enableHybridProtection(this)

        // Re-sync Hyper Focus self-protection on process start.
        // This keeps restrictions consistent even after process death/restart.
        applicationScope.launch {
            val hfPrefs = hyperFocusDataStore.current()
            DeviceOwnerKioskManager.setHyperFocusSelfProtection(this@NeuroFlowApplication, hfPrefs.isActive)
            DeviceOwnerKioskManager.syncHyperFocusBlockedPackagesSuspension(
                this@NeuroFlowApplication,
                hfPrefs.blockedPackages,
                hfPrefs.isActive && DeviceOwnerKioskManager.isStrictKioskEnforcementEnabled(this@NeuroFlowApplication)
            )
        }

        // Seed the persistent tag catalog from all existing tasks.
        applicationScope.launch {
            val allTags = taskRepository.getAllTasks()
                .flatMap { task -> task.tags.split(",") }
                .map { it.trim() }
                .filter { it.isNotBlank() }
            userPreferencesDataStore.mergeTagCatalog(allTags)
        }
    }

    private fun enforceRestoreVersionPolicy() {
        val firstLaunchAfterInstall = markFirstLaunchAfterInstall()
        runBlocking {
            databaseCleaner.enforceRestoreVersionPolicy(firstLaunchAfterInstall)
        }
    }

    private fun markFirstLaunchAfterInstall(): Boolean {
        val marker = File(noBackupFilesDir, INSTALL_MARKER_FILE)
        if (marker.exists()) return false
        return runCatching {
            marker.parentFile?.mkdirs()
            marker.writeText("1")
            true
        }.getOrDefault(true)
    }

    private fun scheduleDailyWorkers() {
        val workManager = WorkManager.getInstance(this)

        // Notification workers (daily plan, streak, escalation) follow user-configured hours/toggles.
        applicationScope.launch {
            val prefs = userPreferencesDataStore.preferencesFlow.first()
            scheduleNotificationWorkers(this@NeuroFlowApplication, prefs)
        }

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

        // ScheduleAutoTasksWorker — runs periodically (configurable throttle) to auto-schedule eligible tasks
        // Gated by autoSchedulingEnabled preference
        applicationScope.launch {
            val prefs = userPreferencesDataStore.preferencesFlow.first()
            if (prefs.autoSchedulingEnabled) {
                val throttleMinutes = prefs.autoSchedulingBackgroundThrottleMinutes
                val autoScheduleRequest = ScheduleAutoTasksWorker.buildPeriodicWorkRequest(throttleMinutes)
                workManager.enqueueUniquePeriodicWork(
                    ScheduleAutoTasksWorker.WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    autoScheduleRequest
                )
            }
        }

        // Foreground refresh loop — while app process is alive, request a scheduler pass every 5 minutes.
        // Background periodic work remains the fallback when the app process is not active.
        applicationScope.launch {
            while (true) {
                val prefs = userPreferencesDataStore.preferencesFlow.first()
                if (prefs.autoSchedulingEnabled) {
                    workManager.enqueueUniqueWork(
                        ScheduleAutoTasksWorker.FOREGROUND_TICK_WORK_NAME,
                        ExistingWorkPolicy.KEEP,
                        ScheduleAutoTasksWorker.buildOneTimeWorkRequest()
                    )
                }
                delay(TimeUnit.MINUTES.toMillis(5))
            }
        }

        // DistractionSyncWorker — runs once daily to refresh per-task distraction scores
        // Silently skips if PACKAGE_USAGE_STATS permission is not granted
        DistractionSyncWorker.schedulePeriodic(this)

        // EnergyTelemetryCleanupWorker — runs once daily to enforce retention policy on prediction database
        energyTelemetryCleanupScheduler.scheduleCleanup()

        // SleepPressureRefreshWorker — runs once daily to refresh sleep pressure and create automatic fallback sleep logs
        applicationScope.launch {
            val prefs = userPreferencesDataStore.preferencesFlow.first()
            scheduleSleepPressureRefreshWorker(this@NeuroFlowApplication, prefs)
        }
    }
}
