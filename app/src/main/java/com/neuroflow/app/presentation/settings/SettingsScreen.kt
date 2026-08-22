package com.neuroflow.app.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neuroflow.app.domain.model.AppTheme
import com.neuroflow.app.presentation.common.theme.NeuroFlowColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPriorityWeights: () -> Unit,
    onNavigateToLauncherSettings: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Profile & Identity
            SettingsSection("Profile & Identity") {
                // Sync local state when prefs load from DataStore (avoids stale initial value)
                var identityLabel by remember { mutableStateOf(prefs.identityLabel) }
                LaunchedEffect(prefs.identityLabel) { identityLabel = prefs.identityLabel }
                OutlinedTextField(
                    value = identityLabel,
                    onValueChange = {
                        identityLabel = it
                        viewModel.updatePreferences { p -> p.copy(identityLabel = it) }
                    },
                    label = { Text("I am a...") },
                    placeholder = { Text("e.g. Deep Worker, Consistent Learner") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                var topGoal by remember { mutableStateOf(prefs.topGoal) }
                LaunchedEffect(prefs.topGoal) { topGoal = prefs.topGoal }
                OutlinedTextField(
                    value = topGoal,
                    onValueChange = {
                        topGoal = it
                        viewModel.updatePreferences { p -> p.copy(topGoal = it) }
                    },
                    label = { Text("My top goal") },
                    placeholder = { Text("What are you working towards?") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                SettingsNumberRow("Wake-up Hour", prefs.wakeUpHour, 0, 23, formatHour = true) {
                    viewModel.updatePreferences { p -> p.copy(wakeUpHour = it) }
                }
                SettingsNumberRow("Peak Energy Start", prefs.peakEnergyStart, 0, 23, formatHour = true) {
                    viewModel.updatePreferences { p -> p.copy(peakEnergyStart = it) }
                }
                SettingsNumberRow("Peak Energy End", prefs.peakEnergyEnd, 0, 23, formatHour = true) {
                    // Must be after peak start
                    viewModel.updatePreferences { p -> p.copy(peakEnergyEnd = maxOf(it, p.peakEnergyStart + 1)) }
                }
                if (prefs.peakEnergyEnd <= prefs.peakEnergyStart) {
                    Text(
                        "Peak end must be after peak start",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
            }

            // Work Schedule
            SettingsSection("Work Schedule") {
                SettingsNumberRow("Work Day Start", prefs.workDayStart, 0, 23, formatHour = true) {
                    viewModel.updatePreferences { p -> p.copy(workDayStart = it) }
                }
                SettingsNumberRow("Work Day End", prefs.workDayEnd, 0, 23, formatHour = true) {
                    // Must be after work start
                    viewModel.updatePreferences { p -> p.copy(workDayEnd = maxOf(it, p.workDayStart + 1)) }
                }
                if (prefs.workDayEnd <= prefs.workDayStart) {
                    Text(
                        "Work end must be after work start",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                } else {
                    Text(
                        "Work window: ${hourLabel(prefs.workDayStart)} – ${hourLabel(prefs.workDayEnd)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                SettingsNumberRow("Pomodoro (minutes)", prefs.defaultPomodoroMinutes, 5, 60) {
                    viewModel.updatePreferences { p -> p.copy(defaultPomodoroMinutes = it) }
                }
                SettingsNumberRow("Break (minutes)", prefs.defaultBreakMinutes, 1, 30) {
                    viewModel.updatePreferences { p -> p.copy(defaultBreakMinutes = it) }
                }
            }

            // Focus Behaviour
            SettingsSection("Focus Behaviour") {
                SettingsToggleRow(
                    label = "WOOP planning prompt",
                    description = "Show the WOOP sheet when opening a task for the first time",
                    checked = prefs.woopEnabled,
                    onCheckedChange = { viewModel.updatePreferences { p -> p.copy(woopEnabled = it) } }
                )
                Spacer(modifier = Modifier.height(8.dp))
                SettingsToggleRow(
                    label = "Auto-start tracker",
                    description = "Automatically starts tracking after 8 seconds on the focus screen",
                    checked = prefs.autoTrackerEnabled,
                    onCheckedChange = { viewModel.updatePreferences { p -> p.copy(autoTrackerEnabled = it) } }
                )
            }

            // Priority Weights
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                onClick = onNavigateToPriorityWeights
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Priority Weights", fontWeight = FontWeight.Bold)
                        Text("Customize task scoring weights", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Filled.ChevronRight, "Navigate")
                }
            }

            // Launcher
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                onClick = onNavigateToLauncherSettings
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Launcher", fontWeight = FontWeight.Bold)
                        Text(
                            "Home screen pages, icons, dock, distraction scoring",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Filled.ChevronRight, "Navigate")
                }
            }

            // Appearance
            SettingsSection("Appearance") {
                Text("Theme", fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppTheme.entries.forEach { theme ->
                        FilterChip(
                            selected = prefs.theme == theme,
                            onClick = { viewModel.setTheme(theme) },
                            label = { Text(theme.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
            }

            // Data
            SettingsSection("Data") {
                OutlinedButton(
                    onClick = { showClearDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeuroFlowColors.TrackingRed)
                ) {
                    Icon(Icons.Filled.DeleteForever, "Clear")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear All Data")
                }
            }

            // About
            SettingsSection("About") {
                Text("proFlow v3.0.0", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear All Data?") },
            text = { Text("This will permanently delete all tasks, sessions, and goals. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllData()
                    showClearDialog = false
                }) { Text("Delete", color = NeuroFlowColors.TrackingRed) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SettingsNumberRow(label: String, value: Int, min: Int, max: Int, formatHour: Boolean = false, onValueChange: (Int) -> Unit) {
    val displayValue = if (formatHour) hourLabel(value) else "$value"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (value > min) onValueChange(value - 1) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Remove, "Decrease", modifier = Modifier.size(18.dp))
            }
            Text(displayValue, fontWeight = FontWeight.Bold, modifier = Modifier.width(52.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 13.sp)
            IconButton(onClick = { if (value < max) onValueChange(value + 1) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Add, "Increase", modifier = Modifier.size(18.dp))
            }
        }
    }
}

private fun hourLabel(hour: Int) = when {
    hour == 0  -> "12 am"
    hour < 12  -> "$hour am"
    hour == 12 -> "12 pm"
    else       -> "${hour - 12} pm"
}

@Composable
private fun SettingsToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
