package com.neuroflow.app.presentation.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neuroflow.app.data.local.entity.TaskEntity
import com.neuroflow.app.domain.model.EnergyLevel
import com.neuroflow.app.domain.model.Priority
import com.neuroflow.app.domain.model.Quadrant
import com.neuroflow.app.domain.model.Recurrence
import com.neuroflow.app.domain.model.TaskType
import com.neuroflow.app.presentation.common.theme.NeuroFlowColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun NewTaskSheet(
    onDismiss: () -> Unit,
    onSave: (TaskEntity) -> Unit,
    editTask: TaskEntity? = null,
    prefilledQuadrant: Quadrant? = null,
    availableTasks: List<TaskEntity> = emptyList()
) {
    val flowViewModel: NewTaskFlowViewModel = hiltViewModel()
    val tagViewModel: TaskTagViewModel = hiltViewModel()

    val ui by flowViewModel.uiState.collectAsStateWithLifecycle()
    val prefs by flowViewModel.preferences.collectAsStateWithLifecycle()
    val catalogTags by tagViewModel.tags.collectAsStateWithLifecycle()

    val taskTags = remember(availableTasks) {
        availableTasks
            .flatMap { task -> task.tags.split(",") }
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }
    val suggestedTags = remember(catalogTags, taskTags) {
        (catalogTags + taskTags)
            .distinctBy { it.lowercase(Locale.getDefault()) }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

    LaunchedEffect(editTask?.id, prefilledQuadrant, availableTasks.size) {
        flowViewModel.initialize(editTask, prefilledQuadrant, availableTasks)
    }

    val stepCount = flowViewModel.totalStepCount()
    val stepIndex = flowViewModel.currentStepIndex()
    val progress = ((stepIndex + 1).toFloat() / stepCount.toFloat()).coerceIn(0f, 1f)

    val hasDraftContent = ui.title.isNotBlank() ||
        ui.description.isNotBlank() ||
        ui.tags.isNotEmpty() ||
        ui.waitingFor.isNotBlank() ||
        ui.stepByStepPlan.isNotBlank() ||
        ui.selectedDepIds.isNotEmpty() ||
        ui.deadlineDate != null ||
        ui.scheduledDate != null ||
        ui.habitDate != null

    var showDiscardDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var dateTarget by remember { mutableStateOf("deadline") }
    var timeTarget by remember { mutableStateOf("deadline") }

    fun requestDismiss() {
        if (hasDraftContent) {
            showDiscardDialog = true
        } else {
            flowViewModel.reset()
            onDismiss()
        }
    }

    Dialog(onDismissRequest = { requestDismiss() }) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(0.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        if (stepIndex == 0) requestDismiss() else flowViewModel.previousStep()
                    }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (ui.isEditing) "Edit Task" else "Create Task",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Step ${stepIndex + 1} of $stepCount",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { requestDismiss() }) {
                        Text("Close")
                    }
                }

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = NeuroFlowColors.Purple
                )

                AnimatedContent(
                    targetState = ui.currentStep,
                    transitionSpec = {
                        if (targetState.ordinal > initialState.ordinal) {
                            (slideInHorizontally(animationSpec = tween(220)) { it / 3 } + fadeIn()) togetherWith
                                (slideOutHorizontally(animationSpec = tween(220)) { -it / 4 } + fadeOut())
                        } else {
                            (slideInHorizontally(animationSpec = tween(220)) { -it / 3 } + fadeIn()) togetherWith
                                (slideOutHorizontally(animationSpec = tween(220)) { it / 4 } + fadeOut())
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) { step ->
                    when (step) {
                        NewTaskFlowStep.BASIC_INFO -> BasicInfoStep(
                            title = ui.title,
                            description = ui.description,
                            onTitleChange = flowViewModel::updateTitle,
                            onDescriptionChange = flowViewModel::updateDescription
                        )

                        NewTaskFlowStep.CLASSIFICATION -> ClassificationStep(
                            quadrant = ui.quadrant,
                            priority = ui.priority,
                            energyLevel = ui.energyLevel,
                            taskType = ui.taskType,
                            isFrog = ui.isFrog,
                            onQuadrantChange = flowViewModel::updateQuadrant,
                            onPriorityChange = flowViewModel::updatePriority,
                            onEnergyChange = flowViewModel::updateEnergyLevel,
                            onTaskTypeChange = flowViewModel::updateTaskType,
                            onFrogChange = flowViewModel::updateIsFrog
                        )

                        NewTaskFlowStep.TIMING -> TimingStep(
                            recurrence = ui.recurrence,
                            customIntervalDays = ui.customIntervalDays,
                            deadlineDate = ui.deadlineDate,
                            deadlineTime = ui.deadlineTime,
                            scheduledDate = ui.scheduledDate,
                            scheduledTime = ui.scheduledTime,
                            habitDate = ui.habitDate,
                            habitTime = ui.habitTime,
                            reminderFlags = ui.reminderFlags,
                            isScheduleLocked = ui.isScheduleLocked,
                            onRecurrenceChange = flowViewModel::updateRecurrence,
                            onCustomIntervalChange = flowViewModel::updateCustomIntervalDays,
                            onDeadlineDateClick = {
                                dateTarget = "deadline"
                                showDatePicker = true
                            },
                            onDeadlineTimeClick = {
                                timeTarget = "deadline"
                                showTimePicker = true
                            },
                            onScheduledDateClick = {
                                dateTarget = "scheduled"
                                showDatePicker = true
                            },
                            onScheduledTimeClick = {
                                timeTarget = "scheduled"
                                showTimePicker = true
                            },
                            onHabitDateClick = {
                                dateTarget = "habit"
                                showDatePicker = true
                            },
                            onHabitTimeClick = {
                                timeTarget = "habit"
                                showTimePicker = true
                            },
                            onReminderFlagsChange = flowViewModel::updateReminderFlags,
                            onScheduleLockedChange = flowViewModel::updateScheduleLocked
                        )

                        NewTaskFlowStep.RECURRENCE -> RecurrenceStep(
                            recurrence = ui.recurrence,
                            customIntervalDays = ui.customIntervalDays,
                            habitDate = ui.habitDate,
                            habitTime = ui.habitTime,
                            onHabitDateClick = {
                                dateTarget = "habit"
                                showDatePicker = true
                            },
                            onHabitTimeClick = {
                                timeTarget = "habit"
                                showTimePicker = true
                            }
                        )

                        NewTaskFlowStep.EFFORT -> EffortStep(
                            estimatedDurationMinutes = ui.estimatedDurationMinutes,
                            impactScore = ui.impactScore,
                            valueScore = ui.valueScore,
                            effortScore = ui.effortScore,
                            enjoymentScore = ui.enjoymentScore,
                            contextTag = ui.contextTag,
                            isPublicCommitment = ui.isPublicCommitment,
                            isAnxietyTask = ui.isAnxietyTask,
                            goalRiskLevel = ui.goalRiskLevel,
                            includeExecutionStep = ui.includeExecutionStep,
                            onDurationChange = flowViewModel::updateEstimatedDuration,
                            onImpactChange = flowViewModel::updateImpact,
                            onValueChange = flowViewModel::updateValue,
                            onEffortChange = flowViewModel::updateEffort,
                            onEnjoymentChange = flowViewModel::updateEnjoyment,
                            onContextChange = flowViewModel::updateContextTag,
                            onPublicCommitmentChange = flowViewModel::updatePublicCommitment,
                            onAnxietyChange = flowViewModel::updateAnxietyTask,
                            onGoalRiskChange = flowViewModel::updateGoalRiskLevel,
                            onIncludeExecutionChange = flowViewModel::setIncludeExecutionStep
                        )

                        NewTaskFlowStep.EXECUTION -> ExecutionStep(
                            waitingFor = ui.waitingFor,
                            stepByStepPlan = ui.stepByStepPlan,
                            selectedDepIds = ui.selectedDepIds,
                            availableTasks = availableTasks,
                            editTaskId = editTask?.id,
                            onWaitingForChange = flowViewModel::updateWaitingFor,
                            onPlanChange = flowViewModel::updatePlan,
                            onToggleDependency = flowViewModel::toggleDependency
                        )

                        NewTaskFlowStep.TAGS -> TagsStep(
                            selectedTags = ui.tags,
                            suggestedTags = suggestedTags,
                            onAddTag = flowViewModel::addTag,
                            onToggleTag = flowViewModel::toggleTag,
                            onRemoveTag = flowViewModel::removeTag
                        )

                        NewTaskFlowStep.REVIEW -> ReviewStep(
                            uiState = ui,
                            autoSchedulePreview = flowViewModel.autoSchedulePreview(),
                            autoSchedulingEnabled = prefs.autoSchedulingEnabled
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        if (stepIndex == 0) requestDismiss() else flowViewModel.previousStep()
                    }) {
                        Text(if (stepIndex == 0) "Cancel" else "Back")
                    }
                    Button(
                        onClick = {
                            val isLast = stepIndex == stepCount - 1
                            if (isLast) {
                                tagViewModel.addTags(ui.tags)
                                onSave(flowViewModel.buildTaskPayload(editTask))
                            } else {
                                flowViewModel.nextStep()
                            }
                        },
                        enabled = if (stepIndex == stepCount - 1) ui.title.isNotBlank() else flowViewModel.canMoveNext(),
                        colors = ButtonDefaults.buttonColors(containerColor = NeuroFlowColors.Purple)
                    ) {
                        Text(if (stepIndex == stepCount - 1) if (ui.isEditing) "Update Task" else "Create Task" else "Next")
                    }
                }
            }
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard task draft?") },
            text = { Text("You have unsaved changes. Keep editing or discard this draft.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        flowViewModel.reset()
                        onDismiss()
                    }
                ) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Keep editing")
                }
            }
        )
    }

    if (showDatePicker) {
        val initial = when (dateTarget) {
            "scheduled" -> ui.scheduledDate
            "habit" -> ui.habitDate
            else -> ui.deadlineDate
        } ?: System.currentTimeMillis()
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val selected = datePickerState.selectedDateMillis?.let(::utcMidnightToLocalMidnight)
                    when (dateTarget) {
                        "scheduled" -> flowViewModel.updateScheduledDate(selected)
                        "habit" -> flowViewModel.updateHabitDate(selected)
                        else -> flowViewModel.updateDeadlineDate(selected)
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) { DatePicker(state = datePickerState) }
    }

    if (showTimePicker) {
        val initialMillis = when (timeTarget) {
            "scheduled" -> ui.scheduledTime
            "habit" -> ui.habitTime
            else -> ui.deadlineTime
        } ?: 0L
        val timePickerState = rememberTimePickerState(
            initialHour = (initialMillis / 3_600_000L).toInt().coerceIn(0, 23),
            initialMinute = ((initialMillis % 3_600_000L) / 60_000L).toInt().coerceIn(0, 59)
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Select Time") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    val selected = timePickerState.hour * 3_600_000L + timePickerState.minute * 60_000L
                    when (timeTarget) {
                        "scheduled" -> flowViewModel.updateScheduledTime(selected)
                        "habit" -> flowViewModel.updateHabitTime(selected)
                        else -> flowViewModel.updateDeadlineTime(selected)
                    }
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BasicInfoStep(
    title: String,
    description: String,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Basic Info", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text("Task Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NeuroFlowColors.Purple,
                focusedLabelColor = NeuroFlowColors.Purple
            )
        )
        OutlinedTextField(
            value = description,
            onValueChange = onDescriptionChange,
            label = { Text("Description") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NeuroFlowColors.Purple,
                focusedLabelColor = NeuroFlowColors.Purple
            )
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClassificationStep(
    quadrant: Quadrant,
    priority: Priority,
    energyLevel: EnergyLevel,
    taskType: TaskType,
    isFrog: Boolean,
    onQuadrantChange: (Quadrant) -> Unit,
    onPriorityChange: (Priority) -> Unit,
    onEnergyChange: (EnergyLevel) -> Unit,
    onTaskTypeChange: (TaskType) -> Unit,
    onFrogChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Classification", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        DropdownField(
            label = "Quadrant",
            value = quadrantDisplayName(quadrant),
            items = Quadrant.entries,
            itemLabel = ::quadrantDisplayName,
            onSelected = onQuadrantChange
        )
        DropdownField(
            label = "Priority",
            value = priorityDisplayName(priority),
            items = Priority.entries,
            itemLabel = ::priorityDisplayName,
            onSelected = onPriorityChange
        )

        Text("Energy level", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EnergyLevel.entries.forEach { level ->
                FilterChip(
                    selected = energyLevel == level,
                    onClick = { onEnergyChange(level) },
                    label = { Text(level.name.lowercase().replaceFirstChar { it.uppercase() }) }
                )
            }
        }

        Text("Task type", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TaskType.entries.forEach { type ->
                FilterChip(
                    selected = taskType == type,
                    onClick = { onTaskTypeChange(type) },
                    label = { Text(type.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }) }
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = isFrog,
                onCheckedChange = onFrogChange,
                colors = SwitchDefaults.colors(checkedTrackColor = NeuroFlowColors.Purple)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Mark as frog task")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimingStep(
    recurrence: Recurrence,
    customIntervalDays: Int,
    deadlineDate: Long?,
    deadlineTime: Long?,
    scheduledDate: Long?,
    scheduledTime: Long?,
    habitDate: Long?,
    habitTime: Long?,
    reminderFlags: Int,
    isScheduleLocked: Boolean,
    onRecurrenceChange: (Recurrence) -> Unit,
    onCustomIntervalChange: (Int) -> Unit,
    onDeadlineDateClick: () -> Unit,
    onDeadlineTimeClick: () -> Unit,
    onScheduledDateClick: () -> Unit,
    onScheduledTimeClick: () -> Unit,
    onHabitDateClick: () -> Unit,
    onHabitTimeClick: () -> Unit,
    onReminderFlagsChange: (Int) -> Unit,
    onScheduleLockedChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Timing", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        DropdownField(
            label = "Recurrence",
            value = recurrenceDisplayName(recurrence),
            items = Recurrence.entries,
            itemLabel = ::recurrenceDisplayName,
            onSelected = onRecurrenceChange
        )

        if (recurrence == Recurrence.CUSTOM) {
            OutlinedTextField(
                value = customIntervalDays.toString(),
                onValueChange = { value ->
                    value.toIntOrNull()?.let(onCustomIntervalChange)
                },
                label = { Text("Custom interval days") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (recurrence == Recurrence.NONE) {
            Text("Deadline", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateChip(deadlineDate, "Date", onDeadlineDateClick)
                TimeChip(deadlineTime, "Time", onDeadlineTimeClick)
            }

            Text("Scheduled", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateChip(scheduledDate, "Date", onScheduledDateClick)
                TimeChip(scheduledTime, "Time", onScheduledTimeClick)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = isScheduleLocked,
                    onCheckedChange = onScheduleLockedChange
                )
                Text("Lock schedule")
            }
        } else {
            Text("Recurring start", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateChip(habitDate, "Start date", onHabitDateClick)
                TimeChip(habitTime, "Start time", onHabitTimeClick)
            }
        }

        Text("Reminders", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ReminderChip("15 min", 1, reminderFlags, onReminderFlagsChange)
            ReminderChip("30 min", 2, reminderFlags, onReminderFlagsChange)
            ReminderChip("1 hour", 4, reminderFlags, onReminderFlagsChange)
            ReminderChip("1 day", 8, reminderFlags, onReminderFlagsChange)
        }
    }
}

@Composable
private fun RecurrenceStep(
    recurrence: Recurrence,
    customIntervalDays: Int,
    habitDate: Long?,
    habitTime: Long?,
    onHabitDateClick: () -> Unit,
    onHabitTimeClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Recurrence Anchor", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Pick the first occurrence. Anchor is timezone-safe and used for all recurrence cycles.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text("Pattern: ${recurrenceDisplayName(recurrence)}${if (recurrence == Recurrence.CUSTOM) " every $customIntervalDays days" else ""}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateChip(habitDate, "Start date", onHabitDateClick)
            TimeChip(habitTime, "Start time", onHabitTimeClick)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EffortStep(
    estimatedDurationMinutes: Int,
    impactScore: Float,
    valueScore: Float,
    effortScore: Float,
    enjoymentScore: Float,
    contextTag: String,
    isPublicCommitment: Boolean,
    isAnxietyTask: Boolean,
    goalRiskLevel: Int,
    includeExecutionStep: Boolean,
    onDurationChange: (Int) -> Unit,
    onImpactChange: (Float) -> Unit,
    onValueChange: (Float) -> Unit,
    onEffortChange: (Float) -> Unit,
    onEnjoymentChange: (Float) -> Unit,
    onContextChange: (String) -> Unit,
    onPublicCommitmentChange: (Boolean) -> Unit,
    onAnxietyChange: (Boolean) -> Unit,
    onGoalRiskChange: (Int) -> Unit,
    onIncludeExecutionChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Effort + Estimate", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        DropdownField(
            label = "Estimated duration",
            value = durationDisplayName(estimatedDurationMinutes),
            items = durationOptions,
            itemLabel = { it.first },
            onSelected = { onDurationChange(it.second) }
        )

        SliderField("Strategic impact", impactScore, onImpactChange)
        SliderField("Intrinsic value", valueScore, onValueChange)
        SliderField("Effort required", effortScore, onEffortChange)
        SliderField("Enjoyment", enjoymentScore, onEnjoymentChange)

        Text("Context", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("@work", "@home", "@phone", "@computer", "@errands").forEach { tag ->
                FilterChip(
                    selected = contextTag == tag,
                    onClick = { onContextChange(if (contextTag == tag) "" else tag) },
                    label = { Text(tag) }
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = isPublicCommitment, onCheckedChange = onPublicCommitmentChange)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Public commitment")
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = isAnxietyTask, onCheckedChange = onAnxietyChange)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Anxiety task")
        }

        Text("Goal risk", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0 to "None", 1 to "At Risk", 2 to "Critical").forEach { (level, label) ->
                FilterChip(
                    selected = goalRiskLevel == level,
                    onClick = { onGoalRiskChange(level) },
                    label = { Text(label) }
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = includeExecutionStep, onCheckedChange = onIncludeExecutionChange)
            Text("Add execution/dependency details")
        }
    }
}

@Composable
private fun ExecutionStep(
    waitingFor: String,
    stepByStepPlan: String,
    selectedDepIds: Set<String>,
    availableTasks: List<TaskEntity>,
    editTaskId: String?,
    onWaitingForChange: (String) -> Unit,
    onPlanChange: (String) -> Unit,
    onToggleDependency: (String) -> Unit
) {
    val candidates = remember(availableTasks, editTaskId) { availableTasks.filter { it.id != editTaskId } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Execution", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value = waitingFor,
            onValueChange = onWaitingForChange,
            label = { Text("Waiting for") },
            placeholder = { Text("External dependency") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = stepByStepPlan,
            onValueChange = onPlanChange,
            label = { Text("Step-by-step plan") },
            placeholder = { Text("One step per line") },
            minLines = 4,
            modifier = Modifier.fillMaxWidth()
        )

        Text("Dependencies", style = MaterialTheme.typography.labelLarge)
        if (candidates.isEmpty()) {
            Text("No other active tasks available.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                candidates.forEach { task ->
                    val selected = selectedDepIds.contains(task.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleDependency(task.id) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = selected,
                            onCheckedChange = { onToggleDependency(task.id) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(task.title)
                            Text(
                                "${task.quadrant.name.replace('_', ' ')} · ${task.priority.name}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsStep(
    selectedTags: List<String>,
    suggestedTags: List<String>,
    onAddTag: (String) -> Unit,
    onToggleTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit
) {
    var newTag by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Tags", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = newTag,
                onValueChange = { newTag = it },
                label = { Text("Add tag") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = {
                onAddTag(newTag)
                newTag = ""
            }) {
                Text("Add")
            }
        }

        if (selectedTags.isNotEmpty()) {
            Text("Selected", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                selectedTags.forEach { tag ->
                    InputChip(
                        selected = true,
                        onClick = { onRemoveTag(tag) },
                        label = { Text(tag) },
                        trailingIcon = {
                            Icon(Icons.Filled.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp))
                        }
                    )
                }
            }
        }

        if (suggestedTags.isNotEmpty()) {
            Text("Suggested taxonomy", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestedTags.forEach { tag ->
                    val isSelected = selectedTags.any { it.equals(tag, ignoreCase = true) }
                    FilterChip(
                        selected = isSelected,
                        onClick = { onToggleTag(tag) },
                        label = { Text(tag) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewStep(
    uiState: NewTaskFlowUiState,
    autoSchedulePreview: AutoSchedulePreview,
    autoSchedulingEnabled: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Review", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        ReviewItem("Title", uiState.title)
        if (uiState.description.isNotBlank()) ReviewItem("Description", uiState.description)
        ReviewItem("Quadrant", quadrantDisplayName(uiState.quadrant))
        ReviewItem("Priority", priorityDisplayName(uiState.priority))
        ReviewItem("Recurrence", recurrenceDisplayName(uiState.recurrence))
        if (uiState.recurrence != Recurrence.NONE) {
            ReviewItem(
                "Anchor",
                "${uiState.habitDate?.let(::formatDate) ?: "Not set"} ${uiState.habitTime?.let(::formatTime) ?: ""}".trim()
            )
        }
        if (uiState.recurrence == Recurrence.NONE) {
            ReviewItem(
                "Manual schedule",
                listOfNotNull(
                    uiState.scheduledDate?.let(::formatDate),
                    uiState.scheduledTime?.let(::formatTime)
                ).joinToString(" ").ifBlank { "Not set" }
            )
        }
        ReviewItem("Tags", if (uiState.tags.isEmpty()) "None" else uiState.tags.joinToString(", "))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 2.dp,
            color = if (autoSchedulePreview.willAutoSchedule) Color(0xFFDFF6E7) else Color(0xFFFFF0E2)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = if (autoSchedulePreview.willAutoSchedule) "Auto-scheduling preview" else "Auto-scheduling not applied",
                    fontWeight = FontWeight.SemiBold
                )
                Text(autoSchedulePreview.reason)
                if (autoSchedulingEnabled && uiState.recurrence == Recurrence.NONE && uiState.scheduledDate == null) {
                    Text(
                        "Task has no manual schedule; scheduler will attempt placement when worker runs.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewItem(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> DropdownField(
    label: String,
    value: String,
    items: List<T>,
    itemLabel: (T) -> String,
    onSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(itemLabel(item)) },
                    onClick = {
                        onSelected(item)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SliderField(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("$label (${value.toInt()})", style = MaterialTheme.typography.labelMedium)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(
                thumbColor = NeuroFlowColors.Purple,
                activeTrackColor = NeuroFlowColors.Purple
            )
        )
    }
}

@Composable
private fun DateChip(dateMillis: Long?, placeholder: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) {
        Icon(Icons.Filled.CalendarMonth, contentDescription = "Date", modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(if (dateMillis != null) formatDate(dateMillis) else placeholder)
    }
}

@Composable
private fun TimeChip(timeMillis: Long?, placeholder: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) {
        Icon(Icons.Filled.Schedule, contentDescription = "Time", modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(if (timeMillis != null) formatTime(timeMillis) else placeholder)
    }
}

@Composable
private fun ReminderChip(
    label: String,
    flag: Int,
    currentFlags: Int,
    onFlagsChange: (Int) -> Unit
) {
    val selected = (currentFlags and flag) != 0
    FilterChip(
        selected = selected,
        onClick = {
            onFlagsChange(if (selected) currentFlags xor flag else currentFlags or flag)
        },
        label = { Text(label) },
        leadingIcon = if (selected) {
            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
        } else null
    )
}

private fun quadrantDisplayName(q: Quadrant) = when (q) {
    Quadrant.DO_FIRST -> "Q1: Do First"
    Quadrant.SCHEDULE -> "Q2: Schedule"
    Quadrant.DELEGATE -> "Q3: Delegate"
    Quadrant.ELIMINATE -> "Q4: Eliminate"
}

private fun priorityDisplayName(p: Priority) = when (p) {
    Priority.HIGH -> "High"
    Priority.MEDIUM -> "Medium"
    Priority.LOW -> "Low"
}

private fun recurrenceDisplayName(r: Recurrence) = when (r) {
    Recurrence.NONE -> "No Repeat"
    Recurrence.DAILY -> "Daily"
    Recurrence.WEEKLY -> "Weekly"
    Recurrence.MONTHLY -> "Monthly"
    Recurrence.CUSTOM -> "Custom"
}

private val durationOptions = listOf(
    "0 Mins" to 0,
    "1 Min" to 1,
    "5 Mins" to 5,
    "15 Mins" to 15,
    "30 Mins" to 30,
    "45 Mins" to 45,
    "1 Hour" to 60,
    "1.5 Hours" to 90,
    "2 Hours" to 120,
    "3 Hours" to 180,
    "4 Hours" to 240,
    "6 Hours" to 360,
    "8 Hours" to 480,
    "10 Hours" to 600
)

private fun durationDisplayName(minutes: Int): String = durationOptions
    .firstOrNull { it.second == minutes }
    ?.first
    ?: "$minutes Mins"

private fun formatDate(millis: Long): String {
    val sdf = SimpleDateFormat("MM/dd", Locale.getDefault())
    return sdf.format(Date(millis))
}

private fun formatTime(millis: Long): String {
    val hours = (millis / 3_600_000L).toInt()
    val minutes = ((millis % 3_600_000L) / 60_000L).toInt()
    val amPm = if (hours < 12) "AM" else "PM"
    val displayHour = if (hours == 0) 12 else if (hours > 12) hours - 12 else hours
    return String.format("%d:%02d %s", displayHour, minutes, amPm)
}

private fun utcMidnightToLocalMidnight(utcMillis: Long): Long {
    val utcCal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
    utcCal.timeInMillis = utcMillis

    val localCal = java.util.Calendar.getInstance()
    localCal.set(java.util.Calendar.YEAR, utcCal.get(java.util.Calendar.YEAR))
    localCal.set(java.util.Calendar.MONTH, utcCal.get(java.util.Calendar.MONTH))
    localCal.set(java.util.Calendar.DAY_OF_MONTH, utcCal.get(java.util.Calendar.DAY_OF_MONTH))
    localCal.set(java.util.Calendar.HOUR_OF_DAY, 0)
    localCal.set(java.util.Calendar.MINUTE, 0)
    localCal.set(java.util.Calendar.SECOND, 0)
    localCal.set(java.util.Calendar.MILLISECOND, 0)
    return localCal.timeInMillis
}
