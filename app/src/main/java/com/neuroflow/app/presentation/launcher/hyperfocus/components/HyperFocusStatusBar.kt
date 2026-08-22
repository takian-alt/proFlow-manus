package com.neuroflow.app.presentation.launcher.hyperfocus.components

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neuroflow.app.domain.model.HyperFocusState
import com.neuroflow.app.presentation.launcher.hyperfocus.HyperFocusProgress
import com.neuroflow.app.presentation.launcher.hyperfocus.data.HyperFocusPreferences
import kotlinx.coroutines.delay

@Composable
fun HyperFocusStatusBar(
    prefs: HyperFocusPreferences,
    progress: HyperFocusProgress,
    unlockSecondsRemaining: Long?,
    onRewardsClick: () -> Unit,
    onPlanningClick: () -> Unit
) {
    if (!prefs.isActive) {
        Spacer(Modifier.height(0.dp))
        return
    }

    val context = LocalContext.current

    // Poll accessibility state every 3 seconds while session is active
    var accessibilityOk by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            accessibilityOk = com.neuroflow.app.presentation.launcher.hyperfocus.util.AccessibilityUtil
                .isAppBlockingServiceEnabled(context)
            delay(3_000L)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Warning banner — shown when accessibility is off
        if (!accessibilityOk) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFB71C1C))
                    .clickable {
                        context.startActivity(
                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "⚠️ Blocking disabled — tap to fix",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Fix →",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Normal status bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when {
                unlockSecondsRemaining != null -> {
                    val minutes = unlockSecondsRemaining / 60
                    val seconds = unlockSecondsRemaining % 60
                    Text(
                        text = "🔓 UNLOCKED — ${minutes}m ${seconds}s remaining",
                        color = Color(0xFF4CAF50),
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                prefs.state == HyperFocusState.FULL_REWARD_PENDING -> {
                    Text(
                        text = "🎉 All done! Add 3 tasks for tomorrow to claim your reward →",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.clickable { onPlanningClick() }
                    )
                }

                prefs.state == HyperFocusState.FULLY_UNLOCKED -> {
                    Text(
                        text = "🔓 Apps unlocked — great work today!",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF4CAF50)
                    )
                }

                else -> {
                    Text(
                        text = "🔒 FOCUS",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(Modifier.width(4.dp))
                    LinearProgressIndicator(
                        progress = { progress.fraction },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Rewards →",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.clickable { onRewardsClick() }
                    )
                }
            }
        }
    }
}
