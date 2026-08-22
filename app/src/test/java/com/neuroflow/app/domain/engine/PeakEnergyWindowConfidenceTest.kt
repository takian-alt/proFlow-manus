package com.neuroflow.app.domain.engine

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.floats.shouldBeGreaterThanOrEqual
import io.kotest.matchers.floats.shouldBeLessThanOrEqual

class PeakEnergyWindowConfidenceTest : StringSpec({

    "detect builds per-window confidence with lower reliability for later peaks under drift" {
        val meq = MEQChronotypeDetector.Result(
            totalScore = 52,
            chronotype = MEQChronotypeDetector.Chronotype.INTERMEDIATE,
            baselinePeakStartHour = 9,
            baselinePeakEndHour = 14,
            answeredQuestions = 19,
            confidence = 0.88f
        )

        val signals = PeakEnergyEngine.MorningPersonalizationSignals(
            averageSleepMinutes = 390,
            wakeVarianceMinutes = 130,
            sleepLogCoverage = 0.62f,
            behaviorCoverage = 0.45f,
            driftMinutes = 24,
            weeklyBacktestErrorMinutes = 84f,
            behaviorSlots = listOf(
                PeakEnergyEngine.SlotPerformanceAggregate(
                    bucketStartMinute = 9 * 60,
                    qualityWeightedCompletionRate = 0.58f,
                    abortRate = 0.28f,
                    distractionRate = 0.31f,
                    sampleCount = 8
                )
            )
        )

        val result = PeakEnergyEngine.detect(
            meqResult = meq,
            wakeUpHour = 7,
            sleepHour = 0,
            sleepPressurePoints = 1500,
            personalizationSignals = signals
        )

        val windowConfidences = result.effectiveProfile.windowConfidences
        windowConfidences shouldHaveSize result.effectiveProfile.windows.size
        windowConfidences[1] shouldBeLessThanOrEqual windowConfidences[0]
        windowConfidences[2] shouldBeLessThanOrEqual windowConfidences[1]
        windowConfidences[2] shouldBeGreaterThanOrEqual 0.25f
    }
})
