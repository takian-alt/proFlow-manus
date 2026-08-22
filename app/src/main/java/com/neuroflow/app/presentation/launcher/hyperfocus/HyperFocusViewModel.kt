package com.neuroflow.app.presentation.launcher.hyperfocus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neuroflow.app.data.repository.TaskRepository
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
        val completedSinceActivation = if (prefs.lockedTaskIds.isNotEmpty()) {
            taskRepository.getAllTasks()
                .count { it.id in prefs.lockedTaskIds && it.status == TaskStatus.COMPLETED }
                .coerceAtLeast(0)
        } else {
            val totalCompleted = taskRepository.getAllTasks().count { it.status == TaskStatus.COMPLETED }
            (totalCompleted - prefs.tasksCompletedAtActivation).coerceAtLeast(0)
        }
        val fraction = completedSinceActivation.toFloat() / prefs.dailyTaskTarget.coerceAtLeast(1)
        val currentTier = RewardEngine.computeTier(completedSinceActivation, prefs.dailyTaskTarget)
        val tasksToNextTier = RewardEngine.tasksToNextTier(completedSinceActivation, prefs.dailyTaskTarget)
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
    }

    val lockoutSecondsRemaining: StateFlow<Long?> = combine(tickerFlow, hyperFocusDataStore.flow) { _, prefs ->
        val expiresAt = prefs.lockoutExpiresAt ?: return@combine null
        val remaining = (expiresAt - System.currentTimeMillis()) / 1000
        if (remaining > 0) remaining else null
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val activeTaskCount: StateFlow<Int> = flow {
        while (true) {
            emit(taskRepository.getActiveTasks().size)
            delay(2000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun activate(blockedPackages: Set<String>) {
        viewModelScope.launch {
            // Single DB read — pass the list to the manager so both target count
            // and lockedTaskIds snapshot come from the exact same query result
            val activeTasks = taskRepository.getActiveTasks()
            if (activeTasks.isEmpty()) return@launch
            hyperFocusManager.activate(blockedPackages, activeTasks.size, activeTasks.map { it.id }.toSet())
        }
    }

    fun claimRewardForTier(tier: RewardTier) {
        viewModelScope.launch {
            val prefs = hyperFocusDataStore.current()
            val sessionId = prefs.sessionId ?: return@launch

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
