package com.neuroflow.app.presentation.focus

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neuroflow.app.data.local.UserPreferences
import com.neuroflow.app.data.local.UserPreferencesDataStore
import com.neuroflow.app.data.local.entity.TaskEntity
import com.neuroflow.app.data.local.entity.TimeSessionEntity
import com.neuroflow.app.data.repository.SessionRepository
import com.neuroflow.app.data.repository.TaskRepository
import com.neuroflow.app.domain.engine.AnalyticsEngine
import com.neuroflow.app.domain.engine.TaskScoringEngine
import com.neuroflow.app.domain.model.Recurrence
import com.neuroflow.app.domain.model.TaskStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
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
    // Live scoring
    val currentScore: Int = 0,
    val urgencyFraction: Float = 0f,
    val urgencyLabel: String = "",
    val scoreBreakdown: List<Pair<String, Float>> = emptyList(),
    // All active tasks for dependency scoring
    val allActiveTasks: List<TaskEntity> = emptyList()
)

@HiltViewModel
class FocusViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val taskRepository: TaskRepository,
    private val sessionRepository: SessionRepository,
    private val preferencesDataStore: UserPreferencesDataStore,
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

    init {
        loadTask()
        loadSessions()
        loadPreferences()
        startScoreTick()
        observeNextTask()
        restoreActiveSession()
    }

    private var lastScheduledReminderFlags: Int = -1

    private fun loadTask() {
        viewModelScope.launch {
            taskRepository.observeById(taskId).collect { task ->
                _uiState.update { it.copy(task = task) }
                refreshScore()
                // Only re-schedule reminders if reminderFlags actually changed
                if (task != null && task.reminderFlags != 0 && task.reminderFlags != lastScheduledReminderFlags) {
                    lastScheduledReminderFlags = task.reminderFlags
                    scheduleReminders(task)
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
                _uiState.update {
                    it.copy(preferences = prefs, pomodoroTotal = prefs.defaultPomodoroMinutes * 60)
                }
                refreshScore()
            }
        }
    }

    /**
     * On init, check if there's already an open session for this task in the DB.
     * If yes, restore the elapsed time and resume the timer (or keep paused state).
     */
    private fun restoreActiveSession() {
        viewModelScope.launch {
            val openSession = sessionRepository.getOpenSessionForTask(taskId) ?: return@launch
            val now = System.currentTimeMillis()
            val isPaused = openSession.pausedAt != null

            // Elapsed = (now - startedAt) - totalPausedMs - (time since last pause if paused)
            val pausedSinceMs = if (isPaused) now - openSession.pausedAt!! else 0L
            val elapsedMs = (now - openSession.startedAt) - openSession.totalPausedMs - pausedSinceMs
            val elapsedSec = maxOf(0L, elapsedMs / 1000L)

            _uiState.update {
                it.copy(
                    isTracking = true,
                    isPaused = isPaused,
                    elapsedSeconds = elapsedSec,
                    activeSessionId = openSession.id
                )
            }

            if (!isPaused) startTimerTick()
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
        val now = System.currentTimeMillis()

        viewModelScope.launch {
            // Persist open session to DB immediately — survives leaving the page
            val session = TimeSessionEntity(
                taskId = taskId,
                startedAt = now,
                endedAt = null,
                pausedAt = null,
                totalPausedMs = 0L,
                sessionType = "MANUAL"
            )
            sessionRepository.insert(session)
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
            val session = sessionRepository.getOpenSessionForTask(taskId) ?: return@launch
            if (!state.isPaused) {
                sessionRepository.update(session.copy(pausedAt = now))
                _uiState.update { it.copy(isPaused = true) }
            } else {
                val pausedDuration = now - (session.pausedAt ?: now)
                sessionRepository.update(
                    session.copy(
                        pausedAt = null,
                        totalPausedMs = session.totalPausedMs + pausedDuration
                    )
                )
                _uiState.update { it.copy(isPaused = false) }
            }
        }
    }

    /** Pauses ALL open sessions across all tasks — called when leaving the app/focus screen */
    fun pauseAllTracking() {
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            val openSessions = sessionRepository.getOpenSessions()
            openSessions.forEach { session ->
                if (session.pausedAt == null) {
                    sessionRepository.update(session.copy(pausedAt = now))
                }
            }
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
        val state = _uiState.value
        val sessionId = state.activeSessionId ?: run {
            _uiState.update { it.copy(isTracking = false, isPaused = false, elapsedSeconds = 0) }
            return
        }

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val session = sessionRepository.getOpenSessionForTask(taskId)
            if (session != null) {
                // If still paused when stopped, finalize pause duration
                val extraPausedMs = if (session.pausedAt != null) now - session.pausedAt else 0L
                val totalPaused = session.totalPausedMs + extraPausedMs
                val elapsedMs = (now - session.startedAt) - totalPaused
                val durationMinutes = maxOf(0f, elapsedMs / 60_000f)

                sessionRepository.update(
                    session.copy(
                        endedAt = now,
                        pausedAt = null,
                        totalPausedMs = totalPaused,
                        durationMinutes = durationMinutes
                    )
                )

                val task = taskRepository.getById(taskId)
                task?.let {
                    taskRepository.update(
                        it.copy(
                            totalTimeTrackedMinutes = it.totalTimeTrackedMinutes + durationMinutes,
                            sessionCount = it.sessionCount + 1,
                            lastSessionDurationMinutes = durationMinutes,
                            updatedAt = now
                        )
                    )
                }
            }
            _uiState.update {
                it.copy(isTracking = false, isPaused = false, elapsedSeconds = 0, activeSessionId = null)
            }
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
        if (_uiState.value.isTracking) finalizeSession()
        stopPomodoro()
        viewModelScope.launch {
            val task = taskRepository.getById(taskId) ?: return@launch
            val sessions = sessionRepository.getByTaskId(taskId)
            // Only count closed sessions with real duration — open/zero sessions skew MAPE
            val actualDuration = sessions
                .filter { it.endedAt != null && it.durationMinutes > 0f }
                .sumOf { it.durationMinutes.toDouble() }.toFloat()

            // Only compute MAPE/SMAPE when we have both an estimate and actual tracked time
            val mape = if (task.estimatedDurationMinutes > 0 && actualDuration > 0f)
                AnalyticsEngine.computeMape(task.estimatedDurationMinutes.toFloat(), actualDuration) else null
            val smape = if (task.estimatedDurationMinutes > 0 && actualDuration > 0f)
                AnalyticsEngine.computeSmape(task.estimatedDurationMinutes.toFloat(), actualDuration) else null

            val points = task.impactScore / 10
            val now = System.currentTimeMillis()

            taskRepository.update(
                task.copy(
                    status = TaskStatus.COMPLETED,
                    completedAt = now,
                    actualDurationMinutes = actualDuration,
                    estimationErrorMape = mape,
                    estimationErrorSmape = smape,
                    focusModePoints = points,
                    isHabitual = task.recurrence != Recurrence.NONE,
                    habitStreak = if (task.recurrence != Recurrence.NONE) task.habitStreak + 1 else task.habitStreak,
                    updatedAt = now
                )
            )

            if (task.recurrence != Recurrence.NONE) {
                val intervalMs = when (task.recurrence) {
                    Recurrence.DAILY   -> 86_400_000L
                    Recurrence.WEEKLY  -> 7 * 86_400_000L
                    Recurrence.MONTHLY -> 30 * 86_400_000L
                    Recurrence.CUSTOM  -> task.recurrenceIntervalDays * 86_400_000L
                    Recurrence.NONE    -> 0L
                }
                taskRepository.insert(
                    task.copy(
                        id = UUID.randomUUID().toString(),
                        status = TaskStatus.ACTIVE,
                        completedAt = null,
                        deadlineDate = task.deadlineDate?.plus(intervalMs),
                        scheduledDate = task.scheduledDate?.plus(intervalMs),
                        isScheduleLocked = task.isScheduleLocked,
                        totalTimeTrackedMinutes = 0f,
                        sessionCount = 0,
                        lastSessionDurationMinutes = null,
                        actualDurationMinutes = null,
                        estimationErrorMape = null,
                        estimationErrorSmape = null,
                        focusModePoints = 0,
                        postponeCount = 0,
                        habitStreak = task.habitStreak + 1,
                        isHabitual = true,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }

            preferencesDataStore.updatePreferences { prefs ->
                val now2 = System.currentTimeMillis()
                val todayStart = run {
                    val cal = java.util.Calendar.getInstance()
                    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                    cal.set(java.util.Calendar.MINUTE, 0)
                    cal.set(java.util.Calendar.SECOND, 0)
                    cal.set(java.util.Calendar.MILLISECOND, 0)
                    cal.timeInMillis
                }
                val yesterdayStart = todayStart - 86_400_000L
                // Increment streak if last active was yesterday (continuing) or today (already counted)
                // Reset to 1 if last active was before yesterday (streak broken)
                val newStreak = when {
                    prefs.lastActiveDate >= todayStart -> prefs.dailyStreak  // already counted today
                    prefs.lastActiveDate >= yesterdayStart -> prefs.dailyStreak + 1  // continuing streak
                    else -> 1  // streak broken — restart
                }
                prefs.copy(
                    totalTasksCompleted = prefs.totalTasksCompleted + 1,
                    totalFocusMinutes = prefs.totalFocusMinutes + actualDuration.toInt(),
                    dailyStreak = newStreak,
                    lastActiveDate = now2,
                    longestStreak = maxOf(prefs.longestStreak, newStreak)
                )
            }

            _uiState.update { it.copy(isCompleted = true, pointsEarned = points, showCompletionSheet = true) }
        }
    }

    fun dismissCompletion() {
        _uiState.update { it.copy(showCompletionSheet = false) }
    }

    fun skipTask() {
        skippedTaskIds.add(taskId)
        stopPomodoro()
        viewModelScope.launch {
            val task = taskRepository.getById(taskId)
            task?.let {
                taskRepository.update(it.copy(postponeCount = it.postponeCount + 1, updatedAt = System.currentTimeMillis()))
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

    /**
     * Schedules OneTimeWorkRequests for each reminder flag set on the task.
     * Flags: 15min=1, 30min=2, 1hr=4, 1day=8 — before deadline or scheduled time.
     */
    fun scheduleReminders(task: TaskEntity) {
        val targetMs = task.deadlineDate?.let { it + (task.deadlineTime ?: 0L) }
            ?: task.scheduledDate?.let { it + (task.scheduledTime ?: 0L) }
            ?: return  // no time anchor — nothing to schedule against

        val now = System.currentTimeMillis()
        val flags = task.reminderFlags
        val offsets = listOf(1 to 15L, 2 to 30L, 4 to 60L, 8 to 1440L)

        offsets.forEach { (flag, minutesBefore) ->
            if (flags and flag != 0) {
                val fireAt = targetMs - minutesBefore * 60_000L
                val delayMs = fireAt - now
                if (delayMs > 0) {
                    val data = androidx.work.Data.Builder()
                        .putString("taskId", task.id)
                        .putString("taskTitle", task.title)
                        .putLong("targetMs", targetMs)
                        .putLong("minutesBefore", minutesBefore)
                        .build()
                    val request = androidx.work.OneTimeWorkRequestBuilder<com.neuroflow.app.worker.TaskReminderWorker>()
                        .setInitialDelay(delayMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                        .setInputData(data)
                        .addTag("reminder_${task.id}_${flag}")
                        .build()
                    // Cancel any existing reminder for this task+flag before scheduling
                    androidx.work.WorkManager.getInstance(applicationContext)
                        .cancelAllWorkByTag("reminder_${task.id}_${flag}")
                    androidx.work.WorkManager.getInstance(applicationContext)
                        .enqueue(request)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        scoreTickJob?.cancel()
        timerJob?.cancel()
        pomodoroJob?.cancel()
        // NOTE: open session stays in DB — timer resumes on next visit
    }
}
