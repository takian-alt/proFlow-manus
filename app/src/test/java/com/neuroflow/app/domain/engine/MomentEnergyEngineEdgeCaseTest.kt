package com.neuroflow.app.domain.engine

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlin.math.abs

class MomentEnergyEngineEdgeCaseTest : StringSpec({

    "stale signals down-weight confidence and adjustment magnitude" {
        val fresh = MomentEnergyEngine.predict(
            supportiveSnapshot(signalFreshnessAgeMillis = 0L)
        )
        val stale = MomentEnergyEngine.predict(
            supportiveSnapshot(signalFreshnessAgeMillis = 45L * 60_000L)
        )

        stale.confidence shouldBe 0.35f
        stale.confidence shouldBeLessThan fresh.confidence
        abs(stale.adjustedRawEnergy - 15f) shouldBeLessThan abs(fresh.adjustedRawEnergy - 15f)
    }

    "lower peak confidence reduces moment confidence under same context" {
        val lowPeakConfidence = MomentEnergyEngine.predict(
            supportiveSnapshot(peakConfidence = 0.2f)
        )
        val highPeakConfidence = MomentEnergyEngine.predict(
            supportiveSnapshot(peakConfidence = 0.85f)
        )

        lowPeakConfidence.confidence shouldBeLessThan highPeakConfidence.confidence
    }

    // Requirement 6: trendStrength() guards
    "trendStrength returns 0 for all-zero focus windows (NaN guard)" {
        val features = MomentEnergyEngine.MultiHorizonFeatures(
            window5m = MomentEnergyEngine.MultiHorizonFeatures.WindowMetrics(0f, 0, 0),
            window15m = MomentEnergyEngine.MultiHorizonFeatures.WindowMetrics(0f, 0, 0),
            window30m = MomentEnergyEngine.MultiHorizonFeatures.WindowMetrics(0f, 0, 0),
            window60m = MomentEnergyEngine.MultiHorizonFeatures.WindowMetrics(0f, 0, 0),
            window180m = MomentEnergyEngine.MultiHorizonFeatures.WindowMetrics(0f, 0, 0)
        )
        features.trendStrength() shouldBe 0.0f
    }

    "trendStrength result is clamped to [-1, 1] for extreme focus values" {
        val features = MomentEnergyEngine.MultiHorizonFeatures(
            window5m = MomentEnergyEngine.MultiHorizonFeatures.WindowMetrics(0f, 0, 0),
            window15m = MomentEnergyEngine.MultiHorizonFeatures.WindowMetrics(0f, 0, 0),
            window30m = MomentEnergyEngine.MultiHorizonFeatures.WindowMetrics(0f, 0, 0),
            window60m = MomentEnergyEngine.MultiHorizonFeatures.WindowMetrics(0f, 0, 0),
            window180m = MomentEnergyEngine.MultiHorizonFeatures.WindowMetrics(Float.MAX_VALUE, 0, 0)
        )
        val result = features.trendStrength()
        (result >= -1f && result <= 1f) shouldBe true
    }

    "cumulativeInterruptionTrend returns 0 for all-zero interruption windows" {
        val features = MomentEnergyEngine.MultiHorizonFeatures(
            window5m = MomentEnergyEngine.MultiHorizonFeatures.WindowMetrics(0f, 0, 0),
            window15m = MomentEnergyEngine.MultiHorizonFeatures.WindowMetrics(0f, 0, 0),
            window30m = MomentEnergyEngine.MultiHorizonFeatures.WindowMetrics(0f, 0, 0),
            window60m = MomentEnergyEngine.MultiHorizonFeatures.WindowMetrics(0f, 0, 0),
            window180m = MomentEnergyEngine.MultiHorizonFeatures.WindowMetrics(0f, 0, 0)
        )
        features.cumulativeInterruptionTrend() shouldBe 0.0f
    }

    "cumulativeInterruptionTrend result is clamped to [-1, 1] for extreme interruption values" {
        val features = MomentEnergyEngine.MultiHorizonFeatures(
            window5m = MomentEnergyEngine.MultiHorizonFeatures.WindowMetrics(0f, Int.MAX_VALUE, 0),
            window15m = MomentEnergyEngine.MultiHorizonFeatures.WindowMetrics(0f, 0, 0),
            window30m = MomentEnergyEngine.MultiHorizonFeatures.WindowMetrics(0f, 0, 0),
            window60m = MomentEnergyEngine.MultiHorizonFeatures.WindowMetrics(0f, 0, 0),
            window180m = MomentEnergyEngine.MultiHorizonFeatures.WindowMetrics(0f, 0, 0)
        )
        val result = features.cumulativeInterruptionTrend()
        (result >= -1f && result <= 1f) shouldBe true
    }

    "positive multi-horizon trend increases confidence over negative trend" {
        val positiveTrend = MomentEnergyEngine.predict(
            supportiveSnapshot(
                multiHorizonFeatures = MomentEnergyEngine.MultiHorizonFeatures(
                    window5m = MomentEnergyEngine.MultiHorizonFeatures.WindowMetrics(
                        focusMinutes = 1f,
                        interruptionCount = 4,
                        appSwitchCount = 2
                    ),
                    window15m = MomentEnergyEngine.MultiHorizonFeatures.WindowMetrics(
                        focusMinutes = 12f,
                        interruptionCount = 8,
                        appSwitchCount = 4
                    ),
                    window30m = MomentEnergyEngine.MultiHorizonFeatures.WindowMetrics(
                        focusMinutes = 30f,
                        interruptionCount = 12,
                        appSwitchCount = 6
                    ),
                    window60m = MomentEnergyEngine.MultiHorizonFeatures.WindowMetrics(
                        focusMinutes = 72f,
                        interruptionCount = 16,
                        appSwitchCount = 8
                    ),
                    window180m = MomentEnergyEngine.MultiHorizonFeatures.WindowMetrics(
                        focusMinutes = 198f,
                        interruptionCount = 20,
                        appSwitchCount = 10
                    )
                )
            )
        )
        val negativeTrend = MomentEnergyEngine.predict(
            supportiveSnapshot(
                multiHorizonFeatures = MomentEnergyEngine.MultiHorizonFeatures(
                    window5m = MomentEnergyEngine.MultiHorizonFeatures.WindowMetrics(
                        focusMinutes = 5f,
                        interruptionCount = 1,
                        appSwitchCount = 0
                    ),
                    window15m = MomentEnergyEngine.MultiHorizonFeatures.WindowMetrics(
                        focusMinutes = 6f,
                        interruptionCount = 3,
                        appSwitchCount = 1
                    ),
                    window30m = MomentEnergyEngine.MultiHorizonFeatures.WindowMetrics(
                        focusMinutes = 9f,
                        interruptionCount = 8,
                        appSwitchCount = 3
                    ),
                    window60m = MomentEnergyEngine.MultiHorizonFeatures.WindowMetrics(
                        focusMinutes = 10f,
                        interruptionCount = 20,
                        appSwitchCount = 6
                    ),
                    window180m = MomentEnergyEngine.MultiHorizonFeatures.WindowMetrics(
                        focusMinutes = 12f,
                        interruptionCount = 80,
                        appSwitchCount = 20
                    )
                )
            )
        )

        negativeTrend.trendStrength shouldBeLessThan 0f
        positiveTrend.trendStrength shouldBeGreaterThan 0f
        negativeTrend.confidence shouldBeLessThan positiveTrend.confidence
    }
})

private fun supportiveSnapshot(
    peakConfidence: Float = 0.8f,
    signalFreshnessAgeMillis: Long = 0L,
    multiHorizonFeatures: MomentEnergyEngine.MultiHorizonFeatures? = null
): MomentEnergyEngine.MomentSignalSnapshot {
    return MomentEnergyEngine.MomentSignalSnapshot(
        baselineRawEnergy = 15f,
        sleepPressurePoints = 900,
        peakConfidence = peakConfidence,
        minutesSincePeak = 45,
        recentFocusMinutes = 100f,
        recentInterruptionCount = 1,
        recentAppSwitchCount = 1,
        recentPauseResumeCount = 1,
        notificationCount = 1,
        activeTaskCount = 2,
        overdueTaskCount = 0,
        dueSoonTaskCount = 0,
        activeSessionCount = 1,
        recentWindowMinutes = 180,
        multiHorizonFeatures = multiHorizonFeatures,
        signalFreshnessAgeMillis = signalFreshnessAgeMillis
    )
}
