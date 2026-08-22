package com.neuroflow.app.presentation.common

import com.neuroflow.app.data.local.UserPreferences
import com.neuroflow.app.domain.engine.SleepPressureDetector
import com.neuroflow.app.domain.engine.PeakEnergyEngine
import com.neuroflow.app.domain.model.EnergyLevel
import java.util.Calendar
import kotlin.math.abs

object EnergyInsight {

    fun isMorningType(chronotype: String?): Boolean {
        return chronotype == "MODERATE_MORNING" || chronotype == "DEFINITE_MORNING"
    }

    fun effectivePeakMinuteOfDay(prefs: UserPreferences): Int {
        return if (
            prefs.quizPeakEnabled &&
            prefs.effectivePeakMinuteOfDay in 0 until (24 * 60)
        ) {
            prefs.effectivePeakMinuteOfDay
        } else {
            (prefs.peakEnergyStart.coerceIn(0, 23) * 60)
        }
    }

    fun detectedPeakMinuteOfDayOrNull(prefs: UserPreferences): Int? {
        return prefs.detectedPeakMinuteOfDay.takeIf { it in 0 until (24 * 60) }
    }

    fun confidencePercent(prefs: UserPreferences): Int {
        return (prefs.peakDetectionConfidence.coerceIn(0f, 1f) * 100f).toInt()
    }

    fun minuteLabel(minuteOfDay: Int): String {
        val normalized = ((minuteOfDay % (24 * 60)) + (24 * 60)) % (24 * 60)
        val hour = normalized / 60
        val minute = normalized % 60
        val amPm = if (hour < 12) "am" else "pm"
        val displayHour = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return String.format("%d:%02d%s", displayHour, minute, amPm)
    }

    fun timingHintForNow(targetMinuteOfDay: Int, now: Calendar = Calendar.getInstance()): String {
        val nowMinute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val minutesToPeak = minuteDistance(nowMinute, targetMinuteOfDay)
        return if (minutesToPeak <= 10) {
            "Peak zone now"
        } else {
            "Peak around ${minuteLabel(targetMinuteOfDay)}"
        }
    }

    fun confidenceTier(confidence: Float): String {
        val safe = confidence.coerceIn(0f, 1f)
        return when {
            safe >= 0.8f -> "High"
            safe >= 0.6f -> "Moderate"
            else -> "Low"
        }
    }

    fun ageLabel(ageMillis: Long): String {
        val safe = ageMillis.coerceAtLeast(0L)
        val minutes = safe / 60_000L
        return when {
            minutes < 1L -> "<1m"
            minutes < 60L -> "${minutes}m"
            else -> {
                val hours = minutes / 60L
                val rem = minutes % 60L
                if (rem == 0L) "${hours}h" else "${hours}h ${rem}m"
            }
        }
    }

    fun freshnessLabel(ageMillis: Long): String {
        val safe = ageMillis.coerceAtLeast(0L)
        return when {
            safe <= 2 * 60_000L -> "Fresh"
            safe <= 10 * 60_000L -> "Aging"
            else -> "Stale"
        }
    }

    fun stabilityScore(
        momentConfidence: Float,
        peakConfidence: Float,
        freshnessAgeMillis: Long
    ): Int {
        val safeMoment = momentConfidence.coerceIn(0f, 1f)
        val safePeak = peakConfidence.coerceIn(0f, 1f)
        val freshnessFactor = when {
            freshnessAgeMillis <= 2 * 60_000L -> 1f
            freshnessAgeMillis <= 10 * 60_000L -> 0.82f
            freshnessAgeMillis <= 30 * 60_000L -> 0.62f
            else -> 0.45f
        }
        val composite = (
            safeMoment * 0.45f +
                safePeak * 0.35f +
                freshnessFactor * 0.20f
            ).coerceIn(0f, 1f)
        return (composite * 100f).toInt().coerceIn(0, 100)
    }

    fun confidenceTierRationale(
        momentConfidence: Float,
        peakConfidence: Float,
        freshnessAgeMillis: Long
    ): String {
        val tier = confidenceTier(((momentConfidence + peakConfidence) / 2f).coerceIn(0f, 1f))
        val freshness = freshnessLabel(freshnessAgeMillis).lowercase()
        return "$tier confidence • peak ${(peakConfidence.coerceIn(0f, 1f) * 100f).toInt()}% • " +
            "moment ${(momentConfidence.coerceIn(0f, 1f) * 100f).toInt()}% • signals $freshness"
    }

    fun backtestFitLabel(errorMinutes: Float?): String {
        if (errorMinutes == null) return "Backtest fit: pending"
        val rounded = errorMinutes.toInt().coerceAtLeast(0)
        val quality = when {
            rounded <= 30 -> "Strong"
            rounded <= 75 -> "Moderate"
            else -> "Weak"
        }
        return "Backtest fit: $quality (${rounded}m error)"
    }

    fun whyEnergyChanged(momentAdjustment: Float, momentSummary: String): String {
        val direction = when {
            momentAdjustment >= 4f -> "Energy moved up from supportive context"
            momentAdjustment <= -4f -> "Energy moved down from pressure signals"
            else -> "Energy stayed close to baseline"
        }
        val summary = momentSummary.ifBlank { "Context signals are limited." }
        return "$direction. $summary"
    }

    fun whatToDoNow(
        availableEnergy: Int,
        fatigueZone: SleepPressureDetector.FatigueZone,
        hasRecentData: Boolean
    ): String {
        if (!hasRecentData) {
            return "Signals look stale. Refresh context and avoid major priority changes until data updates."
        }
        return when {
            availableEnergy >= 75 && fatigueZone <= SleepPressureDetector.FatigueZone.MODERATE -> {
                "Do deep work now while capacity is high."
            }
            availableEnergy >= 50 -> {
                "Push meaningful progress on medium-complexity tasks."
            }
            availableEnergy >= 30 -> {
                "Use this window for admin tasks and interruption cleanup."
            }
            else -> {
                "Take a short recovery break, then restart with a small low-friction task."
            }
        }
    }

    fun taskEnergyMismatchHint(taskEnergyLevel: EnergyLevel, availableEnergy: Int): String? {
        val score = availableEnergy.coerceIn(0, 100)
        return when (taskEnergyLevel) {
            EnergyLevel.HIGH -> if (score < 60) {
                "This task needs high energy, but your current score is low. Consider a shorter warm-up task first."
            } else {
                null
            }

            EnergyLevel.MEDIUM -> if (score < 35) {
                "This medium-energy task may feel heavy right now. Break it into a smaller first step."
            } else {
                null
            }

            EnergyLevel.LOW -> if (score > 75) {
                "You have surplus energy now. A higher-energy task could create better momentum."
            } else {
                null
            }
        }
    }

    fun profileSummary(profile: PeakEnergyEngine.EffectivePeakProfile?): String {
        if (profile == null) return "Baseline profile"
        val windows = profile.windows.joinToString(separator = ", ") { w ->
            "${(w.durationMinutes / 60f)}h"
        }
        return "${profile.profileType.name.lowercase().replaceFirstChar { it.uppercase() }} profile • windows $windows"
    }

    fun profileConfidenceLine(profile: PeakEnergyEngine.EffectivePeakProfile?): String {
        if (profile == null) return "Confidence unavailable"
        val c = profile.confidence
        return "Confidence ${confidenceTier(c.overall)} (${(c.overall * 100).toInt()}%) • " +
            "sleep ${(c.sleepCoverage * 100).toInt()}% • wake ${(c.wakeConsistency * 100).toInt()}% • behavior ${(c.behaviorPerformance * 100).toInt()}%"
    }

    fun windowConfidencePercent(profile: PeakEnergyEngine.EffectivePeakProfile?, windowIndex: Int): Int {
        val fallback = profile?.confidence?.overall ?: 0.5f
        val value = profile?.windowConfidences?.getOrNull(windowIndex)?.coerceIn(0f, 1f) ?: fallback
        return (value * 100f).toInt().coerceIn(0, 100)
    }

    fun windowConfidenceTier(profile: PeakEnergyEngine.EffectivePeakProfile?, windowIndex: Int): String {
        val fallback = profile?.confidence?.overall ?: 0.5f
        val value = profile?.windowConfidences?.getOrNull(windowIndex)?.coerceIn(0f, 1f) ?: fallback
        return confidenceTier(value)
    }

    fun confidenceImprovementActions(profile: PeakEnergyEngine.EffectivePeakProfile?): List<String> {
        if (profile == null) {
            return listOf("Complete MEQ and log sleep for at least 5 nights to calibrate peak windows.")
        }

        if (profile.confidenceGatedAbstention) {
            val reason = profile.abstentionReason.ifBlank { "insufficient confidence for personalized prediction" }
            return listOf(
                "Prediction is currently withheld: $reason.",
                "Log sleep daily for 7 nights and keep wake time inside a 30-minute band.",
                "Complete at least one 20+ minute focus session during each suggested peak window for 5 days.",
                "Keep manual profile overrides off during this recovery period so the model can re-calibrate."
            )
        }

        val actions = mutableListOf<String>()
        val c = profile.confidence
        val peak2 = profile.windowConfidences.getOrNull(1) ?: c.overall
        val peak3 = profile.windowConfidences.getOrNull(2) ?: c.overall

        if (c.sleepCoverage < 0.7f) {
            actions += "Log sleep daily for 7 nights so the model can stabilize fatigue and peak timing."
        }
        if (c.wakeConsistency < 0.72f) {
            actions += "Keep wake time within +/-30 minutes for the next week to reduce timing drift."
        }
        if (c.behaviorPerformance < 0.7f) {
            actions += "Complete at least one focused task in each suggested peak window for 5 days."
        }
        if (peak2 < 0.65f || peak3 < 0.6f) {
            actions += "For Peak 2/3, schedule medium or light tasks there and avoid frequent app switching."
        }

        val backtestError = profile.weeklyBacktestErrorMinutes
        if (backtestError != null && backtestError > 75f) {
            actions += "Backtest error is high; keep manual overrides off for a few days so adaptive tuning can catch up."
        }
        if (profile.driftStatus != "stable") {
            actions += "Drift is detected; shift planned deep-work start by 30-60 minutes and reassess after 3 days."
        }

        if (actions.isEmpty()) {
            actions += "Confidence is stable. Keep current sleep logging and wake-time consistency to maintain it."
        }

        return actions.take(4)
    }

    fun adaptiveHint(profile: PeakEnergyEngine.EffectivePeakProfile?): String {
        return profile?.explanation ?: "Using baseline peak estimate."
    }

    fun backtestSummary(profile: PeakEnergyEngine.EffectivePeakProfile?): String {
        return backtestFitLabel(profile?.weeklyBacktestErrorMinutes)
    }

    fun profileModeLabel(manualOverrideEnabled: Boolean, profile: PeakEnergyEngine.EffectivePeakProfile?): String {
        return when {
            manualOverrideEnabled -> "Profile mode: Manual override"
            profile?.confidenceGatedAbstention == true -> "Profile mode: Confidence-gated abstention (safe baseline)"
            profile?.adaptiveFreezeMode == true -> "Profile mode: Adaptive freeze safety fallback"
            profile != null -> "Profile mode: Adaptive ${profile.profileType.name.lowercase()}"
            else -> "Profile mode: Baseline"
        }
    }

    private fun minuteDistance(a: Int, b: Int): Int {
        val raw = abs(a - b)
        return minOf(raw, (24 * 60) - raw)
    }
}
