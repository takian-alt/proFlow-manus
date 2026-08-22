package com.neuroflow.app.presentation.launcher.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neuroflow.app.domain.engine.AnalyticsEngine
import com.neuroflow.app.domain.model.Quadrant
import com.neuroflow.app.presentation.launcher.LauncherViewModel
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Left page — scrollable blocks: Subliminal, Quick Note, WOOP.
 * Each block can be toggled via the settings sheet (⚙ icon top-right).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeftPage(
    viewModel: LauncherViewModel,
    modifier: Modifier = Modifier
) {
    val blocks by viewModel.leftPageBlocks.collectAsStateWithLifecycle()
    val userPrefs by viewModel.userPreferences.collectAsStateWithLifecycle()
    val topTaskWoop by viewModel.topTaskWoopEntity.collectAsStateWithLifecycle()
    val top3Distracted by viewModel.top3DistractingApps.collectAsStateWithLifecycle()
    val distractionLoading by viewModel.distractionLoading.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }

    // Load on first composition and reload on every resume.
    // The ViewModel checks blockEnabled internally, so we don't need to gate it here.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        viewModel.loadTop3DistractingApps()
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadTop3DistractingApps()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Soft gray background
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF2F3F5))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Page header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Focus Space",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = { showSettings = true }) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Left page settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Subliminal messages block
            if (blocks["subliminal"] == true) {
                val affirmations = userPrefs?.affirmations ?: emptyList()
                SubliminalBlock(affirmations = affirmations)
            }

            // Quick note block
            if (blocks["quick_note"] == true) {
                val note = userPrefs?.leftPageQuickNote ?: ""
                QuickNoteBlock(
                    note = note,
                    onSave = { viewModel.saveQuickNote(it) }
                )
            }

            // WOOP block
            if (blocks["woop"] == true) {
                WoopBlock(woop = topTaskWoop)
            }

            // Distraction Top 3 block
            if (blocks["distraction_top3"] == true) {
                DistractionTop3Block(
                    tasks = top3Distracted,
                    isLoading = distractionLoading,
                    onRefresh = { viewModel.loadTop3DistractingApps() },
                    onReset = { viewModel.resetTop3DistractingApps() }
                )
            }
        }

        // Settings sheet
        if (showSettings) {
            LeftPageSettingsSheet(
                blocks = blocks,
                onToggle = { id, visible -> viewModel.setLeftPageBlockVisible(id, visible) },
                onDismiss = { showSettings = false }
            )
        }
    }
}

// ── Subliminal Block ────────────────────────────────────────────────────────

@Composable
private fun SubliminalBlock(affirmations: List<String>) {
    val messages = affirmations.ifEmpty {
        listOf(
            "You are capable of achieving your goals.",
            "Every small step moves you forward.",
            "Focus on what matters most today.",
            "You have the strength to overcome challenges.",
            "Progress, not perfection."
        )
    }
    var index by remember { mutableIntStateOf(0) }
    // rememberUpdatedState so the loop always sees the latest messages without restarting
    val currentMessages by rememberUpdatedState(messages)

    LaunchedEffect(Unit) {
        while (true) {
            delay(3 * 60 * 1000L)
            index = (index + 1) % currentMessages.size
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "✨ Affirmation",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF6B7280)
            )
            AnimatedContent(
                targetState = index,
                transitionSpec = { fadeIn(tween(800)) togetherWith fadeOut(tween(800)) },
                label = "affirmation"
            ) { i ->
                Text(
                    text = currentMessages[i % currentMessages.size],
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF1F2937),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// ── Quick Note Block ────────────────────────────────────────────────────────

@Composable
private fun QuickNoteBlock(note: String, onSave: (String) -> Unit) {
    var text by remember(note) { mutableStateOf(note) }
    var editing by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "📝 Quick Note",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF6B7280)
                )
                if (editing) {
                    TextButton(
                        onClick = {
                            onSave(text)
                            editing = false
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text("Save", style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    TextButton(
                        onClick = { editing = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text("Edit", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (editing) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Write something...") },
                    minLines = 3,
                    maxLines = 6,
                    textStyle = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text(
                    text = text.ifBlank { "Tap Edit to add a note..." },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (text.isBlank()) Color(0xFFD1D5DB) else Color(0xFF374151),
                    minLines = 2
                )
            }
        }
    }
}

// ── WOOP Block ──────────────────────────────────────────────────────────────

@Composable
private fun WoopBlock(woop: com.neuroflow.app.data.local.entity.WoopEntity?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "🎯 WOOP",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF6B7280)
            )
            if (woop == null || (woop.wish.isBlank() && woop.outcome.isBlank())) {
                Text(
                    "No WOOP set for your current task.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFD1D5DB)
                )
            } else {
                WoopRow("Wish", woop.wish)
                WoopRow("Outcome", woop.outcome)
                if (woop.obstacle.isNotBlank()) WoopRow("Obstacle", woop.obstacle)
                if (woop.plan.isNotBlank()) WoopRow("Plan", woop.plan)
            }
        }
    }
}

@Composable
private fun WoopRow(label: String, value: String) {
    if (value.isBlank()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Distraction Top 3 Block ─────────────────────────────────────────────────

@Composable
private fun DistractionTop3Block(
    tasks: List<com.neuroflow.app.domain.engine.DistractionEngine.AppDistractionResult>,
    isLoading: Boolean = false,
    onRefresh: () -> Unit = {},
    onReset: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember {
        mutableStateOf(com.neuroflow.app.domain.engine.DistractionEngine.hasUsagePermission(context))
    }
    // Re-check permission on resume — fires when user returns from Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = com.neuroflow.app.domain.engine.DistractionEngine.hasUsagePermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "📵 Most Distracting Apps",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF6B7280)
                )
                if (hasPermission) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF9CA3AF)
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Reset button — clears data back to empty state
                            if (tasks.isNotEmpty()) {
                                IconButton(
                                    onClick = onReset,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Clear,
                                        contentDescription = "Reset",
                                        tint = Color(0xFF9CA3AF),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            // Refresh button
                            IconButton(
                                onClick = onRefresh,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "Refresh",
                                    tint = Color(0xFF9CA3AF),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            when {
                !hasPermission -> {
                    Text(
                        "Enable Usage Access so NeuroFlow can detect which apps interrupt your focus most.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF6B7280)
                    )
                    Button(
                        onClick = {
                            com.neuroflow.app.domain.engine.DistractionEngine
                                .openUsagePermissionSettings(context)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6366F1)
                        )
                    ) {
                        Text(
                            "Grant Access",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                isLoading && tasks.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF6366F1)
                        )
                    }
                }
                tasks.isEmpty() -> {
                    Text(
                        "No data yet — start a focus session to begin tracking.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFD1D5DB)
                    )
                }
                else -> {
                    tasks.forEachIndexed { index, app ->
                        DistractionAppRow(rank = index + 1, app = app)
                        if (index < tasks.lastIndex) {
                            HorizontalDivider(color = Color(0xFFF3F4F6), thickness = 1.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DistractionAppRow(
    rank: Int,
    app: com.neuroflow.app.domain.engine.DistractionEngine.AppDistractionResult
) {
    val barColor = when {
        app.score >= 75f -> Color(0xFFEF4444)
        app.score >= 50f -> Color(0xFFF97316)
        app.score >= 25f -> Color(0xFFEAB308)
        else             -> Color(0xFF22C55E)
    }
    val animatedFraction by animateFloatAsState(
        targetValue = (app.score / 100f).coerceIn(0f, 1f),
        animationSpec = tween(600),
        label = "distraction_bar_$rank"
    )
    val minutes = (app.totalDistractedMs / 60_000L).toInt()
    val timeLabel = if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Rank badge
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(barColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "$rank",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = barColor
            )
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = app.appLabel,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF1F2937),
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "$timeLabel · ${app.openCount}×",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF9CA3AF)
                )
            }
            // Score bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFFF3F4F6))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedFraction)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(barColor)
                )
            }
        }
    }
}

// ── Left Page Settings Sheet ────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LeftPageSettingsSheet(
    blocks: Map<String, Boolean>,
    onToggle: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "Focus Space Blocks",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            listOf(
                "subliminal" to "✨ Affirmations",
                "quick_note" to "📝 Quick Note",
                "woop" to "🎯 WOOP",
                "distraction_top3" to "📵 Most Distracting Apps"
            ).forEach { (id, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = blocks[id] == true,
                        onCheckedChange = { onToggle(id, it) }
                    )
                }
            }
        }
    }
}

/**
 * Right page — Quick Stats panel.
 * Loads real data from AnalyticsEngine on each visit.
 * Swipe right from the main page to reach it.
 */
@Composable
fun RightPage(
    viewModel: LauncherViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var summary by remember { mutableStateOf<AnalyticsEngine.AnalyticsSummary?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // Reload every time this composable enters composition (page visited)
    LaunchedEffect(Unit) {
        isLoading = true
        summary = viewModel.loadAnalyticsSummary()
        isLoading = false
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (isLoading || summary == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Surface
        }

        val s = summary!!

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Text(
                text = "Quick Stats",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Today card
            TodayCard(s)

            // Focus time card
            FocusCard(s)

            // Streak card
            StreakCard(s)

            // Quadrant breakdown
            QuadrantCard(s)

            // 7-day trend bar chart
            TrendCard(s)

            // Open full analytics button
            OutlinedButton(
                onClick = {
                    val intent = android.content.Intent(
                        context, com.neuroflow.app.MainActivity::class.java
                    ).apply {
                        action = "com.procus.ACTION_OPEN_ANALYTICS"
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Full Analytics")
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TodayCard(s: AnalyticsEngine.AnalyticsSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Today",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatChip(
                    value = "${s.completedToday}",
                    label = "Done",
                    color = MaterialTheme.colorScheme.primary
                )
                StatChip(
                    value = "${s.remainingTasks}",
                    label = "Left",
                    color = MaterialTheme.colorScheme.secondary
                )
                StatChip(
                    value = "${s.completionRate.roundToInt()}%",
                    label = "Rate",
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

@Composable
private fun FocusCard(s: AnalyticsEngine.AnalyticsSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Focus Time",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = formatMinutes(s.focusMinutesToday),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "today",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatMinutes(s.avgSessionMinutes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "avg session",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (s.mostFocusedTaskTitle != null) {
                Text(
                    "Most focused: ${s.mostFocusedTaskTitle}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun StreakCard(s: AnalyticsEngine.AnalyticsSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "🔥 Current Streak",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${s.currentStreak} days",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "Best",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${s.longestStreak}d",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun QuadrantCard(s: AnalyticsEngine.AnalyticsSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Tasks by Quadrant",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            val total = s.tasksByQuadrant.values.sum().takeIf { it > 0 } ?: 1
            listOf(
                Triple("Do First", Quadrant.DO_FIRST, MaterialTheme.colorScheme.error),
                Triple("Schedule", Quadrant.SCHEDULE, MaterialTheme.colorScheme.primary),
                Triple("Delegate", Quadrant.DELEGATE, MaterialTheme.colorScheme.secondary),
                Triple("Eliminate", Quadrant.ELIMINATE, MaterialTheme.colorScheme.outline)
            ).forEach { (label, quadrant, color) ->
                val count = s.tasksByQuadrant[quadrant] ?: 0
                val fraction = count.toFloat() / total
                QuadrantBar(label = label, count = count, fraction = fraction, color = color)
            }
        }
    }
}

@Composable
private fun QuadrantBar(label: String, count: Int, fraction: Float, color: Color) {
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(600),
        label = "bar"
    )
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("$count", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedFraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        }
    }
}

@Composable
private fun TrendCard(s: AnalyticsEngine.AnalyticsSummary) {
    val trend = s.sevenDayTrend
    if (trend.isEmpty()) return

    val maxVal = trend.maxOfOrNull { it.second }?.takeIf { it > 0f } ?: 1f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "7-Day Focus",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                trend.forEach { (day, minutes) ->
                    val fraction = (minutes / maxVal).coerceIn(0f, 1f)
                    val animatedFraction by animateFloatAsState(
                        targetValue = fraction,
                        animationSpec = tween(600),
                        label = "trend_$day"
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    ) {
                        Box(
                            modifier = Modifier
                                .width(16.dp)
                                .fillMaxHeight(animatedFraction.coerceAtLeast(0.04f))
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(
                                    if (fraction > 0f) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                )
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            day,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatChip(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
    }
}

private fun formatMinutes(minutes: Float): String {
    val m = minutes.roundToInt()
    return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
}
