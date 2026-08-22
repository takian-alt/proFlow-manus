package com.neuroflow.app.presentation.launcher.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.provider.Settings
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.neuroflow.app.presentation.launcher.LauncherViewModel
import com.neuroflow.app.presentation.launcher.data.AppInfo
import com.neuroflow.app.presentation.launcher.data.ClockStyle
import com.neuroflow.app.presentation.launcher.data.IconShape
import com.neuroflow.app.presentation.launcher.data.ImportResult
import com.neuroflow.app.presentation.launcher.hyperfocus.HyperFocusViewModel
import com.neuroflow.app.presentation.launcher.hyperfocus.screens.HyperFocusActivationSheet
import com.neuroflow.app.presentation.launcher.hyperfocus.screens.PermissionSetupScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

/**
 * LauncherSettings provides comprehensive customization for the Focus Launcher.
 *
 * This screen includes:
 * - Distraction score assignment UI (Task 20.1)
 * - Quick categorize flow (Task 20.2)
 * - Dock editor, hidden apps, locked apps management (Task 23.2)
 * - Visual customization (clock style, card transparency, icon pack, icon shape, grid size) (Task 23.3)
 * - Feature settings (notification badges, show task score, web search URL, backup/restore) (Task 23.4)
 * - Launcher onboarding card (Task 24)
 *
 * Requirements:
 * - 20.1: LauncherSettings SHALL allow user to assign distractionScore (0-100 slider) to each app
 * - 20.2: Default distractionScore for all apps SHALL be 50 (neutral)
 * - 20.6: LauncherSettings SHALL offer "Quick categorize" flow pre-assigning scores
 * - 24.14: ALL changes SHALL be persisted to PinnedAppsDataStore before screen dismissal
 *
 * @param isOpen Whether the settings screen is currently open
 * @param onDismiss Callback when settings screen is dismissed
 * @param viewModel LauncherViewModel (hoisted from LauncherActivity)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherSettings(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onNavigateToRewards: () -> Unit = {},
    viewModel: LauncherViewModel = viewModel()
) {
    val context = LocalContext.current
    // Collect state from ViewModel
    val allApps by viewModel.allApps.collectAsState()
    val distractionScores by viewModel.distractionScores.collectAsState()

    // HyperFocus ViewModel and state (Requirements: 6.3)
    val hyperFocusViewModel: HyperFocusViewModel = hiltViewModel()
    val hyperFocusPrefs by hyperFocusViewModel.hyperFocusPrefs.collectAsStateWithLifecycle()
    var showActivationSheet by remember { mutableStateOf(false) }
    var showPermissionSetup by remember { mutableStateOf(false) }

    // Track which section is expanded
    var expandedSection by remember { mutableStateOf<SettingsSection?>(null) }

    if (isOpen) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("launcher_settings"),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Top app bar
                TopAppBar(
                    title = { Text("Launcher Settings") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                // Settings content
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Launcher Onboarding Card (Task 24)
                    item {
                        LauncherOnboardingCard(viewModel = viewModel)
                    }

                    // Home Screen Pages Section
                    item {
                        HomeScreenPagesSection(viewModel = viewModel)
                    }

                    // Distraction Scoring Section (Task 20.1, 20.2)
                    item {
                        DistractionScoringSection(
                            allApps = allApps,
                            distractionScores = distractionScores,
                            isExpanded = expandedSection == SettingsSection.DISTRACTION_SCORING,
                            onToggleExpanded = {
                                expandedSection = if (expandedSection == SettingsSection.DISTRACTION_SCORING) {
                                    null
                                } else {
                                    SettingsSection.DISTRACTION_SCORING
                                }
                            },
                            onUpdateScore = { packageName, score ->
                                viewModel.updateDistractionScore(packageName, score)
                            },
                            onQuickCategorize = { categoryScores ->
                                // Apply quick categorize scores (Task 20.2)
                                categoryScores.forEach { (packageName, score) ->
                                    viewModel.updateDistractionScore(packageName, score)
                                }
                            }
                        )
                    }

                    // Dock and App Management Section (Task 23.2)
                    item {
                        DockAndAppManagementSection(
                            viewModel = viewModel,
                            isExpanded = expandedSection == SettingsSection.DOCK_EDITOR,
                            onToggleExpanded = {
                                expandedSection = if (expandedSection == SettingsSection.DOCK_EDITOR) {
                                    null
                                } else {
                                    SettingsSection.DOCK_EDITOR
                                }
                            }
                        )
                    }

                    // Visual Customization Section (Task 23.3)
                    item {
                        VisualCustomizationSection(
                            viewModel = viewModel,
                            isExpanded = expandedSection == SettingsSection.VISUAL_CUSTOMIZATION,
                            onToggleExpanded = {
                                expandedSection = if (expandedSection == SettingsSection.VISUAL_CUSTOMIZATION) {
                                    null
                                } else {
                                    SettingsSection.VISUAL_CUSTOMIZATION
                                }
                            }
                        )
                    }

                    // Feature Settings Section (Task 23.4)
                    item {
                        FeatureSettingsSection(
                            viewModel = viewModel,
                            isExpanded = expandedSection == SettingsSection.FEATURE_SETTINGS,
                            onToggleExpanded = {
                                expandedSection = if (expandedSection == SettingsSection.FEATURE_SETTINGS) {
                                    null
                                } else {
                                    SettingsSection.FEATURE_SETTINGS
                                }
                            }
                        )
                    }

                    // Hyper Focus Section (Task 7.3, Requirements: 6.3)
                    item {
                        HyperFocusSettingsSection(
                            isActive = hyperFocusPrefs.isActive,
                            onSetup = {
                                val accessibilityEnabled = com.neuroflow.app.presentation.launcher.hyperfocus.util.AccessibilityUtil
                                    .isAppBlockingServiceEnabled(context)
                                val usageStatsEnabled = try {
                                    val appOps = context.getSystemService(android.app.AppOpsManager::class.java)
                                    appOps.unsafeCheckOpNoThrow(
                                        android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                                        android.os.Process.myUid(),
                                        context.packageName
                                    ) == android.app.AppOpsManager.MODE_ALLOWED
                                } catch (e: Exception) { false }

                                if (!accessibilityEnabled || !usageStatsEnabled) {
                                    showPermissionSetup = true
                                } else {
                                    showActivationSheet = true
                                }
                            },
                            onViewProgress = { showActivationSheet = false },
                            onNavigateToRewards = onNavigateToRewards,
                            hyperFocusViewModel = hyperFocusViewModel
                        )
                    }

                    // Backup/Restore Section (Task 21.3)
                    item {
                        BackupRestoreSection(
                            viewModel = viewModel
                        )
                    }
                }
            }
        }

        // HyperFocusActivationSheet (Requirements: 6.3)
        if (showActivationSheet) {
            HyperFocusActivationSheet(
                viewModel = hyperFocusViewModel,
                distractionScores = distractionScores,
                onDismiss = { showActivationSheet = false }
            )
        }

        // PermissionSetupScreen shown as full-screen dialog (Requirements: 6.3)
        if (showPermissionSetup) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showPermissionSetup = false },
                properties = androidx.compose.ui.window.DialogProperties(
                    usePlatformDefaultWidth = false,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = false
                )
            ) {
                PermissionSetupScreen(
                    onBothGranted = {
                        showPermissionSetup = false
                        showActivationSheet = true
                    }
                )
            }
        }
    }
}

/**
 * Enum representing different settings sections.
 */
private enum class SettingsSection {
    DISTRACTION_SCORING,
    DOCK_EDITOR,
    VISUAL_CUSTOMIZATION,
    FEATURE_SETTINGS
}

/**
 * Hyper Focus Settings Section (Task 7.3).
 *
 * Shows:
 * - Section header "Hyper Focus"
 * - Current status (active/inactive)
 * - "Setup / Activate" button (checks permissions, shows sheet or permission setup)
 * - "View Progress" row (only when isActive == true)
 *
 * Requirements: 6.3
 */
@Composable
private fun HyperFocusSettingsSection(
    isActive: Boolean,
    onSetup: () -> Unit,
    onViewProgress: () -> Unit,
    onNavigateToRewards: () -> Unit,
    hyperFocusViewModel: HyperFocusViewModel
) {
    val hyperFocusPrefs by hyperFocusViewModel.hyperFocusPrefs.collectAsStateWithLifecycle()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Section header
            Text(
                text = "Hyper Focus",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Status row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Status",
                    style = MaterialTheme.typography.bodyMedium
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    }
                ) {
                    Text(
                        text = if (isActive) "Active" else "Inactive",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isActive) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Show active status only — session cannot be manually stopped
            if (isActive) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = "🔒 Session active — complete your tasks to finish.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                Button(
                    onClick = onSetup,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Setup / Activate")
                }
            }

            // View Progress row — only visible when active
            if (isActive) {
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "View Progress",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(onClick = onViewProgress) {
                        Text("Open")
                    }
                }

                // Inline rewards/progress view
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Rewards",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(onClick = onNavigateToRewards) {
                        Text("View →")
                    }
                }

                // Debug: Show blocked packages
                HorizontalDivider()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Blocked Apps (${hyperFocusPrefs.blockedPackages.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    hyperFocusPrefs.blockedPackages.take(5).forEach { pkg ->
                        Text(
                            text = "• $pkg",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (hyperFocusPrefs.blockedPackages.size > 5) {
                        Text(
                            text = "... and ${hyperFocusPrefs.blockedPackages.size - 5} more",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Home Screen Pages Section.
 * Shows the 3 fixed pages and any user-created extra pages.
 * Allows adding (up to 10 total) and deleting extra pages.
 */
@Composable
private fun HomeScreenPagesSection(viewModel: LauncherViewModel) {
    val extraPages by viewModel.homeScreenPages.collectAsState()
    val totalPages = 3 + extraPages.size

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Home Screen Pages",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Swipe left/right on the home screen to navigate pages.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Fixed pages
            listOf("Page 1 — Left", "Page 2 — Main", "Page 3 — Stats").forEach { label ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Fixed",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Extra user pages
            extraPages.forEachIndexed { index, page ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Page ${index + 4} — ${page.name.ifBlank { "Custom" }}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    IconButton(
                        onClick = { viewModel.removeHomeScreenPage(page.id) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Delete page",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (totalPages < 10) {
                Button(
                    onClick = { viewModel.addHomeScreenPage() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Page  ($totalPages / 10)")
                }
            } else {
                Text(
                    "Maximum 10 pages reached.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Distraction Scoring Section (Task 20.1, 20.2).
 *
 * Allows users to:
 * - Assign distraction scores (0-100) to each app via slider
 * - Use "Quick categorize" to pre-assign scores by category
 * - Default score is 50 (neutral) for all apps
 *
 * Requirements:
 * - 20.1: Allow user to assign distractionScore (0-100 slider) to each app, persisted in PinnedAppsDataStore
 * - 20.2: Default distractionScore for all apps SHALL be 50 (neutral)
 * - 20.6: Offer "Quick categorize" flow pre-assigning scores: Social (85), Games (90), News (75), Productivity (15), Tools (20)
 *
 * @param allApps List of all installed apps
 * @param distractionScores Current distraction scores map
 * @param isExpanded Whether this section is expanded
 * @param onToggleExpanded Callback to toggle expansion
 * @param onUpdateScore Callback to update a single app's score
 * @param onQuickCategorize Callback to apply quick categorize scores
 */
@Composable
private fun DistractionScoringSection(
    allApps: List<AppInfo>,
    distractionScores: Map<String, Int>,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onUpdateScore: (String, Int) -> Unit,
    onQuickCategorize: (Map<String, Int>) -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Section header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Distraction Scoring",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Assign distraction levels to apps (0 = productive, 100 = distracting)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TextButton(onClick = onToggleExpanded) {
                    Text(if (isExpanded) "Collapse" else "Expand")
                }
            }

            // Expanded content
            if (isExpanded) {
                Spacer(modifier = Modifier.height(16.dp))

                // Quick categorize button (Task 20.2)
                Button(
                    onClick = {
                        // Pre-assign scores by category (Requirement 20.6)
                        val categoryScores = categorizeApps(allApps)
                        onQuickCategorize(categoryScores)
                        android.widget.Toast.makeText(
                            context,
                            "Applied quick categorization to ${categoryScores.size} apps",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Quick Categorize")
                }

                Text(
                    text = "Quick categorize assigns: Social (85), Games (90), News (75), Productivity (15), Tools (20)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Sort apps alphabetically using locale-aware Collator
                val sortedApps = remember(allApps) {
                    val collator = Collator.getInstance(Locale.getDefault())
                    allApps.sortedWith(compareBy(collator) { it.label })
                }

                // App list with sliders
                Text(
                    text = "Individual App Scores",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                sortedApps.forEach { app ->
                    // Get current score, default to 50 if not set (Requirement 20.2)
                    val currentScore = distractionScores[app.packageName] ?: 50

                    AppDistractionScoreRow(
                        app = app,
                        currentScore = currentScore,
                        onScoreChange = { newScore ->
                            onUpdateScore(app.packageName, newScore)
                        }
                    )
                }
            }
        }
    }
}

/**
 * Row displaying a single app with its distraction score slider.
 *
 * @param app App to display
 * @param currentScore Current distraction score (0-100)
 * @param onScoreChange Callback when score changes
 */
@Composable
private fun AppDistractionScoreRow(
    app: AppInfo,
    currentScore: Int,
    onScoreChange: (Int) -> Unit
) {
    var sliderValue by remember(currentScore) { mutableFloatStateOf(currentScore.toFloat()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = currentScore.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = when {
                    currentScore < 40 -> MaterialTheme.colorScheme.primary // Low distraction
                    currentScore > 70 -> MaterialTheme.colorScheme.error // High distraction
                    else -> MaterialTheme.colorScheme.onSurface // Neutral
                }
            )
        }

        Slider(
            value = sliderValue,
            onValueChange = { newValue ->
                sliderValue = newValue
            },
            onValueChangeFinished = {
                // Persist score when user finishes dragging (Requirement 20.1)
                onScoreChange(sliderValue.toInt())
            },
            valueRange = 0f..100f,
            steps = 99, // 101 total values (0-100)
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Categorize apps by package name patterns and assign distraction scores.
 *
 * Categories (Requirement 20.6):
 * - Social: 85
 * - Games: 90
 * - News: 75
 * - Productivity: 15
 * - Tools: 20
 *
 * @param apps List of all installed apps
 * @return Map of package name to distraction score
 */
private fun categorizeApps(apps: List<AppInfo>): Map<String, Int> {
    val categoryScores = mutableMapOf<String, Int>()

    apps.forEach { app ->
        val packageName = app.packageName.lowercase()
        val label = app.label.lowercase()

        // Categorize based on package name and label patterns
        val score = when {
            // Social apps (85)
            packageName.contains("facebook") ||
            packageName.contains("instagram") ||
            packageName.contains("twitter") ||
            packageName.contains("tiktok") ||
            packageName.contains("snapchat") ||
            packageName.contains("whatsapp") ||
            packageName.contains("telegram") ||
            packageName.contains("messenger") ||
            packageName.contains("discord") ||
            packageName.contains("reddit") ||
            packageName.contains("linkedin") ||
            label.contains("social") -> 85

            // Games (90)
            packageName.contains("game") ||
            packageName.contains("play.") ||
            label.contains("game") -> 90

            // News (75)
            packageName.contains("news") ||
            packageName.contains("cnn") ||
            packageName.contains("bbc") ||
            packageName.contains("nytimes") || // New York Times
            label.contains("news") -> 75

            // Productivity (15)
            packageName.contains("office") ||
            packageName.contains("docs") ||
            packageName.contains("sheets") ||
            packageName.contains("slides") ||
            packageName.contains("notion") ||
            packageName.contains("evernote") ||
            packageName.contains("todoist") ||
            packageName.contains("trello") ||
            packageName.contains("asana") ||
            packageName.contains("slack") ||
            packageName.contains("zoom") ||
            packageName.contains("teams") ||
            label.contains("productivity") ||
            label.contains("office") -> 15

            // Tools (20)
            packageName.contains("calculator") ||
            packageName.contains("calendar") ||
            packageName.contains("clock") ||
            packageName.contains("camera") ||
            packageName.contains("gallery") ||
            packageName.contains("files") ||
            packageName.contains("settings") ||
            label.contains("tool") ||
            label.contains("utility") -> 20

            // Default: no change (keep existing score or default 50)
            else -> null
        }

        // Only add to map if we have a category match
        score?.let { categoryScores[app.packageName] = it }
    }

    return categoryScores
}

/**
 * Backup/Restore Section (Task 21.3).
 *
 * Provides:
 * - "Export configuration" button using ActivityResultContracts.CreateDocument
 * - "Import configuration" button using ActivityResultContracts.OpenDocument
 * - Write/read JSON files with application/json MIME type
 *
 * Requirements:
 * - 18.2: Provide "Export configuration" button writing JSON to user-chosen file
 * - 18.3: Provide "Import configuration" button reading and validating JSON file
 * - 18.4: Show summary when imported config references uninstalled packages
 *
 * @param viewModel LauncherViewModel
 */
@Composable
private fun BackupRestoreSection(
    viewModel: LauncherViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isExporting by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var importResult by remember { mutableStateOf<ImportResult?>(null) }

    // Export launcher: Create document contract
    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            isExporting = true
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val json = viewModel.exportConfiguration()
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(json.toByteArray())
                    }
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            context,
                            "Configuration exported successfully",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        isExporting = false
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            context,
                            "Export failed: ${e.message}",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                        isExporting = false
                    }
                }
            }
        }
    }

    // Import launcher: Open document contract
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            isImporting = true
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val json = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        inputStream.bufferedReader().readText()
                    } ?: ""

                    val result = viewModel.importConfiguration(json)
                    withContext(Dispatchers.Main) {
                        importResult = result
                        if (result.success) {
                            val message = if (result.skippedPackages.isNotEmpty()) {
                                // Requirement 18.4: Show summary of skipped packages
                                "${result.skippedPackages.size} apps from backup are not installed and were skipped"
                            } else {
                                "Configuration imported successfully"
                            }
                            android.widget.Toast.makeText(
                                context,
                                message,
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        } else {
                            android.widget.Toast.makeText(
                                context,
                                "Import failed: ${result.errorMessage}",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                        isImporting = false
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            context,
                            "Import failed: ${e.message}",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                        isImporting = false
                    }
                }
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Section header
            Text(
                text = "Backup & Restore",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Export and import your launcher configuration",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Export button (Requirement 18.2)
            Button(
                onClick = {
                    // Launch file picker with default filename
                    val timestamp = java.text.SimpleDateFormat(
                        "yyyyMMdd_HHmmss", // Format: YearMonthDay_HourMinuteSecond
                        java.util.Locale.getDefault()
                    ).format(java.util.Date())
                    exportLauncher.launch("launcher_backup_$timestamp.json")
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isExporting && !isImporting
            ) {
                if (isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Export Configuration")
            }

            // Import button (Requirement 18.3)
            OutlinedButton(
                onClick = {
                    // Launch file picker for JSON files
                    importLauncher.launch(arrayOf("application/json"))
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isExporting && !isImporting
            ) {
                if (isImporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Import Configuration")
            }

            // Show import result details if available (Requirement 18.4)
            importResult?.let { result ->
                if (result.success && result.skippedPackages.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = "Skipped Apps",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${result.skippedPackages.size} apps from the backup are not installed on this device:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            result.skippedPackages.take(5).forEach { pkg ->
                                Text(
                                    text = "• $pkg",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                            if (result.skippedPackages.size > 5) {
                                Text(
                                    text = "... and ${result.skippedPackages.size - 5} more",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


/**
 * Dock and App Management Section (Task 23.2).
 *
 * Provides:
 * - Dock editor: add, remove, reorder pinned apps
 * - Hidden apps manager: list with unhide option
 * - Locked apps manager: list with unlock option
 *
 * Requirements:
 * - 24.1: Dock editor with add, remove, reorder
 * - 24.2: Hidden apps manager with unhide option
 * - 24.3: Locked apps manager with unlock option
 */
@Composable
private fun DockAndAppManagementSection(
    viewModel: LauncherViewModel,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit
) {
    val dockApps by viewModel.dockApps.collectAsState()
    val hiddenPackages by viewModel.hiddenPackages.collectAsState()
    val lockedPackages by viewModel.lockedPackages.collectAsState()
    val allApps by viewModel.allApps.collectAsState()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Section header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Dock & App Management",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Manage dock apps, hidden apps, and locked apps",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TextButton(onClick = onToggleExpanded) {
                    Text(if (isExpanded) "Collapse" else "Expand")
                }
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(16.dp))

                // Dock Apps (Requirement 24.1)
                Text(
                    text = "Dock Apps (${dockApps.size}/5)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (dockApps.isEmpty()) {
                    Text(
                        text = "No apps pinned to dock",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    dockApps.forEach { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = app.label,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = { viewModel.removeFromDock(app.packageName) }
                            ) {
                                Text("Remove")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Hidden Apps (Requirement 24.2)
                Text(
                    text = "Hidden Apps (${hiddenPackages.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (hiddenPackages.isEmpty()) {
                    Text(
                        text = "No hidden apps",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    hiddenPackages.forEach { packageName ->
                        val app = allApps.firstOrNull { it.packageName == packageName }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = app?.label ?: packageName,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = { viewModel.unhideApp(packageName) }
                            ) {
                                Text("Unhide")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Locked Apps (Requirement 24.3)
                Text(
                    text = "Locked Apps (${lockedPackages.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (lockedPackages.isEmpty()) {
                    Text(
                        text = "No locked apps",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    lockedPackages.forEach { packageName ->
                        val app = allApps.firstOrNull { it.packageName == packageName }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = app?.label ?: packageName,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = { viewModel.unlockApp(packageName) }
                            ) {
                                Text("Unlock")
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Visual Customization Section (Task 23.3).
 *
 * Provides:
 * - Clock style selector: Digital, Minimal
 * - Card transparency slider: 0.5-1.0, step 0.05
 * - Icon pack selector: all installed packs, apply without restart
 * - Icon shape selector: Circle, Squircle, Rounded Square, Teardrop, System Default
 * - App drawer grid size: 3, 4, or 5 columns
 *
 * Requirements:
 * - 24.4: Clock style selector
 * - 24.5: Card transparency slider
 * - 24.6: Icon pack selector
 * - 24.7: Icon shape selector
 * - 24.8: App drawer grid size
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VisualCustomizationSection(
    viewModel: LauncherViewModel,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit
) {
    val launcherPrefs by viewModel.launcherTheme.collectAsState()
    val installedIconPacks by viewModel.installedIconPacks.collectAsState()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Section header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Visual Customization",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Customize appearance and layout",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TextButton(onClick = onToggleExpanded) {
                    Text(if (isExpanded) "Collapse" else "Expand")
                }
            }

            if (isExpanded && launcherPrefs != null) {
                Spacer(modifier = Modifier.height(16.dp))

                // Clock Style (Requirement 24.4)
                Text(
                    text = "Clock Style",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ClockStyle.entries.forEach { style ->
                        FilterChip(
                            selected = launcherPrefs!!.clockStyle == style,
                            onClick = { viewModel.updateClockStyle(style) },
                            label = { Text(style.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Card Transparency (Requirement 24.5)
                Text(
                    text = "Card Transparency: ${String.format(Locale.US, "%.2f", launcherPrefs!!.cardAlpha)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                var cardAlphaSlider by remember(launcherPrefs!!.cardAlpha) {
                    mutableFloatStateOf(launcherPrefs!!.cardAlpha)
                }

                Slider(
                    value = cardAlphaSlider,
                    onValueChange = { cardAlphaSlider = it },
                    onValueChangeFinished = {
                        viewModel.updateCardAlpha(cardAlphaSlider)
                    },
                    valueRange = 0.5f..1.0f,
                    steps = 9, // 0.05 step size: (1.0 - 0.5) / 0.05 - 1 = 9
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Icon Pack (Requirement 24.6)
                Text(
                    text = "Icon Pack",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                var expandedIconPackDropdown by remember { mutableStateOf(false) }

                ExposedDropdownMenuBox(
                    expanded = expandedIconPackDropdown,
                    onExpandedChange = { expandedIconPackDropdown = it }
                ) {
                    OutlinedTextField(
                        value = launcherPrefs!!.iconPackPackageName?.let { pkg ->
                            installedIconPacks.firstOrNull { it.packageName == pkg }?.label
                        } ?: "System Default",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedIconPackDropdown) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )

                    ExposedDropdownMenu(
                        expanded = expandedIconPackDropdown,
                        onDismissRequest = { expandedIconPackDropdown = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("System Default") },
                            onClick = {
                                viewModel.updateIconPack(null)
                                expandedIconPackDropdown = false
                            }
                        )
                        installedIconPacks.forEach { pack ->
                            DropdownMenuItem(
                                text = { Text(pack.label) },
                                onClick = {
                                    viewModel.updateIconPack(pack.packageName)
                                    expandedIconPackDropdown = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Icon Shape (Requirement 24.7)
                Text(
                    text = "Icon Shape",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                IconShape.entries.forEach { shape ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = launcherPrefs!!.iconShape == shape,
                            onClick = { viewModel.updateIconShape(shape) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = shape.name.lowercase().split('_').joinToString(" ") {
                                it.replaceFirstChar { c -> c.uppercase() }
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // App Drawer Grid Size (Requirement 24.8)
                Text(
                    text = "App Drawer Columns: ${launcherPrefs!!.drawerColumns}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(3, 4, 5).forEach { columns ->
                        FilterChip(
                            selected = launcherPrefs!!.drawerColumns == columns,
                            onClick = { viewModel.updateDrawerColumns(columns) },
                            label = { Text("$columns") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Task Card Style (Phase 4)
                Text(
                    text = "Task Card Style",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    com.neuroflow.app.presentation.launcher.data.CardStyle.entries.forEach { style ->
                        FilterChip(
                            selected = launcherPrefs!!.taskCardStyle == style,
                            onClick = { viewModel.updateCardStyle(style) },
                            label = { Text(style.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Feature Settings Section (Task 23.4).
 *
 * Provides:
 * - Notification badges toggle
 * - "Show task score" toggle
 * - Web search URL configuration
 *
 * Requirements:
 * - 24.10: Notification badges toggle
 * - 24.11: "Show task score" toggle
 * - 24.12: Web search URL configuration
 */
@Composable
private fun FeatureSettingsSection(
    viewModel: LauncherViewModel,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit
) {
    val context = LocalContext.current
    val launcherPrefs by viewModel.launcherTheme.collectAsState()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Section header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Feature Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Configure launcher features",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TextButton(onClick = onToggleExpanded) {
                    Text(if (isExpanded) "Collapse" else "Expand")
                }
            }

            if (isExpanded && launcherPrefs != null) {
                Spacer(modifier = Modifier.height(16.dp))

                // Notification Badges (Requirement 24.10)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Notification Badges",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Show unread notification counts on app icons",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = {
                            // Open notification listener settings (Requirement 13.4)
                            val intent = android.content.Intent(
                                android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
                            )
                            context.startActivity(intent)
                        }
                    ) {
                        Text("Configure")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Show Task Score (Requirement 24.11)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Show Task Score",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Display raw TaskScoringEngine score on FocusTaskCard",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = launcherPrefs!!.showTaskScore,
                        onCheckedChange = { viewModel.updateShowTaskScore(it) }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Web Search URL (Requirement 24.12)
                var webSearchUrl by remember(launcherPrefs!!.webSearchUrl) {
                    mutableStateOf(launcherPrefs!!.webSearchUrl)
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = "Web Search URL",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "URL template for web search (use %s for query placeholder)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = webSearchUrl,
                        onValueChange = { webSearchUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        trailingIcon = {
                            if (webSearchUrl != launcherPrefs!!.webSearchUrl) {
                                IconButton(
                                    onClick = { viewModel.updateWebSearchUrl(webSearchUrl) }
                                ) {
                                    Icon(
                                        imageVector = androidx.compose.material.icons.Icons.Default.Check,
                                        contentDescription = "Save"
                                    )
                                }
                            }
                        }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Home Screen Grid Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Home Screen Grid",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Show app grid on home screen with multiple pages",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = launcherPrefs!!.homeScreenGridEnabled,
                        onCheckedChange = { viewModel.toggleHomeScreenGrid(it) }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Distraction Dimming Toggle (Phase 5)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Dim Distracting Apps During Focus",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Apps with high distraction scores appear dimmed during focus sessions",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = launcherPrefs!!.distractionDimmingEnabled,
                        onCheckedChange = { viewModel.updateDistractionDimming(it) }
                    )
                }
            }
        }
    }
}

/**
 * Launcher Onboarding Card (Task 24).
 *
 * Displays onboarding information for the Focus Launcher with:
 * - "Focus Launcher (Beta)" framed as 7-day trial
 * - Switch-back instructions BEFORE enable button
 * - Note about MIUI/HyperOS "Recommended launcher" popup behavior
 * - "Enable" button opening Settings.ACTION_HOME_SETTINGS
 * - "Focus Launcher is active" with "Switch back" button when launcher is default
 *
 * Requirements:
 * - 23.1: Display "Focus Launcher (Beta)" card framed as 7-day trial
 * - 23.2: Show switch-back instructions BEFORE enable button
 * - 23.3: "Enable" button opens Settings.ACTION_HOME_SETTINGS
 * - 23.4: Note MIUI/HyperOS "Recommended launcher" popup behavior
 * - 23.5: Show "Focus Launcher is active" with "Switch back" button when isDefaultLauncher = true
 *
 * @param viewModel LauncherViewModel
 */
@Composable
private fun LauncherOnboardingCard(
    viewModel: LauncherViewModel
) {
    val context = LocalContext.current
    val isDefaultLauncher by viewModel.isDefaultLauncher.collectAsState()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDefaultLauncher) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Focus Launcher (Beta)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isDefaultLauncher) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    }
                )

                if (isDefaultLauncher) {
                    // Show active badge (Requirement 23.5)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = "Active",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (isDefaultLauncher) {
                // Active state (Requirement 23.5)
                Text(
                    text = "Focus Launcher is active",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Text(
                    text = "Your highest-priority task now appears on every phone unlock. The launcher will continue to learn your patterns and help you stay focused.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                // Switch back button (Requirement 23.5)
                OutlinedButton(
                    onClick = {
                        val intent = android.content.Intent(
                            android.provider.Settings.ACTION_HOME_SETTINGS
                        )
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Text("Switch Back to Previous Launcher")
                }
            } else {
                // Onboarding state (Requirements 23.1, 23.2, 23.3, 23.4)

                // Trial framing (Requirement 23.1)
                Text(
                    text = "Try Focus Launcher for 7 days",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )

                Text(
                    text = "Surface your highest-priority task on every phone unlock. Built with neuroscience-informed features to help you stay focused and productive.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f)
                )

                // Switch-back instructions BEFORE enable button (Requirement 23.2)
                Text(
                    text = "How to switch back:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )

                Text(
                    text = "Settings → Apps → Default apps → Home app → select your previous launcher",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(start = 8.dp)
                )

                // MIUI/HyperOS note (Requirement 23.4)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "ℹ️",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "On MIUI/HyperOS devices, a \"Recommended launcher\" popup may appear. This is normal system behavior.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                // Enable button (Requirement 23.3)
                Button(
                    onClick = {
                        // Open home settings to allow user to select launcher
                        val intent = android.content.Intent(
                            android.provider.Settings.ACTION_HOME_SETTINGS
                        )
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Enable Focus Launcher")
                }
            }
        }
    }
}
