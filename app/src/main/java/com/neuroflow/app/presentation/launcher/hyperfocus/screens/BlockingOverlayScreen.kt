package com.neuroflow.app.presentation.launcher.hyperfocus.screens

import android.app.Activity
import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neuroflow.app.MainActivity
import com.neuroflow.app.domain.model.HyperFocusSessionMode
import androidx.navigation.NavController
import com.neuroflow.app.domain.model.RewardTier
import com.neuroflow.app.presentation.launcher.hyperfocus.HyperFocusViewModel
import kotlinx.coroutines.launch
import kotlin.random.Random

@Composable
fun BlockingOverlayScreen(
    blockedPackage: String,
    viewModel: HyperFocusViewModel,
    navController: NavController
) {
    val prefs by viewModel.hyperFocusPrefs.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val activeTasks by viewModel.activeTasks.collectAsState()
    val isUnlockActive by viewModel.isUnlockActive.collectAsState()
    val unlockSecondsRemaining by viewModel.unlockSecondsRemaining.collectAsState()
    val sessionSecondsRemaining by viewModel.sessionSecondsRemaining.collectAsState()

    var showEmergencyDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isTimeBasedSession = prefs.sessionMode == HyperFocusSessionMode.TIME_BASED
    
    val appName = remember(blockedPackage) {
        try {
            val appInfo = context.packageManager.getApplicationInfo(blockedPackage, 0)
            context.packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            blockedPackage
        }
    }

    // Check if this is a messenger app
    val isMessengerApp = remember(blockedPackage) {
        val messengerPackages = listOf(
            "com.facebook.orca",
            "com.facebook.mlite",
            "org.telegram.messenger",
            "com.whatsapp",
            "com.google.android.apps.messaging",
            "org.thoughtcrime.securesms"
        )
        blockedPackage in messengerPackages
    }

    // Motivational quotes
    val blockedAppQuotes = listOf(
        "Lol. Don't say, (you lost your motivation).",
        "Your goals are more important than this distraction.",
        "This moment defines your discipline. Stay strong.",
        "Your focus is worth more than this.",
        "Remember why you activated HyperFocus. You've got this.",
        "Every second without this app is a win.",
        "Your future self will thank you for this choice.",
        "Distraction is temporary. Your goals are permanent.",
        "You're stronger than this urge. Prove it to yourself.",
        "This app doesn't serve your mission today.",
        "One moment of resistance = lasting progress.",
        "Your attention is your most valuable asset.",
        "Control the urge, control your destiny.",
        "Success requires saying 'no' to distractions.",
        "The harder it is to resist, the more you need to.",
        "Your mind is more powerful than this impulse.",
        "Is this really worth it? Do you really want to become dumber?",
        "Look! who we have here!! Lamao,lol.. I thought ,you have finally became smarter. Ig, you are still stupid"
    )

    val selectedQuote = remember(blockedPackage) {
        blockedAppQuotes.getOrNull(Random.nextInt(maxOf(1, blockedAppQuotes.size))) 
            ?: "Your focus is worth more than this."
    }

    // Animation state
    val lockScale = remember { Animatable(0.5f) }
    LaunchedEffect(Unit) {
        lockScale.animateTo(1f, animationSpec = tween(600, easing = LinearEasing))
    }

    val messengerGlow = remember { Animatable(0.5f) }
    LaunchedEffect(isMessengerApp) {
        if (isMessengerApp) {
            while (true) {
                messengerGlow.animateTo(1f, animationSpec = tween(1000))
                messengerGlow.animateTo(0.5f, animationSpec = tween(1000))
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0A0A0A)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ============ LOCK ICON & HEADING ============
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .scale(lockScale.value)
                        .background(
                            color = if (isMessengerApp) Color(0xFF2D1F1F) else Color(0xFF1A1A2E),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .border(
                            width = 2.dp,
                            color = if (isMessengerApp) Color(0xFFFF6B6B) else Color(0xFF4A90E2),
                            shape = RoundedCornerShape(20.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked",
                        tint = if (isMessengerApp) Color(0xFFFF6B6B) else Color(0xFF4A90E2),
                        modifier = Modifier.size(48.dp)
                    )
                }

                Text(
                    text = "HYPER FOCUS ACTIVE",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = appName,
                    color = if (isMessengerApp) Color(0xFFFF6B6B) else Color(0xFF4A90E2),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }

            // ============ LARGE MOTIVATIONAL QUOTE (MAIN FOCUS) ============
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 2.dp,
                        color = if (isMessengerApp) Color(0xFFFF6B6B) else Color(0xFF4A90E2),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clip(RoundedCornerShape(16.dp)),
                color = if (isMessengerApp) Color(0xFF2D1F1F) else Color(0xFF1A1A2E),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "\"",
                        color = if (isMessengerApp) Color(0xFFFF6B6B) else Color(0xFF4A90E2),
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = selectedQuote,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        lineHeight = 24.sp
                    )
                    Text(
                        text = "\"",
                        color = if (isMessengerApp) Color(0xFFFF6B6B) else Color(0xFF4A90E2),
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // ============ TOP ALERT (IF MESSENGER) ============
            if (isMessengerApp) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.5.dp,
                            color = Color(0xFFFF6B6B).copy(alpha = messengerGlow.value),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clip(RoundedCornerShape(12.dp)),
                    color = Color(0xFF2D1F1F)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.NotificationsActive,
                            contentDescription = "Messenger Alert",
                            tint = Color(0xFFFF6B6B),
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Messenger Quota Active",
                                color = Color(0xFFFF6B6B),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "High distraction alert - stay focused!",
                                color = Color(0xFFFF9999),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // ============ TAMPER DETECTION WARNING ============
            if (prefs.isTamperDetected) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.5.dp,
                            color = Color(0xFFB71C1C),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clip(RoundedCornerShape(12.dp)),
                    color = Color(0xFF3E1B1B)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Tamper Warning",
                            tint = Color(0xFFB71C1C),
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "⚠️ Tamper detected",
                                color = Color(0xFFB71C1C),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = prefs.tamperReason ?: "Unusual activity detected",
                                color = Color(0xFFD32F2F),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            if (isTimeBasedSession) {
                // ============ SESSION TIMER SECTION ============
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF1A1A2E),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Focus Timer",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = formatSeconds(sessionSecondsRemaining),
                            color = if (isMessengerApp) Color(0xFFFF6B6B) else Color(0xFF4A90E2),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Apps stay blocked until this timer ends.",
                            color = Color(0xFFB0B0B0),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                // ============ PROGRESS SECTION ============
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF1A1A2E),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Today's Progress",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )

                        LinearProgressIndicator(
                            progress = { progress.fraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = if (isMessengerApp) Color(0xFFFF6B6B) else Color(0xFF4A90E2),
                            trackColor = Color(0xFF2D2D44),
                            strokeCap = StrokeCap.Round
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "${progress.completedSinceActivation} / ${progress.totalTarget}",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "tasks completed",
                                    color = Color(0xFFB0B0B0),
                                    fontSize = 12.sp
                                )
                            }

                            if (progress.currentTier != RewardTier.FULL) {
                                val tiers = RewardTier.entries
                                val nextIndex = tiers.indexOf(progress.currentTier) + 1
                                val nextTier = if (nextIndex < tiers.size) tiers[nextIndex] else null
                                val unlockLabel = when {
                                    nextTier == null || nextTier == RewardTier.FULL -> "full unlock"
                                    else -> "${nextTier.unlockMinutes}min unlock"
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "${progress.tasksToNextTier}",
                                        color = Color(0xFF4A90E2),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "more → $unlockLabel",
                                        color = Color(0xFFB0B0B0),
                                        fontSize = 12.sp
                                    )
                                }
                            } else {
                                Surface(
                                    modifier = Modifier
                                        .background(
                                            color = Color(0xFF1B5E20),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .padding(8.dp),
                                    color = Color(0xFF1B5E20)
                                ) {
                                    Text(
                                        text = "✓ Full Reward",
                                        color = Color(0xFF69F0AE),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ============ UNLOCK STATUS (IF ACTIVE) ============
            if (isUnlockActive) {
                val timeLabel = unlockSecondsRemaining?.let { secs ->
                    "${secs / 60}m ${secs % 60}s"
                } ?: ""
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 2.dp,
                            color = Color(0xFF69F0AE),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clip(RoundedCornerShape(12.dp)),
                    color = Color(0xFF1B5E20)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "🔓 APP UNLOCKED",
                            color = Color(0xFF69F0AE),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "$timeLabel remaining",
                            color = Color(0xFF81C784),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { navController.navigate("launch_app") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1B5E20),
                        contentColor = Color(0xFF69F0AE)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Open $appName →", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { (context as? Activity)?.finish() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Back to launcher", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            } else {
                // ============ ACTION BUTTONS (LOCKED) ============
                Button(
                    onClick = {
                        val isPlanningPending =
                            prefs.state == com.neuroflow.app.domain.model.HyperFocusState.FULL_REWARD_PENDING

                        if (isPlanningPending) {
                            navController.navigate("planning_prompt")
                            return@Button
                        }

                        val preferredTaskId = prefs.lockedTaskIds.firstOrNull { lockedId ->
                            activeTasks.any { it.id == lockedId }
                        } ?: activeTasks.firstOrNull()?.id

                        val focusIntent = Intent(context, MainActivity::class.java).apply {
                            if (!preferredTaskId.isNullOrBlank()) {
                                action = "com.procus.ACTION_OPEN_FOCUS"
                                putExtra("task_id", preferredTaskId)
                            }
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                        context.startActivity(focusIntent)
                        (context as? Activity)?.finish()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isMessengerApp) Color(0xFFFF6B6B) else Color(0xFF4A90E2),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        if (prefs.state == com.neuroflow.app.domain.model.HyperFocusState.FULL_REWARD_PENDING)
                            "Plan tomorrow's tasks →"
                        else
                            "Go back to tasks",
                        fontWeight = FontWeight.Bold
                    )
                }

                if (prefs.currentTier > RewardTier.NONE &&
                    prefs.state != com.neuroflow.app.domain.model.HyperFocusState.FULL_REWARD_PENDING
                ) {
                    OutlinedButton(
                        onClick = { navController.navigate("code_entry") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Enter unlock code", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (prefs.sessionMode == HyperFocusSessionMode.TASK_BASED) {
                    if (!prefs.emergencyUsed) {
                        TextButton(
                            onClick = { showEmergencyDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Emergency Access (10 mins)", color = Color(0xFFB0B0B0))
                        }
                    } else {
                        Text(
                            text = "Emergency access already used for this session",
                            color = Color(0xFFB0B0B0),
                            fontSize = 12.sp,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    if (showEmergencyDialog) {
        AlertDialog(
            onDismissRequest = { showEmergencyDialog = false },
            containerColor = Color(0xFF1A1A2E),
            titleContentColor = Color.White,
            textContentColor = Color(0xFFB0B0B0),
            title = {
                Text("Emergency Access", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "This will grant you 10 minutes of access to $appName.\n\n" +
                    "⚠️ WARNING: If you use this emergency bypass, you will lose your next intermediate rewards and must complete ALL daily tasks to earn any further unlocks."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEmergencyDialog = false
                        viewModel.activateEmergencyBypass()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B6B))
                ) {
                    Text("Use Emergency", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmergencyDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            }
        )
    }
}

private fun formatSeconds(seconds: Long?): String {
    val safe = (seconds ?: 0L).coerceAtLeast(0L)
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    val secs = safe % 60
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format("%02d:%02d", minutes, secs)
    }
}
