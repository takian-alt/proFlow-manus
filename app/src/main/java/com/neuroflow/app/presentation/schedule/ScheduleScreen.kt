package com.neuroflow.app.presentation.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neuroflow.app.data.local.entity.TaskEntity
import com.neuroflow.app.domain.model.TaskStatus
import com.neuroflow.app.presentation.common.NewTaskSheet
import com.neuroflow.app.presentation.common.getQuadrantBgColor
import com.neuroflow.app.presentation.common.getQuadrantTextColor
import com.neuroflow.app.presentation.common.theme.NeuroFlowColors
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    onNavigateToFocus: (String) -> Unit,
    viewModel: ScheduleViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val dateFormat = SimpleDateFormat("d - M - yyyy", Locale.getDefault())

    // null = closed, -1 = new task (FAB), 0..23 = slot tap
    var addSheetForHour by remember { mutableStateOf<Int?>(null) }
    // Task picker: which hour slot was tapped to pick an existing task
    var pickerHour by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Time Blocking") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { addSheetForHour = -1 },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add task", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Date navigation
            Surface(
                color = Color(0xFFE3F2FD),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.previousDay() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Previous")
                    }
                    Text(
                        text = dateFormat.format(Date(uiState.selectedDate)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { viewModel.nextDay() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next")
                    }
                }
            }

            // Locked tasks banner
            if (uiState.lockedTasks.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            "🔒 LOCKED SCHEDULE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        uiState.lockedTasks.forEach { task ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToFocus(task.id) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.Lock, "Locked",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    task.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val isToday = isToday(uiState.selectedDate)

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(24) { hour ->
                    val tasksAtHour = uiState.tasksForDay.filter { task ->
                        val taskHour = ((task.scheduledTime ?: 0L) / 3_600_000L).toInt()
                        taskHour == hour
                    }
                    TimelineRow(
                        hour = hour,
                        tasks = tasksAtHour,
                        isCurrentHour = isToday && hour == currentHour,
                        isWorkHour = hour in uiState.workDayStart until uiState.workDayEnd,
                        onTaskClick = onNavigateToFocus,
                        onSlotClick = { pickerHour = hour }
                    )
                }
            }
        }
    }

    // Task picker bottom sheet — tap an empty slot to assign an existing task
    pickerHour?.let { hour ->
        val unscheduledTasks = uiState.allActiveTasks.filter { it.scheduledDate == null && !it.isScheduleLocked }
        TaskPickerSheet(
            hour = hour,
            tasks = unscheduledTasks,
            onDismiss = { pickerHour = null },
            onPick = { task ->
                viewModel.scheduleTask(task, hour)
                pickerHour = null
            },
            onNewTask = {
                pickerHour = null
                addSheetForHour = hour
            }
        )
    }

    // New task sheet — FAB or "new task" from picker
    addSheetForHour?.let { hour ->
        NewTaskSheet(
            onDismiss = { addSheetForHour = null },
            onSave = { task ->
                val scheduledTask = if (hour >= 0) {
                    val cal = Calendar.getInstance().apply {
                        timeInMillis = uiState.selectedDate
                        set(Calendar.HOUR_OF_DAY, hour)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    task.copy(
                        scheduledDate = cal.timeInMillis,
                        scheduledTime = hour * 3_600_000L
                    )
                } else task
                viewModel.insertTask(scheduledTask)
                addSheetForHour = null
            },
            availableTasks = uiState.allActiveTasks
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskPickerSheet(
    hour: Int,
    tasks: List<TaskEntity>,
    onDismiss: () -> Unit,
    onPick: (TaskEntity) -> Unit,
    onNewTask: () -> Unit
) {
    val hourLabel = hourLabel(hour)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                "Schedule task at $hourLabel",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Create new task option
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNewTask() },
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Create new task", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Medium)
                }
            }

            if (tasks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Or pick an existing task",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                tasks.forEach { task ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clickable { onPick(task) },
                        shape = RoundedCornerShape(8.dp),
                        color = getQuadrantBgColor(task.quadrant)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(
                                task.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = getQuadrantTextColor(task.quadrant),
                                maxLines = 1
                            )
                            Text(
                                task.quadrant.name.replace("_", " "),
                                style = MaterialTheme.typography.labelSmall,
                                color = getQuadrantTextColor(task.quadrant).copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "No unscheduled tasks — create a new one above.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TimelineRow(
    hour: Int,
    tasks: List<TaskEntity>,
    isCurrentHour: Boolean,
    isWorkHour: Boolean,
    onTaskClick: (String) -> Unit,
    onSlotClick: () -> Unit
) {
    val workHourBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .defaultMinSize(minHeight = 64.dp)
            .background(if (isWorkHour) workHourBg else Color.Transparent)
            .then(
                if (isCurrentHour) Modifier.drawBehind {
                    drawLine(
                        color = Color.Red,
                        start = Offset(0f, size.height / 2),
                        end = Offset(size.width, size.height / 2),
                        strokeWidth = 2.dp.toPx()
                    )
                } else Modifier
            )
    ) {
        // Hour label
        Text(
            text = hourLabel(hour),
            modifier = Modifier
                .width(56.dp)
                .padding(start = 8.dp, top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )

        // Divider line
        HorizontalDivider(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight(),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        // Task blocks — tapping empty space opens the picker
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp, end = 8.dp, top = 2.dp, bottom = 2.dp)
        ) {
            if (tasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .clickable { onSlotClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "+ add",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                    )
                }
            } else {
                tasks.forEach { task ->
                    val blockHeight = maxOf(48, task.estimatedDurationMinutes).dp
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .height(blockHeight)
                            .clickable { onTaskClick(task.id) },
                        shape = RoundedCornerShape(8.dp),
                        color = getQuadrantBgColor(task.quadrant)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = task.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = getQuadrantTextColor(task.quadrant),
                                maxLines = 1
                            )
                            Text(
                                text = task.quadrant.name.replace("_", " "),
                                style = MaterialTheme.typography.labelSmall,
                                color = getQuadrantTextColor(task.quadrant).copy(alpha = 0.7f)
                            )
                        }
                    }
                }
                // Still allow adding more tasks to a slot that already has one
                TextButton(
                    onClick = onSlotClick,
                    modifier = Modifier.height(28.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text("+ add", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

private fun hourLabel(hour: Int) = when {
    hour == 0  -> "12 am"
    hour < 12  -> "$hour am"
    hour == 12 -> "12 pm"
    else       -> "${hour - 12} pm"
}

private fun isToday(millis: Long): Boolean {
    val cal1 = Calendar.getInstance().apply { timeInMillis = millis }
    val cal2 = Calendar.getInstance()
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}
