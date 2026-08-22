package com.neuroflow.app.presentation.common

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.neuroflow.app.data.local.entity.UlyssesContractEntity
import com.neuroflow.app.data.repository.TaskRepository
import com.neuroflow.app.data.repository.UlyssesContractRepository
import com.neuroflow.app.worker.UlyssesEvaluatorWorker
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UlyssesContractSheet(
    taskRepository: TaskRepository,
    contractRepository: UlyssesContractRepository,
    workManager: WorkManager,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var selectedTaskId by remember { mutableStateOf("") }
    var selectedTaskTitle by remember { mutableStateOf("") }
    var consequence by remember { mutableStateOf("") }
    var deadlineMillis by remember { mutableStateOf(System.currentTimeMillis() + 86_400_000L) }
    var showTaskPicker by remember { mutableStateOf(false) }
    val activeTasks by taskRepository.observeActiveTasks().collectAsStateWithLifecycle(initialValue = emptyList())

    val now = System.currentTimeMillis()
    val deadlineIsValid = deadlineMillis > now

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text("⚔️ Ulysses Contract", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(
                "Commit to completing a task by a deadline with a personal consequence.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Task picker
            OutlinedButton(
                onClick = { showTaskPicker = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (selectedTaskTitle.isNotBlank()) selectedTaskTitle else "Select a task...")
            }

            if (showTaskPicker) {
                activeTasks.forEach { task ->
                    TextButton(
                        onClick = {
                            selectedTaskId = task.id
                            selectedTaskTitle = task.title
                            showTaskPicker = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(task.title, maxLines = 1)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Deadline display (simplified — shows current deadline + +1 day / +1 week buttons)
            val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            Text("Deadline: ${sdf.format(Date(deadlineMillis))}", style = MaterialTheme.typography.bodyMedium)
            if (!deadlineIsValid) {
                Text(
                    "Deadline must be in the future.",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { deadlineMillis += 86_400_000L }) { Text("+1 day") }
                OutlinedButton(onClick = { deadlineMillis += 7 * 86_400_000L }) { Text("+1 week") }
                OutlinedButton(onClick = { if (deadlineMillis - 86_400_000L > System.currentTimeMillis()) deadlineMillis -= 86_400_000L }) { Text("-1 day") }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = consequence,
                onValueChange = { consequence = it },
                label = { Text("Consequence if I fail...") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (selectedTaskId.isBlank() || consequence.isBlank() || !deadlineIsValid) return@Button
                        scope.launch {
                            val contract = UlyssesContractEntity(
                                taskId = selectedTaskId,
                                deadlineAt = deadlineMillis,
                                consequence = consequence
                            )
                            contractRepository.insert(contract)
                            val delay = (deadlineMillis - System.currentTimeMillis()).coerceAtLeast(0L)
                            val request = OneTimeWorkRequestBuilder<UlyssesEvaluatorWorker>()
                                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                                .setInputData(workDataOf("contractId" to contract.id))
                                .addTag("ulysses_eval_${contract.id}")
                                .build()
                            workManager.enqueue(request)
                            onDismiss()
                        }
                    },
                    enabled = selectedTaskId.isNotBlank() && consequence.isNotBlank() && deadlineIsValid
                ) {
                    Text("Commit")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
