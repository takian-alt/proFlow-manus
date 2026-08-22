package com.neuroflow.app.presentation.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.neuroflow.app.data.local.DatabaseCleaner
import com.neuroflow.app.data.local.UserPreferences
import com.neuroflow.app.data.local.UserPreferencesDataStore
import com.neuroflow.app.data.local.entity.SleepLogEntity
import com.neuroflow.app.domain.engine.AutonomyNudgeEngine
import com.neuroflow.app.domain.model.AppTheme
import com.neuroflow.app.domain.repository.EnergyMetricsRepository
import com.neuroflow.app.domain.repository.PeakEnergyRepository
import com.neuroflow.app.domain.repository.SleepPressureRepository
import com.neuroflow.app.worker.scheduleNotificationWorkers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesDataStore: UserPreferencesDataStore,
    private val sleepPressureRepository: SleepPressureRepository,
    private val energyMetricsRepository: EnergyMetricsRepository,
    private val peakEnergyRepository: PeakEnergyRepository,
    private val databaseCleaner: DatabaseCleaner
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = preferencesDataStore.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    val sleepLogs: StateFlow<List<SleepLogEntity>> = sleepPressureRepository.observeSleepLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val peakDetection = peakEnergyRepository.peakEnergyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _sleepLogInputError = MutableStateFlow<String?>(null)
    val sleepLogInputError: StateFlow<String?> = _sleepLogInputError.asStateFlow()

    init {
        refreshSleepPressureNow()
    }

    fun updatePreferences(update: (UserPreferences) -> UserPreferences) {
        viewModelScope.launch {
            val current = preferences.value
            val updated = update(current)
            preferencesDataStore.updatePreferences { updated }

            val scheduleChanged =
                current.dailyPlanNotificationsEnabled != updated.dailyPlanNotificationsEnabled ||
                    current.streakNotificationsEnabled != updated.streakNotificationsEnabled ||
                    current.deadlineEscalationNotificationsEnabled != updated.deadlineEscalationNotificationsEnabled ||
                    current.dailyPlanNotificationHour != updated.dailyPlanNotificationHour ||
                    current.streakCheckNotificationHour != updated.streakCheckNotificationHour

            if (scheduleChanged) {
                scheduleNotificationWorkers(context, updated)
            }

            if (current.autonomyNudgeNotificationsEnabled && !updated.autonomyNudgeNotificationsEnabled) {
                WorkManager.getInstance(context).cancelAllWorkByTag(AutonomyNudgeEngine.globalTag())
            }
            if (current.deadlineReminderNotificationsEnabled && !updated.deadlineReminderNotificationsEnabled) {
                WorkManager.getInstance(context).cancelAllWorkByTag("task_reminder_all")
            }
        }
    }

    fun setTheme(theme: AppTheme) {
        updatePreferences { it.copy(theme = theme) }
    }

    fun refreshSleepPressureNow() {
        viewModelScope.launch {
            sleepPressureRepository.refreshCurrentPressure()
        }
    }

    fun addSleepLog(startAtMillis: Long, endAtMillis: Long) {
        viewModelScope.launch {
            when (sleepPressureRepository.addSleepLog(startAtMillis, endAtMillis)) {
                is SleepPressureRepository.AddSleepLogResult.Added -> {
                    _sleepLogInputError.value = null
                }
                SleepPressureRepository.AddSleepLogResult.OverlapsExisting -> {
                    _sleepLogInputError.value = "This sleep period overlaps an existing sleep log."
                }
                is SleepPressureRepository.AddSleepLogResult.TooLong -> {
                    _sleepLogInputError.value = "Sleep log is too long. Max allowed is 16 hours."
                }
            }
        }
    }

    fun clearSleepLogInputError() {
        _sleepLogInputError.value = null
    }

    fun deleteSleepLog(logId: String) {
        viewModelScope.launch {
            sleepPressureRepository.deleteSleepLog(logId)
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            databaseCleaner.clearAllDataAndInvalidateBackup()
            scheduleNotificationWorkers(context, UserPreferences())
            WorkManager.getInstance(context).cancelAllWorkByTag(AutonomyNudgeEngine.globalTag())
            WorkManager.getInstance(context).cancelAllWorkByTag("task_reminder_all")
            _sleepLogInputError.value = null
        }
    }

    fun clearEnergyTelemetry() {
        viewModelScope.launch {
            energyMetricsRepository.clearAllPredictions()
        }
    }
}
