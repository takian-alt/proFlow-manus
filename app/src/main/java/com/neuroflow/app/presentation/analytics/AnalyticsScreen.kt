package com.neuroflow.app.presentation.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neuroflow.app.data.local.entity.ContractOutcome
import com.neuroflow.app.domain.engine.AnalyticsEngine
import com.neuroflow.app.domain.model.Priority
import com.neuroflow.app.domain.model.Quadrant
import com.neuroflow.app.presentation.common.theme.NeuroFlowColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(viewModel: AnalyticsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val summary = uiState.summary

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Analytics") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { innerPadding ->
        if (summary == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding), Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TodayCard(summary)
            XpCard(summary)
            OverallProgressCard(summary)
            FocusTimeCard(summary)
            SevenDayTrendCard(summary)
            QuadrantCard(summary)
            PriorityCard(summary)
            EstimatedTimeCard(summary)
            MapeCard(summary) { viewModel.resetEstimationData() }
            SmapeCard(summary)
            HabitsCard(summary)
            StreakCard(summary, uiState.preferences.identityLabel, uiState.preferences.topGoal)
            if (uiState.preferences.peakEnergyStart < uiState.preferences.peakEnergyEnd) {
                // Use effective (blended) peak if available, otherwise fall back to manual
                val displayPeakStart = if (uiState.preferences.effectivePeakStart >= 0)
                    uiState.preferences.effectivePeakStart else uiState.preferences.peakEnergyStart
                val displayPeakEnd = if (uiState.preferences.effectivePeakEnd >= 0)
                    uiState.preferences.effectivePeakEnd else uiState.preferences.peakEnergyEnd
                PeakHourCard(summary, displayPeakStart, displayPeakEnd)
            }
            // Dynamic peak insight — show when detected peak differs from manual by ≥2h
            val detectedStart = uiState.preferences.detectedPeakStart
            val detectedEnd = uiState.preferences.detectedPeakEnd
            val confidence = uiState.preferences.peakDetectionConfidence
            if (detectedStart >= 0 && confidence > 0f) {
                DynamicPeakCard(
                    manualStart = uiState.preferences.peakEnergyStart,
                    manualEnd = uiState.preferences.peakEnergyEnd,
                    detectedStart = detectedStart,
                    detectedEnd = detectedEnd,
                    effectiveStart = if (uiState.preferences.effectivePeakStart >= 0) uiState.preferences.effectivePeakStart else uiState.preferences.peakEnergyStart,
                    effectiveEnd = if (uiState.preferences.effectivePeakEnd >= 0) uiState.preferences.effectivePeakEnd else uiState.preferences.peakEnergyEnd,
                    confidence = confidence
                )
            }
            NeuroBoostCard(summary)
            if (summary.topProcrastinatedTasks.isNotEmpty()) ProcrastinationCard(summary)
            if (uiState.activeContracts.isNotEmpty() || uiState.archivedContracts.isNotEmpty()) {
                CommitmentsCard(uiState.activeContracts, uiState.archivedContracts)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ── Individual cards ──────────────────────────────────────────────────────────

@Composable
private fun XpCard(s: AnalyticsEngine.AnalyticsSummary) {
    if (s.totalXp == 0) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = NeuroFlowColors.Purple.copy(alpha = 0.12f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("⚡ XP & Points", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                StatColumn("${s.totalXp}", "Total XP")
                StatColumn("+${s.xpToday}", "Today")
                StatColumn("+${s.xpThisWeek}", "This Week")
            }
            if (s.topXpTasks.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text("Top earners", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                s.topXpTasks.forEach { (title, pts) ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        Arrangement.SpaceBetween,
                        Alignment.CenterVertically
                    ) {
                        Text(title, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1)
                        Text(
                            "+$pts XP",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeuroFlowColors.Purple
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayCard(s: AnalyticsEngine.AnalyticsSummary) {    AnalyticsCard("Today's Snapshot") {
        val h = (s.focusMinutesToday / 60).toInt()
        val m = (s.focusMinutesToday % 60).toInt()
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
            StatColumn(if (h > 0) "${h}h ${m}m" else "${m}m", "Focus Time")
            StatColumn("${s.completedToday}", "Completed")
            StatColumn("${s.totalSessions}", "Total Sessions")
        }
    }
}

@Composable
private fun OverallProgressCard(s: AnalyticsEngine.AnalyticsSummary) {
    AnalyticsCard("Overall Progress") {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
            StatColumn("${s.totalTasks}", "Total")
            StatColumn("${s.completedTasks}", "Done")
            StatColumn("${s.remainingTasks}", "Remaining")
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("Completion Rate")
            Text("${String.format("%.1f", s.completionRate)}%", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { (s.completionRate / 100f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = Color(0xFFFFA726)
        )
    }
}

@Composable
private fun FocusTimeCard(s: AnalyticsEngine.AnalyticsSummary) {
    AnalyticsCard("Focus Time") {
        val totalH = (s.focusMinutesTotal / 60).toInt()
        val totalM = (s.focusMinutesTotal % 60).toInt()
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
            StatColumn(if (totalH > 0) "${totalH}h ${totalM}m" else "${totalM}m", "All Time")
            StatColumn("${s.avgSessionMinutes.toInt()}m", "Avg Session")
            StatColumn("${s.totalSessions}", "Sessions")
        }
        if (s.mostFocusedTaskTitle != null) {
            Spacer(Modifier.height(10.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Text("Most focused on:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(s.mostFocusedTaskTitle, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

@Composable
private fun SevenDayTrendCard(s: AnalyticsEngine.AnalyticsSummary) {
    if (s.sevenDayTrend.none { it.second > 0f }) return
    AnalyticsCard("7-Day Focus Trend") {
        val maxMins = s.sevenDayTrend.maxOfOrNull { it.second }?.coerceAtLeast(1f) ?: 1f
        Row(
            modifier = Modifier.fillMaxWidth().height(100.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            s.sevenDayTrend.forEach { (label, mins) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val fraction = (mins / maxMins).coerceIn(0f, 1f)
                    val barH = (fraction * 72f).dp.coerceAtLeast(2.dp)
                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .height(barH)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(
                                if (mins > 0f) NeuroFlowColors.Purple
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (mins > 0f) {
                        Text(
                            "${mins.toInt()}m",
                            fontSize = 9.sp,
                            color = NeuroFlowColors.Purple,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuadrantCard(s: AnalyticsEngine.AnalyticsSummary) {
    AnalyticsCard("Tasks by Quadrant") {
        val quadrantData = listOf(
            "DO" to (s.tasksByQuadrant[Quadrant.DO_FIRST] ?: 0) to NeuroFlowColors.DoFirstBg,
            "PLAN" to (s.tasksByQuadrant[Quadrant.SCHEDULE] ?: 0) to NeuroFlowColors.ScheduleBg,
            "DELEGATE" to (s.tasksByQuadrant[Quadrant.DELEGATE] ?: 0) to NeuroFlowColors.DelegateBg,
            "DELETE" to (s.tasksByQuadrant[Quadrant.ELIMINATE] ?: 0) to NeuroFlowColors.EliminateBg
        )
        val maxCount = quadrantData.maxOfOrNull { it.first.second }?.coerceAtLeast(1) ?: 1
        quadrantData.forEach { (pair, color) ->
            val (label, count) = pair
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, modifier = Modifier.width(72.dp), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Box(
                    modifier = Modifier.weight(1f).height(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier.fillMaxHeight()
                            .fillMaxWidth(count.toFloat() / maxCount)
                            .clip(RoundedCornerShape(4.dp))
                            .background(color)
                    )
                }
                Text("$count", modifier = Modifier.width(32.dp), textAlign = TextAlign.End, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PriorityCard(s: AnalyticsEngine.AnalyticsSummary) {
    AnalyticsCard("Tasks by Priority") {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
            PriorityCircle(s.tasksByPriority[Priority.HIGH] ?: 0, "High", NeuroFlowColors.DoFirstBg, NeuroFlowColors.DoFirstText)
            PriorityCircle(s.tasksByPriority[Priority.MEDIUM] ?: 0, "Medium", NeuroFlowColors.DelegateBg, NeuroFlowColors.DelegateText)
            PriorityCircle(s.tasksByPriority[Priority.LOW] ?: 0, "Low", Color(0xFFFFF9C4), Color(0xFFF9A825))
        }
    }
}

@Composable
private fun EstimatedTimeCard(s: AnalyticsEngine.AnalyticsSummary) {
    AnalyticsCard("Remaining Work") {
        val h = s.totalRemainingMinutes / 60
        val m = s.totalRemainingMinutes % 60
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text("Estimated remaining:")
            Text(
                if (h > 0) "$h hours $m mins" else "$m mins",
                color = Color(0xFF1565C0), fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun MapeCard(s: AnalyticsEngine.AnalyticsSummary, onReset: () -> Unit) {
    val hasData = s.overallMape != 0f || s.weightedMape != 0f
    var showConfirm by remember { mutableStateOf(false) }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Reset Estimation Data?") },
            text = { Text("This will clear MAPE, SMAPE and actual duration from all completed tasks. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { onReset(); showConfirm = false }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = NeuroFlowColors.DeadlineCard)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Time Estimation Accuracy (MAPE)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(12.dp))
            if (!hasData) {
                Text(
                    "No estimation data yet. Complete tasks with time estimates to see accuracy.",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("Overall MAPE:")
                    val mapeColor = when {
                        s.overallMape < 10f -> NeuroFlowColors.MapeGood
                        s.overallMape < 30f -> NeuroFlowColors.MapeMedium
                        else -> NeuroFlowColors.MapeBad
                    }
                    Text("${String.format("%.1f", s.overallMape)}%", color = mapeColor, fontWeight = FontWeight.Bold)
                }
                Text(AnalyticsEngine.mapeGrade(s.overallMape), fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("Weighted MAPE:", fontSize = 13.sp)
                    Text("${String.format("%.1f", s.weightedMape)}%", color = Color(0xFF1565C0), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text("Estimation Breakdown", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("Underestimated tasks:", fontSize = 13.sp)
                    Text("${String.format("%.1f", s.underestimatedPct)}%", color = NeuroFlowColors.MapeBad, fontSize = 13.sp)
                }
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("Overestimated tasks:", fontSize = 13.sp)
                    Text("${String.format("%.1f", s.overestimatedPct)}%", color = NeuroFlowColors.MapeGood, fontSize = 13.sp)
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { showConfirm = true },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Reset Estimation Data", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun SmapeCard(s: AnalyticsEngine.AnalyticsSummary) {
    val hasData = s.overallSmape != 0f || s.weightedSmape != 0f
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = NeuroFlowColors.ScheduledCard)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Symmetric Estimation (SMAPE)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            if (!hasData) {
                Text(
                    "No SMAPE data yet. Reset clears this along with MAPE.",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("Overall SMAPE:")
                    val smapeColor = when {
                        s.overallSmape < 10f -> NeuroFlowColors.MapeGood
                        s.overallSmape < 30f -> NeuroFlowColors.MapeMedium
                        else -> NeuroFlowColors.MapeBad
                    }
                    Text("${String.format("%.1f", s.overallSmape)}%", color = smapeColor, fontWeight = FontWeight.Bold)
                }
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("Weighted SMAPE:")
                    val wSmapeColor = when {
                        s.weightedSmape < 10f -> NeuroFlowColors.MapeGood
                        s.weightedSmape < 30f -> NeuroFlowColors.MapeMedium
                        else -> NeuroFlowColors.MapeBad
                    }
                    Text("${String.format("%.1f", s.weightedSmape)}%", color = wSmapeColor, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "SMAPE is more balanced for small tasks that significantly overrun.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "To reset estimation data, use the button in the MAPE card above.",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun HabitsCard(s: AnalyticsEngine.AnalyticsSummary) {
    if (s.habitTasksTotal == 0) return
    AnalyticsCard("Habits & Consistency") {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
            StatColumn("${s.habitTasksTotal}", "Habit Tasks")
            StatColumn("${s.habitTasksCompleted}", "Completed")
            StatColumn("${s.longestHabitStreak} 🔥", "Best Streak")
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("Habit completion rate")
            Text("${String.format("%.1f", s.habitCompletionRate)}%", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { (s.habitCompletionRate / 100f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = NeuroFlowColors.Purple
        )
        if (s.activeHabitStreaks.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Text("Active streaks", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            s.activeHabitStreaks.forEach { (title, streak) ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    Arrangement.SpaceBetween,
                    Alignment.CenterVertically
                ) {
                    Text(title, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1)
                    Text("🔥 $streak", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NeuroFlowColors.Purple)
                }
            }
        }
    }
}

@Composable
private fun StreakCard(s: AnalyticsEngine.AnalyticsSummary, identityLabel: String, topGoal: String) {
    AnalyticsCard("Streak & Consistency") {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
            StatColumn("${s.currentStreak} 🔥", "Current Streak")
            StatColumn("${s.longestStreak}", "Longest Streak")
        }
        if (identityLabel.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text("You are a $identityLabel", fontWeight = FontWeight.Medium, color = NeuroFlowColors.Purple)
        }
        if (topGoal.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text("Goal: $topGoal", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PeakHourCard(s: AnalyticsEngine.AnalyticsSummary, peakStart: Int, peakEnd: Int) {
    if (s.peakHourFocusMinutes == 0f && s.offPeakFocusMinutes == 0f) return
    val total = (s.peakHourFocusMinutes + s.offPeakFocusMinutes).coerceAtLeast(1f)
    val peakFraction = s.peakHourFocusMinutes / total
    fun fmt(mins: Float): String {
        val h = (mins / 60).toInt(); val m = (mins % 60).toInt()
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }
    val peakLabel = when {
        peakStart == 0  -> "12 am"
        peakStart < 12  -> "$peakStart am"
        peakStart == 12 -> "12 pm"
        else            -> "${peakStart - 12} pm"
    }
    val peakEndLabel = when {
        peakEnd == 0  -> "12 am"
        peakEnd < 12  -> "$peakEnd am"
        peakEnd == 12 -> "12 pm"
        else          -> "${peakEnd - 12} pm"
    }
    AnalyticsCard("⚡ Peak Hour Productivity ($peakLabel–$peakEndLabel)") {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
            StatColumn(fmt(s.peakHourFocusMinutes), "Peak Focus")
            StatColumn(fmt(s.offPeakFocusMinutes), "Off-Peak Focus")
            StatColumn("${s.peakHourTasksCompleted}", "Tasks in Peak")
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("Peak focus ratio", fontSize = 13.sp)
            Text("${(peakFraction * 100).toInt()}%", fontWeight = FontWeight.Bold, color = NeuroFlowColors.Purple)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { peakFraction.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp)),
            color = NeuroFlowColors.Purple
        )
        Spacer(Modifier.height(6.dp))
        val advice = when {
            peakFraction >= 0.6f -> "Great — you're using your peak hours well."
            peakFraction >= 0.35f -> "Try to schedule more deep work during your peak window."
            else -> "Most focus is happening off-peak. Protect your ${peakLabel}–${peakEndLabel} window."
        }
        Text(advice, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ProcrastinationCard(s: AnalyticsEngine.AnalyticsSummary) {
    AnalyticsCard("Procrastination Radar") {
        s.topProcrastinatedTasks.forEach { task ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(task.title, fontWeight = FontWeight.Medium, maxLines = 1)
                    Text("Deferred ${task.postponeCount}×", fontSize = 12.sp, color = NeuroFlowColors.MapeBad)
                }
            }
        }
    }
}

@Composable
private fun NeuroBoostCard(s: AnalyticsEngine.AnalyticsSummary) {
    val hasData = s.frogTasksTotal > 0 || s.anxietyTasksTotal > 0 ||
        s.publicCommitmentTotal > 0 || s.contextTagBreakdown.isNotEmpty()
    if (!hasData) return

    AnalyticsCard("🧠 Neuro Boost Insights") {
        // Frog tasks
        if (s.frogTasksTotal > 0) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text("🐸 Frog tasks completed", fontSize = 13.sp)
                Text("${s.frogTasksCompleted}/${s.frogTasksTotal}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { if (s.frogTasksTotal > 0) s.frogTasksCompleted.toFloat() / s.frogTasksTotal else 0f },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = NeuroFlowColors.DoFirstText
            )
            Spacer(Modifier.height(10.dp))
        }

        // Anxiety tasks
        if (s.anxietyTasksTotal > 0) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text("😰 Anxiety tasks faced", fontSize = 13.sp)
                Text("${s.anxietyTasksCompleted}/${s.anxietyTasksTotal}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Spacer(Modifier.height(10.dp))
        }

        // Public commitments
        if (s.publicCommitmentTotal > 0) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text("📢 Public commitments kept", fontSize = 13.sp)
                Text("${s.publicCommitmentCompleted}/${s.publicCommitmentTotal}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Spacer(Modifier.height(10.dp))
        }

        // If-Then plan usage
        if (s.ifThenPlanUsageRate > 0f) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text("📌 If-Then plan usage", fontSize = 13.sp)
                Text("${String.format("%.0f", s.ifThenPlanUsageRate)}%", fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    color = NeuroFlowColors.Purple)
            }
            Spacer(Modifier.height(10.dp))
        }

        // Task type distribution
        if (s.taskTypeDistribution.isNotEmpty()) {
            Text("Task Type Distribution", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            val total = s.taskTypeDistribution.values.sum().coerceAtLeast(1)
            s.taskTypeDistribution.entries.sortedByDescending { it.value }.forEach { (type, count) ->
                val label = when (type) {
                    "ANALYTICAL" -> "🧠 Analytical"
                    "CREATIVE"   -> "🎨 Creative"
                    "ADMIN"      -> "📋 Admin"
                    "PHYSICAL"   -> "💪 Physical"
                    else         -> type
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, modifier = Modifier.width(100.dp), fontSize = 12.sp)
                    Box(Modifier.weight(1f).height(16.dp).clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(count.toFloat() / total)
                            .clip(RoundedCornerShape(3.dp)).background(NeuroFlowColors.Purple.copy(alpha = 0.7f)))
                    }
                    Text("$count", modifier = Modifier.width(28.dp), fontSize = 12.sp,
                        textAlign = TextAlign.End, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        // Context tag breakdown
        if (s.contextTagBreakdown.isNotEmpty()) {
            Text("Context Tag Breakdown", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                s.contextTagBreakdown.entries.sortedByDescending { it.value }.take(5).forEach { (tag, count) ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = NeuroFlowColors.Purple.copy(alpha = 0.12f)
                    ) {
                        Text("$tag ($count)", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            fontSize = 12.sp, color = NeuroFlowColors.Purple, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun DynamicPeakCard(
    manualStart: Int, manualEnd: Int,
    detectedStart: Int, detectedEnd: Int,
    effectiveStart: Int, effectiveEnd: Int,
    confidence: Float
) {
    fun fmtHour(h: Int): String {
        val amPm = if (h < 12) "am" else "pm"
        val display = if (h == 0 || h == 12) 12 else h % 12
        return "$display$amPm"
    }
    val pct = (confidence * 100).toInt()
    val shifted = kotlin.math.abs(detectedStart - manualStart) >= 2

    AnalyticsCard("⚡ Dynamic Peak Energy") {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text("Manual setting", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${fmtHour(manualStart)}–${fmtHour(manualEnd)}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text("Detected from sessions", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "${fmtHour(detectedStart)}–${fmtHour(detectedEnd)}",
                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = if (shifted) NeuroFlowColors.Purple else MaterialTheme.colorScheme.onSurface
            )
        }
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text("Effective (scoring uses)", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "${fmtHour(effectiveStart)}–${fmtHour(effectiveEnd)}",
                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = NeuroFlowColors.Purple
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("Detection confidence", fontSize = 12.sp)
            Text("$pct%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NeuroFlowColors.Purple)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { confidence.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = NeuroFlowColors.Purple
        )
        Spacer(Modifier.height(8.dp))
        if (shifted) {
            Text(
                "Your actual peak (${fmtHour(detectedStart)}–${fmtHour(detectedEnd)}) differs from your manual setting. " +
                "Scoring is already blending both. Update your setting in Onboarding to fully align.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                "Your focus sessions confirm your peak window. Scoring is aligned.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Shared composables ────────────────────────────────────────────────────────

@Composable
private fun CommitmentsCard(
    active: List<com.neuroflow.app.data.local.entity.UlyssesContractEntity>,
    archived: List<com.neuroflow.app.data.local.entity.UlyssesContractEntity>
) {
    val wins = archived.count { it.outcome == ContractOutcome.WIN }
    val losses = archived.count { it.outcome == ContractOutcome.LOSS }
    val sdf = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())

    AnalyticsCard("⚔️ Ulysses Contracts") {
        if (active.isNotEmpty()) {
            Text("Active", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            active.forEach { contract ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(contract.consequence, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1)
                    Text("Due ${sdf.format(java.util.Date(contract.deadlineAt))}", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        if (archived.isNotEmpty()) {
            Text("Results", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                StatColumn("$wins ✅", "Wins")
                StatColumn("$losses ❌", "Losses")
            }
        }
        if (active.isEmpty() && archived.isEmpty()) {
            Text("No contracts yet.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AnalyticsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun StatColumn(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PriorityCircle(count: Int, label: String, bgColor: Color, textColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(56.dp).clip(CircleShape).background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Text("$count", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = textColor)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 12.sp)
    }
}

