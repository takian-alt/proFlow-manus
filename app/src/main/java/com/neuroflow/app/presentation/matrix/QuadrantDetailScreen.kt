package com.neuroflow.app.presentation.matrix

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neuroflow.app.domain.engine.TaskScoringEngine
import com.neuroflow.app.domain.model.Quadrant
import com.neuroflow.app.presentation.common.NewTaskSheet
import com.neuroflow.app.presentation.common.TaskRow
import com.neuroflow.app.presentation.common.getQuadrantLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuadrantDetailScreen(
    quadrantName: String,
    onNavigateBack: () -> Unit,
    onNavigateToFocus: (String) -> Unit,
    viewModel: MatrixViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val quadrant = Quadrant.valueOf(quadrantName)
    // Sort tasks by score so weight changes are reflected in the list order
    val tasks = remember(uiState.tasksByQuadrant, uiState.preferences) {
        val raw = uiState.tasksByQuadrant[quadrant] ?: emptyList()
        TaskScoringEngine.sortedByScore(raw, uiState.preferences)
    }
    var showNewTaskSheet by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<com.neuroflow.app.data.local.entity.TaskEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(getQuadrantLabel(quadrant)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showNewTaskSheet = true },
                icon = { Icon(Icons.Filled.Add, "Add Task") },
                text = { Text("Add Task") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { innerPadding ->
        if (tasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = emptyStateMessage(quadrant),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(32.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(tasks, key = { it.id }) { task ->
                    TaskRow(
                        task = task,
                        onTaskClick = { onNavigateToFocus(task.id) },
                        onCompleteClick = { viewModel.completeTask(task) },
                        onEditClick = { editingTask = task }
                    )
                }
            }
        }
    }

    if (showNewTaskSheet) {
        NewTaskSheet(
            onDismiss = { showNewTaskSheet = false },
            onSave = { task ->
                viewModel.insertTask(task.copy(quadrant = quadrant))
                showNewTaskSheet = false
            },
            prefilledQuadrant = quadrant,
            availableTasks = uiState.allActiveTasks
        )
    }

    editingTask?.let { task ->
        NewTaskSheet(
            onDismiss = { editingTask = null },
            onSave = { updated ->
                viewModel.updateTask(updated)
                editingTask = null
            },
            editTask = task,
            availableTasks = uiState.allActiveTasks
        )
    }
}

private fun emptyStateMessage(quadrant: Quadrant): String = when (quadrant) {
    Quadrant.DO_FIRST  -> "🎯 No urgent & important tasks!\nYou're on top of things."
    Quadrant.SCHEDULE  -> "📅 Nothing scheduled yet.\nPlan your important tasks here."
    Quadrant.DELEGATE  -> "🤝 No tasks to delegate.\nFocus on what matters most to you."
    Quadrant.ELIMINATE -> "✨ Nothing to eliminate!\nYou're staying focused."
}
