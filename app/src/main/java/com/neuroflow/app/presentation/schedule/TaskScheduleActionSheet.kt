package com.neuroflow.app.presentation.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neuroflow.app.data.local.entity.TaskEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskScheduleActionSheet(
    task: TaskEntity,
    onMoveEarlier: () -> Unit,
    onMoveLater: () -> Unit,
    onShorten: () -> Unit,
    onExtend: () -> Unit,
    onToggleLock: () -> Unit,
    onSplit: () -> Unit,
    onConvertToRecurring: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(task.title)
            Button(onClick = onMoveEarlier, modifier = Modifier.fillMaxWidth()) { Text("Move 30 minutes earlier") }
            Button(onClick = onMoveLater, modifier = Modifier.fillMaxWidth()) { Text("Move 30 minutes later") }
            Button(onClick = onShorten, enabled = task.estimatedDurationMinutes > 30, modifier = Modifier.fillMaxWidth()) {
                Text("Shorten by 30 minutes")
            }
            Button(onClick = onExtend, enabled = task.estimatedDurationMinutes < 360, modifier = Modifier.fillMaxWidth()) {
                Text("Extend by 30 minutes")
            }
            Button(onClick = onToggleLock, modifier = Modifier.fillMaxWidth()) {
                Text(if (task.isScheduleLocked) "Unlock schedule" else "Lock schedule")
            }
            Button(
                onClick = onSplit,
                enabled = task.canSplit && task.estimatedDurationMinutes > 90,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Split into linked tasks") }
            Button(
                onClick = onConvertToRecurring,
                enabled = !task.isHabitual,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Convert to daily recurring") }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        }
    }
}
