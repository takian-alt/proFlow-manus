package com.neuroflow.app.domain.repository

import com.neuroflow.app.data.local.NeuroFlowDatabase
import com.neuroflow.app.data.local.entity.EnergyPredictionEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Phase 2: Repository for energy telemetry and audit trails.
 *
 * Manages local energy prediction snapshots for backtesting, calibration validation,
 * and non-invasive audit trails. Enforces retention policies (30-day raw, 90-day
 * aggregated metrics) to keep storage bounded.
 */
@Singleton
class EnergyMetricsRepository @Inject constructor(
    private val database: NeuroFlowDatabase
) {
    companion object {
        private const val RAW_DATA_RETENTION_DAYS = 30L
        private const val AGGREGATED_RETENTION_DAYS = 90L
        const val MIN_RETENTION_DAYS = 1
        const val MAX_RETENTION_DAYS = 90
        const val RAW_DATA_RETENTION_MILLIS = RAW_DATA_RETENTION_DAYS * 24 * 60 * 60 * 1000L
        const val AGGREGATED_RETENTION_MILLIS = AGGREGATED_RETENTION_DAYS * 24 * 60 * 60 * 1000L
    }

    private val energyPredictionDao = database.energyPredictionDao()

    /**
     * Record a new energy prediction snapshot for audit and backtesting.
     * Phase 2 telemetry capture: all component breakdowns are persisted.
     */
    suspend fun recordEnergyPrediction(
        predictedAtMillis: Long,
        baselineRawEnergy: Float,
        peakScore: Float,
        fatiguePenalty: Float,
        sleepPressurePoints: Int,
        fatiguePercent: Int,
        momentAdjustment: Float,
        momentConfidence: Float,
        momentSupportScore: Float,
        momentPressureScore: Float,
        adjustedRawEnergy: Float,
        usableEnergy: Int,
        chronotype: String,
        wakeUpHour: Int,
        peakMinuteOfDay: Int,
        peakConfidence: Float,
        peakDetectionAgeMillis: Long,
        sleepLogAgeMillis: Long,
        sessionDataAgeMillis: Long,
        recentFocusMinutes: Float,
        recentInterruptionCount: Int,
        recentAppSwitchCount: Int,
        activeTaskCount: Int,
        notificationCount: Int
    ) {
        val instant = java.time.Instant.ofEpochMilli(predictedAtMillis)
            .atZone(java.time.ZoneId.systemDefault())
        val dayOfWeek = instant.dayOfWeek.value    // 1=Monday .. 7=Sunday (ISO-8601)
        val hourOfDay = instant.hour

        val entity = EnergyPredictionEntity(
            id = UUID.randomUUID().toString(),
            predictedAtMillis = predictedAtMillis,
            predictedAtDayOfWeek = dayOfWeek,
            predictedAtHourOfDay = hourOfDay,
            peakDetectionAgeMillis = peakDetectionAgeMillis,
            sleepLogAgeMillis = sleepLogAgeMillis,
            sessionDataAgeMillis = sessionDataAgeMillis,
            baselineRawEnergy = baselineRawEnergy,
            peakScore = peakScore,
            fatiguePenalty = fatiguePenalty,
            sleepPressurePoints = sleepPressurePoints,
            fatiguePercent = fatiguePercent,
            momentAdjustment = momentAdjustment,
            momentConfidence = momentConfidence,
            momentSupportScore = momentSupportScore,
            momentPressureScore = momentPressureScore,
            adjustedRawEnergy = adjustedRawEnergy,
            usableEnergy = usableEnergy,
            chronotype = chronotype,
            wakeUpHour = wakeUpHour,
            peakMinuteOfDay = peakMinuteOfDay,
            peakConfidence = peakConfidence,
            recentFocusMinutes = recentFocusMinutes,
            recentInterruptionCount = recentInterruptionCount,
            recentAppSwitchCount = recentAppSwitchCount,
            activeTaskCount = activeTaskCount,
            notificationCount = notificationCount,
            createdAtMillis = System.currentTimeMillis()
        )

        energyPredictionDao.insert(entity)
    }

    /**
     * Get recent energy predictions for diagnostics and validation.
     */
    suspend fun getRecentPredictions(limit: Int = 100): List<EnergyPredictionEntity> {
        return energyPredictionDao.getRecent(limit)
    }

    /**
     * Get energy predictions within a time range (e.g., last 7 days).
     */
    suspend fun getPredictionsInRange(startMillis: Long, endMillis: Long): List<EnergyPredictionEntity> {
        return energyPredictionDao.getInRange(startMillis, endMillis)
    }

    /**
     * Observe recent predictions as a flow (for real-time backtesting updates).
     */
    fun observeRecentPredictions(afterMillis: Long): Flow<List<EnergyPredictionEntity>> {
        return energyPredictionDao.observeAfter(afterMillis)
    }

    /**
     * Get prediction count for storage monitoring.
     */
    suspend fun getPredictionCount(): Int {
        return energyPredictionDao.count()
    }

    /**
     * Clean up old energy predictions according to retention policy.
     * Phase 2 data hygiene: keep 30 days of raw data, aggregate beyond 30 days.
     *
     * Returns: count of records deleted
     */
    suspend fun enforceRetentionPolicy(
        retentionDays: Int = RAW_DATA_RETENTION_DAYS.toInt(),
        nowMillis: Long = System.currentTimeMillis()
    ): Int {
        val safeRetentionDays = retentionDays.coerceIn(MIN_RETENTION_DAYS, MAX_RETENTION_DAYS)
        val cutoffMillis = nowMillis - (safeRetentionDays * 24L * 60L * 60L * 1000L)
        return energyPredictionDao.deleteOlderThan(cutoffMillis)
    }

    suspend fun clearAllPredictions(): Int {
        return energyPredictionDao.deleteAll()
    }

    /**
     * Get statistics for recent predictions (for aggregated analytics).
     */
    suspend fun getStatisticsForRange(startMillis: Long, endMillis: Long): PredictionStatistics {
        val predictions = energyPredictionDao.getInRange(startMillis, endMillis)
        if (predictions.isEmpty()) {
            return PredictionStatistics()
        }

        val avgBaseline = predictions.map { it.baselineRawEnergy }.average().toFloat()
        val avgAdjusted = predictions.map { it.adjustedRawEnergy }.average().toFloat()
        val avgMomentAdjustment = predictions.map { it.momentAdjustment }.average().toFloat()
        val avgMomentConfidence = predictions.map { it.momentConfidence }.average().toFloat()
        val avgFatiguePct = predictions.map { it.fatiguePercent }.average().toInt()
        val avgUsableEnergy = predictions.map { it.usableEnergy }.average().toInt()

        // Minute-of-day analysis for peak clarity
        val peaksByHour = predictions.groupBy { it.predictedAtHourOfDay }
            .mapValues { (_, hourPredictions) ->
                hourPredictions.map { it.adjustedRawEnergy }.average().toFloat()
            }

        return PredictionStatistics(
            sampleCount = predictions.size,
            avgBaselineEnergy = avgBaseline,
            avgAdjustedEnergy = avgAdjusted,
            avgMomentAdjustment = avgMomentAdjustment,
            avgMomentConfidence = avgMomentConfidence,
            avgFatiguePercent = avgFatiguePct,
            avgUsableEnergy = avgUsableEnergy,
            energyByHourOfDay = peaksByHour
        )
    }

    data class PredictionStatistics(
        val sampleCount: Int = 0,
        val avgBaselineEnergy: Float = 0f,
        val avgAdjustedEnergy: Float = 0f,
        val avgMomentAdjustment: Float = 0f,
        val avgMomentConfidence: Float = 0f,
        val avgFatiguePercent: Int = 0,
        val avgUsableEnergy: Int = 0,
        val energyByHourOfDay: Map<Int, Float> = emptyMap()
    )
}
