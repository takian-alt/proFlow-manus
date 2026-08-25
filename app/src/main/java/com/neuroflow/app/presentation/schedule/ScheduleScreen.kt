package com.neuroflow.app.presentation.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import com.neuroflow.app.data.local.entity.timelineStartMinuteOfDay
import com.neuroflow.app.presentation.common.NewTaskSheet
import com.neuroflow.app.presentation.common.getQuadrantBgColor
import com.neuroflow.app.presentation.common.getQuadrantTextColor
import java.text.SimpleDateFormat
import java.util.*

private const val HOURS_PER_DAY = 24
private const val MINUTES_PER_HOUR = 60
private const val MINUTES_PER_DAY = HOURS_PER_DAY * MINUTES_PER_HOUR
private val HOUR_SLOT_HEIGHT = 60.dp
private val MIN_SEGMENT_HEIGHT = 8.dp

private data class HourSegment(
    val task: TaskEntity,
    val offsetMinutesInHour: Int,
    val overlapMinutes: Int,
    val startsInThisHour: Boolean,
    val continuesFromPreviousHour: Boolean,
    val continuesToNextHour: Boolean
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    var actionTaskId by remember { mutableStateOf<String?>(null) }

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

            if (uiState.pendingAutoScheduleReviews.isNotEmpty()) {
                AutoScheduleReviewCard(
                    proposals = uiState.pendingAutoScheduleReviews,
                    tasks = uiState.allActiveTasks,
                    onApproveAll = viewModel::approveAllAutoScheduleProposals,
                    onApprove = viewModel::approveAutoScheduleProposal,
                    onReject = viewModel::rejectAutoScheduleProposal,
                    onShift = viewModel::shiftAutoScheduleProposal
                )
            }
            if (isToday(uiState.selectedDate)) {
                TodayCommandCenterCard(
                    tasksForDay = uiState.tasksForDay,
                    allActiveTasks = uiState.allActiveTasks,
                    workDayStart = uiState.workDayStart,
                    workDayEnd = uiState.workDayEnd,
                    energyNow = uiState.energyNow,
                    onStartFocus = onNavigateToFocus,
                    onReschedule = viewModel::rescheduleTaskToNextBlock
                )
            }
            SchedulePlanHistoryCard(
                versions = uiState.planVersions,
                onRestore = viewModel::restorePlanVersion
            )
            if (uiState.latestUndoableAdjustment != null) {
                TextButton(
                    onClick = viewModel::undoLastScheduleAdjustment,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                ) {
                    Text("Undo last schedule change")
                }
            }

            val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val isToday = isToday(uiState.selectedDate)

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(HOURS_PER_DAY) { hour ->
                    TimelineRow(
                        hour = hour,
                        tasks = uiState.tasksForDay,
                        isCurrentHour = isToday && hour == currentHour,
                        isWorkHour = hour in uiState.workDayStart until uiState.workDayEnd,
                        onTaskClick = onNavigateToFocus,
                        onTaskLongClick = { actionTaskId = it },
                        unavailableBlocks = uiState.unavailableTimeBlocks,
                        selectedDate = uiState.selectedDate,
                        onSlotClick = {
                            val rowStart = uiState.selectedDate + hour * 60 * 60_000L
                            val rowEnd = rowStart + 60 * 60_000L
                            if (uiState.unavailableTimeBlocks.any { it.startMillis < rowEnd && it.endMillis > rowStart }) {
                                viewModel.toggleHourUnavailable(hour)
                            } else {
                                pickerHour = hour
                            }
                        },
                        onSlotLongClick = { viewModel.toggleHourUnavailable(hour) }
                    )
                }
            }
        }
    }

    actionTaskId?.let { taskId ->
        uiState.allActiveTasks.firstOrNull { it.id == taskId }?.let { task ->
            TaskScheduleActionSheet(
                task = task,
                onMoveEarlier = { viewModel.adjustScheduledTask(task, -30); actionTaskId = null },
                onMoveLater = { viewModel.adjustScheduledTask(task, 30); actionTaskId = null },
                onShorten = { viewModel.adjustTaskDuration(task, -30); actionTaskId = null },
                onExtend = { viewModel.adjustTaskDuration(task, 30); actionTaskId = null },
                onToggleLock = { viewModel.toggleTaskScheduleLock(task); actionTaskId = null },
                onSplit = { viewModel.splitScheduledTask(task); actionTaskId = null },
                onConvertToRecurring = { viewModel.convertTaskToRecurring(task); actionTaskId = null },
                onDismiss = { actionTaskId = null }
            )
        }
    }

    // Task picker bottom sheet — tap an empty slot to assign an existing task
    pickerHour?.let { hour ->
        val unscheduledTasks = uiState.allActiveTasks.filter {
            it.scheduledDate == null && it.habitDate == null && !it.isScheduleLocked
        }
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
                        scheduledDate = uiState.selectedDate,
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimelineRow(
    hour: Int,
    tasks: List<TaskEntity>,
    isCurrentHour: Boolean,
    isWorkHour: Boolean,
    onTaskClick: (String) -> Unit,
    onTaskLongClick: (String) -> Unit,
    unavailableBlocks: List<com.neuroflow.app.data.local.entity.UnavailableTimeBlockEntity>,
    selectedDate: Long,
    onSlotClick: () -> Unit,
    onSlotLongClick: () -> Unit
) {
    val hourStartMinute = hour * MINUTES_PER_HOUR
    val hourEndMinute = hourStartMinute + MINUTES_PER_HOUR
    val segments = tasks.map { task ->
        val taskStartMinute = taskStartMinuteOfDay(task)
        val taskEndMinute = (taskStartMinute + taskDurationMinutes(task)).coerceAtMost(MINUTES_PER_DAY)
        val overlapStart = maxOf(taskStartMinute, hourStartMinute)
        val overlapEnd = minOf(taskEndMinute, hourEndMinute)
        if (overlapEnd <= overlapStart) return@map null
        HourSegment(
            task = task,
            offsetMinutesInHour = overlapStart - hourStartMinute,
            overlapMinutes = overlapEnd - overlapStart,
            startsInThisHour = taskStartMinute in hourStartMinute until hourEndMinute,
            continuesFromPreviousHour = taskStartMinute < hourStartMinute,
            continuesToNextHour = taskEndMinute > hourEndMinute
        )
    }.filterNotNull()
        .sortedWith(compareBy<HourSegment>({ taskStartMinuteOfDay(it.task) }, { it.task.id }))

    val workHourBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
    val rowStart = selectedDate + hour * 60 * 60_000L
    val rowEnd = rowStart + 60 * 60_000L
    val isUnavailable = unavailableBlocks.any { it.startMillis < rowEnd && it.endMillis > rowStart }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(
                when {
                    isUnavailable -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
                    isWorkHour -> workHourBg
                    else -> Color.Transparent
                }
            )
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
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp, end = 8.dp, top = 2.dp, bottom = 2.dp)
                .height(HOUR_SLOT_HEIGHT)
                .combinedClickable(
                    onClick = onSlotClick,
                    onLongClick = onSlotLongClick
                )
        ) {
            if (segments.isEmpty()) {
                Text(
                    if (isUnavailable) "Unavailable · long-press to add again" else "+ add · long-press to block",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    segments.forEach { segment ->
                        val topOffset = HOUR_SLOT_HEIGHT * (segment.offsetMinutesInHour / MINUTES_PER_HOUR.toFloat())
                        val rawHeight = HOUR_SLOT_HEIGHT * (segment.overlapMinutes / MINUTES_PER_HOUR.toFloat())
                        val maxAvailableHeight = HOUR_SLOT_HEIGHT - topOffset
                        val blockHeight = maxOf(rawHeight, MIN_SEGMENT_HEIGHT).coerceAtMost(maxAvailableHeight)
                        val seamOverlap = 1.dp
                        val topLift = if (segment.continuesFromPreviousHour) seamOverlap else 0.dp
                        val bottomExtend = if (segment.continuesToNextHour) seamOverlap else 0.dp
                        val adjustedTopOffset = if (topOffset > topLift) topOffset - topLift else 0.dp
                        val adjustedHeight = (blockHeight + topLift + bottomExtend)
                            .coerceAtMost(HOUR_SLOT_HEIGHT - adjustedTopOffset)
                        val blockShape = when {
                            segment.continuesFromPreviousHour && segment.continuesToNextHour -> RoundedCornerShape(0.dp)
                            segment.continuesFromPreviousHour -> RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
                            segment.continuesToNextHour -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                            else -> RoundedCornerShape(8.dp)
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .offset(y = adjustedTopOffset)
                                    .height(adjustedHeight)
                                    .combinedClickable(
                                        onClick = { onTaskClick(segment.task.id) },
                                        onLongClick = { onTaskLongClick(segment.task.id) }
                                    ),
                                shape = blockShape,
                                color = getQuadrantBgColor(segment.task.quadrant)
                            ) {
                                if (segment.startsInThisHour && adjustedHeight >= 24.dp) {
                                    Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = segment.task.title,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Medium,
                                                color = getQuadrantTextColor(segment.task.quadrant),
                                                maxLines = 1
                                            )
                                            if (segment.task.isAutoScheduled) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                                ) {
                                                    Text(
                                                        text = "AUTO",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = getQuadrantTextColor(segment.task.quadrant),
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        if (adjustedHeight >= 36.dp) {
                                            Text(
                                                text = segment.task.quadrant.name.replace("_", " "),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = getQuadrantTextColor(segment.task.quadrant).copy(alpha = 0.7f),
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Text(
                    text = "+ add",
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .clickable { onSlotClick() }
                        .padding(end = 2.dp, bottom = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
            }
        }
    }
}

private fun taskStartMinuteOfDay(task: TaskEntity): Int {
    return task.timelineStartMinuteOfDay()
}

private fun taskDurationMinutes(task: TaskEntity): Int = task.estimatedDurationMinutes.coerceAtLeast(1)

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
