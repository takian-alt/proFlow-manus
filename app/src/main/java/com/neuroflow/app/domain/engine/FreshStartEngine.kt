package com.neuroflow.app.domain.engine

object FreshStartEngine {
    private const val THREE_DAYS_MS = 3L * 24 * 60 * 60 * 1000

    fun isFreshStart(
        nowMillis: Long,
        lastOpenMillis: Long,
        dailyStreak: Int,
        lastActiveDate: Long,
        lastFreshStartShownWeek: Int,
        lastFreshStartShownYear: Int
    ): Boolean {
        // Guard: already shown this ISO week
        if (isoWeekNumber(nowMillis) == lastFreshStartShownWeek &&
            isoYear(nowMillis) == lastFreshStartShownYear) return false

        val cal = java.util.Calendar.getInstance().apply { timeInMillis = nowMillis }
        val dayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK)
        val dayOfMonth = cal.get(java.util.Calendar.DAY_OF_MONTH)

        // Monday (first day of ISO week)
        val isMonday = dayOfWeek == java.util.Calendar.MONDAY
        // First day of month
        val isFirstOfMonth = dayOfMonth == 1
        // Day after streak break (had a streak, now it's 0)
        val isAfterStreakBreak = dailyStreak == 0 && lastActiveDate > 0L
        // 3+ day absence
        val isLongAbsence = lastOpenMillis > 0L && (nowMillis - lastOpenMillis) >= THREE_DAYS_MS

        return isMonday || isFirstOfMonth || isAfterStreakBreak || isLongAbsence
    }

    fun isoWeekNumber(millis: Long): Int {
        val cal = java.util.Calendar.getInstance(java.util.Locale.getDefault()).apply {
            minimalDaysInFirstWeek = 4
            firstDayOfWeek = java.util.Calendar.MONDAY
            timeInMillis = millis
        }
        return cal.get(java.util.Calendar.WEEK_OF_YEAR)
    }

    fun isoYear(millis: Long): Int {
        val cal = java.util.Calendar.getInstance(java.util.Locale.getDefault()).apply {
            minimalDaysInFirstWeek = 4
            firstDayOfWeek = java.util.Calendar.MONDAY
            timeInMillis = millis
        }
        // Use ISO week year (the year the ISO week belongs to)
        return cal.getWeekYear()
    }

    data class EmergencyResetPlan(
        val tasksToReschedule: List<com.neuroflow.app.data.local.entity.TaskEntity>,
        val tasksPushedToBacklog: List<com.neuroflow.app.data.local.entity.TaskEntity>,
        val streakFreezeApplied: Boolean,
        val summaryMessage: String
    )

    /**
     * Instant Frictionless One-Tap Recovery Algorithm:
     * Reshuffles remaining tasks without punishing streaks or clogging the calendar.
     *
     * 1. Protects Streaks: Preserves current streak state or applies a streak-freeze safety buffer.
     * 2. Clears Calendar Overpack: Un-schedules overdue/missed tasks for today, pushing low-impact/non-urgent tasks to Backlog.
     * 3. Keeps Only High-Impact frog/urgent tasks for immediate action today.
     */
    fun buildEmergencyResetPlan(
        activeTasks: List<com.neuroflow.app.data.local.entity.TaskEntity>,
        nowMillis: Long
    ): EmergencyResetPlan {
        val todayStart = java.util.Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis

        // Find active tasks scheduled today or in the past
        val scheduledOverdueOrToday = activeTasks.filter { task ->
            task.scheduledDate != null && task.scheduledDate!! <= todayStart && !task.isScheduleLocked
        }

        // Partition by urgency: High-impact (isFrog, impactScore >= 70, or urgent deadline) vs low-impact
        val (highPriority, lowPriority) = scheduledOverdueOrToday.partition { task ->
            task.isFrog || task.impactScore >= 70 ||
                (task.deadlineDate != null && (task.deadlineDate!! - nowMillis) <= 24 * 60 * 60 * 1000L)
        }

        // Keep top 2 high-priority tasks for today, push the rest to backlog (clear scheduleDate/Time)
        val keepForToday = highPriority.take(2)
        val pushToBacklog = lowPriority + highPriority.drop(2)

        return EmergencyResetPlan(
            tasksToReschedule = keepForToday,
            tasksPushedToBacklog = pushToBacklog,
            streakFreezeApplied = true,
            summaryMessage = "Schedule reset! ${keepForToday.size} essential tasks kept for today, ${pushToBacklog.size} moved to backlog. Streak protected!"
        )
    }
}
