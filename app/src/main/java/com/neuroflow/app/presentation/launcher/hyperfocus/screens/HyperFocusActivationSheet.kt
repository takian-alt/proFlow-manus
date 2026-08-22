package com.neuroflow.app.presentation.launcher.hyperfocus.screens

import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    val activeTaskCount by viewModel.activeTaskCount.collectAsState()
    val hasActiveTasks = activeTaskCount > 0

    // Build full app list with labels, sorted alphabetically
    val allApps = remember {
        context.packageManager.getInstalledApplications(0)
            .filter { it.packageName != context.packageName }
            .mapNotNull { info ->
                val label = context.packageManager.getApplicationLabel(info).toString()
                label to info.packageName
            }
            .sortedBy { it.first.lowercase() }
    }

    // Pre-select apps with distraction score > 70
    val selectedPackages = remember {
        mutableStateMapOf<String, Boolean>().also { map ->
            distractionScores.filter { it.value > 70 }.keys.forEach { pkg -> map[pkg] = true }
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var confirmText by remember { mutableStateOf("") }

    val filteredApps = remember(searchQuery, allApps) {
        if (searchQuery.isBlank()) allApps
        else allApps.filter { (label, pkg) ->
            label.contains(searchQuery, ignoreCase = true) ||
            pkg.contains(searchQuery, ignoreCase = true)
        }
    }

    val selectedCount = selectedPackages.count { it.value }

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
                text = "⚠️ Selected apps will be blocked until you complete your daily tasks.",
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp)
            )
        }

        // Daily task target
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                text = "Daily task target: $activeTaskCount task(s)",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(12.dp)
            )
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
            value = searchQuery,
            onValueChange = { searchQuery = it },
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

        // No tasks warning
        if (!hasActiveTasks) {
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

        // Activate button
        Button(
            onClick = {
                val selected = selectedPackages.filter { it.value }.keys.toSet()
                viewModel.activate(selected)
                onDismiss()
            },
            enabled = confirmText == "FOCUS" && selectedCount > 0 && hasActiveTasks,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Activate Hyper Focus 🔒")
        }
    }
}
