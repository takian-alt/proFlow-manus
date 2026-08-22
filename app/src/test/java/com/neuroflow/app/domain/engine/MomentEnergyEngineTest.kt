package com.neuroflow.app.domain.engine

import org.junit.Assert.assertTrue
import org.junit.Test

class MomentEnergyEngineTest {

    @Test
    fun `calm productive context nudges energy upward`() {
        val result = MomentEnergyEngine.predict(
            MomentEnergyEngine.MomentSignalSnapshot(
                baselineRawEnergy = 12f,
                sleepPressurePoints = 300,
                peakConfidence = 0.8f,
                minutesSincePeak = 90,
                recentFocusMinutes = 100f,
                recentInterruptionCount = 0,
                recentAppSwitchCount = 0,
                recentPauseResumeCount = 0,
                notificationCount = 1,
                activeTaskCount = 2,
                overdueTaskCount = 0,
                dueSoonTaskCount = 0,
                activeSessionCount = 1,
                recentWindowMinutes = 180
            )
        )

        assertTrue(result.adjustedRawEnergy > 12f)
        assertTrue(result.confidence > 0.5f)
        assertTrue(result.summary.contains("supportive") || result.summary.contains("confidence"))
    }

    @Test
    fun `heavy interruption and overdue load pushes energy down`() {
        val result = MomentEnergyEngine.predict(
            MomentEnergyEngine.MomentSignalSnapshot(
                baselineRawEnergy = 40f,
                sleepPressurePoints = 2400,
                peakConfidence = 0.5f,
                minutesSincePeak = 210,
                recentFocusMinutes = 10f,
                recentInterruptionCount = 9,
                recentAppSwitchCount = 8,
                recentPauseResumeCount = 6,
                notificationCount = 18,
                activeTaskCount = 7,
                overdueTaskCount = 4,
                dueSoonTaskCount = 3,
                activeSessionCount = 0,
                recentWindowMinutes = 180
            )
        )

        assertTrue(result.adjustedRawEnergy < 40f)
        assertTrue(result.adjustment < 0f)
        assertTrue(result.confidence >= 0.35f)
    }
}