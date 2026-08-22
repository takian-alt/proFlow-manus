package com.neuroflow.app.domain.engine

object MomentEnergyEngine {

    private const val DEFAULT_WINDOW_MINUTES = 180f
    private const val MAX_ADJUSTMENT = 24f

    /**
     * Phase 3: Multi-horizon feature extraction for trend-aware weighting.
     * Captures focus quality and interruption patterns at 5 different time scales.
     */
    data class MultiHorizonFeatures(
        val window5m: WindowMetrics,
        val window15m: WindowMetrics,
        val window30m: WindowMetrics,
        val window60m: WindowMetrics,
        val window180m: WindowMetrics
    ) {
        data class WindowMetrics(
            val focusMinutes: Float,
            val interruptionCount: Int,
            val appSwitchCount: Int
        )

        fun trendStrength(): Float {
            // Measure trend consistency across horizons: strong positive (focus increasing)
            // or strong negative (focus decreasing) gets higher weight
            val focusTrajectory = listOf(
                window5m.focusMinutes,
                window15m.focusMinutes / 3f,    // normalize by window size
                window30m.focusMinutes / 6f,
                window60m.focusMinutes / 12f,
                window180m.focusMinutes / 36f
            )

            // Guard: empty trajectory — no data to compute trend
            if (focusTrajectory.isEmpty()) return 0.0f

            // Calculate trend: positive diff = improving focus quality
            val diffs = focusTrajectory.zipWithNext { a, b -> b - a }

            // Guard: no differences (single element trajectory)
            if (diffs.isEmpty()) return 0.0f

            val avgDiff = diffs.average().toFloat()

            // Guard: NaN or infinite result (e.g. from degenerate float inputs)
            if (avgDiff.isNaN() || avgDiff.isInfinite()) return 0.0f

            // Clip to [-1, 1] to prevent extreme outliers
            return avgDiff.coerceIn(-1f, 1f)
        }

        fun cumulativeInterruptionTrend(): Float {
            // Measure if interruptions are accumulating (negative trend) or clearing (positive)
            val interruptionTrajectory = listOf(
                window5m.interruptionCount.toFloat(),
                window15m.interruptionCount / 3f,
                window30m.interruptionCount / 6f,
                window60m.interruptionCount / 12f,
                window180m.interruptionCount / 36f
            )

            // Guard: empty trajectory — no data to compute trend
            if (interruptionTrajectory.isEmpty()) return 0.0f

            val diffs = interruptionTrajectory.zipWithNext { a, b -> b - a }

            // Guard: no differences (single element trajectory)
            if (diffs.isEmpty()) return 0.0f

            val avgDiff = diffs.average().toFloat()

            // Guard: NaN or infinite result
            if (avgDiff.isNaN() || avgDiff.isInfinite()) return 0.0f

            // Guard: zero (avoids returning -0.0f from negation)
            if (avgDiff == 0.0f) return 0.0f

            // Negative trend = decreasing interruptions = good
            return (-avgDiff).coerceIn(-1f, 1f)
        }
    }

    data class MomentSignalSnapshot(
        val baselineRawEnergy: Float,
        val sleepPressurePoints: Int,
        val peakConfidence: Float,
        val minutesSincePeak: Int,
        val recentFocusMinutes: Float,
        val recentInterruptionCount: Int,
        val recentAppSwitchCount: Int,
        val recentPauseResumeCount: Int,
        val notificationCount: Int,
        val activeTaskCount: Int,
        val overdueTaskCount: Int,
        val dueSoonTaskCount: Int,
        val activeSessionCount: Int,
        val interruptionSensitivity: Float = 1.0f,
        val notificationSensitivity: Float = 1.0f,
        val taskPressureSensitivity: Float = 1.0f,
        val recentWindowMinutes: Int = 180,
        val multiHorizonFeatures: MultiHorizonFeatures? = null,  // Phase 3: optional multi-window analysis
        val signalFreshnessAgeMillis: Long = 0L  // Phase 1: track how fresh the input signals are
    )

    data class MomentEnergyResult(
        val adjustedRawEnergy: Float,
        val usableEnergy: Float,
        val adjustment: Float,
        val confidence: Float,
        val supportScore: Float,
        val pressureScore: Float,
        val trendStrength: Float,         // Phase 3: trend indicator
        val summary: String
    )

    fun predict(snapshot: MomentSignalSnapshot): MomentEnergyResult {
        val windowMinutes = snapshot.recentWindowMinutes.coerceAtLeast(1).toFloat()
        val focusDensity = (snapshot.recentFocusMinutes / windowMinutes).coerceIn(0f, 1f)
        val interruptionSensitivity = snapshot.interruptionSensitivity.coerceIn(0.5f, 2.0f)
        val notificationSensitivity = snapshot.notificationSensitivity.coerceIn(0.5f, 2.0f)
        val taskPressureSensitivity = snapshot.taskPressureSensitivity.coerceIn(0.5f, 2.0f)
        val interruptionLoad = (
            (snapshot.recentInterruptionCount * 0.18f) +
                (snapshot.recentAppSwitchCount * 0.12f) +
                (snapshot.recentPauseResumeCount * 0.08f)
            ).div(8f).times(interruptionSensitivity).coerceIn(0f, 1f)
        val notificationLoad = (snapshot.notificationCount / 20f)
            .times(notificationSensitivity)
            .coerceIn(0f, 1f)
        val taskPressure = (
            (snapshot.activeTaskCount * 0.05f) +
                (snapshot.overdueTaskCount * 0.18f) +
                (snapshot.dueSoonTaskCount * 0.10f)
            ).times(taskPressureSensitivity).coerceIn(0f, 1f)
        val sleepPressure = SleepPressureDetector.fatigueRatio(snapshot.sleepPressurePoints)
        val peakConfidence = snapshot.peakConfidence.coerceIn(0f, 1f)

        val supportScore = (
            focusDensity * 0.45f +
                (1f - interruptionLoad) * 0.25f +
                (1f - notificationLoad) * 0.15f +
                (1f - taskPressure) * 0.15f
            ).coerceIn(0f, 1f)

        val pressureScore = (
            sleepPressure * 0.55f +
                (snapshot.minutesSincePeak / DEFAULT_WINDOW_MINUTES).coerceIn(0f, 1f) * 0.20f +
                taskPressure * 0.25f
            ).coerceIn(0f, 1f)

        // Phase 3: Multi-horizon trend analysis for adaptive weighting
        var trendStrength = 0f
        if (snapshot.multiHorizonFeatures != null) {
            trendStrength = snapshot.multiHorizonFeatures.trendStrength()
        }

        val densityFactor = (0.40f + supportScore * 0.45f + peakConfidence * 0.15f).coerceIn(0.25f, 1f)
        val calmFactor = (1f - interruptionLoad * 0.35f - notificationLoad * 0.15f).coerceIn(0.45f, 1f)

        // Phase 1 staleness guard: downweight confidence if peak detection confidence is low (< 0.4)
        // This prevents moment adjustments from being applied when baseline peak is unreliable
        val peakStalenessGuard = if (peakConfidence < 0.4f) {
            peakConfidence / 0.4f  // Linear scale: 0.0->0.0, 0.4->1.0
        } else {
            1f
        }

        // Phase 3: Trend strength amplifies confidence when momentum is positive (improving focus)
        // Dampens when momentum is negative (degrading focus) to prevent false optimism
        val trendWeighting = if (trendStrength > 0f) {
            1f + (trendStrength * 0.2f)  // +0% to +20% confidence boost for positive trends
        } else {
            1f + (trendStrength * 0.15f)  // -0% to -15% confidence penalty for negative trends
        }

        // Phase 1 staleness guard: downweight confidence if input signals are stale (>5 minutes old)
        // Stale signals mean moment adjustments are less reliable and should not override baseline
        val SIGNAL_FRESHNESS_THRESHOLD_MILLIS = 5 * 60_000L
        val signalStalenessGuard = if (snapshot.signalFreshnessAgeMillis > SIGNAL_FRESHNESS_THRESHOLD_MILLIS) {
            val excessStaleMinutes = ((snapshot.signalFreshnessAgeMillis - SIGNAL_FRESHNESS_THRESHOLD_MILLIS) / 60_000f)
            (1f - (excessStaleMinutes / 10f)).coerceIn(0f, 1f)
        } else {
            1f
        }

        val confidence = (densityFactor * calmFactor * peakStalenessGuard * signalStalenessGuard * trendWeighting).coerceIn(0.35f, 0.98f)

        val adjustment = ((supportScore - pressureScore) * MAX_ADJUSTMENT).coerceIn(-MAX_ADJUSTMENT, MAX_ADJUSTMENT)
        val adjustedRawEnergy = (snapshot.baselineRawEnergy + adjustment * confidence).coerceIn(-100f, 100f)
        val usableEnergy = ((adjustedRawEnergy + 100f) / 2f).coerceIn(0f, 100f)

        return MomentEnergyResult(
            adjustedRawEnergy = adjustedRawEnergy,
            usableEnergy = usableEnergy,
            adjustment = adjustment,
            confidence = confidence,
            supportScore = supportScore,
            pressureScore = pressureScore,
            trendStrength = trendStrength,
            summary = summarize(snapshot, supportScore, pressureScore, confidence)
        )
    }

    private fun summarize(
        snapshot: MomentSignalSnapshot,
        supportScore: Float,
        pressureScore: Float,
        confidence: Float
    ): String {
        val supportLabel = when {
            supportScore >= 0.7f -> "recent context is supportive"
            supportScore >= 0.45f -> "recent context is mixed"
            else -> "recent context is weak"
        }
        val pressureLabel = when {
            pressureScore >= 0.7f -> "pressure is high"
            pressureScore >= 0.4f -> "pressure is moderate"
            else -> "pressure is low"
        }
        val sessionLabel = when {
            snapshot.activeSessionCount > 0 -> "active session present"
            snapshot.recentFocusMinutes >= 60f -> "good recent focus"
            snapshot.recentFocusMinutes >= 20f -> "some recent focus"
            else -> "little recent focus"
        }
        return "$supportLabel, $pressureLabel, $sessionLabel, confidence ${(confidence * 100).toInt()}%"
    }
}
