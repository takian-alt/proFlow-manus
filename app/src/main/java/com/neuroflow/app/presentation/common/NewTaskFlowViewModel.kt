package com.neuroflow.app.presentation.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neuroflow.app.data.local.UserPreferences
import com.neuroflow.app.data.local.UserPreferencesDataStore
import com.neuroflow.app.data.local.entity.TaskEntity
import com.neuroflow.app.data.local.entity.localTimeOfDayOffset
import com.neuroflow.app.data.local.entity.startOfLocalDay
import com.neuroflow.app.domain.model.EnergyLevel
import com.neuroflow.app.domain.model.Priority
import com.neuroflow.app.domain.model.Quadrant
import com.neuroflow.app.domain.model.Recurrence
import com.neuroflow.app.domain.model.TaskType
import com.neuroflow.app.domain.scheduler.AutoSchedulingContracts
import com.neuroflow.app.domain.scheduler.AutoScheduleRejectionReason
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class NewTaskFlowViewModel @Inject constructor(
    userPreferencesDataStore: UserPreferencesDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewTaskFlowUiState())
    val uiState: StateFlow<NewTaskFlowUiState> = _uiState.asStateFlow()

    private val _preferences = MutableStateFlow(UserPreferences())
    val preferences: StateFlow<UserPreferences> = _preferences.asStateFlow()

    private var initToken: String? = null

    init {
        viewModelScope.launch {
            userPreferencesDataStore.preferencesFlow.collect { _preferences.value = it }
        }
    }

    fun reset() {
        initToken = null
        _uiState.value = NewTaskFlowUiState()
    }

    fun initialize(
        editTask: TaskEntity?,
        prefilledQuadrant: Quadrant?,
        availableTasks: List<TaskEntity>
    ) {
        val token = buildString {
            append(editTask?.id ?: "new")
            append("|")
            append(prefilledQuadrant?.name ?: "none")
            append("|")
            append(availableTasks.size)
        }
        if (initToken == token) return
        initToken = token

        _uiState.value = NewTaskFlowUiState.fromTask(
            task = editTask,
            prefilledQuadrant = prefilledQuadrant,
            availableTasks = availableTasks
        )
    }

    fun updateTitle(value: String) = _uiState.update { it.copy(title = value) }
    fun updateDescription(value: String) = _uiState.update { it.copy(description = value) }
    fun updateQuadrant(value: Quadrant) = _uiState.update { it.copy(quadrant = value) }
    fun updatePriority(value: Priority) = _uiState.update { it.copy(priority = value) }
    fun updateRecurrence(value: Recurrence) {
        _uiState.update {
            it.copy(
                recurrence = value,
                isScheduleLocked = if (!it.isEditing && value != Recurrence.NONE) true else it.isScheduleLocked
            )
        }
        ensureValidStep()
    }

    fun updateCustomIntervalDays(value: Int) = _uiState.update {
        it.copy(customIntervalDays = value.coerceAtLeast(1))
    }

    fun updateDeadlineDate(value: Long?) = _uiState.update { it.copy(deadlineDate = value) }
    fun updateDeadlineType(value: String) = _uiState.update { it.copy(deadlineType = value) }
    fun updateDeadlineTime(value: Long?) = _uiState.update { it.copy(deadlineTime = value) }
    fun updateScheduledDate(value: Long?) = _uiState.update { it.copy(scheduledDate = value) }
    fun updateScheduledTime(value: Long?) = _uiState.update { it.copy(scheduledTime = value) }
    fun updateScheduleLocked(value: Boolean) = _uiState.update { it.copy(isScheduleLocked = value) }
    fun updateHabitDate(value: Long?) = _uiState.update { it.copy(habitDate = value) }
    fun updateHabitTime(value: Long?) = _uiState.update { it.copy(habitTime = value) }
    fun updateEstimatedDuration(value: Int) = _uiState.update { it.copy(estimatedDurationMinutes = value) }
    fun updateCanSplit(value: Boolean) = _uiState.update { it.copy(canSplit = value) }
    fun updateMaxSessionLength(value: Int) = _uiState.update { it.copy(maxSessionLengthMinutes = value.coerceAtLeast(0)) }
    fun updateImpact(value: Float) = _uiState.update { it.copy(impactScore = value.coerceIn(0f, 100f)) }
    fun updateValue(value: Float) = _uiState.update { it.copy(valueScore = value.coerceIn(0f, 100f)) }
    fun updateEffort(value: Float) = _uiState.update { it.copy(effortScore = value.coerceIn(0f, 100f)) }
    fun updateReminderFlags(value: Int) = _uiState.update { it.copy(reminderFlags = value) }
    fun updateWaitingFor(value: String) = _uiState.update { it.copy(waitingFor = value) }
    fun updatePlan(value: String) = _uiState.update { it.copy(stepByStepPlan = value) }
    fun updateIsFrog(value: Boolean) = _uiState.update { it.copy(isFrog = value) }
    fun updateEnergyLevel(value: EnergyLevel) = _uiState.update { it.copy(energyLevel = value) }
    fun updateTaskType(value: TaskType) = _uiState.update { it.copy(taskType = value) }
    fun updateContextTag(value: String) = _uiState.update { it.copy(contextTag = value) }
    fun updateEnjoyment(value: Float) = _uiState.update { it.copy(enjoymentScore = value.coerceIn(0f, 100f)) }
    fun updatePublicCommitment(value: Boolean) = _uiState.update { it.copy(isPublicCommitment = value) }
    fun updateAnxietyTask(value: Boolean) = _uiState.update { it.copy(isAnxietyTask = value) }
    fun updateGoalRiskLevel(value: Int) = _uiState.update { it.copy(goalRiskLevel = value.coerceIn(0, 2)) }

    fun toggleDependency(taskId: String) {
        _uiState.update {
            val updated = if (it.selectedDepIds.contains(taskId)) {
                it.selectedDepIds - taskId
            } else {
                it.selectedDepIds + taskId
            }
            it.copy(selectedDepIds = updated)
        }
    }

    fun toggleTag(tag: String) {
        val cleaned = tag.trim()
        if (cleaned.isBlank()) return
        _uiState.update {
            val hasTag = it.tags.any { existing -> existing.equals(cleaned, ignoreCase = true) }
            it.copy(
                tags = if (hasTag) {
                    it.tags.filterNot { existing -> existing.equals(cleaned, ignoreCase = true) }
                } else {
                    it.tags + cleaned
                }
            )
        }
    }

    fun addTag(tag: String) {
        val cleaned = tag.trim()
        if (cleaned.isBlank()) return
        _uiState.update { state ->
            if (state.tags.any { it.equals(cleaned, ignoreCase = true) }) state
            else state.copy(tags = state.tags + cleaned)
        }
    }

    fun removeTag(tag: String) {
        _uiState.update { it.copy(tags = it.tags.filterNot { existing -> existing.equals(tag, ignoreCase = true) }) }
    }

    fun setIncludeExecutionStep(value: Boolean) {
        _uiState.update { it.copy(includeExecutionStep = value) }
        ensureValidStep()
    }

    fun canMoveNext(): Boolean {
        val state = _uiState.value
        return when (state.currentStep) {
            NewTaskFlowStep.BASIC_INFO -> state.title.isNotBlank()
            NewTaskFlowStep.RECURRENCE -> state.recurrence == Recurrence.NONE || state.habitDate != null
            else -> true
        }
    }

    fun nextStep() {
        val state = _uiState.value
        if (!canMoveNext()) return
        val steps = visibleSteps(state)
        val index = steps.indexOf(state.currentStep)
        if (index in 0 until steps.lastIndex) {
            _uiState.update { it.copy(currentStep = steps[index + 1]) }
        }
    }

    fun previousStep() {
        val state = _uiState.value
        val steps = visibleSteps(state)
        val index = steps.indexOf(state.currentStep)
        if (index > 0) {
            _uiState.update { it.copy(currentStep = steps[index - 1]) }
        }
    }

    fun visibleSteps(state: NewTaskFlowUiState = _uiState.value): List<NewTaskFlowStep> {
        val base = mutableListOf(
            NewTaskFlowStep.BASIC_INFO,
            NewTaskFlowStep.CLASSIFICATION,
            NewTaskFlowStep.TIMING
        )
        if (state.recurrence != Recurrence.NONE) {
            base += NewTaskFlowStep.RECURRENCE
        }
        base += NewTaskFlowStep.EFFORT
        if (state.includeExecutionStep || state.waitingFor.isNotBlank() || state.stepByStepPlan.isNotBlank() || state.selectedDepIds.isNotEmpty()) {
            base += NewTaskFlowStep.EXECUTION
        }
        base += NewTaskFlowStep.TAGS
        base += NewTaskFlowStep.REVIEW
        return base
    }

    fun currentStepIndex(): Int {
        val steps = visibleSteps()
        return steps.indexOf(_uiState.value.currentStep).coerceAtLeast(0)
    }

    fun totalStepCount(): Int = visibleSteps().size

    fun buildTaskPayload(existing: TaskEntity?): TaskEntity {
        val state = _uiState.value
        val recurringAnchor = if (state.recurrence != Recurrence.NONE && state.habitDate != null) {
            state.habitDate + (state.habitTime ?: 0L)
        } else {
            null
        }

        return (existing ?: TaskEntity(title = state.title)).copy(
            title = state.title,
            description = state.description,
            tags = state.tags.joinToString(","),
            quadrant = state.quadrant,
            priority = state.priority,
            recurrence = state.recurrence,
            recurrenceIntervalDays = state.customIntervalDays,
            habitDate = recurringAnchor,
            deadlineDate = if (state.recurrence == Recurrence.NONE) state.deadlineDate else null,
            deadlineTime = if (state.recurrence == Recurrence.NONE) state.deadlineTime else null,
            deadlineType = state.deadlineType,
            scheduledDate = if (state.recurrence == Recurrence.NONE) state.scheduledDate else null,
            scheduledTime = if (state.recurrence == Recurrence.NONE) state.scheduledTime else null,
            isAutoScheduled = when {
                existing?.isAutoScheduled == true &&
                    state.scheduledDate == existing.scheduledDate &&
                    state.scheduledTime == existing.scheduledTime &&
                    state.recurrence == existing.recurrence -> true
                state.recurrence == Recurrence.NONE && (state.scheduledDate != null || state.scheduledTime != null) -> false
                else -> existing?.isAutoScheduled ?: false
            },
            lastAutoScheduledAt = when {
                // If user manually changed the schedule, reset lastAutoScheduledAt to allow immediate replanning
                existing?.isAutoScheduled == true &&
                    (state.scheduledDate != existing.scheduledDate || state.scheduledTime != existing.scheduledTime) -> null
                // Otherwise preserve existing value
                else -> existing?.lastAutoScheduledAt
            },
            isScheduleLocked = if (state.recurrence != Recurrence.NONE) true else state.isScheduleLocked,
            estimatedDurationMinutes = state.estimatedDurationMinutes,
            canSplit = state.canSplit,
            maxSessionLengthMinutes = state.maxSessionLengthMinutes,
            impactScore = state.impactScore.toInt(),
            valueScore = state.valueScore.toInt(),
            effortScore = state.effortScore.toInt(),
            reminderFlags = state.reminderFlags,
            waitingFor = state.waitingFor,
            dependsOnTaskIds = state.selectedDepIds.joinToString(","),
            ifThenPlan = state.stepByStepPlan,
            isFrog = state.isFrog,
            energyLevel = state.energyLevel,
            taskType = state.taskType,
            contextTag = state.contextTag,
            enjoymentScore = state.enjoymentScore.toInt(),
            isPublicCommitment = state.isPublicCommitment,
            isAnxietyTask = state.isAnxietyTask,
            goalRiskLevel = state.goalRiskLevel,
            updatedAt = System.currentTimeMillis()
        )
    }

    fun autoSchedulePreview(): AutoSchedulePreview {
        val state = _uiState.value
        val prefs = _preferences.value
        if (!prefs.autoSchedulingEnabled) {
            return AutoSchedulePreview(
                willAutoSchedule = false,
                reason = "Auto scheduling is disabled in settings"
            )
        }

        val candidate = buildTaskPayload(existing = null)
        if (!AutoSchedulingContracts.hasDeadlineData(candidate)) {
            return AutoSchedulePreview(
                willAutoSchedule = false,
                reason = "No deadline set"
            )
        }

        if (!AutoSchedulingContracts.isMutableByAutoScheduler(candidate)) {
            val reason = when {
                candidate.isScheduleLocked -> AutoScheduleRejectionReason.LOCKED_TASK
                AutoSchedulingContracts.hasManualScheduleData(candidate) -> AutoScheduleRejectionReason.MANUAL_SCHEDULE_PRESENT
                else -> AutoScheduleRejectionReason.UNKNOWN
            }
            return AutoSchedulePreview(
                willAutoSchedule = false,
                reason = reason.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
            )
        }

        return AutoSchedulePreview(
            willAutoSchedule = true,
            reason = "Eligible for automatic scheduling (about every 5 min while app is active; periodic fallback in background)"
        )
    }

    private fun ensureValidStep() {
        val state = _uiState.value
        val steps = visibleSteps(state)
        if (state.currentStep !in steps) {
            _uiState.update { it.copy(currentStep = steps.first()) }
        }
    }
}

enum class NewTaskFlowStep {
    BASIC_INFO,
    CLASSIFICATION,
    TIMING,
    RECURRENCE,
    EFFORT,
    EXECUTION,
    TAGS,
    REVIEW
}

data class AutoSchedulePreview(
    val willAutoSchedule: Boolean,
    val reason: String
)

data class NewTaskFlowUiState(
    val isEditing: Boolean = false,
    val title: String = "",
    val description: String = "",
    val tags: List<String> = emptyList(),
    val quadrant: Quadrant = Quadrant.DO_FIRST,
    val priority: Priority = Priority.MEDIUM,
    val recurrence: Recurrence = Recurrence.NONE,
    val customIntervalDays: Int = 1,
    val deadlineDate: Long? = null,
    val deadlineTime: Long? = null,
    val deadlineType: String = "SOFT",
    val scheduledDate: Long? = null,
    val scheduledTime: Long? = null,
    val isScheduleLocked: Boolean = false,
    val habitDate: Long? = null,
    val habitTime: Long? = null,
    val estimatedDurationMinutes: Int = 0,
    val canSplit: Boolean = true,
    val maxSessionLengthMinutes: Int = 0,
    val impactScore: Float = 50f,
    val valueScore: Float = 50f,
    val effortScore: Float = 50f,
    val reminderFlags: Int = 0,
    val waitingFor: String = "",
    val selectedDepIds: Set<String> = emptySet(),
    val stepByStepPlan: String = "",
    val isFrog: Boolean = false,
    val energyLevel: EnergyLevel = EnergyLevel.MEDIUM,
    val contextTag: String = "",
    val taskType: TaskType = TaskType.ANALYTICAL,
    val enjoymentScore: Float = 50f,
    val isPublicCommitment: Boolean = false,
    val isAnxietyTask: Boolean = false,
    val goalRiskLevel: Int = 0,
    val includeExecutionStep: Boolean = false,
    val currentStep: NewTaskFlowStep = NewTaskFlowStep.BASIC_INFO
) {
    companion object {
        fun fromTask(
            task: TaskEntity?,
            prefilledQuadrant: Quadrant?,
            availableTasks: List<TaskEntity>
        ): NewTaskFlowUiState {
            val selectedDeps = task?.dependsOnTaskIds
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.toSet()
                ?: emptySet()
            val startStep = NewTaskFlowStep.BASIC_INFO
            val recurrence = task?.recurrence ?: Recurrence.NONE
            val habitDate = task?.habitDate?.let(::startOfLocalDay)
            val habitTime = task?.habitDate?.let(::localTimeOfDayOffset)
            return NewTaskFlowUiState(
                isEditing = task != null,
                title = task?.title ?: "",
                description = task?.description ?: "",
                tags = task?.tags?.split(',')?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList(),
                quadrant = task?.quadrant ?: prefilledQuadrant ?: Quadrant.DO_FIRST,
                priority = task?.priority ?: Priority.MEDIUM,
                recurrence = recurrence,
                customIntervalDays = task?.recurrenceIntervalDays ?: 1,
                deadlineDate = task?.deadlineDate,
                deadlineTime = task?.deadlineTime,
                deadlineType = task?.deadlineType ?: "SOFT",
                scheduledDate = task?.scheduledDate,
                scheduledTime = task?.scheduledTime,
                isScheduleLocked = task?.isScheduleLocked ?: (recurrence != Recurrence.NONE),
                habitDate = habitDate,
                habitTime = habitTime,
                estimatedDurationMinutes = task?.estimatedDurationMinutes ?: 0,
                canSplit = task?.canSplit ?: true,
                maxSessionLengthMinutes = task?.maxSessionLengthMinutes ?: 0,
                impactScore = (task?.impactScore ?: 50).toFloat(),
                valueScore = (task?.valueScore ?: 50).toFloat(),
                effortScore = (task?.effortScore ?: 50).toFloat(),
                reminderFlags = task?.reminderFlags ?: 0,
                waitingFor = task?.waitingFor ?: "",
                selectedDepIds = selectedDeps.intersect(availableTasks.map { it.id }.toSet()),
                stepByStepPlan = task?.ifThenPlan ?: "",
                isFrog = task?.isFrog ?: false,
                energyLevel = task?.energyLevel ?: EnergyLevel.MEDIUM,
                contextTag = task?.contextTag ?: "",
                taskType = task?.taskType ?: TaskType.ANALYTICAL,
                enjoymentScore = (task?.enjoymentScore ?: 50).toFloat(),
                isPublicCommitment = task?.isPublicCommitment ?: false,
                isAnxietyTask = task?.isAnxietyTask ?: false,
                goalRiskLevel = task?.goalRiskLevel ?: 0,
                includeExecutionStep = selectedDeps.isNotEmpty() || !task?.waitingFor.isNullOrBlank() || !task?.ifThenPlan.isNullOrBlank(),
                currentStep = startStep
            )
        }
    }
}
