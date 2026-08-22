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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.neuroflow.app.presentation.launcher.components.*
import com.neuroflow.app.presentation.launcher.data.AppRepository
import com.neuroflow.app.presentation.launcher.domain.LauncherGestureHandler
import com.neuroflow.app.presentation.launcher.hyperfocus.HyperFocusViewModel
import com.neuroflow.app.presentation.launcher.hyperfocus.components.HyperFocusStatusBar
import kotlinx.coroutines.launch
import kotlin.random.Random
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
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
 * - Left (index 0): Focus Space (left panel)
 * - Center (index 1): Quotes panel (inspirational, science-based)
 * - Right (index 2): Main launcher page (app grid + task/clock)
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
 *   1 = Quotes/Center (fixed)
 *   2 = Main content/Right (fixed)
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
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val launcherApps = remember {
        context.getSystemService(android.content.Context.LAUNCHER_APPS_SERVICE) as android.content.pm.LauncherApps
    }

    val totalPages = 3 + extraPages.size
    val indicatorBottomPadding = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding() + 72.dp
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
                1 -> QuotePage(
                    viewModel = viewModel,
                    launcherApps = launcherApps,
                    snackbarHostState = snackbarHostState,
                    gestureHandler = gestureHandler,
                    isVisible = pagerState.currentPage == page
                )
                2 -> ActiveHomePage(layoutMode, viewModel, gestureHandler, pageData = null, showDateTime = false)
                else -> {
                    val extraPage = extraPages.getOrNull(page - 3)
                    ActiveHomePage(layoutMode, viewModel, gestureHandler, pageData = extraPage, showDateTime = false)
                }
            }
        }

        // Page indicators — above dock, below nav bar
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = indicatorBottomPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(totalPages) { index ->
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .semantics { contentDescription = "Go to page ${index + 1}" }
                        .clickable {
                            scope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
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
    pageData: com.neuroflow.app.presentation.launcher.data.HomeScreenPage?,
    showDateTime: Boolean = true
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val launcherApps = remember {
        context.getSystemService(android.content.Context.LAUNCHER_APPS_SERVICE) as android.content.pm.LauncherApps
    }
    when (layoutMode) {
        LayoutMode.PORTRAIT -> PortraitLayout(viewModel, gestureHandler, launcherApps, snackbarHostState, pageData, showDateTime)
        LayoutMode.LANDSCAPE -> LandscapeLayout(viewModel, launcherApps, snackbarHostState)
        LayoutMode.TWO_COLUMN -> TwoColumnLayout(viewModel, launcherApps, snackbarHostState)
    }
}

/**
 * Center (quote) page — quick science-backed influence quotes.
 */
@Composable
private fun QuotePage(
    viewModel: LauncherViewModel,
    launcherApps: android.content.pm.LauncherApps,
    snackbarHostState: SnackbarHostState,
    gestureHandler: LauncherGestureHandler,
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    val quotes = listOf(
        "A small daily improvement leads to huge long-term results. — James Clear",
        "Attention is the rarest and purest form of generosity. — Simone Weil",
        "When you want to change your habits, focus not on what you want to achieve, but on who you wish to become. — James Clear",
        "People with clear goals and consistent focus get more done than people with more talent. — Cal Newport",
        "The best time to plant a tree was 20 years ago. The second best time is now. — Chinese Proverb",
        "The secret of getting ahead is getting started. — Mark Twain",
        "Well begun is half done. — Aristotle",
        "We are what we repeatedly do. Excellence is a habit. — Will Durant",
        "Knowing yourself is the beginning of all wisdom. — Aristotle",
        "He who has a why to live can bear almost any how. — Friedrich Nietzsche",
        "The future depends on what you do today. — Mahatma Gandhi",
        "Do what you can, with what you have, where you are. — Theodore Roosevelt",
        "Simplicity is the ultimate sophistication. — Leonardo da Vinci",
        "What we think, we become. — Buddha",
        "No pressure, no diamonds. — Thomas Carlyle",
        "Fortune favors the bold. — Virgil",
        "Action is the foundational key to all success. — Pablo Picasso",
        "The only way out is through. — Robert Frost",
        "You miss 100 percent of the shots you do not take. — Wayne Gretzky",
        "The best revenge is massive success. — Frank Sinatra",
        "I hear and I forget. I do and I understand. — Confucius",
        "Learning never exhausts the mind. — Leonardo da Vinci",
        "Success is not final, failure is not fatal. — Winston Churchill",
        "What gets measured gets managed. — Peter Drucker",
        "Discipline is the bridge between goals and accomplishment. — Jim Rohn",
        "Energy and persistence conquer all things. — Benjamin Franklin",
        "He who has begun has half done. — Horace",
        "The impediment to action advances action. — Marcus Aurelius",
        "It always seems impossible until it is done. — Nelson Mandela",
        "The journey of a thousand miles begins with one step. — Lao Tzu",
        "The man who moves a mountain begins by carrying small stones. — Confucius",
        "Do not wait; the time will never be just right. — Napoleon Hill",
        "Dreams do not work unless you do. — John C. Maxwell",
        "The harder I work, the luckier I get. — Samuel Goldwyn",
        "Courage is resistance to fear, mastery of fear. — Mark Twain",
        "Quality means doing it right when no one is looking. — Henry Ford",
        "Small deeds done are better than great deeds planned. — Peter Marshall",
        "Fall seven times, stand up eight. — Japanese Proverb",
        "Do not count the days, make the days count. — Muhammad Ali",
        "If opportunity does not knock, build a door. — Milton Berle",
        "Your habits are writing your reputation before success arrives.",
        "The hours you protect become the life you create.",
        "Silence your chaos, and your priorities get louder.",
        "Progress begins the moment you stop negotiating with distraction.",
        "A focused day is a vote for your future self.",
        "You do not need perfect conditions, only honest effort.",
        "When you keep promises to yourself, confidence stops being fragile.",
        "One disciplined week can reset a drifting month.",
        "Clarity comes to people who start before they feel ready.",
        "Build a life that does not need escaping from."
    )
    val customQuotesList by viewModel.customQuotes.collectAsStateWithLifecycle()
    val allQuotes = quotes + customQuotesList
    var index by remember(allQuotes) {
        mutableIntStateOf(if (allQuotes.isNotEmpty()) Random.nextInt(allQuotes.size) else 0)
    }
    var remainingQuoteIndices by remember(allQuotes) { mutableStateOf(emptyList<Int>()) }
    var hasVisitedQuotePage by remember { mutableStateOf(false) }

    fun buildShuffledPool(currentIndex: Int): List<Int> {
        if (allQuotes.isEmpty()) return emptyList()
        if (allQuotes.size == 1) return listOf(0)

        val shuffled = (0 until allQuotes.size).shuffled().toMutableList()
        // Avoid immediate repeats across cycle boundaries.
        if (shuffled.first() == currentIndex) {
            val swapWith = shuffled.indexOfFirst { it != currentIndex }
            if (swapWith > 0) {
                val tmp = shuffled[0]
                shuffled[0] = shuffled[swapWith]
                shuffled[swapWith] = tmp
            }
        }
        return shuffled
    }

    fun pickNextQuote() {
        if (allQuotes.isEmpty()) return

        var pool = remainingQuoteIndices
        if (pool.isEmpty()) {
            pool = buildShuffledPool(index)
        }

        index = pool.first()
        remainingQuoteIndices = pool.drop(1)
    }

    LaunchedEffect(allQuotes) {
        if (allQuotes.isNotEmpty()) {
            // Reset pool when quote content changes (including custom quotes updates).
            remainingQuoteIndices = (0 until allQuotes.size).filter { it != index }.shuffled()
        }
    }

    LaunchedEffect(isVisible) {
        if (!isVisible || allQuotes.isEmpty()) return@LaunchedEffect

        if (hasVisitedQuotePage) {
            pickNextQuote()
        } else {
            hasVisitedQuotePage = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DateTimeDisplay(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🔥 Daily Focus Quote",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = allQuotes.getOrNull(index) ?: "Focus on what matters most today.",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Next quote",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            pickNextQuote()
                        }
                    )
                }
            }
        }

        DockRow(
            viewModel = viewModel,
            appRepository = viewModel.getAppRepository(),
            launcherApps = launcherApps,
            snackbarHostState = snackbarHostState,
            modifier = Modifier
                .fillMaxWidth()
                .then(with(gestureHandler) { Modifier.attachSwipeUp() })
        )
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
    pageData: com.neuroflow.app.presentation.launcher.data.HomeScreenPage?,
    showDateTime: Boolean = true
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Collect state
    val topTask by viewModel.topTask.collectAsStateWithLifecycle()
    val ulyssesContract by viewModel.topTaskUlyssesContract.collectAsStateWithLifecycle()
    val woopEntity by viewModel.topTaskWoopEntity.collectAsStateWithLifecycle()
    val habitTasks by viewModel.habitTasks.collectAsStateWithLifecycle()
    val focusActive by viewModel.focusActive.collectAsStateWithLifecycle()
    val taskSessionActive by viewModel.taskSessionActive.collectAsStateWithLifecycle()
    val activeFocusTaskId by viewModel.activeFocusTaskId.collectAsStateWithLifecycle()
    val focusElapsedSeconds by viewModel.focusElapsedSeconds.collectAsStateWithLifecycle()
    val allActiveTasks by viewModel.allActiveTasks.collectAsStateWithLifecycle()
    val allApps by viewModel.allApps.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val badgeCounts by viewModel.badgeCounts.collectAsStateWithLifecycle()
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle(initialValue = null)
    val peakDetection by viewModel.peakDetection.collectAsStateWithLifecycle(initialValue = null)
    val energy by viewModel.energy.collectAsStateWithLifecycle(initialValue = null)
    val appRepository = viewModel.getAppRepository()

    // HyperFocus state — Requirements: 6.2
    val hyperFocusViewModel: HyperFocusViewModel = hiltViewModel()
    val hyperFocusPrefs by hyperFocusViewModel.hyperFocusPrefs.collectAsStateWithLifecycle()
    val hyperFocusProgress by hyperFocusViewModel.progress.collectAsStateWithLifecycle()
    val unlockSecondsRemaining by hyperFocusViewModel.unlockSecondsRemaining.collectAsStateWithLifecycle()
    val sessionSecondsRemaining by hyperFocusViewModel.sessionSecondsRemaining.collectAsStateWithLifecycle()

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
                if (showDateTime) {
                    DateTimeDisplay(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(with(gestureHandler) { Modifier.attachSwipeDown() })
                    )
                }
                HyperFocusStatusBar(
                    prefs = hyperFocusPrefs,
                    progress = hyperFocusProgress,
                    unlockSecondsRemaining = unlockSecondsRemaining,
                    sessionSecondsRemaining = sessionSecondsRemaining,
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
                    effectivePeakProfile = peakDetection?.effectiveProfile,
                    energy = energy,
                    onSkip = { taskId -> viewModel.skipTask(taskId) },
                    onStartFocus = { taskId ->
                        val effectiveTaskId = if (taskSessionActive) activeFocusTaskId ?: taskId else taskId
                        val intent = Intent(context, com.neuroflow.app.MainActivity::class.java).apply {
                            action = "com.procus.ACTION_OPEN_FOCUS"
                            putExtra("task_id", effectiveTaskId)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    },
                    onStopFocus = { viewModel.stopFocusSession() },
                    onClearSkipped = { viewModel.clearSkippedTasks() },
                    onEmergencyReset = { viewModel.triggerEmergencyReset() },
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
    val taskSessionActive by viewModel.taskSessionActive.collectAsStateWithLifecycle()
    val activeFocusTaskId by viewModel.activeFocusTaskId.collectAsStateWithLifecycle()
    val focusElapsedSeconds by viewModel.focusElapsedSeconds.collectAsStateWithLifecycle()
    val allActiveTasks by viewModel.allActiveTasks.collectAsStateWithLifecycle()
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle(initialValue = null)
    val peakDetection by viewModel.peakDetection.collectAsStateWithLifecycle(initialValue = null)
    val energy by viewModel.energy.collectAsStateWithLifecycle(initialValue = null)

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
                    effectivePeakProfile = peakDetection?.effectiveProfile,
                    energy = energy,
                    onSkip = { taskId -> viewModel.skipTask(taskId) },
                    onStartFocus = { taskId ->
                        val effectiveTaskId = if (taskSessionActive) activeFocusTaskId ?: taskId else taskId
                        val intent = Intent(context, com.neuroflow.app.MainActivity::class.java).apply {
                            action = "com.procus.ACTION_OPEN_FOCUS"
                            putExtra("task_id", effectiveTaskId)
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
    val taskSessionActive by viewModel.taskSessionActive.collectAsStateWithLifecycle()
    val activeFocusTaskId by viewModel.activeFocusTaskId.collectAsStateWithLifecycle()
    val focusElapsedSeconds by viewModel.focusElapsedSeconds.collectAsStateWithLifecycle()
    val allActiveTasks by viewModel.allActiveTasks.collectAsStateWithLifecycle()
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle(initialValue = null)
    val peakDetection by viewModel.peakDetection.collectAsStateWithLifecycle(initialValue = null)
    val energy by viewModel.energy.collectAsStateWithLifecycle(initialValue = null)

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
                effectivePeakProfile = peakDetection?.effectiveProfile,
                energy = energy,
                onSkip = { taskId -> viewModel.skipTask(taskId) },
                onStartFocus = { taskId ->
                    val effectiveTaskId = if (taskSessionActive) activeFocusTaskId ?: taskId else taskId
                    val intent = Intent(context, com.neuroflow.app.MainActivity::class.java).apply {
                        action = "com.procus.ACTION_OPEN_FOCUS"
                        putExtra("task_id", effectiveTaskId)
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
