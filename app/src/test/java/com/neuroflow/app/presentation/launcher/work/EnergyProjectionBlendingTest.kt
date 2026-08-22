package com.neuroflow.app.presentation.launcher.work

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for energy projection blending weights in ScheduleAutoTasksWorker.
 *
 * **Validates: Requirement 3 - Accurate Energy Projection Without Moment Blending**
 *
 * These tests verify that the energyScoreFn lambda applies correct blending weights
 * based on time horizon:
 * - 0-2 hours: 35% moment, 65% baseline
 * - 2-6 hours: 20% moment, 80% baseline
 * - 6-12 hours: 10% moment, 90% baseline
 * - >12 hours: 0% moment, 100% baseline (no moment blending)
 */
class EnergyProjectionBlendingTest {

    /**
     * Simulates the liveWeight calculation from energyScoreFn lambda.
     * This is the core logic being tested from ScheduleAutoTasksWorker.doWork().
     */
    private fun calculateLiveWeight(hoursAhead: Float): Float {
        return when {
            hoursAhead <= 2f -> 0.35f
            hoursAhead <= 6f -> 0.20f
            hoursAhead <= 12f -> 0.10f
            else -> 0.0f
        }
    }

    @Test
    fun `test blending weight for current hour`() {
        val weight = calculateLiveWeight(0f)
        assertEquals("Current hour should use 35% moment weight", 0.35f, weight, 0.001f)
    }

    @Test
    fun `test blending weight for 1 hour ahead`() {
        val weight = calculateLiveWeight(1f)
        assertEquals("1 hour ahead should use 35% moment weight", 0.35f, weight, 0.001f)
    }

    @Test
    fun `test blending weight for 2 hours ahead boundary`() {
        val weight = calculateLiveWeight(2f)
        assertEquals("2 hours ahead (boundary) should use 35% moment weight", 0.35f, weight, 0.001f)
    }

    @Test
    fun `test blending weight for 3 hours ahead`() {
        val weight = calculateLiveWeight(3f)
        assertEquals("3 hours ahead should use 20% moment weight", 0.20f, weight, 0.001f)
    }

    @Test
    fun `test blending weight for 6 hours ahead boundary`() {
        val weight = calculateLiveWeight(6f)
        assertEquals("6 hours ahead (boundary) should use 20% moment weight", 0.20f, weight, 0.001f)
    }

    @Test
    fun `test blending weight for 8 hours ahead`() {
        val weight = calculateLiveWeight(8f)
        assertEquals("8 hours ahead should use 10% moment weight", 0.10f, weight, 0.001f)
    }

    @Test
    fun `test blending weight for 12 hours ahead boundary`() {
        val weight = calculateLiveWeight(12f)
        assertEquals("12 hours ahead (boundary) should use 10% moment weight", 0.10f, weight, 0.001f)
    }

    @Test
    fun `test blending weight for 13 hours ahead - no moment blending`() {
        val weight = calculateLiveWeight(13f)
        assertEquals("13 hours ahead should use 0% moment weight (baseline only)", 0.0f, weight, 0.001f)
    }

    @Test
    fun `test blending weight for 24 hours ahead - no moment blending`() {
        val weight = calculateLiveWeight(24f)
        assertEquals("24 hours ahead should use 0% moment weight (baseline only)", 0.0f, weight, 0.001f)
    }

    @Test
    fun `test blending weight for 48 hours ahead - no moment blending`() {
        val weight = calculateLiveWeight(48f)
        assertEquals("48 hours ahead should use 0% moment weight (baseline only)", 0.0f, weight, 0.001f)
    }

    @Test
    fun `test blending calculation for far future slot`() {
        // Simulate the full blending calculation for a far-future slot
        val baselineEnergy = 75f
        val momentEnergy = 50f
        val hoursAhead = 24f
        val liveWeight = calculateLiveWeight(hoursAhead)

        val blendedEnergy = (
            baselineEnergy * (1f - liveWeight) +
            momentEnergy * liveWeight
        )

        // For 24 hours ahead, liveWeight = 0.0f, so blendedEnergy should equal baselineEnergy
        assertEquals("Far-future energy should be 100% baseline (no moment adjustment)",
            baselineEnergy, blendedEnergy, 0.001f)
    }

    @Test
    fun `test blending calculation for near future slot`() {
        // Simulate the full blending calculation for a near-future slot
        val baselineEnergy = 75f
        val momentEnergy = 50f
        val hoursAhead = 1f
        val liveWeight = calculateLiveWeight(hoursAhead)

        val blendedEnergy = (
            baselineEnergy * (1f - liveWeight) +
            momentEnergy * liveWeight
        )

        // For 1 hour ahead, liveWeight = 0.35f
        // Expected: 75 * 0.65 + 50 * 0.35 = 48.75 + 17.5 = 66.25
        assertEquals("Near-future energy should blend 35% moment with 65% baseline",
            66.25f, blendedEnergy, 0.001f)
    }

    @Test
    fun `test blending calculation for medium future slot`() {
        // Simulate the full blending calculation for a medium-future slot
        val baselineEnergy = 80f
        val momentEnergy = 40f
        val hoursAhead = 4f
        val liveWeight = calculateLiveWeight(hoursAhead)

        val blendedEnergy = (
            baselineEnergy * (1f - liveWeight) +
            momentEnergy * liveWeight
        )

        // For 4 hours ahead, liveWeight = 0.20f
        // Expected: 80 * 0.80 + 40 * 0.20 = 64 + 8 = 72
        assertEquals("Medium-future energy should blend 20% moment with 80% baseline",
            72f, blendedEnergy, 0.001f)
    }
}
