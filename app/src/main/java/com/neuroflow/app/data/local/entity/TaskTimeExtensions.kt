package com.neuroflow.app.data.local.entity

import com.neuroflow.app.domain.model.Recurrence
import java.util.Calendar

private const val MINUTES_PER_DAY = 24 * 60

/**
 * Recurring tasks should anchor on habitDate when present.
 */
fun TaskEntity.isRecurringWithAnchor(): Boolean =
    recurrence != Recurrence.NONE && habitDate != null

/**
 * Target used by reminder/notification flows.
 */
fun TaskEntity.effectiveReminderTargetMillis(): Long? {
    if (isRecurringWithAnchor()) return habitDate
    return deadlineDate?.let { it + (deadlineTime ?: 0L) }
        ?: scheduledDate?.let { it + (scheduledTime ?: 0L) }
        ?: habitDate
}

/**
 * Anchor used by schedule/time-block style flows.
 */
fun TaskEntity.effectiveScheduleAnchorMillis(): Long? {
    if (isRecurringWithAnchor()) return habitDate
    return scheduledDate?.let { it + (scheduledTime ?: 0L) }
        ?: habitDate
}

/**
 * Local minute-of-day for timeline placement.
 */
fun TaskEntity.timelineStartMinuteOfDay(): Int {
    val anchorMillis = effectiveScheduleAnchorMillis() ?: return 0
    val cal = Calendar.getInstance().apply { timeInMillis = anchorMillis }
    return (cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE))
        .coerceIn(0, MINUTES_PER_DAY - 1)
}

fun startOfLocalDay(millis: Long): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}

fun localTimeOfDayOffset(millis: Long): Long {
    val dayStart = startOfLocalDay(millis)
    return (millis - dayStart).coerceAtLeast(0L)
}

fun splitToLocalDateAndTime(millis: Long): Pair<Long, Long> {
    val date = startOfLocalDay(millis)
    val time = localTimeOfDayOffset(millis)
    return date to time
}
