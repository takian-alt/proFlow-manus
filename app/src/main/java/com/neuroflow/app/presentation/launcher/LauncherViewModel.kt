package com.neuroflow.app.presentation.launcher

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.neuroflow.app.data.local.UserPreferencesDataStore
import com.neuroflow.app.data.local.entity.UlyssesContractEntity
import com.neuroflow.app.data.local.entity.WoopEntity
import com.neuroflow.app.data.repository.TaskRepository
import com.neuroflow.app.data.repository.UlyssesContractRepository
import com.neuroflow.app.data.repository.WoopRepository
import com.neuroflow.app.presentation.launcher.hyperfocus.data.HyperFocusDataStore
import com.neuroflow.app.presentation.launcher.hyperfocus.data.HyperFocusPreferences
import com.neuroflow.app.domain.engine.TaskScoringEngine
import com.neuroflow.app.domain.model.Recurrence
import com.neuroflow.app.domain.model.TaskStatus
import com.neuroflow.app.presentation.launcher.data.AppInfo
import com.neuroflow.app.presentation.launcher.data.AppRepository
import com.neuroflow.app.presentation.launcher.data.FolderDefinition
import com.neuroflow.app.presentation.launcher.data.NotificationBadgeManager
import com.neuroflow.app.presentation.launcher.data.PinnedAppsDataStore
import androidx.compose.runtime.mutableStateOf
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

/**
 * Central ViewModel for the Focus Launcher.
 * All state is StateFlow with SharingStarted.WhileSubscribed(5000).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LauncherViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val taskRepository: TaskRepository,
    private val ulyssesContractRepository: UlyssesContractRepository,
    private val woopRepository: WoopRepository,
    private val appRepository: AppRepository,
    private val pinnedAppsDataStore: PinnedAppsDataStore,
    private val userPreferencesDataStore: UserPreferencesDataStore,
    notificationBadgeManager: NotificationBadgeManager,
    private val sessionRepository: com.neuroflow.app.data.repository.SessionRepository,
    private val launcherBackupManager: com.neuroflow.app.presentation.launcher.data.LauncherBackupManager,
    private val iconPackManager: com.neuroflow.app.presentation.launcher.data.IconPackManager,
    private val hyperFocusDataStore: HyperFocusDataStore,
) : ViewModel() {

    init {
        // Query installed icon packs on initialization (Task 23.3)
        viewModelScope.launch {
            iconPackManager.queryInstalledPacks()
        }

        // Auto-populate dock with common apps on first launch
        viewModelScope.launch {
            appRepository.apps.first { it.isNotEmpty() }
            val currentPrefs = pinnedAppsDataStore.launcherPrefsFlow.first()
            // Only auto-populate if dock is empty (first launch)
            if (currentPrefs.dockPackages.isEmpty()) {
                val apps = appRepository.apps.first()
                val commonDock = listOf(
                    "com.android.chrome",
                    "com.google.android.gm",
                    "com.android.camera2",
                    "com.android.contacts"
                )
                commonDock.forEach { pkg ->
                    if (apps.any { it.packageName == pkg }) pinToDock(pkg)
                }
            }
        }
    }

    // ── Task State ──────────────────────────────────────────────────────────

    /**
     * Skipped task IDs (persisted in PinnedAppsDataStore).
     * Tasks in this set are excluded from topTask computation.
     */
    val skippedTaskIds: StateFlow<Set<String>> = pinnedAppsDataStore.launcherPrefsFlow
        .map { it.skippedTaskIds }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    /**
     * All active tasks (for detecting if tasks exist but are all skipped).
     */
    val allActiveTasks: StateFlow<List<com.neuroflow.app.data.local.entity.TaskEntity>> =
        taskRepository.observeActiveTasks()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Top task computation: highest-scored active task excluding skipped IDs.
     * Uses TaskScoringEngine.sortedByScore and filters out skipped tasks.
     */
    val topTask = combine(
        allActiveTasks,
        userPreferencesDataStore.preferencesFlow,
        skippedTaskIds
    ) { tasks, prefs, skipped ->
        TaskScoringEngine.sortedByScore(tasks, prefs)
            .firstOrNull { it.id !in skipped }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * Ulysses contract for the top task (if exists).
     * Observes the active contract for the current top task.
     */
    val topTaskUlyssesContract: StateFlow<UlyssesContractEntity?> = topTask
        .mapLatest { task ->
            task?.let { ulyssesContractRepository.getActiveForTask(it.id) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * WOOP entity for the top task (if exists).
     * Observes the WOOP data for the current top task.
     */
    val topTaskWoopEntity: StateFlow<WoopEntity?> = topTask.flatMapLatest { task ->
        if (task != null) {
            woopRepository.observeByTaskId(task.id)
        } else {
            flowOf(null)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * Habit tasks: recurring tasks due today, top 3 by score.
     * Filters tasks with recurrence != NONE and habitDate <= today.
     */
    val habitTasks = combine(
        taskRepository.observeActiveTasks(),
        userPreferencesDataStore.preferencesFlow
    ) { tasks, prefs ->
        val now = System.currentTimeMillis()
        val todayStart = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val todayEnd = todayStart + 86_400_000L

        // Filter recurring tasks due today
        val recurringDueToday = tasks.filter { task ->
            task.recurrence != Recurrence.NONE &&
            task.habitDate != null &&
            task.habitDate in todayStart..todayEnd
        }

        // Sort by score and take top 3
        TaskScoringEngine.sortedByScore(recurringDueToday, prefs).take(3)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── App State ───────────────────────────────────────────────────────────

    /**
     * All installed apps from AppRepository.
     */
    val allApps: StateFlow<List<AppInfo>> = appRepository.apps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Search query for app drawer filtering.
     */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /**
     * Filtered apps: combine(allApps, searchQuery, hiddenPackages, lockedPackages) for app drawer search.
     * Filters out apps that are both hidden AND locked (Requirement 15.8).
     * Never uses derivedStateOf.
     */
    val filteredApps = combine(
        allApps,
        searchQuery,
        pinnedAppsDataStore.launcherPrefsFlow.map { it.hiddenPackages },
        pinnedAppsDataStore.launcherPrefsFlow.map { it.lockedPackages }
    ) { apps, query, hiddenPackages, lockedPackages ->
        // Filter out apps that are both hidden AND locked (Requirement 15.8)
        val visibleApps = apps.filter { app ->
            !(app.packageName in hiddenPackages && app.packageName in lockedPackages)
        }

        if (query.isBlank()) {
            visibleApps
        } else {
            visibleApps.filter { app ->
                app.label.contains(query, ignoreCase = true) ||
                app.packageName.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Recent apps: last 10 launched apps from PinnedAppsDataStore.
     */
    val recentApps = combine(
        pinnedAppsDataStore.launcherPrefsFlow,
        allApps
    ) { prefs, apps ->
        prefs.recentPackages.mapNotNull { packageName ->
            apps.firstOrNull { it.packageName == packageName }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Launcher Config ─────────────────────────────────────────────────────

    /**
     * Launcher theme configuration from PinnedAppsDataStore.
     */
    val launcherTheme = pinnedAppsDataStore.launcherPrefsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * Home screen pages for multi-page grid.
     */
    val homeScreenPages: StateFlow<List<com.neuroflow.app.presentation.launcher.data.HomeScreenPage>> =
        pinnedAppsDataStore.launcherPrefsFlow
            .map { it.homeScreenPages }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Whether home screen grid is enabled.
     */
    val homeScreenGridEnabled: StateFlow<Boolean> =
        pinnedAppsDataStore.launcherPrefsFlow
            .map { it.homeScreenGridEnabled }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /**
     * Dock apps: apps pinned to the dock (max 5).
     */
    val dockApps = combine(
        pinnedAppsDataStore.launcherPrefsFlow,
        allApps
    ) { prefs, apps ->
        prefs.dockPackages.mapNotNull { packageName ->
            apps.firstOrNull { it.packageName == packageName }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Folders from PinnedAppsDataStore.
     */
    val folders: StateFlow<List<FolderDefinition>> = pinnedAppsDataStore.launcherPrefsFlow
        .map { it.folders }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Notification badge counts from NotificationBadgeManager.
     */
    val badgeCounts: StateFlow<Map<String, Int>> = notificationBadgeManager.badgeCounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /**
     * Distraction scores from PinnedAppsDataStore.
     */
    val distractionScores: StateFlow<Map<String, Int>> = pinnedAppsDataStore.launcherPrefsFlow
        .map { it.distractionScores }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /**
     * Hidden packages from PinnedAppsDataStore (Task 23.2).
     */
    val hiddenPackages: StateFlow<Set<String>> = pinnedAppsDataStore.launcherPrefsFlow
        .map { it.hiddenPackages }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    /**
     * Locked packages from PinnedAppsDataStore (Task 23.2).
     */
    val lockedPackages: StateFlow<Set<String>> = pinnedAppsDataStore.launcherPrefsFlow
        .map { it.lockedPackages }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    /**
     * Installed icon packs from IconPackManager (Task 23.3).
     */
    val installedIconPacks: StateFlow<List<com.neuroflow.app.presentation.launcher.data.IconPackInfo>> =
        iconPackManager.installedPacks
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * User preferences from UserPreferencesDataStore (Task 25.1).
     * Exposed for FreshStart detection in LauncherActivity.
     */
    val userPreferences = userPreferencesDataStore.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * Left page block visibility map.
     */
    val leftPageBlocks: StateFlow<Map<String, Boolean>> = pinnedAppsDataStore.launcherPrefsFlow
        .map { it.leftPageBlocks }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            mapOf(
                "subliminal" to true,
                "quick_note" to true,
                "woop" to true,
                "distraction_top3" to true
            )
        )

    /**
     * Top 3 most distracting apps during focus sessions, computed on demand.
     * Loaded via loadTop3DistractingApps() — returns empty until called.
     */
    private val _top3DistractingApps = MutableStateFlow<List<com.neuroflow.app.domain.engine.DistractionEngine.AppDistractionResult>>(emptyList())
    val top3DistractingApps: StateFlow<List<com.neuroflow.app.domain.engine.DistractionEngine.AppDistractionResult>> = _top3DistractingApps.asStateFlow()

    private val _isLoadingDistraction = java.util.concurrent.atomic.AtomicBoolean(false)

    private val _distractionLoading = MutableStateFlow(false)
    val distractionLoading: StateFlow<Boolean> = _distractionLoading.asStateFlow()

    fun resetTop3DistractingApps() {
        _top3DistractingApps.value = emptyList()
    }

    fun loadTop3DistractingApps() {
        if (!_isLoadingDistraction.compareAndSet(false, true)) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                _distractionLoading.value = true
                val blockEnabled = pinnedAppsDataStore.launcherPrefsFlow.first()
                    .leftPageBlocks["distraction_top3"] != false
                if (!blockEnabled) return@launch
                if (!com.neuroflow.app.domain.engine.DistractionEngine.hasUsagePermission(context)) return@launch
                val sessions = sessionRepository.getAllSessions()
                _top3DistractingApps.value = com.neuroflow.app.domain.engine.DistractionEngine
                    .rankAppsByDistraction(sessions = sessions, context = context)
            } finally {
                _distractionLoading.value = false
                _isLoadingDistraction.set(false)
            }
        }
    }

    fun setLeftPageBlockVisible(blockId: String, visible: Boolean) {
        viewModelScope.launch {
            pinnedAppsDataStore.updatePreferences { prefs ->
                prefs.copy(leftPageBlocks = prefs.leftPageBlocks + (blockId to visible))
            }
        }
    }

    fun saveQuickNote(note: String) {
        viewModelScope.launch {
            userPreferencesDataStore.updatePreferences { it.copy(leftPageQuickNote = note) }
        }
    }

    // ── Phase 1 Scaffolding ─────────────────────────────────────────────────

    /**
     * Hyper Focus preferences from HyperFocusDataStore.
     * Exposes the full HyperFocusPreferences state for UI and AppIcon blocking checks.
     */
    val hyperFocusPrefs: StateFlow<HyperFocusPreferences> = hyperFocusDataStore.flow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HyperFocusPreferences())

    /**
     * Focus mode active state (Phase 5: wired to session repository).
     * Tracks whether a focus session is currently active OR hyper focus is active.
     */
    val focusActive: StateFlow<Boolean> = combine(
        sessionRepository.observeOpenSessions().map { sessions -> sessions.isNotEmpty() },
        hyperFocusPrefs.map { it.isActive }
    ) { sessionActive, hyperActive -> sessionActive || hyperActive }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /**
     * Elapsed seconds for the active focus session.
     * Ticks every second from the real session startedAt, accounting for paused time.
     * Survives page swipes because it lives in the ViewModel, not in a composable.
     *
     * Re-evaluates on every tick so pause/resume changes are reflected immediately
     * without capturing a stale session snapshot.
     */
    val focusElapsedSeconds: StateFlow<Int> = sessionRepository.observeOpenSessions()
        .flatMapLatest { sessions ->
            val session = sessions.firstOrNull()
            if (session == null) {
                flowOf(0)
            } else {
                // Use the session ID as the stable key; re-query live data each tick
                // so pausedAt / totalPausedMs changes are always fresh.
                flow {
                    while (true) {
                        // Re-read the latest session state from the DB on each tick
                        val live = sessionRepository.getOpenSessions()
                            .firstOrNull { it.id == session.id }
                            ?: sessionRepository.getOpenSessions().firstOrNull() // fallback if session was replaced
                        if (live == null) {
                            emit(0)
                        } else {
                            val now = System.currentTimeMillis()
                            // If currently paused, freeze the counter at the moment of pause
                            val pausedAt = live.pausedAt
                            val pausedMs = if (pausedAt != null) now - pausedAt else 0L
                            val elapsed = ((now - live.startedAt) - live.totalPausedMs - pausedMs)
                                .coerceAtLeast(0L)
                            emit((elapsed / 1000L).toInt())
                        }
                        kotlinx.coroutines.delay(1000)
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /**
     * Default launcher detection (Requirement 23.5).
     * Re-checks on every subscription so the settings screen always shows the current state.
     * Uses RoleManager on API 29+ with ResolveInfo fallback.
     */
    val isDefaultLauncher: StateFlow<Boolean> = flow {
        while (true) {
            val isDefault = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
                roleManager?.isRoleHeld(RoleManager.ROLE_HOME) ?: false
            } else {
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                val resolveInfo = context.packageManager.resolveActivity(intent, 0)
                resolveInfo?.activityInfo?.packageName == context.packageName
            }
            emit(isDefault)
            kotlinx.coroutines.delay(2_000)
        }
    }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // ── Actions ─────────────────────────────────────────────────────────────

    /**
     * Stop the currently active focus session immediately.
     * Sets endedAt to now and clears pausedAt so the timer resets.
     * Called from the launcher's "Stop Session" button — no need to open MainActivity.
     */
    fun stopFocusSession() {
        viewModelScope.launch {
            val openSessions = sessionRepository.getOpenSessions()
            val now = System.currentTimeMillis()
            openSessions.forEach { session ->
                // If paused, account for the paused time before closing
                val extraPausedMs = if (session.pausedAt != null) now - session.pausedAt else 0L
                sessionRepository.update(
                    session.copy(
                        endedAt = now,
                        pausedAt = null,
                        totalPausedMs = session.totalPausedMs + extraPausedMs
                    )
                )
            }
        }
    }

    /**
     * Skip task: add to skippedTaskIds in PinnedAppsDataStore.
     * Task remains active but is excluded from topTask computation.
     * Skipped tasks are cleared when clearSkippedTasks() is called (e.g., on launcher restart).
     */
    fun skipTask(taskId: String) {
        viewModelScope.launch {
            pinnedAppsDataStore.updatePreferences { prefs ->
                prefs.copy(skippedTaskIds = prefs.skippedTaskIds + taskId)
            }
        }
    }

    /**
     * Clear all skipped tasks.
     * Called when launcher activity starts to reset the skip list.
     */
    fun clearSkippedTasks() {
        viewModelScope.launch {
            pinnedAppsDataStore.updatePreferences { prefs ->
                prefs.copy(skippedTaskIds = emptySet())
            }
        }
    }

    /**
     * Complete habit: call TaskRepository.completeAndRecur.
     * Marks task completed and creates next occurrence if recurring.
     */
    fun completeHabit(task: com.neuroflow.app.data.local.entity.TaskEntity) {
        viewModelScope.launch {
            taskRepository.completeAndRecur(task, System.currentTimeMillis())
        }
    }

    /**
     * Pin app to dock: add to dockPackages (max 5).
     */
    fun pinToDock(packageName: String) {
        viewModelScope.launch {
            pinnedAppsDataStore.updatePreferences { prefs ->
                if (prefs.dockPackages.size >= 5) {
                    // Already at max capacity
                    prefs
                } else {
                    prefs.copy(dockPackages = prefs.dockPackages + packageName)
                }
            }
        }
    }

    /**
     * Remove app from dock.
     */
    fun removeFromDock(packageName: String) {
        viewModelScope.launch {
            pinnedAppsDataStore.updatePreferences { prefs ->
                prefs.copy(dockPackages = prefs.dockPackages - packageName)
            }
        }
    }

    /**
     * Reorder dock apps: update dockPackages order.
     */
    fun reorderDock(newOrder: List<String>) {
        viewModelScope.launch {
            pinnedAppsDataStore.updatePreferences { prefs ->
                prefs.copy(dockPackages = newOrder.take(5))
            }
        }
    }

    /**
     * Hide app: add to hiddenPackages.
     */
    fun hideApp(packageName: String) {
        viewModelScope.launch {
            pinnedAppsDataStore.updatePreferences { prefs ->
                prefs.copy(hiddenPackages = prefs.hiddenPackages + packageName)
            }
        }
    }

    /**
     * Lock app: add to lockedPackages.
     */
    fun lockApp(packageName: String) {
        viewModelScope.launch {
            pinnedAppsDataStore.updatePreferences { prefs ->
                prefs.copy(lockedPackages = prefs.lockedPackages + packageName)
            }
        }
    }

    /**
     * Set search query: update searchQuery StateFlow.
     */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * Record app launch: call AppRepository.recordLaunch.
     * Prepends to recent list (max 10).
     */
    fun recordLaunch(packageName: String) {
        viewModelScope.launch {
            appRepository.recordLaunch(packageName)
        }
    }

    /**
     * Update distraction score: update distractionScores map.
     */
    fun updateDistractionScore(packageName: String, score: Int) {
        viewModelScope.launch {
            pinnedAppsDataStore.updatePreferences { prefs ->
                prefs.copy(
                    distractionScores = prefs.distractionScores + (packageName to score)
                )
            }
        }
    }

    // ── Folder Management ───────────────────────────────────────────────────

    /**
     * Create folder from two apps (Requirement 4.6).
     * Replaces both apps with a new folder at the first app's position.
     *
     * @param packageName1 First app package name
     * @param packageName2 Second app package name
     * @param gridIndex Position in home screen grid
     * @param name Initial folder name (defaults to "Folder")
     */
    fun createFolder(
        packageName1: String,
        packageName2: String,
        gridIndex: Int,
        name: String = "Folder"
    ) {
        viewModelScope.launch {
            pinnedAppsDataStore.updatePreferences { prefs ->
                val newFolder = FolderDefinition(
                    id = java.util.UUID.randomUUID().toString(),
                    name = name,
                    packages = listOf(packageName1, packageName2),
                    gridIndex = gridIndex
                )
                prefs.copy(folders = prefs.folders + newFolder)
            }
        }
    }

    /**
     * Add app to existing folder (Requirement 4.6).
     *
     * @param folderId Folder ID to add app to
     * @param packageName Package name to add
     */
    fun addAppToFolder(folderId: String, packageName: String) {
        viewModelScope.launch {
            pinnedAppsDataStore.updatePreferences { prefs ->
                val updatedFolders = prefs.folders.map { folder ->
                    if (folder.id == folderId) {
                        folder.copy(packages = folder.packages + packageName)
                    } else {
                        folder
                    }
                }
                prefs.copy(folders = updatedFolders)
            }
        }
    }

    /**
     * Remove app from folder (Requirement 4.4).
     * Auto-dissolves folder if only 1 app remains (Requirement 4.5).
     *
     * @param folderId Folder ID to remove app from
     * @param packageName Package name to remove
     */
    fun removeAppFromFolder(folderId: String, packageName: String) {
        viewModelScope.launch {
            pinnedAppsDataStore.updatePreferences { prefs ->
                val updatedFolders = prefs.folders.mapNotNull { folder ->
                    if (folder.id == folderId) {
                        val updatedPackages = folder.packages - packageName
                        // Auto-dissolve if only 1 app remains (Requirement 4.5)
                        if (updatedPackages.size <= 1) {
                            null // Remove folder
                        } else {
                            folder.copy(packages = updatedPackages)
                        }
                    } else {
                        folder
                    }
                }
                prefs.copy(folders = updatedFolders)
            }
        }
    }

    /**
     * Rename folder (Requirement 4.3).
     *
     * @param folderId Folder ID to rename
     * @param newName New folder name
     */
    fun renameFolder(folderId: String, newName: String) {
        viewModelScope.launch {
            pinnedAppsDataStore.updatePreferences { prefs ->
                val updatedFolders = prefs.folders.map { folder ->
                    if (folder.id == folderId) {
                        folder.copy(name = newName)
                    } else {
                        folder
                    }
                }
                prefs.copy(folders = updatedFolders)
            }
        }
    }

    /**
     * Delete folder completely.
     *
     * @param folderId Folder ID to delete
     */
    fun deleteFolder(folderId: String) {
        viewModelScope.launch {
            pinnedAppsDataStore.updatePreferences { prefs ->
                prefs.copy(folders = prefs.folders.filter { it.id != folderId })
            }
        }
    }

    /**
     * Remove uninstalled apps from folders (Requirement 4.8).
     * Called by PackageChangeReceiver on PACKAGE_REMOVED.
     *
     * @param packageName Package name that was uninstalled
     */
    fun removeUninstalledAppFromFolders(packageName: String) {
        viewModelScope.launch {
            pinnedAppsDataStore.updatePreferences { prefs ->
                val updatedFolders = prefs.folders.mapNotNull { folder ->
                    if (packageName in folder.packages) {
                        val updatedPackages = folder.packages - packageName
                        // Auto-dissolve if only 1 app remains
                        if (updatedPackages.size <= 1) {
                            null // Remove folder
                        } else {
                            folder.copy(packages = updatedPackages)
                        }
                    } else {
                        folder
                    }
                }
                prefs.copy(folders = updatedFolders)
            }
        }
    }

    // ── QuickStatsPanel ─────────────────────────────────────────────────────

    /**
     * Load analytics summary for QuickStatsPanel.
     * Calls AnalyticsEngine.buildSummary() on IO dispatcher.
     * Requirement 22.5: Clear stale data on each open by calling buildSummary().
     *
     * @return AnalyticsSummary with current stats
     */
    suspend fun loadAnalyticsSummary(): com.neuroflow.app.domain.engine.AnalyticsEngine.AnalyticsSummary {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val allTasks = taskRepository.getAllTasks()
            val allSessions = sessionRepository.getAllSessions()
            val prefs = userPreferencesDataStore.preferencesFlow.first()
            com.neuroflow.app.domain.engine.AnalyticsEngine.buildSummary(
                allTasks = allTasks,
                allSessions = allSessions,
                prefs = prefs,
                nowMillis = System.currentTimeMillis()
            )
        }
    }

    // ── Backup & Restore ────────────────────────────────────────────────────

    /**
     * Export launcher configuration to JSON string (Task 21.1).
     * Serializes LauncherPreferences including schemaVersion.
     *
     * Requirements:
     * - 18.1: Serialize dock apps, folders, hidden apps, locked apps, icon pack, icon shape, grid size, card transparency, clock style
     * - 18.5: Include schemaVersion field in backup JSON
     *
     * @return JSON string containing all launcher configuration
     */
    suspend fun exportConfiguration(): String {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            launcherBackupManager.export()
        }
    }

    /**
     * Import launcher configuration from JSON string (Task 21.2).
     * Validates schema and skips uninstalled packages.
     *
     * Requirements:
     * - 18.3: Import validates schema and applies restored configuration
     * - 18.4: Skip uninstalled packages and return ImportResult with skippedPackages list
     * - 18.5: Apply migration function for older backup schemas
     *
     * @param json JSON string from export()
     * @return ImportResult with success status and list of skipped packages
     */
    suspend fun importConfiguration(json: String): com.neuroflow.app.presentation.launcher.data.ImportResult {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            launcherBackupManager.import(json)
        }
    }

    // ── Settings Management (Task 23) ───────────────────────────────────────

    /**
     * Unhide app: remove from hiddenPackages (Task 23.2).
     * Requirement 24.2: Hidden apps manager with unhide option.
     */
    fun unhideApp(packageName: String) {
        viewModelScope.launch {
            pinnedAppsDataStore.updatePreferences { prefs ->
                prefs.copy(hiddenPackages = prefs.hiddenPackages - packageName)
            }
        }
    }

    /**
     * Unlock app: remove from lockedPackages (Task 23.2).
     * Requirement 24.3: Locked apps manager with unlock option.
     */
    fun unlockApp(packageName: String) {
        viewModelScope.launch {
            pinnedAppsDataStore.updatePreferences { prefs ->
                prefs.copy(lockedPackages = prefs.lockedPackages - packageName)
            }
        }
    }

    /**
     * Update clock style (Task 23.3).
     * Requirement 24.4: Clock style selector (Digital, Minimal).
     */
    fun updateClockStyle(clockStyle: com.neuroflow.app.presentation.launcher.data.ClockStyle) {
        viewModelScope.launch {
            pinnedAppsDataStore.updatePreferences { prefs ->
                prefs.copy(clockStyle = clockStyle)
            }
        }
    }

    /**
     * Update card transparency (Task 23.3).
     * Requirement 24.5: Card transparency slider (0.5-1.0, step 0.05).
     */
    fun updateCardAlpha(alpha: Float) {
        viewModelScope.launch {
            pinnedAppsDataStore.updatePreferences { prefs ->
                prefs.copy(cardAlpha = alpha.coerceIn(0.5f, 1.0f))
            }
        }
    }

    /**
     * Update icon pack selection (Task 23.3).
     * Requirement 24.6: Icon pack selector, apply without restart.
     */
    fun updateIconPack(packageName: String?) {
        viewModelScope.launch {
            pinnedAppsDataStore.updatePreferences { prefs ->
                prefs.copy(iconPackPackageName = packageName)
            }
            // Load the icon pack if not null
            packageName?.let {
                iconPackManager.loadIconPack(it)
            }
            // Invalidate icon cache to trigger recomposition
            appRepository.clearIconCache()
        }
    }

    /**
     * Update icon shape (Task 23.3).
     * Requirement 24.7: Icon shape selector (Circle, Squircle, Rounded Square, Teardrop, System Default).
     * On shape change: invalidate cache and trigger recomposition without launcher restart.
     */
    fun updateIconShape(iconShape: com.neuroflow.app.presentation.launcher.data.IconShape) {
        viewModelScope.launch {
            pinnedAppsDataStore.updatePreferences { prefs ->
                prefs.copy(iconShape = iconShape)
            }
            // Invalidate icon cache to trigger recomposition
            appRepository.clearIconCache()
        }
    }

    /**
     * Update app drawer grid size (Task 23.3).
     * Requirement 24.8: App drawer grid size (3, 4, or 5 columns).
     */
    fun updateDrawerColumns(columns: Int) {
        viewModelScope.launch {
            pinnedAppsDataStore.updatePreferences { prefs ->
                prefs.copy(drawerColumns = columns.coerceIn(3, 5))
            }
        }
    }

    /**
     * Update web search URL (Task 23.4).
     * Requirement 24.12: Web search URL configuration.
     */
    fun updateWebSearchUrl(url: String) {
        viewModelScope.launch {
            pinnedAppsDataStore.updatePreferences { prefs ->
                prefs.copy(webSearchUrl = url)
            }
        }
    }

    /**
     * Update show task score toggle (Task 23.4).
     * Requirement 24.11: "Show task score" toggle.
     */
    fun updateShowTaskScore(show: Boolean) {
        viewModelScope.launch {
            pinnedAppsDataStore.updatePreferences { prefs ->
                prefs.copy(showTaskScore = show)
            }
        }
    }

    /**
     * Update task card style (Phase 4).
     * Allows switching between ELEVATED, FLAT, and OUTLINED card styles.
     */
    fun updateCardStyle(cardStyle: com.neuroflow.app.presentation.launcher.data.CardStyle) {
        viewModelScope.launch {
            pinnedAppsDataStore.updatePreferences { prefs ->
                prefs.copy(taskCardStyle = cardStyle)
            }
        }
    }

    /**
     * Update distraction dimming toggle (Phase 5).
     * Enables/disables dimming of distracting apps during focus mode.
     */
    fun updateDistractionDimming(enabled: Boolean) {
        viewModelScope.launch {
            pinnedAppsDataStore.updatePreferences { prefs ->
                prefs.copy(distractionDimmingEnabled = enabled)
            }
        }
    }

    // ── FreshStart Integration (Task 25) ───────────────────────────────────

    /**
     * Update user preferences (Task 25.2).
     * Used by FreshStart overlay to update UserPreferencesDataStore.
     * Requirements:
     * - 25.2: Update UserPreferencesDataStore on confirm/dismiss
     * - 25.3: Display at most once per detected fresh start marker
     *
     * @param update Lambda to transform current preferences
     */
    fun updateUserPreferences(update: (com.neuroflow.app.data.local.UserPreferences) -> com.neuroflow.app.data.local.UserPreferences) {
        viewModelScope.launch {
            userPreferencesDataStore.updatePreferences(update)
        }
    }

    // ── Home Screen Grid Management ────────────────────────────────────────

    /**
     * Add a new home screen page (max 7 extra = 10 total).
     */
    fun addHomeScreenPage(name: String = "") {
        viewModelScope.launch {
            pinnedAppsDataStore.updatePreferences { prefs ->
                // 3 fixed pages + max 7 extra = 10 total
                if (prefs.homeScreenPages.size >= 7) return@updatePreferences prefs
                val newPage = com.neuroflow.app.presentation.launcher.data.HomeScreenPage(
                    id = java.util.UUID.randomUUID().toString(),
                    name = name
                )
                prefs.copy(homeScreenPages = prefs.homeScreenPages + newPage)
            }
        }
    }

    /**
     * Remove a home screen page.
     */
    fun removeHomeScreenPage(pageId: String) {
        viewModelScope.launch {
            pinnedAppsDataStore.updatePreferences { prefs ->
                prefs.copy(homeScreenPages = prefs.homeScreenPages.filter { it.id != pageId })
            }
        }
    }

    /**
     * Add app to home screen page at specific position.
     */
    fun addAppToPage(pageId: String, packageName: String, gridPosition: Int) {
        viewModelScope.launch {
            pinnedAppsDataStore.updatePreferences { prefs ->
                val pages = prefs.homeScreenPages.map { page ->
                    if (page.id == pageId) {
                        // Remove any existing item at this position
                        val filteredItems = page.items.filter { it.gridPosition != gridPosition }
                        val newItem = com.neuroflow.app.presentation.launcher.data.HomeScreenItem.App(
                            packageName = packageName,
                            gridPosition = gridPosition
                        )
                        page.copy(items = filteredItems + newItem)
                    } else {
                        page
                    }
                }
                prefs.copy(homeScreenPages = pages)
            }
        }
    }

    /**
     * Remove item from page at specific position.
     */
    fun removeItemFromPage(pageId: String, gridPosition: Int) {
        viewModelScope.launch {
            pinnedAppsDataStore.updatePreferences { prefs ->
                val pages = prefs.homeScreenPages.map { page ->
                    if (page.id == pageId) {
                        page.copy(items = page.items.filter { it.gridPosition != gridPosition })
                    } else {
                        page
                    }
                }
                prefs.copy(homeScreenPages = pages)
            }
        }
    }

    /**
     * Move item within a page.
     */
    fun moveItemInPage(pageId: String, fromPosition: Int, toPosition: Int) {
        viewModelScope.launch {
            pinnedAppsDataStore.updatePreferences { prefs ->
                val pages = prefs.homeScreenPages.map { page ->
                    if (page.id == pageId) {
                        val item = page.items.firstOrNull { it.gridPosition == fromPosition }
                        if (item != null) {
                            val filteredItems = page.items.filter {
                                it.gridPosition != fromPosition && it.gridPosition != toPosition
                            }
                            val movedItem = when (item) {
                                is com.neuroflow.app.presentation.launcher.data.HomeScreenItem.App ->
                                    item.copy(gridPosition = toPosition)
                                is com.neuroflow.app.presentation.launcher.data.HomeScreenItem.Folder ->
                                    item.copy(gridPosition = toPosition)
                            }
                            page.copy(items = filteredItems + movedItem)
                        } else {
                            page
                        }
                    } else {
                        page
                    }
                }
                prefs.copy(homeScreenPages = pages)
            }
        }
    }

    /**
     * Toggle home screen grid enabled/disabled.
     */
    fun toggleHomeScreenGrid(enabled: Boolean) {
        viewModelScope.launch {
            pinnedAppsDataStore.updatePreferences { prefs ->
                prefs.copy(homeScreenGridEnabled = enabled)
            }
        }
    }

    /**
     * Get AppRepository for DockRow component.
     * Exposed to allow HomeScreen to pass it to DockRow.
     */
    fun getAppRepository(): AppRepository = appRepository

    // ── Drag state (shared between grid and gesture handler) ────────────────

    /** True while the user is dragging an icon on the home screen grid. */
    val isDraggingIcon = mutableStateOf(false)
}
