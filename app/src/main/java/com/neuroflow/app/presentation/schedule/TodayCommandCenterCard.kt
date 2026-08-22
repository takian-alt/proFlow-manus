package com.neuroflow.app.presentation.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neuroflow.app.data.local.entity.TaskEntity
import java.util.Calendar

@Composable
fun TodayCommandCenterCard(
    tasksForDay: List<TaskEntity>,
    allActiveTasks: List<TaskEntity>,
    workDayStart: Int,
    workDayEnd: Int,
    energyNow: Int?,
    onStartFocus: (String) -> Unit,
    onReschedule: (TaskEntity) -> Unit
) {
    val now = System.currentTimeMillis()
    val nextTask = tasksForDay
        .filter { it.scheduledDate != null && it.scheduledTime != null }
        .filter { it.scheduledDate!! + it.scheduledTime!! >= now }
        .minByOrNull { it.scheduledDate!! + it.scheduledTime!! }
    val overdueCount = allActiveTasks.count { task ->
        val deadline = task.deadlineDate?.plus(task.deadlineTime ?: 0L)
        task.status.name == "ACTIVE" && deadline != null && deadline < now
    }
    val scheduledMinutes = tasksForDay.sumOf { it.estimatedDurationMinutes.coerceAtLeast(1) }
    val availableMinutes = ((workDayEnd - workDayStart).coerceAtLeast(1) * 60)
    val riskRatio = (scheduledMinutes.toFloat() / availableMinutes).coerceIn(0f, 1.5f)
    val riskLabel = when {
        riskRatio > 1f -> "Over capacity by ${scheduledMinutes - availableMinutes} min"
        riskRatio >= 0.85f -> "Tight day — keep buffer"
        else -> "Capacity looks healthy"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Today command center", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Next task", style = MaterialTheme.typography.labelMedium)
                    Text(
                        nextTask?.title ?: "No upcoming task",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (nextTask != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(onClick = { onStartFocus(nextTask.id) }) { Text("Focus") }
                        Button(onClick = { onReschedule(nextTask) }) { Text("Move") }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Energy now: ${energyNow?.let { "$it/100" } ?: "Unavailable"}", style = MaterialTheme.typography.bodySmall)
            Text("Schedule risk: $riskLabel", style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(
                progress = { (riskRatio / 1.0f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                color = when {
                    riskRatio > 1f -> MaterialTheme.colorScheme.error
                    riskRatio >= 0.85f -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.primary
                }
            )
            Text(
                text = "${scheduledMinutes} min planned · ${availableMinutes} min capacity · $overdueCount overdue",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
