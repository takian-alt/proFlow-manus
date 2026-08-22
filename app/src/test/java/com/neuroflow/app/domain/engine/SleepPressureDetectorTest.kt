package com.neuroflow.app.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class SleepPressureDetectorTest {

    @Test
    fun `awake accumulation is one point per minute`() {
        val result = SleepPressureDetector.detect(24f)
        assertEquals(1440, result.pressurePoints)
    }

    @Test
    fun `negative awake hours are clamped to zero`() {
        val result = SleepPressureDetector.detect(-2f)
        assertEquals(0, result.pressurePoints)
        assertEquals(0, result.awakeMinutes)
    }

    @Test
    fun `recovery milestones match the specification`() {
        assertEquals(30, SleepPressureDetector.recoveryForSleepMinutes(30))
        assertEquals(186, SleepPressureDetector.recoveryForSleepMinutes(60))
        assertEquals(216, SleepPressureDetector.recoveryForSleepMinutes(75))
        assertEquals(279, SleepPressureDetector.recoveryForSleepMinutes(90))
        assertEquals(289, SleepPressureDetector.recoveryForSleepMinutes(100))
        assertEquals(558, SleepPressureDetector.recoveryForSleepMinutes(180))
        assertEquals(1302, SleepPressureDetector.recoveryForSleepMinutes(420))
    }

    @Test
    fun `interruption boundaries apply only eligible bonuses`() {
        assertEquals(59, SleepPressureDetector.recoveryForSleepMinutes(59))
        assertEquals(186, SleepPressureDetector.recoveryForSleepMinutes(60))
        assertEquals(188, SleepPressureDetector.recoveryForSleepMinutes(61))
        assertEquals(244, SleepPressureDetector.recoveryForSleepMinutes(89))
        assertEquals(279, SleepPressureDetector.recoveryForSleepMinutes(90))
    }

    @Test
    fun `full cycle repeats then starts at phase one again`() {
        val oneHundredNinety = SleepPressureDetector.recoveryForSleepMinutes(190)
        // 2 full cycles (558) + 10 minutes in new phase one (10)
        assertEquals(568, oneHundredNinety)
    }

    @Test
    fun `sleep recovery is floored at zero pressure`() {
        val result = SleepPressureDetector.applySleepSession(
            currentPressure = 1020,
            sleepMinutes = 420
        )

        assertEquals(0, result.pressurePoints)
        assertEquals(1302, result.recoveryPointsEarned)
        assertEquals(1020, result.recoveryPointsApplied)
    }

    @Test
    fun `awake accumulation adds to current pressure`() {
        val result = SleepPressureDetector.applyAwakeMinutes(
            currentPressure = 200,
            awakeMinutes = 95
        )

        assertEquals(295, result.pressurePoints)
        assertEquals(95, result.awakeMinutes)
    }

    @Test
    fun `negative inputs are safely clamped`() {
        assertEquals(0, SleepPressureDetector.recoveryForSleepMinutes(-10))

        val awakeResult = SleepPressureDetector.applyAwakeMinutes(
            currentPressure = -20,
            awakeMinutes = -5
        )
        assertEquals(0, awakeResult.pressurePoints)

        val sleepResult = SleepPressureDetector.applySleepSession(
            currentPressure = -20,
            sleepMinutes = -40
        )
        assertEquals(0, sleepResult.pressurePoints)
        assertEquals(0, sleepResult.recoveryPointsEarned)
        assertEquals(0, sleepResult.recoveryPointsApplied)
    }

    @Test
    fun `fatigue percent maps raw pressure to 0 to 100`() {
        assertEquals(0, SleepPressureDetector.fatiguePercent(0))
        assertEquals(57, SleepPressureDetector.fatiguePercent(1500))
        assertEquals(39, SleepPressureDetector.fatiguePercent(940))
        assertEquals(100, SleepPressureDetector.fatiguePercent(3000))
        assertEquals(100, SleepPressureDetector.fatiguePercent(9000))
    }

    @Test
    fun `fatigue ratio is monotonic with pressure`() {
        val low = SleepPressureDetector.fatigueRatio(600)
        val mid = SleepPressureDetector.fatigueRatio(1400)
        val high = SleepPressureDetector.fatigueRatio(2400)

        assert(low < mid)
        assert(mid < high)
    }

    @Test
    fun `fatigue zones follow configured thresholds`() {
        assertEquals(SleepPressureDetector.FatigueZone.RESTED, SleepPressureDetector.fatigueZone(100))
        assertEquals(SleepPressureDetector.FatigueZone.MODERATE, SleepPressureDetector.fatigueZone(900))
        assertEquals(SleepPressureDetector.FatigueZone.HIGH, SleepPressureDetector.fatigueZone(1800))
        assertEquals(SleepPressureDetector.FatigueZone.CRITICAL, SleepPressureDetector.fatigueZone(2600))
    }
}
