package com.neuroflow.app.presentation.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neuroflow.app.data.local.UserPreferences
import com.neuroflow.app.data.local.UserPreferencesDataStore
import com.neuroflow.app.data.local.entity.ContractOutcome
import com.neuroflow.app.data.local.entity.EnergyPredictionEntity
import com.neuroflow.app.data.local.entity.UlyssesContractEntity
import com.neuroflow.app.data.repository.SessionRepository
import com.neuroflow.app.data.repository.TaskRepository
import com.neuroflow.app.data.repository.UlyssesContractRepository
import com.neuroflow.app.domain.engine.AnalyticsEngine
import com.neuroflow.app.domain.repository.EnergyMetricsRepository
import com.neuroflow.app.domain.repository.EnergyScoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AnalyticsUiState(
    val summary: AnalyticsEngine.AnalyticsSummary? = null,
    val preferences: UserPreferences = UserPreferences(),
    val energy: EnergyScoreRepository.EnergyUiModel? = null,
    val isLoading: Boolean = true,
    val activeContracts: List<UlyssesContractEntity> = emptyList(),
    val archivedContracts: List<UlyssesContractEntity> = emptyList(),
    val interruptionPauseResumeCount: Int = 0,
    val interruptionAppSwitchCount: Int = 0,
    val interruptionBurstCount: Int = 0,
    val interruptionTrend7d: List<Pair<String, Int>> = emptyList(),
    val interruptionRatePerHourTrend7d: List<Pair<String, Float>> = emptyList(),
    val abstentionRateTrend7d: List<Pair<String, Float>> = emptyList()
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val sessionRepository: SessionRepository,
    private val preferencesDataStore: UserPreferencesDataStore,
    private val contractRepository: UlyssesContractRepository,
    private val energyScoreRepository: EnergyScoreRepository,
    private val energyMetricsRepository: EnergyMetricsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    init {
        combine(
            taskRepository.observeAll(),
            sessionRepository.observeAll(),
            preferencesDataStore.preferencesFlow
        ) { tasks, sessions, prefs ->
            val summary = AnalyticsEngine.buildSummary(
                allTasks = tasks,
                allSessions = sessions,
                prefs = prefs
            )
            AnalyticsUiState(
                summary = summary,
                preferences = prefs,
                isLoading = false,
                interruptionPauseResumeCount = sessions.sumOf { it.pauseResumeCount },
                interruptionAppSwitchCount = sessions.sumOf { it.appSwitchCount },
                interruptionBurstCount = sessions.sumOf { it.interruptionBurstCount },
                interruptionTrend7d = interruptionTrend7d(sessions),
                interruptionRatePerHourTrend7d = interruptionRatePerHourTrend7d(sessions)
            )
        }.onEach { state ->
            _uiState.update {
                it.copy(
                    summary = state.summary,
                    preferences = state.preferences,
                    isLoading = state.isLoading,
                    interruptionPauseResumeCount = state.interruptionPauseResumeCount,
                    interruptionAppSwitchCount = state.interruptionAppSwitchCount,
                    interruptionBurstCount = state.interruptionBurstCount,
                    interruptionTrend7d = state.interruptionTrend7d,
                    interruptionRatePerHourTrend7d = state.interruptionRatePerHourTrend7d
                )
            }
        }.launchIn(viewModelScope)

        contractRepository.observeActive().onEach { contracts ->
            _uiState.update { it.copy(activeContracts = contracts) }
        }.launchIn(viewModelScope)

        contractRepository.observeArchived().onEach { contracts ->
            _uiState.update { it.copy(archivedContracts = contracts) }
        }.launchIn(viewModelScope)

        energyScoreRepository.observeEnergy().onEach { energy ->
            _uiState.update { it.copy(energy = energy) }
        }.launchIn(viewModelScope)

        val sevenDaysMillis = 7L * 24L * 60L * 60L * 1000L
        val cutoffMillis = System.currentTimeMillis() - sevenDaysMillis
        energyMetricsRepository.observeRecentPredictions(afterMillis = cutoffMillis).onEach { predictions ->
            _uiState.update {
                it.copy(abstentionRateTrend7d = buildAbstentionRateTrend7d(predictions))
            }
        }.launchIn(viewModelScope)
    }

    fun resetEstimationData() {
        viewModelScope.launch {
            taskRepository.resetEstimationErrors()
        }
    }

    private fun interruptionTrend7d(
        sessions: List<com.neuroflow.app.data.local.entity.TimeSessionEntity>
    ): List<Pair<String, Int>> {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(zone).toLocalDate()
        val counts = mutableMapOf<java.time.LocalDate, Int>()
        sessions.forEach { s ->
            val date = Instant.ofEpochMilli(s.startedAt).atZone(zone).toLocalDate()
            val score = s.pauseResumeCount + s.appSwitchCount + s.interruptionBurstCount
            counts[date] = (counts[date] ?: 0) + score
        }
        return (6 downTo 0).map { daysAgo ->
            val date = today.minusDays(daysAgo.toLong())
            val label = date.dayOfWeek.name.take(3)
            label.lowercase().replaceFirstChar { it.uppercase() } to (counts[date] ?: 0)
        }
    }

    private fun interruptionRatePerHourTrend7d(
        sessions: List<com.neuroflow.app.data.local.entity.TimeSessionEntity>
    ): List<Pair<String, Float>> {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(zone).toLocalDate()
        val interruptionByDate = mutableMapOf<java.time.LocalDate, Int>()
        val minutesByDate = mutableMapOf<java.time.LocalDate, Float>()
        sessions.forEach { s ->
            val date = Instant.ofEpochMilli(s.startedAt).atZone(zone).toLocalDate()
            val interruptions = s.pauseResumeCount + s.appSwitchCount + s.interruptionBurstCount
            interruptionByDate[date] = (interruptionByDate[date] ?: 0) + interruptions
            minutesByDate[date] = (minutesByDate[date] ?: 0f) + s.durationMinutes.coerceAtLeast(0f)
        }
        return (6 downTo 0).map { daysAgo ->
            val date = today.minusDays(daysAgo.toLong())
            val label = date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
            val interruptions = interruptionByDate[date] ?: 0
            val hours = (minutesByDate[date] ?: 0f) / 60f
            val rate = if (hours <= 0.05f) 0f else interruptions.toFloat() / hours
            label to rate
        }
    }
}

internal fun buildAbstentionRateTrend7d(
    predictions: List<EnergyPredictionEntity>,
    nowMillis: Long = System.currentTimeMillis()
): List<Pair<String, Float>> {
    val zone = ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val totalsByDate = mutableMapOf<java.time.LocalDate, Int>()
    val abstainedByDate = mutableMapOf<java.time.LocalDate, Int>()

    predictions.forEach { prediction ->
        val date = Instant.ofEpochMilli(prediction.predictedAtMillis).atZone(zone).toLocalDate()
        val daysDelta = java.time.temporal.ChronoUnit.DAYS.between(date, today)
        if (daysDelta !in 0L..6L) return@forEach

        totalsByDate[date] = (totalsByDate[date] ?: 0) + 1
        if (prediction.peakConfidence <= 0.4f) {
            abstainedByDate[date] = (abstainedByDate[date] ?: 0) + 1
        }
    }

    return (6 downTo 0).map { daysAgo ->
        val date = today.minusDays(daysAgo.toLong())
        val label = date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
        val total = totalsByDate[date] ?: 0
        val abstained = abstainedByDate[date] ?: 0
        val ratePercent = if (total == 0) 0f else (abstained.toFloat() / total.toFloat()) * 100f
        label to ratePercent
    }
}
