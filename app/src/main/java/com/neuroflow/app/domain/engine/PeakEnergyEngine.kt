package com.neuroflow.app.domain.engine

import java.util.Calendar
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Baseline peak-energy engine built from the user's MEQ chronotype result.
 *
 * The peak time is derived from the user's wake-up time plus a chronotype offset:
 *  - definite morning: right after waking (0 hours)
 *  - moderate morning: 2.5 hours after waking
 *  - intermediate: 2.5 hours after waking
 *  - moderate evening: 5.5 hours after waking
 *  - definite evening: 5.5 hours after waking
 *
 * The resulting baseline peak is a single time point with a nominal peak value of 4000.
 */
object PeakEnergyEngine {

    const val PEAK_VALUE = 4000
    private const val MINUTES_PER_DAY = 24 * 60
    private const val SOFT_MAX_SLEEP_PRESSURE = 3000f
    private const val SLOT_BUCKET_MINUTES = 60
    private const val MIN_WINDOW_GAP_MINUTES = 90

    data class PeakWindow(
        val startMinuteOffset: Int,
        val durationMinutes: Int,
        val amplitude: Float
    )

    data class CircadianProfile(
        val windows: List<PeakWindow>,
        val phaseShiftMinutes: Int = 0
    )

    enum class ProfileType {
        WORKDAY,
        WEEKEND
    }

    data class ConfidenceComponents(
        val sleepCoverage: Float,
        val wakeConsistency: Float,
        val behaviorPerformance: Float,
        val overall: Float
    )

    data class SlotPerformanceAggregate(
        val bucketStartMinute: Int,
        val qualityWeightedCompletionRate: Float,
        val abortRate: Float,
        val distractionRate: Float,
        val sampleCount: Int
    )

    data class TaskTypePerformanceAggregate(
        val taskType: String,
        val averageBestBucketMinute: Int,
        val weightedQuality: Float,
        val sampleCount: Int
    )

    data class TuningCoefficients(
        val sleepWeight: Float = 0.30f,
        val wakeWeight: Float = 0.25f,
        val behaviorWeight: Float = 0.25f,
        val baseWeight: Float = 0.20f
    )

    data class ProfileOverride(
        val enabled: Boolean = false,
        val profileType: ProfileType? = null,
        val anchorMinuteOfDay: Int? = null,
        val windows: List<PeakWindow> = emptyList()
    )

    data class MorningPersonalizationSignals(
        val averageSleepMinutes: Int? = null,
        val wakeVarianceMinutes: Int? = null,
        val sleepLogCoverage: Float = 0f,
        val profileType: ProfileType = ProfileType.WORKDAY,
        val baselineAnchorMinuteOfDay: Int = 150,
        val behaviorSlots: List<SlotPerformanceAggregate> = emptyList(),
        val taskTypePerformance: List<TaskTypePerformanceAggregate> = emptyList(),
        val behaviorCoverage: Float = 0f,
        val recentWeight: Float = 1f,
        val coldStartFactor: Float = 1f,
        val driftMinutes: Int = 0,
        val weeklyBacktestErrorMinutes: Float? = null,
        val tuning: TuningCoefficients = TuningCoefficients(),
        val profileOverride: ProfileOverride? = null,
        // Phase 3: Adaptive tuning trigger fields
        val predictedVsObservedDivergence: Float = 0f,  // Backtest error magnitude (0..1 scale)
        val lastTuningUpdatedAtMillis: Long = 0L,       // Timestamp of last adaptive tuning
        val sampleCountSinceLastTuning: Int = 0,        // Predictions captured since last tuning
        val shouldTriggerAdaptiveTuning: Boolean = false, // Signal to recompute tuning coefficients
        val adaptiveFreezeMode: Boolean = false,
        val confidenceGatedAbstention: Boolean = false,
        val abstentionReason: String = ""
    )

    data class EffectivePeakProfile(
        val profileType: ProfileType,
        val anchorMinuteOfDay: Int,
        val windows: List<PeakWindow>,
        val confidence: ConfidenceComponents,
        val windowConfidences: List<Float> = emptyList(),
        val explanation: String,
        val driftStatus: String = "stable",
        val weeklyBacktestErrorMinutes: Float? = null,
        val adaptiveFreezeMode: Boolean = false,
        val confidenceGatedAbstention: Boolean = false,
        val abstentionReason: String = ""
    )

    data class DetectionResult(
        val chronotype: MEQChronotypeDetector.Chronotype,
        val wakeUpHour: Int,
        val peakOffsetHours: Float,
        val peakHourOfDay: Int,
        val peakMinuteOfDay: Int,
        val peakValue: Int = PEAK_VALUE,
        val confidence: Float,
        val circadianProfile: CircadianProfile = defaultCircadianProfile(chronotype),
        val effectiveProfile: EffectivePeakProfile = defaultEffectiveProfile(
            chronotype = chronotype,
            peakMinuteOfDay = peakMinuteOfDay
        ),
        val confidenceGatedAbstention: Boolean = false,
        val abstentionReason: String = "",
        val detectedAtMillis: Long = System.currentTimeMillis()
    ) {

        /**
         * Returns the current peak-energy value, decreasing by 1 point per minute after
         * the assigned peak time. The value resets to [peakValue] at the next daily peak
         * timepoint, rather than needing to decay all the way to zero.
         *
         * Clamped to [0, peakValue] to prevent negative values from being exposed.
         */
        fun currentValueAt(nowMillis: Long = System.currentTimeMillis()): Int {
            val nowMinuteOfDay = minuteOfDay(nowMillis)
            val minutesSincePeak = minutesSincePeak(nowMinuteOfDay)
            return (peakValue - minutesSincePeak).coerceAtLeast(0)
        }

        /** Minutes elapsed since the most recent peak occurrence. */
        fun minutesSincePeak(nowMillis: Long = System.currentTimeMillis()): Int {
            val nowMinuteOfDay = minuteOfDay(nowMillis)
            return minutesSincePeak(nowMinuteOfDay)
        }

        private fun minutesSincePeak(nowMinuteOfDay: Int): Int {
            return if (nowMinuteOfDay >= peakMinuteOfDay) {
                nowMinuteOfDay - peakMinuteOfDay
            } else {
                nowMinuteOfDay + (24 * 60) - peakMinuteOfDay
            }
        }
    }

    fun detect(
        meqResult: MEQChronotypeDetector.Result,
        wakeUpHour: Int,
        sleepHour: Int? = null,
        sleepPressurePoints: Int = 0,
        personalizationSignals: MorningPersonalizationSignals? = null
    ): DetectionResult {
        val profile = buildCircadianProfile(
            chronotype = meqResult.chronotype,
            wakeUpHour = wakeUpHour,
            sleepHour = sleepHour,
            sleepPressurePoints = sleepPressurePoints,
            signals = personalizationSignals
        )
        val override = personalizationSignals?.profileOverride
        val offsetHours = (chronotypeOffset(meqResult.chronotype) + profile.phaseShiftMinutes / 60f)
        val normalizedWakeHour = normalizeHour(wakeUpHour)
        val peakMinuteOfDay = override?.anchorMinuteOfDay?.takeIf { override.enabled }?.let(::normalizeMinuteOfDay)
            ?: (((normalizedWakeHour * 60) + (offsetHours * 60f).roundToInt()) % MINUTES_PER_DAY)
        val confidenceComponents = confidenceComponents(
            baseConfidence = meqResult.confidence,
            chronotype = meqResult.chronotype,
            wakeUpHour = wakeUpHour,
            sleepHour = sleepHour,
            sleepPressurePoints = sleepPressurePoints,
            signals = personalizationSignals
        )
        val effectiveProfile = buildEffectiveProfile(
            chronotype = meqResult.chronotype,
            peakMinuteOfDay = peakMinuteOfDay,
            profile = profile,
            confidence = confidenceComponents,
            signals = personalizationSignals
        )

        return DetectionResult(
            chronotype = meqResult.chronotype,
            wakeUpHour = normalizedWakeHour,
            peakOffsetHours = offsetHours,
            peakHourOfDay = peakMinuteOfDay / 60,
            peakMinuteOfDay = peakMinuteOfDay,
            peakValue = PEAK_VALUE,
            confidence = confidenceComponents.overall,
            circadianProfile = profile,
            effectiveProfile = effectiveProfile,
            confidenceGatedAbstention = personalizationSignals?.confidenceGatedAbstention == true,
            abstentionReason = personalizationSignals?.abstentionReason.orEmpty()
        )
    }

    fun chronotypeOffset(chronotype: MEQChronotypeDetector.Chronotype): Float {
        return when (chronotype) {
            MEQChronotypeDetector.Chronotype.DEFINITE_MORNING -> 0f
            MEQChronotypeDetector.Chronotype.MODERATE_MORNING -> 2.5f
            MEQChronotypeDetector.Chronotype.INTERMEDIATE -> 2.5f
            MEQChronotypeDetector.Chronotype.MODERATE_EVENING -> 5.5f
            MEQChronotypeDetector.Chronotype.DEFINITE_EVENING -> 5.5f
        }
    }

    fun baselinePeakMinuteOfDay(meqResult: MEQChronotypeDetector.Result, wakeUpHour: Int): Int {
        return detect(meqResult, wakeUpHour).peakMinuteOfDay
    }

    fun defaultCircadianProfile(chronotype: MEQChronotypeDetector.Chronotype): CircadianProfile {
        return when (chronotype) {
            MEQChronotypeDetector.Chronotype.DEFINITE_MORNING,
            MEQChronotypeDetector.Chronotype.MODERATE_MORNING -> CircadianProfile(
                windows = listOf(
                    PeakWindow(startMinuteOffset = 0, durationMinutes = 210, amplitude = 1.0f),
                    PeakWindow(startMinuteOffset = 570, durationMinutes = 150, amplitude = 0.8f),
                    PeakWindow(startMinuteOffset = 810, durationMinutes = 60, amplitude = 0.6f)
                )
            )
            MEQChronotypeDetector.Chronotype.INTERMEDIATE -> CircadianProfile(
                windows = listOf(
                    // Intermediate baseline: 2.5h, 10.5h, 14.5h after wake with durations 3.5h, 2.5h, 1h.
                    // Offsets below are relative to the first peak anchor in this engine's profile space.
                    PeakWindow(startMinuteOffset = 0, durationMinutes = 210, amplitude = 1.0f),
                    PeakWindow(startMinuteOffset = 480, durationMinutes = 150, amplitude = 0.78f),
                    PeakWindow(startMinuteOffset = 720, durationMinutes = 60, amplitude = 0.58f)
                )
            )
            MEQChronotypeDetector.Chronotype.MODERATE_EVENING,
            MEQChronotypeDetector.Chronotype.DEFINITE_EVENING -> CircadianProfile(
                windows = listOf(
                    // Night-owl baseline: 5.5h, 11h, 15.5h after wake with durations 3h, 4h, 1h.
                    // Offsets below are relative to the first peak anchor in this engine's profile space.
                    PeakWindow(startMinuteOffset = 0, durationMinutes = 180, amplitude = 1.0f),
                    PeakWindow(startMinuteOffset = 330, durationMinutes = 240, amplitude = 0.85f),
                    PeakWindow(startMinuteOffset = 600, durationMinutes = 60, amplitude = 0.6f)
                )
            )
        }
    }

    fun defaultEffectiveProfile(
        chronotype: MEQChronotypeDetector.Chronotype,
        peakMinuteOfDay: Int
    ): EffectivePeakProfile {
        val confidence = ConfidenceComponents(
            sleepCoverage = 0.5f,
            wakeConsistency = 0.5f,
            behaviorPerformance = 0.5f,
            overall = 0.5f
        )
        val windows = defaultCircadianProfile(chronotype).windows
        return EffectivePeakProfile(
            profileType = ProfileType.WORKDAY,
            anchorMinuteOfDay = normalizeMinuteOfDay(peakMinuteOfDay),
            windows = windows,
            confidence = confidence,
            windowConfidences = buildWindowConfidences(
                windows = windows,
                confidence = confidence,
                weeklyBacktestErrorMinutes = null,
                driftMinutes = 0
            ),
            explanation = "Baseline profile"
        )
    }

    private fun buildCircadianProfile(
        chronotype: MEQChronotypeDetector.Chronotype,
        wakeUpHour: Int,
        sleepHour: Int?,
        sleepPressurePoints: Int,
        signals: MorningPersonalizationSignals?
    ): CircadianProfile {
        val override = signals?.profileOverride
        val base = if (override?.enabled == true && override.windows.isNotEmpty()) {
            CircadianProfile(
                windows = override.windows.map { w ->
                    w.copy(
                        durationMinutes = w.durationMinutes.coerceIn(30, 6 * 60),
                        amplitude = w.amplitude.coerceIn(0.2f, 1f)
                    )
                }
            )
        } else {
            defaultCircadianProfile(chronotype)
        }
        if (override?.enabled == true || signals?.adaptiveFreezeMode == true || signals?.confidenceGatedAbstention == true) {
            return base.copy(phaseShiftMinutes = 0)
        }

        // Phase 3 chronotype refinement: all chronotypes get adaptive shifts,
        // with chronotype-specific scaling so evening/intermediate users adapt safely.
        val chronotypeShiftScale = when (chronotype) {
            MEQChronotypeDetector.Chronotype.DEFINITE_MORNING,
            MEQChronotypeDetector.Chronotype.MODERATE_MORNING -> 1.0f
            MEQChronotypeDetector.Chronotype.INTERMEDIATE -> 0.82f
            MEQChronotypeDetector.Chronotype.MODERATE_EVENING -> 0.72f
            MEQChronotypeDetector.Chronotype.DEFINITE_EVENING -> 0.65f
        }
        val chronotypeAmplitudeScale = when (chronotype) {
            MEQChronotypeDetector.Chronotype.DEFINITE_MORNING,
            MEQChronotypeDetector.Chronotype.MODERATE_MORNING -> 1.0f
            MEQChronotypeDetector.Chronotype.INTERMEDIATE -> 0.94f
            MEQChronotypeDetector.Chronotype.MODERATE_EVENING -> 0.91f
            MEQChronotypeDetector.Chronotype.DEFINITE_EVENING -> 0.88f
        }
        val (minShiftMinutes, maxShiftMinutes) = when (chronotype) {
            MEQChronotypeDetector.Chronotype.DEFINITE_MORNING,
            MEQChronotypeDetector.Chronotype.MODERATE_MORNING -> -35 to 110
            MEQChronotypeDetector.Chronotype.INTERMEDIATE -> -45 to 90
            MEQChronotypeDetector.Chronotype.MODERATE_EVENING -> -60 to 75
            MEQChronotypeDetector.Chronotype.DEFINITE_EVENING -> -70 to 65
        }

        val normalizedWake = normalizeHour(wakeUpHour)
        val normalizedSleep = sleepHour?.let(::normalizeHour)
        val preferenceSleepDurationHours = if (normalizedSleep != null) {
            (((normalizedWake - normalizedSleep + 24) % 24).toFloat()).coerceIn(3f, 12f)
        } else {
            8f
        }
        val observedSleepDurationHours = signals?.averageSleepMinutes
            ?.coerceIn(180, 720)
            ?.div(60f)
        val observedWeight = (signals?.sleepLogCoverage ?: 0f).coerceIn(0f, 1f)
        val sleepDurationHours = (
            (preferenceSleepDurationHours * (1f - observedWeight)) +
                ((observedSleepDurationHours ?: preferenceSleepDurationHours) * observedWeight)
            ).coerceIn(3f, 12f)

        val sleepDebtHours = (7.5f - sleepDurationHours).coerceAtLeast(0f)
        val oversleepHours = (sleepDurationHours - 9f).coerceAtLeast(0f)
        val pressureRatio = (sleepPressurePoints.coerceAtLeast(0).toFloat() / SOFT_MAX_SLEEP_PRESSURE).coerceIn(0f, 1f)
        val wakeVarianceRatio = (
            (signals?.wakeVarianceMinutes?.coerceAtLeast(0)?.toFloat() ?: 0f) / 120f
            ).coerceIn(0f, 1f)
        val baselineAnchor = normalizeMinuteOfDay(signals?.baselineAnchorMinuteOfDay ?: 150)
        val behaviorShift = behaviorDrivenShiftMinutes(
            slots = signals?.behaviorSlots ?: emptyList(),
            baselineAnchorMinute = baselineAnchor
        )
        val taskTypeShift = taskTypeShiftMinutes(
            perf = signals?.taskTypePerformance ?: emptyList(),
            baselineAnchorMinute = baselineAnchor
        )
        val coldStartDampening = (signals?.coldStartFactor ?: 1f).coerceIn(0.3f, 1f)
        val driftShift = (signals?.driftMinutes ?: 0).coerceIn(-35, 35)

        val phaseShiftMinutes = (
            (sleepDebtHours * 18f) +
                (pressureRatio * 20f) -
                (oversleepHours * 10f) +
                (wakeVarianceRatio * 26f) +
                ((behaviorShift + taskTypeShift) * coldStartDampening) +
                driftShift
            ).times(chronotypeShiftScale)
            .roundToInt()
            .coerceIn(minShiftMinutes, maxShiftMinutes)

        val amplitudeScale = (
            1f -
                (sleepDebtHours * 0.06f) -
                (pressureRatio * 0.1f) -
                (wakeVarianceRatio * 0.08f)
            ).times(chronotypeAmplitudeScale)
            .coerceIn(0.6f, 1f)

        val adaptiveStartOffsets = adaptiveWindowStartOffsets(
            baseWindows = base.windows,
            baselineAnchorMinute = baselineAnchor,
            phaseShiftMinutes = phaseShiftMinutes,
            signals = signals,
            coldStartDampening = coldStartDampening
        )

        val spacedOffsets = enforceWindowSpacing(adaptiveStartOffsets, minGapMinutes = MIN_WINDOW_GAP_MINUTES)

        return CircadianProfile(
            windows = base.windows.mapIndexed { index, window ->
                val relativeWeight = when (index) {
                    0 -> 1f
                    1 -> 0.9f
                    else -> 0.8f
                }
                window.copy(
                    startMinuteOffset = spacedOffsets.getOrNull(index) ?: normalizeMinuteOfDay(window.startMinuteOffset),
                    amplitude = (window.amplitude * amplitudeScale * relativeWeight).coerceIn(0.35f, 1f)
                )
            },
            phaseShiftMinutes = phaseShiftMinutes
        )
    }

    private fun adaptiveWindowStartOffsets(
        baseWindows: List<PeakWindow>,
        baselineAnchorMinute: Int,
        phaseShiftMinutes: Int,
        signals: MorningPersonalizationSignals?,
        coldStartDampening: Float
    ): List<Int> {
        if (baseWindows.isEmpty()) return emptyList()
        val slots = signals?.behaviorSlots.orEmpty()
        if (slots.isEmpty()) {
            return baseWindows.map { normalizeMinuteOfDay(it.startMinuteOffset) }
        }

        return baseWindows.mapIndexed { index, window ->
            val expectedStartMinute = normalizeMinuteOfDay(
                baselineAnchorMinute + phaseShiftMinutes + window.startMinuteOffset
            )
            val searchRadius = when (index) {
                0 -> 120
                1 -> 180
                else -> 210
            }
            val maxShift = when (index) {
                0 -> 45
                1 -> 95
                else -> 120
            }
            val windowAdaptScale = when (index) {
                0 -> 0.45f
                1 -> 0.62f
                else -> 0.72f
            }

            val weightedShift = weightedSlotShiftMinutes(
                slots = slots,
                targetMinute = expectedStartMinute,
                searchRadiusMinutes = searchRadius
            )

            val shifted = (weightedShift * coldStartDampening * windowAdaptScale)
                .roundToInt()
                .coerceIn(-maxShift, maxShift)

            normalizeMinuteOfDay(window.startMinuteOffset + shifted)
        }
    }

    private fun weightedSlotShiftMinutes(
        slots: List<SlotPerformanceAggregate>,
        targetMinute: Int,
        searchRadiusMinutes: Int
    ): Float {
        if (slots.isEmpty()) return 0f
        var weightedDeltaSum = 0f
        var totalWeight = 0f

        slots.forEach { slot ->
            val delta = minuteDelta(slot.bucketStartMinute, targetMinute)
            val absDelta = abs(delta)
            if (absDelta <= searchRadiusMinutes) {
                val quality = (
                    slot.qualityWeightedCompletionRate * 0.6f +
                        (1f - slot.abortRate) * 0.25f +
                        (1f - slot.distractionRate) * 0.15f
                    ).coerceIn(0f, 1f)
                val closeness = (1f - (absDelta.toFloat() / searchRadiusMinutes.toFloat())).coerceIn(0f, 1f)
                val sampleStrength = (slot.sampleCount.toFloat() / 12f).coerceIn(0.15f, 1.25f)
                val weight = quality * closeness * sampleStrength
                if (weight > 0f) {
                    weightedDeltaSum += delta * weight
                    totalWeight += weight
                }
            }
        }

        if (totalWeight < 0.35f) return 0f
        return (weightedDeltaSum / totalWeight).coerceIn(-180f, 180f)
    }

    private fun enforceWindowSpacing(offsets: List<Int>, minGapMinutes: Int): List<Int> {
        if (offsets.isEmpty()) return offsets
        val adjusted = mutableListOf<Int>()
        var prevUnwrapped = offsets.first()
        adjusted += normalizeMinuteOfDay(prevUnwrapped)

        for (i in 1 until offsets.size) {
            var candidate = offsets[i]
            while (candidate <= prevUnwrapped) {
                candidate += MINUTES_PER_DAY
            }
            if (candidate - prevUnwrapped < minGapMinutes) {
                candidate = prevUnwrapped + minGapMinutes
            }
            adjusted += normalizeMinuteOfDay(candidate)
            prevUnwrapped = candidate
        }
        return adjusted
    }

    private fun confidenceComponents(
        baseConfidence: Float,
        chronotype: MEQChronotypeDetector.Chronotype,
        wakeUpHour: Int,
        sleepHour: Int?,
        sleepPressurePoints: Int,
        signals: MorningPersonalizationSignals?
    ): ConfidenceComponents {
        val normalizedBase = baseConfidence.coerceIn(0f, 1f)
        // Phase 1 generalization: all chronotypes now benefit from sleep, wake, and behavioral confidence tuning
        // (previously only morning types received adaptive confidence; evening and intermediate received only base confidence)

        // Phase 3 uncertainty calibration: confidence reflects reliability, not just data presence.
        val uncertaintyCalibratedBase = calibrateConfidenceForUncertainty(
            baseConfidence = normalizedBase,
            wakeVarianceMinutes = signals?.wakeVarianceMinutes ?: 180,
            behaviorCoverage = signals?.behaviorCoverage ?: 0f,
            predictedDivergence = signals?.predictedVsObservedDivergence ?: 0f,
            coldStartFactor = signals?.coldStartFactor ?: 0.7f
        )

        val sleepDurationHours = sleepHour?.let {
            val wake = normalizeHour(wakeUpHour)
            val sleep = normalizeHour(it)
            (((wake - sleep + 24) % 24).toFloat()).coerceIn(3f, 12f)
        } ?: 8f

        val durationPenalty = when {
            sleepDurationHours in 7f..9f -> 0f
            sleepDurationHours in 6f..10f -> 0.07f
            else -> 0.14f
        }
        val pressurePenalty = ((sleepPressurePoints.coerceAtLeast(0).toFloat() / SOFT_MAX_SLEEP_PRESSURE) * 0.12f)
            .coerceIn(0f, 0.12f)
        val sleepCoverageScore = ((signals?.sleepLogCoverage ?: 0f) * 0.65f + (1f - durationPenalty) * 0.35f)
            .coerceIn(0f, 1f)
        val wakeConsistencyScore = (1f - ((signals?.wakeVarianceMinutes ?: 180).coerceAtLeast(0).toFloat() / 180f))
            .coerceIn(0f, 1f)
        val behaviorPerformanceScore = behaviorConfidence(signals)

        val tuning = signals?.tuning ?: TuningCoefficients()
        val normalizedTuning = normalizeTuning(tuning)
        val overall = if (signals?.confidenceGatedAbstention == true) {
            (
                sleepCoverageScore * 0.34f +
                    wakeConsistencyScore * 0.33f +
                    behaviorPerformanceScore * 0.33f -
                    pressurePenalty * 0.5f
                ).coerceIn(0.25f, 0.6f)
                .coerceAtMost(0.4f)
        } else {
            (
                uncertaintyCalibratedBase * normalizedTuning.baseWeight +
                    sleepCoverageScore * normalizedTuning.sleepWeight +
                    wakeConsistencyScore * normalizedTuning.wakeWeight +
                    behaviorPerformanceScore * normalizedTuning.behaviorWeight -
                    pressurePenalty
                ).coerceIn(0.45f, 1f)
        }

        return ConfidenceComponents(
            sleepCoverage = sleepCoverageScore,
            wakeConsistency = wakeConsistencyScore,
            behaviorPerformance = behaviorPerformanceScore,
            overall = overall
        )
    }

    private fun behaviorConfidence(signals: MorningPersonalizationSignals?): Float {
        if (signals == null) return 0.5f
        val coverage = signals.behaviorCoverage.coerceIn(0f, 1f)
        if (signals.behaviorSlots.isEmpty()) return (0.35f + coverage * 0.35f).coerceIn(0f, 1f)
        val qualityAvg = signals.behaviorSlots
            .map { slot ->
                (slot.qualityWeightedCompletionRate * 0.6f) +
                    ((1f - slot.abortRate) * 0.2f) +
                    ((1f - slot.distractionRate) * 0.2f)
            }
            .average()
            .toFloat()
            .coerceIn(0f, 1f)
        return (qualityAvg * 0.75f + coverage * 0.25f).coerceIn(0f, 1f)
    }

    private fun behaviorDrivenShiftMinutes(
        slots: List<SlotPerformanceAggregate>,
        baselineAnchorMinute: Int
    ): Float {
        if (slots.isEmpty()) return 0f
        val topSlot = slots.maxByOrNull {
            (it.qualityWeightedCompletionRate * 0.6f) +
                ((1f - it.abortRate) * 0.25f) +
                ((1f - it.distractionRate) * 0.15f)
        } ?: return 0f
        val delta = minuteDelta(topSlot.bucketStartMinute, baselineAnchorMinute)
        return (delta.toFloat() * 0.35f).coerceIn(-40f, 50f)
    }

    private fun taskTypeShiftMinutes(
        perf: List<TaskTypePerformanceAggregate>,
        baselineAnchorMinute: Int
    ): Float {
        if (perf.isEmpty()) return 0f
        val weighted = perf.sumOf {
            val weight = it.weightedQuality.coerceIn(0f, 1f)
            minuteDelta(it.averageBestBucketMinute, baselineAnchorMinute).toDouble() * weight
        }
        val totalWeight = perf.sumOf { it.weightedQuality.coerceIn(0f, 1f).toDouble() }.coerceAtLeast(1e-6)
        return ((weighted / totalWeight).toFloat() * 0.18f).coerceIn(-25f, 25f)
    }

    private fun normalizeTuning(input: TuningCoefficients): TuningCoefficients {
        val sleep = input.sleepWeight.coerceIn(0.1f, 0.6f)
        val wake = input.wakeWeight.coerceIn(0.1f, 0.5f)
        val behavior = input.behaviorWeight.coerceIn(0.1f, 0.6f)
        val base = input.baseWeight.coerceIn(0.05f, 0.5f)
        val total = (sleep + wake + behavior + base).coerceAtLeast(1e-6f)
        return TuningCoefficients(
            sleepWeight = sleep / total,
            wakeWeight = wake / total,
            behaviorWeight = behavior / total,
            baseWeight = base / total
        )
    }

    /**
     * Phase 3: Determine if adaptive tuning should be triggered based on prediction-vs-observed divergence.
     * Replaces fixed 7-day cadence with divergence-based adaptive triggers.
     *
     * Tuning is triggered when:
     * - Accumulated divergence exceeds threshold (0.25 = 25% mean absolute error)
     * - AND sample count since last tuning exceeds minimum (50 predictions)
     * - OR time since last tuning exceeds maximum (7 days fallback)
     */
    fun shouldTriggerAdaptiveTuning(
        divergence: Float,
        sampleCount: Int,
        lastTuningMillis: Long,
        nowMillis: Long
    ): Boolean {
        val divergeThreshold = 0.25f
        val minSampleCount = 50
        val maxTuningIntervalMillis = 7 * 24 * 60 * 60 * 1000L  // 7 days fallback

        val timeSinceLastTuning = if (lastTuningMillis > 0L) {
            nowMillis - lastTuningMillis
        } else {
            maxTuningIntervalMillis  // First tuning
        }

        return (divergence >= divergeThreshold && sampleCount >= minSampleCount) ||
                (timeSinceLastTuning >= maxTuningIntervalMillis)
    }

    /**
     * Phase 3: Uncertainty-aware confidence calibration.
     * Adjusts confidence to reflect reliability (data quality + consistency) rather than just data presence.
     *
     * Penalties applied for:
     * - High variance in wake times (unreliable schedule)
     * - Low behavioral coverage (sparse observations)
     * - Predictive divergence (model-reality mismatch)
     * - Cold start conditions (insufficient history)
     */
    fun calibrateConfidenceForUncertainty(
        baseConfidence: Float,
        wakeVarianceMinutes: Int,
        behaviorCoverage: Float,
        predictedDivergence: Float,
        coldStartFactor: Float
    ): Float {
        var confidence = baseConfidence.coerceIn(0f, 1f)

        // Variance penalty: high variance = unreliable predictions
        val variancePenalty = (wakeVarianceMinutes.coerceAtLeast(0).toFloat() / 180f)
            .coerceIn(0f, 1f)
            .let { it * it }  // Quadratic scaling: small variance has minimal impact
            .times(0.15f)  // Max 15% penalty

        // Coverage penalty: sparse behavior data = low confidence
        val coveragePenalty = (1f - behaviorCoverage.coerceIn(0f, 1f)) * 0.10f  // Max 10% penalty

        // Divergence penalty: model-reality mismatch = uncertain
        val divergencePenalty = predictedDivergence.coerceIn(0f, 1f) * 0.20f  // Max 20% penalty

        // Cold start factor: insufficient history = reduced certainty
        confidence *= coldStartFactor.coerceIn(0.5f, 1f)

        // Apply all penalties
        confidence = (confidence - variancePenalty - coveragePenalty - divergencePenalty)
            .coerceIn(0.25f, 1f)  // Floor at 0.25 (never fully unknowable)

        return confidence
    }

    private fun buildEffectiveProfile(
        chronotype: MEQChronotypeDetector.Chronotype,
        peakMinuteOfDay: Int,
        profile: CircadianProfile,
        confidence: ConfidenceComponents,
        signals: MorningPersonalizationSignals?
    ): EffectivePeakProfile {
        val profileOverride = signals?.profileOverride
        val profileType = profileOverride?.profileType ?: signals?.profileType ?: ProfileType.WORKDAY
        val explanation = if (profileOverride?.enabled == true) {
            "Manual profile override active"
        } else if (signals?.confidenceGatedAbstention == true) {
            val reason = signals.abstentionReason.ifBlank { "insufficient confidence for personalized prediction" }
            "Confidence-gated abstention active: $reason"
        } else if (signals?.adaptiveFreezeMode == true) {
            "Adaptive profile freeze active: falling back closer to baseline until quality recovers"
        } else {
            // Phase 1 generalization: all chronotypes (morning, intermediate, evening) now have adaptive explanation
            val components = listOf(
                "sleep ${(confidence.sleepCoverage * 100).toInt()}%",
                "wake ${(confidence.wakeConsistency * 100).toInt()}%",
                "behavior ${(confidence.behaviorPerformance * 100).toInt()}%"
            )
            "Adaptive profile (${profileType.name.lowercase()}): ${components.joinToString(", ")}"
        }
        val driftStatus = when {
            (signals?.driftMinutes ?: 0) >= 18 -> "later_drift"
            (signals?.driftMinutes ?: 0) <= -18 -> "earlier_drift"
            else -> "stable"
        }
        return EffectivePeakProfile(
            profileType = profileType,
            anchorMinuteOfDay = normalizeMinuteOfDay(peakMinuteOfDay),
            windows = profile.windows,
            confidence = confidence,
            windowConfidences = buildWindowConfidences(
                windows = profile.windows,
                confidence = confidence,
                weeklyBacktestErrorMinutes = signals?.weeklyBacktestErrorMinutes,
                driftMinutes = signals?.driftMinutes ?: 0
            ),
            explanation = explanation,
            driftStatus = driftStatus,
            weeklyBacktestErrorMinutes = signals?.weeklyBacktestErrorMinutes,
            adaptiveFreezeMode = signals?.adaptiveFreezeMode == true,
            confidenceGatedAbstention = signals?.confidenceGatedAbstention == true,
            abstentionReason = signals?.abstentionReason.orEmpty()
        )
    }

    /**
     * Derives reliability per circadian window.
     *
     * Peak 2 and Peak 3 rely more on wake consistency and behavior alignment than
     * Peak 1, because they are further from the anchor and more sensitive to drift.
     */
    private fun buildWindowConfidences(
        windows: List<PeakWindow>,
        confidence: ConfidenceComponents,
        weeklyBacktestErrorMinutes: Float?,
        driftMinutes: Int
    ): List<Float> {
        val driftRatio = (kotlin.math.abs(driftMinutes).toFloat() / 60f).coerceIn(0f, 1f)
        val backtestPenalty = when {
            weeklyBacktestErrorMinutes == null -> 0.05f
            weeklyBacktestErrorMinutes <= 30f -> 0f
            weeklyBacktestErrorMinutes <= 75f -> 0.08f
            else -> 0.14f
        }

        return windows.mapIndexed { index, window ->
            val componentBlend = when (index) {
                0 -> {
                    confidence.sleepCoverage * 0.50f +
                        confidence.wakeConsistency * 0.30f +
                        confidence.behaviorPerformance * 0.20f
                }
                1 -> {
                    confidence.sleepCoverage * 0.25f +
                        confidence.wakeConsistency * 0.35f +
                        confidence.behaviorPerformance * 0.40f
                }
                else -> {
                    confidence.sleepCoverage * 0.20f +
                        confidence.wakeConsistency * 0.30f +
                        confidence.behaviorPerformance * 0.50f
                }
            }.coerceIn(0f, 1f)

            val amplitudeFactor = (0.75f + window.amplitude.coerceIn(0.35f, 1f) * 0.25f)
                .coerceIn(0.7f, 1f)
            val lateWindowPenalty = when (index) {
                0 -> 0f
                1 -> 0.05f + driftRatio * 0.05f
                else -> 0.10f + driftRatio * 0.08f
            }

            val blended = (componentBlend * 0.75f + confidence.overall * 0.25f)
                .coerceIn(0f, 1f)

            (blended * amplitudeFactor - lateWindowPenalty - backtestPenalty)
                .coerceIn(0.25f, 1f)
        }
    }

    fun bucketMinuteOfDay(timestampMillis: Long): Int {
        val minute = minuteOfDay(timestampMillis)
        return (minute / SLOT_BUCKET_MINUTES) * SLOT_BUCKET_MINUTES
    }

    private fun normalizeMinuteOfDay(minute: Int): Int {
        val normalized = minute % MINUTES_PER_DAY
        return if (normalized < 0) normalized + MINUTES_PER_DAY else normalized
    }

    private fun minuteDelta(targetMinute: Int, referenceMinute: Int): Int {
        val t = normalizeMinuteOfDay(targetMinute)
        val r = normalizeMinuteOfDay(referenceMinute)
        val forward = (t - r + MINUTES_PER_DAY) % MINUTES_PER_DAY
        val backward = forward - MINUTES_PER_DAY
        return if (abs(forward) <= abs(backward)) forward else backward
    }

    private fun minuteOfDay(nowMillis: Long): Int {
        val calendar = Calendar.getInstance().apply { timeInMillis = nowMillis }
        return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
    }

    private fun normalizeHour(hour: Int): Int {
        val normalized = hour % 24
        return if (normalized < 0) normalized + 24 else normalized
    }
}
