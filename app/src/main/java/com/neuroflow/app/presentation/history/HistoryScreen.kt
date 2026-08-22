package com.neuroflow.app.presentation.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neuroflow.app.data.local.entity.TaskEntity
import com.neuroflow.app.data.local.entity.TimeSessionEntity
import com.neuroflow.app.domain.model.EnergyLevel
import com.neuroflow.app.domain.model.TaskType
import com.neuroflow.app.presentation.common.TagChip
import com.neuroflow.app.presentation.common.formatDurationFloat
import com.neuroflow.app.presentation.common.theme.NeuroFlowColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTaskId by remember { mutableStateOf<String?>(null) }
    var selectedSessions by remember { mutableStateOf<List<TimeSessionEntity>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val selectedTask = selectedTaskId?.let { id -> uiState.completedTasks.find { it.id == id } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        when {
            uiState.isLoading -> {
                Box(Modifier.fillMaxSize().padding(innerPadding), Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.completedTasks.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No completed tasks yet.\nStart completing tasks to see them here!",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(uiState.completedTasks, key = { it.id }) { task ->
                        HistoryTaskRow(
                            task = task,
                            onClick = {
                                scope.launch {
                                    selectedSessions = viewModel.getSessionsForTask(task.id)
                                    selectedTaskId = task.id
                                }
                            },
                            onDelete = {
                                viewModel.deleteTask(task)
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Task deleted",
                                        actionLabel = "Undo",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.restoreTask(task)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    selectedTask?.let { task ->
        HistoryDetailSheet(
            task = task,
            sessions = selectedSessions,
            onDismiss = { selectedTaskId = null }
        )
    }
}

@Composable
private fun HistoryTaskRow(task: TaskEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Filled.CheckCircle,
                "Completed",
                tint = NeuroFlowColors.ScheduleText,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium,
                        textDecoration = TextDecoration.LineThrough
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))

                if (task.estimationErrorMape != null || task.totalTimeTrackedMinutes > 0) {
                    // MAPE
                    task.estimationErrorMape?.let { mape ->
                        val color = when {
                            mape < 20f -> NeuroFlowColors.MapeGood
                            mape < 50f -> NeuroFlowColors.MapeMedium
                            else -> NeuroFlowColors.MapeBad
                        }
                        Text(
                            text = "MAPE: ${String.format("%.1f", mape)}%",
                            fontSize = 12.sp,
                            color = color,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        text = "⏱ Time Tracking: ${formatDurationFloat(task.totalTimeTrackedMinutes)} spent",
                        fontSize = 12.sp,
                        color = Color(0xFF1565C0)
                    )
                    Text(
                        text = "📊 Sessions: ${task.sessionCount} session${if (task.sessionCount != 1) "s" else ""}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "No time tracking",
                        fontSize = 12.sp,
                        color = NeuroFlowColors.MapeGood.copy(alpha = 0.7f)
                    )
                }

                if (task.tags.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        task.tags.split(",").filter { it.isNotBlank() }.take(3).forEach { tag ->
                            TagChip(tag.trim())
                        }
                    }
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp).align(Alignment.CenterVertically)) {
                Icon(Icons.Filled.Delete, "Delete", tint = NeuroFlowColors.MapeBad, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryDetailSheet(
    task: TaskEntity,
    sessions: List<TimeSessionEntity>,
    onDismiss: () -> Unit
) {
    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Text(task.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            if (task.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(task.description, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))

            // ── Classification ────────────────────────────────────────────────
            DetailSection("Classification") {
                DetailRow("Quadrant", when (task.quadrant.name) {
                    "DO_FIRST"  -> "Q1: Do First"
                    "SCHEDULE"  -> "Q2: Schedule"
                    "DELEGATE"  -> "Q3: Delegate"
                    "ELIMINATE" -> "Q4: Eliminate"
                    else        -> task.quadrant.name
                })
                DetailRow("Priority", task.priority.name)
                DetailRow("Task Type", when (task.taskType) {
                    TaskType.ANALYTICAL -> "🧠 Analytical"
                    TaskType.CREATIVE   -> "🎨 Creative"
                    TaskType.ADMIN      -> "📋 Admin"
                    TaskType.PHYSICAL   -> "💪 Physical"
                })
                DetailRow("Energy Level", when (task.energyLevel) {
                    EnergyLevel.HIGH   -> "🔴 High"
                    EnergyLevel.MEDIUM -> "🟡 Medium"
                    EnergyLevel.LOW    -> "🟢 Low"
                })
                if (task.contextTag.isNotBlank()) DetailRow("Context", task.contextTag)
                if (task.tags.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Tags", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        task.tags.split(",").filter { it.isNotBlank() }.forEach { TagChip(it.trim()) }
                    }
                }
            }

            // ── Scoring ───────────────────────────────────────────────────────
            DetailSection("Scoring") {
                DetailRow("Impact", "${task.impactScore}/100")
                DetailRow("Value", "${task.valueScore}/100")
                DetailRow("Effort", "${task.effortScore}/100")
                DetailRow("Enjoyment", "${task.enjoymentScore}/100")
            }

            // ── Timing ────────────────────────────────────────────────────────
            DetailSection("Timing") {
                task.deadlineDate?.let { DetailRow("Deadline", sdf.format(Date(it))) }
                task.scheduledDate?.let { DetailRow("Scheduled", sdf.format(Date(it))) }
                if (task.estimatedDurationMinutes > 0)
                    DetailRow("Estimated", "${task.estimatedDurationMinutes} min")
                if (task.recurrence.name != "NONE") {
                    val recLabel = when (task.recurrence.name) {
                        "DAILY"   -> "Daily"
                        "WEEKLY"  -> "Weekly"
                        "MONTHLY" -> "Monthly"
                        "CUSTOM"  -> "Every ${task.recurrenceIntervalDays} day(s)"
                        else      -> task.recurrence.name
                    }
                    DetailRow("Recurrence", recLabel)
                }
                if (task.isScheduleLocked) DetailRow("Schedule", "🔒 Locked")
            }

            // ── Neuro Boost ───────────────────────────────────────────────────
            DetailSection("Neuro Boost") {
                if (task.isFrog) DetailRow("Frog", "🐸 Yes — hardest task first")
                if (task.isPublicCommitment) DetailRow("Public Commitment", "📢 Yes")
                if (task.isAnxietyTask) DetailRow("Anxiety Task", "😰 Yes")
                if (task.goalRiskLevel > 0) DetailRow("Goal Risk", when (task.goalRiskLevel) {
                    1 -> "⚠ At Risk"
                    2 -> "🚨 Critical"
                    else -> "None"
                })
                if (task.ifThenPlan.isNotBlank()) DetailRow("If-Then Plan", task.ifThenPlan)
                if (task.waitingFor.isNotBlank()) DetailRow("Waiting For", task.waitingFor)
                if (task.dependsOnTaskIds.isNotBlank()) DetailRow("Depends On", task.dependsOnTaskIds)
            }

            // ── Time Tracking ─────────────────────────────────────────────────
            DetailSection("Time Tracking") {
                DetailRow("Total Time", formatDurationFloat(task.totalTimeTrackedMinutes))
                DetailRow("Sessions", "${sessions.size}")
                if (sessions.isNotEmpty()) {
                    val avg = task.totalTimeTrackedMinutes / sessions.size
                    DetailRow("Avg Session", formatDurationFloat(avg))
                }
                task.actualDurationMinutes?.let { DetailRow("Actual Duration", formatDurationFloat(it)) }
                task.estimationErrorMape?.let {
                    val color = when {
                        it < 20f -> NeuroFlowColors.MapeGood
                        it < 50f -> NeuroFlowColors.MapeMedium
                        else     -> NeuroFlowColors.MapeBad
                    }
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text("MAPE", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${String.format("%.1f", it)}%", fontSize = 13.sp, color = color, fontWeight = FontWeight.Medium)
                    }
                }
                if (task.habitStreak > 0) DetailRow("Habit Streak", "🔥 ${task.habitStreak}")
                if (task.postponeCount > 0) DetailRow("Deferred", "${task.postponeCount}×")
                task.completedAt?.let { DetailRow("Completed", sdf.format(Date(it))) }
            }

            // ── Session Details ───────────────────────────────────────────────
            if (sessions.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Session Details", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(8.dp))
                sessions.forEachIndexed { index, session ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Session ${index + 1}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Duration: ${formatDurationFloat(session.durationMinutes)}", fontSize = 13.sp)
                            session.startedAt.let {
                                Text("Started: ${sdf.format(Date(it))}", fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            session.endedAt?.let {
                                Text("Ended: ${sdf.format(Date(it))}", fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = NeuroFlowColors.Purple)
            ) { Text("Close") }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable () -> Unit) {
    Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    Spacer(Modifier.height(6.dp))
    content()
    Spacer(Modifier.height(12.dp))
    HorizontalDivider()
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1.5f))
    }
}
