package com.neuroflow.app.domain.repository

import com.neuroflow.app.data.local.UserPreferencesDataStore
import com.neuroflow.app.data.local.entity.SleepLogEntity
import com.neuroflow.app.data.local.entity.TaskEntity
import com.neuroflow.app.data.local.entity.TimeSessionEntity
import com.neuroflow.app.data.repository.SessionRepository
import com.neuroflow.app.data.repository.SleepLogRepository
import com.neuroflow.app.data.repository.TaskRepository
import com.neuroflow.app.domain.engine.MEQChronotypeDetector
import com.neuroflow.app.domain.engine.PeakEnergyEngine
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository that wires PeakEnergyEngine with user preferences.
 * Provides the current peak energy value based on user's chronotype and wake time.
 */
@Singleton
class PeakEnergyRepository @Inject constructor(
    private val preferencesDataStore: UserPreferencesDataStore,
    private val sleepLogRepository: SleepLogRepository,
    private val sessionRepository: SessionRepository,
    private val taskRepository: TaskRepository
) {
    companion object {
        private const val LOOKBACK_DAYS = 14
        private const val RECENCY_LAMBDA = 0.18
        private const val QUALITY_MINUTES = 20f
        private const val ABORT_MINUTES = 12f
        private const val COMPLETION_PROXIMITY_MINUTES = 120L
        private const val MAX_DISTRACTION_SCORE = 100f
        private const val FREEZE_DIVERGENCE_THRESHOLD = 0.45f
        private const val FREEZE_MIN_SAMPLES = 60
        private const val FREEZE_STREAK_TRIGGER = 3
        private const val UNFREEZE_DIVERGENCE_THRESHOLD = 0.25f
        private const val UNFREEZE_MIN_SAMPLES = 50
        private const val GATE_MIN_SLEEP_COVERAGE = 0.30f
        private const val GATE_MIN_BEHAVIOR_COVERAGE = 0.25f
        private const val GATE_MIN_SAMPLES = 16
        private const val GATE_MAX_WAKE_VARIANCE_MINUTES = 150
        private const val GATE_MAX_DIVERGENCE = 0.55f
        private const val DAY_MILLIS = 24L * 60L * 60L * 1000L
    }

    @Volatile
    private var freezeGuardBootstrapped: Boolean = false

    @Volatile
    private var adaptiveFreezeState: Boolean = false

    @Volatile
    private var adaptiveFreezeDegradeStreak: Int = 0

    @Volatile
    private var lastFreezeGuardUpdateDay: Long = Long.MIN_VALUE

    @Volatile
    private var abstentionTelemetryBootstrapped: Boolean = false

    @Volatile
    private var confidenceAbstentionState: Boolean = false
    /**
     * Provides a flow of peak energy detection results based on current preferences.
     * Updates whenever user's chronotype or wake time changes.
     */
    val peakEnergyFlow: Flow<PeakEnergyEngine.DetectionResult> = preferencesDataStore.preferencesFlow.map { prefs ->
        buildDetectionFromPreferences(
            quizPeakEnabled = prefs.quizPeakEnabled,
            quizChronotype = prefs.quizChronotype,
            manualChronotype = prefs.manualChronotype,
            wakeUpHour = prefs.wakeUpHour,
            sleepHour = prefs.sleepHour,
            sleepPressurePoints = prefs.sleepPressurePoints,
            manualPeakStart = prefs.peakEnergyStart,
            manualPeakEnd = prefs.peakEnergyEnd,
            profileOverrideEnabled = prefs.manualPeakProfileEnabled,
            profileOverrideType = prefs.manualPeakProfileType,
            profileOverrideAnchorMinute = prefs.manualPeakAnchorMinuteOfDay,
            manualWindow1StartOffsetMinutes = prefs.manualPeakWindow1StartOffsetMinutes,
            manualWindow2StartOffsetMinutes = prefs.manualPeakWindow2StartOffsetMinutes,
            manualWindow3StartOffsetMinutes = prefs.manualPeakWindow3StartOffsetMinutes,
            manualWindow1DurationMinutes = prefs.manualPeakWindow1DurationMinutes,
            manualWindow2DurationMinutes = prefs.manualPeakWindow2DurationMinutes,
            manualWindow3DurationMinutes = prefs.manualPeakWindow3DurationMinutes,
            manualWindow1Amplitude = prefs.manualPeakWindow1Amplitude,
            manualWindow2Amplitude = prefs.manualPeakWindow2Amplitude,
            manualWindow3Amplitude = prefs.manualPeakWindow3Amplitude
        )
    }
    
    /**
     * Get the peak energy result with all metadata (peak time, decay value, etc.)
     */
    suspend fun getPeakEnergyDetection(): PeakEnergyEngine.DetectionResult {
        val prefs = preferencesDataStore.preferencesFlow.first()
        return buildDetectionFromPreferences(
            quizPeakEnabled = prefs.quizPeakEnabled,
            quizChronotype = prefs.quizChronotype,
            manualChronotype = prefs.manualChronotype,
            wakeUpHour = prefs.wakeUpHour,
            sleepHour = prefs.sleepHour,
            sleepPressurePoints = prefs.sleepPressurePoints,
            manualPeakStart = prefs.peakEnergyStart,
            manualPeakEnd = prefs.peakEnergyEnd,
            profileOverrideEnabled = prefs.manualPeakProfileEnabled,
            profileOverrideType = prefs.manualPeakProfileType,
            profileOverrideAnchorMinute = prefs.manualPeakAnchorMinuteOfDay,
            manualWindow1StartOffsetMinutes = prefs.manualPeakWindow1StartOffsetMinutes,
            manualWindow2StartOffsetMinutes = prefs.manualPeakWindow2StartOffsetMinutes,
            manualWindow3StartOffsetMinutes = prefs.manualPeakWindow3StartOffsetMinutes,
            manualWindow1DurationMinutes = prefs.manualPeakWindow1DurationMinutes,
            manualWindow2DurationMinutes = prefs.manualPeakWindow2DurationMinutes,
            manualWindow3DurationMinutes = prefs.manualPeakWindow3DurationMinutes,
            manualWindow1Amplitude = prefs.manualPeakWindow1Amplitude,
            manualWindow2Amplitude = prefs.manualPeakWindow2Amplitude,
            manualWindow3Amplitude = prefs.manualPeakWindow3Amplitude
        )
    }

    private suspend fun buildDetectionFromPreferences(
        quizPeakEnabled: Boolean,
        quizChronotype: String?,
        manualChronotype: String?,
        wakeUpHour: Int,
        sleepHour: Int,
        sleepPressurePoints: Int,
        manualPeakStart: Int,
        manualPeakEnd: Int,
        profileOverrideEnabled: Boolean,
        profileOverrideType: String,
        profileOverrideAnchorMinute: Int,
        manualWindow1StartOffsetMinutes: Int,
        manualWindow2StartOffsetMinutes: Int,
        manualWindow3StartOffsetMinutes: Int,
        manualWindow1DurationMinutes: Int,
        manualWindow2DurationMinutes: Int,
        manualWindow3DurationMinutes: Int,
        manualWindow1Amplitude: Float,
        manualWindow2Amplitude: Float,
        manualWindow3Amplitude: Float
    ): PeakEnergyEngine.DetectionResult {
        val parsedQuizChronotype = parseChronotype(quizChronotype)
        val profileOverride = buildProfileOverride(
            enabled = profileOverrideEnabled,
            typeRaw = profileOverrideType,
            anchorMinute = profileOverrideAnchorMinute,
            w1StartOffset = manualWindow1StartOffsetMinutes,
            w2StartOffset = manualWindow2StartOffsetMinutes,
            w3StartOffset = manualWindow3StartOffsetMinutes,
            w1Duration = manualWindow1DurationMinutes,
            w2Duration = manualWindow2DurationMinutes,
            w3Duration = manualWindow3DurationMinutes,
            w1Amplitude = manualWindow1Amplitude,
            w2Amplitude = manualWindow2Amplitude,
            w3Amplitude = manualWindow3Amplitude
        )
        if (quizPeakEnabled && parsedQuizChronotype != null) {
            val qualityGuard = evaluateAdaptiveFreezeGuard()
            val profileType = currentProfileType()
            var personalizationSignals = loadMorningPersonalizationSignals(
                profileType = profileType,
                wakeUpHour = wakeUpHour,
                chronotype = parsedQuizChronotype,
                profileOverride = profileOverride,
                freezeAdaptiveMode = qualityGuard.freezeEnabled
            )
            val tuningUpdated = if (personalizationSignals.confidenceGatedAbstention) {
                false
            } else {
                maybeAutoTuneMorningWeights(
                    divergence = personalizationSignals.predictedVsObservedDivergence,
                    sampleCount = personalizationSignals.sampleCountSinceLastTuning,
                    lastTuningMillis = personalizationSignals.lastTuningUpdatedAtMillis,
                    shouldTriggerAdaptiveTuning = personalizationSignals.shouldTriggerAdaptiveTuning
                )
            }
            if (tuningUpdated) {
                // Rebuild signals so detection uses the newly tuned coefficients immediately.
                personalizationSignals = loadMorningPersonalizationSignals(
                    profileType = profileType,
                    wakeUpHour = wakeUpHour,
                    chronotype = parsedQuizChronotype,
                    profileOverride = profileOverride,
                    freezeAdaptiveMode = qualityGuard.freezeEnabled
                )
            }
            syncAbstentionTelemetry(
                abstain = personalizationSignals.confidenceGatedAbstention,
                reason = personalizationSignals.abstentionReason,
                reasonCategory = classifyAbstentionReason(personalizationSignals.abstentionReason)
            )
            val meqResult = toMeqResult(parsedQuizChronotype)
            return PeakEnergyEngine.detect(
                meqResult = meqResult,
                wakeUpHour = wakeUpHour,
                sleepHour = sleepHour,
                sleepPressurePoints = sleepPressurePoints,
                personalizationSignals = personalizationSignals
            )
        }

        val fallbackChronotype = parseChronotype(manualChronotype)
            ?: parsedQuizChronotype
            ?: MEQChronotypeDetector.Chronotype.INTERMEDIATE

        val peakMinuteOfDay = midpointMinuteOfDay(manualPeakStart, manualPeakEnd)
        val safeWakeHour = normalizeHour(wakeUpHour)
        val offsetMinutes = minutesDifferenceWrapped(startMinute = safeWakeHour * 60, endMinute = peakMinuteOfDay)
        syncAbstentionTelemetry(abstain = false, reason = "", reasonCategory = null)

        return PeakEnergyEngine.DetectionResult(
            chronotype = fallbackChronotype,
            wakeUpHour = safeWakeHour,
            peakOffsetHours = offsetMinutes / 60f,
            peakHourOfDay = peakMinuteOfDay / 60,
            peakMinuteOfDay = peakMinuteOfDay,
            peakValue = PeakEnergyEngine.PEAK_VALUE,
            confidence = 1f,
            circadianProfile = PeakEnergyEngine.defaultCircadianProfile(fallbackChronotype),
            detectedAtMillis = System.currentTimeMillis()
        )
    }

    private suspend fun loadMorningPersonalizationSignals(
        profileType: PeakEnergyEngine.ProfileType,
        wakeUpHour: Int,
        chronotype: MEQChronotypeDetector.Chronotype,
        profileOverride: PeakEnergyEngine.ProfileOverride?,
        freezeAdaptiveMode: Boolean,
        lookbackDays: Int = LOOKBACK_DAYS
    ): PeakEnergyEngine.MorningPersonalizationSignals {
        val allLogs = sleepLogRepository.getAll()
        val allSessions = sessionRepository.getAllSessions()
        val allTasks = taskRepository.getAllTasks()
        if (allLogs.isEmpty() && allSessions.isEmpty()) {
            return PeakEnergyEngine.MorningPersonalizationSignals(
                profileType = profileType,
                profileOverride = profileOverride,
                adaptiveFreezeMode = freezeAdaptiveMode,
                confidenceGatedAbstention = true,
                abstentionReason = "not enough sleep and focus history yet"
            )
        }

        val cutoffMillis = System.currentTimeMillis() - (lookbackDays * 24L * 60L * 60L * 1000L)
        val recentLogs = allLogs
            .asSequence()
            .filter { it.endAt >= cutoffMillis }
            .filter { profileTypeForMillis(it.endAt) == profileType }
            .sortedByDescending { it.startAt }
            .take(lookbackDays)
            .toList()

        val averageSleepMinutes = recentLogs
            .takeIf { it.isNotEmpty() }
            ?.map { it.durationMinutes.coerceIn(60, 16 * 60) }
            ?.average()
            ?.roundToInt()

        val wakeVarianceMinutes = circularWakeVarianceMinutes(recentLogs).takeIf { recentLogs.isNotEmpty() }
        val expectedCoverage = lookbackDays.coerceAtLeast(1).toFloat()
        val coverage = (recentLogs.size / expectedCoverage).coerceIn(0f, 1f)
        val behavior = behaviorSignals(
            sessions = allSessions,
            tasks = allTasks,
            cutoffMillis = cutoffMillis,
            profileType = profileType,
            lookbackDays = lookbackDays
        )
        val baselineAnchorMinuteOfDay = (
            normalizeHour(wakeUpHour) * 60 +
                (PeakEnergyEngine.chronotypeOffset(chronotype) * 60f).roundToInt()
            ) % (24 * 60)
        val driftMinutes = driftMinutes(
            slots = behavior.slots,
            baselineAnchorMinute = baselineAnchorMinuteOfDay
        )
        val coldStart = coldStartFactor(behavior.coverage, coverage)
        val weeklyBacktestErrorMinutes = weeklyBacktestError(
            slots = behavior.slots,
            baselineAnchorMinute = baselineAnchorMinuteOfDay
        )
        val divergence = ((weeklyBacktestErrorMinutes ?: 0f) / 180f).coerceIn(0f, 1f)
        val sampleCount = behavior.slots.sumOf { it.sampleCount }
        val confidenceGate = evaluateConfidenceGate(
            freezeAdaptiveMode = freezeAdaptiveMode,
            profileOverride = profileOverride,
            sleepLogCoverage = coverage,
            behaviorCoverage = behavior.coverage,
            wakeVarianceMinutes = wakeVarianceMinutes,
            divergence = divergence,
            sampleCount = sampleCount
        )
        val prefs = preferencesDataStore.preferencesFlow.first()
        val shouldTriggerAdaptiveTuning = !freezeAdaptiveMode && !confidenceGate.abstain && PeakEnergyEngine.shouldTriggerAdaptiveTuning(
            divergence = divergence,
            sampleCount = sampleCount,
            lastTuningMillis = prefs.morningTuneUpdatedAtMillis,
            nowMillis = System.currentTimeMillis()
        )
        val tuning = PeakEnergyEngine.TuningCoefficients(
            sleepWeight = prefs.morningTuneSleepWeight,
            wakeWeight = prefs.morningTuneWakeWeight,
            behaviorWeight = prefs.morningTuneBehaviorWeight,
            baseWeight = prefs.morningTuneBaseWeight
        )

        return PeakEnergyEngine.MorningPersonalizationSignals(
            averageSleepMinutes = averageSleepMinutes,
            wakeVarianceMinutes = wakeVarianceMinutes,
            sleepLogCoverage = coverage,
            profileType = profileType,
            baselineAnchorMinuteOfDay = baselineAnchorMinuteOfDay,
            behaviorSlots = behavior.slots,
            taskTypePerformance = behavior.taskTypePerformance,
            behaviorCoverage = behavior.coverage,
            recentWeight = behavior.recentWeight,
            coldStartFactor = coldStart,
            driftMinutes = driftMinutes,
            weeklyBacktestErrorMinutes = weeklyBacktestErrorMinutes,
            tuning = tuning,
            profileOverride = profileOverride,
            predictedVsObservedDivergence = divergence,
            lastTuningUpdatedAtMillis = prefs.morningTuneUpdatedAtMillis,
            sampleCountSinceLastTuning = sampleCount,
            shouldTriggerAdaptiveTuning = shouldTriggerAdaptiveTuning,
            adaptiveFreezeMode = freezeAdaptiveMode,
            confidenceGatedAbstention = confidenceGate.abstain,
            abstentionReason = confidenceGate.reason
        )
    }

    private enum class AbstentionReasonCategory {
        FREEZE_SAFETY,
        LOW_SAMPLES,
        LOW_COVERAGE,
        HIGH_WAKE_VARIANCE,
        HIGH_DIVERGENCE,
        OTHER
    }

    private data class ConfidenceGateDecision(
        val abstain: Boolean,
        val reason: String,
        val reasonCategory: AbstentionReasonCategory? = null
    )

    private fun evaluateConfidenceGate(
        freezeAdaptiveMode: Boolean,
        profileOverride: PeakEnergyEngine.ProfileOverride?,
        sleepLogCoverage: Float,
        behaviorCoverage: Float,
        wakeVarianceMinutes: Int?,
        divergence: Float,
        sampleCount: Int
    ): ConfidenceGateDecision {
        if (profileOverride?.enabled == true) {
            return ConfidenceGateDecision(abstain = false, reason = "", reasonCategory = null)
        }
        if (freezeAdaptiveMode) {
            return ConfidenceGateDecision(
                abstain = true,
                reason = "adaptive safety freeze is active while quality recovers",
                reasonCategory = AbstentionReasonCategory.FREEZE_SAFETY
            )
        }
        if (sampleCount < GATE_MIN_SAMPLES) {
            return ConfidenceGateDecision(
                abstain = true,
                reason = "insufficient focus-session samples for reliable personalization",
                reasonCategory = AbstentionReasonCategory.LOW_SAMPLES
            )
        }
        if (sleepLogCoverage < GATE_MIN_SLEEP_COVERAGE && behaviorCoverage < GATE_MIN_BEHAVIOR_COVERAGE) {
            return ConfidenceGateDecision(
                abstain = true,
                reason = "sleep and behavior coverage are both below reliability threshold",
                reasonCategory = AbstentionReasonCategory.LOW_COVERAGE
            )
        }
        if ((wakeVarianceMinutes ?: 0) > GATE_MAX_WAKE_VARIANCE_MINUTES) {
            return ConfidenceGateDecision(
                abstain = true,
                reason = "wake time is too inconsistent for stable peak inference",
                reasonCategory = AbstentionReasonCategory.HIGH_WAKE_VARIANCE
            )
        }
        if (divergence > GATE_MAX_DIVERGENCE) {
            return ConfidenceGateDecision(
                abstain = true,
                reason = "recent predicted-vs-observed drift is above safe limit",
                reasonCategory = AbstentionReasonCategory.HIGH_DIVERGENCE
            )
        }
        return ConfidenceGateDecision(abstain = false, reason = "", reasonCategory = null)
    }

    private fun classifyAbstentionReason(reason: String): AbstentionReasonCategory {
        val normalized = reason.lowercase()
        return when {
            "freeze" in normalized -> AbstentionReasonCategory.FREEZE_SAFETY
            "samples" in normalized -> AbstentionReasonCategory.LOW_SAMPLES
            "coverage" in normalized -> AbstentionReasonCategory.LOW_COVERAGE
            "wake" in normalized -> AbstentionReasonCategory.HIGH_WAKE_VARIANCE
            "drift" in normalized || "divergence" in normalized -> AbstentionReasonCategory.HIGH_DIVERGENCE
            else -> AbstentionReasonCategory.OTHER
        }
    }

    private suspend fun syncAbstentionTelemetry(
        abstain: Boolean,
        reason: String,
        reasonCategory: AbstentionReasonCategory?
    ) {
        val prefs = preferencesDataStore.preferencesFlow.first()
        if (!abstentionTelemetryBootstrapped) {
            confidenceAbstentionState = prefs.peakConfidenceAbstentionEnabled
            abstentionTelemetryBootstrapped = true
        }

        val normalizedReason = if (abstain) {
            reason.ifBlank { "insufficient confidence for personalized prediction" }
        } else {
            ""
        }

        val transitionToAbstention = abstain && !confidenceAbstentionState
        val transitionToRecovery = !abstain && confidenceAbstentionState
        val reasonChanged = prefs.peakConfidenceAbstentionReason != normalizedReason
        val stateChanged = prefs.peakConfidenceAbstentionEnabled != abstain

        if (!transitionToAbstention && !transitionToRecovery && !reasonChanged && !stateChanged) {
            confidenceAbstentionState = abstain
            return
        }

        val now = System.currentTimeMillis()
        preferencesDataStore.updatePreferences {
            it.copy(
                peakConfidenceAbstentionEnabled = abstain,
                peakConfidenceAbstentionReason = normalizedReason,
                peakConfidenceAbstentionTriggerCount = if (transitionToAbstention) {
                    it.peakConfidenceAbstentionTriggerCount + 1
                } else {
                    it.peakConfidenceAbstentionTriggerCount
                },
                peakConfidenceAbstentionRecoveryCount = if (transitionToRecovery) {
                    it.peakConfidenceAbstentionRecoveryCount + 1
                } else {
                    it.peakConfidenceAbstentionRecoveryCount
                },
                peakConfidenceAbstentionReasonFreezeCount = if (
                    transitionToAbstention && reasonCategory == AbstentionReasonCategory.FREEZE_SAFETY
                ) {
                    it.peakConfidenceAbstentionReasonFreezeCount + 1
                } else {
                    it.peakConfidenceAbstentionReasonFreezeCount
                },
                peakConfidenceAbstentionReasonLowSamplesCount = if (
                    transitionToAbstention && reasonCategory == AbstentionReasonCategory.LOW_SAMPLES
                ) {
                    it.peakConfidenceAbstentionReasonLowSamplesCount + 1
                } else {
                    it.peakConfidenceAbstentionReasonLowSamplesCount
                },
                peakConfidenceAbstentionReasonLowCoverageCount = if (
                    transitionToAbstention && reasonCategory == AbstentionReasonCategory.LOW_COVERAGE
                ) {
                    it.peakConfidenceAbstentionReasonLowCoverageCount + 1
                } else {
                    it.peakConfidenceAbstentionReasonLowCoverageCount
                },
                peakConfidenceAbstentionReasonWakeVarianceCount = if (
                    transitionToAbstention && reasonCategory == AbstentionReasonCategory.HIGH_WAKE_VARIANCE
                ) {
                    it.peakConfidenceAbstentionReasonWakeVarianceCount + 1
                } else {
                    it.peakConfidenceAbstentionReasonWakeVarianceCount
                },
                peakConfidenceAbstentionReasonDivergenceCount = if (
                    transitionToAbstention && reasonCategory == AbstentionReasonCategory.HIGH_DIVERGENCE
                ) {
                    it.peakConfidenceAbstentionReasonDivergenceCount + 1
                } else {
                    it.peakConfidenceAbstentionReasonDivergenceCount
                },
                peakConfidenceAbstentionReasonOtherCount = if (
                    transitionToAbstention &&
                        (reasonCategory == null || reasonCategory == AbstentionReasonCategory.OTHER)
                ) {
                    it.peakConfidenceAbstentionReasonOtherCount + 1
                } else {
                    it.peakConfidenceAbstentionReasonOtherCount
                },
                peakConfidenceAbstentionLastChangedAtMillis = if (
                    transitionToAbstention || transitionToRecovery || reasonChanged || stateChanged
                ) {
                    now
                } else {
                    it.peakConfidenceAbstentionLastChangedAtMillis
                }
            )
        }

        confidenceAbstentionState = abstain
    }

    private data class AdaptiveFreezeGuard(
        val freezeEnabled: Boolean,
        val degradeStreak: Int
    )

    private suspend fun evaluateAdaptiveFreezeGuard(): AdaptiveFreezeGuard {
        val prefs = preferencesDataStore.preferencesFlow.first()
        if (!freezeGuardBootstrapped) {
            adaptiveFreezeState = prefs.adaptivePeakFreezeEnabled
            adaptiveFreezeDegradeStreak = prefs.peakQualityDegradeStreak.coerceAtLeast(0)
            freezeGuardBootstrapped = true
        }
        val sessions = sessionRepository.getAllSessions()
        val tasks = taskRepository.getAllTasks()
        val nowMillis = System.currentTimeMillis()
        val cutoffMillis = nowMillis - (LOOKBACK_DAYS * 24L * 60L * 60L * 1000L)
        val profileType = currentProfileType()
        val behavior = behaviorSignals(
            sessions = sessions,
            tasks = tasks,
            cutoffMillis = cutoffMillis,
            profileType = profileType,
            lookbackDays = LOOKBACK_DAYS
        )

        val baselineAnchorMinute = ((normalizeHour(prefs.wakeUpHour) * 60) +
            (PeakEnergyEngine.chronotypeOffset(
                parseChronotype(prefs.quizChronotype ?: prefs.manualChronotype)
                    ?: MEQChronotypeDetector.Chronotype.INTERMEDIATE
            ) * 60f).roundToInt()) % (24 * 60)
        val weeklyError = weeklyBacktestError(behavior.slots, baselineAnchorMinute)
        val divergence = ((weeklyError ?: 0f) / 180f).coerceIn(0f, 1f)
        val sampleCount = behavior.slots.sumOf { it.sampleCount }

        val qualityBad = divergence >= FREEZE_DIVERGENCE_THRESHOLD && sampleCount >= FREEZE_MIN_SAMPLES
        val qualityRecovered = divergence <= UNFREEZE_DIVERGENCE_THRESHOLD && sampleCount >= UNFREEZE_MIN_SAMPLES

        val dayIndex = nowMillis / DAY_MILLIS
        var stateChanged = false

        if (dayIndex != lastFreezeGuardUpdateDay) {
            when {
                qualityBad -> {
                    adaptiveFreezeDegradeStreak += 1
                    stateChanged = true
                }
                qualityRecovered -> {
                    if (adaptiveFreezeDegradeStreak != 0) stateChanged = true
                    adaptiveFreezeDegradeStreak = 0
                }
            }

            val newFreeze = if (qualityRecovered) {
                false
            } else {
                adaptiveFreezeState || adaptiveFreezeDegradeStreak >= FREEZE_STREAK_TRIGGER
            }
            if (newFreeze != adaptiveFreezeState) {
                adaptiveFreezeState = newFreeze
                stateChanged = true
            }

            lastFreezeGuardUpdateDay = dayIndex
        }

        if (qualityRecovered && adaptiveFreezeState) {
            adaptiveFreezeState = false
            adaptiveFreezeDegradeStreak = 0
            stateChanged = true
        }

        if (stateChanged &&
            (adaptiveFreezeState != prefs.adaptivePeakFreezeEnabled ||
                adaptiveFreezeDegradeStreak != prefs.peakQualityDegradeStreak)
        ) {
            preferencesDataStore.updatePreferences {
                it.copy(
                    adaptivePeakFreezeEnabled = adaptiveFreezeState,
                    peakQualityDegradeStreak = adaptiveFreezeDegradeStreak.coerceAtLeast(0)
                )
            }
        }

        return AdaptiveFreezeGuard(
            freezeEnabled = adaptiveFreezeState,
            degradeStreak = adaptiveFreezeDegradeStreak
        )
    }

    private data class BehaviorSignals(
        val slots: List<PeakEnergyEngine.SlotPerformanceAggregate>,
        val taskTypePerformance: List<PeakEnergyEngine.TaskTypePerformanceAggregate>,
        val coverage: Float,
        val recentWeight: Float
    )

    private suspend fun behaviorSignals(
        sessions: List<TimeSessionEntity>,
        tasks: List<TaskEntity>,
        cutoffMillis: Long,
        profileType: PeakEnergyEngine.ProfileType,
        lookbackDays: Int
    ): BehaviorSignals {
        val tasksById = tasks.associateBy { it.id }
        data class Acc(
            var weight: Double = 0.0,
            var completion: Double = 0.0,
            var abort: Double = 0.0,
            var distraction: Double = 0.0,
            var samples: Int = 0
        )
        val bucket = mutableMapOf<Int, Acc>()
        data class TaskTypeAcc(
            var weightedBucketSum: Double = 0.0,
            var weightedQualitySum: Double = 0.0,
            var weight: Double = 0.0,
            var samples: Int = 0
        )
        val taskTypeBuckets = mutableMapOf<String, TaskTypeAcc>()
        var totalWeightedSamples = 0.0
        var recentWeighted = 0.0

        sessions.asSequence()
            .filter { it.startedAt >= cutoffMillis }
            .filter { it.endedAt != null }
            .filter { profileTypeForMillis(it.startedAt) == profileType }
            .forEach { session ->
                val endedAt = session.endedAt ?: return@forEach
                val task = tasksById[session.taskId]
                val duration = session.durationMinutes.coerceAtLeast(0f)
                val isAbort = duration in 0f..ABORT_MINUTES
                val distractionScore = (task?.distractionScore ?: -1f).let {
                    if (it < 0f) 0.3f else (it / MAX_DISTRACTION_SCORE).coerceIn(0f, 1f)
                }
                val completion = if (task != null && task.completedAt != null) {
                    val deltaMinutes = kotlin.math.abs(task.completedAt - endedAt) / 60_000L
                    if (deltaMinutes <= COMPLETION_PROXIMITY_MINUTES) 1f else 0f
                } else {
                    0f
                }
                val qualityWeight = when {
                    duration >= QUALITY_MINUTES && distractionScore <= 0.5f -> 1f
                    duration >= (QUALITY_MINUTES * 0.7f) -> 0.7f
                    else -> 0.35f
                }
                val recencyWeight = recencyWeight(session.startedAt, lookbackDays)
                val sampleWeight = (qualityWeight * recencyWeight).coerceAtLeast(0.05f)
                val bucketMinute = PeakEnergyEngine.bucketMinuteOfDay(session.startedAt)
                val acc = bucket.getOrPut(bucketMinute) { Acc() }
                acc.weight += sampleWeight
                acc.completion += completion * sampleWeight
                acc.abort += (if (isAbort) 1f else 0f) * sampleWeight
                acc.distraction += distractionScore * sampleWeight
                acc.samples += 1

                val taskType = task?.taskType?.name ?: "UNKNOWN"
                val quality = (
                    completion * 0.6f +
                        (1f - if (isAbort) 1f else 0f) * 0.2f +
                        (1f - distractionScore) * 0.2f
                    ).coerceIn(0f, 1f)
                val typeAcc = taskTypeBuckets.getOrPut(taskType) { TaskTypeAcc() }
                typeAcc.weightedBucketSum += bucketMinute * sampleWeight
                typeAcc.weightedQualitySum += quality * sampleWeight
                typeAcc.weight += sampleWeight
                typeAcc.samples += 1
                totalWeightedSamples += sampleWeight
                recentWeighted += recencyWeight
            }

        if (bucket.isEmpty()) return BehaviorSignals(emptyList(), emptyList(), 0f, 1f)

        val slots = bucket.entries.map { (bucketStart, acc) ->
            val safeWeight = acc.weight.coerceAtLeast(1e-6)
            PeakEnergyEngine.SlotPerformanceAggregate(
                bucketStartMinute = bucketStart,
                qualityWeightedCompletionRate = (acc.completion / safeWeight).toFloat().coerceIn(0f, 1f),
                abortRate = (acc.abort / safeWeight).toFloat().coerceIn(0f, 1f),
                distractionRate = (acc.distraction / safeWeight).toFloat().coerceIn(0f, 1f),
                sampleCount = acc.samples
            )
        }.sortedBy { it.bucketStartMinute }
        val typePerf = taskTypeBuckets.entries.map { (taskType, acc) ->
            val safeWeight = acc.weight.coerceAtLeast(1e-6)
            PeakEnergyEngine.TaskTypePerformanceAggregate(
                taskType = taskType,
                averageBestBucketMinute = (acc.weightedBucketSum / safeWeight).roundToInt().coerceIn(0, 1439),
                weightedQuality = (acc.weightedQualitySum / safeWeight).toFloat().coerceIn(0f, 1f),
                sampleCount = acc.samples
            )
        }

        val expectedSamples = (lookbackDays * 2).toFloat().coerceAtLeast(1f)
        val coverage = (totalWeightedSamples.toFloat() / expectedSamples).coerceIn(0f, 1f)
        val recentWeight = (recentWeighted / bucket.values.sumOf { it.samples.toDouble() }.coerceAtLeast(1.0))
            .toFloat()
            .coerceIn(0.5f, 1.2f)

        return BehaviorSignals(
            slots = slots,
            taskTypePerformance = typePerf,
            coverage = coverage,
            recentWeight = recentWeight
        )
    }

    private fun coldStartFactor(behaviorCoverage: Float, sleepCoverage: Float): Float {
        val blended = (behaviorCoverage * 0.7f + sleepCoverage * 0.3f).coerceIn(0f, 1f)
        return (0.35f + blended * 0.65f).coerceIn(0.35f, 1f)
    }

    private fun driftMinutes(
        slots: List<PeakEnergyEngine.SlotPerformanceAggregate>,
        baselineAnchorMinute: Int
    ): Int {
        if (slots.isEmpty()) return 0
        val top = slots.maxByOrNull {
            (it.qualityWeightedCompletionRate * 0.6f) + ((1f - it.abortRate) * 0.25f) + ((1f - it.distractionRate) * 0.15f)
        } ?: return 0
        val day = 24 * 60
        // Window 1 (morning) target is baselineAnchorMinute
        val forward = (top.bucketStartMinute - baselineAnchorMinute + day) % day
        val signed = if (forward > day / 2) forward - day else forward
        return (signed * 0.2f).roundToInt().coerceIn(-35, 35)
    }

    private fun weeklyBacktestError(
        slots: List<PeakEnergyEngine.SlotPerformanceAggregate>,
        baselineAnchorMinute: Int
    ): Float? {
        if (slots.isEmpty()) return null
        // Standard window offsets relative to baselineAnchor (0, 570, 810 min)
        val defaultWindowStartMinutes = listOf(
            baselineAnchorMinute,
            (baselineAnchorMinute + 570) % (24 * 60),
            (baselineAnchorMinute + 810) % (24 * 60)
        )
        val day = 24 * 60

        // Find min distance from each performance slot to its nearest window target
        val minDivergences = slots.map { slot ->
            val slotMin = slot.bucketStartMinute
            val closestWindowDist = defaultWindowStartMinutes.minOf { target ->
                val fwd = (slotMin - target + day) % day
                val signed = if (fwd > day / 2) fwd - day else fwd
                abs(signed)
            }
            closestWindowDist.toFloat()
        }

        return if (minDivergences.isNotEmpty()) minDivergences.average().toFloat() else null
    }

    private fun buildProfileOverride(
        enabled: Boolean,
        typeRaw: String,
        anchorMinute: Int,
        w1StartOffset: Int,
        w2StartOffset: Int,
        w3StartOffset: Int,
        w1Duration: Int,
        w2Duration: Int,
        w3Duration: Int,
        w1Amplitude: Float,
        w2Amplitude: Float,
        w3Amplitude: Float
    ): PeakEnergyEngine.ProfileOverride? {
        if (!enabled) return null
        val profileType = when (typeRaw.uppercase()) {
            "WORKDAY" -> PeakEnergyEngine.ProfileType.WORKDAY
            "WEEKEND" -> PeakEnergyEngine.ProfileType.WEEKEND
            else -> null
        }
        return PeakEnergyEngine.ProfileOverride(
            enabled = true,
            profileType = profileType,
            anchorMinuteOfDay = anchorMinute.coerceIn(0, 1439),
            windows = listOf(
                PeakEnergyEngine.PeakWindow(w1StartOffset.coerceIn(0, 1439), w1Duration.coerceIn(30, 360), w1Amplitude.coerceIn(0.2f, 1f)),
                PeakEnergyEngine.PeakWindow(w2StartOffset.coerceIn(0, 1439), w2Duration.coerceIn(30, 360), w2Amplitude.coerceIn(0.2f, 1f)),
                PeakEnergyEngine.PeakWindow(w3StartOffset.coerceIn(0, 1439), w3Duration.coerceIn(30, 360), w3Amplitude.coerceIn(0.2f, 1f))
            )
        )
    }

    private suspend fun maybeAutoTuneMorningWeights(
        divergence: Float,
        sampleCount: Int,
        lastTuningMillis: Long,
        shouldTriggerAdaptiveTuning: Boolean
    ): Boolean {
        val prefs = preferencesDataStore.preferencesFlow.first()
        val now = System.currentTimeMillis()
        val trigger = shouldTriggerAdaptiveTuning || PeakEnergyEngine.shouldTriggerAdaptiveTuning(
            divergence = divergence,
            sampleCount = sampleCount,
            lastTuningMillis = lastTuningMillis,
            nowMillis = now
        )
        if (!trigger) return false

        val sessions = sessionRepository.getAllSessions()
        if (sessions.isEmpty()) return false
        val lookbackStart = now - (LOOKBACK_DAYS * 24L * 60L * 60L * 1000L)
        val recent = sessions.filter { it.startedAt >= lookbackStart && it.endedAt != null }
        if (recent.size < 8) return false

        val interruptionRate = recent
            .map { (it.pauseResumeCount + it.interruptionBurstCount).toFloat() / maxOf(it.durationMinutes, 1f) }
            .average()
            .toFloat()
            .coerceIn(0f, 1f)
        val qualityRate = recent
            .map { (it.durationMinutes / 45f).coerceIn(0f, 1f) * (1f - (it.appSwitchCount / 6f).coerceIn(0f, 1f)) }
            .average()
            .toFloat()
            .coerceIn(0f, 1f)

        // Phase 3 adaptive tuning: scale coefficient updates with observed divergence.
        val divergenceBoost = (1f + divergence.coerceIn(0f, 1f) * 0.8f).coerceIn(1f, 1.8f)

        val deltaBehavior = ((qualityRate - interruptionRate) * 0.05f * divergenceBoost).coerceIn(-0.035f, 0.035f)
        val deltaWake = ((interruptionRate - 0.25f) * 0.03f * divergenceBoost).coerceIn(-0.025f, 0.025f)
        val deltaSleep = ((0.7f - qualityRate) * 0.02f * divergenceBoost).coerceIn(-0.02f, 0.02f)

        val tunedSleep = (prefs.morningTuneSleepWeight + deltaSleep).coerceIn(0.15f, 0.45f)
        val tunedWake = (prefs.morningTuneWakeWeight + deltaWake).coerceIn(0.15f, 0.4f)
        val tunedBehavior = (prefs.morningTuneBehaviorWeight + deltaBehavior).coerceIn(0.15f, 0.45f)
        val tunedBase = prefs.morningTuneBaseWeight.coerceIn(0.1f, 0.3f)
        val total = (tunedSleep + tunedWake + tunedBehavior + tunedBase).coerceAtLeast(1e-6f)

        preferencesDataStore.updatePreferences {
            it.copy(
                morningTuneSleepWeight = tunedSleep / total,
                morningTuneWakeWeight = tunedWake / total,
                morningTuneBehaviorWeight = tunedBehavior / total,
                morningTuneBaseWeight = tunedBase / total,
                morningTuneUpdatedAtMillis = now,
                morningTuneVersion = maxOf(1, it.morningTuneVersion) + 1
            )
        }
        return true
    }

    private fun recencyWeight(timestampMillis: Long, lookbackDays: Int): Float {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(zone).toLocalDate()
        val date = Instant.ofEpochMilli(timestampMillis).atZone(zone).toLocalDate()
        val daysAgo = ChronoUnit.DAYS.between(date, today).toInt().coerceAtLeast(0)
        val bounded = daysAgo.coerceAtMost(lookbackDays)
        return exp(-RECENCY_LAMBDA * bounded.toDouble()).toFloat().coerceIn(0.08f, 1f)
    }

    private fun circularWakeVarianceMinutes(logs: List<SleepLogEntity>): Int {
        if (logs.isEmpty()) return 0
        val zoneId = ZoneId.systemDefault()
        val wakeMinutes = logs.map { log ->
            val local = Instant.ofEpochMilli(log.endAt).atZone(zoneId).toLocalTime()
            local.hour * 60 + local.minute
        }
        val radians = wakeMinutes.map { (it.toDouble() / (24.0 * 60.0)) * (2.0 * Math.PI) }
        val meanSin = radians.map(::sin).average()
        val meanCos = radians.map(::cos).average()
        val resultantLength = kotlin.math.sqrt((meanSin * meanSin) + (meanCos * meanCos)).coerceIn(0.0, 1.0)
        val circularStd = kotlin.math.sqrt((-2.0 * kotlin.math.ln(resultantLength.coerceAtLeast(1e-6))))
        val minutesStd = (circularStd * (24.0 * 60.0) / (2.0 * Math.PI)).roundToInt()
        return minutesStd.coerceIn(0, 240)
    }

    private fun currentProfileType(nowMillis: Long = System.currentTimeMillis()): PeakEnergyEngine.ProfileType {
        return profileTypeForMillis(nowMillis)
    }

    private fun profileTypeForMillis(millis: Long): PeakEnergyEngine.ProfileType {
        val zone = ZoneId.systemDefault()
        val date = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        return when (date.dayOfWeek) {
            java.time.DayOfWeek.SATURDAY,
            java.time.DayOfWeek.SUNDAY -> PeakEnergyEngine.ProfileType.WEEKEND
            else -> PeakEnergyEngine.ProfileType.WORKDAY
        }
    }

    private fun parseChronotype(raw: String?): MEQChronotypeDetector.Chronotype? {
        if (raw.isNullOrBlank()) return null
        return try {
            MEQChronotypeDetector.Chronotype.valueOf(raw)
        } catch (_: Exception) {
            null
        }
    }

    private fun toMeqResult(chronotype: MEQChronotypeDetector.Chronotype): MEQChronotypeDetector.Result {
        val totalScore = when (chronotype) {
            MEQChronotypeDetector.Chronotype.DEFINITE_MORNING -> 75
            MEQChronotypeDetector.Chronotype.MODERATE_MORNING -> 64
            MEQChronotypeDetector.Chronotype.INTERMEDIATE -> 50
            MEQChronotypeDetector.Chronotype.MODERATE_EVENING -> 36
            MEQChronotypeDetector.Chronotype.DEFINITE_EVENING -> 24
        }
        val (peakStart, peakEnd) = MEQChronotypeDetector.baselinePeakWindow(chronotype)
        return MEQChronotypeDetector.Result(
            totalScore = totalScore,
            chronotype = chronotype,
            baselinePeakStartHour = peakStart,
            baselinePeakEndHour = peakEnd,
            answeredQuestions = MEQChronotypeDetector.QUESTION_COUNT,
            confidence = 1f
        )
    }

    private fun midpointMinuteOfDay(startHour: Int, endHour: Int): Int {
        val startMinute = normalizeHour(startHour) * 60
        var endMinute = normalizeHour(endHour) * 60
        if (endMinute < startMinute) {
            endMinute += 24 * 60
        }
        val midpoint = (startMinute + endMinute) / 2
        return midpoint % (24 * 60)
    }

    private fun normalizeHour(hour: Int): Int {
        val normalized = hour % 24
        return if (normalized < 0) normalized + 24 else normalized
    }

    private fun minutesDifferenceWrapped(startMinute: Int, endMinute: Int): Int {
        val dayMinutes = 24 * 60
        val start = ((startMinute % dayMinutes) + dayMinutes) % dayMinutes
        val end = ((endMinute % dayMinutes) + dayMinutes) % dayMinutes
        return if (end >= start) end - start else (dayMinutes - start) + end
    }
}
