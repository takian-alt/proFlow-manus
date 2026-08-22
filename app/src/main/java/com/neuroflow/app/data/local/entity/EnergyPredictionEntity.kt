package com.neuroflow.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Phase 2 telemetry entity: stores snapshots of energy predictions with full component breakdowns.
 * Used for audit trails, backtesting, calibration validation, and local analytics.
 *
 * One record per prediction refresh (~60 seconds). Bounded retention: 30 days raw, 90 days aggregated.
 */
@Entity(tableName = "energy_predictions")
data class EnergyPredictionEntity(
    @PrimaryKey
    val id: String,

    // Temporal context
    val predictedAtMillis: Long,
    val predictedAtDayOfWeek: Int,        // 1=Monday .. 7=Sunday (ISO-8601, from java.time.DayOfWeek.value)
    val predictedAtHourOfDay: Int,        // 0..23

    // Ground truth inputs (freshness guards)
    val peakDetectionAgeMillis: Long,     // How old was the peak detection?
    val sleepLogAgeMillis: Long,          // How recently was sleep logged?
    val sessionDataAgeMillis: Long,       // How old is the last session event?

    // Component scores (for diagnostics and calibration)
    val baselineRawEnergy: Float,         // -100..100 raw energy before moment adjustment
    val peakScore: Float,                 // Circadian baseline contribution
    val fatiguePenalty: Float,            // Sleep pressure penalty to baseline
    val sleepPressurePoints: Int,         // Raw sleep pressure accumulation
    val fatiguePercent: Int,              // 0..100% fatigue zone

    // Moment adjustment layer (Phase 1 guards applied)
    val momentAdjustment: Float,          // ±24pt from interruptions/tasks/notifications
    val momentConfidence: Float,          // 0..1 confidence in moment adjustment
    val momentSupportScore: Float,        // Focus density, calmness signal
    val momentPressureScore: Float,       // Sleep pressure, peak age, task load

    // Final output
    val adjustedRawEnergy: Float,         // Baseline + (adjustment × confidence)
    val usableEnergy: Int,                // 0..100 user-facing energy percentage

    // Chronotype context (for future per-type adaptive tuning)
    val chronotype: String,               // DEFINITE_MORNING, MODERATE_MORNING, INTERMEDIATE, etc.
    val wakeUpHour: Int,                  // User's configured wake time
    val peakMinuteOfDay: Int,             // Computed peak time for this chronotype
    val peakConfidence: Float,            // Confidence in baseline peak computation

    // Feature signals (for feature importance tracking)
    val recentFocusMinutes: Float,        // Last 180-min focused work time
    val recentInterruptionCount: Int,     // Session interruptions in window
    val recentAppSwitchCount: Int,        // App switches in window
    val activeTaskCount: Int,             // Currently active open tasks
    val notificationCount: Int,           // Badge count from notifications

    // Retention marker (for cleanup)
    val createdAtMillis: Long = System.currentTimeMillis()
)
