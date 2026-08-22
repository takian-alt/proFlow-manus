package com.neuroflow.app.presentation.matrix

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neuroflow.app.data.local.UserPreferences
import com.neuroflow.app.data.local.UserPreferencesDataStore
import com.neuroflow.app.data.local.entity.TaskEntity
import com.neuroflow.app.data.repository.TaskRepository
import com.neuroflow.app.domain.engine.TaskScoringEngine
import com.neuroflow.app.domain.model.Quadrant
import com.neuroflow.app.domain.model.TaskStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MatrixUiState(
    val tasksByQuadrant: Map<Quadrant, List<TaskEntity>> = emptyMap(),
    val quadrantCounts: Map<Quadrant, Int> = emptyMap(),
    val topScoredTaskId: String? = null,
    val allActiveTasks: List<TaskEntity> = emptyList(),
    val preferences: UserPreferences = UserPreferences(),
    val isLoading: Boolean = true
)

@HiltViewModel
class MatrixViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val preferencesDataStore: UserPreferencesDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(MatrixUiState())
    val uiState: StateFlow<MatrixUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                taskRepository.observeActiveTasks(),
                preferencesDataStore.preferencesFlow
            ) { tasks, prefs ->
                val byQuadrant = Quadrant.entries.associateWith { q ->
                    tasks.filter { it.quadrant == q }
                }
                val counts = byQuadrant.mapValues { it.value.size }
                val sorted = TaskScoringEngine.sortedByScore(tasks, prefs)
                MatrixUiState(
                    tasksByQuadrant = byQuadrant,
                    quadrantCounts = counts,
                    topScoredTaskId = sorted.firstOrNull()?.id,
                    allActiveTasks = tasks,
                    preferences = prefs,
                    isLoading = false
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun addTask(task: TaskEntity) {
        viewModelScope.launch {
            val autoQuadrant = TaskScoringEngine.autoAssignQuadrant(task, System.currentTimeMillis())
            taskRepository.insert(task.copy(quadrant = autoQuadrant))
        }
    }

    fun insertTask(task: TaskEntity) {
        viewModelScope.launch {
            taskRepository.insert(task)
        }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            taskRepository.delete(task)
        }
    }

    fun updateTask(task: TaskEntity) {
        viewModelScope.launch {
            taskRepository.update(task.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    fun completeTask(task: TaskEntity) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            taskRepository.update(
                task.copy(
                    status = TaskStatus.COMPLETED,
                    completedAt = now,
                    updatedAt = now
                )
            )
            preferencesDataStore.updatePreferences { prefs ->
                val todayStart = run {
                    val cal = java.util.Calendar.getInstance()
                    cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0)
                    cal.set(java.util.Calendar.SECOND, 0); cal.set(java.util.Calendar.MILLISECOND, 0)
                    cal.timeInMillis
                }
                val yesterdayStart = todayStart - 86_400_000L
                val newStreak = when {
                    prefs.lastActiveDate >= todayStart -> prefs.dailyStreak
                    prefs.lastActiveDate >= yesterdayStart -> prefs.dailyStreak + 1
                    else -> 1
                }
                prefs.copy(
                    totalTasksCompleted = prefs.totalTasksCompleted + 1,
                    dailyStreak = newStreak,
                    lastActiveDate = now,
                    longestStreak = maxOf(prefs.longestStreak, newStreak)
                )
            }
        }
    }
}
