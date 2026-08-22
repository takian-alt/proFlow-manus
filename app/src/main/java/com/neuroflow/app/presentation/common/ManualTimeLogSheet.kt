package com.neuroflow.app.presentation.common

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Bottom sheet for manually logging time spent on a task.
 *
 * Shows hour + minute number pickers. Pre-fills from [prefillMinutes] when provided
 * (e.g. the task's estimated duration). Calls [onConfirm] with the total minutes
 * entered, or [onSkip] if the user wants to complete without logging time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualTimeLogSheet(
    taskTitle: String,
    prefillMinutes: Int = 0,
    onConfirm: (Float) -> Unit,
    onSkip: () -> Unit
) {
    var hours by remember { mutableIntStateOf(prefillMinutes / 60) }
    var minutes by remember { mutableIntStateOf(prefillMinutes % 60) }

    val totalMinutes = hours * 60 + minutes

    ModalBottomSheet(onDismissRequest = onSkip) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("⏱ Log Time Spent", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                taskTitle,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
            Spacer(Modifier.height(24.dp))

            // Hour + minute pickers side by side
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Hours
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Hours", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalIconButton(
                            onClick = { if (hours > 0) hours-- },
                            modifier = Modifier.size(40.dp)
                        ) { Text("−", fontSize = 18.sp) }
                        Text(
                            "$hours",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        FilledTonalIconButton(
                            onClick = { if (hours < 23) hours++ },
                            modifier = Modifier.size(40.dp)
                        ) { Text("+", fontSize = 18.sp) }
                    }
                }

                Spacer(Modifier.width(24.dp))
                Text(":", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(24.dp))

                // Minutes
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Minutes", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalIconButton(
                            onClick = { if (minutes > 0) minutes -= 5 else if (hours > 0) { hours--; minutes = 55 } },
                            modifier = Modifier.size(40.dp)
                        ) { Text("−", fontSize = 18.sp) }
                        Text(
                            minutes.toString().padStart(2, '0'),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        FilledTonalIconButton(
                            onClick = { if (minutes < 55) minutes += 5 else { minutes = 0; if (hours < 23) hours++ } },
                            modifier = Modifier.size(40.dp)
                        ) { Text("+", fontSize = 18.sp) }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            if (totalMinutes > 0) {
                Text(
                    "= $totalMinutes minutes total",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(28.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onSkip,
                    modifier = Modifier.weight(1f)
                ) { Text("Skip") }
                Button(
                    onClick = { onConfirm(totalMinutes.toFloat()) },
                    modifier = Modifier.weight(1f),
                    enabled = totalMinutes > 0
                ) { Text("Log & Done") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
