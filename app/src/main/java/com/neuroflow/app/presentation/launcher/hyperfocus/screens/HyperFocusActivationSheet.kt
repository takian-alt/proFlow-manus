package com.neuroflow.app.presentation.launcher.hyperfocus.screens

import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neuroflow.app.domain.model.HyperFocusSessionMode
import com.neuroflow.app.presentation.launcher.hyperfocus.HyperFocusViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HyperFocusActivationSheet(
    viewModel: HyperFocusViewModel,
    distractionScores: Map<String, Int>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var accessibilityEnabled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        accessibilityEnabled = com.neuroflow.app.presentation.launcher.hyperfocus.util.AccessibilityUtil
            .isAppBlockingServiceEnabled(context)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        if (!accessibilityEnabled) {
            PermissionSetupScreen(onBothGranted = { accessibilityEnabled = true })
        } else {
            ActivationSheetContent(
                viewModel = viewModel,
                distractionScores = distractionScores,
                onDismiss = onDismiss
            )
        }
    }
}

@Composable
private fun ActivationSheetContent(
    viewModel: HyperFocusViewModel,
    distractionScores: Map<String, Int>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activeTasks by viewModel.activeTasks.collectAsState()
    val hasActiveTasks = activeTasks.isNotEmpty()

    // Build full app list with labels, sorted alphabetically, only including launchable apps
    val allApps = remember {
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN, null)
        intent.addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        val resolveInfos = context.packageManager.queryIntentActivities(intent, 0)
        
        resolveInfos.mapNotNull { info ->
            if (info.activityInfo.packageName == context.packageName) null
            else {
                val label = info.loadLabel(context.packageManager).toString()
                label to info.activityInfo.packageName
            }
        }.distinctBy { it.second }.sortedBy { it.first.lowercase() }
    }

    // Pre-select apps with distraction score > 70
    val selectedPackages = remember {
        mutableStateMapOf<String, Boolean>().also { map ->
            distractionScores.filter { it.value > 70 }.keys.forEach { pkg -> map[pkg] = true }
        }
    }
    val selectedTaskIds = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(activeTasks) {
        val activeIds = activeTasks.map { it.id }.toSet()
        val stale = selectedTaskIds.keys.filter { it !in activeIds }
        stale.forEach { selectedTaskIds.remove(it) }
        activeTasks.forEach { task ->
            if (selectedTaskIds[task.id] == null) {
                selectedTaskIds[task.id] = false
            }
        }
    }

    var taskSearchQuery by remember { mutableStateOf("") }
    var appSearchQuery by remember { mutableStateOf("") }
    var confirmText by remember { mutableStateOf("") }
    var sessionMode by remember { mutableStateOf(HyperFocusSessionMode.TASK_BASED) }
    val durationOptions = remember { listOf(15, 25, 45, 60, 90, 120) }
    var selectedDurationMinutes by remember { mutableStateOf(25) }

    val filteredApps = remember(appSearchQuery, allApps) {
        if (appSearchQuery.isBlank()) allApps
        else allApps.filter { (label, pkg) ->
            label.contains(appSearchQuery, ignoreCase = true) ||
            pkg.contains(appSearchQuery, ignoreCase = true)
        }
    }
    val filteredTasks = remember(taskSearchQuery, activeTasks) {
        if (taskSearchQuery.isBlank()) activeTasks
        else activeTasks.filter { task ->
            task.title.contains(taskSearchQuery, ignoreCase = true) ||
                task.description.contains(taskSearchQuery, ignoreCase = true)
        }
    }

    val selectedCount = selectedPackages.count { it.value }
    val selectedTaskCount = selectedTaskIds.count { it.value }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title
        Text(
            text = "Activate Hyper Focus",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        // Warning banner
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.errorContainer,
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                text = if (sessionMode == HyperFocusSessionMode.TIME_BASED) {
                    "⚠️ Selected apps will be blocked for your chosen duration."
                } else {
                    "⚠️ Selected apps will be blocked until you complete your daily tasks."
                },
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp)
            )
        }

        Text(
            text = "Focus mode",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = sessionMode == HyperFocusSessionMode.TASK_BASED,
                onClick = { sessionMode = HyperFocusSessionMode.TASK_BASED },
                label = { Text("Task-based") }
            )
            FilterChip(
                selected = sessionMode == HyperFocusSessionMode.TIME_BASED,
                onClick = { sessionMode = HyperFocusSessionMode.TIME_BASED },
                label = { Text("Time-based") }
            )
        }

        if (sessionMode == HyperFocusSessionMode.TASK_BASED) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Select tasks for this session",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Daily task target: $selectedTaskCount task(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Text(
                text = "Block duration",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                durationOptions.forEach { minutes ->
                    FilterChip(
                        selected = selectedDurationMinutes == minutes,
                        onClick = { selectedDurationMinutes = minutes },
                        label = { Text("${minutes}m") }
                    )
                }
            }
        }

        if (sessionMode == HyperFocusSessionMode.TASK_BASED) {
            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tasks in Hyper Focus",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "$selectedTaskCount selected",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            OutlinedTextField(
                value = taskSearchQuery,
                onValueChange = { taskSearchQuery = it },
                placeholder = { Text("Search tasks...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (filteredTasks.isEmpty()) {
                Text(
                    text = "No active tasks found.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                filteredTasks.forEach { task ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = selectedTaskIds[task.id] == true,
                            onCheckedChange = { checked -> selectedTaskIds[task.id] = checked }
                        )
                        Column(modifier = Modifier.padding(start = 4.dp)) {
                            Text(
                                text = task.title,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (task.description.isNotBlank()) {
                                Text(
                                    text = task.description,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider()

        // Confirmation field
        OutlinedTextField(
            value = confirmText,
            onValueChange = { confirmText = it },
            label = { Text("Type FOCUS to confirm") },
            supportingText = { Text("This action is hard to reverse during a session.") },
            singleLine = true,
            isError = confirmText.isNotEmpty() && confirmText != "FOCUS",
            modifier = Modifier.fillMaxWidth()
        )

        // Activate button near top for faster access
        Button(
            onClick = {
                val selected = selectedPackages.filter { it.value }.keys.toSet()
                if (sessionMode == HyperFocusSessionMode.TIME_BASED) {
                    viewModel.activateTimed(selected, selectedDurationMinutes)
                } else {
                    val selectedTasks = selectedTaskIds.filter { it.value }.keys.toSet()
                    viewModel.activate(selected, selectedTasks)
                }
                onDismiss()
            },
            enabled = confirmText == "FOCUS" && selectedCount > 0 &&
                (sessionMode == HyperFocusSessionMode.TIME_BASED || (hasActiveTasks && selectedTaskCount > 0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            val label = if (sessionMode == HyperFocusSessionMode.TIME_BASED) {
                "Activate ${selectedDurationMinutes}m Focus Lock"
            } else {
                "Activate Hyper Focus 🔒"
            }
            Text(label)
        }

        HorizontalDivider()

        // App selector header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Apps to block",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "$selectedCount selected",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Search field
        OutlinedTextField(
            value = appSearchQuery,
            onValueChange = { appSearchQuery = it },
            placeholder = { Text("Search apps...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // App list
        if (filteredApps.isEmpty()) {
            Text(
                text = "No apps found.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            filteredApps.forEach { (label, pkg) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = selectedPackages[pkg] == true,
                        onCheckedChange = { checked -> selectedPackages[pkg] = checked }
                    )
                    Column(modifier = Modifier.padding(start = 4.dp)) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = pkg,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // No tasks warning
        if (sessionMode == HyperFocusSessionMode.TASK_BASED && !hasActiveTasks) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "⚠️ You need at least one active task before activating Hyper Focus.",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}
