package com.neuroflow.app.presentation.logtime

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neuroflow.app.data.local.entity.TaskEntity
import com.neuroflow.app.presentation.common.ManualTimeLogSheet
import com.neuroflow.app.presentation.matrix.MatrixViewModel

/**
 * Standalone screen accessible from the drawer.
 * Lists all active tasks; tapping one opens [ManualTimeLogSheet] to log time
 * without completing the task.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogTimeScreen(
    onNavigateBack: () -> Unit,
    viewModel: MatrixViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTask by remember { mutableStateOf<TaskEntity?>(null) }
    var loggedTaskIds by remember { mutableStateOf(emptySet<String>()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Log Time") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (uiState.allActiveTasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No active tasks to log time for.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(uiState.allActiveTasks, key = { it.id }) { task ->
                    val justLogged = task.id in loggedTaskIds
                    ListItem(
                        headlineContent = {
                            Text(task.title, fontWeight = FontWeight.Medium)
                        },
                        supportingContent = {
                            val est = task.estimatedDurationMinutes
                            val hint = if (est > 0) "Est. ${est}m" else "No estimate"
                            Text(if (justLogged) "✓ Time logged" else hint)
                        },
                        trailingContent = {
                            TextButton(onClick = { selectedTask = task }) {
                                Text(if (justLogged) "Log more" else "Log")
                            }
                        },
                        modifier = Modifier.clickable { selectedTask = task }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    selectedTask?.let { task ->
        ManualTimeLogSheet(
            taskTitle = task.title,
            prefillMinutes = task.estimatedDurationMinutes,
            onConfirm = { minutes ->
                viewModel.logTimeOnly(task, minutes)
                loggedTaskIds = loggedTaskIds + task.id
                selectedTask = null
            },
            onSkip = { selectedTask = null }
        )
    }
}
