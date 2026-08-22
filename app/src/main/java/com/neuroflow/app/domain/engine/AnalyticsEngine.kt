package com.neuroflow.app.domain.engine

import com.neuroflow.app.data.local.UserPreferences
import com.neuroflow.app.data.local.entity.TaskEntity
import com.neuroflow.app.data.local.entity.TimeSessionEntity
import com.neuroflow.app.domain.model.Priority
import com.neuroflow.app.domain.model.Quadrant
import com.neuroflow.app.domain.model.TaskStatus
import java.util.Calendar
import kotlin.math.abs

object AnalyticsEngine {

    // ── MAPE / SMAPE ─────────────────────────────────────────────────────────

    fun computeMape(estimated: Float, actual: Float): Float {
        if (estimated == 0f) return 0f
        return (abs(actual - estimated) / estimated) * 100f
    }

    fun computeSmape(estimated: Float, actual: Float): Float {
        val denom = (estimated + actual) / 2f
        if (denom == 0f) return 0f
        return (abs(actual - estimated) / denom) * 100f
    }

    fun computeWeightedMape(tasks: List<TaskEntity>): Float {
        val valid = tasks.filter { it.estimatedDurationMinutes > 0 && it.actualDurationMinutes != null }
        if (valid.isEmpty()) return 0f
        val totalWeight = valid.sumOf { it.estimatedDurationMinutes }.toFloat()
        val weightedSum = valid.sumOf {
            (computeMape(it.estimatedDurationMinutes.toFloat(), it.actualDurationMinutes!!) * it.estimatedDurationMinutes).toDouble()
        }
        return (weightedSum / totalWeight).toFloat()
    }

    fun computeWeightedSmape(tasks: List<TaskEntity>): Float {
        val valid = tasks.filter { it.estimatedDurationMinutes > 0 && it.actualDurationMinutes != null }
        if (valid.isEmpty()) return 0f
        val totalWeight = valid.sumOf { it.estimatedDurationMinutes }.toFloat()
        val weightedSum = valid.sumOf {
            (computeSmape(it.estimatedDurationMinutes.toFloat(), it.actualDurationMinutes!!) * it.estimatedDurationMinutes).toDouble()
        }
        return (weightedSum / totalWeight).toFloat()
    }

    fun mapeGrade(mape: Float): String = when {
        mape == 0f  -> "No data yet — complete tasks with time tracking to see your accuracy"
        mape < 10f  -> "✨ Excellent time estimation!"
        mape < 25f  -> "👍 Good estimation, minor drift"
        mape < 50f  -> "⚠ Moderate estimation error — try breaking tasks into smaller chunks"
        else        -> "🚨 High estimation error — recalibrate your estimates"
    }

    // ── SESSION HELPERS ───────────────────────────────────────────────────────

    /** Returns start-of-day millis for a given timestamp */
    private fun startOfDay(millis: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    private fun normalizeHour(hour: Int): Int {
        val normalized = hour % 24
        return if (normalized < 0) normalized + 24 else normalized
    }

    private fun isHourInPeakWindow(hour: Int, peakStartHour: Int, peakEndHour: Int): Boolean {
        val h = normalizeHour(hour)
        val start = normalizeHour(peakStartHour)
        val end = normalizeHour(peakEndHour)
        return if (start <= end) {
            h in start..end
        } else {
            h >= start || h <= end
        }
    }

    private fun peakWindowsForDay(dayStartMillis: Long, peakStartHour: Int, peakEndHour: Int): List<Pair<Long, Long>> {
        val hourMs = 3_600_000L
        val start = normalizeHour(peakStartHour)
        val end = normalizeHour(peakEndHour)

        return if (start <= end) {
            listOf(dayStartMillis + start * hourMs to dayStartMillis + (end + 1) * hourMs)
        } else {
            // Night window: e.g. 22:00 to 02:00
            // Day 1: 22:00 to 24:00 (dayStart + 22h to dayStart + 24h)
            // Day 2: 00:00 to 03:00 (dayStart to dayStart + 3h)
            listOf(
                dayStartMillis + start * hourMs to dayStartMillis + 24 * hourMs,
                dayStartMillis to dayStartMillis + (end + 1) * hourMs
            )
        }
    }

    // ── 7-DAY FOCUS TREND ────────────────────────────────────────────────────

    /** Returns list of (dayLabel, focusMinutes) for the last 7 days, oldest first */
    fun sevenDayFocusTrend(
        sessions: List<TimeSessionEntity>,
        nowMillis: Long = System.currentTimeMillis()
    ): List<Pair<String, Float>> {
        val dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        return (6 downTo 0).map { daysAgo ->
            val dayStart = startOfDay(nowMillis) - daysAgo * 86_400_000L
            val dayEnd = dayStart + 86_400_000L
            val daySessions = sessions.filter {
                it.startedAt >= dayStart && it.startedAt < dayEnd && it.endedAt != null
            }
            val totalMins = daySessions.sumOf { it.durationMinutes.toDouble() }.toFloat()
            val cal = Calendar.getInstance().apply { timeInMillis = dayStart }
            val label = dayLabels[(cal.get(Calendar.DAY_OF_WEEK) + 5) % 7]
            label to totalMins
        }
    }

    // ── SUMMARY ───────────────────────────────────────────────────────────────

    data class AnalyticsSummary(
        // Task counts
        val totalTasks: Int,
        val completedTasks: Int,
        val remainingTasks: Int,
        val completionRate: Float,
        val completedToday: Int,
        // Quadrant / priority breakdown
        val tasksByQuadrant: Map<Quadrant, Int>,
        val tasksByPriority: Map<Priority, Int>,
        val completedTodayByQuadrant: Map<Quadrant, Int>,
        val totalRemainingMinutes: Int,
        // Time tracking
        val focusMinutesToday: Float,
        val focusMinutesTotal: Float,
        val avgSessionMinutes: Float,
        val totalSessions: Int,
        val sessionsToday: Int,
        val mostFocusedTaskTitle: String?,
        val sevenDayTrend: List<Pair<String, Float>>,
        // Estimation accuracy
        val overallMape: Float,
        val weightedMape: Float,
        val underestimatedPct: Float,
        val overestimatedPct:Float,
        val overallSmape: Float,
        val weightedSmape: Float,
        // Habits
        val habitTasksTotal: Int,
        val habitTasksCompleted: Int,
        val habitCompletionRate: Float,
        val longestHabitStreak: Int,
        val activeHabitStreaks: List<Pair<String, Int>>,  // title -> current streak, top 5
        val recurringTasksTotal: Int,
        val recurringTasksCompleted: Int,
        val lockedScheduleTasksTotal: Int,
        val lockedScheduleTasksCompleted: Int,
        // Streaks
        val currentStreak: Int,
        val longestStreak: Int,
        // Procrastination
        val topProcrastinatedTasks: List<TaskEntity>,
        // Neuro Boost stats
        val frogTasksTotal: Int,
        val frogTasksCompleted: Int,
        val anxietyTasksTotal: Int,
        val anxietyTasksCompleted: Int,
        val publicCommitmentTotal: Int,
        val publicCommitmentCompleted: Int,
        val ifThenPlanUsageRate: Float,       // % of tasks with if-then plan
        val contextTagBreakdown: Map<String, Int>,  // tag -> count
        val taskTagBreakdown: Map<String, Int>,     // user tags -> count
        val taskTypeDistribution: Map<String, Int>,  // type name -> count
        // XP / points
        val totalXp: Int,
        val xpToday: Int,
        val xpThisWeek: Int,
        val topXpTasks: List<Pair<String, Int>>,       // title -> points, top 5
        // Peak-hour productivity
        val peakHourFocusMinutes: Float,
        val offPeakFocusMinutes: Float,
        val peakHourTasksCompleted: Int
    )
    fun buildSummary(
        allTasks: List<TaskEntity>,
        allSessions: List<TimeSessionEntity>,
        prefs: UserPreferences,
        nowMillis: Long = System.currentTimeMillis()
    ): AnalyticsSummary {
        val active = allTasks.filter { it.status == TaskStatus.ACTIVE }
        val completed = allTasks.filter { it.status == TaskStatus.COMPLETED }

        // ── Task counts ──────────────────────────────────────────────────────
        val todayStart = startOfDay(nowMillis)
        val completedToday = completed.count { (it.completedAt ?: 0L) >= todayStart }
        val completionRate = if (allTasks.isNotEmpty()) completed.size.toFloat() / allTasks.size * 100f else 0f

        // ── Quadrant / priority ──────────────────────────────────────────────
        val tasksByQuadrant = Quadrant.entries.associateWith { q -> allTasks.count { it.quadrant == q } }
        val tasksByPriority = Priority.entries.associateWith { p -> allTasks.count { it.priority == p } }
        val completedTodayByQuadrant = Quadrant.entries.associateWith { q ->
            completed.count { it.quadrant == q && (it.completedAt ?: 0L) >= todayStart }
        }
        val totalRemainingMinutes = active.sumOf { it.estimatedDurationMinutes }

        // ── Session stats ────────────────────────────────────────────────────
        val closedSessions = allSessions.filter { it.endedAt != null && it.durationMinutes > 0f }
        val focusMinutesToday = closedSessions
            .filter { it.startedAt >= todayStart }
            .sumOf { it.durationMinutes.toDouble() }.toFloat()
        val sessionsToday = closedSessions.count { it.startedAt >= todayStart }
        val focusMinutesTotal = closedSessions.sumOf { it.durationMinutes.toDouble() }.toFloat()
        val avgSessionMinutes = if (closedSessions.isNotEmpty())
            focusMinutesTotal / closedSessions.size else 0f
        // Most focused task (most total tracked minutes)
        val mostFocusedTask = allTasks
            .filter { it.totalTimeTrackedMinutes > 0f }
            .maxByOrNull { it.totalTimeTrackedMinutes }

        val sevenDayTrend = sevenDayFocusTrend(allSessions, nowMillis)

        // ── MAPE / SMAPE ─────────────────────────────────────────────────────
        val tracked = completed.filter { it.estimatedDurationMinutes > 0 && it.actualDurationMinutes != null }
        val overallMape = if (tracked.isNotEmpty())
            tracked.map { computeMape(it.estimatedDurationMinutes.toFloat(), it.actualDurationMinutes!!) }.average().toFloat()
        else 0f
        val overallSmape = if (tracked.isNotEmpty())
            tracked.map { computeSmape(it.estimatedDurationMinutes.toFloat(), it.actualDurationMinutes!!) }.average().toFloat()
        else 0f
        val weightedMape = computeWeightedMape(completed)
        val weightedSmape = computeWeightedSmape(completed)

        val underestimated = tracked.filter { it.actualDurationMinutes!! > it.estimatedDurationMinutes }
        val overestimated = tracked.filter { it.actualDurationMinutes!! < it.estimatedDurationMinutes }
        val underestimatedPct = if (tracked.isNotEmpty()) underestimated.size.toFloat() / tracked.size * 100f else 0f
        val overestimatedPct = if (tracked.isNotEmpty()) overestimated.size.toFloat() / tracked.size * 100f else 0f

// ── Habits ───────────────────────────────────────────────────────────
        // Use recurrence as source of truth — isHabitual is only set on completion
        val habitTasks = allTasks.filter { it.recurrence != com.neuroflow.app.domain.model.Recurrence.NONE || it.isHabitual }
        val habitCompleted = habitTasks.filter { it.status == TaskStatus.COMPLETED }
        val recurringTasksTotal = allTasks.count { it.recurrence != com.neuroflow.app.domain.model.Recurrence.NONE }
        val recurringTasksCompleted = completed.count { it.recurrence != com.neuroflow.app.domain.model.Recurrence.NONE }
        val lockedScheduleTasksTotal = allTasks.count { it.isScheduleLocked }
        val lockedScheduleTasksCompleted = completed.count { it.isScheduleLocked }
        val habitCompletionRate = if (habitTasks.isNotEmpty())
            habitCompleted.size.toFloat() / habitTasks.size * 100f else 0f
        // Best streak across all tasks (active recurring tasks carry the streak forward)
        val longestHabitStreak = allTasks.maxOfOrNull { it.habitStreak } ?: 0
        // Active habits with their current streaks for display
        val activeHabitStreaks = active
            .filter { it.recurrence != com.neuroflow.app.domain.model.Recurrence.NONE || it.isHabitual }
            .filter { it.habitStreak > 0 }
            .sortedByDescending { it.habitStreak }
            .take(5)
            .map { it.title to it.habitStreak }

        // ── Procrastination ──────────────────────────────────────────────────
        val topProcrastinated = active
            .filter { it.postponeCount > 0 }
            .sortedByDescending { it.postponeCount }
            .take(5)

        // ── Neuro Boost stats ─────────────────────────────────────────────────
        val frogTasks = allTasks.filter { it.isFrog }
        val frogCompleted = frogTasks.filter { it.status == TaskStatus.COMPLETED }

        val anxietyTasks = allTasks.filter { it.isAnxietyTask }
        val anxietyCompleted = anxietyTasks.filter { it.status == TaskStatus.COMPLETED }

        val publicCommitmentTasks = allTasks.filter { it.isPublicCommitment }
        val publicCommitmentCompleted = publicCommitmentTasks.filter { it.status == TaskStatus.COMPLETED }

        val ifThenPlanUsageRate = if (allTasks.isNotEmpty())
            allTasks.count { it.ifThenPlan.isNotBlank() }.toFloat() / allTasks.size * 100f else 0f

        val contextTagBreakdown = allTasks
            .filter { it.contextTag.isNotBlank() }
            .groupBy { it.contextTag }
            .mapValues { it.value.size }

        val taskTagBreakdown = allTasks
            .flatMap { task -> task.tags.split(",").map { it.trim() }.filter { it.isNotBlank() } }
            .groupingBy { it }
            .eachCount()

        val taskTypeDistribution = allTasks
            .groupBy { it.taskType.name }
            .mapValues { it.value.size }

        // ── XP / Points ───────────────────────────────────────────────────────
        val weekStart = startOfDay(nowMillis) - 6 * 86_400_000L
        val totalXp = completed.sumOf { it.focusModePoints }
        val xpToday = completed
            .filter { (it.completedAt ?: 0L) >= todayStart }
            .sumOf { it.focusModePoints }
        val xpThisWeek = completed
            .filter { (it.completedAt ?: 0L) >= weekStart }
            .sumOf { it.focusModePoints }
        val topXpTasks = completed
            .filter { it.focusModePoints > 0 }
            .sortedByDescending { it.focusModePoints }
            .take(5)
            .map { it.title to it.focusModePoints }

        val useQuizPeak = prefs.quizPeakEnabled && prefs.effectivePeakStart >= 0 && prefs.effectivePeakEnd >= 0
        val peakStartHour = if (useQuizPeak) prefs.effectivePeakStart else prefs.peakEnergyStart
        val peakEndHour = if (useQuizPeak) prefs.effectivePeakEnd else prefs.peakEnergyEnd

        // ── Peak-hour productivity ────────────────────────────────────────────
        // Split each session's duration at peak window boundaries so only the
        // portion that actually fell within peak hours is counted.
        var peakHourFocusMinutes = 0f
        for (session in closedSessions) {
            val sessionStart = session.startedAt
            val sessionEnd = session.endedAt ?: continue
            if (sessionEnd <= sessionStart) continue

            // Build peak windows for the days this session spans (handles overnight sessions)
            val startDay = startOfDay(sessionStart)
            val endDay = startOfDay(sessionEnd)
            var day = startDay
            while (day <= endDay) {
                val windows = peakWindowsForDay(day, peakStartHour, peakEndHour)
                for ((peakWindowStart, peakWindowEnd) in windows) {
                    val overlapStart = maxOf(sessionStart, peakWindowStart)
                    val overlapEnd = minOf(sessionEnd, peakWindowEnd)
                    if (overlapEnd > overlapStart) {
                        peakHourFocusMinutes += (overlapEnd - overlapStart) / 60_000f
                    }
                }
                day += 86_400_000L
            }
        }
        val offPeakFocusMinutes = (focusMinutesTotal - peakHourFocusMinutes).coerceAtLeast(0f)
        val peakHourTasksCompleted = completed.count { task ->
            val completedMs = task.completedAt ?: return@count false
            val cal = Calendar.getInstance().apply { timeInMillis = completedMs }
            val h = cal.get(Calendar.HOUR_OF_DAY)
            isHourInPeakWindow(h, peakStartHour, peakEndHour)
        }

        return AnalyticsSummary(
            totalTasks = allTasks.size,
            completedTasks = completed.size,
            remainingTasks = active.size,
            completionRate = completionRate,
            completedToday = completedToday,
            tasksByQuadrant = tasksByQuadrant,
            tasksByPriority = tasksByPriority,
            completedTodayByQuadrant = completedTodayByQuadrant,
            totalRemainingMinutes = totalRemainingMinutes,
            focusMinutesToday = focusMinutesToday,
            focusMinutesTotal = focusMinutesTotal,
            avgSessionMinutes = avgSessionMinutes,
            totalSessions = closedSessions.size,
            sessionsToday = sessionsToday,
            mostFocusedTaskTitle = mostFocusedTask?.title,
            sevenDayTrend = sevenDayTrend,
            overallMape = overallMape,
            weightedMape = weightedMape,
            underestimatedPct = underestimatedPct,
            overestimatedPct = overestimatedPct,
            overallSmape = overallSmape,
            weightedSmape = weightedSmape,
            habitTasksTotal = habitTasks.size,
            habitTasksCompleted = habitCompleted.size,
            habitCompletionRate = habitCompletionRate,
            longestHabitStreak = longestHabitStreak,
            activeHabitStreaks = activeHabitStreaks,
            recurringTasksTotal = recurringTasksTotal,
            recurringTasksCompleted = recurringTasksCompleted,
            lockedScheduleTasksTotal = lockedScheduleTasksTotal,
            lockedScheduleTasksCompleted = lockedScheduleTasksCompleted,
            currentStreak = prefs.dailyStreak,
            longestStreak = prefs.longestStreak,
            topProcrastinatedTasks = topProcrastinated,
            frogTasksTotal = frogTasks.size,
            frogTasksCompleted = frogCompleted.size,
            anxietyTasksTotal = anxietyTasks.size,
            anxietyTasksCompleted = anxietyCompleted.size,
            publicCommitmentTotal = publicCommitmentTasks.size,
            publicCommitmentCompleted = publicCommitmentCompleted.size,
            ifThenPlanUsageRate = ifThenPlanUsageRate,
            contextTagBreakdown = contextTagBreakdown,
            taskTagBreakdown = taskTagBreakdown,
            taskTypeDistribution = taskTypeDistribution,
            totalXp = totalXp,
            xpToday = xpToday,
            xpThisWeek = xpThisWeek,
            topXpTasks = topXpTasks,
            peakHourFocusMinutes = peakHourFocusMinutes,
            offPeakFocusMinutes = offPeakFocusMinutes,
            peakHourTasksCompleted = peakHourTasksCompleted
        )
    }
}
