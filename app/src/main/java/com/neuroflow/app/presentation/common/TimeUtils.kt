package com.neuroflow.app.presentation.common

import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.*

fun formatRelativeTime(millis: Long, hasTime: Boolean, now: Long = System.currentTimeMillis()): String {
    if (!hasTime) {
        val zone = ZoneId.systemDefault()
        val nowDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val targetDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        val diffDays = ChronoUnit.DAYS.between(nowDate, targetDate).toInt()
        return when {
            diffDays == 0 -> "Today"
            diffDays == 1 -> "Tomorrow"
            diffDays == -1 -> "Yesterday"
            diffDays > 1 -> "In $diffDays days"
            else -> "Overdue by ${kotlin.math.abs(diffDays)} days"
        }
    }

    val diff = millis - now
    val absDiff = kotlin.math.abs(diff)

    if (absDiff < 60_000L) {
        return if (diff < 0) "Overdue" else "Right now"
    }

    val totalMinutes = absDiff / 60_000
    val days = totalMinutes / (60 * 24)
    val hours = (totalMinutes % (60 * 24)) / 60
    val minutes = totalMinutes % 60

    if (diff < 0) {
        val parts = mutableListOf<String>()
        if (days > 0) parts += "$days day${if (days > 1) "s" else ""}"
        if (hours > 0) parts += "$hours hr${if (hours > 1) "s" else ""}"
        if (minutes > 0) parts += "$minutes min${if (minutes > 1) "s" else ""}"
        val timeStr = parts.joinToString(" ").ifBlank { "now" }
        return "Overdue by $timeStr"
    }

    val parts = mutableListOf<String>()
    if (days > 0) parts += "$days day${if (days > 1) "s" else ""}"
    if (hours > 0) parts += "$hours hr${if (hours > 1) "s" else ""}"
    if (minutes > 0 && days == 0L) parts += "$minutes min${if (minutes > 1) "s" else ""}"

    val timeStr = parts.joinToString(" ").ifBlank { "now" }
    return when {
        timeStr == "now" -> "Right now"
        else -> "In $timeStr"
    }
}

fun formatFullDate(millis: Long, hasTime: Boolean): String {
    val formatStr = if (hasTime) "MMM d, h:mm a" else "MMM d, yyyy"
    val sdf = SimpleDateFormat(formatStr, Locale.getDefault())
    return sdf.format(Date(millis))
}
