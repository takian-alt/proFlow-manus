package com.neuroflow.app.presentation.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neuroflow.app.data.local.UserPreferences
import com.neuroflow.app.data.local.UserPreferencesDataStore
import com.neuroflow.app.data.local.entity.ContractOutcome
import com.neuroflow.app.data.local.entity.UlyssesContractEntity
import com.neuroflow.app.data.repository.SessionRepository
import com.neuroflow.app.data.repository.TaskRepository
import com.neuroflow.app.data.repository.UlyssesContractRepository
import com.neuroflow.app.domain.engine.AnalyticsEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AnalyticsUiState(
    val summary: AnalyticsEngine.AnalyticsSummary? = null,
    val preferences: UserPreferences = UserPreferences(),
    val isLoading: Boolean = true,
    val activeContracts: List<UlyssesContractEntity> = emptyList(),
    val archivedContracts: List<UlyssesContractEntity> = emptyList()
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val sessionRepository: SessionRepository,
    private val preferencesDataStore: UserPreferencesDataStore,
    private val contractRepository: UlyssesContractRepository
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
            AnalyticsUiState(summary = summary, preferences = prefs, isLoading = false)
        }.onEach { state ->
            _uiState.update { it.copy(summary = state.summary, preferences = state.preferences, isLoading = state.isLoading) }
        }.launchIn(viewModelScope)

        contractRepository.observeActive().onEach { contracts ->
            _uiState.update { it.copy(activeContracts = contracts) }
        }.launchIn(viewModelScope)

        contractRepository.observeArchived().onEach { contracts ->
            _uiState.update { it.copy(archivedContracts = contracts) }
        }.launchIn(viewModelScope)
    }

    fun resetEstimationData() {
        viewModelScope.launch {
            taskRepository.resetEstimationErrors()
        }
    }
}
