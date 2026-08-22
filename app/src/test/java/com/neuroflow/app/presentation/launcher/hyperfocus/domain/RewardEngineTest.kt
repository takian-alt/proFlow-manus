package com.neuroflow.app.presentation.launcher.hyperfocus.domain

import com.neuroflow.app.domain.model.RewardTier
import com.neuroflow.app.presentation.launcher.hyperfocus.data.HyperFocusPreferences
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/**
 * Unit tests for RewardEngine.
 *
 * Validates: Requirements 2.1, 2.2
 *
 * Tests:
 * - HF-1: Tier is monotonically non-decreasing as completed tasks increase
 * - HF-2: FULL tier iff completed >= target
 * - HF-3: isUnlockActive returns false when activeUnlockExpiresAt is in the past
 * - HF-4: tasksToNextTier returns 0 when at FULL tier
 */
class RewardEngineTest : StringSpec({

    // HF-1: Tier is monotonically non-decreasing for completed 0..target+5
    "HF-1: tier is monotonically non-decreasing as completed tasks increase" {
        val target = 5
        val tiers = (0..10).map { completed ->
            RewardEngine.computeTier(completed, target)
        }
        for (i in 1 until tiers.size) {
            (tiers[i].ordinal >= tiers[i - 1].ordinal) shouldBe true
        }
    }

    // HF-2: FULL tier iff completed >= target
    "HF-2: FULL tier when completed >= target, not FULL otherwise" {
        val target = 5
        RewardEngine.computeTier(0, target) shouldBe RewardTier.NONE
        RewardEngine.computeTier(1, target) shouldBe RewardTier.MICRO
        RewardEngine.computeTier(2, target) shouldBe RewardTier.PARTIAL
        RewardEngine.computeTier(3, target) shouldBe RewardTier.EARNED
        RewardEngine.computeTier(4, target) shouldBe RewardTier.EARNED
        RewardEngine.computeTier(5, target) shouldBe RewardTier.FULL
        RewardEngine.computeTier(6, target) shouldBe RewardTier.FULL
    }

    // HF-3: isUnlockActive returns false when activeUnlockExpiresAt is in the past
    "HF-3: isUnlockActive returns false when activeUnlockExpiresAt is in the past" {
        val prefs = HyperFocusPreferences(
            activeUnlockExpiresAt = System.currentTimeMillis() - 1000L
        )
        RewardEngine.isUnlockActive(prefs).shouldBeFalse()
    }

    // HF-4: tasksToNextTier returns 0 when at FULL tier
    "HF-4: tasksToNextTier returns 0 when completed >= target" {
        RewardEngine.tasksToNextTier(5, 5) shouldBe 0
        RewardEngine.tasksToNextTier(6, 5) shouldBe 0
        RewardEngine.tasksToNextTier(10, 5) shouldBe 0
    }

    "isUnlockActive returns true when activeUnlockExpiresAt is in the future" {
        val prefs = HyperFocusPreferences(
            activeUnlockExpiresAt = System.currentTimeMillis() + 60_000L
        )
        RewardEngine.isUnlockActive(prefs).shouldBeTrue()
    }

    "isUnlockActive returns false when activeUnlockExpiresAt is null" {
        val prefs = HyperFocusPreferences(activeUnlockExpiresAt = null)
        RewardEngine.isUnlockActive(prefs).shouldBeFalse()
    }

    "computeTier returns NONE for 0 completed" {
        RewardEngine.computeTier(0, 10) shouldBe RewardTier.NONE
    }

    "computeTier returns MICRO for 1 completed (target=10)" {
        RewardEngine.computeTier(1, 10) shouldBe RewardTier.NONE
    }

    "computeTier returns PARTIAL for 3 completed (target=10)" {
        RewardEngine.computeTier(3, 10) shouldBe RewardTier.MICRO
    }

    "computeTier returns EARNED for 5 completed (target=10)" {
        RewardEngine.computeTier(5, 10) shouldBe RewardTier.PARTIAL
    }

    "secondsRemaining returns null when unlock not active" {
        val prefs = HyperFocusPreferences(activeUnlockExpiresAt = null)
        RewardEngine.secondsRemaining(prefs).shouldBeNull()
    }

    "secondsRemaining returns positive value when unlock active" {
        val prefs = HyperFocusPreferences(
            activeUnlockExpiresAt = System.currentTimeMillis() + 60_000L
        )
        val seconds = RewardEngine.secondsRemaining(prefs)
        seconds.shouldNotBeNull()
        (seconds > 0) shouldBe true
    }
})
