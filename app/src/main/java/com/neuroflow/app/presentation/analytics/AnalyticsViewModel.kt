package com.neuroflow.app.presentation.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neuroflow.app.data.local.UserPreferences
import com.neuroflow.app.data.local.UserPreferencesDataStore
import com.neuroflow.app.data.repository.SessionRepository
import com.neuroflow.app.data.repository.TaskRepository
import com.neuroflow.app.domain.engine.AnalyticsEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AnalyticsUiState(
    val summary: AnalyticsEngine.AnalyticsSummary? = null,
    val preferences: UserPreferences = UserPreferences(),
    val isLoading: Boolean = true
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val sessionRepository: SessionRepository,
    private val preferencesDataStore: UserPreferencesDataStore
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
            _uiState.value = state
        }.launchIn(viewModelScope)
    }

    fun resetEstimationData() {
        viewModelScope.launch {
            taskRepository.resetEstimationErrors()
        }
    }
}
