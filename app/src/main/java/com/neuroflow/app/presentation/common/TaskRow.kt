package com.neuroflow.app.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neuroflow.app.data.local.entity.TaskEntity
import com.neuroflow.app.domain.model.EnergyLevel
import com.neuroflow.app.domain.model.Priority
import com.neuroflow.app.domain.model.Quadrant
import com.neuroflow.app.domain.model.TaskStatus
import com.neuroflow.app.presentation.common.theme.NeuroFlowColors

@Composable
fun TaskRow(
    task: TaskEntity,
    onTaskClick: () -> Unit,
    onCompleteClick: () -> Unit,
    onEditClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable { onTaskClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Completion circle
                IconButton(
                    onClick = onCompleteClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    if (task.status == TaskStatus.COMPLETED) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = "Completed",
                            tint = NeuroFlowColors.ScheduleText,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Icon(
                            Icons.Outlined.Circle,
                            contentDescription = "Incomplete",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    // Title row with frog badge
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (task.isFrog) {
                            Text("🐸", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Medium,
                                textDecoration = if (task.status == TaskStatus.COMPLETED)
                                    TextDecoration.LineThrough else TextDecoration.None
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (task.description.isNotBlank()) {
                        Text(
                            text = task.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    // Tags row
                    if (task.tags.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            task.tags.split(",").filter { it.isNotBlank() }.take(3).forEach { tag ->
                                TagChip(tag.trim())
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(horizontalAlignment = Alignment.End) {
                    PriorityDot(task.priority)
                    if (task.estimatedDurationMinutes > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formatDuration(task.estimatedDurationMinutes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (task.isScheduleLocked) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Icon(Icons.Filled.Lock, "Locked", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(12.dp))
                    }
                    if (onEditClick != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        IconButton(onClick = onEditClick, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.Edit, "Edit", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Neuro boost badges row — shown below the main row
            val badges = buildList {
                if (task.energyLevel == com.neuroflow.app.domain.model.EnergyLevel.HIGH) add("🔴 High energy")
                if (task.energyLevel == com.neuroflow.app.domain.model.EnergyLevel.LOW) add("🟢 Low energy")
                if (task.contextTag.isNotBlank()) add(task.contextTag)
                if (task.isAnxietyTask) add("😰 Anxiety")
                if (task.isPublicCommitment) add("📢 Committed")
                if (task.waitingFor.isNotBlank()) add("⏳ Blocked")
                if (task.goalRiskLevel == 1) add("⚠ At Risk")
                if (task.goalRiskLevel == 2) add("🚨 Critical")
            }
            if (badges.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(start = 40.dp)
                ) {
                    badges.take(4).forEach { badge ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PriorityDot(priority: Priority) {
    val color = when (priority) {
        Priority.HIGH   -> NeuroFlowColors.DoFirstText
        Priority.MEDIUM -> NeuroFlowColors.DelegateText
        Priority.LOW    -> Color(0xFFFDD835)
    }
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
fun TagChip(tag: String) {
    val isDark = com.neuroflow.app.presentation.common.theme.LocalIsDarkTheme.current
    val chipColors = if (isDark) listOf(
        Color(0xFF1A2A3E), Color(0xFF2E1F3E), Color(0xFF1B3A1F),
        Color(0xFF3E3A1A), Color(0xFF3E1F1F), Color(0xFF1A2E2E)
    ) else listOf(
        Color(0xFFE3F2FD), Color(0xFFF3E5F5), Color(0xFFE8F5E9),
        Color(0xFFFFF3E0), Color(0xFFFFEBEE), Color(0xFFE0F7FA)
    )
    val colorIndex = tag.hashCode().let { kotlin.math.abs(it) } % chipColors.size
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = chipColors[colorIndex],
        modifier = Modifier.height(22.dp)
    ) {
        Text(
            text = tag,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

fun formatDuration(minutes: Int): String = when {
    minutes < 60 -> "${minutes}m"
    minutes % 60 == 0 -> "${minutes / 60}h"
    else -> "${minutes / 60}h ${minutes % 60}m"
}

fun formatDurationFloat(minutes: Float): String {
    val totalMinutes = minutes.toInt()
    return when {
        totalMinutes < 60 -> "${String.format("%.1f", minutes)} min"
        else -> {
            val h = totalMinutes / 60
            val m = totalMinutes % 60
            "${h}h ${m}m"
        }
    }
}

@Composable
fun getQuadrantBgColor(quadrant: Quadrant, isDark: Boolean = false): Color {
    val dark = com.neuroflow.app.presentation.common.theme.LocalIsDarkTheme.current
    return when (quadrant) {
        Quadrant.DO_FIRST  -> if (dark) NeuroFlowColors.DoFirstBgDark else NeuroFlowColors.DoFirstBg
        Quadrant.SCHEDULE  -> if (dark) NeuroFlowColors.ScheduleBgDark else NeuroFlowColors.ScheduleBg
        Quadrant.DELEGATE  -> if (dark) NeuroFlowColors.DelegateBgDark else NeuroFlowColors.DelegateBg
        Quadrant.ELIMINATE -> if (dark) NeuroFlowColors.EliminateBgDark else NeuroFlowColors.EliminateBg
    }
}

@Composable
fun getQuadrantTextColor(quadrant: Quadrant, isDark: Boolean = false): Color {
    val dark = com.neuroflow.app.presentation.common.theme.LocalIsDarkTheme.current
    return when (quadrant) {
        Quadrant.DO_FIRST  -> if (dark) NeuroFlowColors.DoFirstTextDark else NeuroFlowColors.DoFirstText
        Quadrant.SCHEDULE  -> if (dark) NeuroFlowColors.ScheduleTextDark else NeuroFlowColors.ScheduleText
        Quadrant.DELEGATE  -> if (dark) NeuroFlowColors.DelegateTextDark else NeuroFlowColors.DelegateText
        Quadrant.ELIMINATE -> if (dark) NeuroFlowColors.EliminateTextDark else NeuroFlowColors.EliminateText
    }
}

fun getQuadrantLabel(quadrant: Quadrant): String = when (quadrant) {
    Quadrant.DO_FIRST  -> "DO FIRST"
    Quadrant.SCHEDULE  -> "SCHEDULE"
    Quadrant.DELEGATE  -> "DELEGATE"
    Quadrant.ELIMINATE -> "ELIMINATE"
}
