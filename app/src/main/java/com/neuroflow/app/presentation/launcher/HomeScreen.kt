package com.neuroflow.app.presentation.launcher

import android.content.Intent
import android.content.pm.LauncherApps
import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.neuroflow.app.presentation.launcher.components.*
import com.neuroflow.app.presentation.launcher.data.AppRepository
import com.neuroflow.app.presentation.launcher.domain.LauncherGestureHandler
import com.neuroflow.app.presentation.launcher.hyperfocus.HyperFocusViewModel
import com.neuroflow.app.presentation.launcher.hyperfocus.components.HyperFocusStatusBar
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars

/**
 * HomeScreen - Root composable for the launcher home screen.
 *
 * Responsible for selecting the correct layout based on window size and orientation:
 * - PortraitLayout: Default phone portrait layout
 * - LandscapeLayout: Phone landscape layout
 * - TwoColumnLayout: Foldable unfolded / tablet layout
 *
 * Implements HorizontalPager with 3 pages:
 * - Left (index 0): FutureLeftPage placeholder
 * - Center (index 1): Active home screen
 * - Right (index 2): FutureRightPage placeholder
 *
 * Requirements: 2.1, 2.2, 2.3, 2.12, 2.13, 2.14, 26.1
 *
 * @param windowSizeClass WindowSizeClass computed in LauncherActivity
 * @param viewModel LauncherViewModel providing all state
 * @param gestureHandler LauncherGestureHandler for gesture detection
 * @param modifier Modifier for the home screen container
 */
/**
 * Page indices:
 *   0 = Left (fixed)
 *   1 = Main/Center (fixed)
 *   2 = Stats/Right (fixed)
 *   3..N = Extra pages from homeScreenPages datastore (user-created, deletable)
 *
 * homeScreenPages in the datastore holds ONLY the extra pages (index 3+).
 * Total pages = 3 + extraPages.size  (max 10 total = 7 extra).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    windowSizeClass: WindowSizeClass,
    viewModel: LauncherViewModel,
    gestureHandler: LauncherGestureHandler,
    onRegisterNavigateHome: ((() -> Unit)) -> Unit = {},
    onPageChanged: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val extraPages by viewModel.homeScreenPages.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val totalPages = 3 + extraPages.size
    val pagerState = rememberPagerState(
        initialPage = 1,
        pageCount = { totalPages }
    )

    // Register navigate-home callback so activity can trigger it on back/home press
    LaunchedEffect(Unit) {
        onRegisterNavigateHome {
            scope.launch {
                if (pagerState.currentPage != 1) {
                    pagerState.animateScrollToPage(1)
                }
            }
        }
    }

    // Report page changes to activity so back callback can be toggled
    LaunchedEffect(pagerState.currentPage) {
        onPageChanged(pagerState.currentPage)
    }

    val layoutMode = when {
        windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded -> LayoutMode.TWO_COLUMN
        configuration.orientation == Configuration.ORIENTATION_LANDSCAPE -> LayoutMode.LANDSCAPE
        else -> LayoutMode.PORTRAIT
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        HorizontalPager(
            state = pagerState,
            flingBehavior = PagerDefaults.flingBehavior(
                state = pagerState,
                snapPositionalThreshold = 0.45f
            ),
            modifier = Modifier
                .fillMaxSize()
                .testTag("home_screen")
                .then(with(gestureHandler) { Modifier.attachGestures() })
        ) { page ->
            when (page) {
                0 -> LeftPage(viewModel = viewModel)
                1 -> ActiveHomePage(layoutMode, viewModel, gestureHandler, pageData = null)
                2 -> RightPage(viewModel = viewModel)
                else -> {
                    val extraPage = extraPages.getOrNull(page - 3)
                    ActiveHomePage(layoutMode, viewModel, gestureHandler, pageData = extraPage)
                }
            }
        }

        // Page indicators — above dock, below nav bar
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 88.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(totalPages) { index ->
                Box(
                    modifier = Modifier
                        .size(if (index == pagerState.currentPage) 8.dp else 6.dp)
                        .background(
                            color = if (index == pagerState.currentPage)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            shape = CircleShape
                        )
                )
            }
        }
    }
}

/**
 * Layout mode enum for selecting the appropriate layout.
 */
private enum class LayoutMode {
    PORTRAIT,
    LANDSCAPE,
    TWO_COLUMN
}

/**
 * Active home page - renders the main layout.
 * pageData = null means the fixed Main page (no extra app grid).
 * pageData = HomeScreenPage means an extra user-created page with its own app grid.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActiveHomePage(
    layoutMode: LayoutMode,
    viewModel: LauncherViewModel,
    gestureHandler: LauncherGestureHandler,
    pageData: com.neuroflow.app.presentation.launcher.data.HomeScreenPage?
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val launcherApps = remember {
        context.getSystemService(android.content.Context.LAUNCHER_APPS_SERVICE) as android.content.pm.LauncherApps
    }
    when (layoutMode) {
        LayoutMode.PORTRAIT -> PortraitLayout(viewModel, gestureHandler, launcherApps, snackbarHostState, pageData)
        LayoutMode.LANDSCAPE -> LandscapeLayout(viewModel, launcherApps, snackbarHostState)
        LayoutMode.TWO_COLUMN -> TwoColumnLayout(viewModel, launcherApps, snackbarHostState)
    }
}

/**
 * Portrait layout - default phone portrait layout.
 *
 * Components top-to-bottom:
 * - DateTimeDisplay
 * - FocusTaskCard
 * - HabitQuickRow
 * - HomeScreenPageGrid (for current page)
 * - Add Page button (on last home page)
 * - DockRow
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PortraitLayout(
    viewModel: LauncherViewModel,
    gestureHandler: LauncherGestureHandler,
    launcherApps: android.content.pm.LauncherApps,
    snackbarHostState: SnackbarHostState,
    pageData: com.neuroflow.app.presentation.launcher.data.HomeScreenPage?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Collect state
    val topTask by viewModel.topTask.collectAsStateWithLifecycle()
    val ulyssesContract by viewModel.topTaskUlyssesContract.collectAsStateWithLifecycle()
    val woopEntity by viewModel.topTaskWoopEntity.collectAsStateWithLifecycle()
    val habitTasks by viewModel.habitTasks.collectAsStateWithLifecycle()
    val focusActive by viewModel.focusActive.collectAsStateWithLifecycle()
    val focusElapsedSeconds by viewModel.focusElapsedSeconds.collectAsStateWithLifecycle()
    val allActiveTasks by viewModel.allActiveTasks.collectAsStateWithLifecycle()
    val allApps by viewModel.allApps.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val badgeCounts by viewModel.badgeCounts.collectAsStateWithLifecycle()
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle(initialValue = null)
    val appRepository = viewModel.getAppRepository()

    // HyperFocus state — Requirements: 6.2
    val hyperFocusViewModel: HyperFocusViewModel = hiltViewModel()
    val hyperFocusPrefs by hyperFocusViewModel.hyperFocusPrefs.collectAsStateWithLifecycle()
    val hyperFocusProgress by hyperFocusViewModel.progress.collectAsStateWithLifecycle()
    val unlockSecondsRemaining by hyperFocusViewModel.unlockSecondsRemaining.collectAsStateWithLifecycle()

    var selectedFolder by remember { mutableStateOf<com.neuroflow.app.presentation.launcher.data.FolderDefinition?>(null) }
    var showFolderOverlay by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Only show clock/task/habits on the main page (pageData == null)
        if (pageData == null) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DateTimeDisplay(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(with(gestureHandler) { Modifier.attachSwipeDown() })
                )
                HyperFocusStatusBar(
                    prefs = hyperFocusPrefs,
                    progress = hyperFocusProgress,
                    unlockSecondsRemaining = unlockSecondsRemaining,
                    onRewardsClick = {
                        (context as? LauncherActivity)?.let { it.isRewardsOpen = true }
                    },
                    onPlanningClick = {
                        (context as? LauncherActivity)?.let { it.isPlanningOpen = true }
                    }
                )
                FocusTaskCard(
                    topTask = topTask,
                    ulyssesContract = ulyssesContract,
                    woopEntity = woopEntity,
                    focusActive = focusActive,
                    focusElapsedSeconds = focusElapsedSeconds,
                    hasActiveTasks = allActiveTasks.isNotEmpty(),
                    prefs = userPreferences,
                    onSkip = { taskId -> viewModel.skipTask(taskId) },
                    onStartFocus = { taskId ->
                        val intent = Intent(context, com.neuroflow.app.MainActivity::class.java).apply {
                            action = "com.procus.ACTION_OPEN_FOCUS"
                            putExtra("task_id", taskId)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    },
                    onStopFocus = { viewModel.stopFocusSession() },
                    onClearSkipped = { viewModel.clearSkippedTasks() },
                    modifier = Modifier.fillMaxWidth()
                )
                HabitQuickRow(
                    habitTasks = habitTasks,
                    onCompleteHabit = { task -> viewModel.completeHabit(task) },
                    modifier = Modifier.fillMaxWidth()
                )
                WidgetSlotRow(
                    widgets = emptyList<WidgetSlot>(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.weight(1f))
        } else {
            // Extra page: header + grid fills all available space above dock
            Text(
                text = pageData.name.ifBlank { "Page" },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            HomeScreenPageGrid(
                page = pageData,
                allApps = allApps,
                folders = folders,
                badgeCounts = badgeCounts,
                focusActive = focusActive,
                launcherApps = launcherApps,
                onAppTap = { app ->
                    scope.launch { appRepository.launchApp(app.packageName, app.userHandle) }
                },
                onFolderTap = { folder ->
                    selectedFolder = folder
                    showFolderOverlay = true
                },
                onRemoveItem = { position ->
                    viewModel.removeItemFromPage(pageData.id, position)
                },
                onMoveItem = { from, to ->
                    viewModel.moveItemInPage(pageData.id, from, to)
                },
                isDraggingIcon = viewModel.isDraggingIcon,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }

        DockRow(
            viewModel = viewModel,
            appRepository = appRepository,
            launcherApps = launcherApps,
            snackbarHostState = snackbarHostState,
            modifier = Modifier
                .fillMaxWidth()
                .then(with(gestureHandler) { Modifier.attachSwipeUp() })
        )
    }

    if (showFolderOverlay && selectedFolder != null) {
        com.neuroflow.app.presentation.launcher.folder.AppFolderOverlay(
            folder = selectedFolder!!,
            apps = allApps,
            launcherApps = launcherApps,
            badgeCounts = badgeCounts,
            focusActive = focusActive,
            onDismiss = { showFolderOverlay = false },
            onAppLaunch = { packageName ->
                val app = allApps.firstOrNull { it.packageName == packageName }
                app?.let { scope.launch { appRepository.launchApp(it.packageName, it.userHandle) } }
            },
            onAppLongPress = { },
            onRemoveApp = { packageName -> viewModel.removeAppFromFolder(selectedFolder!!.id, packageName) },
            onRenameFolder = { newName -> viewModel.renameFolder(selectedFolder!!.id, newName) },
            onPinToDock = { packageName -> viewModel.pinToDock(packageName) },
            onHide = { packageName -> viewModel.hideApp(packageName) }
        )
    }
}
/**
 * Landscape layout - phone landscape layout.
 *
 * Layout:
 * - DateTimeDisplay spanning full width at top
 * - FocusTaskCard on left half
 * - HabitQuickRow + DockRow on right half
 */
@Composable
private fun LandscapeLayout(
    viewModel: LauncherViewModel,
    launcherApps: android.content.pm.LauncherApps,
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current

    // Collect state
    val topTask by viewModel.topTask.collectAsStateWithLifecycle()
    val ulyssesContract by viewModel.topTaskUlyssesContract.collectAsStateWithLifecycle()
    val woopEntity by viewModel.topTaskWoopEntity.collectAsStateWithLifecycle()
    val habitTasks by viewModel.habitTasks.collectAsStateWithLifecycle()
    val focusActive by viewModel.focusActive.collectAsStateWithLifecycle()
    val focusElapsedSeconds by viewModel.focusElapsedSeconds.collectAsStateWithLifecycle()
    val allActiveTasks by viewModel.allActiveTasks.collectAsStateWithLifecycle()
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle(initialValue = null)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // DateTimeDisplay spanning full width
        DateTimeDisplay(
            modifier = Modifier.fillMaxWidth()
        )

        // Two-column content
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Left half - FocusTaskCard
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Top
            ) {
                FocusTaskCard(
                    topTask = topTask,
                    ulyssesContract = ulyssesContract,
                    woopEntity = woopEntity,
                    focusActive = focusActive,
                    focusElapsedSeconds = focusElapsedSeconds,
                    hasActiveTasks = allActiveTasks.isNotEmpty(),
                    prefs = userPreferences,
                    onSkip = { taskId -> viewModel.skipTask(taskId) },
                    onStartFocus = { taskId ->
                        val intent = Intent(context, com.neuroflow.app.MainActivity::class.java).apply {
                            action = "com.procus.ACTION_OPEN_FOCUS"
                            putExtra("task_id", taskId)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    },
                    onStopFocus = { viewModel.stopFocusSession() },
                    onClearSkipped = { viewModel.clearSkippedTasks() },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Right half - HabitQuickRow + DockRow
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                HabitQuickRow(
                    habitTasks = habitTasks,
                    onCompleteHabit = { task -> viewModel.completeHabit(task) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.weight(1f))

                DockRow(
                    viewModel = viewModel,
                    appRepository = viewModel.getAppRepository(),
                    launcherApps = launcherApps,
                    snackbarHostState = snackbarHostState,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Two-column layout - foldable unfolded / tablet layout.
 *
 * Layout:
 * - FocusTaskCard in left column
 * - HabitQuickRow + QuickStatsPanel inline in right column
 */
@Composable
private fun TwoColumnLayout(
    viewModel: LauncherViewModel,
    launcherApps: android.content.pm.LauncherApps,
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current

    // Collect state
    val topTask by viewModel.topTask.collectAsStateWithLifecycle()
    val ulyssesContract by viewModel.topTaskUlyssesContract.collectAsStateWithLifecycle()
    val woopEntity by viewModel.topTaskWoopEntity.collectAsStateWithLifecycle()
    val habitTasks by viewModel.habitTasks.collectAsStateWithLifecycle()
    val focusActive by viewModel.focusActive.collectAsStateWithLifecycle()
    val focusElapsedSeconds by viewModel.focusElapsedSeconds.collectAsStateWithLifecycle()
    val allActiveTasks by viewModel.allActiveTasks.collectAsStateWithLifecycle()
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle(initialValue = null)

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Left column - FocusTaskCard
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Top
        ) {
            FocusTaskCard(
                topTask = topTask,
                ulyssesContract = ulyssesContract,
                woopEntity = woopEntity,
                focusActive = focusActive,
                focusElapsedSeconds = focusElapsedSeconds,
                hasActiveTasks = allActiveTasks.isNotEmpty(),
                prefs = userPreferences,
                onSkip = { taskId -> viewModel.skipTask(taskId) },
                onStartFocus = { taskId ->
                    val intent = Intent(context, com.neuroflow.app.MainActivity::class.java).apply {
                        action = "com.procus.ACTION_OPEN_FOCUS"
                        putExtra("task_id", taskId)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                },
                onStopFocus = { viewModel.stopFocusSession() },
                onClearSkipped = { viewModel.clearSkippedTasks() },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Right column - HabitQuickRow + QuickStatsPanel inline
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HabitQuickRow(
                habitTasks = habitTasks,
                onCompleteHabit = { task -> viewModel.completeHabit(task) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.weight(1f))

            // QuickStatsPanel placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Quick Stats Panel\n(Coming in future task)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
