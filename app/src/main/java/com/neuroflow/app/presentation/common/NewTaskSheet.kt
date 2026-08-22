package com.neuroflow.app.presentation.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neuroflow.app.data.local.entity.TaskEntity
import com.neuroflow.app.domain.model.EnergyLevel
import com.neuroflow.app.domain.model.Priority
import com.neuroflow.app.domain.model.Quadrant
import com.neuroflow.app.domain.model.Recurrence
import com.neuroflow.app.domain.model.TaskType
import com.neuroflow.app.presentation.common.theme.NeuroFlowColors
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewTaskSheet(
    onDismiss: () -> Unit,
    onSave: (TaskEntity) -> Unit,
    editTask: TaskEntity? = null,
    prefilledQuadrant: Quadrant? = null,
    availableTasks: List<TaskEntity> = emptyList()
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isEditing = editTask != null

    var title by remember { mutableStateOf(editTask?.title ?: "") }
    var description by remember { mutableStateOf(editTask?.description ?: "") }
    var tags by remember { mutableStateOf<List<String>>(editTask?.tags?.split(",")?.filter { it.isNotBlank() } ?: emptyList()) }
    var selectedQuadrant by remember { mutableStateOf(editTask?.quadrant ?: prefilledQuadrant ?: Quadrant.DO_FIRST) }
    var selectedPriority by remember { mutableStateOf(editTask?.priority ?: Priority.MEDIUM) }
    var selectedRecurrence by remember { mutableStateOf(editTask?.recurrence ?: Recurrence.NONE) }
    var customIntervalDays by remember { mutableIntStateOf(editTask?.recurrenceIntervalDays ?: 1) }
    var deadlineDate by remember { mutableLongStateOf(editTask?.deadlineDate ?: 0L) }
    var deadlineTime by remember { mutableLongStateOf(editTask?.deadlineTime ?: -1L) }
    var scheduledDate by remember { mutableLongStateOf(editTask?.scheduledDate ?: 0L) }
    var scheduledTime by remember { mutableLongStateOf(editTask?.scheduledTime ?: -1L) }
    var isScheduleLocked by remember { mutableStateOf(editTask?.isScheduleLocked ?: false) }
    var habitDate by remember { mutableLongStateOf(editTask?.habitDate ?: 0L) }
    var estimatedDuration by remember { mutableIntStateOf(editTask?.estimatedDurationMinutes ?: 0) }
    var impactScore by remember { mutableFloatStateOf((editTask?.impactScore ?: 50).toFloat()) }
    var valueScore by remember { mutableFloatStateOf((editTask?.valueScore ?: 50).toFloat()) }
    var effortScore by remember { mutableFloatStateOf((editTask?.effortScore ?: 50).toFloat()) }
    var reminderFlags by remember { mutableIntStateOf(editTask?.reminderFlags ?: 0) }
    var waitingFor by remember { mutableStateOf(editTask?.waitingFor ?: "") }

    // Neuro boost
    var isFrog by remember { mutableStateOf(editTask?.isFrog ?: false) }
    var energyLevel by remember { mutableStateOf(editTask?.energyLevel ?: EnergyLevel.MEDIUM) }
    var contextTag by remember { mutableStateOf(editTask?.contextTag ?: "") }
    var ifThenPlan by remember { mutableStateOf(editTask?.ifThenPlan ?: "") }
    var taskType by remember { mutableStateOf(editTask?.taskType ?: TaskType.ANALYTICAL) }
    var enjoymentScore by remember { mutableFloatStateOf((editTask?.enjoymentScore ?: 50).toFloat()) }
    var isPublicCommitment by remember { mutableStateOf(editTask?.isPublicCommitment ?: false) }
    var isAnxietyTask by remember { mutableStateOf(editTask?.isAnxietyTask ?: false) }
    var goalRiskLevel by remember { mutableIntStateOf(editTask?.goalRiskLevel ?: 0) }
    var dependsOnTaskIds by remember { mutableStateOf(editTask?.dependsOnTaskIds ?: "") }
    var showNeuroBoost by remember { mutableStateOf(false) }
    var showDepsDialog by remember { mutableStateOf(false) }
    // Parse selected dep IDs into a set for easy toggle
    var selectedDepIds by remember {
        mutableStateOf(
            editTask?.dependsOnTaskIds?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
        )
    }

    var showTagDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showSchedDatePicker by remember { mutableStateOf(false) }
    var showSchedTimePicker by remember { mutableStateOf(false) }
    var showHabitDatePicker by remember { mutableStateOf(false) }
    var showHabitTimePicker by remember { mutableStateOf(false) }
    var habitTime by remember { mutableLongStateOf(editTask?.habitDate?.let {
        // extract time-of-day portion if already set
        val cal = Calendar.getInstance().apply { timeInMillis = it }
        (cal.get(Calendar.HOUR_OF_DAY) * 3600000L + cal.get(Calendar.MINUTE) * 60000L).takeIf { t -> t > 0L } ?: -1L
    } ?: -1L) }
    var datePickerTarget by remember { mutableStateOf("deadline") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Header
            Text(
                text = if (isEditing) "Edit Task" else "New Task",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Task Name
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Task Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeuroFlowColors.Purple,
                    focusedLabelColor = NeuroFlowColors.Purple
                )
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Description
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeuroFlowColors.Purple,
                    focusedLabelColor = NeuroFlowColors.Purple
                )
            )
            Spacer(modifier = Modifier.height(16.dp))

            // TAGS
            SectionLabel("TAGS")
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { showTagDialog = true }) {
                    Icon(Icons.Filled.Add, "Add", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Tag")
                }
            }
            if (tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    tags.forEach { tag ->
                        InputChip(
                            selected = false,
                            onClick = { tags = tags.toMutableList().apply { remove(tag) } },
                            label = { Text(tag) },
                            trailingIcon = {
                                Icon(Icons.Filled.Close, "Remove", modifier = Modifier.size(14.dp))
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Quadrant dropdown
            var quadrantExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = quadrantExpanded,
                onExpandedChange = { quadrantExpanded = it }
            ) {
                OutlinedTextField(
                    value = quadrantDisplayName(selectedQuadrant),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Quadrant") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = quadrantExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = quadrantExpanded,
                    onDismissRequest = { quadrantExpanded = false }
                ) {
                    Quadrant.entries.forEach { q ->
                        DropdownMenuItem(
                            text = { Text(quadrantDisplayName(q)) },
                            onClick = {
                                selectedQuadrant = q
                                quadrantExpanded = false
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Priority dropdown
            var priorityExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = priorityExpanded,
                onExpandedChange = { priorityExpanded = it }
            ) {
                OutlinedTextField(
                    value = priorityDisplayName(selectedPriority),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Priority") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = priorityExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = priorityExpanded,
                    onDismissRequest = { priorityExpanded = false }
                ) {
                    Priority.entries.forEach { p ->
                        DropdownMenuItem(
                            text = { Text(priorityDisplayName(p)) },
                            onClick = {
                                selectedPriority = p
                                priorityExpanded = false
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Recurrence dropdown
            var recurrenceExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = recurrenceExpanded,
                onExpandedChange = { recurrenceExpanded = it }
            ) {
                OutlinedTextField(
                    value = recurrenceDisplayName(selectedRecurrence),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Recurrence") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = recurrenceExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = recurrenceExpanded,
                    onDismissRequest = { recurrenceExpanded = false }
                ) {
                    Recurrence.entries.forEach { r ->
                        DropdownMenuItem(
                            text = { Text(recurrenceDisplayName(r)) },
                            onClick = {
                                selectedRecurrence = r
                                recurrenceExpanded = false
                            }
                        )
                    }
                }
            }
            if (selectedRecurrence == Recurrence.CUSTOM) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Every", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = customIntervalDays.toString(),
                        onValueChange = { v -> v.toIntOrNull()?.let { if (it > 0) customIntervalDays = it } },
                        label = { Text("Days") },
                        singleLine = true,
                        modifier = Modifier.width(80.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeuroFlowColors.Purple,
                            focusedLabelColor = NeuroFlowColors.Purple
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("day(s)", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // HABIT DATE — shown only for recurring tasks; this is the anchor that shifts each cycle
            if (selectedRecurrence != Recurrence.NONE) {
                SectionLabel("HABIT START DATE & TIME (first occurrence)")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showHabitDatePicker = true }) {
                        Icon(Icons.Filled.CalendarMonth, "Date", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (habitDate > 0) formatDate(habitDate) else "Pick date",
                            color = if (habitDate > 0) NeuroFlowColors.Purple else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    OutlinedButton(onClick = { showHabitTimePicker = true }) {
                        Icon(Icons.Filled.Schedule, "Time", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (habitTime >= 0) formatTime(habitTime) else "Pick time",
                            color = if (habitTime >= 0) NeuroFlowColors.Purple else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // DEADLINE — hidden for recurring tasks (use habitDate instead)
            if (selectedRecurrence == Recurrence.NONE) {
                SectionLabel("DEADLINE (when it must be done)")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        datePickerTarget = "deadline"
                        showDatePicker = true
                    }) {
                        Icon(Icons.Filled.CalendarMonth, "Date", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (deadlineDate > 0) formatDate(deadlineDate) else "Date",
                            color = if (deadlineDate > 0) NeuroFlowColors.Purple else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    OutlinedButton(onClick = {
                        datePickerTarget = "deadline"
                        showTimePicker = true
                    }) {
                        Icon(Icons.Filled.Schedule, "Time", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (deadlineTime >= 0) formatTime(deadlineTime) else "Time",
                            color = if (deadlineTime >= 0) NeuroFlowColors.Purple else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // REMINDERS
            SectionLabel("REMINDERS (before deadline/scheduled time)")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReminderChip("15 min", 1, reminderFlags) { reminderFlags = it }
                ReminderChip("30 min", 2, reminderFlags) { reminderFlags = it }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReminderChip("1 hour", 4, reminderFlags) { reminderFlags = it }
                ReminderChip("1 day", 8, reminderFlags) { reminderFlags = it }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // SCHEDULED — hidden for recurring tasks (habitDate is the anchor)
            if (selectedRecurrence == Recurrence.NONE) {
                SectionLabel("SCHEDULED (when you plan to do it)")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        datePickerTarget = "scheduled"
                        showSchedDatePicker = true
                    }) {
                        Icon(Icons.Filled.CalendarMonth, "Date", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (scheduledDate > 0) formatDate(scheduledDate) else "Date",
                            color = if (scheduledDate > 0) NeuroFlowColors.Purple else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    OutlinedButton(onClick = {
                        datePickerTarget = "scheduled"
                        showSchedTimePicker = true
                    }) {
                        Icon(Icons.Filled.Schedule, "Time", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (scheduledTime >= 0) formatTime(scheduledTime) else "Time",
                            color = if (scheduledTime >= 0) NeuroFlowColors.Purple else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Lock schedule
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = isScheduleLocked,
                        onCheckedChange = { isScheduleLocked = it }
                    )
                    Text("🔒 Lock Schedule (won't be auto-rescheduled)")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ESTIMATED DURATION
            SectionLabel("ESTIMATED DURATION")
            var durationExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = durationExpanded,
                onExpandedChange = { durationExpanded = it }
            ) {
                OutlinedTextField(
                    value = durationDisplayName(estimatedDuration),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Duration") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = durationExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = durationExpanded,
                    onDismissRequest = { durationExpanded = false }
                ) {
                    durationOptions.forEach { (label, minutes) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                estimatedDuration = minutes
                                durationExpanded = false
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // TASK SCORING
            SectionLabel("TASK SCORING")

            // Impact
            Text("Strategic Impact (0-100)", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = impactScore.toInt().toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = NeuroFlowColors.Purple,
                    modifier = Modifier.width(40.dp)
                )
                Slider(
                    value = impactScore,
                    onValueChange = { impactScore = it },
                    valueRange = 0f..100f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(thumbColor = NeuroFlowColors.Purple, activeTrackColor = NeuroFlowColors.Purple)
                )
            }

            // Value
            Text("Intrinsic Value (0-100)", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = valueScore.toInt().toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = NeuroFlowColors.ScheduleText,
                    modifier = Modifier.width(40.dp)
                )
                Slider(
                    value = valueScore,
                    onValueChange = { valueScore = it },
                    valueRange = 0f..100f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(thumbColor = NeuroFlowColors.ScheduleText, activeTrackColor = NeuroFlowColors.ScheduleText)
                )
            }

            // Effort
            Text("Effort Required (0=easy, 100=hard)", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = effortScore.toInt().toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = NeuroFlowColors.DelegateText,
                    modifier = Modifier.width(40.dp)
                )
                Slider(
                    value = effortScore,
                    onValueChange = { effortScore = it },
                    valueRange = 0f..100f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(thumbColor = NeuroFlowColors.DelegateText, activeTrackColor = NeuroFlowColors.DelegateText)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Waiting for
            OutlinedTextField(
                value = waitingFor,
                onValueChange = { waitingFor = it },
                label = { Text("Waiting for (external dependency)") },
                placeholder = { Text("e.g., Waiting for client approval") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeuroFlowColors.Purple,
                    focusedLabelColor = NeuroFlowColors.Purple
                )
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Neuro Boost Section (collapsible)
            TextButton(
                onClick = { showNeuroBoost = !showNeuroBoost }
            ) {
                Text(
                    if (showNeuroBoost) "🧠 Hide Neuro Boost ▲" else "🧠 Show Neuro Boost ▼",
                    color = NeuroFlowColors.Purple
                )
            }

            if (showNeuroBoost) {
                Spacer(modifier = Modifier.height(8.dp))

                // Frog toggle
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = isFrog,
                        onCheckedChange = { isFrog = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = NeuroFlowColors.Purple)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("🐸 Mark as Frog (hardest task first)")
                }
                Spacer(modifier = Modifier.height(12.dp))

                // Energy Level
                Text("Energy Level", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EnergyLevel.entries.forEach { level ->
                        FilterChip(
                            selected = energyLevel == level,
                            onClick = { energyLevel = level },
                            label = {
                                Text(
                                    when (level) {
                                        EnergyLevel.HIGH -> "🔴 High"
                                        EnergyLevel.MEDIUM -> "🟡 Medium"
                                        EnergyLevel.LOW -> "🟢 Low"
                                    }
                                )
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                // Task Type (circadian matching)
                Text("Task Type", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    TaskType.entries.forEach { type ->
                        FilterChip(
                            selected = taskType == type,
                            onClick = { taskType = type },
                            label = {
                                Text(
                                    when (type) {
                                        TaskType.ANALYTICAL -> "🧠 Analytical"
                                        TaskType.CREATIVE   -> "🎨 Creative"
                                        TaskType.ADMIN      -> "📋 Admin"
                                        TaskType.PHYSICAL   -> "💪 Physical"
                                    },
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                // Context tag
                Text("Context Tag", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("@work", "@home", "@phone", "@computer", "@errands").forEach { tag ->
                        FilterChip(
                            selected = contextTag == tag,
                            onClick = { contextTag = if (contextTag == tag) "" else tag },
                            label = { Text(tag) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                // Enjoyment Score
                Text("Enjoyment (0=dread, 100=love)", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = enjoymentScore.toInt().toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = NeuroFlowColors.Purple,
                        modifier = Modifier.width(40.dp)
                    )
                    Slider(
                        value = enjoymentScore,
                        onValueChange = { enjoymentScore = it },
                        valueRange = 0f..100f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(thumbColor = NeuroFlowColors.Purple, activeTrackColor = NeuroFlowColors.Purple)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                // Public Commitment toggle
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = isPublicCommitment,
                        onCheckedChange = { isPublicCommitment = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = NeuroFlowColors.Purple)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("📢 Public Commitment")
                        Text("Told someone you'd do this", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Anxiety Task toggle
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = isAnxietyTask,
                        onCheckedChange = { isAnxietyTask = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = NeuroFlowColors.Purple)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("😰 Anxiety Task")
                        Text("You tend to avoid this — surface it early", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                // Goal Risk Level
                Text("Goal Risk Level", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "None", 1 to "⚠ At Risk", 2 to "🚨 Critical").forEach { (level, label) ->
                        FilterChip(
                            selected = goalRiskLevel == level,
                            onClick = { goalRiskLevel = level },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                // Depends On — task picker
                Text("Depends On", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { showDepsDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Add, "Pick tasks", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (selectedDepIds.isEmpty()) "Select tasks this depends on"
                        else "${selectedDepIds.size} task(s) selected"
                    )
                }
                if (selectedDepIds.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    val depTitles = availableTasks.filter { it.id in selectedDepIds }
                    depTitles.forEach { dep ->
                        InputChip(
                            selected = true,
                            onClick = { selectedDepIds = selectedDepIds - dep.id },
                            label = { Text(dep.title, maxLines = 1) },
                            trailingIcon = {
                                Icon(Icons.Filled.Close, "Remove", modifier = Modifier.size(14.dp))
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                // If-Then Plan
                OutlinedTextField(
                    value = ifThenPlan,
                    onValueChange = { ifThenPlan = it },
                    label = { Text("If-Then Plan") },
                    placeholder = { Text("e.g., When I sit at my desk at 9am, I will do this") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Footer buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            val task = (editTask ?: TaskEntity(title = title)).copy(
                                title = title,
                                description = description,
                                tags = tags.joinToString(","),
                                quadrant = selectedQuadrant,
                                priority = selectedPriority,
                                recurrence = selectedRecurrence,
                                recurrenceIntervalDays = customIntervalDays,
                                habitDate = if (selectedRecurrence != Recurrence.NONE && habitDate > 0) {
                                    // Merge date + time into a single epoch millis
                                    val datePart = habitDate
                                    val timePart = if (habitTime >= 0) habitTime else 0L
                                    datePart + timePart
                                } else null,
                                deadlineDate = if (selectedRecurrence == Recurrence.NONE && deadlineDate > 0) deadlineDate else null,
                                deadlineTime = if (selectedRecurrence == Recurrence.NONE && deadlineTime >= 0) deadlineTime else null,
                                scheduledDate = if (selectedRecurrence == Recurrence.NONE && scheduledDate > 0) scheduledDate else null,
                                scheduledTime = if (selectedRecurrence == Recurrence.NONE && scheduledTime >= 0) scheduledTime else null,
                                isScheduleLocked = isScheduleLocked,
                                estimatedDurationMinutes = estimatedDuration,
                                impactScore = impactScore.toInt(),
                                valueScore = valueScore.toInt(),
                                effortScore = effortScore.toInt(),
                                reminderFlags = reminderFlags,
                                waitingFor = waitingFor,
                                isFrog = isFrog,
                                energyLevel = energyLevel,
                                taskType = taskType,
                                contextTag = contextTag,
                                ifThenPlan = ifThenPlan,
                                enjoymentScore = enjoymentScore.toInt(),
                                isPublicCommitment = isPublicCommitment,
                                isAnxietyTask = isAnxietyTask,
                                goalRiskLevel = goalRiskLevel,
                                dependsOnTaskIds = selectedDepIds.joinToString(","),
                                updatedAt = System.currentTimeMillis()
                            )
                            onSave(task)
                        }
                    },
                    enabled = title.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeuroFlowColors.Purple
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text(if (isEditing) "Update" else "Add")
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Dependency picker dialog
    if (showDepsDialog) {
        val otherTasks = availableTasks.filter { it.id != editTask?.id }
        AlertDialog(
            onDismissRequest = { showDepsDialog = false },
            title = { Text("Select Dependencies") },
            text = {
                if (otherTasks.isEmpty()) {
                    Text("No other active tasks available.")
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.heightIn(max = 400.dp)
                    ) {
                        items(otherTasks) { t ->
                            val isSelected = t.id in selectedDepIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedDepIds = if (isSelected) selectedDepIds - t.id else selectedDepIds + t.id
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = {
                                        selectedDepIds = if (isSelected) selectedDepIds - t.id else selectedDepIds + t.id
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(t.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                    Text(
                                        "${t.quadrant.name.replace("_", " ")} · ${t.priority.name}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDepsDialog = false }) { Text("Done") }
            }
        )
    }

    // Tag dialog
    if (showTagDialog) {
        var newTag by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showTagDialog = false },
            title = { Text("Add Tag") },
            text = {
                OutlinedTextField(
                    value = newTag,
                    onValueChange = { newTag = it },
                    label = { Text("Tag name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newTag.isNotBlank()) {
                        tags = tags.toMutableList().apply { add(newTag.trim()) }
                    }
                    showTagDialog = false
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showTagDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Habit date picker
    if (showHabitDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = if (habitDate > 0) habitDate else System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showHabitDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis -> habitDate = utcMidnightToLocalMidnight(millis) }
                    showHabitDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showHabitDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Habit time picker
    if (showHabitTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = if (habitTime >= 0) (habitTime / 3600000).toInt() else 8,
            initialMinute = if (habitTime >= 0) ((habitTime % 3600000) / 60000).toInt() else 0
        )
        AlertDialog(
            onDismissRequest = { showHabitTimePicker = false },
            title = { Text("Select Habit Time") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    habitTime = timePickerState.hour * 3600000L + timePickerState.minute * 60000L
                    showHabitTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showHabitTimePicker = false }) { Text("Cancel") }
            }
        )
    }

    // Date pickers
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        deadlineDate = utcMidnightToLocalMidnight(millis)
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showSchedDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showSchedDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        scheduledDate = utcMidnightToLocalMidnight(millis)
                    }
                    showSchedDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showSchedDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState()
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Select Time") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    deadlineTime = (timePickerState.hour * 3600000L + timePickerState.minute * 60000L)
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            }
        )
    }

    if (showSchedTimePicker) {
        val timePickerState = rememberTimePickerState()
        AlertDialog(
            onDismissRequest = { showSchedTimePicker = false },
            title = { Text("Select Time") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    scheduledTime = (timePickerState.hour * 3600000L + timePickerState.minute * 60000L)
                    showSchedTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showSchedTimePicker = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun ReminderChip(
    label: String,
    flag: Int,
    currentFlags: Int,
    onFlagsChange: (Int) -> Unit
) {
    val isSelected = (currentFlags and flag) != 0
    FilterChip(
        selected = isSelected,
        onClick = {
            onFlagsChange(if (isSelected) currentFlags xor flag else currentFlags or flag)
        },
        label = { Text(label) },
        leadingIcon = if (isSelected) {
            { Icon(Icons.Filled.Check, "Selected", modifier = Modifier.size(16.dp)) }
        } else null
    )
}

private fun quadrantDisplayName(q: Quadrant) = when (q) {
    Quadrant.DO_FIRST  -> "Q1: Do First"
    Quadrant.SCHEDULE  -> "Q2: Schedule"
    Quadrant.DELEGATE  -> "Q3: Delegate"
    Quadrant.ELIMINATE -> "Q4: Eliminate"
}

private fun priorityDisplayName(p: Priority) = when (p) {
    Priority.HIGH   -> "🔴 High Priority"
    Priority.MEDIUM -> "🟠 Medium Priority"
    Priority.LOW    -> "🟡 Low Priority"
}

private fun recurrenceDisplayName(r: Recurrence) = when (r) {
    Recurrence.NONE    -> "🔕 No Repeat"
    Recurrence.DAILY   -> "📅 Daily"
    Recurrence.WEEKLY  -> "📅 Weekly"
    Recurrence.MONTHLY -> "📅 Monthly"
    Recurrence.CUSTOM  -> "📅 Custom"
}

private val durationOptions = listOf(
    "0 Mins" to 0, "1 Min" to 1, "5 Mins" to 5, "15 Mins" to 15,
    "30 Mins" to 30, "45 Mins" to 45, "1 Hour" to 60, "1.5 Hours" to 90,
    "2 Hours" to 120, "3 Hours" to 180, "4 Hours" to 240,
    "6 Hours" to 360, "8 Hours" to 480, "10 Hours" to 600
)

private fun durationDisplayName(minutes: Int): String = durationOptions
    .firstOrNull { it.second == minutes }?.first ?: "$minutes Mins"

private fun formatDate(millis: Long): String {
    val sdf = SimpleDateFormat("MM/dd", Locale.getDefault())
    return sdf.format(Date(millis))
}

private fun formatTime(millis: Long): String {
    val hours = (millis / 3600000).toInt()
    val minutes = ((millis % 3600000) / 60000).toInt()
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
