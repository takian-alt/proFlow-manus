package com.neuroflow.app.domain.repository

import com.neuroflow.app.data.repository.SleepLogRepository
import com.neuroflow.app.data.local.UserPreferencesDataStore
import com.neuroflow.app.data.local.entity.TaskEntity
import com.neuroflow.app.data.local.entity.TimeSessionEntity
import com.neuroflow.app.data.repository.SessionRepository
import com.neuroflow.app.data.repository.TaskRepository
import com.neuroflow.app.domain.engine.EnergyScoreEngine
import com.neuroflow.app.domain.engine.MomentEnergyEngine
import com.neuroflow.app.domain.engine.PeakEnergyEngine
import com.neuroflow.app.domain.engine.SleepPressureDetector
import com.neuroflow.app.domain.model.TaskStatus
import com.neuroflow.app.presentation.launcher.data.NotificationBadgeManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow

@Singleton
class EnergyScoreRepository @Inject constructor(
    private val preferencesDataStore: UserPreferencesDataStore,
    private val peakEnergyRepository: PeakEnergyRepository,
    private val sessionRepository: SessionRepository,
    private val taskRepository: TaskRepository,
    private val sleepLogRepository: SleepLogRepository,
    private val notificationBadgeManager: NotificationBadgeManager,
    private val energyMetricsRepository: EnergyMetricsRepository
) {

    companion object {
        private const val RETENTION_ENFORCE_INTERVAL_MILLIS = 6 * 60 * 60 * 1000L
    }

    @Volatile
    private var lastRetentionEnforcedAtMillis: Long = 0L

    private data class MomentSignalContext(
        val sessions: List<TimeSessionEntity>,
        val tasks: List<TaskEntity>,
        val badgeCounts: Map<String, Int>,
        val latestSleepLogEndAt: Long?  // null = no sleep logs recorded yet
    )

    data class EnergyUiModel(
        val availableEnergy: Int,
        val rawEnergy: Float,
        val baselineRawEnergy: Float,
        val peakScore: Float,
        val fatiguePenalty: Float,
        val momentAdjustment: Float,
        val momentConfidence: Float,
        val currentPeakValue: Int,
        val peakValue: Int,
        val peakDrop: Int,
        val minutesSincePeak: Int,
        val minutesUntilPeakReset: Int,
        val sleepPressurePoints: Int,
        val fatiguePercent: Int,
        val fatigueZone: SleepPressureDetector.FatigueZone,
        val circadianFactor: Float,
        val reservoirFactor: Float,
        val confidenceFactor: Float,
        val effectivePeakProfile: PeakEnergyEngine.EffectivePeakProfile? = null,
        val momentSignalSummary: String = "",
        val peakDetectionAgeMillis: Long = 0L,
        // Freshness tracking: age of input data sources
        val sessionDataAgeMillis: Long = 0L,
        val sleepLogDataAgeMillis: Long = 0L,
        val taskDataAgeMillis: Long = 0L,
        val overallFreshnessAgeMillis: Long = 0L,
        val hasRecentData: Boolean = true,
        val refreshedAtMillis: Long
    )

    fun observeEnergy(refreshIntervalMillis: Long = 60_000L): Flow<EnergyUiModel> {
        val ticker = flow {
            while (true) {
                emit(System.currentTimeMillis())
                delay(refreshIntervalMillis)
            }
        }

        val momentSignals = combine(
            sessionRepository.observeAll(),
            taskRepository.observeAll(),
            sleepLogRepository.observeAll(),
            notificationBadgeManager.badgeCounts
        ) { sessions, tasks, sleepLogs, badgeCounts ->
            MomentSignalContext(
                sessions = sessions,
                tasks = tasks,
                badgeCounts = badgeCounts,
                latestSleepLogEndAt = sleepLogs.maxByOrNull { it.endAt }?.endAt
            )
        }

        return combine(
            peakEnergyRepository.peakEnergyFlow,
            preferencesDataStore.preferencesFlow,
            momentSignals,
            ticker
        ) { peakDetection, prefs, momentSignals, nowMillis ->
            val sleepPressurePoints = prefs.sleepPressurePoints.coerceAtLeast(0)
            val baselineScore = EnergyScoreEngine.calculateDetailed(
                EnergyScoreEngine.EnergySnapshot(
                    peakEnergy = peakDetection,
                    sleepPressurePoints = sleepPressurePoints,
                    nowMillis = nowMillis
                )
            )

            val recentWindowMinutes = 180
            val sessionMetrics5m = summarizeRecentSessions(momentSignals.sessions, nowMillis, 5)
            val sessionMetrics15m = summarizeRecentSessions(momentSignals.sessions, nowMillis, 15)
            val sessionMetrics30m = summarizeRecentSessions(momentSignals.sessions, nowMillis, 30)
            val sessionMetrics60m = summarizeRecentSessions(momentSignals.sessions, nowMillis, 60)
            val sessionMetrics180m = summarizeRecentSessions(momentSignals.sessions, nowMillis, recentWindowMinutes)
            val recentSessionMetrics = sessionMetrics180m
            val taskMetrics = summarizeTasks(momentSignals.tasks, nowMillis)
            val notificationCount = momentSignals.badgeCounts.values.sum()

            val sessionDataAgeMillis = momentSignals.sessions
                .maxByOrNull { it.startedAt }
                ?.let { (nowMillis - it.startedAt).coerceAtLeast(0L) }
                ?: Long.MAX_VALUE
            val taskDataAgeMillis = momentSignals.tasks
                .maxByOrNull { it.updatedAt }
                ?.let { (nowMillis - it.updatedAt).coerceAtLeast(0L) }
                ?: Long.MAX_VALUE
            val sleepLogDataAgeMillis = momentSignals.latestSleepLogEndAt
                ?.let { (nowMillis - it).coerceAtLeast(0L) }
                ?: Long.MAX_VALUE  // No sleep logs = maximally stale
            val overallFreshnessAgeMillis = maxOf(
                (nowMillis - peakDetection.detectedAtMillis).coerceAtLeast(0L),
                sessionDataAgeMillis,
                taskDataAgeMillis,
                sleepLogDataAgeMillis
            )

            val multiHorizonFeatures = MomentEnergyEngine.MultiHorizonFeatures(
                window5m = MomentEnergyEngine.MultiHorizonFeatures.WindowMetrics(
                    focusMinutes = sessionMetrics5m.recentFocusMinutes,
                    interruptionCount = sessionMetrics5m.recentInterruptionCount,
                    appSwitchCount = sessionMetrics5m.recentAppSwitchCount
                ),
                window15m = MomentEnergyEngine.MultiHorizonFeatures.WindowMetrics(
                    focusMinutes = sessionMetrics15m.recentFocusMinutes,
                    interruptionCount = sessionMetrics15m.recentInterruptionCount,
                    appSwitchCount = sessionMetrics15m.recentAppSwitchCount
                ),
                window30m = MomentEnergyEngine.MultiHorizonFeatures.WindowMetrics(
                    focusMinutes = sessionMetrics30m.recentFocusMinutes,
                    interruptionCount = sessionMetrics30m.recentInterruptionCount,
                    appSwitchCount = sessionMetrics30m.recentAppSwitchCount
                ),
                window60m = MomentEnergyEngine.MultiHorizonFeatures.WindowMetrics(
                    focusMinutes = sessionMetrics60m.recentFocusMinutes,
                    interruptionCount = sessionMetrics60m.recentInterruptionCount,
                    appSwitchCount = sessionMetrics60m.recentAppSwitchCount
                ),
                window180m = MomentEnergyEngine.MultiHorizonFeatures.WindowMetrics(
                    focusMinutes = sessionMetrics180m.recentFocusMinutes,
                    interruptionCount = sessionMetrics180m.recentInterruptionCount,
                    appSwitchCount = sessionMetrics180m.recentAppSwitchCount
                )
            )

            val momentResult = MomentEnergyEngine.predict(
                MomentEnergyEngine.MomentSignalSnapshot(
                    baselineRawEnergy = baselineScore.rawEnergy,
                    sleepPressurePoints = sleepPressurePoints,
                    peakConfidence = peakDetection.confidence,
                    minutesSincePeak = peakDetection.minutesSincePeak(nowMillis),
                    recentFocusMinutes = recentSessionMetrics.recentFocusMinutes,
                    recentInterruptionCount = recentSessionMetrics.recentInterruptionCount,
                    recentAppSwitchCount = recentSessionMetrics.recentAppSwitchCount,
                    recentPauseResumeCount = recentSessionMetrics.recentPauseResumeCount,
                    notificationCount = notificationCount,
                    activeTaskCount = taskMetrics.activeTaskCount,
                    overdueTaskCount = taskMetrics.overdueTaskCount,
                    dueSoonTaskCount = taskMetrics.dueSoonTaskCount,
                    activeSessionCount = recentSessionMetrics.activeSessionCount,
                    interruptionSensitivity = prefs.momentInterruptionSensitivity,
                    notificationSensitivity = prefs.momentNotificationSensitivity,
                    taskPressureSensitivity = prefs.momentTaskPressureSensitivity,
                    recentWindowMinutes = recentWindowMinutes,
                    multiHorizonFeatures = multiHorizonFeatures,
                    signalFreshnessAgeMillis = overallFreshnessAgeMillis
                )
            )

            val currentPeakValue = peakDetection.currentValueAt(nowMillis)
            val peakValue = peakDetection.peakValue
            val peakDrop = (peakValue - currentPeakValue).coerceAtLeast(0)
            val minutesSincePeak = peakDetection.minutesSincePeak(nowMillis)
            val minutesUntilPeakReset = ((24 * 60) - minutesSincePeak).coerceAtLeast(0)

            // Data is considered "recent" if all sources are less than 5 minutes old
            val freshnessThresholdMillis = 5 * 60_000L
            val hasRecentData = overallFreshnessAgeMillis < freshnessThresholdMillis
            val fatiguePercent = SleepPressureDetector.fatiguePercent(sleepPressurePoints)
            val fatigueZone = SleepPressureDetector.fatigueZone(sleepPressurePoints)

            val energyUiModel = EnergyUiModel(
                availableEnergy = momentResult.usableEnergy.toInt().coerceIn(0, 100),
                rawEnergy = momentResult.adjustedRawEnergy,
                baselineRawEnergy = baselineScore.rawEnergy,
                peakScore = baselineScore.peakScore,
                fatiguePenalty = baselineScore.fatiguePenalty,
                momentAdjustment = momentResult.adjustment,
                momentConfidence = momentResult.confidence,
                currentPeakValue = currentPeakValue,
                peakValue = peakValue,
                peakDrop = peakDrop,
                minutesSincePeak = minutesSincePeak,
                minutesUntilPeakReset = minutesUntilPeakReset,
                sleepPressurePoints = sleepPressurePoints,
                fatiguePercent = fatiguePercent,
                fatigueZone = fatigueZone,
                circadianFactor = baselineScore.circadianFactor,
                reservoirFactor = baselineScore.reservoirFactor,
                confidenceFactor = baselineScore.confidenceFactor,
                effectivePeakProfile = peakDetection.effectiveProfile,
                momentSignalSummary = momentResult.summary,
                peakDetectionAgeMillis = nowMillis - peakDetection.detectedAtMillis,
                sessionDataAgeMillis = sessionDataAgeMillis,
                sleepLogDataAgeMillis = sleepLogDataAgeMillis,
                taskDataAgeMillis = taskDataAgeMillis,
                overallFreshnessAgeMillis = overallFreshnessAgeMillis,
                hasRecentData = hasRecentData,
                refreshedAtMillis = nowMillis
            )

            val telemetryEnabled = prefs.energyTelemetryEnabled
            val telemetryRetentionDays = prefs.energyTelemetryRetentionDays
                .coerceIn(EnergyMetricsRepository.MIN_RETENTION_DAYS, EnergyMetricsRepository.MAX_RETENTION_DAYS)

            if (telemetryEnabled) {
                try {
                    energyMetricsRepository.recordEnergyPrediction(
                        predictedAtMillis = nowMillis,
                        baselineRawEnergy = baselineScore.rawEnergy,
                        peakScore = baselineScore.peakScore,
                        fatiguePenalty = baselineScore.fatiguePenalty,
                        sleepPressurePoints = sleepPressurePoints,
                        fatiguePercent = fatiguePercent,
                        momentAdjustment = momentResult.adjustment,
                        momentConfidence = momentResult.confidence,
                        momentSupportScore = momentResult.supportScore,
                        momentPressureScore = momentResult.pressureScore,
                        adjustedRawEnergy = momentResult.adjustedRawEnergy,
                        usableEnergy = momentResult.usableEnergy.toInt().coerceIn(0, 100),
                        chronotype = peakDetection.chronotype.name,
                        wakeUpHour = peakDetection.wakeUpHour,
                        peakMinuteOfDay = peakDetection.peakMinuteOfDay,
                        peakConfidence = peakDetection.confidence,
                        peakDetectionAgeMillis = (nowMillis - peakDetection.detectedAtMillis).coerceAtLeast(0L),
                        sleepLogAgeMillis = sleepLogDataAgeMillis,
                        sessionDataAgeMillis = sessionDataAgeMillis,
                        recentFocusMinutes = recentSessionMetrics.recentFocusMinutes,
                        recentInterruptionCount = recentSessionMetrics.recentInterruptionCount,
                        recentAppSwitchCount = recentSessionMetrics.recentAppSwitchCount,
                        activeTaskCount = taskMetrics.activeTaskCount,
                        notificationCount = notificationCount
                    )
                } catch (_: Exception) {
                    // Telemetry persistence must never block live energy updates.
                }

                if (nowMillis - lastRetentionEnforcedAtMillis >= RETENTION_ENFORCE_INTERVAL_MILLIS) {
                    try {
                        energyMetricsRepository.enforceRetentionPolicy(
                            retentionDays = telemetryRetentionDays,
                            nowMillis = nowMillis
                        )
                        lastRetentionEnforcedAtMillis = nowMillis
                    } catch (_: Exception) {
                        // Retention should not interfere with live scoring.
                    }
                }
            }

            energyUiModel
        }
    }

    private data class RecentSessionMetrics(
        val recentFocusMinutes: Float,
        val recentInterruptionCount: Int,
        val recentAppSwitchCount: Int,
        val recentPauseResumeCount: Int,
        val activeSessionCount: Int
    )

    private data class TaskMetrics(
        val activeTaskCount: Int,
        val overdueTaskCount: Int,
        val dueSoonTaskCount: Int
    )

    private fun summarizeRecentSessions(
        sessions: List<TimeSessionEntity>,
        nowMillis: Long,
        windowMinutes: Int
    ): RecentSessionMetrics {
        val cutoffMillis = nowMillis - (windowMinutes * 60_000L)
        var recentFocusMinutes = 0f
        var recentInterruptionCount = 0
        var recentAppSwitchCount = 0
        var recentPauseResumeCount = 0
        var activeSessionCount = 0

        sessions.forEach { session ->
            val endedAt = session.endedAt ?: nowMillis
            if (session.startedAt >= cutoffMillis || endedAt >= cutoffMillis) {
                val durationMinutes = if (session.durationMinutes > 0f) {
                    session.durationMinutes
                } else {
                    ((endedAt - session.startedAt - session.totalPausedMs).coerceAtLeast(0L) / 60_000f)
                }
                recentFocusMinutes += durationMinutes.coerceAtLeast(0f)
                recentInterruptionCount += session.pauseResumeCount + session.appSwitchCount + session.interruptionBurstCount
                recentAppSwitchCount += session.appSwitchCount
                recentPauseResumeCount += session.pauseResumeCount
                if (session.endedAt == null) {
                    activeSessionCount += 1
                }
            }
        }

        return RecentSessionMetrics(
            recentFocusMinutes = recentFocusMinutes,
            recentInterruptionCount = recentInterruptionCount,
            recentAppSwitchCount = recentAppSwitchCount,
            recentPauseResumeCount = recentPauseResumeCount,
            activeSessionCount = activeSessionCount
        )
    }

    private fun summarizeTasks(
        tasks: List<TaskEntity>,
        nowMillis: Long
    ): TaskMetrics {
        val twoHoursMs = 2 * 60 * 60_000L
        val activeTaskCount = tasks.count { it.status == TaskStatus.ACTIVE }
        val overdueTaskCount = tasks.count { task ->
            task.status != TaskStatus.COMPLETED && (task.deadlineDate ?: Long.MAX_VALUE) < nowMillis
        }
        val dueSoonTaskCount = tasks.count { task ->
            if (task.status == TaskStatus.COMPLETED) return@count false
            val deadlineDelta = (task.deadlineDate ?: Long.MAX_VALUE) - nowMillis
            val scheduledDelta = (task.scheduledDate ?: Long.MAX_VALUE) - nowMillis
            deadlineDelta in 0 until twoHoursMs || scheduledDelta in 0 until twoHoursMs
        }

        return TaskMetrics(
            activeTaskCount = activeTaskCount,
            overdueTaskCount = overdueTaskCount,
            dueSoonTaskCount = dueSoonTaskCount
        )
    }
}
