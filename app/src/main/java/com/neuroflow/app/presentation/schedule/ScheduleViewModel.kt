package com.neuroflow.app.presentation.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neuroflow.app.data.local.UserPreferencesDataStore
import com.neuroflow.app.data.local.entity.TaskEntity
import com.neuroflow.app.data.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

data class ScheduleUiState(
    val selectedDate: Long = todayStartMillis(),
    val tasksForDay: List<TaskEntity> = emptyList(),
    val lockedTasks: List<TaskEntity> = emptyList(),
    val allActiveTasks: List<TaskEntity> = emptyList(),
    val isLoading: Boolean = true,
    val workDayStart: Int = 8,
    val workDayEnd: Int = 20
)

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val preferencesDataStore: UserPreferencesDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    init {
        loadTasksForDate(_uiState.value.selectedDate)
        observeAllActive()
        observeWorkHours()
    }

    private fun observeWorkHours() {
        viewModelScope.launch {
            preferencesDataStore.preferencesFlow.collect { prefs ->
                _uiState.update { it.copy(workDayStart = prefs.workDayStart, workDayEnd = prefs.workDayEnd) }
            }
        }
    }

    private fun observeAllActive() {
        viewModelScope.launch {
            taskRepository.observeActiveTasks().collect { tasks ->
                _uiState.update { it.copy(allActiveTasks = tasks) }
            }
        }
    }

    fun selectDate(date: Long) {
        _uiState.update { it.copy(selectedDate = date) }
        loadTasksForDate(date)
    }

    fun nextDay() {
        val next = _uiState.value.selectedDate + 86_400_000
        selectDate(next)
    }

    fun previousDay() {
        val prev = _uiState.value.selectedDate - 86_400_000
        selectDate(prev)
    }

    private fun loadTasksForDate(date: Long) {
        viewModelScope.launch {
            taskRepository.observeTasksForDate(date).collect { tasks ->
                // Locked tasks are pinned — they cannot be reordered or auto-rescheduled
                val locked = tasks.filter { it.isScheduleLocked }
                val unlocked = tasks.filter { !it.isScheduleLocked }
                _uiState.update {
                    it.copy(
                        tasksForDay = unlocked,
                        lockedTasks = locked,
                        isLoading = false
                    )
                }
            }
        }
    }

    /** Assigns an existing task to a specific hour slot on the selected date. Locked tasks are skipped. */
    fun scheduleTask(task: TaskEntity, hour: Int) {
        if (task.isScheduleLocked) return
        viewModelScope.launch {
            val cal = Calendar.getInstance().apply {
                timeInMillis = _uiState.value.selectedDate
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val slotMillis = cal.timeInMillis
            taskRepository.update(
                task.copy(
                    scheduledDate = slotMillis,
                    scheduledTime = (hour * 3_600_000L),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    /** Inserts a brand-new task (from the NewTaskSheet). */
    fun insertTask(task: TaskEntity) {
        viewModelScope.launch { taskRepository.insert(task) }
    }
}

fun todayStartMillis(): Long {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}
