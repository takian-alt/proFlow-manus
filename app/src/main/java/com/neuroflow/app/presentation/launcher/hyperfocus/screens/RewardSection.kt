package com.neuroflow.app.presentation.launcher.hyperfocus.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neuroflow.app.domain.model.RewardTier
import com.neuroflow.app.presentation.launcher.hyperfocus.HyperFocusViewModel

@Composable
fun RewardSection(viewModel: HyperFocusViewModel, modifier: Modifier = Modifier) {
    val prefs by viewModel.hyperFocusPrefs.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val rewardCounts by viewModel.rewardCounts.collectAsState()
    val claimedCode by viewModel.claimedCodeToShow.collectAsState()
    val claimedTier by viewModel.claimedCodeTier.collectAsState()

    if (!prefs.isActive) return

    Column(
        modifier = modifier
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Rewards",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )

        Text(
            text = "${progress.completedSinceActivation} / ${progress.totalTarget} tasks complete",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(4.dp))

        // One row per tier (skip NONE and FULL for display purposes — FULL shown separately)
        val displayTiers = listOf(
            RewardTier.MICRO  to "2 min unlock",
            RewardTier.PARTIAL to "10 min unlock",
            RewardTier.EARNED  to "30 min unlock",
            RewardTier.FULL    to "Full unlock"
        )

        displayTiers.forEach { (tier, label) ->
            val isEarned = progress.currentTier >= tier && tier != RewardTier.NONE
            val hasUnclaimedCode = (rewardCounts[tier] ?: 0) > 0
            // Hide the FULL tier claim button once planning is pending (code already claimed)
            val isPlanningPending = prefs.state == com.neuroflow.app.domain.model.HyperFocusState.FULL_REWARD_PENDING
            val isAvailable = isEarned && hasUnclaimedCode && !(tier == RewardTier.FULL && isPlanningPending)
            val isPendingThisTier = claimedCode != null && claimedTier == tier

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = when {
                    isPendingThisTier || (isEarned && !hasUnclaimedCode) ->
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    isEarned ->
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    else ->
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                }
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = label,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = if (isEarned)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = when {
                                    !isEarned -> "Not yet earned"
                                    !hasUnclaimedCode -> "Already used"
                                    else -> "Earned — ready to claim"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = { viewModel.claimRewardForTier(tier) },
                            enabled = isAvailable,
                            colors = ButtonDefaults.buttonColors(
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text(if (isPendingThisTier) "Show" else "Claim")
                        }
                    }

                    // Show the claimed code inline under this tier row
                    if (isPendingThisTier && claimedCode != null) {
                        val code = claimedCode ?: return@Column
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Your code:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surface,
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = code,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 28.sp,
                                letterSpacing = 4.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = { viewModel.dismissClaimedCode() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Dismiss")
                        }
                    }
                }
            }
        }
    }
}
