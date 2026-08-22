package com.neuroflow.app.domain.engine

import com.neuroflow.app.data.local.UserPreferences
import com.neuroflow.app.data.local.entity.effectiveScheduleAnchorMillis
import com.neuroflow.app.data.local.entity.isRecurringWithAnchor
import com.neuroflow.app.data.local.entity.TaskEntity
import com.neuroflow.app.domain.model.EnergyLevel
import com.neuroflow.app.domain.model.Priority
import com.neuroflow.app.domain.model.Quadrant
import com.neuroflow.app.domain.model.TaskStatus
import com.neuroflow.app.domain.model.TaskType
import com.neuroflow.app.domain.scheduler.TagEnergyDemand
import com.neuroflow.app.domain.scheduler.TagPreferredWindow
import com.neuroflow.app.domain.scheduler.TaskTagSchedulingProfile
import com.neuroflow.app.domain.scheduler.TaskCategory
import com.neuroflow.app.domain.scheduler.determineCategory
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
 *  - peak context now resolves from profile windows (primary/secondary/tertiary)
 *    when available, instead of relying only on a single hour window
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

    private data class MinuteWindow(
        val startMinuteOfDay: Int,
        val durationMinutes: Int,
        val amplitude: Float
    )

    private data class PeakContext(
        val primaryStartHour: Int,
        val activePeakAmplitude: Float
    )

    private fun normalizeHour(hour: Int): Int {
        val normalized = hour % 24
        return if (normalized < 0) normalized + 24 else normalized
    }

    private fun normalizeMinute(minute: Int): Int {
        val normalized = minute % (24 * 60)
        return if (normalized < 0) normalized + (24 * 60) else normalized
    }

    private fun minuteOfDay(nowMillis: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    private fun isMinuteInWindow(minute: Int, startMinute: Int, durationMinutes: Int): Boolean {
        if (durationMinutes <= 0) return false
        val safeMinute = normalizeMinute(minute)
        val safeStart = normalizeMinute(startMinute)
        val endExclusive = safeStart + durationMinutes
        return if (endExclusive <= 24 * 60) {
            safeMinute in safeStart until endExclusive
        } else {
            safeMinute >= safeStart || safeMinute < (endExclusive % (24 * 60))
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

    private fun resolveProfileMinuteWindows(prefs: UserPreferences): List<MinuteWindow> {
        if (prefs.manualPeakProfileEnabled) {
            val anchorMinute = normalizeMinute(prefs.manualPeakAnchorMinuteOfDay)
            return listOf(
                MinuteWindow(
                    startMinuteOfDay = normalizeMinute(anchorMinute + prefs.manualPeakWindow1StartOffsetMinutes),
                    durationMinutes = prefs.manualPeakWindow1DurationMinutes.coerceIn(30, 360),
                    amplitude = prefs.manualPeakWindow1Amplitude.coerceIn(0.2f, 1f)
                ),
                MinuteWindow(
                    startMinuteOfDay = normalizeMinute(anchorMinute + prefs.manualPeakWindow2StartOffsetMinutes),
                    durationMinutes = prefs.manualPeakWindow2DurationMinutes.coerceIn(30, 360),
                    amplitude = prefs.manualPeakWindow2Amplitude.coerceIn(0.2f, 1f)
                ),
                MinuteWindow(
                    startMinuteOfDay = normalizeMinute(anchorMinute + prefs.manualPeakWindow3StartOffsetMinutes),
                    durationMinutes = prefs.manualPeakWindow3DurationMinutes.coerceIn(30, 360),
                    amplitude = prefs.manualPeakWindow3Amplitude.coerceIn(0.2f, 1f)
                )
            )
        }

        // Confidence-gated abstention disables adaptive peak windows so downstream
        // ranking falls back to stable manual/default hour logic.
        if (prefs.quizPeakEnabled && prefs.peakConfidenceAbstentionEnabled) {
            return emptyList()
        }

        if (prefs.quizPeakEnabled && prefs.effectivePeakMinuteOfDay in 0 until (24 * 60)) {
            val chronotype = parseChronotype(prefs.quizChronotype ?: prefs.manualChronotype)
            val defaultWindows = chronotype?.let { PeakEnergyEngine.defaultCircadianProfile(it).windows }
            if (!defaultWindows.isNullOrEmpty()) {
                val anchorMinute = normalizeMinute(prefs.effectivePeakMinuteOfDay)
                return defaultWindows.map { window ->
                    MinuteWindow(
                        startMinuteOfDay = normalizeMinute(anchorMinute + window.startMinuteOffset),
                        durationMinutes = window.durationMinutes.coerceIn(30, 360),
                        amplitude = window.amplitude.coerceIn(0.2f, 1f)
                    )
                }
            }
        }

        return emptyList()
    }

    private fun peakAmplitudeTier(amplitude: Float): Float {
        return when {
            amplitude >= 0.9f -> 1.0f
            amplitude >= 0.75f -> 0.75f
            amplitude >= 0.55f -> 0.5f
            else -> 0f
        }
    }

    private fun resolvePeakContext(prefs: UserPreferences, nowMillis: Long): PeakContext {
        val minuteWindows = resolveProfileMinuteWindows(prefs)
        if (minuteWindows.isNotEmpty()) {
            val nowMinute = minuteOfDay(nowMillis)
            val activeAmplitude = minuteWindows
                .filter { window ->
                    isMinuteInWindow(
                        minute = nowMinute,
                        startMinute = window.startMinuteOfDay,
                        durationMinutes = window.durationMinutes
                    )
                }
                .maxOfOrNull { it.amplitude }
                ?: 0f
            return PeakContext(
                primaryStartHour = (minuteWindows.first().startMinuteOfDay / 60).coerceIn(0, 23),
                activePeakAmplitude = activeAmplitude
            )
        }

        val hour = Calendar.getInstance().apply { timeInMillis = nowMillis }.get(Calendar.HOUR_OF_DAY)
        val useQuizPeak = prefs.quizPeakEnabled &&
            !prefs.peakConfidenceAbstentionEnabled &&
            prefs.effectivePeakStart >= 0 &&
            prefs.effectivePeakEnd >= 0
        val (effectivePeakStart, effectivePeakEnd) = if (useQuizPeak) {
            prefs.effectivePeakStart to prefs.effectivePeakEnd
        } else {
            prefs.peakEnergyStart to prefs.peakEnergyEnd
        }
        val inPeak = isHourInPeakWindow(hour, effectivePeakStart, effectivePeakEnd)
        return PeakContext(
            primaryStartHour = normalizeHour(effectivePeakStart),
            activePeakAmplitude = if (inPeak) 1f else 0f
        )
    }

    private fun isHourInPeakWindow(hour: Int, peakStartHour: Int, peakEndHour: Int): Boolean {
        val h = normalizeHour(hour)
        val start = normalizeHour(peakStartHour)
        val end = normalizeHour(peakEndHour)
        if (start == end) return true
        return if (start < end) {
            h in start until end
        } else {
            h >= start || h < end
        }
    }

    private fun tagSuitabilityScore(task: TaskEntity, hour: Int, isLowEnergySlot: Boolean): Float {
        val profiles = TaskTagSchedulingProfile.profilesFor(task.tags)
        if (profiles.isEmpty()) return 0f

        val aggregate = profiles.map { profile ->
            var score = 0f
            val tagEnergyMatch = when (profile.energyDemand) {
                TagEnergyDemand.HIGH -> when (task.energyLevel) {
                    EnergyLevel.HIGH -> 1f
                    EnergyLevel.MEDIUM -> 0.4f
                    EnergyLevel.LOW -> -0.4f
                }
                TagEnergyDemand.MEDIUM -> when (task.energyLevel) {
                    EnergyLevel.HIGH -> 0.6f
                    EnergyLevel.MEDIUM -> 1f
                    EnergyLevel.LOW -> 0.2f
                }
                TagEnergyDemand.LOW -> when (task.energyLevel) {
                    EnergyLevel.HIGH -> 0.2f
                    EnergyLevel.MEDIUM -> 0.8f
                    EnergyLevel.LOW -> 1f
                }
            }
            score += tagEnergyMatch * 14f

            if (profile.preferredContext != null && profile.preferredContext.equals(task.contextTag, ignoreCase = true)) {
                score += 8f
            }

            val windowMatch = when (profile.preferredWindow) {
                TagPreferredWindow.MORNING -> if (hour in 6..11) 1f else 0f
                TagPreferredWindow.MIDDAY -> if (hour in 12..15) 1f else 0f
                TagPreferredWindow.EVENING -> if (hour in 16..21) 1f else 0f
                TagPreferredWindow.FLEXIBLE -> 0.4f
            }
            score += windowMatch * 10f

            val durationHours = task.estimatedDurationMinutes / 60f
            val fragmentationPenalty = if (durationHours >= 2.0f) {
                (1f - profile.fragmentationTolerance).coerceIn(0f, 1f) * 12f
            } else {
                0f
            }
            score -= fragmentationPenalty

            if (isLowEnergySlot && profile.energyDemand == TagEnergyDemand.HIGH) {
                score -= 8f
            }
            score
        }

        return aggregate.average().toFloat()
    }

    private fun categorySuitabilityScore(task: TaskEntity, hour: Int, prefs: UserPreferences): Float {
        val category = task.determineCategory()

        val quizPeak = prefs.quizPeakEnabled &&
            !prefs.peakConfidenceAbstentionEnabled &&
            prefs.effectivePeakStart >= 0 &&
            prefs.effectivePeakEnd >= 0
        val (effectivePeakStart, effectivePeakEnd) = if (quizPeak) {
            prefs.effectivePeakStart to prefs.effectivePeakEnd
        } else {
            prefs.peakEnergyStart to prefs.peakEnergyEnd
        }
        val isPeakHour = isHourInPeakWindow(hour, effectivePeakStart, effectivePeakEnd)

        // Chronotype-aware windows — same logic as AutoSchedulingEngine.calculateCategoryFit()
        val wakeHour = prefs.wakeUpHour.coerceIn(0, 23)
        val morningWindowEnd = (wakeHour + 3).coerceAtMost(23)
        val isMorningWindow = hour in wakeHour..morningWindowEnd

        // Late-afternoon window relative to chronotype peak end
        val chronotypeEnd = when (prefs.quizChronotype ?: prefs.manualChronotype) {
            "DEFINITE_MORNING"  -> 11
            "MODERATE_MORNING"  -> 12
            "INTERMEDIATE"      -> 14
            "MODERATE_EVENING"  -> 18
            "DEFINITE_EVENING"  -> 21
            else                -> effectivePeakEnd.coerceIn(11, 21)
        }
        val afternoonWindowStart = (chronotypeEnd - 5).coerceAtLeast(12)
        val afternoonWindowEnd = (chronotypeEnd - 1).coerceAtLeast(afternoonWindowStart + 1)
        val isAfternoonWindow = hour in afternoonWindowStart..afternoonWindowEnd

        val sleepHour = prefs.sleepHour.coerceIn(18, 27)
        val windDownStart = (sleepHour - 2).coerceIn(18, 23)
        val isWindDown = hour >= windDownStart

        return when (category) {
            TaskCategory.MINDFULNESS -> {
                // Mindfulness: right after waking or wind-down. Avoid cognitive peak.
                when {
                    isMorningWindow -> 35f
                    isWindDown -> 30f
                    isPeakHour -> -25f
                    else -> 10f
                }
            }
            TaskCategory.EXERCISE -> {
                // Exercise: morning (cortisol peak) or late afternoon (body temp peak).
                when {
                    isMorningWindow -> 35f
                    isAfternoonWindow -> 30f
                    isPeakHour -> -20f  // Cognitive peak wasted on physical work
                    hour in morningWindowEnd..afternoonWindowStart -> 15f
                    else -> -15f
                }
            }
            TaskCategory.PHYSICAL -> {
                // Physical: manual tasks, errands. Avoid cognitive peaks.
                when {
                    isPeakHour -> -25f
                    hour in (wakeHour + 1)..(sleepHour.coerceAtMost(22) - 1) -> 30f
                    else -> 10f
                }
            }
            TaskCategory.ANALYTICAL, TaskCategory.HARD_WORK -> {
                // Analytical & Hard Work: High cognitive demand. Must be in peak.
                when {
                    isPeakHour -> 50f
                    hour in wakeHour..(wakeHour + 4) -> 25f  // Early morning still good
                    else -> -20f
                }
            }
            TaskCategory.CREATIVE -> {
                // Creative: post-peak or late afternoon.
                val isPostPeak = hour in chronotypeEnd..(chronotypeEnd + 3).coerceAtMost(23)
                when {
                    isPostPeak -> 40f
                    isAfternoonWindow -> 35f
                    isPeakHour -> 20f
                    else -> 10f
                }
            }
            TaskCategory.ROUTINE -> {
                // Routine/Admin: energy valleys. Post-lunch dip relative to wake time.
                val dipStart = (wakeHour + 6).coerceIn(12, 15)
                val dipEnd = (dipStart + 2).coerceAtMost(17)
                val inPostLunchDip = hour in dipStart..dipEnd
                when {
                    inPostLunchDip -> 45f
                    isPeakHour -> -20f
                    else -> 20f
                }
            }
            TaskCategory.FLEXIBLE -> 15f
        }
    }

    fun score(
        task: TaskEntity,
        prefs: UserPreferences,
        allActiveTasks: List<TaskEntity> = emptyList(),
        nowMillis: Long = System.currentTimeMillis()
    ): Float {
        if (task.status != TaskStatus.ACTIVE) return 0f

        // Hard block: tasks waiting on an external dependency are not actionable
        if (task.waitingFor.isNotBlank()) return 0f

        // Hard block: if task has unresolved dependencies, score near-zero
        val blockedByCount = if (task.dependsOnTaskIds.isNotBlank() && allActiveTasks.isNotEmpty()) {
            val depIds = task.dependsOnTaskIds.split(",").map { it.trim() }.filter { it.isNotBlank() }
            depIds.count { depId -> allActiveTasks.any { it.id == depId && it.status == TaskStatus.ACTIVE } }
        } else 0
        if (blockedByCount > 0) return 0f

        val recurringAnchorMode = task.isRecurringWithAnchor()
        val lockAnchorMs = task.effectiveScheduleAnchorMillis()
        if (task.isScheduleLocked && lockAnchorMs != null) {
            val minutesUntilAnchor = (lockAnchorMs - nowMillis) / 60_000f
            // Locked tasks remain visible in task lists, but carry zero ranking priority
            // until their scheduled window is near.
            if (minutesUntilAnchor > 10f) return 0f
        }

        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val peakContext = resolvePeakContext(prefs, nowMillis)
        val peakTierScale = peakAmplitudeTier(peakContext.activePeakAmplitude)
        val isPeakHour = peakTierScale > 0f
        val isMorning = hour < peakContext.primaryStartHour
        val isLowEnergySlot = hour in 13..15
        val isWeekend = dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
        val isWithinWorkDay = hour in prefs.workDayStart until prefs.workDayEnd
        val effortNorm = task.effortScore / 100f

        var s = 0f

        // ── 1. QUADRANT BASE (Eisenhower Matrix) ─────────────────────────────
        s += when (task.quadrant) {
            Quadrant.DO_FIRST  -> 300f
            Quadrant.SCHEDULE  -> 180f
            Quadrant.DELEGATE  -> 80f
            Quadrant.ELIMINATE -> 20f
        } * prefs.weightQuadrant

        // ── 2. DEADLINE PRESSURE (Temporal Motivation Theory) ────────────────
        // W1+W6 fix: Deadline urgency is now effort-weighted so trivial tasks with close
        // deadlines don't dominate high-effort important tasks. A 5-min review due tomorrow
        // no longer outranks a 5-hour research project due in 2 weeks.
        if (!recurringAnchorMode && task.deadlineDate != null) {
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
            // Effort-weighted multiplier: easy tasks (effort<30) get dampened urgency (0.6x),
            // hard tasks (effort>70) get amplified urgency (up to 1.25x).
            // This prevents "review spreadsheet due tomorrow" from dominating "deep research due in 2 weeks".
            val effortMultiplier = when {
                effortNorm < 0.3f -> 0.6f + effortNorm     // 0.6–0.9 for easy tasks
                effortNorm > 0.7f -> 1.0f + (effortNorm - 0.7f) * 0.83f  // 1.0–1.25 for hard tasks
                else -> 1.0f                                // 1.0 for medium tasks
            }
            s += deadlineScore * effortMultiplier * prefs.weightDeadlineUrgency
        }

        // ── 3. SCHEDULED TIME PROXIMITY (GTD next-action window) ─────────────
        if (!recurringAnchorMode && task.scheduledDate != null) {
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
            val schedWeight = if (task.deadlineDate != null) prefs.weightDeadlineUrgency else 1.0f
            s += schedScore * schedWeight
        }

        // ── 4. PRIORITY LEVEL ────────────────────────────────────────────────
        s += when (task.priority) {
            Priority.HIGH   -> 150f
            Priority.MEDIUM -> 75f
            Priority.LOW    -> 20f
        } * prefs.weightPriorityLevel

        // ── 5. STRATEGIC IMPACT + INTRINSIC VALUE (Self-Determination Theory) ─
        // Weighted composite: impact slightly more important for prioritization.
        // W1 fix: Add importance floor — high-impact tasks always get a minimum score boost
        // regardless of deadline distance, preventing them from being buried by trivial urgent tasks.
        val importanceScore = (task.impactScore * 0.55f + task.valueScore * 0.45f)
        s += importanceScore * prefs.weightImpact
        // Importance floor: tasks with composite ≥ 65 get a guaranteed minimum boost (30-60 pts)
        // that ensures they remain visible even when deadline-driven tasks dominate.
        if (importanceScore >= 65f) {
            val floorBoost = ((importanceScore - 65f) / 35f) * 60f  // 0–60 pts scaled
            s += floorBoost
        }

        // ── 6. EFFORT × CONTEXT (BJ Fogg + Eat the Frog) ────────────────────
        // weightFocusMode governs all focus-related boosts (effort + frog below)
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
                EnergyLevel.HIGH   ->  70f * peakTierScale
                EnergyLevel.MEDIUM ->  20f * peakTierScale
                EnergyLevel.LOW    -> -35f * peakTierScale
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
                isPeakHour      ->  60f * peakTierScale
                isMorning       ->  30f
                isLowEnergySlot -> -25f
                else            ->   5f
            }
            TaskType.CREATIVE -> when {
                hour in 10..11  ->  55f
                hour in 16..18  ->  45f
                isPeakHour      ->  20f * peakTierScale
                isLowEnergySlot -> -10f
                else            ->   0f
            }
            TaskType.ADMIN -> when {
                isLowEnergySlot ->  50f
                isPeakHour      -> -15f * peakTierScale
                else            ->  10f
            }
            TaskType.PHYSICAL -> when {
                isMorning       ->  30f
                isPeakHour      ->  20f * peakTierScale
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
                isPeakHour      ->  90f * peakTierScale
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

        // ── 15. TAG SUITABILITY (scheduler-aligned additive fit) ─────────────
        s += tagSuitabilityScore(task, hour, isLowEnergySlot)

        // ── 15.5 CATEGORY SUITABILITY (scheduler-aligned additive fit) ───────
        s += categorySuitabilityScore(task, hour, prefs)

        // ── 16. RECENCY BIAS CORRECTION ──────────────────────────────────────
        val daysSinceCreated = (nowMillis - task.createdAt) / 86_400_000f
        if (daysSinceCreated > 7 && task.sessionCount == 0) {
            s += min(daysSinceCreated * 2f, 40f)
        }

        // ── 17. PROGRESS PRINCIPLE (Teresa Amabile) ──────────────────────────
        if (task.sessionCount > 0) {
            s += min(task.sessionCount * 18f, 72f)
        }

        // ── 18. IMPLEMENTATION INTENTIONS (Peter Gollwitzer) ─────────────────
        if (task.ifThenPlan.isNotBlank()) {
            val planBoost = 25f + (task.effortScore / 100f) * 55f
            s += planBoost
        }

        // ── 19. ENJOYMENT AS CONTINUOUS MODIFIER (Temptation Bundling) ───────
        val enjoyNorm = task.enjoymentScore / 100f
        val effortN = task.effortScore / 100f
        val enjoymentModifier = when {
            enjoyNorm < 0.3f && effortN > 0.6f -> 40f
            enjoyNorm < 0.3f -> 20f
            enjoyNorm > 0.7f && effortN > 0.6f -> 20f
            enjoyNorm > 0.7f -> 15f
            else -> 0f
        }
        s += enjoymentModifier

        // ── 20. COMMITMENT DEVICE (Social Accountability) ────────────────────
        if (task.isPublicCommitment) s += 70f

        if (task.isScheduleLocked && lockAnchorMs != null) {
            val minutesUntil = (lockAnchorMs - nowMillis) / 60_000f
            if (minutesUntil >= -30f && minutesUntil < 10f) s += 160f
            else if (minutesUntil >= -120f && minutesUntil < 60f) s += 80f
            else if (minutesUntil >= -240f && minutesUntil < 120f) s += 30f
        }

        // ── 21. LOSS AVERSION (Kahneman & Tversky) ───────────────────────────
        s += when (task.goalRiskLevel) {
            1 -> 60f
            2 -> 130f
            else -> 0f
        }

        // ── 22. STRESS INOCULATION (Anxiety Task Anti-Avoidance) ─────────────
        if (task.isAnxietyTask) {
            s += 40f + if (enjoyNorm < 0.4f) 20f else 0f
        }

        // ── 23. DISTRACTION-AWARE BOOST ───────────────────────────────────────
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
        if (!task.isRecurringWithAnchor() && task.deadlineDate != null) {
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
        val anchorMs = task.effectiveScheduleAnchorMillis()
        if (anchorMs != null) {
            val minutesUntil = (anchorMs - nowMillis) / 60_000f
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
        if (!task.isRecurringWithAnchor() && task.deadlineDate != null) {
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
        val anchorMs = task.effectiveScheduleAnchorMillis()
        if (anchorMs != null) {
            val minutesUntil = (anchorMs - nowMillis) / 60_000f
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

        // Blocked tasks score 0 — show a single explanatory entry instead of a full breakdown
        if (task.waitingFor.isNotBlank()) {
            return listOf("⏳ Waiting for external dependency (blocked)" to 0f)
        }
        val blockedByCount = if (task.dependsOnTaskIds.isNotBlank() && allActiveTasks.isNotEmpty()) {
            val depIds = task.dependsOnTaskIds.split(",").map { it.trim() }.filter { it.isNotBlank() }
            depIds.count { depId -> allActiveTasks.any { it.id == depId && it.status == TaskStatus.ACTIVE } }
        } else 0
        if (blockedByCount > 0) {
            return listOf("🔗 Blocked by $blockedByCount incomplete dependency task(s)" to 0f)
        }

        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val peakContext = resolvePeakContext(prefs, nowMillis)
        val peakTierScale = peakAmplitudeTier(peakContext.activePeakAmplitude)
        val isPeakHour = peakTierScale > 0f
        val isMorning = hour < peakContext.primaryStartHour
        val isLowEnergySlot = hour in 13..15
        val isWeekend = dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
        val isWithinWorkDay = hour in prefs.workDayStart until prefs.workDayEnd

        val result = mutableListOf<Pair<String, Float>>()
        val effortNorm = task.effortScore / 100f
        val effortN = effortNorm
        val enjoyNorm = task.enjoymentScore / 100f

        val quadrantBase = when (task.quadrant) {
            Quadrant.DO_FIRST  -> 300f
            Quadrant.SCHEDULE  -> 180f
            Quadrant.DELEGATE  -> 80f
            Quadrant.ELIMINATE -> 20f
        } * prefs.weightQuadrant
        result += "Quadrant (${task.quadrant.name})" to quadrantBase

        if (!task.isRecurringWithAnchor() && task.deadlineDate != null) {
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
            val effortMultiplier = when {
                effortNorm < 0.3f -> 0.6f + effortNorm
                effortNorm > 0.7f -> 1.0f + (effortNorm - 0.7f) * 0.83f
                else -> 1.0f
            }
            result += "Deadline pressure" to deadlineScore * effortMultiplier * prefs.weightDeadlineUrgency
        }

        if (!task.isRecurringWithAnchor() && task.scheduledDate != null) {
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
            val schedWeight = if (task.deadlineDate != null) prefs.weightDeadlineUrgency else 1.0f
            result += "Scheduled proximity" to schedScore * schedWeight
        }

        val priorityScore = when (task.priority) {
            Priority.HIGH   -> 150f
            Priority.MEDIUM -> 75f
            Priority.LOW    -> 20f
        } * prefs.weightPriorityLevel
        result += "Priority level (${task.priority.name})" to priorityScore

        val importanceScore = (task.impactScore * 0.55f + task.valueScore * 0.45f)
        result += "Strategic impact & value" to importanceScore * prefs.weightImpact
        if (importanceScore >= 65f) {
            result += "High-impact floor" to ((importanceScore - 65f) / 35f) * 60f
        }

        val effortBoost = when {
            effortNorm < 0.3f -> (1f - effortNorm) * 60f
            effortNorm > 0.7f && (isPeakHour || isMorning) -> effortNorm * 55f
            effortNorm > 0.7f -> -(effortNorm * 20f)
            else -> 0f
        }
        val weightedEffort = effortBoost * prefs.weightFocusMode
        if (weightedEffort != 0f) {
            result += "Effort-matching nudge" to weightedEffort
        }

        if (task.estimatedDurationMinutes in 1..90) {
            val durationScore = max(0f, 40f - task.estimatedDurationMinutes * 0.3f) * prefs.weightDuration
            if (durationScore != 0f) {
                result += "Quick-win duration boost" to durationScore
            }
        }

        val energyBonus = when {
            isPeakHour -> when (task.energyLevel) {
                EnergyLevel.HIGH   ->  70f * peakTierScale
                EnergyLevel.MEDIUM ->  20f * peakTierScale
                EnergyLevel.LOW    -> -35f * peakTierScale
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
        if (energyBonus != 0f) {
            result += "Energy matching" to energyBonus
        }

        val circadianBonus = when (task.taskType) {
            TaskType.ANALYTICAL -> when {
                isPeakHour      ->  60f * peakTierScale
                isMorning       ->  30f
                isLowEnergySlot -> -25f
                else            ->   5f
            }
            TaskType.CREATIVE -> when {
                hour in 10..11  ->  55f
                hour in 16..18  ->  45f
                isPeakHour      ->  20f * peakTierScale
                isLowEnergySlot -> -10f
                else            ->   0f
            }
            TaskType.ADMIN -> when {
                isLowEnergySlot ->  50f
                isPeakHour      -> -15f * peakTierScale
                else            ->  10f
            }
            TaskType.PHYSICAL -> when {
                isMorning       ->  30f
                isPeakHour      ->  20f * peakTierScale
                isLowEnergySlot -> -10f
                else            ->  10f
            }
        }
        if (circadianBonus != 0f) {
            result += "Circadian task matching" to circadianBonus
        }

        if (task.isFrog) {
            val base = when {
                isMorning       -> 120f
                isPeakHour      ->  90f * peakTierScale
                isLowEnergySlot ->  20f
                else            ->  50f
            }
            result += "🐸 Frog task" to base * (0.5f + task.effortScore / 200f) * prefs.weightFocusMode
        }

        if (task.postponeCount > 0) {
            result += "↩ Postponed ${task.postponeCount}x" to min(task.postponeCount * 30f, 180f)
        }

        if (task.isHabitual && task.habitStreak > 0) {
            result += "Habit streak protection" to min(task.habitStreak * 12f, 100f)
        }

        val unblockCount = allActiveTasks.count { other ->
            other.id != task.id &&
            other.dependsOnTaskIds.split(",").any { it.trim() == task.id }
        }
        if (unblockCount > 0) {
            result += "Dependency unblocker" to sqrt(unblockCount.toFloat()) * 80f
        }

        var contextAdjust = 0f
        if (isWeekend && task.contextTag == "@work") contextAdjust -= 50f
        if (!isWeekend && task.contextTag == "@home") contextAdjust -= 15f
        if (task.contextTag == "@computer" && !isWeekend) contextAdjust += 10f
        if (!isWithinWorkDay && task.contextTag == "@work") contextAdjust -= 60f
        if (isWithinWorkDay && task.contextTag == "@work") contextAdjust += 20f
        if (contextAdjust != 0f) {
            result += "Context alignment (${task.contextTag})" to contextAdjust
        }

        val tagFit = tagSuitabilityScore(task, hour, isLowEnergySlot)
        if (tagFit != 0f) {
            result += "🏷 Tag fit" to tagFit
        }

        val categoryFit = categorySuitabilityScore(task, hour, prefs)
        if (categoryFit != 0f) {
            result += "🏷 Category suitability" to categoryFit
        }

        val daysSinceCreated = (nowMillis - task.createdAt) / 86_400_000f
        if (daysSinceCreated > 7 && task.sessionCount == 0) {
            result += "Anti-staleness surfacing" to min(daysSinceCreated * 2f, 40f)
        }

        if (task.sessionCount > 0) {
            result += "Progress momentum" to min(task.sessionCount * 18f, 72f)
        }

        if (task.ifThenPlan.isNotBlank()) {
            result += "🎯 If-then plan" to (25f + task.effortScore / 100f * 55f)
        }

        val enjoymentModifier = when {
            enjoyNorm < 0.3f && effortN > 0.6f -> 40f
            enjoyNorm < 0.3f -> 20f
            enjoyNorm > 0.7f && effortN > 0.6f -> 20f
            enjoyNorm > 0.7f -> 15f
            else -> 0f
        }
        if (enjoymentModifier != 0f) {
            result += "Enjoyment motivation" to enjoymentModifier
        }

        if (task.isPublicCommitment) {
            result += "📢 Public commitment" to 70f
        }

        val lockAnchorMs = task.effectiveScheduleAnchorMillis()
        if (task.isScheduleLocked && lockAnchorMs != null) {
            val minutesUntil = (lockAnchorMs - nowMillis) / 60_000f
            val lockScore = when {
                minutesUntil >= -30f && minutesUntil < 10f -> 160f
                minutesUntil >= -120f && minutesUntil < 60f -> 80f
                minutesUntil >= -240f && minutesUntil < 120f -> 30f
                else -> 0f
            }
            if (lockScore != 0f) {
                result += "🔒 Schedule locked" to lockScore
            }
        }

        if (task.goalRiskLevel > 0) {
            result += "⚠ Goal risk" to if (task.goalRiskLevel == 2) 130f else 60f
        }

        if (task.isAnxietyTask) {
            result += "😰 Anxiety task surfaced" to (40f + if (enjoyNorm < 0.4f) 20f else 0f)
        }

        if (task.distractionScore > 0f) {
            val boost = DistractionEngine.priorityBoost(task.distractionScore)
            if (boost > 0f) {
                result += "📵 ${DistractionEngine.label(task.distractionScore)}" to boost
            }
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
        // Blocked tasks (waiting for external dependency) are not actionable — put in SCHEDULE
        // so they don't surface as DO_FIRST when the user can't act on them yet.
        if (task.waitingFor.isNotBlank()) return Quadrant.SCHEDULE

        val hoursLeft = if (task.deadlineDate != null)
            (task.deadlineDate + (task.deadlineTime ?: 0L) - nowMillis) / 3_600_000f
        else Float.MAX_VALUE

        // W10 fix: Effort-scaled urgency threshold — hard tasks need more lead time.
        // A 30-minute admin task is urgent at 72 hours, but a 5-hour research project
        // should be flagged urgent at 120+ hours to leave time for proper work.
        val effortN = task.effortScore / 100f
        val urgencyHorizon = when {
            effortN >= 0.8f -> 120f  // Hard tasks: urgent within 5 days
            effortN >= 0.6f -> 96f   // Medium-hard tasks: urgent within 4 days
            else -> 72f              // Easy tasks: original 3-day threshold
        }
        val isUrgent = hoursLeft < urgencyHorizon || task.priority == Priority.HIGH

        // W10 fix: Graduated importance with buffer zone (45-55 instead of hard 50).
        // Tasks scoring 45-55 get a probability-weighted classification instead of
        // a 2-point difference changing quadrant.
        val importanceComposite = task.impactScore * 0.55f + task.valueScore * 0.45f
        val isImportant = when {
            importanceComposite >= 55f -> true           // Clearly important
            importanceComposite >= 45f -> {              // Buffer zone: use supporting signals
                task.goalId != null || task.isFrog || task.isPublicCommitment ||
                    task.goalRiskLevel > 0 || task.effortScore >= 70
            }
            else -> task.goalId != null || task.isFrog || task.isPublicCommitment || task.goalRiskLevel > 0
        }

        return when {
            isUrgent && isImportant   -> Quadrant.DO_FIRST
            !isUrgent && isImportant  -> Quadrant.SCHEDULE
            isUrgent && !isImportant  -> Quadrant.DELEGATE
            else                      -> Quadrant.ELIMINATE
        }
    }
}
