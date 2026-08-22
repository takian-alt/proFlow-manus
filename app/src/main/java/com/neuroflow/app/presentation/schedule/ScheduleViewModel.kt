package com.neuroflow.app.presentation.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neuroflow.app.data.calendar.CalendarIntegrationRepository
import com.neuroflow.app.data.local.UserPreferencesDataStore
import com.neuroflow.app.data.local.dao.AutoScheduleTelemetryDao
import com.neuroflow.app.data.local.entity.AutoScheduleTelemetryEntity
import com.neuroflow.app.data.local.entity.TaskEntity
import com.neuroflow.app.data.local.entity.timelineStartMinuteOfDay
import com.neuroflow.app.data.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

data class ScheduleUiState(
    val selectedDate: Long = todayStartMillis(),
    val tasksForDay: List<TaskEntity> = emptyList(),
    val lockedTasks: List<TaskEntity> = emptyList(),
    val allActiveTasks: List<TaskEntity> = emptyList(),
    val pendingAutoScheduleReviews: List<AutoScheduleTelemetryEntity> = emptyList(),
    val isLoading: Boolean = true,
    val workDayStart: Int = 8,
    val workDayEnd: Int = 20
)

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val preferencesDataStore: UserPreferencesDataStore,
    private val autoScheduleTelemetryDao: AutoScheduleTelemetryDao,
    private val calendarIntegrationRepository: CalendarIntegrationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    init {
        loadTasksForDate(_uiState.value.selectedDate)
        observeAllActive()
        observePendingAutoScheduleReviews()
        observeWorkHours()
    }

    private fun observePendingAutoScheduleReviews() {
        viewModelScope.launch {
            autoScheduleTelemetryDao.observePending().collect { proposals ->
                _uiState.update { it.copy(pendingAutoScheduleReviews = proposals) }
            }
        }
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
        val next = Calendar.getInstance().apply {
            timeInMillis = _uiState.value.selectedDate
            add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
        selectDate(next)
    }

    fun previousDay() {
        val prev = Calendar.getInstance().apply {
            timeInMillis = _uiState.value.selectedDate
            add(Calendar.DAY_OF_YEAR, -1)
        }.timeInMillis
        selectDate(prev)
    }

    private fun loadTasksForDate(date: Long) {
        viewModelScope.launch {
            taskRepository.observeTasksForDate(date).collect { tasks ->
                val sortedTasks = tasks.sortedWith(
                    compareBy<TaskEntity> { it.timelineStartMinuteOfDay() }
                        .thenBy { it.createdAt }
                )
                // Keep locked tasks visible in the timeline so users can still see their time blocks.
                val locked = sortedTasks.filter { it.isScheduleLocked }
                _uiState.update {
                    it.copy(
                        tasksForDay = sortedTasks,
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
            // Build the full scheduled timestamp using Calendar (DST-safe)
            val cal = Calendar.getInstance().apply {
                timeInMillis = _uiState.value.selectedDate
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val scheduledMillis = cal.timeInMillis

            // Split into date and time components (DST-safe)
            val dateCal = Calendar.getInstance().apply {
                timeInMillis = scheduledMillis
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val scheduledDate = dateCal.timeInMillis
            val scheduledTime = scheduledMillis - scheduledDate

            taskRepository.update(
                task.copy(
                    scheduledDate = scheduledDate,
                    scheduledTime = scheduledTime,
                    isAutoScheduled = false,
                    lastAutoScheduledAt = null,  // Reset cooldown when user manually reschedules
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun approveAutoScheduleProposal(proposal: AutoScheduleTelemetryEntity) {
        viewModelScope.launch {
            val task = taskRepository.getById(proposal.taskId)
            val scheduledDate = proposal.selectedSlotDate
            val scheduledTime = proposal.selectedSlotTime
            if (task == null || task.isScheduleLocked || task.scheduledDate != null || scheduledDate == null || scheduledTime == null) {
                autoScheduleTelemetryDao.recordFeedback(
                    id = proposal.id,
                    reviewStatus = "REJECTED",
                    adjustment = "stale_or_conflicting_proposal",
                    outcome = "NOT_APPLIED",
                    feedbackAtMillis = System.currentTimeMillis()
                )
                return@launch
            }

            val now = System.currentTimeMillis()
            taskRepository.update(
                task.copy(
                    scheduledDate = scheduledDate,
                    scheduledTime = scheduledTime,
                    isAutoScheduled = true,
                    lastAutoScheduledAt = now,
                    updatedAt = now
                )
            )
            val prefs = preferencesDataStore.preferencesFlow.first()
            if (prefs.calendarIntegrationEnabled && prefs.calendarExportAcceptedSchedules) {
                calendarIntegrationRepository.createTaskEvent(
                    task = task.copy(
                        scheduledDate = scheduledDate,
                        scheduledTime = scheduledTime
                    ),
                    startMillis = scheduledDate + scheduledTime
                )
            }
            autoScheduleTelemetryDao.recordFeedback(
                id = proposal.id,
                reviewStatus = "APPROVED",
                adjustment = "approved",
                outcome = "SCHEDULED",
                feedbackAtMillis = now
            )
        }
    }

    fun rejectAutoScheduleProposal(proposal: AutoScheduleTelemetryEntity) {
        viewModelScope.launch {
            autoScheduleTelemetryDao.recordFeedback(
                id = proposal.id,
                reviewStatus = "REJECTED",
                adjustment = "rejected",
                outcome = "NOT_SCHEDULED",
                feedbackAtMillis = System.currentTimeMillis()
            )
        }
    }

    fun approveAllAutoScheduleProposals() {
        _uiState.value.pendingAutoScheduleReviews.forEach(::approveAutoScheduleProposal)
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
