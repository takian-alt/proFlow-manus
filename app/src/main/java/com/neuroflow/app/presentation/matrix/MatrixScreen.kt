package com.neuroflow.app.presentation.matrix

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neuroflow.app.domain.model.Quadrant
import com.neuroflow.app.presentation.common.NewTaskSheet
import com.neuroflow.app.presentation.common.getQuadrantBgColor
import com.neuroflow.app.presentation.common.getQuadrantLabel
import com.neuroflow.app.presentation.common.getQuadrantTextColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatrixScreen(
    onOpenDrawer: () -> Unit,
    onNavigateToQuadrant: (String) -> Unit,
    onNavigateToFocus: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: MatrixViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showNewTaskSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("proFlow") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Filled.Menu, "Menu")
                    }
                },
                actions = {
                    // Play button — Focus mode with top task
                    IconButton(
                        onClick = {
                            uiState.topScoredTaskId?.let { onNavigateToFocus(it) }
                        },
                        enabled = uiState.topScoredTaskId != null
                    ) {
                        Icon(Icons.Filled.PlayArrow, "Focus Mode")
                    }
                    IconButton(onClick = { /* search */ }) {
                        Icon(Icons.Filled.Search, "Search")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Filled.Settings, "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showNewTaskSheet = true },
                icon = { Icon(Icons.Filled.Add, "New Task") },
                text = { Text("New Task") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(8.dp)
        ) {
            // 2x2 quadrant grid
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuadrantCard(
                    quadrant = Quadrant.DO_FIRST,
                    count = uiState.quadrantCounts[Quadrant.DO_FIRST] ?: 0,
                    onClick = { onNavigateToQuadrant("DO_FIRST") },
                    modifier = Modifier.weight(1f)
                )
                QuadrantCard(
                    quadrant = Quadrant.SCHEDULE,
                    count = uiState.quadrantCounts[Quadrant.SCHEDULE] ?: 0,
                    onClick = { onNavigateToQuadrant("SCHEDULE") },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuadrantCard(
                    quadrant = Quadrant.DELEGATE,
                    count = uiState.quadrantCounts[Quadrant.DELEGATE] ?: 0,
                    onClick = { onNavigateToQuadrant("DELEGATE") },
                    modifier = Modifier.weight(1f)
                )
                QuadrantCard(
                    quadrant = Quadrant.ELIMINATE,
                    count = uiState.quadrantCounts[Quadrant.ELIMINATE] ?: 0,
                    onClick = { onNavigateToQuadrant("ELIMINATE") },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    if (showNewTaskSheet) {
        NewTaskSheet(
            onDismiss = { showNewTaskSheet = false },
            onSave = { task ->
                viewModel.insertTask(task)
                showNewTaskSheet = false
            },
            availableTasks = uiState.allActiveTasks
        )
    }
}

@Composable
fun QuadrantCard(
    quadrant: Quadrant,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(16.dp))
            .background(getQuadrantBgColor(quadrant))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = getQuadrantLabel(quadrant),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = getQuadrantTextColor(quadrant),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "$count Tasks",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = getQuadrantTextColor(quadrant)
            )
        }
    }
}
