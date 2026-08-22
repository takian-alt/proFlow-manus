package com.neuroflow.app.presentation.focus

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neuroflow.app.data.local.entity.TaskEntity
import com.neuroflow.app.presentation.common.getQuadrantLabel
import com.neuroflow.app.presentation.common.theme.LocalIsDarkTheme
import com.neuroflow.app.presentation.common.theme.NeuroFlowColors
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FocusScreen(
    onNavigateBack: () -> Unit,
    onNavigateToNextTask: (taskId: String, skipped: String) -> Unit,
    viewModel: FocusViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val task = uiState.task

    // Intercept system back button while tracking
    BackHandler(enabled = uiState.isTracking) {
        viewModel.showTrackingBlockDialog()
    }

    if (uiState.showTrackingBlockDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissTrackingBlockDialog() },
            title = { Text("Time Tracking Active") },
            text = { Text("Pause the tracker before leaving, or stay in Focus Mode.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.pauseAndLeave(onNavigateBack) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA726))
                ) {
                    Text("Pause & Leave")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissTrackingBlockDialog() }) {
                    Text("Stay")
                }
            }
        )
    }

    // 3-step stop confirmation
    when (uiState.stopConfirmStep) {
        1 -> AlertDialog(
            onDismissRequest = { viewModel.cancelStop() },
            title = { Text("⚠️ Stop Tracking?") },
            text = { Text("You're about to stop the timer. This will save your current session. Are you sure?") },
            confirmButton = {
                Button(
                    onClick = { viewModel.advanceStopConfirm() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA726))
                ) { Text("Yes, continue") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelStop() }) { Text("Cancel") }
            }
        )
        2 -> AlertDialog(
            onDismissRequest = { viewModel.cancelStop() },
            title = { Text("⚠️ Are you really sure?") },
            text = { Text("Once stopped, the timer resets to zero. You cannot undo this. The session will be saved to history.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.advanceStopConfirm() },
                    colors = ButtonDefaults.buttonColors(containerColor = NeuroFlowColors.TrackingRed)
                ) { Text("Yes, stop it") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelStop() }) { Text("Go back") }
            }
        )
        3 -> AlertDialog(
            onDismissRequest = { viewModel.cancelStop() },
            title = { Text("🛑 Final confirmation") },
            text = { Text("This is your last chance. Stopping now will end this tracking session permanently. Your focus time will be recorded.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.advanceStopConfirm() },
                    colors = ButtonDefaults.buttonColors(containerColor = NeuroFlowColors.TrackingRed)
                ) { Text("STOP TRACKING") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelStop() }) { Text("Keep tracking") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Focus", maxLines = 1) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (uiState.isTracking) viewModel.showTrackingBlockDialog()
                            else onNavigateBack()
                        }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            "Back",
                            tint = if (uiState.isTracking)
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                actions = {
                    if (task?.isScheduleLocked == true) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = NeuroFlowColors.LockedGray
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Lock, "Locked", tint = Color.White, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("LOCKED", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    if (uiState.isTracking) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = NeuroFlowColors.TrackingRed
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.HourglassTop, "Tracking", tint = Color.White, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("TRACKING", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { innerPadding ->
        val focusBg = if (LocalIsDarkTheme.current) NeuroFlowColors.FocusBgDark else NeuroFlowColors.FocusBg
        val deadlineCardColor = if (LocalIsDarkTheme.current) NeuroFlowColors.DeadlineCardDark else NeuroFlowColors.DeadlineCard
        val scheduledCardColor = if (LocalIsDarkTheme.current) NeuroFlowColors.ScheduledCardDark else NeuroFlowColors.ScheduledCard
        val pomodoroCardColor = if (LocalIsDarkTheme.current) NeuroFlowColors.PomodoroCardDark else NeuroFlowColors.PomodoroCard

        if (task == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(focusBg)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Row 1: Live score + urgency
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Priority Score",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                            Text(
                                "${uiState.currentScore}",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "Urgency",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                            Text(
                                uiState.urgencyLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    uiState.urgencyFraction > 0.8f -> NeuroFlowColors.DoFirstText
                                    uiState.urgencyFraction > 0.5f -> NeuroFlowColors.DelegateText
                                    else -> MaterialTheme.colorScheme.onPrimaryContainer
                                }
                            )
                        }
                    }
                    if (uiState.urgencyFraction > 0f) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { uiState.urgencyFraction },
                            modifier = Modifier.fillMaxWidth(),
                            color = when {
                                uiState.urgencyFraction > 0.8f -> NeuroFlowColors.DoFirstText
                                uiState.urgencyFraction > 0.5f -> NeuroFlowColors.DelegateText
                                else -> NeuroFlowColors.ScheduleText
                            },
                            trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Score breakdown — expandable "Why this score?" card
            if (uiState.scoreBreakdown.isNotEmpty()) {
                var expanded by remember { mutableStateOf(false) }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Why this score?",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = if (expanded) "Collapse" else "Expand",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (expanded) {
                            Spacer(modifier = Modifier.height(4.dp))
                            uiState.scoreBreakdown.forEach { (label, pts) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                    Text(
                                        text = if (pts >= 0) "+${pts.toInt()}" else "${pts.toInt()}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (pts >= 0) NeuroFlowColors.ScheduleText else NeuroFlowColors.DoFirstText
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Row 2: Metadata badges
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = getQuadrantLabel(task.quadrant),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFFFCDD2)
                ) {
                    Text(
                        text = "+${task.impactScore / 10} pts on done",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = NeuroFlowColors.DoFirstText
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Row 2: Priority badge
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Transparent,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Text(
                    text = "${task.priority} Priority",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = when (task.priority) {
                        com.neuroflow.app.domain.model.Priority.HIGH -> NeuroFlowColors.DoFirstText
                        com.neuroflow.app.domain.model.Priority.MEDIUM -> NeuroFlowColors.DelegateText
                        com.neuroflow.app.domain.model.Priority.LOW -> Color(0xFFFDD835)
                    }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Row 3: Task title
            Text(
                text = task.title,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))

            // If-Then Plan card — shows the user's implementation intention while working
            if (task.ifThenPlan.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("🎯 YOUR PLAN", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32), fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(task.ifThenPlan, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF1B5E20))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Waiting For card — shows blocker + resolved button
            if (task.waitingFor.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("⏳ WAITING FOR", fontWeight = FontWeight.Bold, color = Color(0xFFE65100), fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(task.waitingFor, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFBF360C))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.resolveWaitingFor() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF2E7D32))
                        ) {
                            Text("Resolved", fontSize = 12.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Blocked deps warning — if this task depends on unfinished tasks
            val blockedByTitles = remember(task.dependsOnTaskIds, uiState.allActiveTasks) {
                if (task.dependsOnTaskIds.isBlank()) emptyList()
                else {
                    val depIds = task.dependsOnTaskIds.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    uiState.allActiveTasks.filter { it.id in depIds }.map { it.title }
                }
            }
            if (blockedByTitles.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("🚫 BLOCKED BY", fontWeight = FontWeight.Bold, color = NeuroFlowColors.DoFirstText, fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        blockedByTitles.forEach { title ->
                            Text("• $title", style = MaterialTheme.typography.bodySmall, color = Color(0xFFB71C1C))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Neuro boost badges row
            val neuroBoostBadges = buildList {
                if (task.isFrog) add("🐸 Frog")
                when (task.energyLevel) {
                    com.neuroflow.app.domain.model.EnergyLevel.HIGH -> add("🔴 High energy")
                    com.neuroflow.app.domain.model.EnergyLevel.LOW  -> add("🟢 Low energy")
                    else -> {}
                }
                when (task.taskType) {
                    com.neuroflow.app.domain.model.TaskType.ANALYTICAL -> add("🧠 Analytical")
                    com.neuroflow.app.domain.model.TaskType.CREATIVE   -> add("🎨 Creative")
                    com.neuroflow.app.domain.model.TaskType.ADMIN      -> add("📋 Admin")
                    com.neuroflow.app.domain.model.TaskType.PHYSICAL   -> add("💪 Physical")
                }
                if (task.contextTag.isNotBlank()) add(task.contextTag)
                if (task.isAnxietyTask) add("😰 Anxiety task")
                if (task.isPublicCommitment) add("📢 Public commitment")
                if (task.goalRiskLevel == 1) add("⚠ Goal at risk")
                if (task.goalRiskLevel == 2) add("🚨 Goal critical")
            }
            if (neuroBoostBadges.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    neuroBoostBadges.forEach { badge ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Row 4: Deadline card
            if (task.deadlineDate != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = deadlineCardColor),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("⏰ DEADLINE", fontWeight = FontWeight.Bold, color = NeuroFlowColors.DoFirstText, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        val deadlineMillis = task.deadlineDate + (task.deadlineTime ?: 0)
                        Text(
                            text = relativeTimeString(deadlineMillis),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = formatFullDate(deadlineMillis),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Row 5: Scheduled card
            if (task.scheduledDate != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = scheduledCardColor),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("📅 SCHEDULED", fontWeight = FontWeight.Bold, color = Color(0xFF1565C0), fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        val schedMillis = task.scheduledDate + (task.scheduledTime ?: 0)
                        Text(
                            text = relativeTimeString(schedMillis),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = formatFullDate(schedMillis),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Row 6: Duration badge
            if (task.estimatedDurationMinutes > 0) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "⏱ ${task.estimatedDurationMinutes} mins",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Row 7: Time Tracking card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Time Tracking",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = formatTimer(uiState.elapsedSeconds),
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 48.sp
                        ),
                        color = if (uiState.isPaused)
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (uiState.isTracking) {
                        Text(
                            text = if (uiState.isPaused) "⏸ Paused — timer saved" else "● Recording",
                            color = if (uiState.isPaused) NeuroFlowColors.DelegateText else NeuroFlowColors.ScheduleText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { viewModel.startTracking() },
                            enabled = !uiState.isTracking,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Icon(Icons.Filled.PlayArrow, "Start", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Start")
                        }
                        Button(
                            onClick = { viewModel.pauseTracking() },
                            enabled = uiState.isTracking,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFFA726),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                if (uiState.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                "Pause",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (uiState.isPaused) "Resume" else "Pause")
                        }
                        Button(
                            onClick = { viewModel.requestStop() },
                            enabled = uiState.isTracking,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeuroFlowColors.TrackingRed,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Filled.Stop, "Stop", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Stop")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Row 8: Pomodoro card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = pomodoroCardColor),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "🍅 POMODORO (${uiState.preferences.defaultPomodoroMinutes} min)",
                        fontWeight = FontWeight.Bold,
                        color = NeuroFlowColors.Purple
                    )
                    if (uiState.pomodoroActive) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val remaining = uiState.pomodoroTotal - uiState.pomodoroSeconds
                        Text(
                            text = formatTimer(remaining),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = NeuroFlowColors.Purple
                        )
                        LinearProgressIndicator(
                            progress = { uiState.pomodoroSeconds.toFloat() / uiState.pomodoroTotal },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            color = NeuroFlowColors.Purple,
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                if (uiState.pomodoroActive) viewModel.stopPomodoro()
                                else viewModel.startPomodoro()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeuroFlowColors.Purple
                            )
                        ) {
                            Icon(
                                if (uiState.pomodoroActive) Icons.Filled.Stop else Icons.Filled.Timer,
                                "Pomodoro",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (uiState.pomodoroActive) "Stop" else "Start")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Next task suggestion (updates dynamically)
            if (uiState.nextTaskTitle != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.SkipNext,
                            contentDescription = null,
                            tint = NeuroFlowColors.Purple,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                "Up next",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                uiState.nextTaskTitle!!,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Row 9: Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        viewModel.skipTask()
                        uiState.nextTaskId?.let { onNavigateToNextTask(it, viewModel.buildSkippedArg()) } ?: onNavigateBack()
                    }
                ) {
                    Text("Skip", fontSize = 16.sp)
                }
                Button(
                    onClick = { viewModel.completeTask() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Black,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(24.dp),
                    contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp)
                ) {
                    Text("DONE", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Completion bottom sheet
    if (uiState.showCompletionSheet) {
        CompletionSheet(
            task = task!!,
            pointsEarned = uiState.pointsEarned,
            identityLabel = uiState.preferences.identityLabel,
            onNextTask = {
                viewModel.dismissCompletion()
                uiState.nextTaskId?.let { onNavigateToNextTask(it, viewModel.buildSkippedArg()) } ?: onNavigateBack()
            },
            onHome = {
                viewModel.dismissCompletion()
                onNavigateBack()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompletionSheet(
    task: TaskEntity,
    pointsEarned: Int,
    identityLabel: String,
    onNextTask: () -> Unit,
    onHome: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onHome,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("✅", fontSize = 48.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = task.title,
                style = MaterialTheme.typography.headlineMedium,
                textDecoration = TextDecoration.LineThrough,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "+$pointsEarned XP earned",
                style = MaterialTheme.typography.titleMedium,
                color = NeuroFlowColors.Purple
            )
            if (task.isHabitual && task.habitStreak > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Streak: ${task.habitStreak} days 🔥", fontSize = 14.sp)
            }
            if (identityLabel.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "That's what ${identityLabel}s do.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(onClick = onHome) {
                    Text("Home")
                }
                Button(
                    onClick = onNextTask,
                    colors = ButtonDefaults.buttonColors(containerColor = NeuroFlowColors.Purple)
                ) {
                    Text("Next Task")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun formatTimer(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format(Locale.US, "%d : %02d", mins, secs)
}

private fun relativeTimeString(millis: Long): String {
    val now = System.currentTimeMillis()
    val diff = millis - now
    val absDiff = kotlin.math.abs(diff)
    val isPast = diff < 0

    val minutes = absDiff / 60_000
    val hours = absDiff / 3_600_000
    val days = absDiff / 86_400_000

    val timeStr = when {
        days > 0 -> "$days day${if (days > 1) "s" else ""}"
        hours > 0 -> "$hours hour${if (hours > 1) "s" else ""}"
        minutes > 0 -> "$minutes minute${if (minutes > 1) "s" else ""}"
        else -> "now"
    }

    return when {
        timeStr == "now" -> "Right now"
        isPast -> "Started $timeStr ago"
        else -> "Due in $timeStr"
    }
}

private fun formatFullDate(millis: Long): String {
    val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    return sdf.format(Date(millis))
}
