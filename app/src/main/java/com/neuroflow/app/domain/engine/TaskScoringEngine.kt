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
 * NeuroFlow Priority Scoring Engine v2
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
 */
object TaskScoringEngine {

    // Calibrated max — sum of all components at theoretical maximum
    // Recalibrated so scores spread across 0–1000 meaningfully
    private const val THEORETICAL_MAX = 1800f

    fun score(
        task: TaskEntity,
        prefs: UserPreferences,
        allActiveTasks: List<TaskEntity> = emptyList(),
        nowMillis: Long = System.currentTimeMillis()
    ): Float {
        if (task.status != TaskStatus.ACTIVE) return 0f

        // Hard block: if task has unresolved dependencies, score near-zero
        // (can't do it yet — don't surface it)
        val blockedByCount = if (task.dependsOnTaskIds.isNotBlank() && allActiveTasks.isNotEmpty()) {
            val depIds = task.dependsOnTaskIds.split(",").map { it.trim() }.filter { it.isNotBlank() }
            depIds.count { depId -> allActiveTasks.any { it.id == depId && it.status == TaskStatus.ACTIVE } }
        } else 0
        if (blockedByCount > 0) return max(0f, 5f * (1f / blockedByCount))

        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val isPeakHour = hour in prefs.peakEnergyStart..prefs.peakEnergyEnd
        val isMorning = hour < prefs.peakEnergyStart
        val isLowEnergySlot = hour in 13..15  // post-lunch dip
        val isWeekend = dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
        val isWithinWorkDay = hour in prefs.workDayStart until prefs.workDayEnd

        var s = 0f

        // ── 1. QUADRANT BASE (Eisenhower Matrix) ─────────────────────────────
        // Foundation of the score — quadrant is the user's explicit importance signal
        s += when (task.quadrant) {
            Quadrant.DO_FIRST  -> 300f
            Quadrant.SCHEDULE  -> 180f
            Quadrant.DELEGATE  -> 80f
            Quadrant.ELIMINATE -> 20f
        } * prefs.weightQuadrant

        // ── 2. DEADLINE PRESSURE (Temporal Motivation Theory) ────────────────
        // Hyperbolic discounting: value = importance / (1 + k * delay)
        // Exponential urgency curve as deadline approaches
        if (task.deadlineDate != null) {
            val deadlineMs = task.deadlineDate + (task.deadlineTime ?: 0L)
            val hoursLeft = (deadlineMs - nowMillis) / 3_600_000f
            val deadlineScore = when {
                hoursLeft < 0    -> 500f  // overdue — maximum urgency
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
        // Tasks scheduled for right now get a strong boost
        if (task.scheduledDate != null) {
            val schedMs = task.scheduledDate + (task.scheduledTime ?: 0L)
            val minutesUntil = (schedMs - nowMillis) / 60_000f
            val schedScore = when {
                minutesUntil < -120 -> 5f   // long past — probably missed
                minutesUntil < -30  -> 40f
                minutesUntil < 0    -> 130f  // happening now
                minutesUntil < 15   -> 170f  // imminent
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
        // Impact = external/strategic value. Value = intrinsic motivation.
        // Both matter — pure impact with no intrinsic value leads to burnout.
        // Weighted composite: impact slightly more important for prioritization.
        val importanceScore = (task.impactScore * 0.55f + task.valueScore * 0.45f)
        s += importanceScore * prefs.weightImpact

        // ── 6. EFFORT × CONTEXT (BJ Fogg + Eat the Frog) ────────────────────
        // Low effort = quick win → boost anytime (dopamine momentum)
        // High effort = frog → boost only in peak/morning (cognitive resources)
        // Medium effort = neutral
        val effortNorm = task.effortScore / 100f  // 0.0 to 1.0
        val effortBoost = when {
            effortNorm < 0.3f -> (1f - effortNorm) * 60f  // quick win: up to +60
            effortNorm > 0.7f && (isPeakHour || isMorning) -> effortNorm * 55f  // hard task in good slot
            effortNorm > 0.7f -> -(effortNorm * 20f)  // hard task in wrong slot — slight penalty
            else -> 0f
        }
        s += effortBoost * prefs.weightFocusMode

        // ── 7. DURATION MOMENTUM ─────────────────────────────────────────────
        // Shorter tasks get a small boost — faster dopamine hit, clears the list
        // But very long tasks aren't penalized heavily — just not boosted
        if (task.estimatedDurationMinutes in 1..90) {
            s += max(0f, 40f - task.estimatedDurationMinutes * 0.3f) * prefs.weightDuration
        }

        // ── 8. ENERGY MATCHING (Cognitive Load Theory) ───────────────────────
        // Continuous penalty/boost based on energy mismatch, not just binary
        val energyBonus = when {
            isPeakHour -> when (task.energyLevel) {
                EnergyLevel.HIGH   ->  70f   // perfect match
                EnergyLevel.MEDIUM ->  20f
                EnergyLevel.LOW    -> -35f   // wasting peak time on low-energy task
            }
            isLowEnergySlot -> when (task.energyLevel) {
                EnergyLevel.LOW    ->  70f   // perfect match
                EnergyLevel.MEDIUM ->  15f
                EnergyLevel.HIGH   -> -35f   // can't do high-energy work when drained
            }
            isMorning -> when (task.energyLevel) {
                EnergyLevel.HIGH   ->  30f   // morning ramp-up
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
        // Research: analytical peaks in morning, creative in late morning/afternoon,
        // admin best in post-lunch dip, physical flexible
        val circadianBonus = when (task.taskType) {
            TaskType.ANALYTICAL -> when {
                isPeakHour   ->  60f
                isMorning    ->  30f
                isLowEnergySlot -> -25f
                else         ->   5f
            }
            TaskType.CREATIVE -> when {
                hour in 10..11 ->  55f
                hour in 16..18 ->  45f
                isPeakHour     ->  20f
                isLowEnergySlot -> -10f
                else           ->   0f
            }
            TaskType.ADMIN -> when {
                isLowEnergySlot ->  50f
                isPeakHour      -> -15f  // don't waste peak on admin
                else            ->  10f
            }
            TaskType.PHYSICAL -> when {
                isMorning    ->  30f
                isPeakHour   ->  20f
                isLowEnergySlot -> -10f
                else         ->  10f
            }
        }
        s += circadianBonus

        // ── 10. FROG BOOST (Eat the Frog — Brian Tracy) ──────────────────────
        // The hardest, most-dreaded task should be done FIRST.
        // Massive boost before peak hours, strong during peak, minimal after.
        // Combined with effort score for accuracy — a "frog" that's actually easy
        // gets a smaller boost.
        if (task.isFrog) {
            val frogBase = when {
                isMorning    -> 120f  // pre-peak: ideal frog time
                isPeakHour   ->  90f  // still good
                isLowEnergySlot ->  20f  // too late, but still surface it
                else         ->  50f
            }
            // Scale by effort — a hard frog deserves more boost than an easy one
            val effortMultiplier = 0.5f + (task.effortScore / 100f) * 0.5f  // 0.5x to 1.0x
            s += frogBase * effortMultiplier
        }

        // ── 11. POSTPONE PENALTY → URGENCY ESCALATION (Zeigarnik Effect) ─────
        // Each skip increases the psychological nag. Capped to prevent runaway.
        s += min(task.postponeCount * 30f, 180f)

        // ── 12. HABIT STREAK PROTECTION ──────────────────────────────────────
        // Streaks have compounding value — protect them
        if (task.isHabitual && task.habitStreak > 0) {
            s += min(task.habitStreak * 12f, 100f)
        }

        // ── 13. DEPENDENCY UNBLOCKING (Critical Path Method) ─────────────────
        // If finishing THIS task unblocks N other tasks, it's a force multiplier.
        // Do it first to unlock the critical path.
        val unblockCount = allActiveTasks.count { other ->
            other.id != task.id &&
            other.dependsOnTaskIds.split(",").any { it.trim() == task.id }
        }
        if (unblockCount > 0) {
            // Diminishing returns: sqrt so 4 blocked tasks isn't 4x as important as 1
            s += sqrt(unblockCount.toFloat()) * 80f
        }

        // ── 14. WEEKEND CONTEXT ADJUSTMENT ───────────────────────────────────
        if (isWeekend && task.contextTag == "@work") s -= 50f
        if (!isWeekend && task.contextTag == "@home") s -= 15f
        // Bonus for context match (e.g., @phone tasks when you have time to call)
        if (task.contextTag == "@computer" && !isWeekend) s += 10f

        // ── 14b. WORK HOURS ADJUSTMENT ────────────────────────────────────────
        // Penalize work-tagged tasks outside the user's configured work window
        if (!isWithinWorkDay && task.contextTag == "@work") s -= 60f
        // Boost work tasks during work hours
        if (isWithinWorkDay && task.contextTag == "@work") s += 20f

        // ── 15. RECENCY BIAS CORRECTION ──────────────────────────────────────
        // Old tasks that haven't been started yet get a nudge
        val daysSinceCreated = (nowMillis - task.createdAt) / 86_400_000f
        if (daysSinceCreated > 7 && task.sessionCount == 0) {
            s += min(daysSinceCreated * 2f, 40f)  // grows with age, capped at 40
        }

        // ── 16. PROGRESS PRINCIPLE (Teresa Amabile) ──────────────────────────
        // Started tasks have momentum — finishing them feels good
        // Also: partially done tasks have sunk cost that motivates completion
        if (task.sessionCount > 0) {
            s += min(task.sessionCount * 18f, 72f)
        }

        // ── 17. IMPLEMENTATION INTENTIONS (Peter Gollwitzer) ─────────────────
        // "When X happens, I will do Y" — 2-3x completion rate improvement.
        // Scale boost by effort: a hard task with a concrete plan deserves more
        // boost than an easy task (which you'd do anyway).
        if (task.ifThenPlan.isNotBlank()) {
            val planBoost = 25f + (task.effortScore / 100f) * 55f  // 25 to 80 pts
            s += planBoost
        }

        // ── 18. ENJOYMENT AS CONTINUOUS MODIFIER (Temptation Bundling) ───────
        // Enjoyment affects activation energy — how hard it is to START.
        // Low enjoyment + high effort = procrastination trap → surface it more
        // High enjoyment = easier to start → slight boost (you'll actually do it)
        // This is continuous, not just at extremes
        val enjoyNorm = task.enjoymentScore / 100f  // 0.0 to 1.0
        val effortN = task.effortScore / 100f
        val enjoymentModifier = when {
            enjoyNorm < 0.3f && effortN > 0.6f -> 40f   // dread + hard = procrastination trap
            enjoyNorm < 0.3f -> 20f                       // dread alone — surface it
            enjoyNorm > 0.7f && effortN > 0.6f -> 25f   // love it + hard = temptation bundle
            enjoyNorm > 0.7f -> 15f                       // love it — slight boost
            else -> 0f
        }
        s += enjoymentModifier

        // ── 19. COMMITMENT DEVICE (Social Accountability) ────────────────────
        // Told someone you'd do this — social cost of failure is real
        if (task.isPublicCommitment) s += 70f

        // Schedule lock = explicit commitment to a time slot — surface it when that time comes
        if (task.isScheduleLocked && task.scheduledDate != null) {
            val minutesUntil = (task.scheduledDate - nowMillis) / 60_000f
            if (minutesUntil in -30f..60f) s += 80f  // strong boost when the locked slot is now/imminent
            else if (minutesUntil in -120f..240f) s += 30f
        }

        // ── 20. LOSS AVERSION (Kahneman & Tversky) ───────────────────────────
        // Fear of losing > hope of gaining. Goal at risk = strong motivator.
        s += when (task.goalRiskLevel) {
            1 -> 60f    // goal at risk
            2 -> 130f   // goal critical — loss aversion at maximum
            else -> 0f
        }

        // ── 21. STRESS INOCULATION (Anxiety Task Anti-Avoidance) ─────────────
        // Anxiety tasks are avoided → they spiral → surface them early
        // Combined with enjoyment: an anxiety task you also dread gets extra push
        if (task.isAnxietyTask) {
            val anxietyBoost = 40f + if (enjoyNorm < 0.4f) 20f else 0f
            s += anxietyBoost
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
        val isPeakHour = hour in prefs.peakEnergyStart..prefs.peakEnergyEnd
        val isMorning = hour < prefs.peakEnergyStart
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
                isPeakHour      -> 90f
                isLowEnergySlot -> 20f
                else            -> 50f
            }
            result += "🐸 Frog task" to base * (0.5f + task.effortScore / 200f)
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

        return result.sortedByDescending { it.second }
    }

    fun autoAssignQuadrant(task: TaskEntity, nowMillis: Long = System.currentTimeMillis()): Quadrant {
        val hoursLeft = if (task.deadlineDate != null)
            (task.deadlineDate + (task.deadlineTime ?: 0L) - nowMillis) / 3_600_000f
        else Float.MAX_VALUE

        val isUrgent = hoursLeft < 72 || task.priority == Priority.HIGH
        val isImportant = task.impactScore >= 50 || task.valueScore >= 50 ||
            task.goalId != null || task.isFrog || task.isPublicCommitment || task.goalRiskLevel > 0

        return when {
            isUrgent && isImportant   -> Quadrant.DO_FIRST
            !isUrgent && isImportant  -> Quadrant.SCHEDULE
            isUrgent && !isImportant  -> Quadrant.DELEGATE
            else                      -> Quadrant.ELIMINATE
        }
    }
}
