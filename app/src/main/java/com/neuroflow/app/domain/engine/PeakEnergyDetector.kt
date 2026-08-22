package com.neuroflow.app.domain.engine

import com.neuroflow.app.data.local.entity.TimeSessionEntity
import java.util.Calendar
import kotlin.math.roundToInt

/**
 * Dynamically detects the user's peak energy window from their actual focus session history.
 *
 * Algorithm:
 *  1. Bucket all closed sessions by hour-of-day (0–23).
 *  2. Apply exponential recency weighting: sessions in the last 7 days count 3×,
 *     last 30 days 1.5×, older 0.5× — so recent behaviour dominates.
 *  3. Find the 3-hour sliding window with the highest total weighted focus minutes.
 *  4. Return a [DetectionResult] with the detected window, confidence (0–1),
 *     and whether there is enough data to act on it.
 *
 * Confidence formula:
 *   confidence = min(1.0, sessionCount / MIN_SESSIONS_FOR_FULL_CONFIDENCE)
 *
 * The scoring engine blends detected peak with the user's manual setting:
 *   effectivePeak = lerp(manualPeak, detectedPeak, confidence)
 */
object PeakEnergyDetector {

    /** Minimum sessions before we trust the detection enough to auto-update. */
    const val MIN_SESSIONS_FOR_UPDATE = 10

    /** Sessions needed for full confidence (confidence = 1.0). */
    private const val MIN_SESSIONS_FOR_FULL_CONFIDENCE = 30

    /** Width of the peak window in hours. */
    const val WINDOW_HOURS = 3

    data class DetectionResult(
        /** Detected peak start hour (0–23). */
        val detectedStart: Int,
        /** Detected peak end hour (0–23, exclusive). */
        val detectedEnd: Int,
        /** 0.0 = no data, 1.0 = fully confident. */
        val confidence: Float,
        /** True when there are enough sessions to auto-update preferences. */
        val hasEnoughData: Boolean,
        /** Per-hour weighted focus minutes (index = hour 0–23). */
        val hourlyWeights: FloatArray
    )

    fun detect(
        sessions: List<TimeSessionEntity>,
        nowMillis: Long = System.currentTimeMillis()
    ): DetectionResult {
        val closed = sessions.filter { it.endedAt != null && it.durationMinutes > 0f }

        val hourlyWeights = FloatArray(24)

        val sevenDaysAgo = nowMillis - 7 * 86_400_000L
        val thirtyDaysAgo = nowMillis - 30 * 86_400_000L

        closed.forEach { session ->
            val cal = Calendar.getInstance().apply { timeInMillis = session.startedAt }
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val recencyWeight = when {
                session.startedAt >= sevenDaysAgo  -> 3.0f
                session.startedAt >= thirtyDaysAgo -> 1.5f
                else                               -> 0.5f
            }
            hourlyWeights[hour] += session.durationMinutes * recencyWeight
        }

        // Find the 3-hour window with the highest total weighted minutes
        var bestStart = 8  // default morning if no data
        var bestScore = -1f
        for (start in 0..21) {  // 0..21 so window [start, start+2] stays within 0–23
            val windowScore = hourlyWeights[start] + hourlyWeights[start + 1] + hourlyWeights[start + 2]
            if (windowScore > bestScore) {
                bestScore = windowScore
                bestStart = start
            }
        }

        val confidence = (closed.size.toFloat() / MIN_SESSIONS_FOR_FULL_CONFIDENCE).coerceIn(0f, 1f)

        return DetectionResult(
            detectedStart = bestStart,
            detectedEnd = (bestStart + WINDOW_HOURS).coerceAtMost(23),
            confidence = confidence,
            hasEnoughData = closed.size >= MIN_SESSIONS_FOR_UPDATE,
            hourlyWeights = hourlyWeights
        )
    }

    /**
     * Blends the manually-set peak with the detected peak based on confidence.
     * Returns the effective peak start/end to use in scoring.
     */
    fun effectivePeak(
        manualStart: Int,
        manualEnd: Int,
        result: DetectionResult
    ): Pair<Int, Int> {
        if (!result.hasEnoughData) return manualStart to manualEnd
        val c = result.confidence
        val effectiveStart = lerp(manualStart.toFloat(), result.detectedStart.toFloat(), c).roundToInt()
        val effectiveEnd = lerp(manualEnd.toFloat(), result.detectedEnd.toFloat(), c).roundToInt()
        return effectiveStart to effectiveEnd
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
}
