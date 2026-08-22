package com.neuroflow.app.domain.engine

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.math.abs

class PeakEnergyMorningCalibrationTest : StringSpec({

    "low sleep debt and high consistency keep stronger confidence" {
        val baseline = morningResult()
        val highQualitySignals = PeakEnergyEngine.MorningPersonalizationSignals(
            averageSleepMinutes = 480,
            wakeVarianceMinutes = 18,
            sleepLogCoverage = 0.9f,
            behaviorCoverage = 0.85f,
            behaviorSlots = listOf(
                PeakEnergyEngine.SlotPerformanceAggregate(
                    bucketStartMinute = 120,
                    qualityWeightedCompletionRate = 0.9f,
                    abortRate = 0.08f,
                    distractionRate = 0.18f,
                    sampleCount = 8
                )
            )
        )
        val lowerQualitySignals = PeakEnergyEngine.MorningPersonalizationSignals(
            averageSleepMinutes = 360,
            wakeVarianceMinutes = 130,
            sleepLogCoverage = 0.35f,
            behaviorCoverage = 0.3f,
            behaviorSlots = listOf(
                PeakEnergyEngine.SlotPerformanceAggregate(
                    bucketStartMinute = 300,
                    qualityWeightedCompletionRate = 0.3f,
                    abortRate = 0.55f,
                    distractionRate = 0.7f,
                    sampleCount = 4
                )
            )
        )

        val highQuality = PeakEnergyEngine.detect(
            meqResult = baseline,
            wakeUpHour = 6,
            sleepHour = 22,
            sleepPressurePoints = 700,
            personalizationSignals = highQualitySignals
        )
        val lowerQuality = PeakEnergyEngine.detect(
            meqResult = baseline,
            wakeUpHour = 6,
            sleepHour = 0,
            sleepPressurePoints = 2200,
            personalizationSignals = lowerQualitySignals
        )

        lowerQuality.confidence shouldBeLessThan highQuality.confidence
    }

    "inconsistent wake timing increases phase shift compared to consistent case" {
        val baseline = morningResult()
        val consistent = PeakEnergyEngine.detect(
            meqResult = baseline,
            wakeUpHour = 6,
            sleepHour = 22,
            sleepPressurePoints = 900,
            personalizationSignals = PeakEnergyEngine.MorningPersonalizationSignals(
                averageSleepMinutes = 470,
                wakeVarianceMinutes = 12,
                sleepLogCoverage = 0.9f
            )
        )
        val inconsistent = PeakEnergyEngine.detect(
            meqResult = baseline,
            wakeUpHour = 6,
            sleepHour = 22,
            sleepPressurePoints = 900,
            personalizationSignals = PeakEnergyEngine.MorningPersonalizationSignals(
                averageSleepMinutes = 470,
                wakeVarianceMinutes = 165,
                sleepLogCoverage = 0.9f
            )
        )

        inconsistent.circadianProfile.phaseShiftMinutes shouldNotBe consistent.circadianProfile.phaseShiftMinutes
        (inconsistent.circadianProfile.phaseShiftMinutes > consistent.circadianProfile.phaseShiftMinutes) shouldBe true
    }

    "weekend profile type is preserved in effective profile" {
        val detected = PeakEnergyEngine.detect(
            meqResult = morningResult(),
            wakeUpHour = 7,
            sleepHour = 23,
            sleepPressurePoints = 800,
            personalizationSignals = PeakEnergyEngine.MorningPersonalizationSignals(
                profileType = PeakEnergyEngine.ProfileType.WEEKEND,
                averageSleepMinutes = 510,
                wakeVarianceMinutes = 45,
                sleepLogCoverage = 0.7f
            )
        )

        detected.effectiveProfile.profileType shouldBe PeakEnergyEngine.ProfileType.WEEKEND
    }

    "morning windows keep stable durations 210 150 60" {
        val detected = PeakEnergyEngine.detect(
            meqResult = morningResult(),
            wakeUpHour = 6,
            sleepHour = 22,
            sleepPressurePoints = 1000,
            personalizationSignals = PeakEnergyEngine.MorningPersonalizationSignals(
                averageSleepMinutes = 460,
                wakeVarianceMinutes = 35,
                sleepLogCoverage = 0.8f
            )
        )

        detected.circadianProfile.windows.map { it.durationMinutes } shouldBe listOf(210, 150, 60)
    }

    "cold start dampens behavior shift magnitude" {
        val richSignals = PeakEnergyEngine.MorningPersonalizationSignals(
            averageSleepMinutes = 460,
            wakeVarianceMinutes = 35,
            sleepLogCoverage = 0.8f,
            behaviorSlots = listOf(
                PeakEnergyEngine.SlotPerformanceAggregate(
                    bucketStartMinute = 360,
                    qualityWeightedCompletionRate = 0.9f,
                    abortRate = 0.05f,
                    distractionRate = 0.15f,
                    sampleCount = 12
                )
            ),
            coldStartFactor = 1f
        )
        val coldSignals = richSignals.copy(coldStartFactor = 0.35f)
        val rich = PeakEnergyEngine.detect(morningResult(), 6, 22, 900, richSignals)
        val cold = PeakEnergyEngine.detect(morningResult(), 6, 22, 900, coldSignals)
        (kotlin.math.abs(cold.circadianProfile.phaseShiftMinutes) <= kotlin.math.abs(rich.circadianProfile.phaseShiftMinutes)) shouldBe true
    }

    "task type performance contributes to anchor shift" {
        val base = PeakEnergyEngine.detect(
            meqResult = morningResult(),
            wakeUpHour = 6,
            sleepHour = 22,
            sleepPressurePoints = 800,
            personalizationSignals = PeakEnergyEngine.MorningPersonalizationSignals(
                sleepLogCoverage = 0.8f,
                wakeVarianceMinutes = 25,
                averageSleepMinutes = 470
            )
        )
        val shifted = PeakEnergyEngine.detect(
            meqResult = morningResult(),
            wakeUpHour = 6,
            sleepHour = 22,
            sleepPressurePoints = 800,
            personalizationSignals = PeakEnergyEngine.MorningPersonalizationSignals(
                sleepLogCoverage = 0.8f,
                wakeVarianceMinutes = 25,
                averageSleepMinutes = 470,
                taskTypePerformance = listOf(
                    PeakEnergyEngine.TaskTypePerformanceAggregate(
                        taskType = "ANALYTICAL",
                        averageBestBucketMinute = 300,
                        weightedQuality = 0.9f,
                        sampleCount = 10
                    )
                )
            )
        )
        shifted.peakMinuteOfDay shouldNotBe base.peakMinuteOfDay
    }

    "behavior evidence can adapt peak 2 and peak 3 timing more than peak 1" {
        val detected = PeakEnergyEngine.detect(
            meqResult = morningResult(),
            wakeUpHour = 6,
            sleepHour = 22,
            sleepPressurePoints = 850,
            personalizationSignals = PeakEnergyEngine.MorningPersonalizationSignals(
                averageSleepMinutes = 480,
                wakeVarianceMinutes = 18,
                sleepLogCoverage = 0.95f,
                behaviorCoverage = 0.92f,
                coldStartFactor = 1f,
                baselineAnchorMinuteOfDay = 510,
                behaviorSlots = listOf(
                    PeakEnergyEngine.SlotPerformanceAggregate(
                        bucketStartMinute = 510,
                        qualityWeightedCompletionRate = 0.72f,
                        abortRate = 0.16f,
                        distractionRate = 0.28f,
                        sampleCount = 7
                    ),
                    PeakEnergyEngine.SlotPerformanceAggregate(
                        bucketStartMinute = 1170,
                        qualityWeightedCompletionRate = 0.95f,
                        abortRate = 0.05f,
                        distractionRate = 0.08f,
                        sampleCount = 22
                    ),
                    PeakEnergyEngine.SlotPerformanceAggregate(
                        bucketStartMinute = 0,
                        qualityWeightedCompletionRate = 0.93f,
                        abortRate = 0.06f,
                        distractionRate = 0.10f,
                        sampleCount = 19
                    )
                )
            )
        )

        val defaults = PeakEnergyEngine.defaultCircadianProfile(morningResult().chronotype).windows
        val adapted = detected.circadianProfile.windows

        val shift1 = abs(wrappedDelta(adapted[0].startMinuteOffset, defaults[0].startMinuteOffset))
        val shift2 = abs(wrappedDelta(adapted[1].startMinuteOffset, defaults[1].startMinuteOffset))
        val shift3 = abs(wrappedDelta(adapted[2].startMinuteOffset, defaults[2].startMinuteOffset))

        (shift2 > shift1) shouldBe true
        (shift3 > shift1) shouldBe true
        (shift2 >= 10) shouldBe true
        (shift3 >= 10) shouldBe true
    }

    "low behavior confidence dampens peak 2 and peak 3 adaptation" {
        val highSignals = PeakEnergyEngine.MorningPersonalizationSignals(
            averageSleepMinutes = 470,
            wakeVarianceMinutes = 20,
            sleepLogCoverage = 0.92f,
            behaviorCoverage = 0.9f,
            coldStartFactor = 1f,
            baselineAnchorMinuteOfDay = 510,
            behaviorSlots = listOf(
                PeakEnergyEngine.SlotPerformanceAggregate(
                    bucketStartMinute = 1170,
                    qualityWeightedCompletionRate = 0.94f,
                    abortRate = 0.05f,
                    distractionRate = 0.09f,
                    sampleCount = 20
                ),
                PeakEnergyEngine.SlotPerformanceAggregate(
                    bucketStartMinute = 0,
                    qualityWeightedCompletionRate = 0.92f,
                    abortRate = 0.05f,
                    distractionRate = 0.11f,
                    sampleCount = 18
                )
            )
        )
        val lowSignals = highSignals.copy(
            sleepLogCoverage = 0.2f,
            behaviorCoverage = 0.12f,
            coldStartFactor = 0.35f
        )

        val high = PeakEnergyEngine.detect(morningResult(), 6, 22, 900, highSignals)
        val low = PeakEnergyEngine.detect(morningResult(), 6, 22, 900, lowSignals)
        val defaults = PeakEnergyEngine.defaultCircadianProfile(morningResult().chronotype).windows

        val highShift2 = abs(wrappedDelta(high.circadianProfile.windows[1].startMinuteOffset, defaults[1].startMinuteOffset))
        val highShift3 = abs(wrappedDelta(high.circadianProfile.windows[2].startMinuteOffset, defaults[2].startMinuteOffset))
        val lowShift2 = abs(wrappedDelta(low.circadianProfile.windows[1].startMinuteOffset, defaults[1].startMinuteOffset))
        val lowShift3 = abs(wrappedDelta(low.circadianProfile.windows[2].startMinuteOffset, defaults[2].startMinuteOffset))

        (lowShift2 < highShift2) shouldBe true
        (lowShift3 < highShift3) shouldBe true
    }

    "drift status is exposed in effective profile" {
        val detected = PeakEnergyEngine.detect(
            meqResult = morningResult(),
            wakeUpHour = 6,
            sleepHour = 22,
            sleepPressurePoints = 900,
            personalizationSignals = PeakEnergyEngine.MorningPersonalizationSignals(
                sleepLogCoverage = 0.9f,
                wakeVarianceMinutes = 22,
                averageSleepMinutes = 470,
                driftMinutes = 24,
                weeklyBacktestErrorMinutes = 38f
            )
        )
        detected.effectiveProfile.driftStatus shouldBe "later_drift"
        detected.effectiveProfile.weeklyBacktestErrorMinutes shouldBe 38f
        detected.effectiveProfile.confidence.shouldBeInstanceOf<PeakEnergyEngine.ConfidenceComponents>()
    }

    "manual profile override replaces adaptive anchor and windows" {
        val detected = PeakEnergyEngine.detect(
            meqResult = morningResult(),
            wakeUpHour = 6,
            sleepHour = 22,
            sleepPressurePoints = 1400,
            personalizationSignals = PeakEnergyEngine.MorningPersonalizationSignals(
                profileOverride = PeakEnergyEngine.ProfileOverride(
                    enabled = true,
                    profileType = PeakEnergyEngine.ProfileType.WEEKEND,
                    anchorMinuteOfDay = 510,
                    windows = listOf(
                        PeakEnergyEngine.PeakWindow(0, 180, 1.0f),
                        PeakEnergyEngine.PeakWindow(570, 120, 0.7f),
                        PeakEnergyEngine.PeakWindow(810, 90, 0.5f)
                    )
                )
            )
        )

        detected.peakMinuteOfDay shouldBe 510
        detected.circadianProfile.windows.map { it.durationMinutes } shouldBe listOf(180, 120, 90)
        detected.effectiveProfile.profileType shouldBe PeakEnergyEngine.ProfileType.WEEKEND
    }

    "confidence-gated abstention disables adaptive shifting and caps confidence" {
        val detected = PeakEnergyEngine.detect(
            meqResult = morningResult(),
            wakeUpHour = 6,
            sleepHour = 22,
            sleepPressurePoints = 1200,
            personalizationSignals = PeakEnergyEngine.MorningPersonalizationSignals(
                averageSleepMinutes = 430,
                wakeVarianceMinutes = 150,
                sleepLogCoverage = 0.2f,
                behaviorCoverage = 0.18f,
                behaviorSlots = listOf(
                    PeakEnergyEngine.SlotPerformanceAggregate(
                        bucketStartMinute = 1140,
                        qualityWeightedCompletionRate = 0.5f,
                        abortRate = 0.45f,
                        distractionRate = 0.55f,
                        sampleCount = 5
                    )
                ),
                confidenceGatedAbstention = true,
                abstentionReason = "insufficient focus-session samples for reliable personalization"
            )
        )

        detected.circadianProfile.phaseShiftMinutes shouldBe 0
        (detected.confidence <= 0.4f) shouldBe true
        detected.confidenceGatedAbstention shouldBe true
        detected.effectiveProfile.confidenceGatedAbstention shouldBe true
    }
})

private fun morningResult(): MEQChronotypeDetector.Result {
    return MEQChronotypeDetector.Result(
        totalScore = 64,
        chronotype = MEQChronotypeDetector.Chronotype.MODERATE_MORNING,
        baselinePeakStartHour = 7,
        baselinePeakEndHour = 12,
        answeredQuestions = MEQChronotypeDetector.QUESTION_COUNT,
        confidence = 1f
    )
}

private fun wrappedDelta(a: Int, b: Int): Int {
    val day = 24 * 60
    val aa = ((a % day) + day) % day
    val bb = ((b % day) + day) % day
    val forward = (aa - bb + day) % day
    val backward = forward - day
    return if (abs(forward) <= abs(backward)) forward else backward
}
