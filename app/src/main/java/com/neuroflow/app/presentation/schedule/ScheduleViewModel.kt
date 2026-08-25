package com.neuroflow.app.presentation.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neuroflow.app.data.calendar.CalendarIntegrationRepository
import com.neuroflow.app.data.local.UserPreferencesDataStore
import com.neuroflow.app.data.local.dao.AutoScheduleTelemetryDao
import com.neuroflow.app.data.local.dao.ScheduleAdjustmentDao
import com.neuroflow.app.data.local.dao.SchedulePlanVersionDao
import com.neuroflow.app.data.local.dao.UnavailableTimeBlockDao
import com.neuroflow.app.data.local.entity.AutoScheduleTelemetryEntity
import com.neuroflow.app.data.local.entity.ScheduleAdjustmentEntity
import com.neuroflow.app.data.local.entity.TaskEntity
import com.neuroflow.app.data.local.entity.UnavailableTimeBlockEntity
import com.neuroflow.app.data.local.entity.timelineStartMinuteOfDay
import com.neuroflow.app.domain.repository.EnergyScoreRepository
import com.neuroflow.app.data.local.entity.SchedulePlanVersionEntity
import com.neuroflow.app.domain.scheduler.SchedulePlanVersionCodec
import com.neuroflow.app.domain.scheduler.TaskSplitPlanner
import com.neuroflow.app.domain.model.Recurrence
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
    val latestUndoableAdjustment: ScheduleAdjustmentEntity? = null,
    val energyNow: Int? = null,
    val unavailableTimeBlocks: List<UnavailableTimeBlockEntity> = emptyList(),
    val planVersions: List<SchedulePlanVersionEntity> = emptyList(),
    val isLoading: Boolean = true,
    val workDayStart: Int = 8,
    val workDayEnd: Int = 20
)

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val preferencesDataStore: UserPreferencesDataStore,
    private val autoScheduleTelemetryDao: AutoScheduleTelemetryDao,
    private val calendarIntegrationRepository: CalendarIntegrationRepository,
    private val scheduleAdjustmentDao: ScheduleAdjustmentDao,
    private val energyScoreRepository: EnergyScoreRepository,
    private val unavailableTimeBlockDao: UnavailableTimeBlockDao,
    private val schedulePlanVersionDao: SchedulePlanVersionDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    init {
        loadTasksForDate(_uiState.value.selectedDate)
        observeAllActive()
        observePendingAutoScheduleReviews()
        observeLatestUndoableAdjustment()
        observeEnergy()
        observeUnavailableTimeBlocks()
        observePlanVersions()
        observeWorkHours()
    }

    private fun observePendingAutoScheduleReviews() {
        viewModelScope.launch {
            autoScheduleTelemetryDao.observePending().collect { proposals ->
                _uiState.update { it.copy(pendingAutoScheduleReviews = proposals) }
            }
        }
    }

    private fun observePlanVersions() {
        viewModelScope.launch {
            schedulePlanVersionDao.observeRecent().collect { versions ->
                _uiState.update { it.copy(planVersions = versions) }
            }
        }
    }

    private fun observeUnavailableTimeBlocks() {
        viewModelScope.launch {
            unavailableTimeBlockDao.observeAll().collect { blocks ->
                _uiState.update { it.copy(unavailableTimeBlocks = blocks) }
            }
        }
    }

    private fun observeEnergy() {
        viewModelScope.launch {
            energyScoreRepository.observeEnergy().collect { energy ->
                _uiState.update { it.copy(energyNow = energy.availableEnergy) }
            }
        }
    }

    private fun observeLatestUndoableAdjustment() {
        viewModelScope.launch {
            scheduleAdjustmentDao.observeLatestUndoable().collect { adjustment ->
                _uiState.update { it.copy(latestUndoableAdjustment = adjustment) }
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

    fun adjustScheduledTask(task: TaskEntity, minutes: Int) {
        if (task.isScheduleLocked || task.scheduledDate == null || task.scheduledTime == null) return
        viewModelScope.launch {
            val oldMillis = task.scheduledDate + task.scheduledTime
            val newMillis = oldMillis + minutes * 60_000L
            val (newDate, newTime) = splitMillisToDateAndTime(newMillis)
            val updated = task.copy(
                scheduledDate = newDate,
                scheduledTime = newTime,
                isAutoScheduled = false,
                lastAutoScheduledAt = null,
                updatedAt = System.currentTimeMillis()
            )
            taskRepository.update(updated)
            scheduleAdjustmentDao.insert(
                ScheduleAdjustmentEntity(
                    taskId = task.id,
                    previousScheduledDate = task.scheduledDate,
                    previousScheduledTime = task.scheduledTime,
                    newScheduledDate = newDate,
                    newScheduledTime = newTime,
                    source = "MANUAL",
                    reason = if (minutes < 0) "moved_earlier" else "moved_later"
                )
            )
            recordCurrentPlanVersion("MANUAL")
        }
    }

    fun adjustTaskDuration(task: TaskEntity, deltaMinutes: Int) {
        if (task.isScheduleLocked) return
        val current = task.estimatedDurationMinutes.takeIf { it > 0 } ?: 30
        val updatedDuration = (current + deltaMinutes).coerceIn(15, 360)
        if (updatedDuration == current) return
        viewModelScope.launch {
            taskRepository.update(task.copy(estimatedDurationMinutes = updatedDuration, updatedAt = System.currentTimeMillis()))
            scheduleAdjustmentDao.insert(
                ScheduleAdjustmentEntity(
                    taskId = task.id,
                    previousScheduledDate = task.scheduledDate,
                    previousScheduledTime = task.scheduledTime,
                    newScheduledDate = task.scheduledDate,
                    newScheduledTime = task.scheduledTime,
                    source = "MANUAL",
                    reason = "resized_${deltaMinutes}m"
                )
            )
            recordCurrentPlanVersion("MANUAL")
        }
    }

    fun toggleTaskScheduleLock(task: TaskEntity) {
        viewModelScope.launch {
            taskRepository.update(task.copy(isScheduleLocked = !task.isScheduleLocked, updatedAt = System.currentTimeMillis()))
        }
    }

    fun splitScheduledTask(task: TaskEntity) {
        if (!task.canSplit || task.estimatedDurationMinutes <= 90) return
        viewModelScope.launch {
            val parts = TaskSplitPlanner.createParts(task)
            taskRepository.update(task.copy(status = com.neuroflow.app.domain.model.TaskStatus.ARCHIVED, updatedAt = System.currentTimeMillis()))
            taskRepository.insertAll(parts)
            recordCurrentPlanVersion("MANUAL")
        }
    }

    fun convertTaskToRecurring(task: TaskEntity) {
        if (task.isHabitual) return
        viewModelScope.launch {
            val anchor = task.scheduledDate?.plus(task.scheduledTime ?: 0L) ?: System.currentTimeMillis()
            taskRepository.update(
                task.copy(
                    recurrence = Recurrence.DAILY,
                    isHabitual = true,
                    habitDate = anchor,
                    scheduledDate = null,
                    scheduledTime = null,
                    isScheduleLocked = true,
                    isAutoScheduled = false,
                    updatedAt = System.currentTimeMillis()
                )
            )
            recordCurrentPlanVersion("MANUAL")
        }
    }

    fun toggleHourUnavailable(hour: Int) {
        viewModelScope.launch {
            val start = Calendar.getInstance().apply {
                timeInMillis = _uiState.value.selectedDate
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val end = start + 60 * 60_000L
            val overlaps = unavailableTimeBlockDao.getOverlapping(start, end)
            if (overlaps.isNotEmpty()) {
                overlaps.forEach(unavailableTimeBlockDao::delete)
            } else {
                unavailableTimeBlockDao.insert(
                    UnavailableTimeBlockEntity(
                        startMillis = start,
                        endMillis = end,
                        label = "Unavailable"
                    )
                )
            }
        }
    }

    fun markHourUnavailable(hour: Int) = toggleHourUnavailable(hour)

    /** Assigns an existing task to a specific hour slot on the selected date. Locked tasks are skipped. */
    fun rescheduleTaskToNextBlock(task: TaskEntity) {
        if (task.isScheduleLocked) return
        viewModelScope.launch {
            val now = Calendar.getInstance()
            now.add(Calendar.MINUTE, 30)
            now.set(Calendar.SECOND, 0)
            now.set(Calendar.MILLISECOND, 0)
            now.set(Calendar.MINUTE, (now.get(Calendar.MINUTE) / 30) * 30)
            val scheduledMillis = now.timeInMillis
            val dateCal = Calendar.getInstance().apply {
                timeInMillis = scheduledMillis
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val scheduledDate = dateCal.timeInMillis
            val updated = task.copy(
                scheduledDate = scheduledDate,
                scheduledTime = scheduledMillis - scheduledDate,
                isAutoScheduled = false,
                lastAutoScheduledAt = null,
                updatedAt = System.currentTimeMillis()
            )
            taskRepository.update(updated)
            scheduleAdjustmentDao.insert(
                ScheduleAdjustmentEntity(
                    taskId = task.id,
                    previousScheduledDate = task.scheduledDate,
                    previousScheduledTime = task.scheduledTime,
                    newScheduledDate = updated.scheduledDate,
                    newScheduledTime = updated.scheduledTime,
                    source = "MANUAL",
                    reason = "today_command_center_reschedule"
                )
            )
            recordCurrentPlanVersion("MANUAL")
        }
    }

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
            scheduleAdjustmentDao.insert(
                ScheduleAdjustmentEntity(
                    taskId = task.id,
                    previousScheduledDate = task.scheduledDate,
                    previousScheduledTime = task.scheduledTime,
                    newScheduledDate = scheduledDate,
                    newScheduledTime = scheduledTime,
                    source = "MANUAL",
                    reason = "manual_schedule"
                )
            )
            recordCurrentPlanVersion("MANUAL")
        }
    }

    fun shiftAutoScheduleProposal(proposal: AutoScheduleTelemetryEntity, minutes: Int) {
        viewModelScope.launch {
            val date = proposal.selectedSlotDate ?: return@launch
            val time = proposal.selectedSlotTime ?: return@launch
            val shiftedMillis = date + time + minutes * 60_000L
            val (shiftedDate, shiftedTime) = splitMillisToDateAndTime(shiftedMillis)
            autoScheduleTelemetryDao.updateProposalTime(
                id = proposal.id,
                date = shiftedDate,
                time = shiftedTime,
                adjustment = if (minutes < 0) "moved_earlier" else "moved_later"
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
            scheduleAdjustmentDao.insert(
                ScheduleAdjustmentEntity(
                    taskId = task.id,
                    previousScheduledDate = task.scheduledDate,
                    previousScheduledTime = task.scheduledTime,
                    newScheduledDate = scheduledDate,
                    newScheduledTime = scheduledTime,
                    source = "AUTO_APPROVED",
                    reason = proposal.assignmentReason
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
                adjustment = listOfNotNull(proposal.userAdjustment, "approved").joinToString(";"),
                outcome = "SCHEDULED",
                feedbackAtMillis = now
            )
            recordCurrentPlanVersion("REVIEW_APPROVED")
        }
    }

    fun rejectAutoScheduleProposal(proposal: AutoScheduleTelemetryEntity) {
        viewModelScope.launch {
            autoScheduleTelemetryDao.recordFeedback(
                id = proposal.id,
                reviewStatus = "REJECTED",
                adjustment = listOfNotNull(proposal.userAdjustment, "rejected").joinToString(";"),
                outcome = "NOT_SCHEDULED",
                feedbackAtMillis = System.currentTimeMillis()
            )
        }
    }

    fun undoLastScheduleAdjustment() {
        viewModelScope.launch {
            val adjustment = scheduleAdjustmentDao.observeLatestUndoable().first() ?: return@launch
            val task = taskRepository.getById(adjustment.taskId) ?: return@launch
            val now = System.currentTimeMillis()
            taskRepository.update(
                task.copy(
                    scheduledDate = adjustment.previousScheduledDate,
                    scheduledTime = adjustment.previousScheduledTime,
                    isAutoScheduled = false,
                    lastAutoScheduledAt = null,
                    updatedAt = now
                )
            )
            scheduleAdjustmentDao.markUndone(adjustment.id)
            scheduleAdjustmentDao.insert(
                ScheduleAdjustmentEntity(
                    taskId = task.id,
                    previousScheduledDate = task.scheduledDate,
                    previousScheduledTime = task.scheduledTime,
                    newScheduledDate = adjustment.previousScheduledDate,
                    newScheduledTime = adjustment.previousScheduledTime,
                    source = "UNDO",
                    reason = "undo_last_schedule"
                )
            )
        }
    }

    fun restorePlanVersion(version: SchedulePlanVersionEntity) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            SchedulePlanVersionCodec.decode(version.summaryJson).forEach { entry ->
                val task = taskRepository.getById(entry.taskId) ?: return@forEach
                if (task.isScheduleLocked || (!task.isAutoScheduled && task.scheduledDate != null)) return@forEach
                taskRepository.update(
                    task.copy(
                        scheduledDate = entry.scheduledDate,
                        scheduledTime = entry.scheduledTime,
                        isAutoScheduled = true,
                        lastAutoScheduledAt = now,
                        updatedAt = now
                    )
                )
            }
            recordCurrentPlanVersion("RESTORE")
        }
    }

    private suspend fun recordCurrentPlanVersion(source: String) {
        val tasks = taskRepository.getAllTasks()
        schedulePlanVersionDao.insert(
            SchedulePlanVersionEntity(
                source = source,
                summaryJson = SchedulePlanVersionCodec.encode(tasks),
                taskCount = tasks.count { it.scheduledDate != null && it.scheduledTime != null }
            )
        )
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
