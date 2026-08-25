package com.neuroflow.app.presentation.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neuroflow.app.data.local.entity.SchedulePlanVersionEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SchedulePlanHistoryCard(
    versions: List<SchedulePlanVersionEntity>,
    onRestore: (SchedulePlanVersionEntity) -> Unit
) {
    if (versions.size < 2) return
    val format = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Plan history", style = MaterialTheme.typography.titleMedium)
            Text(
                "Restore an earlier plan without overwriting locked or manually scheduled tasks.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            versions.drop(1).take(3).forEach { version ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "${format.format(Date(version.createdAtMillis))} · ${version.taskCount} tasks",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(onClick = { onRestore(version) }) { Text("Restore") }
                }
            }
        }
    }
}
