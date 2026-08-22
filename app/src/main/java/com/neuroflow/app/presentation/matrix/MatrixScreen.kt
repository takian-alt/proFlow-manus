package com.neuroflow.app.presentation.matrix

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neuroflow.app.domain.engine.SleepPressureDetector
import com.neuroflow.app.domain.model.Quadrant
import com.neuroflow.app.domain.repository.EnergyScoreRepository
import com.neuroflow.app.presentation.common.EnergyInsight
import com.neuroflow.app.presentation.common.NewTaskSheet
import com.neuroflow.app.presentation.common.getQuadrantBgColor
import com.neuroflow.app.presentation.common.getQuadrantLabel
import com.neuroflow.app.presentation.common.getQuadrantTextColor
import com.neuroflow.app.presentation.common.theme.NeuroFlowColors
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatrixScreen(
    onOpenDrawer: () -> Unit,
    onNavigateToQuadrant: (String) -> Unit,
    onNavigateToFocus: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: MatrixViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showNewTaskSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("proFlow") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Filled.Menu, "Menu")
                    }
                },
                actions = {
                    // Play button — Focus mode with top task
                    IconButton(
                        onClick = {
                            uiState.topScoredTaskId?.let { onNavigateToFocus(it) }
                        },
                        enabled = uiState.topScoredTaskId != null
                    ) {
                        Icon(Icons.Filled.PlayArrow, "Focus Mode")
                    }
                    IconButton(onClick = { /* search */ }) {
                        Icon(Icons.Filled.Search, "Search")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Filled.Settings, "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showNewTaskSheet = true },
                icon = { Icon(Icons.Filled.Add, "New Task") },
                text = { Text("New Task") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(8.dp)
        ) {
            uiState.energy?.let { energy ->
                MatrixEnergyStrip(energy)
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 2x2 quadrant grid
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuadrantCard(
                    quadrant = Quadrant.DO_FIRST,
                    count = uiState.quadrantCounts[Quadrant.DO_FIRST] ?: 0,
                    onClick = { onNavigateToQuadrant("DO_FIRST") },
                    modifier = Modifier.weight(1f)
                )
                QuadrantCard(
                    quadrant = Quadrant.SCHEDULE,
                    count = uiState.quadrantCounts[Quadrant.SCHEDULE] ?: 0,
                    onClick = { onNavigateToQuadrant("SCHEDULE") },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuadrantCard(
                    quadrant = Quadrant.DELEGATE,
                    count = uiState.quadrantCounts[Quadrant.DELEGATE] ?: 0,
                    onClick = { onNavigateToQuadrant("DELEGATE") },
                    modifier = Modifier.weight(1f)
                )
                QuadrantCard(
                    quadrant = Quadrant.ELIMINATE,
                    count = uiState.quadrantCounts[Quadrant.ELIMINATE] ?: 0,
                    onClick = { onNavigateToQuadrant("ELIMINATE") },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    if (showNewTaskSheet) {
        NewTaskSheet(
            onDismiss = { showNewTaskSheet = false },
            onSave = { task ->
                viewModel.insertTask(task)
                showNewTaskSheet = false
            },
            availableTasks = uiState.allActiveTasks
        )
    }
}

@Composable
private fun MatrixEnergyStrip(energy: EnergyScoreRepository.EnergyUiModel) {
    fun normalizeMinuteOfDay(minuteOfDay: Int): Int {
        val normalized = minuteOfDay % (24 * 60)
        return if (normalized < 0) normalized + (24 * 60) else normalized
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

    fun fmtMinuteOfDay(minuteOfDay: Int): String {
        val normalized = normalizeMinuteOfDay(minuteOfDay)
        val hour = normalized / 60
        val minute = normalized % 60
        val amPm = if (hour < 12) "am" else "pm"
        val displayHour = if (hour == 0 || hour == 12) 12 else hour % 12
        return String.format("%d:%02d%s", displayHour, minute, amPm)
    }

    fun formatMinutes(totalMinutes: Int): String {
        val safe = totalMinutes.coerceAtLeast(0)
        val hours = safe / 60
        val minutes = safe % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    val fatigueColor = when (energy.fatigueZone) {
        SleepPressureDetector.FatigueZone.RESTED -> Color(0xFF43A047)
        SleepPressureDetector.FatigueZone.MODERATE -> Color(0xFFF9A825)
        SleepPressureDetector.FatigueZone.HIGH -> Color(0xFFF57C00)
        SleepPressureDetector.FatigueZone.CRITICAL -> Color(0xFFE53935)
    }

    data class PeakWindowUi(
        val index: Int,
        val startMinute: Int,
        val endMinute: Int,
        val isActiveNow: Boolean,
        val minutesUntilStart: Int
    )

    val nowMinuteOfDay = Calendar.getInstance().let {
        it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE)
    }
    val profileWindows = energy.effectivePeakProfile?.let { profile ->
        profile.windows.mapIndexed { index, window ->
            val startMinute = normalizeMinuteOfDay(profile.anchorMinuteOfDay + window.startMinuteOffset)
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
                isActiveNow = activeNow,
                minutesUntilStart = if (activeNow) 0 else minutesUntil(startMinute, nowMinuteOfDay)
            )
        }
    }.orEmpty()
    val activeWindow = profileWindows.firstOrNull { it.isActiveNow }
    val nextWindow = if (activeWindow != null) {
        profileWindows.filterNot { it.isActiveNow }.minByOrNull { it.minutesUntilStart }
    } else {
        profileWindows.minByOrNull { it.minutesUntilStart }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .background(
                    brush = Brush.linearGradient(
                        listOf(
                            NeuroFlowColors.Purple.copy(alpha = 0.14f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                        )
                    )
                )
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Energy Score", fontWeight = FontWeight.Bold)
                Text(
                    "${energy.availableEnergy}/100",
                    fontWeight = FontWeight.ExtraBold,
                    color = NeuroFlowColors.Purple
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { (energy.availableEnergy / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = NeuroFlowColors.Purple,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Peak ${energy.currentPeakValue}/${energy.peakValue}", fontSize = 12.sp)
                Text("Drop ${energy.peakDrop}", fontSize = 12.sp)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Sleep pressure ${energy.sleepPressurePoints}", fontSize = 12.sp)
                Text(
                    "Fatigue ${energy.fatiguePercent}%",
                    fontSize = 12.sp,
                    color = fatigueColor,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Baseline ${String.format("%.1f", energy.baselineRawEnergy)}", fontSize = 12.sp)
                Text(
                    "Moment ${String.format("%+.1f", energy.momentAdjustment)}",
                    fontSize = 12.sp,
                    color = if (energy.momentAdjustment >= 0f) Color(0xFF2E7D32) else Color(0xFFC62828)
                )
            }
            if (activeWindow != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Now in Peak ${activeWindow.index + 1} (${fmtMinuteOfDay(activeWindow.startMinute)}–${fmtMinuteOfDay(activeWindow.endMinute)})",
                    fontSize = 12.sp,
                    color = Color(0xFF2E7D32),
                    fontWeight = FontWeight.Bold
                )
            } else if (nextWindow != null) {
                Text(
                    text = "Next Peak ${nextWindow.index + 1} in ${formatMinutes(nextWindow.minutesUntilStart)} (${fmtMinuteOfDay(nextWindow.startMinute)})",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val freshness = "${EnergyInsight.ageLabel(energy.overallFreshnessAgeMillis)} (${EnergyInsight.freshnessLabel(energy.overallFreshnessAgeMillis)})"
            val stability = EnergyInsight.stabilityScore(
                momentConfidence = energy.momentConfidence,
                peakConfidence = energy.confidenceFactor,
                freshnessAgeMillis = energy.overallFreshnessAgeMillis
            )
            val peak1Confidence = EnergyInsight.windowConfidencePercent(energy.effectivePeakProfile, 0)
            val peak2Confidence = EnergyInsight.windowConfidencePercent(energy.effectivePeakProfile, 1)
            val peak3Confidence = EnergyInsight.windowConfidencePercent(energy.effectivePeakProfile, 2)
            val confidenceActions = EnergyInsight.confidenceImprovementActions(energy.effectivePeakProfile)
            Text(
                text = "Freshness $freshness • Stability ${stability}%",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = EnergyInsight.backtestSummary(energy.effectivePeakProfile),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Peak confidence P1 ${peak1Confidence}% • P2 ${peak2Confidence}% • P3 ${peak3Confidence}%",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = EnergyInsight.whatToDoNow(
                    availableEnergy = energy.availableEnergy,
                    fatigueZone = energy.fatigueZone,
                    hasRecentData = energy.hasRecentData
                ),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            confidenceActions.take(2).forEach { action ->
                Text(
                    text = "• $action",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun QuadrantCard(
    quadrant: Quadrant,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(16.dp))
            .background(getQuadrantBgColor(quadrant))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = getQuadrantLabel(quadrant),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = getQuadrantTextColor(quadrant),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "$count Tasks",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = getQuadrantTextColor(quadrant)
            )
        }
    }
}
