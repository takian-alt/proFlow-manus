package com.neuroflow.app.presentation.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neuroflow.app.data.local.UserPreferencesDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DrawerViewModel @Inject constructor(
    private val preferencesDataStore: UserPreferencesDataStore
) : ViewModel() {

    val preferences = preferencesDataStore.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun saveYearlyGoals(goals: List<String>) {
        viewModelScope.launch {
            preferencesDataStore.updatePreferences { it.copy(yearlyGoals = goals) }
        }
    }

    fun saveWeeklyGoals(goals: List<String>) {
        viewModelScope.launch {
            preferencesDataStore.updatePreferences { it.copy(weeklyGoals = goals) }
        }
    }
}
