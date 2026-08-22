package com.neuroflow.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neuroflow.app.data.local.UserPreferences
import com.neuroflow.app.data.local.UserPreferencesDataStore
import com.neuroflow.app.data.repository.GoalRepository
import com.neuroflow.app.data.repository.SessionRepository
import com.neuroflow.app.data.repository.TaskRepository
import com.neuroflow.app.domain.model.AppTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesDataStore: UserPreferencesDataStore,
    private val taskRepository: TaskRepository,
    private val sessionRepository: SessionRepository,
    private val goalRepository: GoalRepository
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = preferencesDataStore.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    fun updatePreferences(update: (UserPreferences) -> UserPreferences) {
        viewModelScope.launch {
            preferencesDataStore.updatePreferences(update)
        }
    }

    fun setTheme(theme: AppTheme) {
        updatePreferences { it.copy(theme = theme) }
    }

    fun clearAllData() {
        viewModelScope.launch {
            taskRepository.deleteAll()
            sessionRepository.deleteAll()
            goalRepository.deleteAll()
        }
    }
}
