package com.neuroflow.app.domain.engine

import kotlin.math.roundToInt
import kotlin.math.pow

/**
 * Sleep pressure model based on accumulated wake time and cycle-based sleep recovery.
 *
 * Rules:
 * - Awake: +1 point/minute.
 * - Sleep: repeating 90-minute cycles.
 *   - 0..60 min: +1 recovery point/minute.
 *   - At minute 60: +126 completion bonus (total at 60 min = 186).
 *   - 61..90 min: +2 recovery points/minute.
 *   - At minute 90: +33 completion bonus (total at 90 min = 279).
 * - Recovery cannot drive pressure below 0.
 *
 * Future extension (not implemented yet): oversleeping/sleep inertia behavior.
 */
object SleepPressureDetector {

    const val AWAKE_POINTS_PER_MINUTE = 1
    const val CYCLE_MINUTES = 90
    const val PHASE_ONE_END_MINUTE = 60
    const val PHASE_ONE_BONUS = 126
    const val PHASE_TWO_BONUS = 33
    const val FULL_CYCLE_RECOVERY = 279
    const val SOFT_MAX_REFERENCE = 3000
    private const val FATIGUE_CURVE_GAMMA = 0.82f
    // Phase 1 overflow guard: max pressure = ~10 days of continuous wake (14,400 minutes)
    // This prevents unbounded accumulation and integer overflow in downstream calculations
    private const val MAX_PRESSURE_POINTS = 14_400

    enum class FatigueZone {
        RESTED,
        MODERATE,
        HIGH,
        CRITICAL
    }

    data class DetectionResult(
        val pressurePoints: Int,
        val awakeMinutes: Int = 0,
        val sleepMinutes: Int = 0,
        val recoveryPointsEarned: Int = 0,
        val recoveryPointsApplied: Int = 0
    )

    /**
     * Backward-compatible helper: computes pressure from a pure awake duration.
     */
    fun detect(awakeHours: Float): DetectionResult {
        val safeHours = awakeHours.coerceAtLeast(0f)
        val awakeMinutes = (safeHours * 60f).roundToInt()
        return DetectionResult(
            pressurePoints = pressureFromAwakeMinutes(awakeMinutes),
            awakeMinutes = awakeMinutes
        )
    }

    /**
     * Converts awake minutes into pressure points at +1 point/min.
     * 
     * Phase 1 overflow guard: output is capped at MAX_PRESSURE_POINTS to prevent
     * unbounded accumulation and downstream calculation overflow.
     */
    fun pressureFromAwakeMinutes(awakeMinutes: Int): Int {
        val safeAwakeMinutes = awakeMinutes.coerceAtLeast(0)
        return (safeAwakeMinutes * AWAKE_POINTS_PER_MINUTE).coerceAtMost(MAX_PRESSURE_POINTS)
    }

    /**
     * Applies wake-time accumulation to the current pressure.
     * 
     * Phase 1 overflow guard: the result is capped at MAX_PRESSURE_POINTS to prevent
     * integer overflow and ensure pressure stays within reasonable bounds.
     */
    fun applyAwakeMinutes(currentPressure: Int, awakeMinutes: Int): DetectionResult {
        val safeCurrentPressure = currentPressure.coerceAtLeast(0).coerceAtMost(MAX_PRESSURE_POINTS)
        val safeAwakeMinutes = awakeMinutes.coerceAtLeast(0)
        val addedPressure = pressureFromAwakeMinutes(safeAwakeMinutes)

        return DetectionResult(
            pressurePoints = (safeCurrentPressure + addedPressure).coerceAtMost(MAX_PRESSURE_POINTS),
            awakeMinutes = safeAwakeMinutes
        )
    }

    /**
     * Returns earned recovery points for one uninterrupted sleep session.
     *
     * Cycle restarts every 90 minutes and does not carry over between sessions.
     */
    fun recoveryForSleepMinutes(sleepMinutes: Int): Int {
        val safeSleepMinutes = sleepMinutes.coerceAtLeast(0)
        if (safeSleepMinutes == 0) return 0

        val fullCycles = safeSleepMinutes / CYCLE_MINUTES
        val remainderMinutes = safeSleepMinutes % CYCLE_MINUTES

        return (fullCycles * FULL_CYCLE_RECOVERY) + remainderRecovery(remainderMinutes)
    }

    /**
     * Applies sleep-session recovery with a hard zero floor.
     * 
     * Phase 1 overflow guard: pressure is always kept within [0, MAX_PRESSURE_POINTS]
     * to ensure recovery calculations remain stable and predictable.
     */
    fun applySleepSession(currentPressure: Int, sleepMinutes: Int): DetectionResult {
        val safeCurrentPressure = currentPressure.coerceAtLeast(0).coerceAtMost(MAX_PRESSURE_POINTS)
        val safeSleepMinutes = sleepMinutes.coerceAtLeast(0)
        val earnedRecovery = recoveryForSleepMinutes(safeSleepMinutes)
        val appliedRecovery = earnedRecovery.coerceAtMost(safeCurrentPressure)

        return DetectionResult(
            pressurePoints = (safeCurrentPressure - appliedRecovery).coerceAtLeast(0).coerceAtMost(MAX_PRESSURE_POINTS),
            sleepMinutes = safeSleepMinutes,
            recoveryPointsEarned = earnedRecovery,
            recoveryPointsApplied = appliedRecovery
        )
    }

    /**
     * Maps raw pressure points to a user-facing 0..100 fatigue percentage using
     * [SOFT_MAX_REFERENCE] as the soft upper bound and a nonlinear fatigue curve.
     */
    fun fatiguePercent(pressurePoints: Int, softMaxReference: Int = SOFT_MAX_REFERENCE): Int {
        return (fatigueRatio(pressurePoints, softMaxReference) * 100f)
            .roundToInt()
            .coerceIn(0, 100)
    }

    /**
     * Fatigue ratio in [0..1] using a concave curve so fatigue rises earlier than
     * linear mapping and better matches subjective tiredness in long wake windows.
     *
     * Phase 1 overflow guard: explicitly clamps output to [0.0, 1.0] to prevent
     * invalid fatigue calculations that could affect downstream energy predictions.
     */
    fun fatigueRatio(pressurePoints: Int, softMaxReference: Int = SOFT_MAX_REFERENCE): Float {
        val safeMax = softMaxReference.coerceAtLeast(1)
        val safePressure = pressurePoints.coerceAtLeast(0)
        val ratio = (safePressure.toFloat() / safeMax.toFloat()).coerceIn(0f, 1f)
        // Explicit overflow guard: fatigue ratio must always be in [0.0, 1.0]
        return ratio.pow(FATIGUE_CURVE_GAMMA).coerceIn(0f, 1f)
    }

    /**
     * Converts raw pressure points to fatigue zones for UI color coding.
     */
    fun fatigueZone(pressurePoints: Int, softMaxReference: Int = SOFT_MAX_REFERENCE): FatigueZone {
        val percent = fatiguePercent(pressurePoints, softMaxReference)
        return when {
            percent <= 24 -> FatigueZone.RESTED
            percent <= 49 -> FatigueZone.MODERATE
            percent <= 74 -> FatigueZone.HIGH
            else -> FatigueZone.CRITICAL
        }
    }

    fun fatigueZoneLabel(zone: FatigueZone): String {
        return when (zone) {
            FatigueZone.RESTED -> "Rested"
            FatigueZone.MODERATE -> "Moderate"
            FatigueZone.HIGH -> "High"
            FatigueZone.CRITICAL -> "Critical"
        }
    }

    private fun remainderRecovery(remainderMinutes: Int): Int {
        if (remainderMinutes <= 0) return 0

        return when {
            remainderMinutes < PHASE_ONE_END_MINUTE -> remainderMinutes
            remainderMinutes == PHASE_ONE_END_MINUTE -> PHASE_ONE_END_MINUTE + PHASE_ONE_BONUS
            remainderMinutes < CYCLE_MINUTES -> {
                val phaseTwoMinutes = remainderMinutes - PHASE_ONE_END_MINUTE
                (PHASE_ONE_END_MINUTE + PHASE_ONE_BONUS) + (phaseTwoMinutes * 2)
            }
            else -> FULL_CYCLE_RECOVERY
        }
    }
}
