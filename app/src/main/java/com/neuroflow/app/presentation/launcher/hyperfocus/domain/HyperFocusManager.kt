package com.neuroflow.app.presentation.launcher.hyperfocus.domain

import android.content.Context
import android.content.Intent
import android.util.Log
import com.neuroflow.app.data.repository.TaskRepository
import com.neuroflow.app.domain.model.HyperFocusState
import com.neuroflow.app.domain.model.RewardTier
import com.neuroflow.app.domain.model.TaskStatus
import com.neuroflow.app.presentation.launcher.hyperfocus.data.HyperFocusDataStore
import com.neuroflow.app.presentation.launcher.hyperfocus.data.HyperFocusPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import com.neuroflow.app.presentation.launcher.hyperfocus.service.UnlockTimerService
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.neuroflow.app.worker.AccessibilityWatchdogWorker
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed class UnlockResult {
    data class Success(val unlockMinutes: Int) : UnlockResult()
    object InvalidCode : UnlockResult()
    data class Lockout(val secondsRemaining: Long) : UnlockResult()
}

interface HyperFocusManager {
    suspend fun activate(blockedPackages: Set<String>, dailyTaskTarget: Int, lockedTaskIds: Set<String>)
    suspend fun deactivate()
    suspend fun onTaskCompleted()
    suspend fun submitCode(enteredCode: String): UnlockResult
    suspend fun completePlanning()
    suspend fun updateHeartbeat()
    suspend fun reportTamper(reason: String)
}

@Singleton
class HyperFocusManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val hyperFocusDataStore: HyperFocusDataStore,
    private val unlockCodeRepository: UnlockCodeRepository,
    private val taskRepository: TaskRepository
) : HyperFocusManager {

    companion object {
        private const val TAG = "HyperFocusManager"
    }

    override suspend fun deactivate() {
        val sessionId = hyperFocusDataStore.current().sessionId
        Log.d(TAG, "Deactivating Hyper Focus session: $sessionId")
        hyperFocusDataStore.update { HyperFocusPreferences() }
        if (sessionId != null) {
            unlockCodeRepository.deleteSessionCodes(sessionId)
        }
        // Stop both timer and monitor services
        context.stopService(Intent(context, UnlockTimerService::class.java))
        context.stopService(
            Intent(context, com.neuroflow.app.presentation.launcher.hyperfocus.service.HyperFocusMonitorService::class.java)
        )
        WorkManager.getInstance(context).cancelUniqueWork(AccessibilityWatchdogWorker.WORK_NAME)
    }

    override suspend fun activate(blockedPackages: Set<String>, dailyTaskTarget: Int, lockedTaskIds: Set<String>) {
        val sessionId = UUID.randomUUID().toString()

        Log.d(TAG, "Activating Hyper Focus - Session: $sessionId")
        Log.d(TAG, "Blocked packages (${blockedPackages.size}): ${blockedPackages.joinToString()}")
        Log.d(TAG, "Daily task target: $dailyTaskTarget")

        // Clean up expired codes from previous sessions, then generate fresh pool
        unlockCodeRepository.deleteExpiredCodes()
        unlockCodeRepository.generateCodePool(sessionId)

        hyperFocusDataStore.update {
            HyperFocusPreferences(
                isActive = true,
                sessionId = sessionId,
                state = HyperFocusState.ACTIVE,
                blockedPackages = blockedPackages,
                dailyTaskTarget = dailyTaskTarget,
                tasksCompletedAtActivation = 0,
                currentTier = RewardTier.NONE,
                activeUnlockExpiresAt = null,
                wrongCodeAttempts = 0,
                lockoutExpiresAt = null,
                lockedTaskIds = lockedTaskIds
            )
        }

        // Start the monitor service to keep Hyper Focus active
        context.startForegroundService(
            Intent(context, com.neuroflow.app.presentation.launcher.hyperfocus.service.HyperFocusMonitorService::class.java)
        )

        // Schedule periodic accessibility watchdog (every 15 min)
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            AccessibilityWatchdogWorker.WORK_NAME,
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<AccessibilityWatchdogWorker>(15, TimeUnit.MINUTES).build()
        )
    }

    override suspend fun onTaskCompleted() {
        val prefs = hyperFocusDataStore.current()
        if (!prefs.isActive) return

        // Only count tasks that were active when the session started
        val completedLockedTasks = if (prefs.lockedTaskIds.isNotEmpty()) {
            taskRepository.getAllTasks()
                .count { it.id in prefs.lockedTaskIds && it.status == TaskStatus.COMPLETED }
        } else {
            // Fallback for sessions started before this fix (no lockedTaskIds stored)
            taskRepository.getAllTasks().count { it.status == TaskStatus.COMPLETED } - prefs.tasksCompletedAtActivation
        }
        val completedSinceActivation = completedLockedTasks.coerceAtLeast(0)
        val newTier = RewardEngine.computeTier(completedSinceActivation, prefs.dailyTaskTarget)

        if (newTier.ordinal > prefs.currentTier.ordinal ||
            (completedSinceActivation >= prefs.dailyTaskTarget &&
             prefs.state != HyperFocusState.FULL_REWARD_PENDING &&
             prefs.state != HyperFocusState.FULLY_UNLOCKED)
        ) {
            hyperFocusDataStore.update {
                val updatedTier = if (newTier.ordinal > it.currentTier.ordinal) newTier else it.currentTier
                val updatedState = if (completedSinceActivation >= prefs.dailyTaskTarget &&
                    it.state != HyperFocusState.FULL_REWARD_PENDING &&
                    it.state != HyperFocusState.FULLY_UNLOCKED
                ) HyperFocusState.FULL_REWARD_PENDING else it.state
                it.copy(currentTier = updatedTier, state = updatedState)
            }
        }
    }

    override suspend fun submitCode(enteredCode: String): UnlockResult {
        val prefs = hyperFocusDataStore.current()
        val now = System.currentTimeMillis()

        if (prefs.lockoutExpiresAt != null && prefs.lockoutExpiresAt > now) {
            return UnlockResult.Lockout((prefs.lockoutExpiresAt - now) / 1000)
        }

        val normalized = enteredCode.trim().uppercase()
        val sessionId = prefs.sessionId ?: return UnlockResult.InvalidCode
        val match = unlockCodeRepository.validateAndClaim(sessionId, normalized)

        if (match == null) {
            val newAttempts = prefs.wrongCodeAttempts + 1
            if (newAttempts >= 3) {
                hyperFocusDataStore.update {
                    it.copy(wrongCodeAttempts = 0, lockoutExpiresAt = now + 5 * 60 * 1000L)
                }
            } else {
                hyperFocusDataStore.update { it.copy(wrongCodeAttempts = newAttempts) }
            }
            return UnlockResult.InvalidCode
        }

        val expiresAt = if (match.tier == RewardTier.FULL) null
                        else now + match.tier.unlockMinutes * 60_000L
        unlockCodeRepository.markUsed(match.id, expiresAt)
        hyperFocusDataStore.update {
            val newState = if (match.tier == RewardTier.FULL)
                com.neuroflow.app.domain.model.HyperFocusState.FULL_REWARD_PENDING
            else
                it.state
            it.copy(
                activeUnlockExpiresAt = expiresAt,
                wrongCodeAttempts = 0,
                pendingCodeId = null,
                state = newState
            )
        }

        // Start the foreground timer service for timed unlocks (FULL tier = permanent, no timer needed)
        if (expiresAt != null) {
            context.startForegroundService(Intent(context, UnlockTimerService::class.java))
        }

        return UnlockResult.Success(match.tier.unlockMinutes)
    }

    override suspend fun completePlanning() {
        // Read sessionId first, then update state atomically
        val sessionId = hyperFocusDataStore.current().sessionId
        // Grant the full unlock now that tomorrow's tasks are planned
        hyperFocusDataStore.update {
            HyperFocusPreferences(
                isActive = true,
                sessionId = it.sessionId,
                state = HyperFocusState.FULLY_UNLOCKED,
                blockedPackages = it.blockedPackages,
                dailyTaskTarget = it.dailyTaskTarget,
                tasksCompletedAtActivation = it.tasksCompletedAtActivation,
                currentTier = it.currentTier,
                activeUnlockExpiresAt = null,
                wrongCodeAttempts = 0,
                lockoutExpiresAt = null,
                lockedTaskIds = it.lockedTaskIds
            )
        }
        sessionId?.let { unlockCodeRepository.deleteSessionCodes(it) }
        // Stop timer service — full unlock has no timer
        context.stopService(Intent(context, UnlockTimerService::class.java))
        // Stop monitor service — session is effectively over (apps are unlocked)
        context.stopService(
            Intent(context, com.neuroflow.app.presentation.launcher.hyperfocus.service.HyperFocusMonitorService::class.java)
        )
        // Cancel the accessibility watchdog — session is over
        WorkManager.getInstance(context).cancelUniqueWork(AccessibilityWatchdogWorker.WORK_NAME)
    }

    override suspend fun updateHeartbeat() {
        hyperFocusDataStore.updateHeartbeat(System.currentTimeMillis())
    }

    override suspend fun reportTamper(reason: String) {
        val timestamp = System.currentTimeMillis()
        Log.w(TAG, "HyperFocus tamper detected: $reason")
        hyperFocusDataStore.update {
            it.copy(
                isTamperDetected = true,
                tamperReason = reason,
                tamperDetectedAt = timestamp
            )
        }
    }
}

