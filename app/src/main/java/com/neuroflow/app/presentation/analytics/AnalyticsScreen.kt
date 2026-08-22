package com.neuroflow.app.presentation.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Brush
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
import com.neuroflow.app.domain.engine.SleepPressureDetector
import com.neuroflow.app.domain.model.Priority
import com.neuroflow.app.domain.model.Quadrant
import com.neuroflow.app.domain.repository.EnergyScoreRepository
import com.neuroflow.app.presentation.common.EnergyInsight
import com.neuroflow.app.presentation.common.theme.NeuroFlowColors
import java.util.Calendar

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
            uiState.energy?.let { energy ->
                EnergyPulseCard(energy)
                MorningCalibrationCard(
                    profile = energy.effectivePeakProfile,
                    manualOverrideEnabled = uiState.preferences.manualPeakProfileEnabled,
                    tuneSleep = uiState.preferences.morningTuneSleepWeight,
                    tuneWake = uiState.preferences.morningTuneWakeWeight,
                    tuneBehavior = uiState.preferences.morningTuneBehaviorWeight,
                    tuneBase = uiState.preferences.morningTuneBaseWeight,
                    abstentionTriggerCount = uiState.preferences.peakConfidenceAbstentionTriggerCount,
                    abstentionRecoveryCount = uiState.preferences.peakConfidenceAbstentionRecoveryCount,
                    abstentionReason = uiState.preferences.peakConfidenceAbstentionReason,
                    abstentionReasonFreezeCount = uiState.preferences.peakConfidenceAbstentionReasonFreezeCount,
                    abstentionReasonLowSamplesCount = uiState.preferences.peakConfidenceAbstentionReasonLowSamplesCount,
                    abstentionReasonLowCoverageCount = uiState.preferences.peakConfidenceAbstentionReasonLowCoverageCount,
                    abstentionReasonWakeVarianceCount = uiState.preferences.peakConfidenceAbstentionReasonWakeVarianceCount,
                    abstentionReasonDivergenceCount = uiState.preferences.peakConfidenceAbstentionReasonDivergenceCount,
                    abstentionReasonOtherCount = uiState.preferences.peakConfidenceAbstentionReasonOtherCount,
                    abstentionRateTrend7d = uiState.abstentionRateTrend7d
                )
                InterruptionTelemetryCard(
                    pauseResumeCount = uiState.interruptionPauseResumeCount,
                    appSwitchCount = uiState.interruptionAppSwitchCount,
                    burstCount = uiState.interruptionBurstCount,
                    trend7d = uiState.interruptionTrend7d,
                    ratePerHourTrend7d = uiState.interruptionRatePerHourTrend7d
                )
            }
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
            RecurrenceAndScheduleCard(summary)
            StreakCard(summary, uiState.preferences.identityLabel, uiState.preferences.topGoal)
            val useQuizPeak = uiState.preferences.quizPeakEnabled &&
                uiState.preferences.effectivePeakStart >= 0 &&
                uiState.preferences.effectivePeakEnd >= 0
            // Use effective (blended) peak if available, otherwise fall back to manual.
            val displayPeakStart = if (useQuizPeak)
                uiState.preferences.effectivePeakStart else uiState.preferences.peakEnergyStart
            val displayPeakEnd = if (useQuizPeak)
                uiState.preferences.effectivePeakEnd else uiState.preferences.peakEnergyEnd
            PeakHourCard(summary, displayPeakStart, displayPeakEnd)

            // Peak info insight
            val detectedStart = uiState.preferences.detectedPeakStart
            val detectedEnd = uiState.preferences.detectedPeakEnd
            val detectedPeakMinuteOfDay = uiState.preferences.detectedPeakMinuteOfDay
            val effectivePeakMinuteOfDay = if (useQuizPeak && uiState.preferences.effectivePeakMinuteOfDay >= 0) {
                uiState.preferences.effectivePeakMinuteOfDay
            } else {
                displayPeakStart * 60
            }
            val confidence = uiState.preferences.peakDetectionConfidence
            val hasDetectedPeak =
                detectedStart >= 0 &&
                    detectedPeakMinuteOfDay in 0 until (24 * 60) &&
                    confidence > 0f
            DynamicPeakCard(
                chronotype = uiState.preferences.quizChronotype ?: uiState.preferences.manualChronotype,
                manualStart = uiState.preferences.peakEnergyStart,
                manualEnd = uiState.preferences.peakEnergyEnd,
                detectedStart = detectedStart,
                detectedEnd = detectedEnd,
                detectedPeakMinuteOfDay = detectedPeakMinuteOfDay,
                effectiveStart = displayPeakStart,
                effectiveEnd = displayPeakEnd,
                effectivePeakMinuteOfDay = effectivePeakMinuteOfDay,
                quizPeakEnabled = uiState.preferences.quizPeakEnabled,
                confidence = confidence,
                hasDetectedPeak = hasDetectedPeak,
                effectiveProfile = uiState.energy?.effectivePeakProfile
            )

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
private fun EnergyPulseCard(energy: EnergyScoreRepository.EnergyUiModel) {
    val fatigueColor = when (energy.fatigueZone) {
        SleepPressureDetector.FatigueZone.RESTED -> Color(0xFF43A047)
        SleepPressureDetector.FatigueZone.MODERATE -> Color(0xFFF9A825)
        SleepPressureDetector.FatigueZone.HIGH -> Color(0xFFF57C00)
        SleepPressureDetector.FatigueZone.CRITICAL -> Color(0xFFE53935)
    }
    val peakDecreasedPct = if (energy.peakValue > 0) {
        ((energy.peakDrop * 100f) / energy.peakValue.toFloat()).coerceIn(0f, 100f)
    } else {
        0f
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            NeuroFlowColors.Purple.copy(alpha = 0.16f),
                            Color(0xFF1565C0).copy(alpha = 0.14f)
                        )
                    )
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Energy Score", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "${energy.availableEnergy}/100",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = NeuroFlowColors.Purple
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Adjusted Raw", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        String.format("%.1f", energy.rawEnergy),
                        fontWeight = FontWeight.Bold,
                        color = if (energy.rawEnergy >= 0f) Color(0xFF2E7D32) else Color(0xFFC62828)
                    )
                }
            }

            LinearProgressIndicator(
                progress = { (energy.availableEnergy / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = NeuroFlowColors.Purple,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                EnergyMetricChip("Peak Score", String.format("%.1f", energy.peakScore), Color(0xFF1565C0))
                EnergyMetricChip("Fatigue Penalty", String.format("%.1f", energy.fatiguePenalty), fatigueColor)
                EnergyMetricChip("Sleep Pressure", "${energy.sleepPressurePoints}", fatigueColor)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                EnergyMetricChip("Baseline", String.format("%.1f", energy.baselineRawEnergy), Color(0xFF1565C0))
                EnergyMetricChip(
                    "Moment Δ",
                    String.format("%+.1f", energy.momentAdjustment),
                    if (energy.momentAdjustment >= 0f) Color(0xFF2E7D32) else Color(0xFFC62828)
                )
                EnergyMetricChip(
                    "Moment Conf",
                    "${(energy.momentConfidence * 100).toInt()}%",
                    NeuroFlowColors.Purple
                )
            }

            val weightedMoment = energy.momentAdjustment * energy.momentConfidence
            Text(
                "Formula: baseline = peak score - fatigue penalty. adjusted = baseline + (moment Δ × moment conf).",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Peak score = circadian capacity. Fatigue penalty = sleep-pressure drag. Moment Δ = short-term context boost/drag.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Current moment contribution: ${String.format("%+.1f", weightedMoment)}",
                fontSize = 11.sp,
                color = if (weightedMoment >= 0f) Color(0xFF2E7D32) else Color(0xFFC62828)
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text("Peak now", fontSize = 13.sp)
                Text(
                    "${energy.currentPeakValue}/${energy.peakValue}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text("Peak decreased", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "${energy.peakDrop} (${peakDecreasedPct.toInt()}%)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text("Since peak", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${formatMinutes(energy.minutesSincePeak)}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text("Until peak reset", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${formatMinutes(energy.minutesUntilPeakReset)}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("Fatigue", fontSize = 13.sp)
                Text(
                    "${energy.fatiguePercent}% (${SleepPressureDetector.fatigueZoneLabel(energy.fatigueZone)})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = fatigueColor
                )
            }
            LinearProgressIndicator(
                progress = { (energy.fatiguePercent / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = fatigueColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "Circadian ${(energy.circadianFactor * 100).toInt()}% • Reservoir ${(energy.reservoirFactor * 100).toInt()}% • Confidence ${(energy.confidenceFactor * 100).toInt()}%",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val freshness = "${EnergyInsight.ageLabel(energy.overallFreshnessAgeMillis)} (${EnergyInsight.freshnessLabel(energy.overallFreshnessAgeMillis)})"
            val stability = EnergyInsight.stabilityScore(
                momentConfidence = energy.momentConfidence,
                peakConfidence = energy.confidenceFactor,
                freshnessAgeMillis = energy.overallFreshnessAgeMillis
            )
            Text(
                "Freshness $freshness • Stability ${stability}% • ${EnergyInsight.backtestSummary(energy.effectivePeakProfile)}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                EnergyInsight.confidenceTierRationale(
                    momentConfidence = energy.momentConfidence,
                    peakConfidence = energy.confidenceFactor,
                    freshnessAgeMillis = energy.overallFreshnessAgeMillis
                ),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Why energy changed: ${EnergyInsight.whyEnergyChanged(energy.momentAdjustment, energy.momentSignalSummary)}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "What to do now: ${EnergyInsight.whatToDoNow(energy.availableEnergy, energy.fatigueZone, energy.hasRecentData)}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EnergyMetricChip(label: String, value: String, tint: Color) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = tint.copy(alpha = 0.12f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = tint)
        }
    }
}

private fun formatMinutes(totalMinutes: Int): String {
    val safe = totalMinutes.coerceAtLeast(0)
    val hours = safe / 60
    val minutes = safe % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

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
            StatColumn("${s.sessionsToday}", "Sessions")
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
private fun RecurrenceAndScheduleCard(s: AnalyticsEngine.AnalyticsSummary) {
    if (s.recurringTasksTotal == 0 && s.lockedScheduleTasksTotal == 0) return
    AnalyticsCard("Recurrence & Locked Schedule") {
        if (s.recurringTasksTotal > 0) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text("Recurring tasks completed", fontSize = 13.sp)
                Text(
                    "${s.recurringTasksCompleted}/${s.recurringTasksTotal}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        if (s.lockedScheduleTasksTotal > 0) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text("Locked-schedule tasks completed", fontSize = 13.sp)
                Text(
                    "${s.lockedScheduleTasksCompleted}/${s.lockedScheduleTasksTotal}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        val recurringRate = if (s.recurringTasksTotal > 0)
            s.recurringTasksCompleted.toFloat() / s.recurringTasksTotal else 0f
        val lockedRate = if (s.lockedScheduleTasksTotal > 0)
            s.lockedScheduleTasksCompleted.toFloat() / s.lockedScheduleTasksTotal else 0f

        if (s.recurringTasksTotal > 0 || s.lockedScheduleTasksTotal > 0) {
            Text(
                "Completion reliability",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            if (s.recurringTasksTotal > 0) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("Recurring", fontSize = 12.sp)
                    Text("${(recurringRate * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                LinearProgressIndicator(
                    progress = { recurringRate.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = NeuroFlowColors.Purple
                )
                Spacer(Modifier.height(6.dp))
            }
            if (s.lockedScheduleTasksTotal > 0) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("Locked schedule", fontSize = 12.sp)
                    Text("${(lockedRate * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                LinearProgressIndicator(
                    progress = { lockedRate.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = Color(0xFF1565C0)
                )
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
        Spacer(Modifier.height(4.dp))
        Text(
            "This card tracks your primary peak window. See Peak Info for all peak windows and timings.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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

        // Step-by-step plan usage
        if (s.ifThenPlanUsageRate > 0f) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text("📌 Step-by-step plan usage", fontSize = 13.sp)
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

        if (s.taskTagBreakdown.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Task Tag Breakdown", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                s.taskTagBreakdown.entries.sortedByDescending { it.value }.take(6).forEach { (tag, count) ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF1565C0).copy(alpha = 0.12f)
                    ) {
                        Text(
                            "$tag ($count)",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            fontSize = 12.sp,
                            color = Color(0xFF1565C0),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DynamicPeakCard(
    chronotype: String?,
    manualStart: Int, manualEnd: Int,
    detectedStart: Int, detectedEnd: Int,
    detectedPeakMinuteOfDay: Int,
    effectiveStart: Int, effectiveEnd: Int,
    effectivePeakMinuteOfDay: Int,
    quizPeakEnabled: Boolean,
    confidence: Float,
    hasDetectedPeak: Boolean,
    effectiveProfile: com.neuroflow.app.domain.engine.PeakEnergyEngine.EffectivePeakProfile?
) {
    fun normalizeMinuteOfDay(minuteOfDay: Int): Int {
        val normalized = minuteOfDay % (24 * 60)
        return if (normalized < 0) normalized + (24 * 60) else normalized
    }

    fun resolvePeakMinuteOfDay(minuteOfDay: Int, fallbackHour: Int): Int {
        return if (minuteOfDay in 0 until (24 * 60)) minuteOfDay else normalizeMinuteOfDay(fallbackHour * 60)
    }

    fun fmtHour(h: Int): String {
        val amPm = if (h < 12) "am" else "pm"
        val display = if (h == 0 || h == 12) 12 else h % 12
        return "$display$amPm"
    }

    fun fmtMinuteOfDay(minuteOfDay: Int): String {
        val normalized = normalizeMinuteOfDay(minuteOfDay)
        val hour = normalized / 60
        val minute = normalized % 60
        val amPm = if (hour < 12) "am" else "pm"
        val displayHour = if (hour == 0 || hour == 12) 12 else hour % 12
        return String.format("%d:%02d%s", displayHour, minute, amPm)
    }

    fun minuteIsInsideWindow(minute: Int, startMinute: Int, durationMinutes: Int): Boolean {
        if (durationMinutes <= 0) return false
        val safeMinute = normalizeMinuteOfDay(minute)
        val safeStart = normalizeMinuteOfDay(startMinute)
        val endExclusive = safeStart + durationMinutes
        return if (endExclusive <= 24 * 60) {
            safeMinute in safeStart until endExclusive
        } else {
            safeMinute >= safeStart || safeMinute < (endExclusive % (24 * 60))
        }
    }

    fun minutesUntil(targetMinute: Int, nowMinute: Int): Int {
        val safeTarget = normalizeMinuteOfDay(targetMinute)
        val safeNow = normalizeMinuteOfDay(nowMinute)
        val forward = safeTarget - safeNow
        return if (forward >= 0) forward else forward + (24 * 60)
    }

    fun windowIntensityLabel(amplitude: Float): String {
        return when {
            amplitude >= 0.9f -> "Hard-work"
            amplitude >= 0.7f -> "Deep-work"
            else -> "Light-focus"
        }
    }

    fun windowConfidenceColor(confidenceValue: Float): Color {
        return when {
            confidenceValue >= 0.8f -> Color(0xFF2E7D32)
            confidenceValue >= 0.6f -> Color(0xFF1565C0)
            else -> Color(0xFFC62828)
        }
    }

    data class PeakWindowUi(
        val index: Int,
        val startMinute: Int,
        val endMinute: Int,
        val durationMinutes: Int,
        val amplitude: Float,
        val confidence: Float,
        val isActiveNow: Boolean,
        val minutesUntilStart: Int
    )

    val detectionConfidence = confidence.coerceIn(0f, 1f)
    val detectedPeakMinute = if (hasDetectedPeak) {
        resolvePeakMinuteOfDay(detectedPeakMinuteOfDay, detectedStart)
    } else {
        normalizeMinuteOfDay(manualStart * 60)
    }
    val effectivePeakMinute = resolvePeakMinuteOfDay(effectivePeakMinuteOfDay, effectiveStart)
    val pct = (detectionConfidence * 100).toInt()
    val manualPeakMinute = normalizeMinuteOfDay(manualStart * 60)
    val shifted = hasDetectedPeak && circularMinuteDistance(detectedPeakMinute, manualPeakMinute) >= 120
    val isMorningType = EnergyInsight.isMorningType(chronotype)
    val nowMinuteOfDay = Calendar.getInstance().let {
        it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE)
    }
    val profileWindows = effectiveProfile?.windows?.mapIndexed { index, window ->
        val startMinute = normalizeMinuteOfDay(effectivePeakMinute + window.startMinuteOffset)
        val endMinute = normalizeMinuteOfDay(startMinute + window.durationMinutes)
        val activeNow = minuteIsInsideWindow(
            minute = nowMinuteOfDay,
            startMinute = startMinute,
            durationMinutes = window.durationMinutes
        )
        PeakWindowUi(
            index = index,
            startMinute = startMinute,
            endMinute = endMinute,
            durationMinutes = window.durationMinutes,
            amplitude = window.amplitude,
            confidence = (effectiveProfile?.windowConfidences?.getOrNull(index) ?: detectionConfidence).coerceIn(0f, 1f),
            isActiveNow = activeNow,
            minutesUntilStart = if (activeNow) 0 else minutesUntil(startMinute, nowMinuteOfDay)
        )
    }.orEmpty()
    val activeWindow = profileWindows.firstOrNull { it.isActiveNow }
    val nextWindow = profileWindows.minByOrNull { if (it.isActiveNow) 0 else it.minutesUntilStart }

    AnalyticsCard("⚡ Peak Info") {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text("Manual setting", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${fmtHour(manualStart)}–${fmtHour(manualEnd)}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text("Detected from MEQ quiz", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (hasDetectedPeak) {
                Text(
                    fmtMinuteOfDay(detectedPeakMinute),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (shifted) NeuroFlowColors.Purple else MaterialTheme.colorScheme.onSurface
                )
            } else {
                Text(
                    "Not available yet",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (hasDetectedPeak && detectedEnd >= 0) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text("Detected hour window", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${fmtHour(detectedStart)}–${fmtHour(detectedEnd)}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text("Effective (scoring uses)", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                fmtMinuteOfDay(effectivePeakMinute),
                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = NeuroFlowColors.Purple
            )
        }
        if (profileWindows.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Today peak windows",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            profileWindows.forEach { window ->
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    val intensity = windowIntensityLabel(window.amplitude)
                    val label = "Peak ${window.index + 1} ($intensity)"
                    Text(
                        label,
                        fontSize = 12.sp,
                        color = if (window.isActiveNow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "${fmtMinuteOfDay(window.startMinute)}–${fmtMinuteOfDay(window.endMinute)} (${formatMinutes(window.durationMinutes)})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (window.isActiveNow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
                Row(Modifier.fillMaxWidth(), Arrangement.End) {
                    Text(
                        "${(window.confidence * 100).toInt()}% ${EnergyInsight.windowConfidenceTier(effectiveProfile, window.index)} confidence",
                        fontSize = 11.sp,
                        color = windowConfidenceColor(window.confidence)
                    )
                }
            }
            val peak2Confidence = profileWindows.getOrNull(1)?.confidence
            val peak3Confidence = profileWindows.getOrNull(2)?.confidence
            if (peak2Confidence != null || peak3Confidence != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    buildString {
                        append("Peak 2 confidence ")
                        append(peak2Confidence?.let { "${(it * 100).toInt()}%" } ?: "n/a")
                        append(" • Peak 3 confidence ")
                        append(peak3Confidence?.let { "${(it * 100).toInt()}%" } ?: "n/a")
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(6.dp))
            when {
                activeWindow != null -> {
                    Text(
                        "You are in Peak ${activeWindow.index + 1} now. Peak 1 is usually best for hardest work; later peaks are better for medium or lighter execution.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                nextWindow != null -> {
                    Text(
                        "Next peak: Peak ${nextWindow.index + 1} in ${formatMinutes(nextWindow.minutesUntilStart)} at ${fmtMinuteOfDay(nextWindow.startMinute)}.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (isMorningType) {
            Spacer(Modifier.height(6.dp))
            Text(
                EnergyInsight.profileSummary(effectiveProfile),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                EnergyInsight.adaptiveHint(effectiveProfile),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("Detection confidence", fontSize = 12.sp)
            Text("$pct%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NeuroFlowColors.Purple)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { detectionConfidence },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = NeuroFlowColors.Purple
        )
        Spacer(Modifier.height(8.dp))
        Text(
            EnergyInsight.profileConfidenceLine(effectiveProfile),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val confidenceActions = EnergyInsight.confidenceImprovementActions(effectiveProfile)
        if (confidenceActions.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "How to raise confidence",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            confidenceActions.forEach { action ->
                Text(
                    "• $action",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (!hasDetectedPeak) {
            Text(
                "Detected peak data is not available yet. Peak Info is using your manual peak window and adaptive profile windows when available.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (!quizPeakEnabled) {
            Text(
                "Quiz peak is turned off. Scoring is using your manual peak window.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (shifted) {
            Text(
                "Your quiz-derived peak point (${fmtMinuteOfDay(detectedPeakMinute)}) differs from your manual setting window (${fmtHour(manualStart)}–${fmtHour(manualEnd)}). " +
                "Scoring is already blending both. Update your setting in Onboarding to fully align.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                "Your manual setting aligns with your quiz-derived peak point.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun circularMinuteDistance(a: Int, b: Int): Int {
    val day = 24 * 60
    val aa = ((a % day) + day) % day
    val bb = ((b % day) + day) % day
    val diff = kotlin.math.abs(aa - bb)
    return minOf(diff, day - diff)
}

internal fun abstentionReasonMix(
    freezeCount: Int,
    lowSamplesCount: Int,
    lowCoverageCount: Int,
    wakeVarianceCount: Int,
    divergenceCount: Int,
    otherCount: Int
): List<Pair<String, Int>> {
    return listOf(
        "Freeze safety" to freezeCount.coerceAtLeast(0),
        "Low samples" to lowSamplesCount.coerceAtLeast(0),
        "Low coverage" to lowCoverageCount.coerceAtLeast(0),
        "Wake variance" to wakeVarianceCount.coerceAtLeast(0),
        "High divergence" to divergenceCount.coerceAtLeast(0),
        "Other" to otherCount.coerceAtLeast(0)
    )
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
}

@Composable
private fun MorningCalibrationCard(
    profile: com.neuroflow.app.domain.engine.PeakEnergyEngine.EffectivePeakProfile?,
    manualOverrideEnabled: Boolean,
    tuneSleep: Float,
    tuneWake: Float,
    tuneBehavior: Float,
    tuneBase: Float,
    abstentionTriggerCount: Int,
    abstentionRecoveryCount: Int,
    abstentionReason: String,
    abstentionReasonFreezeCount: Int,
    abstentionReasonLowSamplesCount: Int,
    abstentionReasonLowCoverageCount: Int,
    abstentionReasonWakeVarianceCount: Int,
    abstentionReasonDivergenceCount: Int,
    abstentionReasonOtherCount: Int,
    abstentionRateTrend7d: List<Pair<String, Float>>
) {
    AnalyticsCard("Morning Calibration Diagnostics") {
        if (profile == null) {
            Text("No calibration data yet.", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Text(
                "Complete MEQ and a few focus sessions to unlock adaptive diagnostics.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@AnalyticsCard
        }
        Text(EnergyInsight.profileSummary(profile), fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Text(
            EnergyInsight.profileModeLabel(manualOverrideEnabled = manualOverrideEnabled, profile = profile),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(4.dp))
        Text(EnergyInsight.profileConfidenceLine(profile), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(EnergyInsight.backtestSummary(profile), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Text(
            "Abstention telemetry: triggered ${abstentionTriggerCount.coerceAtLeast(0)}x • recovered ${abstentionRecoveryCount.coerceAtLeast(0)}x",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (profile.confidenceGatedAbstention || abstentionReason.isNotBlank()) {
            Text(
                "Latest abstention reason: ${abstentionReason.ifBlank { "insufficient confidence for personalized prediction" }}",
                fontSize = 11.sp,
                color = if (profile.confidenceGatedAbstention) Color(0xFFC62828) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val reasonBreakdown = abstentionReasonMix(
            freezeCount = abstentionReasonFreezeCount,
            lowSamplesCount = abstentionReasonLowSamplesCount,
            lowCoverageCount = abstentionReasonLowCoverageCount,
            wakeVarianceCount = abstentionReasonWakeVarianceCount,
            divergenceCount = abstentionReasonDivergenceCount,
            otherCount = abstentionReasonOtherCount
        )
        val reasonTotal = reasonBreakdown.sumOf { it.second }
        if (reasonTotal > 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Abstention reason mix",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            reasonBreakdown.take(4).forEach { (label, count) ->
                val pct = ((count.toFloat() / reasonTotal.toFloat()) * 100f).toInt().coerceIn(0, 100)
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("• $label", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$count ($pct%)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        val trend = abstentionRateTrend7d.takeIf { it.size == 7 } ?: emptyList()
        if (trend.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "7-day abstention rate",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            val maxRate = trend.maxOfOrNull { it.second }?.coerceAtLeast(5f) ?: 5f
            Row(
                modifier = Modifier.fillMaxWidth().height(66.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                trend.forEach { (label, rateValue) ->
                    val safeRate = rateValue.coerceIn(0f, 100f)
                    val fraction = (safeRate / maxRate).coerceIn(0f, 1f)
                    val barHeight = (fraction * 44f).dp.coerceAtLeast(2.dp)
                    val barColor = when {
                        safeRate >= 50f -> Color(0xFFC62828)
                        safeRate >= 20f -> Color(0xFFEF6C00)
                        else -> Color(0xFF2E7D32)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .width(14.dp)
                                .height(barHeight)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(barColor.copy(alpha = 0.78f))
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            label,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            val latestRate = trend.lastOrNull()?.second?.coerceIn(0f, 100f) ?: 0f
            val avgRate = trend.map { it.second.coerceIn(0f, 100f) }.average().toFloat()
            Spacer(Modifier.height(4.dp))
            Text(
                "Latest ${latestRate.toInt()}% • Avg ${avgRate.toInt()}%",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text("Drift status", fontSize = 12.sp)
            Text(profile.driftStatus, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text("Confidence sleep", fontSize = 12.sp)
            Text("${(profile.confidence.sleepCoverage * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text("Confidence wake", fontSize = 12.sp)
            Text("${(profile.confidence.wakeConsistency * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text("Confidence behavior", fontSize = 12.sp)
            Text("${(profile.confidence.behaviorPerformance * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Sleep = sleep-log coverage and duration quality. Wake = wake-time regularity. Behavior = how task outcomes match predicted slots.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text("Auto-tune weights", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text("Sleep ${(tuneSleep * 100).toInt()}%", fontSize = 12.sp)
            Text("Wake ${(tuneWake * 100).toInt()}%", fontSize = 12.sp)
            Text("Behavior ${(tuneBehavior * 100).toInt()}%", fontSize = 12.sp)
            Text("Base ${(tuneBase * 100).toInt()}%", fontSize = 12.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "These weights control how strongly each signal can move your detected peak profile.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val actions = EnergyInsight.confidenceImprovementActions(profile)
        if (actions.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Confidence boosters", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            actions.forEach { action ->
                Text(
                    "• $action",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun InterruptionTelemetryCard(
    pauseResumeCount: Int,
    appSwitchCount: Int,
    burstCount: Int,
    trend7d: List<Pair<String, Int>>,
    ratePerHourTrend7d: List<Pair<String, Float>>
) {
    var showRatePerHour by remember { mutableStateOf(false) }
    AnalyticsCard("Interruption Telemetry") {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
            StatColumn("$pauseResumeCount", "Pause/Resume")
            StatColumn("$appSwitchCount", "App Switches")
            StatColumn("$burstCount", "Burst Events")
        }
        Spacer(Modifier.height(8.dp))
        val total = (pauseResumeCount + appSwitchCount + burstCount).coerceAtLeast(1)
        val burstRatio = (burstCount.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        Text("Burst share ${(burstRatio * 100).toInt()}%", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LinearProgressIndicator(
            progress = { burstRatio },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = NeuroFlowColors.Purple
        )
        if (trend7d.isNotEmpty() && trend7d.any { it.second > 0 }) {
            Spacer(Modifier.height(10.dp))
            Text("7-day interruption trend", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !showRatePerHour,
                    onClick = { showRatePerHour = false },
                    label = { Text("Count") }
                )
                FilterChip(
                    selected = showRatePerHour,
                    onClick = { showRatePerHour = true },
                    label = { Text("Rate/hr") }
                )
            }
            Spacer(Modifier.height(6.dp))
            val countMax = trend7d.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
            val rateMax = ratePerHourTrend7d.maxOfOrNull { it.second }?.coerceAtLeast(0.1f) ?: 0.1f
            Row(
                modifier = Modifier.fillMaxWidth().height(80.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                trend7d.forEachIndexed { idx, (label, countValue) ->
                    val rateValue = ratePerHourTrend7d.getOrNull(idx)?.second ?: 0f
                    val fraction = if (showRatePerHour) {
                        (rateValue / rateMax).coerceIn(0f, 1f)
                    } else {
                        (countValue.toFloat() / countMax.toFloat()).coerceIn(0f, 1f)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val barH = (fraction * 56f).dp.coerceAtLeast(2.dp)
                        Box(
                            modifier = Modifier
                                .width(18.dp)
                                .height(barH)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(NeuroFlowColors.Purple.copy(alpha = 0.75f))
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            val latestCount = trend7d.lastOrNull()?.second ?: 0
            val latestRate = ratePerHourTrend7d.lastOrNull()?.second ?: 0f
            Text(
                if (showRatePerHour) "Latest: ${String.format("%.1f", latestRate)} interruptions/hr"
                else "Latest: $latestCount interruptions",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (pauseResumeCount == 0 && appSwitchCount == 0 && burstCount == 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                "No interruption telemetry yet. Start focus sessions to populate this card.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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

