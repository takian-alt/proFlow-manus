package com.neuroflow.app.presentation.common

import com.neuroflow.app.domain.engine.MEQChronotypeDetector
import com.neuroflow.app.domain.engine.PeakEnergyEngine
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class EnergyInsightGuidanceTest : StringSpec({

    "window confidence helpers use per-window confidence when available" {
        val profile = PeakEnergyEngine.EffectivePeakProfile(
            profileType = PeakEnergyEngine.ProfileType.WORKDAY,
            anchorMinuteOfDay = 360,
            windows = PeakEnergyEngine.defaultCircadianProfile(
                MEQChronotypeDetector.Chronotype.INTERMEDIATE
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

        EnergyInsight.windowConfidencePercent(profile, 1) shouldBe 61
        EnergyInsight.windowConfidenceTier(profile, 2) shouldBe "Low"
    }

    "confidence improvement actions include peak 2 and 3 guidance when later windows are weak" {
        val profile = PeakEnergyEngine.EffectivePeakProfile(
            profileType = PeakEnergyEngine.ProfileType.WORKDAY,
            anchorMinuteOfDay = 360,
            windows = PeakEnergyEngine.defaultCircadianProfile(
                MEQChronotypeDetector.Chronotype.INTERMEDIATE
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
        actions.firstOrNull { it.contains("Peak 2/3") } shouldNotBe null
    }

    "profile mode label shows freeze safety fallback when adaptive freeze is active" {
        val profile = PeakEnergyEngine.EffectivePeakProfile(
            profileType = PeakEnergyEngine.ProfileType.WORKDAY,
            anchorMinuteOfDay = 360,
            windows = PeakEnergyEngine.defaultCircadianProfile(
                MEQChronotypeDetector.Chronotype.INTERMEDIATE
            ).windows,
            confidence = PeakEnergyEngine.ConfidenceComponents(
                sleepCoverage = 0.7f,
                wakeConsistency = 0.7f,
                behaviorPerformance = 0.7f,
                overall = 0.7f
            ),
            explanation = "Adaptive profile freeze active",
            adaptiveFreezeMode = true
        )

        EnergyInsight.profileModeLabel(
            manualOverrideEnabled = false,
            profile = profile
        ) shouldBe "Profile mode: Adaptive freeze safety fallback"
    }

    "abstention mode label and guidance are surfaced when prediction is withheld" {
        val profile = PeakEnergyEngine.EffectivePeakProfile(
            profileType = PeakEnergyEngine.ProfileType.WORKDAY,
            anchorMinuteOfDay = 360,
            windows = PeakEnergyEngine.defaultCircadianProfile(
                MEQChronotypeDetector.Chronotype.INTERMEDIATE
            ).windows,
            confidence = PeakEnergyEngine.ConfidenceComponents(
                sleepCoverage = 0.3f,
                wakeConsistency = 0.35f,
                behaviorPerformance = 0.4f,
                overall = 0.37f
            ),
            explanation = "Confidence-gated abstention active",
            confidenceGatedAbstention = true,
            abstentionReason = "insufficient focus-session samples for reliable personalization"
        )

        EnergyInsight.profileModeLabel(
            manualOverrideEnabled = false,
            profile = profile
        ) shouldBe "Profile mode: Confidence-gated abstention (safe baseline)"

        val actions = EnergyInsight.confidenceImprovementActions(profile)
        actions.first().contains("Prediction is currently withheld") shouldBe true
    }
})
