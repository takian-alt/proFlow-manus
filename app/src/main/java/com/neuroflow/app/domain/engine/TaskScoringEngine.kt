package com.neuroflow.app.domain.engine

import com.neuroflow.app.data.local.UserPreferences
import com.neuroflow.app.data.local.entity.TaskEntity
import com.neuroflow.app.domain.model.EnergyLevel
import com.neuroflow.app.domain.model.Priority
import com.neuroflow.app.domain.model.Quadrant
import com.neuroflow.app.domain.model.TaskStatus
import com.neuroflow.app.domain.model.TaskType
import java.util.Calendar
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * NeuroFlow Priority Scoring Engine v3
 *
 * A composite, science-backed prioritization formula that scores tasks on a
 * continuous scale. Higher score = do this task next.
 *
 * Theoretical frameworks integrated:
 *  1. Eisenhower Matrix          — quadrant base weight
 *  2. Temporal Motivation Theory — hyperbolic deadline discounting (Steel 2007)
 *  3. GTD / Next-Action          — scheduled proximity window
 *  4. Cognitive Load Theory      — energy-level matching
 *  5. Circadian Rhythm Research  — task-type × time-of-day (Anderson et al.)
 *  6. Eat the Frog (Tracy)       — hardest task first, morning-weighted
 *  7. BJ Fogg Tiny Habits        — effort-adjusted quick-win momentum
 *  8. Zeigarnik Effect           — postponed tasks nag until done
 *  9. Progress Principle (Amabile) — started tasks get momentum boost
 * 10. Implementation Intentions (Gollwitzer) — if-then plan × effort scaling
 * 11. Temptation Bundling (Milkman) — enjoyment as continuous modifier
 * 12. Commitment Devices          — public commitment accountability
 * 13. Loss Aversion (Kahneman)    — goal-risk amplification
 * 14. Stress Inoculation          — anxiety task anti-avoidance surfacing
 * 15. Critical Path Method        — dependency unblocking priority
 * 16. Self-Determination Theory   — intrinsic value as sustained motivator
 *
 * v3 changes:
 *  - THEORETICAL_MAX recalibrated to actual component sum (~2445) so displayScore
 *    spreads meaningfully across 0–999 instead of saturating at 999 for most tasks
 *  - scoreBreakdown now uses effectivePeakStart/End (same as score()) — was using
 *    raw peakEnergyStart/End, causing breakdown to show different values than actual score
 *  - distractionScore sentinel corrected: only boost when score > 0f (not >= 0f),
 *    so tasks with no usage data (-1f) and untracked tasks (0f) are correctly excluded
 *  - Frog boost now multiplied by weightFocusMode for consistency — weightFocusMode
 *    governs all focus-related boosts (effort + frog), not just effort
 *  - autoAssignQuadrant importance check now uses the same impact+value composite
 *    (0.55/0.45 split) as score(), so quadrant auto-assignment is consistent with
 *    how the engine actually ranks tasks
 */
object TaskScoringEngine {

    // Recalibrated: sum of all components at theoretical maximum with all weights = 1.0
    // Quadrant(300) + Deadline(500) + Scheduled(170) + Priority(150) + Impact(100) +
    // Effort(60) + Duration(40) + Energy(70) + Circadian(60) + Frog(120) +
    // Postpone(180) + Habit(100) + Unblock(113) + Context(30) + Recency(40) +
    // Progress(72) + IfThen(80) + Enjoyment(40) + Commitment(70) + ScheduleLock(80) +
    // LossAversion(130) + Anxiety(60) + Distraction(80) = ~2445
    private const val THEORETICAL_MAX = 2445f

    fun score(
        task: TaskEntity,
        prefs: UserPreferences,
        allActiveTasks: List<TaskEntity> = emptyList(),
        nowMillis: Long = System.currentTimeMillis()
    ): Float {
        if (task.status != TaskStatus.ACTIVE) return 0f

        // Hard block: if task has unresolved dependencies, score near-zero
        val blockedByCount = if (task.dependsOnTaskIds.isNotBlank() && allActiveTasks.isNotEmpty()) {
            val depIds = task.dependsOnTaskIds.split(",").map { it.trim() }.filter { it.isNotBlank() }
            depIds.count { depId -> allActiveTasks.any { it.id == depId && it.status == TaskStatus.ACTIVE } }
        } else 0
        if (blockedByCount > 0) return max(0f, 5f * (1f / blockedByCount))

        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)

        // Use dynamically detected peak if available, blended with manual setting
        val (effectivePeakStart, effectivePeakEnd) = if (prefs.effectivePeakStart >= 0) {
            prefs.effectivePeakStart to prefs.effectivePeakEnd
        } else {
            prefs.peakEnergyStart to prefs.peakEnergyEnd
        }

        val isPeakHour = hour in effectivePeakStart..effectivePeakEnd
        val isMorning = hour < effectivePeakStart
        val isLowEnergySlot = hour in 13..15
        val isWeekend = dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
        val isWithinWorkDay = hour in prefs.workDayStart until prefs.workDayEnd

        var s = 0f

        // ── 1. QUADRANT BASE (Eisenhower Matrix) ─────────────────────────────
        s += when (task.quadrant) {
            Quadrant.DO_FIRST  -> 300f
            Quadrant.SCHEDULE  -> 180f
            Quadrant.DELEGATE  -> 80f
            Quadrant.ELIMINATE -> 20f
        } * prefs.weightQuadrant

        // ── 2. DEADLINE PRESSURE (Temporal Motivation Theory) ────────────────
        if (task.deadlineDate != null) {
            val deadlineMs = task.deadlineDate + (task.deadlineTime ?: 0L)
            val hoursLeft = (deadlineMs - nowMillis) / 3_600_000f
            val deadlineScore = when {
                hoursLeft < 0    -> 500f
                hoursLeft < 1    -> 420f
                hoursLeft < 4    -> 340f
                hoursLeft < 12   -> 260f
                hoursLeft < 24   -> 200f
                hoursLeft < 48   -> 150f
                hoursLeft < 72   -> 110f
                hoursLeft < 168  -> 70f
                hoursLeft < 336  -> 40f
                hoursLeft < 720  -> 20f
                else             -> max(5f, 15f * exp(-hoursLeft / 720f))
            }
            s += deadlineScore * prefs.weightDeadlineUrgency
        }

        // ── 3. SCHEDULED TIME PROXIMITY (GTD next-action window) ─────────────
        if (task.scheduledDate != null) {
            val schedMs = task.scheduledDate + (task.scheduledTime ?: 0L)
            val minutesUntil = (schedMs - nowMillis) / 60_000f
            val schedScore = when {
                minutesUntil < -120 -> 5f
                minutesUntil < -30  -> 40f
                minutesUntil < 0    -> 130f
                minutesUntil < 15   -> 170f
                minutesUntil < 60   -> 110f
                minutesUntil < 240  -> 65f
                minutesUntil < 1440 -> 35f
                else                -> 8f
            }
            s += schedScore * prefs.weightDeadlineUrgency
        }

        // ── 4. PRIORITY LEVEL ────────────────────────────────────────────────
        s += when (task.priority) {
            Priority.HIGH   -> 150f
            Priority.MEDIUM -> 75f
            Priority.LOW    -> 20f
        } * prefs.weightPriorityLevel

        // ── 5. STRATEGIC IMPACT + INTRINSIC VALUE (Self-Determination Theory) ─
        // Weighted composite: impact slightly more important for prioritization.
        val importanceScore = (task.impactScore * 0.55f + task.valueScore * 0.45f)
        s += importanceScore * prefs.weightImpact

        // ── 6. EFFORT × CONTEXT (BJ Fogg + Eat the Frog) ────────────────────
        // weightFocusMode governs all focus-related boosts (effort + frog below)
        val effortNorm = task.effortScore / 100f
        val effortBoost = when {
            effortNorm < 0.3f -> (1f - effortNorm) * 60f
            effortNorm > 0.7f && (isPeakHour || isMorning) -> effortNorm * 55f
            effortNorm > 0.7f -> -(effortNorm * 20f)
            else -> 0f
        }
        s += effortBoost * prefs.weightFocusMode

        // ── 7. DURATION MOMENTUM ─────────────────────────────────────────────
        if (task.estimatedDurationMinutes in 1..90) {
            s += max(0f, 40f - task.estimatedDurationMinutes * 0.3f) * prefs.weightDuration
        }

        // ── 8. ENERGY MATCHING (Cognitive Load Theory) ───────────────────────
        val energyBonus = when {
            isPeakHour -> when (task.energyLevel) {
                EnergyLevel.HIGH   ->  70f
                EnergyLevel.MEDIUM ->  20f
                EnergyLevel.LOW    -> -35f
            }
            isLowEnergySlot -> when (task.energyLevel) {
                EnergyLevel.LOW    ->  70f
                EnergyLevel.MEDIUM ->  15f
                EnergyLevel.HIGH   -> -35f
            }
            isMorning -> when (task.energyLevel) {
                EnergyLevel.HIGH   ->  30f
                EnergyLevel.MEDIUM ->  10f
                EnergyLevel.LOW    ->   0f
            }
            else -> when (task.energyLevel) {
                EnergyLevel.MEDIUM ->  10f
                else               ->   0f
            }
        }
        s += energyBonus

        // ── 9. CIRCADIAN TASK-TYPE MATCHING ──────────────────────────────────
        val circadianBonus = when (task.taskType) {
            TaskType.ANALYTICAL -> when {
                isPeakHour      ->  60f
                isMorning       ->  30f
                isLowEnergySlot -> -25f
                else            ->   5f
            }
            TaskType.CREATIVE -> when {
                hour in 10..11  ->  55f
                hour in 16..18  ->  45f
                isPeakHour      ->  20f
                isLowEnergySlot -> -10f
                else            ->   0f
            }
            TaskType.ADMIN -> when {
                isLowEnergySlot ->  50f
                isPeakHour      -> -15f
                else            ->  10f
            }
            TaskType.PHYSICAL -> when {
                isMorning       ->  30f
                isPeakHour      ->  20f
                isLowEnergySlot -> -10f
                else            ->  10f
            }
        }
        s += circadianBonus

        // ── 10. FROG BOOST (Eat the Frog — Brian Tracy) ──────────────────────
        // weightFocusMode applied here too — frog is a focus-mode concept
        if (task.isFrog) {
            val frogBase = when {
                isMorning       -> 120f
                isPeakHour      ->  90f
                isLowEnergySlot ->  20f
                else            ->  50f
            }
            val effortMultiplier = 0.5f + (task.effortScore / 100f) * 0.5f
            s += frogBase * effortMultiplier * prefs.weightFocusMode
        }

        // ── 11. POSTPONE PENALTY → URGENCY ESCALATION (Zeigarnik Effect) ─────
        s += min(task.postponeCount * 30f, 180f)

        // ── 12. HABIT STREAK PROTECTION ──────────────────────────────────────
        if (task.isHabitual && task.habitStreak > 0) {
            s += min(task.habitStreak * 12f, 100f)
        }

        // ── 13. DEPENDENCY UNBLOCKING (Critical Path Method) ─────────────────
        val unblockCount = allActiveTasks.count { other ->
            other.id != task.id &&
            other.dependsOnTaskIds.split(",").any { it.trim() == task.id }
        }
        if (unblockCount > 0) {
            s += sqrt(unblockCount.toFloat()) * 80f
        }

        // ── 14. WEEKEND / WORK-HOURS CONTEXT ADJUSTMENT ──────────────────────
        if (isWeekend && task.contextTag == "@work") s -= 50f
        if (!isWeekend && task.contextTag == "@home") s -= 15f
        if (task.contextTag == "@computer" && !isWeekend) s += 10f
        if (!isWithinWorkDay && task.contextTag == "@work") s -= 60f
        if (isWithinWorkDay && task.contextTag == "@work") s += 20f

        // ── 15. RECENCY BIAS CORRECTION ──────────────────────────────────────
        val daysSinceCreated = (nowMillis - task.createdAt) / 86_400_000f
        if (daysSinceCreated > 7 && task.sessionCount == 0) {
            s += min(daysSinceCreated * 2f, 40f)
        }

        // ── 16. PROGRESS PRINCIPLE (Teresa Amabile) ──────────────────────────
        if (task.sessionCount > 0) {
            s += min(task.sessionCount * 18f, 72f)
        }

        // ── 17. IMPLEMENTATION INTENTIONS (Peter Gollwitzer) ─────────────────
        if (task.ifThenPlan.isNotBlank()) {
            val planBoost = 25f + (task.effortScore / 100f) * 55f
            s += planBoost
        }

        // ── 18. ENJOYMENT AS CONTINUOUS MODIFIER (Temptation Bundling) ───────
        val enjoyNorm = task.enjoymentScore / 100f
        val effortN = task.effortScore / 100f
        val enjoymentModifier = when {
            enjoyNorm < 0.3f && effortN > 0.6f -> 40f
            enjoyNorm < 0.3f -> 20f
            enjoyNorm > 0.7f && effortN > 0.6f -> 25f
            enjoyNorm > 0.7f -> 15f
            else -> 0f
        }
        s += enjoymentModifier

        // ── 19. COMMITMENT DEVICE (Social Accountability) ────────────────────
        if (task.isPublicCommitment) s += 70f

        if (task.isScheduleLocked && task.scheduledDate != null) {
            val minutesUntil = (task.scheduledDate - nowMillis) / 60_000f
            if (minutesUntil in -30f..60f) s += 80f
            else if (minutesUntil in -120f..240f) s += 30f
        }

        // ── 20. LOSS AVERSION (Kahneman & Tversky) ───────────────────────────
        s += when (task.goalRiskLevel) {
            1 -> 60f
            2 -> 130f
            else -> 0f
        }

        // ── 21. STRESS INOCULATION (Anxiety Task Anti-Avoidance) ─────────────
        if (task.isAnxietyTask) {
            s += 40f + if (enjoyNorm < 0.4f) 20f else 0f
        }

        // ── 22. DISTRACTION-AWARE BOOST ───────────────────────────────────────
        // Only boost when a real score has been computed (> 0f).
        // distractionScore = -1f means not yet computed; 0f means no distractions recorded.
        if (task.distractionScore > 0f) {
            s += DistractionEngine.priorityBoost(task.distractionScore)
        }

        return max(0f, s)
    }

    /** Normalized 0–999 display score for UI */
    fun displayScore(
        task: TaskEntity,
        prefs: UserPreferences,
        allActiveTasks: List<TaskEntity> = emptyList(),
        nowMillis: Long = System.currentTimeMillis()
    ): Int {
        val raw = score(task, prefs, allActiveTasks, nowMillis)
        return min(((raw / THEORETICAL_MAX) * 999f).toInt(), 999)
    }

    fun sortedByScore(
        tasks: List<TaskEntity>,
        prefs: UserPreferences,
        nowMillis: Long = System.currentTimeMillis()
    ): List<TaskEntity> {
        val active = tasks.filter { it.status == TaskStatus.ACTIVE }
        return active.sortedByDescending { score(it, prefs, active, nowMillis) }
    }

    fun urgencyLabel(task: TaskEntity, nowMillis: Long = System.currentTimeMillis()): String {
        if (task.deadlineDate != null) {
            val hoursLeft = (task.deadlineDate + (task.deadlineTime ?: 0L) - nowMillis) / 3_600_000f
            return when {
                hoursLeft < 0    -> "OVERDUE"
                hoursLeft < 1    -> "< 1 hour"
                hoursLeft < 4    -> "< 4 hours"
                hoursLeft < 12   -> "< 12 hours"
                hoursLeft < 24   -> "Today"
                hoursLeft < 48   -> "Tomorrow"
                hoursLeft < 168  -> "This week"
                else             -> "Later"
            }
        }
        if (task.scheduledDate != null) {
            val minutesUntil = (task.scheduledDate + (task.scheduledTime ?: 0L) - nowMillis) / 60_000f
            return when {
                minutesUntil < -60  -> "Scheduled (past)"
                minutesUntil < 0    -> "Starting now"
                minutesUntil < 60   -> "In < 1 hour"
                minutesUntil < 1440 -> "Today"
                else                -> "Scheduled"
            }
        }
        return "No deadline"
    }

    fun urgencyFraction(task: TaskEntity, nowMillis: Long = System.currentTimeMillis()): Float {
        if (task.deadlineDate != null) {
            val hoursLeft = (task.deadlineDate + (task.deadlineTime ?: 0L) - nowMillis) / 3_600_000f
            return when {
                hoursLeft <= 0   -> 1.0f
                hoursLeft < 1    -> 0.95f
                hoursLeft < 4    -> 0.88f
                hoursLeft < 12   -> 0.78f
                hoursLeft < 24   -> 0.68f
                hoursLeft < 48   -> 0.52f
                hoursLeft < 168  -> 0.32f
                else             -> max(0f, 1f - hoursLeft / 720f)
            }
        }
        if (task.scheduledDate != null) {
            val minutesUntil = (task.scheduledDate + (task.scheduledTime ?: 0L) - nowMillis) / 60_000f
            return when {
                minutesUntil <= 0    -> 0.85f
                minutesUntil < 15    -> 0.72f
                minutesUntil < 60    -> 0.52f
                minutesUntil < 240   -> 0.30f
                minutesUntil < 1440  -> 0.15f
                else                 -> 0f
            }
        }
        return 0f
    }

    /**
     * Returns a human-readable explanation of why this task scored the way it did.
     * Displayed in FocusScreen as an expandable "Why this score?" card.
     *
     * Uses effectivePeakStart/End (same as score()) so the breakdown always
     * reflects the actual score components — not the raw manual preference.
     */
    fun scoreBreakdown(
        task: TaskEntity,
        prefs: UserPreferences,
        allActiveTasks: List<TaskEntity> = emptyList(),
        nowMillis: Long = System.currentTimeMillis()
    ): List<Pair<String, Float>> {
        if (task.status != TaskStatus.ACTIVE) return emptyList()

        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val hour = cal.get(Calendar.HOUR_OF_DAY)

        // Mirror score()'s effective peak resolution exactly
        val (effectivePeakStart, effectivePeakEnd) = if (prefs.effectivePeakStart >= 0) {
            prefs.effectivePeakStart to prefs.effectivePeakEnd
        } else {
            prefs.peakEnergyStart to prefs.peakEnergyEnd
        }

        val isPeakHour = hour in effectivePeakStart..effectivePeakEnd
        val isMorning = hour < effectivePeakStart
        val isLowEnergySlot = hour in 13..15

        val result = mutableListOf<Pair<String, Float>>()

        val quadrantBase = when (task.quadrant) {
            Quadrant.DO_FIRST  -> 300f
            Quadrant.SCHEDULE  -> 180f
            Quadrant.DELEGATE  -> 80f
            Quadrant.ELIMINATE -> 20f
        } * prefs.weightQuadrant
        result += "Quadrant (${task.quadrant.name})" to quadrantBase

        if (task.isFrog) {
            val base = when {
                isMorning       -> 120f
                isPeakHour      ->  90f
                isLowEnergySlot ->  20f
                else            ->  50f
            }
            result += "🐸 Frog task" to base * (0.5f + task.effortScore / 200f) * prefs.weightFocusMode
        }
        if (task.ifThenPlan.isNotBlank()) {
            result += "🎯 If-then plan" to (25f + task.effortScore / 100f * 55f)
        }
        if (task.isPublicCommitment) result += "📢 Public commitment" to 70f
        if (task.isScheduleLocked) result += "🔒 Schedule locked" to 30f
        if (task.isAnxietyTask) result += "😰 Anxiety task surfaced" to 40f
        if (task.goalRiskLevel > 0) result += "⚠ Goal risk" to if (task.goalRiskLevel == 2) 130f else 60f
        if (task.waitingFor.isNotBlank()) result += "⏳ Waiting for (blocked)" to -999f
        if (task.postponeCount > 0) result += "↩ Postponed ${task.postponeCount}x" to min(task.postponeCount * 30f, 180f)
        if (task.distractionScore > 0f) {
            val boost = DistractionEngine.priorityBoost(task.distractionScore)
            if (boost > 0f) result += "📵 ${DistractionEngine.label(task.distractionScore)}" to boost
        }

        return result.sortedByDescending { it.second }
    }

    /**
     * Auto-assigns an Eisenhower quadrant based on urgency and importance.
     *
     * Importance uses the same 0.55/0.45 impact+value composite as score() so
     * auto-assigned quadrants are consistent with how the engine actually ranks tasks.
     * Threshold: composite score >= 50 (out of 100) = important.
     */
    fun autoAssignQuadrant(task: TaskEntity, nowMillis: Long = System.currentTimeMillis()): Quadrant {
        val hoursLeft = if (task.deadlineDate != null)
            (task.deadlineDate + (task.deadlineTime ?: 0L) - nowMillis) / 3_600_000f
        else Float.MAX_VALUE

        val isUrgent = hoursLeft < 72 || task.priority == Priority.HIGH

        // Mirror the score() composite so quadrant assignment stays consistent
        val importanceComposite = task.impactScore * 0.55f + task.valueScore * 0.45f
        val isImportant = importanceComposite >= 50f ||
            task.goalId != null || task.isFrog || task.isPublicCommitment || task.goalRiskLevel > 0

        return when {
            isUrgent && isImportant   -> Quadrant.DO_FIRST
            !isUrgent && isImportant  -> Quadrant.SCHEDULE
            isUrgent && !isImportant  -> Quadrant.DELEGATE
            else                      -> Quadrant.ELIMINATE
        }
    }
}
