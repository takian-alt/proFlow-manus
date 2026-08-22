package com.neuroflow.app.presentation.launcher.drawer

import android.content.Intent
import android.content.pm.LauncherApps
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.neuroflow.app.presentation.launcher.LauncherViewModel
import com.neuroflow.app.presentation.launcher.domain.BiometricAppLock
import com.neuroflow.app.presentation.launcher.components.AppIcon
import com.neuroflow.app.presentation.launcher.data.AppInfo
import java.text.Collator
import java.util.Locale

/**
 * Launch an app with biometric authentication check if locked.
 *
 * @param app App to launch
 * @param isLocked Whether the app is locked
 * @param activity FragmentActivity for biometric prompt
 * @param launcherApps LauncherApps system service
 * @param onRecordLaunch Callback to record launch
 * @param onDismiss Callback to dismiss drawer
 */
private fun launchAppWithBiometricCheck(
    app: AppInfo,
    isLocked: Boolean,
    activity: FragmentActivity,
    launcherApps: LauncherApps,
    onRecordLaunch: () -> Unit,
    onDismiss: () -> Unit
) {
    if (isLocked) {
        // Show biometric prompt before launching (Requirement 15.3)
        BiometricAppLock.authenticate(
            activity = activity,
            onSuccess = {
                // Authentication succeeded - launch app
                onRecordLaunch()
                launcherApps.startMainActivity(
                    android.content.ComponentName(app.packageName, app.className),
                    app.userHandle,
                    null,
                    null
                )
                onDismiss()
            },
            onFailure = {
                // Authentication failed or cancelled - do not launch (Requirement 15.4)
                // BiometricAppLock already shows appropriate toast for LOCKOUT errors (Requirement 15.5)
            }
        )
    } else {
        // Not locked - launch directly
        onRecordLaunch()
        launcherApps.startMainActivity(
            android.content.ComponentName(app.packageName, app.className),
            app.userHandle,
            null,
            null
        )
        onDismiss()
    }
}

private fun AppInfo.drawerItemKey(): String {
    return "$packageName|$className|${userHandle.hashCode()}"
}

/**
 * AppDrawer displays all installed apps in a full-screen overlay with search.
 *
 * Key design constraints:
 * - Uses LauncherViewModel directly (no separate ViewModel)
 * - All filtering happens in LauncherViewModel via combine(allApps, searchQuery) StateFlow
 * - Icons loaded via Coil with memory-only cache from AppRepository LruCache
 * - RTL support: use Arrangement.Start/End and Alignment.Start/End
 * - Badge positioning: TopEnd in LTR, TopStart in RTL
 * - Work apps launched via LauncherApps.startMainActivity with UserHandle
 * - Reuse existing AppIcon composable for all icon rendering
 * - Reuse existing ShortcutPopup composable for context menu
 *
 * Requirements:
 * - 8.1: Slide-up animation with spring (dampingRatio = 0.8f, stiffness = 400f)
 * - 8.2: Auto-focus search bar via FocusRequester on open
 * - 8.3: Filter apps within 100ms per keystroke using combine(allApps, searchQuery) StateFlow
 * - 8.4: Display apps in configurable grid (3, 4, or 5 columns) sorted alphabetically
 * - 8.5: Show "Recent Apps" row (last 4 launched) above alphabetical grid
 * - 8.6: Show shortcuts, "Open", "Pin to Dock", "Hide from Drawer", "App Info" on long-press
 * - 8.7: Show "Remove a dock app first" message when dock is full
 * - 8.8: Add to hidden list and remove immediately when "Hide from Drawer" selected
 * - 8.9: Dismiss on swipe-down or tap outside, clear search query
 * - 8.10: Load icons via Coil with memory-only cache from AppRepository LruCache
 * - 8.11: Display work apps with work badge overlay
 * - 8.12: Launch work apps via LauncherApps.startMainActivity with UserHandle
 * - 8.13: Show notification badges from NotificationBadgeService
 *
 * @param isOpen Whether the drawer is currently open
 * @param onDismiss Callback when drawer is dismissed
 * @param viewModel LauncherViewModel (hoisted from LauncherActivity)
 * @param launcherApps LauncherApps system service for launching apps and shortcuts
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDrawer(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onOpenWalkthrough: () -> Unit = {},
    viewModel: LauncherViewModel = viewModel(),
    launcherApps: LauncherApps
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    // Get FragmentActivity for biometric prompt (Requirement 15.3)
    val activity = context as? FragmentActivity
        ?: throw IllegalStateException("AppDrawer must be used within a FragmentActivity")

    // Collect state from ViewModel
    val filteredApps by viewModel.filteredApps.collectAsState()
    val recentApps by viewModel.recentApps.collectAsState()
    val recentlyInstalledApps by viewModel.recentlyInstalledApps.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val launcherPrefsNullable by viewModel.launcherTheme.collectAsState()
    val badgeCounts by viewModel.badgeCounts.collectAsState()
    val focusActive by viewModel.focusActive.collectAsState()
    val dockApps by viewModel.dockApps.collectAsState()
    val distractionScores by viewModel.distractionScores.collectAsState()
    val homeScreenPages by viewModel.homeScreenPages.collectAsState()

    // State for page picker dialog
    var showPagePickerDialog by remember { mutableStateOf(false) }
    var pendingAddPackage by remember { mutableStateOf<String?>(null) }

    // Helper: trigger page picker for adding app to home screen
    fun addAppToHome(packageName: String) {
        val pagesWithSpace = homeScreenPages.filter { it.items.size < 20 }
        if (pagesWithSpace.isEmpty()) {
            android.widget.Toast.makeText(
                context,
                if (homeScreenPages.isEmpty())
                    "Create a custom page first in Launcher Settings → Home Screen Pages"
                else
                    "All custom pages are full. Add a new page in Launcher Settings.",
                android.widget.Toast.LENGTH_LONG
            ).show()
            return
        }
        pendingAddPackage = packageName
        showPagePickerDialog = true
    }

    // Early return if not open (don't render when closed)
    if (!isOpen) {
        return
    }

    LaunchedEffect(isOpen) {
        if (isOpen) {
            viewModel.refreshDrawerData()
        }
    }

    // Page picker dialog for "Add to Home Screen"
    if (showPagePickerDialog && pendingAddPackage != null) {
        val pagesWithSpace = homeScreenPages.filter { it.items.size < 20 }
        AlertDialog(
            onDismissRequest = {
                showPagePickerDialog = false
                pendingAddPackage = null
            },
            title = { Text("Add to Home Screen") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Choose a page:",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    pagesWithSpace.forEachIndexed { index, page ->
                        val freeSlots = 20 - page.items.size
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            onClick = {
                                val pkg = pendingAddPackage!!
                                val usedPositions = page.items.map { it.gridPosition }.toSet()
                                val freeSlot = (0 until 20).firstOrNull { it !in usedPositions }
                                if (freeSlot != null) {
                                    viewModel.addAppToPage(page.id, pkg, freeSlot)
                                    android.widget.Toast.makeText(
                                        context,
                                        "Added to ${page.name.ifBlank { "Page ${index + 4}" }}",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                                showPagePickerDialog = false
                                pendingAddPackage = null
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    page.name.ifBlank { "Page ${index + 4}" },
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "$freeSlots free",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (index < pagesWithSpace.lastIndex) Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    showPagePickerDialog = false
                    pendingAddPackage = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Early return if theme not loaded yet
    val launcherPrefs = launcherPrefsNullable ?: return

    // Get locked packages from preferences (Requirement 15.7)
    val lockedPackages = launcherPrefs.lockedPackages

    // Drawer columns from preferences (default 4, configurable 3-5)
    val drawerColumns = launcherPrefs.drawerColumns

    val recentlyOpenedApps = remember(recentApps) {
        recentApps.take(4)
    }

    val recentlyInstalledQuickApps = remember(recentlyInstalledApps, recentlyOpenedApps) {
        val openedKeys = recentlyOpenedApps.map { it.drawerItemKey() }.toSet()
        recentlyInstalledApps
            .filterNot { it.drawerItemKey() in openedKeys }
            .take(4)
    }

    var quickAccessMode by rememberSaveable { mutableStateOf("opened") }
    LaunchedEffect(recentlyOpenedApps, recentlyInstalledQuickApps) {
        if (quickAccessMode == "installed" && recentlyInstalledQuickApps.isEmpty()) {
            quickAccessMode = "opened"
        }
    }

    val quickRowApps = remember(quickAccessMode, recentlyOpenedApps, recentlyInstalledQuickApps) {
        when {
            quickAccessMode == "installed" && recentlyInstalledQuickApps.isNotEmpty() -> recentlyInstalledQuickApps
            else -> recentlyOpenedApps
        }
    }
    val showQuickAccess = searchQuery.isBlank() &&
        (recentlyOpenedApps.isNotEmpty() || recentlyInstalledQuickApps.isNotEmpty())

    // Slide-up animation with spring (Requirement 8.1)
    val offsetY by animateDpAsState(
        targetValue = if (isOpen) 0.dp else 1000.dp,
        animationSpec = spring(
            dampingRatio = 0.85f,
            stiffness = 600f
        ),
        label = "drawerOffset"
    )

    // Track drag offset for swipe-down dismiss
    var dragOffset by remember { mutableStateOf(0f) }
    val dismissThreshold = with(density) { 100.dp.toPx() }

    // Note: Auto-focus removed - keyboard opening on drawer open was irritating
    // Users can tap search field if they want to search

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .pointerInput(Unit) {
                // Tap outside to dismiss (Requirement 8.9)
                detectVerticalDragGestures(
                        onDragEnd = {
                            if (dragOffset > dismissThreshold) {
                                onDismiss()
                            }
                            dragOffset = 0f
                        },
                        onVerticalDrag = { _, dragAmount ->
                            if (dragAmount > 0) { // Only track downward drags
                                dragOffset += dragAmount
                            }
                        }
                    )
                }
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(y = offsetY)
                    .padding(top = 48.dp)
                    .testTag("app_drawer"),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier
                                .width(44.dp)
                                .height(4.dp),
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
                        ) {}
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (searchQuery.isBlank()) "App Drawer" else "Search Results",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "${filteredApps.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Search bar (Requirement 8.2)
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search apps") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = "Search"
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = "Clear search"
                                    )
                                }
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        FilledTonalButton(onClick = onOpenWalkthrough) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("2-Min Walkthrough")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val featuredPackages = remember(quickRowApps, searchQuery) {
                        if (searchQuery.isBlank()) {
                            quickRowApps
                                .map { it.drawerItemKey() }
                                .toSet()
                        } else {
                            emptySet()
                        }
                    }

                    // Sort apps (Requirement 19.2, 20.3)
                    val sortedApps = remember(filteredApps, focusActive, distractionScores, featuredPackages) {
                        val collator = Collator.getInstance(Locale.getDefault())
                        val appsForGrid = if (featuredPackages.isEmpty()) {
                            filteredApps
                        } else {
                            filteredApps.filterNot { it.drawerItemKey() in featuredPackages }
                        }

                        if (focusActive) {
                            // Focus mode sorting (Requirement 20.3):
                            // Low-distraction apps (< 40) first, then neutral, then high-distraction (> 70) last
                            appsForGrid.sortedWith(
                                compareBy<AppInfo> { app ->
                                    val score = distractionScores[app.packageName] ?: 50
                                    when {
                                        score < 40 -> 0  // Low distraction - first
                                        score > 70 -> 2  // High distraction - last
                                        else -> 1        // Neutral - middle
                                    }
                                }.thenBy(collator) { it.label } // Then alphabetically within each group
                            )
                        } else {
                            // Normal alphabetical sorting
                            appsForGrid.sortedWith(compareBy(collator) { it.label })
                        }
                    }

                    // Group apps by first letter for section headers (when search is inactive)
                    val groupedApps = remember(sortedApps, searchQuery) {
                        if (searchQuery.isBlank()) {
                            sortedApps.groupBy { it.label.firstOrNull()?.uppercaseChar() ?: '#' }
                        } else {
                            mapOf("" to sortedApps) // No grouping when searching
                        }
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(drawerColumns),
                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.Start),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (showQuickAccess) {
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(drawerColumns) }) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp),
                                    shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 9.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Quick Access",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )

                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                if (recentlyOpenedApps.isNotEmpty()) {
                                                    FilterChip(
                                                        selected = quickAccessMode == "opened",
                                                        onClick = { quickAccessMode = "opened" },
                                                        label = { Text("Opened") },
                                                        leadingIcon = {
                                                            Icon(
                                                                imageVector = Icons.Outlined.Schedule,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    )
                                                }
                                                if (recentlyInstalledQuickApps.isNotEmpty()) {
                                                    FilterChip(
                                                        selected = quickAccessMode == "installed",
                                                        onClick = { quickAccessMode = "installed" },
                                                        label = { Text("Installed") },
                                                        leadingIcon = {
                                                            Icon(
                                                                imageVector = Icons.Outlined.NewReleases,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.Start)) {
                                            items(quickRowApps, key = { it.drawerItemKey() }) { app ->
                                                val isLocked = app.packageName in lockedPackages

                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    modifier = Modifier.width(64.dp)
                                                ) {
                                                    AppIcon(
                                                        appInfo = app,
                                                        launcherApps = launcherApps,
                                                        badgeCount = badgeCounts[app.packageName] ?: 0,
                                                        isLocked = isLocked,
                                                        focusActive = focusActive,
                                                        onTap = {
                                                            launchAppWithBiometricCheck(
                                                                app = app,
                                                                isLocked = isLocked,
                                                                activity = activity,
                                                                launcherApps = launcherApps,
                                                                onRecordLaunch = { viewModel.recordLaunch(app.packageName) },
                                                                onDismiss = onDismiss
                                                            )
                                                        },
                                                        onPinToDock = {
                                                            if (dockApps.size >= 5) {
                                                                android.widget.Toast.makeText(context, "Remove a dock app first", android.widget.Toast.LENGTH_SHORT).show()
                                                            } else {
                                                                viewModel.pinToDock(app.packageName)
                                                            }
                                                        },
                                                        onHide = { viewModel.hideApp(app.packageName) },
                                                        onLock = { viewModel.lockApp(app.packageName) },
                                                        onAddToHome = { addAppToHome(app.packageName) }
                                                    )

                                                    Spacer(modifier = Modifier.height(2.dp))

                                                    Text(
                                                        text = app.label,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        maxLines = 1,
                                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Alphabetical app grid heading (Requirement 8.4)
                        if (searchQuery.isBlank()) {
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(drawerColumns) }) {
                                Text(
                                    text = "All Apps",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                        }

                        // Focus mode banner (Requirement 20.4)
                        if (focusActive && searchQuery.isBlank()) {
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(drawerColumns) }) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                ) {
                                    Text(
                                        text = "Focus session active. Distraction apps are sorted last.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                        }

                        groupedApps.forEach { (section, apps) ->
                            // Section header (only when search is inactive)
                            if (searchQuery.isBlank() && section.toString().isNotEmpty()) {
                                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(drawerColumns) }) {
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = section.toString(),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            // App icons
                            items(apps) { app ->
                                val isLocked = app.packageName in lockedPackages

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    AppIcon(
                                        appInfo = app,
                                        launcherApps = launcherApps,
                                        badgeCount = badgeCounts[app.packageName] ?: 0,
                                        isLocked = isLocked,
                                        focusActive = focusActive,
                                        onTap = {
                                            launchAppWithBiometricCheck(
                                                app = app,
                                                isLocked = isLocked,
                                                activity = activity,
                                                launcherApps = launcherApps,
                                                onRecordLaunch = { viewModel.recordLaunch(app.packageName) },
                                                onDismiss = onDismiss
                                            )
                                        },
                                        onPinToDock = {
                                            if (dockApps.size >= 5) {
                                                android.widget.Toast.makeText(context, "Remove a dock app first", android.widget.Toast.LENGTH_SHORT).show()
                                            } else {
                                                viewModel.pinToDock(app.packageName)
                                            }
                                        },
                                        onHide = { viewModel.hideApp(app.packageName) },
                                        onLock = { viewModel.lockApp(app.packageName) },
                                        onAddToHome = { addAppToHome(app.packageName) }
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = app.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 2,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
}
