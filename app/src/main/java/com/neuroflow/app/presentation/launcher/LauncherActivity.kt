package com.neuroflow.app.presentation.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import com.google.android.material.color.DynamicColors
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.neuroflow.app.MainActivity
import com.neuroflow.app.presentation.launcher.data.PackageChangeReceiver
import com.neuroflow.app.presentation.launcher.data.AppRepository
import com.neuroflow.app.presentation.launcher.domain.AppWidgetHostWrapper
import com.neuroflow.app.presentation.launcher.domain.LauncherGestureHandler
import com.neuroflow.app.presentation.launcher.drawer.AppDrawer
import com.neuroflow.app.presentation.launcher.stats.QuickStatsPanel
import com.neuroflow.app.presentation.launcher.settings.LauncherSettings
import com.neuroflow.app.presentation.launcher.hyperfocus.HyperFocusViewModel
import com.neuroflow.app.presentation.launcher.hyperfocus.screens.PlanningPromptScreen
import com.neuroflow.app.presentation.launcher.hyperfocus.screens.RewardsScreen
import com.neuroflow.app.presentation.launcher.theme.ProvideLauncherTheme
import com.neuroflow.app.presentation.launcher.theme.mapPreferencesToTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neuroflow.app.domain.engine.AnalyticsEngine
import com.neuroflow.app.domain.engine.FreshStartEngine
import com.neuroflow.app.presentation.common.NewChapterCard
import kotlinx.coroutines.launch

/**
 * LauncherActivity serves as the Android home screen replacement for proFlow Launcher.
 *
 * This activity is registered with MAIN/HOME intent filters and configured with:
 * - singleTask launch mode to prevent multiple instances
 * - Empty task affinity to isolate from main app task
 * - Transparent theme to show system wallpaper
 * - Dynamic Color support on API 31+ (Material You)
 *
 * Extends FragmentActivity (not ComponentActivity) to support BiometricPrompt for app locking.
 *
 * Lifecycle:
 * - onCreate: Apply theme, set content with crash recovery fallback
 * - onStart: Register PackageChangeReceiver, start AppWidgetHost listening
 * - onStop: Unregister PackageChangeReceiver, stop AppWidgetHost listening
 * - onTrimMemory: Forward to AppRepository for cache management
 * - onLowMemory: Clear icon cache
 * - onBackPressed: Close AppDrawer/QuickStatsPanel if open, never finish activity
 *
 * Requirements: 1.6, 1.13, 1.14, 14.12, 2.1, 2.2, 2.3, 2.12, 2.13, 2.14, 9.6-9.11, 26.1
 */
@AndroidEntryPoint
class LauncherActivity : FragmentActivity() {

    @Inject
    lateinit var packageChangeReceiver: PackageChangeReceiver

    @Inject
    lateinit var appRepository: AppRepository

    @Inject
    lateinit var appWidgetHost: AppWidgetHostWrapper

    private lateinit var launcherApps: LauncherApps

    private val viewModel: LauncherViewModel by viewModels()

    // State for drawer and panels
    private var isAppDrawerOpen by mutableStateOf(false)
    private var isLauncherSettingsOpen by mutableStateOf(false)
    internal var isRewardsOpen by mutableStateOf(false)
    internal var isPlanningOpen by mutableStateOf(false)

    // Tracks whether the pager is on the main page (index 1)
    private var isPagerOnMainPage by mutableStateOf(true)

    // Callback set by HomeScreen to navigate pager to main page (index 1)
    private var navigateToMainPage: (() -> Unit)? = null

    // Back callback — always enabled so back never leaves the launcher
    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            handleBackPressed()
        }
    }

    // Debounce rapid home button presses
    private var lastNewIntentMs = 0L

    // FreshStart overlay state
    private var freshStartHandled by mutableStateOf(false)

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge: let content draw behind system bars
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Make nav bar and status bar fully transparent so wallpaper shows through
        // Nav bar button appearance is controlled by WindowInsetsController
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                // Start with light appearance (dark buttons) — adapts to wallpaper color
                controller.setSystemBarsAppearance(
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS or
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS or
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )
            }
        }

        // Clear skipped tasks on launcher start
        viewModel.clearSkippedTasks()

        // Initialize LauncherApps system service
        launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

        // Apply Dynamic Color on API 31+ (Material You)
        // Wrapped in try-catch: MIUI/HyperOS theming engine can conflict with Material You
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                DynamicColors.applyToActivityIfAvailable(this)
            } catch (e: Exception) {
                Log.w("LauncherActivity", "DynamicColors failed (MIUI/HyperOS?)", e)
            }
        }

        // Register back callback — enabled only when there's something to dismiss
        onBackPressedDispatcher.addCallback(this, backCallback)

        // Create gesture handler
        val gestureHandler = LauncherGestureHandler(
            context = this,
            onSwipeUp = { isAppDrawerOpen = true },
            onSwipeDown = { attemptNotificationShadeExpansion() },
            onSwipeLeft = { },
            onSwipeRight = { },
            onLongPress = { isLauncherSettingsOpen = true },
            isDraggingIcon = { viewModel.isDraggingIcon.value }
        )

        // Set content with crash recovery fallback
        // Note: Compose doesn't support try-catch around composables.
        // For crash recovery, SafeHomeScreen.kt exists as a minimal fallback,
        // but actual crash handling would need to be implemented at the
        // Application level using Thread.setDefaultUncaughtExceptionHandler
        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val launcherPrefs by viewModel.launcherTheme.collectAsStateWithLifecycle()
            val userPrefs by viewModel.userPreferences.collectAsStateWithLifecycle()
            val context = LocalContext.current
            val scope = rememberCoroutineScope()

            // Map preferences to theme
            val launcherTheme = launcherPrefs?.let { prefs ->
                mapPreferencesToTheme(
                    preferences = prefs,
                    isDarkTheme = false // TODO: Get from system or user preference
                )
            } ?: com.neuroflow.app.presentation.launcher.domain.LauncherTheme()

            ProvideLauncherTheme(theme = launcherTheme) {
                // Main home screen
                HomeScreen(
                    windowSizeClass = windowSizeClass,
                    viewModel = viewModel,
                    gestureHandler = gestureHandler,
                    onRegisterNavigateHome = { navigateToMainPage = it },
                    onPageChanged = { page ->
                        isPagerOnMainPage = (page == 1)
                    }
                )

                // FreshStart overlay (Requirement 25.1, 25.4)
                // Display NewChapterCard as full-screen overlay when fresh start detected
                // Rendered on top of HorizontalPager, not as separate page
                // Store userPrefs in local variable to enable smart cast
                val currentUserPrefs = userPrefs
                if (!freshStartHandled && currentUserPrefs != null && FreshStartEngine.isFreshStart(
                        nowMillis = System.currentTimeMillis(),
                        lastOpenMillis = currentUserPrefs.lastAppOpenMillis,
                        dailyStreak = currentUserPrefs.dailyStreak,
                        lastActiveDate = currentUserPrefs.lastActiveDate,
                        lastFreshStartShownWeek = currentUserPrefs.lastFreshStartShownWeek,
                        lastFreshStartShownYear = currentUserPrefs.lastFreshStartShownYear
                    )
                ) {
                    NewChapterCard(
                        onConfirm = { intent ->
                            freshStartHandled = true
                            scope.launch {
                                val now = System.currentTimeMillis()
                                viewModel.updateUserPreferences { p ->
                                    p.copy(
                                        weeklyIntent = intent,
                                        weeklyIntentIsoWeek = FreshStartEngine.isoWeekNumber(now),
                                        weeklyIntentIsoYear = FreshStartEngine.isoYear(now),
                                        lastFreshStartShownWeek = FreshStartEngine.isoWeekNumber(now),
                                        lastFreshStartShownYear = FreshStartEngine.isoYear(now),
                                        lastAppOpenMillis = now
                                    )
                                }
                            }
                        },
                        onDismiss = {
                            freshStartHandled = true
                            scope.launch {
                                val now = System.currentTimeMillis()
                                viewModel.updateUserPreferences { p ->
                                    p.copy(
                                        lastFreshStartShownWeek = FreshStartEngine.isoWeekNumber(now),
                                        lastFreshStartShownYear = FreshStartEngine.isoYear(now),
                                        lastAppOpenMillis = now
                                    )
                                }
                            }
                        }
                    )
                }

                // App drawer overlay (always rendered, visibility controlled by isOpen)
                AppDrawer(
                    isOpen = isAppDrawerOpen,
                    onDismiss = { isAppDrawerOpen = false },
                    viewModel = viewModel,
                    launcherApps = launcherApps
                )

                // LauncherSettings overlay
                LauncherSettings(
                    isOpen = isLauncherSettingsOpen,
                    onDismiss = { isLauncherSettingsOpen = false },
                    onNavigateToRewards = { isRewardsOpen = true },
                    viewModel = viewModel
                )

                // Rewards overlay
                if (isRewardsOpen) {
                    val hyperFocusViewModel: HyperFocusViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                    RewardsScreen(
                        onNavigateBack = { isRewardsOpen = false },
                        viewModel = hyperFocusViewModel
                    )
                }

                // Planning prompt overlay
                if (isPlanningOpen) {
                    val hyperFocusViewModel: HyperFocusViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                    PlanningPromptScreen(
                        viewModel = hyperFocusViewModel,
                        onDismiss = { isPlanningOpen = false }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Debounce rapid home presses (300ms window)
        val now = System.currentTimeMillis()
        if (now - lastNewIntentMs < 300L) return
        lastNewIntentMs = now
        // Close overlays and snap back to main page when home button pressed
        isAppDrawerOpen = false
        isLauncherSettingsOpen = false
        isRewardsOpen = false
        isPlanningOpen = false
        navigateToMainPage?.invoke()
    }

    override fun onStart() {
        super.onStart()
        // Register PackageChangeReceiver for package install/uninstall events
        registerReceiver(packageChangeReceiver, PackageChangeReceiver.createIntentFilter())

        // Start listening for widget updates
        // Safe to call even when no widgets are bound (Phase 1 scaffolding)
        appWidgetHost.startListening()
    }

    override fun onStop() {
        super.onStop()
        // Unregister PackageChangeReceiver
        try {
            unregisterReceiver(packageChangeReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver was not registered, ignore
        }

        // Stop listening for widget updates
        appWidgetHost.stopListening()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Forward memory trim events to AppRepository
        appRepository.onTrimMemory(level)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        // Clear icon cache on critical memory pressure
        appRepository.clearIconCache()
    }

    /**
     * Handle back button press.
     * Close AppDrawer/Settings if open, navigate to main page if on another page.
     * Always consumed — a launcher never backs out to another launcher.
     */
    private fun handleBackPressed() {
        when {
            isAppDrawerOpen -> isAppDrawerOpen = false
            isLauncherSettingsOpen -> isLauncherSettingsOpen = false
            isRewardsOpen -> isRewardsOpen = false
            isPlanningOpen -> isPlanningOpen = false
            !isPagerOnMainPage -> navigateToMainPage?.invoke()
        }
    }

    // updateBackCallback kept as no-op for safety (callback is always enabled now)
    private fun updateBackCallback() = Unit

    /**
     * Attempt to expand notification shade via StatusBarManager reflection.
     * May fail on some devices/ROMs where this is restricted.
     */
    private fun attemptNotificationShadeExpansion() {
        try {
            @Suppress("DEPRECATION")
            val statusBarService = getSystemService(Context.STATUS_BAR_SERVICE)
            val statusBarManager = Class.forName("android.app.StatusBarManager")
            val expandMethod = statusBarManager.getMethod("expandNotificationsPanel")
            expandMethod.invoke(statusBarService)
        } catch (e: Exception) {
            // Fail silently - some devices/ROMs restrict this
            Log.w("LauncherActivity", "Failed to expand notification shade", e)
        }
    }

    /**
     * Create Intent to open MainActivity with focus action.
     */
    fun createFocusIntent(taskId: String): Intent {
        return Intent(this, MainActivity::class.java).apply {
            action = "com.procus.ACTION_OPEN_FOCUS"
            putExtra("task_id", taskId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }
}
