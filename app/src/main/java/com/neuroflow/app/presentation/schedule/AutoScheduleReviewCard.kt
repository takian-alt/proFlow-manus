package com.neuroflow.app.presentation.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neuroflow.app.data.local.entity.AutoScheduleTelemetryEntity
import com.neuroflow.app.data.local.entity.TaskEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AutoScheduleReviewCard(
    proposals: List<AutoScheduleTelemetryEntity>,
    tasks: List<TaskEntity>,
    onApproveAll: () -> Unit,
    onApprove: (AutoScheduleTelemetryEntity) -> Unit,
    onReject: (AutoScheduleTelemetryEntity) -> Unit
) {
    val taskById = tasks.associateBy { it.id }
    val dateFormat = SimpleDateFormat("EEE, d MMM · h:mm a", Locale.getDefault())

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Review autoschedule (${proposals.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Nothing is added to your calendar until you approve it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))

            proposals.take(8).forEachIndexed { index, proposal ->
                val task = taskById[proposal.taskId]
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = task?.title ?: "Task no longer available",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    val start = proposal.selectedSlotDate?.let { date ->
                        proposal.selectedSlotTime?.let { time -> date + time }
                    }
                    Text(
                        text = start?.let { dateFormat.format(Date(it)) } ?: "No proposed time",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    if (proposal.assignmentReason.isNotBlank()) {
                        Text(
                            text = proposal.assignmentReason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { onReject(proposal) }) {
                            Text("Reject")
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Button(onClick = { onApprove(proposal) }) {
                            Text("Approve")
                        }
                    }
                    if (index < proposals.take(8).lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }

            if (proposals.size > 8) {
                Text(
                    text = "+ ${proposals.size - 8} more proposals",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Button(
                onClick = onApproveAll,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Approve all")
            }
        }
    }
}
