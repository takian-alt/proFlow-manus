package com.neuroflow.app.presentation.launcher.components

import android.content.Intent
import android.content.pm.LauncherApps
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.fragment.app.FragmentActivity
import com.neuroflow.app.presentation.launcher.LauncherViewModel
import com.neuroflow.app.presentation.launcher.data.AppInfo
import com.neuroflow.app.presentation.launcher.data.AppRepository
import com.neuroflow.app.presentation.launcher.domain.BiometricAppLock
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.abs

/**
 * Dock row composable displaying 4-5 pinned app icons.
 *
 * Features:
 * - Displays app icons from PinnedAppsDataStore via LauncherViewModel.dockApps
 * - Loads icons via AppIcon composable with notification badges
 * - Supports drag-and-drop reordering, persists to PinnedAppsDataStore immediately
 * - Applies themed icons from IconPackManager when icon pack selected
 * - Dims icons with distractionScore > 70 to 40% opacity when focusActive = true
 * - On tap: launches app via AppRepository.launchApp(packageName, userHandle)
 * - On long-press without drag: shows shortcut context menu with "Remove from Dock" and "App Info"
 * - On long-press with drag: initiates drag-and-drop reordering
 * - Removes from dock on uninstall within 2 seconds (handled by PackageChangeReceiver)
 *
 * Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7, 7.8, 7.9, 8.7
 *
 * @param viewModel LauncherViewModel providing dockApps, badgeCounts, focusActive, and actions
 * @param appRepository AppRepository for launching apps
 * @param launcherApps LauncherApps system service for shortcuts
 * @param snackbarHostState SnackbarHostState for showing messages
 * @param modifier Modifier for the dock row container
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DockRow(
    viewModel: LauncherViewModel,
    appRepository: AppRepository,
    launcherApps: LauncherApps,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Get FragmentActivity for biometric prompt (Requirement 15.3)
    val activity = context as? FragmentActivity
        ?: throw IllegalStateException("DockRow must be used within a FragmentActivity")

    // Collect state from ViewModel
    val dockApps by viewModel.dockApps.collectAsState()
    val badgeCounts by viewModel.badgeCounts.collectAsState()
    val focusActive by viewModel.focusActive.collectAsState()
    val launcherPrefs by viewModel.launcherTheme.collectAsState()

    // Get locked packages from preferences (Requirement 15.7)
    val lockedPackages = launcherPrefs?.lockedPackages ?: emptySet()

    // Drag-and-drop state
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var targetIndex by remember { mutableStateOf<Int?>(null) }
    var hasDragged by remember { mutableStateOf(false) }

    // Show context menu state
    var showContextMenu by remember { mutableStateOf(false) }
    var contextMenuApp by remember { mutableStateOf<AppInfo?>(null) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("dock_row"),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            dockApps.forEachIndexed { index, appInfo ->
                val isDragging = draggedIndex == index
                val isLocked = appInfo.packageName in lockedPackages

                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .then(
                            if (isDragging) {
                                Modifier
                                    .offset {
                                        IntOffset(
                                            dragOffset.x.roundToInt(),
                                            dragOffset.y.roundToInt()
                                        )
                                    }
                                    .zIndex(1f)
                            } else {
                                Modifier
                            }
                        )
                        .pointerInput(dockApps.size) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggedIndex = index
                                    dragOffset = Offset.Zero
                                    hasDragged = false
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffset += dragAmount

                                    // Mark as dragged if moved more than 10dp
                                    if (abs(dragOffset.x) > 10f || abs(dragOffset.y) > 10f) {
                                        hasDragged = true
                                    }

                                    // Calculate target index based on drag position
                                    if (hasDragged) {
                                        val iconWidth = with(density) { 72.dp.toPx() }
                                        val offsetIndex = (dragOffset.x / iconWidth).roundToInt()
                                        val newIndex = (index + offsetIndex).coerceIn(0, dockApps.size - 1)
                                        targetIndex = if (newIndex != index) newIndex else null
                                    }
                                },
                                onDragEnd = {
                                    if (hasDragged) {
                                        // Reorder dock apps if dragged
                                        if (targetIndex != null && targetIndex != index) {
                                            val newOrder = dockApps.toMutableList()
                                            val item = newOrder.removeAt(index)
                                            newOrder.add(targetIndex!!, item)
                                            viewModel.reorderDock(newOrder.map { it.packageName })
                                        }
                                    } else {
                                        // Show context menu if not dragged (long press without drag)
                                        contextMenuApp = appInfo
                                        showContextMenu = true
                                    }

                                    // Reset drag state
                                    draggedIndex = null
                                    dragOffset = Offset.Zero
                                    targetIndex = null
                                    hasDragged = false
                                },
                                onDragCancel = {
                                    // Reset drag state
                                    draggedIndex = null
                                    dragOffset = Offset.Zero
                                    targetIndex = null
                                    hasDragged = false
                                }
                            )
                        }
                ) {
                    AppIcon(
                        appInfo = appInfo,
                        launcherApps = launcherApps,
                        badgeCount = badgeCounts[appInfo.packageName] ?: 0,
                        isLocked = isLocked,
                        focusActive = focusActive,
                        modifier = Modifier.size(56.dp),
                        onTap = {
                            // Launch app with biometric check if locked (Requirement 15.3, 15.4)
                            if (isLocked) {
                                BiometricAppLock.authenticate(
                                    activity = activity,
                                    onSuccess = {
                                        // Authentication succeeded - launch app
                                        scope.launch {
                                            appRepository.launchApp(appInfo.packageName, appInfo.userHandle)
                                        }
                                    },
                                    onFailure = {
                                        // Authentication failed or cancelled - do not launch (Requirement 15.4)
                                        // BiometricAppLock already shows appropriate toast for LOCKOUT errors (Requirement 15.5)
                                    }
                                )
                            } else {
                                // Not locked - launch directly
                                scope.launch {
                                    appRepository.launchApp(appInfo.packageName, appInfo.userHandle)
                                }
                            }
                        },
                        onPinToDock = {
                            // Already in dock, no action needed
                        },
                        onHide = {
                            // Hide app from drawer
                            viewModel.hideApp(appInfo.packageName)
                        },
                        onLock = {
                            // Lock app with biometric authentication (Requirement 15.2)
                            viewModel.lockApp(appInfo.packageName)
                        }
                    )
                }
            }
        }

        // Context menu (shown as a simple dialog for now)
        if (showContextMenu && contextMenuApp != null) {
            // TODO: Implement proper context menu with shortcuts
            // For now, we'll just show a simple action menu
            androidx.compose.material3.AlertDialog(
                onDismissRequest = {
                    showContextMenu = false
                    contextMenuApp = null
                },
                title = {
                    androidx.compose.material3.Text(contextMenuApp!!.label)
                },
                text = {
                    androidx.compose.foundation.layout.Column {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                // Remove from dock
                                viewModel.removeFromDock(contextMenuApp!!.packageName)
                                showContextMenu = false
                                contextMenuApp = null
                            }
                        ) {
                            androidx.compose.material3.Text("Remove from Dock")
                        }

                        androidx.compose.material3.TextButton(
                            onClick = {
                                // Open app info
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = android.net.Uri.fromParts("package", contextMenuApp!!.packageName, null)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                                showContextMenu = false
                                contextMenuApp = null
                            }
                        ) {
                            androidx.compose.material3.Text("App Info")
                        }
                    }
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            showContextMenu = false
                            contextMenuApp = null
                        }
                    ) {
                        androidx.compose.material3.Text("Cancel")
                    }
                }
            )
        }
    }
}
