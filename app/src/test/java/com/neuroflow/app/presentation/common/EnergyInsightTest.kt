package com.neuroflow.app.presentation.common

import com.neuroflow.app.domain.engine.PeakEnergyEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnergyInsightTest {

    @Test
    fun `profile confidence line includes component breakdown`() {
        val profile = PeakEnergyEngine.EffectivePeakProfile(
            profileType = PeakEnergyEngine.ProfileType.WORKDAY,
            anchorMinuteOfDay = 360,
            windows = PeakEnergyEngine.defaultCircadianProfile(
                com.neuroflow.app.domain.engine.MEQChronotypeDetector.Chronotype.MODERATE_MORNING
            ).windows,
            confidence = PeakEnergyEngine.ConfidenceComponents(
                sleepCoverage = 0.8f,
                wakeConsistency = 0.7f,
                behaviorPerformance = 0.6f,
                overall = 0.72f
            ),
            explanation = "Adaptive morning profile",
            driftStatus = "stable",
            weeklyBacktestErrorMinutes = 34f
        )

        val line = EnergyInsight.profileConfidenceLine(profile)
        assertTrue(line.contains("Confidence"))
        assertTrue(line.contains("sleep"))
        assertTrue(line.contains("wake"))
        assertTrue(line.contains("behavior"))
    }

    @Test
    fun `window confidence helpers prefer per-window value`() {
        val profile = PeakEnergyEngine.EffectivePeakProfile(
            profileType = PeakEnergyEngine.ProfileType.WORKDAY,
            anchorMinuteOfDay = 360,
            windows = PeakEnergyEngine.defaultCircadianProfile(
                com.neuroflow.app.domain.engine.MEQChronotypeDetector.Chronotype.INTERMEDIATE
            ).windows,
            confidence = PeakEnergyEngine.ConfidenceComponents(
                sleepCoverage = 0.8f,
                wakeConsistency = 0.8f,
                behaviorPerformance = 0.8f,
                overall = 0.82f
            ),
            windowConfidences = listOf(0.84f, 0.61f, 0.49f),
            explanation = "Adaptive profile"
        )

        assertEquals(61, EnergyInsight.windowConfidencePercent(profile, 1))
        assertEquals("Low", EnergyInsight.windowConfidenceTier(profile, 2))
    }

    @Test
    fun `confidence improvement actions include peak 2 and 3 guidance when low`() {
        val profile = PeakEnergyEngine.EffectivePeakProfile(
            profileType = PeakEnergyEngine.ProfileType.WORKDAY,
            anchorMinuteOfDay = 360,
            windows = PeakEnergyEngine.defaultCircadianProfile(
                com.neuroflow.app.domain.engine.MEQChronotypeDetector.Chronotype.INTERMEDIATE
            ).windows,
            confidence = PeakEnergyEngine.ConfidenceComponents(
                sleepCoverage = 0.55f,
                wakeConsistency = 0.58f,
                behaviorPerformance = 0.50f,
                overall = 0.57f
            ),
            windowConfidences = listOf(0.72f, 0.52f, 0.42f),
            explanation = "Adaptive profile",
            driftStatus = "later_drift",
            weeklyBacktestErrorMinutes = 92f
        )

        val actions = EnergyInsight.confidenceImprovementActions(profile)
        assertTrue(actions.any { it.contains("Peak 2/3") })
        assertTrue(actions.any { it.contains("wake time") })
    }
}
