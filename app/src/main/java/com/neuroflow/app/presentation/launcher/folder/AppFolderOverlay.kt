package com.neuroflow.app.presentation.launcher.folder

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.neuroflow.app.presentation.launcher.components.AppIcon
import com.neuroflow.app.presentation.launcher.data.AppInfo
import com.neuroflow.app.presentation.launcher.data.FolderDefinition

/**
 * Full-screen overlay displaying folder contents.
 *
 * Features:
 * - Scrollable grid of apps in the folder
 * - Editable folder name at the top
 * - Drag-out to remove app from folder
 * - Auto-dissolve when only 1 app remains
 * - Dismiss on tap outside or back button
 *
 * Requirements: 4.1, 4.2, 4.3, 4.4, 4.5
 *
 * @param folder The folder definition to display
 * @param apps List of all installed apps (for resolving package names)
 * @param launcherApps LauncherApps system service for shortcuts
 * @param badgeCounts Map of package name to notification badge count
 * @param focusActive Whether focus mode is currently active
 * @param onDismiss Callback when overlay should be dismissed
 * @param onAppLaunch Callback when an app in the folder is tapped
 * @param onAppLongPress Callback when an app is long-pressed
 * @param onRemoveApp Callback when an app is dragged out of the folder
 * @param onRenameFolder Callback when folder name is changed
 * @param onPinToDock Callback to pin app to dock
 * @param onHide Callback to hide app from drawer
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppFolderOverlay(
    folder: FolderDefinition,
    apps: List<AppInfo>,
    launcherApps: android.content.pm.LauncherApps,
    badgeCounts: Map<String, Int>,
    focusActive: Boolean,
    onDismiss: () -> Unit,
    onAppLaunch: (String) -> Unit,
    onAppLongPress: (AppInfo) -> Unit,
    onRemoveApp: (String) -> Unit,
    onRenameFolder: (String) -> Unit,
    onPinToDock: (String) -> Unit,
    onHide: (String) -> Unit
) {
    var folderName by remember(folder.name) { mutableStateOf(folder.name) }
    var isEditingName by remember { mutableStateOf(false) }
    var draggedPackage by remember { mutableStateOf<String?>(null) }

    // Resolve apps in folder
    val folderApps = remember(folder.packages, apps) {
        folder.packages.mapNotNull { packageName ->
            apps.firstOrNull { it.packageName == packageName }
        }
    }

    // Auto-dissolve if only 1 app remains (Requirement 4.5)
    LaunchedEffect(folderApps.size) {
        if (folderApps.size <= 1) {
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .clickable(onClick = onDismiss)
        ) {
            // Folder content card
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .fillMaxHeight(0.8f)
                    .align(Alignment.Center)
                    .clickable(enabled = false) { /* Prevent dismiss on card click */ },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Editable folder name (Requirement 4.3)
                    if (isEditingName) {
                        OutlinedTextField(
                            value = folderName,
                            onValueChange = { folderName = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.headlineSmall,
                            trailingIcon = {
                                TextButton(onClick = {
                                    onRenameFolder(folderName)
                                    isEditingName = false
                                }) {
                                    Text("Done")
                                }
                            }
                        )
                    } else {
                        Text(
                            text = folderName,
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isEditingName = true }
                                .padding(bottom = 16.dp),
                            textAlign = TextAlign.Center
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(bottom = 16.dp))

                    // Scrollable grid of apps (Requirement 4.1, 4.3)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(folderApps, key = { it.packageName }) { app ->
                            var isDragging by remember { mutableStateOf(false) }
                            var dragOffset by remember { mutableStateOf(0f to 0f) }

                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .pointerInput(Unit) {
                                        detectDragGestures(
                                            onDragStart = {
                                                isDragging = true
                                                draggedPackage = app.packageName
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragOffset = dragOffset.first + dragAmount.x to
                                                        dragOffset.second + dragAmount.y
                                            },
                                            onDragEnd = {
                                                // Drag-out to remove (Requirement 4.4)
                                                // If dragged significantly outside the card, remove
                                                if (kotlin.math.abs(dragOffset.second) > 200f) {
                                                    onRemoveApp(app.packageName)
                                                }
                                                isDragging = false
                                                draggedPackage = null
                                                dragOffset = 0f to 0f
                                            },
                                            onDragCancel = {
                                                isDragging = false
                                                draggedPackage = null
                                                dragOffset = 0f to 0f
                                            }
                                        )
                                    }
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    AppIcon(
                                        appInfo = app,
                                        launcherApps = launcherApps,
                                        badgeCount = badgeCounts[app.packageName] ?: 0,
                                        focusActive = focusActive,
                                        modifier = Modifier.size(48.dp),
                                        onTap = { onAppLaunch(app.packageName) },
                                        onPinToDock = { onPinToDock(app.packageName) },
                                        onHide = { onHide(app.packageName) }
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = app.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                // Visual feedback during drag
                                if (isDragging) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                                RoundedCornerShape(8.dp)
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Folder icon preview showing first 4 apps in a 2x2 grid.
 *
 * Requirements: 4.2
 *
 * @param folder The folder definition
 * @param apps List of all installed apps
 * @param launcherApps LauncherApps system service for shortcuts
 * @param modifier Modifier for the folder icon
 * @param onTap Callback when folder icon is tapped
 * @param onLongPress Callback when folder icon is long-pressed
 */
@Composable
fun FolderIcon(
    folder: FolderDefinition,
    apps: List<AppInfo>,
    launcherApps: android.content.pm.LauncherApps,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val context = LocalContext.current

    // Get first 4 apps for preview (Requirement 4.2)
    val previewApps = remember(folder.packages, apps) {
        folder.packages.take(4).mapNotNull { packageName ->
            apps.firstOrNull { it.packageName == packageName }
        }
    }

    Box(
        modifier = modifier
            .size(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onTap)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { /* Handle folder drag */ },
                    onDrag = { _, _ -> /* Handle folder drag */ },
                    onDragEnd = { /* Handle folder drop */ }
                )
            }
    ) {
        // 2x2 grid preview of first 4 apps
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                previewApps.getOrNull(0)?.let { app ->
                    AppIcon(
                        appInfo = app,
                        launcherApps = launcherApps,
                        modifier = Modifier.size(24.dp),
                        onTap = {},
                        onPinToDock = {},
                        onHide = {}
                    )
                }
                previewApps.getOrNull(1)?.let { app ->
                    AppIcon(
                        appInfo = app,
                        launcherApps = launcherApps,
                        modifier = Modifier.size(24.dp),
                        onTap = {},
                        onPinToDock = {},
                        onHide = {}
                    )
                }
            }
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                previewApps.getOrNull(2)?.let { app ->
                    AppIcon(
                        appInfo = app,
                        launcherApps = launcherApps,
                        modifier = Modifier.size(24.dp),
                        onTap = {},
                        onPinToDock = {},
                        onHide = {}
                    )
                }
                previewApps.getOrNull(3)?.let { app ->
                    AppIcon(
                        appInfo = app,
                        launcherApps = launcherApps,
                        modifier = Modifier.size(24.dp),
                        onTap = {},
                        onPinToDock = {},
                        onHide = {}
                    )
                }
            }
        }

        // Folder name label
        Text(
            text = folder.name,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                    RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 4.dp, vertical = 2.dp),
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}
