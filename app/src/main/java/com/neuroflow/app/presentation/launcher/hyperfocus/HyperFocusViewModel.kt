package com.neuroflow.app.presentation.launcher.hyperfocus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neuroflow.app.data.repository.TaskRepository
import com.neuroflow.app.data.local.entity.TaskEntity
import com.neuroflow.app.domain.model.HyperFocusSessionMode
import com.neuroflow.app.domain.model.RewardTier
import com.neuroflow.app.domain.model.TaskStatus
import com.neuroflow.app.presentation.launcher.hyperfocus.data.HyperFocusDataStore
import com.neuroflow.app.presentation.launcher.hyperfocus.data.HyperFocusPreferences
import com.neuroflow.app.presentation.launcher.hyperfocus.domain.HyperFocusManager
import com.neuroflow.app.presentation.launcher.hyperfocus.domain.RewardEngine
import com.neuroflow.app.presentation.launcher.hyperfocus.domain.UnlockCodeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HyperFocusProgress(
    val completedSinceActivation: Int,
    val totalTarget: Int,
    val currentTier: RewardTier,
    val fraction: Float,
    val tasksToNextTier: Int
)

@HiltViewModel
class HyperFocusViewModel @Inject constructor(
    private val hyperFocusManager: HyperFocusManager,
    private val hyperFocusDataStore: HyperFocusDataStore,
    private val unlockCodeRepository: UnlockCodeRepository,
    private val taskRepository: TaskRepository,
) : ViewModel() {

    val hyperFocusPrefs: StateFlow<HyperFocusPreferences> = hyperFocusDataStore.flow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HyperFocusPreferences())

    private val tickerFlow = flow {
        while (true) {
            emit(Unit)
            delay(1000)
        }
    }

    val progress: StateFlow<HyperFocusProgress> = combine(
        hyperFocusDataStore.flow,
        tickerFlow
    ) { prefs, _ ->
        if (prefs.sessionMode == HyperFocusSessionMode.TIME_BASED) {
            return@combine HyperFocusProgress(
                completedSinceActivation = 0,
                totalTarget = 0,
                currentTier = RewardTier.NONE,
                fraction = 0f,
                tasksToNextTier = 0
            )
        }

        val completedSinceActivation = if (prefs.lockedTaskIds.isNotEmpty()) {
            taskRepository.getAllTasks()
                .count { it.id in prefs.lockedTaskIds && it.status == TaskStatus.COMPLETED }
                .coerceAtLeast(0)
        } else {
            val totalCompleted = taskRepository.getAllTasks().count { it.status == TaskStatus.COMPLETED }
            (totalCompleted - prefs.tasksCompletedAtActivation).coerceAtLeast(0)
        }
        val fraction = completedSinceActivation.toFloat() / prefs.dailyTaskTarget.coerceAtLeast(1)
        val currentTier = RewardEngine.computeTier(completedSinceActivation, prefs.dailyTaskTarget, prefs.emergencyUsed)
        val tasksToNextTier = RewardEngine.tasksToNextTier(completedSinceActivation, prefs.dailyTaskTarget, prefs.emergencyUsed)
        HyperFocusProgress(
            completedSinceActivation = completedSinceActivation,
            totalTarget = prefs.dailyTaskTarget,
            currentTier = currentTier,
            fraction = fraction.coerceIn(0f, 1f),
            tasksToNextTier = tasksToNextTier
        )
    }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            HyperFocusProgress(0, 0, RewardTier.NONE, 0f, 1)
        )

    val isUnlockActive: StateFlow<Boolean> = hyperFocusDataStore.flow
        .map { RewardEngine.isUnlockActive(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val unlockSecondsRemaining: StateFlow<Long?> = combine(tickerFlow, hyperFocusDataStore.flow) { _, prefs ->
        RewardEngine.secondsRemaining(prefs)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val sessionSecondsRemaining: StateFlow<Long?> = combine(tickerFlow, hyperFocusDataStore.flow) { _, prefs ->
        if (!prefs.isActive || prefs.sessionMode != HyperFocusSessionMode.TIME_BASED) {
            return@combine null
        }
        val endsAt = prefs.sessionEndsAtMillis ?: return@combine null
        ((endsAt - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val claimedCodeToShow: MutableStateFlow<String?> = MutableStateFlow(null)
    // Which tier the currently shown code belongs to
    val claimedCodeTier: MutableStateFlow<RewardTier?> = MutableStateFlow(null)

    // Emits the result of the last submitCode call so the UI can react
    val submitCodeResult: MutableStateFlow<com.neuroflow.app.presentation.launcher.hyperfocus.domain.UnlockResult?> =
        MutableStateFlow(null)

    // Count of unused codes per tier — refreshed whenever prefs change
    val rewardCounts: MutableStateFlow<Map<RewardTier, Int>> = MutableStateFlow(emptyMap())

    init {
        viewModelScope.launch {
            hyperFocusDataStore.flow.collect { prefs ->
                val sessionId = prefs.sessionId ?: return@collect
                val counts = RewardTier.entries
                    .filter { it != RewardTier.NONE }
                    .associateWith { tier ->
                        unlockCodeRepository.countUnusedByTier(sessionId, tier)
                    }
                rewardCounts.value = counts
            }
        }

        viewModelScope.launch {
            combine(tickerFlow, hyperFocusDataStore.flow) { _, prefs -> prefs }
                .collect { prefs ->
                    if (!prefs.isActive || prefs.sessionMode != HyperFocusSessionMode.TIME_BASED) return@collect
                    val endsAt = prefs.sessionEndsAtMillis ?: return@collect
                    if (endsAt <= System.currentTimeMillis()) {
                        hyperFocusManager.deactivate()
                    }
                }
        }
    }

    val lockoutSecondsRemaining: StateFlow<Long?> = combine(tickerFlow, hyperFocusDataStore.flow) { _, prefs ->
        val expiresAt = prefs.lockoutExpiresAt ?: return@combine null
        val remaining = (expiresAt - System.currentTimeMillis()) / 1000
        if (remaining > 0) remaining else null
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val activeTasks: StateFlow<List<TaskEntity>> = taskRepository.observeActiveTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeTaskCount: StateFlow<Int> = flow {
        while (true) {
            emit(taskRepository.getActiveTasks().size)
            delay(2000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun activate(blockedPackages: Set<String>, selectedTaskIds: Set<String>) {
        viewModelScope.launch {
            if (blockedPackages.isEmpty()) return@launch

            val activeIds = taskRepository.getActiveTasks().map { it.id }.toSet()
            val normalizedSelection = selectedTaskIds.intersect(activeIds)
            if (normalizedSelection.isEmpty()) return@launch

            hyperFocusManager.activate(
                blockedPackages = blockedPackages,
                dailyTaskTarget = normalizedSelection.size,
                lockedTaskIds = normalizedSelection
            )
        }
    }

    fun activateTimed(blockedPackages: Set<String>, durationMinutes: Int) {
        viewModelScope.launch {
            if (blockedPackages.isEmpty()) return@launch
            hyperFocusManager.activateTimed(blockedPackages, durationMinutes)
        }
    }

    fun activateEmergencyBypass() {
        viewModelScope.launch {
            hyperFocusManager.triggerEmergencyBypass()
        }
    }

    fun addTasksToActiveSession(taskIds: Set<String>) {
        viewModelScope.launch {
            hyperFocusManager.addTasksToSession(taskIds)
        }
    }

    fun claimRewardForTier(tier: RewardTier) {
        viewModelScope.launch {
            val prefs = hyperFocusDataStore.current()
            if (prefs.sessionMode == HyperFocusSessionMode.TIME_BASED) return@launch
            val sessionId = prefs.sessionId ?: return@launch
            val currentProgress = progress.value

            val canClaimTier = RewardEngine.isTierEarned(
                tier = tier,
                completedSinceActivation = currentProgress.completedSinceActivation,
                totalTarget = currentProgress.totalTarget,
                emergencyUsed = prefs.emergencyUsed
            )
            if (!canClaimTier) return@launch

            // If there's already a pending code for this same tier, just show it again
            if (prefs.pendingCodeId != null && claimedCodeTier.value == tier) {
                val existing = unlockCodeRepository.getCodeById(prefs.pendingCodeId)
                claimedCodeToShow.value = existing
                return@launch
            }

            // Issue a new code for the requested tier
            val result = unlockCodeRepository.getClaimableCode(sessionId, tier) ?: return@launch
            val (codeId, plaintext) = result

            hyperFocusDataStore.update { it.copy(pendingCodeId = codeId) }
            claimedCodeTier.value = tier
            claimedCodeToShow.value = plaintext

            // Refresh counts
            val counts = RewardTier.entries
                .filter { it != RewardTier.NONE }
                .associateWith { t -> unlockCodeRepository.countUnusedByTier(sessionId, t) }
            rewardCounts.value = counts
        }
    }

    fun dismissClaimedCode() {
        claimedCodeToShow.value = null
        claimedCodeTier.value = null
    }

    fun submitCode(entered: String) {
        viewModelScope.launch {
            val result = hyperFocusManager.submitCode(entered)
            submitCodeResult.value = result
        }
    }

    fun clearSubmitCodeResult() {
        submitCodeResult.value = null
    }

    fun completePlanning(tomorrowTaskTitles: List<String>) {
        viewModelScope.launch {
            val cal = java.util.Calendar.getInstance()
            cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            val tomorrowMidnight = cal.timeInMillis
            val now = System.currentTimeMillis()
            
            tomorrowTaskTitles.filter { it.isNotBlank() }.forEach { title ->
                taskRepository.insert(
                    com.neuroflow.app.data.local.entity.TaskEntity(
                        title = title.trim(),
                        scheduledDate = tomorrowMidnight,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }
            hyperFocusManager.completePlanning()
        }
    }
}
