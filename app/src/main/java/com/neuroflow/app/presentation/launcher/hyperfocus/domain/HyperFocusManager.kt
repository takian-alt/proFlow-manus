package com.neuroflow.app.presentation.launcher.hyperfocus.domain

import android.content.Context
import android.content.Intent
import android.util.Log
import com.neuroflow.app.data.repository.TaskRepository
import com.neuroflow.app.domain.model.HyperFocusSessionMode
import com.neuroflow.app.domain.model.HyperFocusState
import com.neuroflow.app.domain.model.RewardTier
import com.neuroflow.app.domain.model.TaskStatus
import com.neuroflow.app.kiosk.DeviceOwnerKioskManager
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
    suspend fun activateTimed(blockedPackages: Set<String>, durationMinutes: Int)
    suspend fun addTasksToSession(taskIds: Set<String>)
    suspend fun isTaskDeletionBlocked(taskId: String): Boolean
    suspend fun deactivate()
    suspend fun onTaskCompleted()
    suspend fun submitCode(enteredCode: String): UnlockResult
    suspend fun completePlanning()
    suspend fun updateHeartbeat()
    suspend fun reportTamper(reason: String)
    suspend fun triggerEmergencyBypass()
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
        val currentPrefs = hyperFocusDataStore.current()
        val sessionId = currentPrefs.sessionId
        Log.d(TAG, "Deactivating Hyper Focus session: $sessionId")
        runCatching { DeviceOwnerKioskManager.setHyperFocusSelfProtection(context, false) }
        runCatching {
            DeviceOwnerKioskManager.syncHyperFocusBlockedPackagesSuspension(
                context,
                currentPrefs.blockedPackages,
                false
            )
        }
        hyperFocusDataStore.update { HyperFocusPreferences() }
        if (sessionId != null) {
            unlockCodeRepository.deleteSessionCodes(sessionId)
        }
        // Stop both timer and monitor services
        runCatching { context.stopService(Intent(context, UnlockTimerService::class.java)) }
        runCatching {
            context.stopService(
                Intent(context, com.neuroflow.app.presentation.launcher.hyperfocus.service.HyperFocusMonitorService::class.java)
            )
        }
        runCatching {
            WorkManager.getInstance(context).cancelUniqueWork(AccessibilityWatchdogWorker.WORK_NAME)
        }
    }

    override suspend fun activate(blockedPackages: Set<String>, dailyTaskTarget: Int, lockedTaskIds: Set<String>) {
        activateInternal(
            blockedPackages = blockedPackages,
            dailyTaskTarget = dailyTaskTarget,
            lockedTaskIds = lockedTaskIds,
            sessionMode = HyperFocusSessionMode.TASK_BASED,
            sessionDurationMinutes = null
        )
    }

    override suspend fun activateTimed(blockedPackages: Set<String>, durationMinutes: Int) {
        val safeDuration = durationMinutes.coerceAtLeast(1)
        activateInternal(
            blockedPackages = blockedPackages,
            dailyTaskTarget = 0,
            lockedTaskIds = emptySet(),
            sessionMode = HyperFocusSessionMode.TIME_BASED,
            sessionDurationMinutes = safeDuration
        )
    }

    override suspend fun addTasksToSession(taskIds: Set<String>) {
        if (taskIds.isEmpty()) return

        val prefs = hyperFocusDataStore.current()
        if (!prefs.isActive || prefs.sessionMode != HyperFocusSessionMode.TASK_BASED) return

        val now = System.currentTimeMillis()
        val validIds = taskRepository.getAllTasks()
            .filter { task ->
                task.id in taskIds &&
                    task.status == TaskStatus.ACTIVE &&
                    (task.scheduledDate == null || task.scheduledDate <= now)
            }
            .map { it.id }
            .toSet()

        if (validIds.isEmpty()) return

        hyperFocusDataStore.update {
            val mergedIds = it.lockedTaskIds + validIds
            if (mergedIds == it.lockedTaskIds) {
                it
            } else {
                it.copy(
                    lockedTaskIds = mergedIds,
                    dailyTaskTarget = mergedIds.size
                )
            }
        }

        // Recompute tier after adding tasks because newly-added tasks may already be completed later in the session.
        onTaskCompleted()
    }

    override suspend fun isTaskDeletionBlocked(taskId: String): Boolean {
        val prefs = hyperFocusDataStore.current()
        if (!prefs.isActive || prefs.sessionMode != HyperFocusSessionMode.TASK_BASED) return false
        return taskId in prefs.lockedTaskIds
    }

    private suspend fun activateInternal(
        blockedPackages: Set<String>,
        dailyTaskTarget: Int,
        lockedTaskIds: Set<String>,
        sessionMode: HyperFocusSessionMode,
        sessionDurationMinutes: Int?
    ) {
        val sessionId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val sessionEndsAtMillis = if (sessionMode == HyperFocusSessionMode.TIME_BASED) {
            now + (sessionDurationMinutes ?: 1) * 60_000L
        } else {
            null
        }

        Log.d(TAG, "Activating Hyper Focus - Session: $sessionId")
        Log.d(TAG, "Blocked packages (${blockedPackages.size}): ${blockedPackages.joinToString()}")
        Log.d(TAG, "Session mode: $sessionMode")
        if (sessionMode == HyperFocusSessionMode.TASK_BASED) {
            Log.d(TAG, "Daily task target: $dailyTaskTarget")
        } else {
            Log.d(TAG, "Duration: ${sessionDurationMinutes ?: 0} minute(s)")
        }

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
                lockedTaskIds = lockedTaskIds,
                sessionMode = sessionMode,
                sessionDurationMinutes = sessionDurationMinutes,
                sessionEndsAtMillis = sessionEndsAtMillis,
                lastServiceHeartbeat = now
            )
        }

        // Start the monitor service to keep Hyper Focus active
        runCatching {
            context.startForegroundService(
                Intent(context, com.neuroflow.app.presentation.launcher.hyperfocus.service.HyperFocusMonitorService::class.java)
            )
        }

        // Schedule periodic accessibility watchdog (every 15 min)
        runCatching {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                AccessibilityWatchdogWorker.WORK_NAME,
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<AccessibilityWatchdogWorker>(15, TimeUnit.MINUTES).build()
            )
        }

        // Ensure strict kiosk protections are applied even when activation happens outside LauncherActivity.
        if (DeviceOwnerKioskManager.isStrictKioskEnforcementEnabled(context)) {
            runCatching { DeviceOwnerKioskManager.enableHybridProtection(context) }
            runCatching { DeviceOwnerKioskManager.bringLauncherToFront(context) }
        }

        runCatching { DeviceOwnerKioskManager.setHyperFocusSelfProtection(context, true) }
        runCatching {
            DeviceOwnerKioskManager.syncHyperFocusBlockedPackagesSuspension(
                context,
                blockedPackages,
                DeviceOwnerKioskManager.isStrictKioskEnforcementEnabled(context)
            )
        }
    }

    override suspend fun onTaskCompleted() {
        val prefs = hyperFocusDataStore.current()
        if (!prefs.isActive) return
        if (prefs.sessionMode == HyperFocusSessionMode.TIME_BASED) return

        // Only count tasks that were active when the session started
        val completedLockedTasks = if (prefs.lockedTaskIds.isNotEmpty()) {
            taskRepository.getAllTasks()
                .count { it.id in prefs.lockedTaskIds && it.status == TaskStatus.COMPLETED }
        } else {
            // Fallback for sessions started before this fix (no lockedTaskIds stored)
            taskRepository.getAllTasks().count { it.status == TaskStatus.COMPLETED } - prefs.tasksCompletedAtActivation
        }
        val completedSinceActivation = completedLockedTasks.coerceAtLeast(0)
        val newTier = RewardEngine.computeTier(completedSinceActivation, prefs.dailyTaskTarget, prefs.emergencyUsed)

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
            runCatching { context.startForegroundService(Intent(context, UnlockTimerService::class.java)) }
        }

        return UnlockResult.Success(match.tier.unlockMinutes)
    }

    override suspend fun completePlanning() {
        // Planning completion marks the end of a Hyper Focus session.
        // Deactivate so the user can immediately start a new session.
        deactivate()
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

    override suspend fun triggerEmergencyBypass() {
        val prefs = hyperFocusDataStore.current()
        if (!prefs.isActive || prefs.emergencyUsed) return
        if (prefs.sessionMode == HyperFocusSessionMode.TIME_BASED) return

        val now = System.currentTimeMillis()
        val expiresAt = now + 10 * 60 * 1000L // 10 minutes unlock (one-time per session)
        // Default to true after the pre-checks above. If the atomic update lambda observes
        // a conflicting state change, it flips this to false to avoid starting the timer.
        var shouldStartService = true

        hyperFocusDataStore.update {
            if (!it.isActive || it.emergencyUsed || it.sessionMode == HyperFocusSessionMode.TIME_BASED) {
                shouldStartService = false
                it
            } else {
                it.copy(
                    activeUnlockExpiresAt = expiresAt,
                    emergencyUsed = true,
                    currentTier = RewardTier.NONE
                )
            }
        }

        if (shouldStartService) {
            runCatching { context.startForegroundService(Intent(context, UnlockTimerService::class.java)) }
        }
    }
}

