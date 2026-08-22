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
}
