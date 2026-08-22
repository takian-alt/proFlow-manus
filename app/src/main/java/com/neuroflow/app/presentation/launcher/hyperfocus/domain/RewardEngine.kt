package com.neuroflow.app.presentation.launcher.hyperfocus.domain

import com.neuroflow.app.domain.model.RewardTier
import com.neuroflow.app.presentation.launcher.hyperfocus.data.HyperFocusPreferences

object RewardEngine {

    fun computeTier(completedSinceActivation: Int, totalTarget: Int): RewardTier {
        if (completedSinceActivation <= 0) return RewardTier.NONE
        val target = totalTarget.coerceAtLeast(1)
        if (completedSinceActivation >= target) return RewardTier.FULL
        // Scale thresholds proportionally: MICRO=25%, PARTIAL=50%, EARNED=75%
        return when {
            completedSinceActivation >= (target * 0.75f).toInt().coerceAtLeast(1) -> RewardTier.EARNED
            completedSinceActivation >= (target * 0.50f).toInt().coerceAtLeast(1) -> RewardTier.PARTIAL
            else -> RewardTier.MICRO
        }
    }

    fun tasksToNextTier(completed: Int, target: Int): Int {
        if (completed <= 0) return 1
        val t = target.coerceAtLeast(1)
        if (completed >= t) return 0
        val earnedThreshold  = (t * 0.75f).toInt().coerceAtLeast(1)
        val partialThreshold = (t * 0.50f).toInt().coerceAtLeast(1)
        return when {
            completed >= earnedThreshold  -> t - completed
            completed >= partialThreshold -> earnedThreshold - completed
            else                          -> partialThreshold - completed
        }
    }

    fun isUnlockActive(prefs: HyperFocusPreferences): Boolean {
        // FULL tier unlock has no expiry (null = permanent until deactivation)
        if (prefs.state == com.neuroflow.app.domain.model.HyperFocusState.FULLY_UNLOCKED) return true
        // FULL_REWARD_PENDING: tasks done but planning not yet complete — not unlocked yet
        return prefs.activeUnlockExpiresAt != null && prefs.activeUnlockExpiresAt > System.currentTimeMillis()
    }

    fun secondsRemaining(prefs: HyperFocusPreferences): Long? {
        if (!isUnlockActive(prefs)) return null
        // FULLY_UNLOCKED has no expiry timer — return null (UI shows permanent unlock)
        val expiresAt = prefs.activeUnlockExpiresAt ?: return null
        return (expiresAt - System.currentTimeMillis()) / 1000
    }
}
