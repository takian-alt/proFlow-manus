package com.neuroflow.app.presentation.matrix

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neuroflow.app.data.local.UserPreferences
import com.neuroflow.app.data.local.UserPreferencesDataStore
import com.neuroflow.app.data.local.entity.TaskEntity
import com.neuroflow.app.data.local.entity.TimeSessionEntity
import com.neuroflow.app.data.repository.SessionRepository
import com.neuroflow.app.data.repository.TaskRepository
import com.neuroflow.app.domain.engine.AnalyticsEngine
import com.neuroflow.app.domain.engine.TaskScoringEngine
import com.neuroflow.app.domain.model.Quadrant
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
    private val sessionRepository: SessionRepository,
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
            taskRepository.completeAndRecur(task, now)
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

    fun completeTaskWithTime(task: TaskEntity, manualMinutes: Float) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val session = TimeSessionEntity(
                taskId = task.id,
                startedAt = now - (manualMinutes * 60_000f).toLong(),
                endedAt = now,
                durationMinutes = manualMinutes,
                sessionType = "MANUAL_LOG"
            )
            sessionRepository.insert(session)
            val mape = if (task.estimatedDurationMinutes > 0)
                AnalyticsEngine.computeMape(task.estimatedDurationMinutes.toFloat(), manualMinutes)
            else null
            val smape = if (task.estimatedDurationMinutes > 0)
                AnalyticsEngine.computeSmape(task.estimatedDurationMinutes.toFloat(), manualMinutes)
            else null
            val taskWithTime = task.copy(
                actualDurationMinutes = manualMinutes,
                totalTimeTrackedMinutes = task.totalTimeTrackedMinutes + manualMinutes,
                sessionCount = task.sessionCount + 1,
                estimationErrorMape = mape,
                estimationErrorSmape = smape,
                updatedAt = now
            )
            taskRepository.completeAndRecur(taskWithTime, now)
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
                    totalFocusMinutes = prefs.totalFocusMinutes + manualMinutes.toInt(),
                    dailyStreak = newStreak,
                    lastActiveDate = now,
                    longestStreak = maxOf(prefs.longestStreak, newStreak)
                )
            }
        }
    }

    /** Logs time against a task without completing it (used from Log Time screen). */
    fun logTimeOnly(task: TaskEntity, manualMinutes: Float) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val session = TimeSessionEntity(
                taskId = task.id,
                startedAt = now - (manualMinutes * 60_000f).toLong(),
                endedAt = now,
                durationMinutes = manualMinutes,
                sessionType = "MANUAL_LOG"
            )
            sessionRepository.insert(session)
            taskRepository.update(
                task.copy(
                    totalTimeTrackedMinutes = task.totalTimeTrackedMinutes + manualMinutes,
                    sessionCount = task.sessionCount + 1,
                    updatedAt = now
                )
            )
            preferencesDataStore.updatePreferences { prefs ->
                prefs.copy(totalFocusMinutes = prefs.totalFocusMinutes + manualMinutes.toInt())
            }
        }
    }
}
