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
                        "proFlow prioritizes your tasks automatically — so you always know what to work on next.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            GuideSection(
                emoji = "🗂",
                title = "Matrix — your home base",
                color = NeuroFlowColors.DoFirstBg,
                textColor = NeuroFlowColors.DoFirstText,
                summary = "Four quadrants, one clear picture of what matters.",
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
                summary = "Every task gets a live score. Higher = do it sooner.",
                steps = listOf(
                    "The score combines 20+ signals: deadline pressure, your energy level, time of day, effort, and more.",
                    "It updates every 30 seconds — so a task due in 2 hours will climb as the clock ticks.",
                    "In Focus Mode, tap 'Why this score?' to see exactly what's driving the number.",
                    "You don't need to manage the score — just add good task details and let it work."
                )
            )

            GuideSection(
                emoji = "🎯",
                title = "Focus Mode — one task at a time",
                color = NeuroFlowColors.ScheduleBg,
                textColor = NeuroFlowColors.ScheduleText,
                summary = "Deep work, distraction-free.",
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
                title = "Time Blocking — plan your day",
                color = NeuroFlowColors.ScheduledCard,
                textColor = Color(0xFF1565C0),
                summary = "Assign tasks to specific hours so your day has structure.",
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
                summary = "See where your time actually goes.",
                steps = listOf(
                    "XP & Points shows the impact you've delivered — higher-impact tasks earn more.",
                    "7-Day Trend shows your focus time per day so you can spot your best days.",
                    "MAPE tracks how accurately you estimate task duration — aim for under 25%.",
                    "Procrastination Radar surfaces tasks you keep deferring. Worth a look.",
                    "Neuro Boost Insights shows how often you're using the science-backed features like Frog tasks and If-Then plans."
                )
            )

            GuideSection(
                emoji = "➕",
                title = "Adding tasks — the details that matter",
                color = MaterialTheme.colorScheme.surfaceVariant,
                textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                summary = "Better inputs = smarter prioritization.",
                steps = listOf(
                    "Impact (0–100): how much does completing this move the needle? High impact = higher score.",
                    "Effort (0–100): how hard is it? Low-effort tasks get a quick-win boost. High-effort tasks are boosted during your peak hours.",
                    "Energy level: match the task to when you'll have the right energy — HIGH tasks score better during your peak hours.",
                    "🐸 Frog: mark your most-dreaded task. It gets a big boost in the morning so you tackle it first.",
                    "If-Then Plan: 'When I sit down at my desk, I will...' — tasks with a concrete plan score higher and get done more often.",
                    "Deadline: even a rough deadline helps the scoring engine surface the task at the right time."
                )
            )

            GuideSection(
                emoji = "🧠",
                title = "The science behind it",
                color = MaterialTheme.colorScheme.surface,
                textColor = MaterialTheme.colorScheme.onSurface,
                summary = "proFlow is built on real productivity research.",
                steps = listOf(
                    "Eisenhower Matrix — the 4-quadrant framework used by presidents and CEOs.",
                    "Temporal Motivation Theory — urgency increases exponentially as deadlines approach.",
                    "Eat the Frog (Brian Tracy) — do your hardest task first to build momentum.",
                    "Zeigarnik Effect — unfinished tasks nag at you. Completing them feels disproportionately good.",
                    "Implementation Intentions — 'if-then' plans double task completion rates (Gollwitzer, 1999).",
                    "Circadian Rhythm Research — analytical work peaks in the morning, creative work in late morning/afternoon.",
                    "You don't need to know any of this — just use the app and it works in the background."
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
                        "Start simple — add 3–5 tasks, set a deadline on the most urgent one, and tap Focus. The rest figures itself out.",
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

