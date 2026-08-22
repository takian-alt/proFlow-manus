package com.neuroflow.app.presentation.focus

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neuroflow.app.data.local.UserPreferences
import com.neuroflow.app.data.local.UserPreferencesDataStore
import com.neuroflow.app.data.local.entity.effectiveReminderTargetMillis
import com.neuroflow.app.data.local.entity.TaskEntity
import com.neuroflow.app.data.local.entity.TimeSessionEntity
import com.neuroflow.app.data.local.entity.WoopEntity
import com.neuroflow.app.data.repository.SessionRepository
import com.neuroflow.app.data.repository.TaskRepository
import com.neuroflow.app.domain.engine.AutonomyNudgeEngine
import com.neuroflow.app.domain.engine.TaskScoringEngine
import com.neuroflow.app.domain.repository.EnergyScoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FocusUiState(
    val task: TaskEntity? = null,
    // Tracking state — restored from DB on init
    val isTracking: Boolean = false,
    val isPaused: Boolean = false,
    val elapsedSeconds: Long = 0,
    val activeSessionId: String? = null,   // ID of the open TimeSessionEntity in DB
    val activeSessionCount: Int = 0,
    val sessions: List<TimeSessionEntity> = emptyList(),
    // Stop confirmation step: 0=none, 1=first, 2=second, 3=third
    val stopConfirmStep: Int = 0,
    // Tracking block dialog (shown when user tries to leave while tracking)
    val showTrackingBlockDialog: Boolean = false,
    // Pomodoro
    val pomodoroActive: Boolean = false,
    val pomodoroSeconds: Long = 0,
    val pomodoroTotal: Int = 25 * 60,
    val preferences: UserPreferences = UserPreferences(),
    val nextTaskId: String? = null,
    val nextTaskTitle: String? = null,
    val isCompleted: Boolean = false,
    val pointsEarned: Int = 0,
    val showCompletionSheet: Boolean = false,
    val completedHabitStreak: Int = 0,   // streak after completion — avoids stale task snapshot in UI
    // Live scoring
    val currentScore: Int = 0,
    val urgencyFraction: Float = 0f,
    val urgencyLabel: String = "",
    val scoreBreakdown: List<Pair<String, Float>> = emptyList(),
    // All active tasks for dependency scoring
    val allActiveTasks: List<TaskEntity> = emptyList(),
    // Behavioral motivation engine fields
    val showWoopPrompt: Boolean = false,
    val woopData: WoopEntity? = null,
    val showLaunchCountdown: Boolean = false,
    val launchCountdownValue: Int = 5,
    val weeklyIntent: String = "",
    val showAffordanceRating: Boolean = false,
    val affectiveForecastError: Float? = null,
    val showNavigationInterstitial: Boolean = false,
    val navigationInterstitialSecondsLeft: Int = 3,
    val dreadedTaskInsight: String? = null,
    // Manual time log sheet — shown when DONE is tapped with no tracked time
    val showManualTimeLog: Boolean = false,
    val showSkipFeedback: Boolean = false,
    val completionAffirmation: String = "",
    val energy: EnergyScoreRepository.EnergyUiModel? = null,
)

@HiltViewModel
class FocusViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val taskRepository: TaskRepository,
    private val sessionRepository: SessionRepository,
    private val preferencesDataStore: UserPreferencesDataStore,
    private val sessionManager: FocusSessionManager,
    private val woopManager: FocusWoopManager,
    private val completionManager: FocusCompletionManager,
    private val reminderScheduler: FocusReminderScheduler,
    private val energyScoreRepository: EnergyScoreRepository,
    private val application: Application
) : ViewModel() {

    private val applicationContext get() = application.applicationContext

    private val taskId: String = savedStateHandle["taskId"] ?: ""
    // Accumulates skipped task IDs across the session so skip never cycles back.
    // Seeded from the nav arg so the set survives screen replacement on each skip.
    private val skippedTaskIds: MutableSet<String> = savedStateHandle.get<String>("skipped")
        ?.split(",")?.filter { it.isNotBlank() }?.toMutableSet() ?: mutableSetOf()

    /** Builds the comma-separated skipped arg to pass forward on navigation. */
    fun buildSkippedArg(): String = (skippedTaskIds + taskId).joinToString(",")

    private val _uiState = MutableStateFlow(FocusUiState())
    val uiState: StateFlow<FocusUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var pomodoroJob: Job? = null
    private var scoreTickJob: Job? = null
    private var launchCountdownJob: Job? = null

    init {
        loadTask()
        loadSessions()
        loadPreferences()
        loadWoopData()
        startScoreTick()
        observeNextTask()
        restoreActiveSession()
        observeEnergy()
    }

    private fun observeEnergy() {
        viewModelScope.launch {
            energyScoreRepository.observeEnergy().collect { energy ->
                _uiState.update { it.copy(energy = energy) }
            }
        }
    }

    private var lastReminderSignature: Pair<Int, Long?>? = null

    private fun loadTask() {
        viewModelScope.launch {
            var launchCountdownStarted = false
            taskRepository.observeById(taskId).collect { task ->
                _uiState.update { it.copy(task = task) }
                refreshScore()
                if (task == null) {
                    lastReminderSignature = null
                } else {
                    val targetMs = task.effectiveReminderTargetMillis()
                    val signature = task.reminderFlags to targetMs
                    // Re-schedule on any effective reminder change, including flag reset to 0.
                    if (signature != lastReminderSignature) {
                        lastReminderSignature = signature
                        scheduleReminders(task)
                    }
                }
                // Start launch countdown once after the first non-null task is received
                if (task != null && !launchCountdownStarted) {
                    launchCountdownStarted = true
                    startLaunchCountdownIfNeeded()
                    // Schedule autonomy nudge if task hasn't been started yet
                    if (task.sessionCount == 0) {
                        AutonomyNudgeEngine.scheduleNudge(applicationContext, task)
                    }
                }
            }
        }
    }

    private fun loadSessions() {
        viewModelScope.launch {
            sessionRepository.observeByTaskId(taskId).collect { sessions ->
                _uiState.update { it.copy(sessions = sessions, activeSessionCount = sessions.size) }
            }
        }
    }

    private fun loadPreferences() {
        viewModelScope.launch {
            preferencesDataStore.preferencesFlow.collect { prefs ->
                val now = System.currentTimeMillis()
                val currentWeek = com.neuroflow.app.domain.engine.FreshStartEngine.isoWeekNumber(now)
                val currentYear = com.neuroflow.app.domain.engine.FreshStartEngine.isoYear(now)
                val weeklyIntent = if (
                    prefs.weeklyIntentIsoWeek == currentWeek &&
                    prefs.weeklyIntentIsoYear == currentYear
                ) prefs.weeklyIntent else ""
                _uiState.update {
                    it.copy(
                        preferences = prefs,
                        pomodoroTotal = prefs.defaultPomodoroMinutes * 60,
                        weeklyIntent = weeklyIntent
                    )
                }
                refreshScore()
            }
        }
    }

    private fun loadWoopData() {
        viewModelScope.launch {
            val data = woopManager.load(taskId)
            _uiState.update {
                it.copy(
                    showWoopPrompt = data.showWoopPrompt,
                    woopData = data.woopData,
                    dreadedTaskInsight = data.dreadedTaskInsight,
                    affectiveForecastError = data.affectiveForecastError
                )
            }
        }
    }

    fun submitWoop(wish: String, outcome: String, obstacle: String, plan: String) {
        viewModelScope.launch {
            val woop = woopManager.submit(taskId, wish, outcome, obstacle, plan) ?: return@launch
            _uiState.update { it.copy(showWoopPrompt = false, woopData = woop) }
        }
    }

    fun dismissWoop() {
        viewModelScope.launch {
            if (!woopManager.dismiss(taskId)) return@launch
            _uiState.update { it.copy(showWoopPrompt = false) }
        }
    }

    fun reopenWoop() {
        if (!_uiState.value.preferences.woopEnabled) return
        _uiState.update { it.copy(showWoopPrompt = true) }
    }

    fun submitAffordanceRating(rating: Float) {
        viewModelScope.launch {
            if (!woopManager.submitAffordanceRating(taskId, rating)) return@launch
            _uiState.update { it.copy(showAffordanceRating = false, affectiveForecastError = rating) }
        }
    }

    fun dismissAffordanceRating() {
        _uiState.update { it.copy(showAffordanceRating = false) }
    }

    /**
     * On init, check if there's already an open session for this task in the DB.
     * If yes, restore the elapsed time and resume the timer (or keep paused state).
     */
    private fun restoreActiveSession() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val restored = sessionManager.restoreActiveSession(taskId, now) ?: return@launch

            _uiState.update {
                it.copy(
                    isTracking = true,
                    isPaused = restored.isPaused,
                    elapsedSeconds = restored.elapsedSeconds,
                    activeSessionId = restored.sessionId
                )
            }

            // Always restart the tick job — ViewModel may have been recreated while timer ran
            timerJob?.cancel()
            if (!restored.isPaused) startTimerTick()
        }
    }

    private fun startScoreTick() {
        scoreTickJob = viewModelScope.launch {
            // Fire immediately, then every 30s so urgency/score updates feel live
            while (true) {
                refreshScore()
                delay(30_000)
            }
        }
    }

    private fun refreshScore() {
        val task = _uiState.value.task ?: return
        val prefs = _uiState.value.preferences
        val activeTasks = _uiState.value.allActiveTasks
        val now = System.currentTimeMillis()
        _uiState.update {
            it.copy(
                currentScore = TaskScoringEngine.displayScore(task, prefs, activeTasks, now),
                urgencyFraction = TaskScoringEngine.urgencyFraction(task, now),
                urgencyLabel = TaskScoringEngine.urgencyLabel(task, now),
                scoreBreakdown = TaskScoringEngine.scoreBreakdown(task, prefs, activeTasks, now)
            )
        }
    }

    private fun observeNextTask() {
        viewModelScope.launch {
            taskRepository.observeActiveTasks().collect { activeTasks ->
                _uiState.update { it.copy(allActiveTasks = activeTasks) }
                val prefs = _uiState.value.preferences
                val sorted = TaskScoringEngine.sortedByScore(activeTasks, prefs)
                val next = sorted.firstOrNull { it.id != taskId && it.id !in skippedTaskIds }
                _uiState.update { it.copy(nextTaskId = next?.id, nextTaskTitle = next?.title) }
                refreshScore()
            }
        }
    }

    // ── TRACKING ─────────────────────────────────────────────────────────────

    fun startTracking() {
        if (_uiState.value.isTracking) return
        launchCountdownJob?.cancel()
        _uiState.update { it.copy(showLaunchCountdown = false) }
        AutonomyNudgeEngine.cancelNudge(applicationContext, taskId)
        val now = System.currentTimeMillis()

        viewModelScope.launch {
            val session = sessionManager.startSession(taskId, now)
            _uiState.update {
                it.copy(isTracking = true, isPaused = false, elapsedSeconds = 0, activeSessionId = session.id)
            }
            startTimerTick()
        }
    }

    private fun startTimerTick() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                if (!_uiState.value.isPaused) {
                    _uiState.update { it.copy(elapsedSeconds = it.elapsedSeconds + 1) }
                }
            }
        }
    }

    fun pauseTracking() {
        val state = _uiState.value
        if (!state.isTracking) return
        val now = System.currentTimeMillis()

        viewModelScope.launch {
            val pausedState = sessionManager.togglePause(taskId, state.isPaused, now) ?: return@launch
            _uiState.update { it.copy(isPaused = pausedState) }
            if (pausedState) {
                timerJob?.cancel()
            } else {
                startTimerTick()
            }
        }
    }

    /** Pauses ALL open sessions across all tasks — called when leaving the app/focus screen */
    fun pauseAllTracking() {
        timerJob?.cancel()
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            sessionManager.pauseAllOpenSessions(now)
            _uiState.update { it.copy(isPaused = true) }
        }
    }

    // ── STOP CONFIRMATION (3 steps) ───────────────────────────────────────────

    fun showTrackingBlockDialog() {
        _uiState.update { it.copy(showTrackingBlockDialog = true) }
    }

    fun dismissTrackingBlockDialog() {
        _uiState.update { it.copy(showTrackingBlockDialog = false) }
    }

    fun pauseAndLeave(onLeave: () -> Unit) {
        _uiState.update { it.copy(showTrackingBlockDialog = false) }
        pauseAllTracking()
        onLeave()
    }

    fun requestStop() {
        _uiState.update { it.copy(stopConfirmStep = 1) }
    }

    fun advanceStopConfirm() {
        val step = _uiState.value.stopConfirmStep
        if (step < 3) {
            _uiState.update { it.copy(stopConfirmStep = step + 1) }
        } else {
            confirmStop()
        }
    }

    fun cancelStop() {
        _uiState.update { it.copy(stopConfirmStep = 0) }
    }

    private fun confirmStop() {
        _uiState.update { it.copy(stopConfirmStep = 0) }
        finalizeSession()
    }

    private fun finalizeSession() {
        timerJob?.cancel()
        if (_uiState.value.activeSessionId == null) {
            _uiState.update { it.copy(isTracking = false, isPaused = false, elapsedSeconds = 0) }
            return
        }
        viewModelScope.launch {
            finalizeSessionSuspend()
        }
    }

    /** Suspending version — call this from within a coroutine to ensure DB write completes before proceeding. */
    private suspend fun finalizeSessionSuspend() {
        timerJob?.cancel()
        val state = _uiState.value
        state.activeSessionId ?: run {
            _uiState.update { it.copy(isTracking = false, isPaused = false, elapsedSeconds = 0) }
            return
        }

        val now = System.currentTimeMillis()
        sessionManager.finalizeSession(taskId, now)
        _uiState.update {
            it.copy(isTracking = false, isPaused = false, elapsedSeconds = 0, activeSessionId = null)
        }

    }

    // ── POMODORO ──────────────────────────────────────────────────────────────

    fun startPomodoro() {
        _uiState.update { it.copy(pomodoroActive = true, pomodoroSeconds = 0) }
        val total = _uiState.value.pomodoroTotal
        pomodoroJob = viewModelScope.launch {
            while (_uiState.value.pomodoroSeconds < total) {
                delay(1000)
                _uiState.update { it.copy(pomodoroSeconds = it.pomodoroSeconds + 1) }
            }
            _uiState.update { it.copy(pomodoroActive = false) }
        }
    }

    fun stopPomodoro() {
        pomodoroJob?.cancel()
        _uiState.update { it.copy(pomodoroActive = false, pomodoroSeconds = 0) }
    }

    // ── COMPLETE ──────────────────────────────────────────────────────────────

    fun completeTask() {
        // Only prompt for manual time log if the task has an estimated duration and no time was tracked
        val state = _uiState.value
        val hasTrackedTime = state.sessions.any { it.endedAt != null && it.durationMinutes > 0f }
        val hasEstimate = (state.task?.estimatedDurationMinutes ?: 0) > 0
        if (!state.isTracking && !hasTrackedTime && hasEstimate) {
            _uiState.update { it.copy(showManualTimeLog = true) }
            return
        }
        doCompleteTask(manualMinutes = null)
    }

    fun completeWithManualTime(minutes: Float) {
        _uiState.update { it.copy(showManualTimeLog = false) }
        doCompleteTask(manualMinutes = minutes.takeIf { it > 0f })
    }

    fun dismissManualTimeLog() {
        _uiState.update { it.copy(showManualTimeLog = false) }
        doCompleteTask(manualMinutes = null)
    }

    private fun doCompleteTask(manualMinutes: Float?) {
        val wasTracking = _uiState.value.isTracking
        stopPomodoro()
        AutonomyNudgeEngine.cancelNudge(applicationContext, taskId)
        viewModelScope.launch {
            // Finalize session first and await DB write before reading sessions
            if (wasTracking) {
                finalizeSessionSuspend()
            }
            val outcome = completionManager.completeTask(taskId, manualMinutes) ?: return@launch

            _uiState.update {
                it.copy(
                    isCompleted = true,
                    pointsEarned = outcome.pointsEarned,
                    showCompletionSheet = true,
                    completedHabitStreak = outcome.newHabitStreak,
                    showAffordanceRating = true,
                    completionAffirmation = it.preferences.affirmations
                        .takeIf { list -> list.isNotEmpty() }
                        ?.random()
                        ?: defaultAffirmations.random()
                )
            }
        }
    }

    fun dismissCompletion() {
        _uiState.update { it.copy(showCompletionSheet = false, completionAffirmation = "") }
    }

    fun requestSkipFeedback() {
        _uiState.update { it.copy(showSkipFeedback = true) }
    }

    fun dismissSkipFeedback() {
        _uiState.update { it.copy(showSkipFeedback = false) }
    }

    fun skipTask(reason: String = "skipped") {
        _uiState.update { it.copy(showSkipFeedback = false) }
        skippedTaskIds.add(taskId)
        stopPomodoro()
        viewModelScope.launch {
            val task = taskRepository.getById(taskId)
            task?.let {
                taskRepository.update(it.copy(postponeCount = it.postponeCount + 1, updatedAt = System.currentTimeMillis()))
                woopManager.recordFeedback(taskId, "MISSED", reason)
            }
        }
    }

    /** Clears the waitingFor blocker — removes the -50pt penalty and marks it resolved */
    fun resolveWaitingFor() {
        viewModelScope.launch {
            val task = taskRepository.getById(taskId) ?: return@launch
            taskRepository.update(task.copy(waitingFor = "", updatedAt = System.currentTimeMillis()))
        }
    }

    fun setStepCompleted(stepIndex: Int, completed: Boolean) {
        viewModelScope.launch {
            val task = taskRepository.getById(taskId) ?: return@launch
            if (task.ifThenPlan.isBlank()) return@launch

            val updatedPlan = StepByStepPlanCodec.setStepCompleted(
                raw = task.ifThenPlan,
                index = stepIndex,
                completed = completed
            )
            if (updatedPlan == task.ifThenPlan) return@launch

            taskRepository.update(
                task.copy(
                    ifThenPlan = updatedPlan,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    /**
     * Schedules OneTimeWorkRequests for each reminder flag set on the task.
     * Flags: 15min=1, 30min=2, 1hr=4, 1day=8 — before deadline or scheduled time.
     */
    fun scheduleReminders(task: TaskEntity) {
        reminderScheduler.schedule(
            task = task,
            notificationsEnabled = _uiState.value.preferences.deadlineReminderNotificationsEnabled,
            applicationContext = applicationContext
        )
    }

    companion object {
        val defaultAffirmations = listOf(
            "Every task completed is a step forward.",
            "You showed up. That's what matters.",
            "Progress over perfection.",
            "Small wins build big momentum.",
            "You are building the habit of finishing."
        )
    }

    override fun onCleared() {        super.onCleared()
        scoreTickJob?.cancel()
        timerJob?.cancel()
        pomodoroJob?.cancel()
        launchCountdownJob?.cancel()
        navigationInterstitialJob?.cancel()
        // NOTE: open session stays in DB — timer resumes on next visit
    }

    private var navigationInterstitialJob: Job? = null

    fun onNavigationAttempted() {
        if (!_uiState.value.isTracking) return
        viewModelScope.launch {
            sessionManager.recordInterruptionBurst(taskId, appSwitchDelta = 1)
        }
        navigationInterstitialJob?.cancel()
        _uiState.update { it.copy(showNavigationInterstitial = true, navigationInterstitialSecondsLeft = 3) }
        navigationInterstitialJob = viewModelScope.launch {
            for (i in 2 downTo 0) {
                delay(1_000)
                _uiState.update { it.copy(navigationInterstitialSecondsLeft = i) }
            }
            onInterstitialExpired()
        }
    }

    fun onInterstitialExpired() {
        navigationInterstitialJob?.cancel()
        _uiState.update { it.copy(showNavigationInterstitial = false) }
    }

    private fun startLaunchCountdownIfNeeded() {
        if (_uiState.value.isTracking) return
        launchCountdownJob = viewModelScope.launch {
            // Read prefs fresh — loadPreferences() may not have emitted yet when this is called
            val prefs = preferencesDataStore.preferencesFlow.first()
            if (!prefs.autoTrackerEnabled) return@launch
            delay(8_000) // wait 8 seconds
            if (_uiState.value.isTracking) return@launch // user started manually
            // Start 5→0 countdown
            _uiState.update { it.copy(showLaunchCountdown = true, launchCountdownValue = 5) }
            for (i in 4 downTo 0) {
                delay(1_000)
                if (_uiState.value.isTracking) {
                    _uiState.update { it.copy(showLaunchCountdown = false) }
                    return@launch
                }
                _uiState.update { it.copy(launchCountdownValue = i) }
            }
            // Countdown reached 0 — auto-start
            _uiState.update { it.copy(showLaunchCountdown = false) }
            startTracking()
        }
    }
}
