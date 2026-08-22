package com.neuroflow.app.presentation.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neuroflow.app.presentation.common.theme.NeuroFlowColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppGuideScreen(onNavigateBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("How proFlow Works") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back",
                            tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Hero
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NeuroFlowColors.Purple.copy(alpha = 0.1f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("⚡", fontSize = 40.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Start here: quick setup, then one repeatable daily loop.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            GuideSection(
                emoji = "🚀",
                title = "Start in 3 minutes",
                color = NeuroFlowColors.Purple.copy(alpha = 0.08f),
                textColor = NeuroFlowColors.Purple,
                summary = "Do only these steps first.",
                steps = listOf(
                    "Add 3-5 tasks only. Keep titles clear and specific.",
                    "Set a deadline on at least one task that truly matters today.",
                    "Mark one hard-but-important task as 🐸 Frog.",
                    "Open Focus Mode and run one 25-minute sprint.",
                    "Ignore everything else until that sprint is done."
                )
            )

            GuideSection(
                emoji = "🔁",
                title = "Daily loop (repeat this)",
                color = MaterialTheme.colorScheme.surfaceVariant,
                textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                summary = "A simple sequence for every day.",
                steps = listOf(
                    "Morning: check Matrix and pick the top task in DO FIRST.",
                    "Block a time slot for your top task.",
                    "Run one Focus sprint before checking anything else.",
                    "After each sprint: mark done, or split and continue.",
                    "Evening: schedule tomorrow's first task."
                )
            )

            GuideSection(
                emoji = "➕",
                title = "Add better tasks in 30 seconds",
                color = MaterialTheme.colorScheme.surface,
                textColor = MaterialTheme.colorScheme.onSurface,
                summary = "Only fill fields that improve decisions.",
                steps = listOf(
                    "Title: write the next visible action, not a vague project name.",
                    "Impact (0-100): higher impact = stronger priority.",
                    "Effort (0-100): low effort helps quick wins; high effort is best in peak hours.",
                    "Deadline: even a rough date improves ranking quality.",
                    "Optional but powerful: mark one 🐸 Frog for momentum."
                )
            )

            GuideSection(
                emoji = "🗂",
                title = "Matrix - choose what to do now",
                color = NeuroFlowColors.DoFirstBg,
                textColor = NeuroFlowColors.DoFirstText,
                summary = "Use this to decide what to do now.",
                steps = listOf(
                    "DO FIRST — urgent and important. These need your attention today.",
                    "SCHEDULE — important but not urgent. Plan time for these.",
                    "DELEGATE — urgent but low impact. Handle quickly or hand off.",
                    "ELIMINATE — neither urgent nor important. Let them go.",
                    "Tap any task to open it in Focus Mode. Tap a quadrant header to see all tasks in it.",
                    "The ✦ badge shows the highest-scored task — that's your recommended next action."
                )
            )

            GuideSection(
                emoji = "⚡",
                title = "Priority Score — how tasks are ranked",
                color = NeuroFlowColors.Purple.copy(alpha = 0.1f),
                textColor = NeuroFlowColors.Purple,
                summary = "Trust the rank, then verify with one tap.",
                steps = listOf(
                    "The score combines 20+ signals: deadline pressure, your energy level, time of day, effort, and more.",
                    "It updates every 30 seconds — so a task due in 2 hours will climb as the clock ticks.",
                    "In Focus Mode, tap 'Why this score?' to see exactly what's driving the number.",
                    "Rule of thumb: pick from your top 3 scored tasks and begin immediately."
                )
            )

            GuideSection(
                emoji = "🎯",
                title = "Focus Mode - finish one thing",
                color = NeuroFlowColors.ScheduleBg,
                textColor = NeuroFlowColors.ScheduleText,
                summary = "This is where progress actually happens.",
                steps = listOf(
                    "Tap any task to enter Focus Mode. You'll see its score, urgency, and your plan.",
                    "Hit Start to begin time tracking. Pause if you need a break — the timer saves automatically.",
                    "Use the 🍅 Pomodoro timer for structured 25-minute sprints.",
                    "Skip moves to the next highest-scored task. It won't cycle back to tasks you've already skipped.",
                    "Hit DONE when finished — you'll earn XP based on the task's impact score."
                )
            )

            GuideSection(
                emoji = "📅",
                title = "Time Blocking - protect deep work",
                color = NeuroFlowColors.ScheduledCard,
                textColor = Color(0xFF1565C0),
                summary = "Turn intent into a realistic day plan.",
                steps = listOf(
                    "Tap any hour slot to assign an existing task or create a new one right there.",
                    "Use the + button to add a task without a specific slot.",
                    "🔒 Lock a task's schedule in the task editor to pin it to that time — it won't be moved.",
                    "Locked tasks get a scoring boost when their slot is approaching, so they surface at the right moment.",
                    "Navigate between days with the arrows at the top."
                )
            )

            GuideSection(
                emoji = "📊",
                title = "Analytics — understand your patterns",
                color = NeuroFlowColors.DeadlineCard,
                textColor = NeuroFlowColors.DoFirstText,
                summary = "Review once a day, then adjust tomorrow.",
                steps = listOf(
                    "XP & Points shows the impact you've delivered — higher-impact tasks earn more.",
                    "7-Day Trend shows your focus time per day so you can spot your best days.",
                    "MAPE tracks how accurately you estimate duration — lower is better.",
                    "Procrastination Radar surfaces tasks you keep deferring. Worth a look.",
                    "If one metric is weak, change one behavior tomorrow. Keep it simple."
                )
            )

            GuideSection(
                emoji = "🧠",
                title = "When you feel stuck",
                color = MaterialTheme.colorScheme.surface,
                textColor = MaterialTheme.colorScheme.onSurface,
                summary = "Use this reset sequence.",
                steps = listOf(
                    "Eisenhower Matrix — the 4-quadrant framework used by presidents and CEOs.",
                    "Temporal Motivation Theory — urgency increases exponentially as deadlines approach.",
                    "Eat the Frog (Brian Tracy) — do your hardest task first to build momentum.",
                    "Zeigarnik Effect — unfinished tasks nag at you. Completing them feels disproportionately good.",
                    "Implementation Intentions — 'if-then' plans double task completion rates (Gollwitzer, 1999).",
                    "Circadian Rhythm Research — analytical work peaks in the morning, creative work in late morning/afternoon.",
                    "WOOP (Oettingen) — mental contrasting with implementation intentions outperforms positive visualization.",
                    "Self-Determination Theory — identity-based motivation outlasts goal-based motivation.",
                    "You don't need to know any of this — just use the app and it works in the background.",
                    "Open Matrix and pick the highest scored task in DO FIRST.",
                    "If it feels too big, split it into the smallest next action.",
                    "Start a 10-minute timer in Focus Mode. Just begin.",
                    "If still blocked, switch to a low-effort quick win and return after momentum builds.",
                    "At end of day, schedule tomorrow's first task so morning starts clean."
                )
            )

            Spacer(Modifier.height(16.dp))

            // Footer tip
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = NeuroFlowColors.Purple.copy(alpha = 0.08f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("💡", fontSize = 24.sp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Do less, but do it daily. Consistency beats intensity.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NeuroFlowColors.Purple
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun GuideSection(
    emoji: String,
    title: String,
    color: Color,
    textColor: Color,
    summary: String,
    steps: List<String>
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column {
            // Header — always visible, tap to expand
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(textColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(emoji, fontSize = 20.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = textColor)
                    Text(summary, fontSize = 13.sp, color = textColor.copy(alpha = 0.75f))
                }
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = textColor.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }

            // Expandable steps
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(color = textColor.copy(alpha = 0.15f))
                    Spacer(Modifier.height(4.dp))
                    steps.forEachIndexed { index, step ->
                        Row(verticalAlignment = Alignment.Top) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 3.dp)
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(textColor.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "${index + 1}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                step,
                                fontSize = 14.sp,
                                color = textColor.copy(alpha = 0.9f),
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

