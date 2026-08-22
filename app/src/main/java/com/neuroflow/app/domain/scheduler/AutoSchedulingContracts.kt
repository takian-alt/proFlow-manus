package com.neuroflow.app.domain.scheduler

import com.neuroflow.app.data.local.entity.TaskEntity
import com.neuroflow.app.data.local.entity.localTimeOfDayOffset
import com.neuroflow.app.data.local.entity.startOfLocalDay
import java.util.Calendar

/**
 * Non-negotiable safety contracts for all auto-scheduling flows.
 */
object AutoSchedulingContracts {

    fun hasManualScheduleData(task: TaskEntity): Boolean {
        return task.scheduledDate != null || task.scheduledTime != null
    }

    fun isMutableByAutoScheduler(task: TaskEntity): Boolean {
        return !task.isScheduleLocked && !hasManualScheduleData(task) && hasDeadlineData(task)
    }

    /**
     * True when a task has a deadline anchor but no explicit scheduled slot yet.
     */
    fun hasDeadlineData(task: TaskEntity): Boolean {
        return task.deadlineDate != null
    }

    /**
     * Validates constraints that apply to a proposed scheduling block.
     * Time fields are represented as local-day offsets, matching TaskEntity's existing
     * deadline/schedule storage convention.
     */
    fun respectsPlacementConstraints(
        task: TaskEntity,
        startMillis: Long,
        endMillis: Long,
        durationMinutes: Int
    ): Boolean {
        val startCalendar = Calendar.getInstance().apply { timeInMillis = startMillis }
        val startDay = startOfLocalDay(startMillis)

        val earliestStartMillis = task.earliestStartDate?.let { date ->
            date + (task.earliestStartTime ?: 0L)
        }
        if (earliestStartMillis != null && startMillis < earliestStartMillis) return false

        val weekdayMask = task.preferredWeekdaysMask and 0x7F
        if (weekdayMask != 0) {
            val weekdayBit = 1 shl (startCalendar.get(Calendar.DAY_OF_WEEK) - 1)
            if (weekdayMask and weekdayBit == 0) return false
        }

        val avoidStart = task.avoidStartTime
        val avoidEnd = task.avoidEndTime
        if (avoidStart != null && avoidEnd != null) {
            val startOffsetMinutes = ((startMillis - startDay) / 60_000L).toInt()
            val endOffsetMinutes = startOffsetMinutes + durationMinutes.coerceAtLeast(1)
            val avoidStartMinutes = (avoidStart / 60_000L).toInt().coerceIn(0, 1_439)
            val avoidEndMinutes = (avoidEnd / 60_000L).toInt().coerceIn(0, 1_440)
            val overlaps = if (avoidStartMinutes <= avoidEndMinutes) {
                startOffsetMinutes < avoidEndMinutes && endOffsetMinutes > avoidStartMinutes
            } else {
                // An avoid window crossing midnight is represented as [start, 24h) or [0, end).
                startOffsetMinutes < avoidEndMinutes || endOffsetMinutes > avoidStartMinutes
            }
            if (overlaps) return false
        }

        val maxSession = task.maxSessionLengthMinutes
        if (maxSession > 0 && durationMinutes > maxSession) return false

        val minimumBlock = task.minimumFocusBlockMinutes
        if (minimumBlock > 0 && durationMinutes < minimumBlock) return false

        return true
    }

    /**
     * Normalizes recurring anchors through local-safe helpers to avoid timezone and
     * minute-of-day drift when recurrence data is reused across cycles.
     */
    fun normalizedRecurringAnchorMillis(task: TaskEntity): Long? {
        val anchor = task.habitDate ?: return null
        return startOfLocalDay(anchor) + localTimeOfDayOffset(anchor)
    }
}

enum class AutoScheduleRejectionReason {
    LOCKED_TASK,
    MANUAL_SCHEDULE_PRESENT,
    DEPENDENCY_BLOCKED,
    NO_CAPACITY_IN_HORIZON,
    LOW_ENERGY_PROTECTION,
    OUTSIDE_WORKDAY,
    UNKNOWN
}

data class AutoScheduleDecisionTelemetry(
    val taskId: String,
    val generatedAtMillis: Long,
    val horizonDays: Int,
    val wasApplied: Boolean,
    val reviewStatus: String = "PENDING", // PENDING, APPROVED, REJECTED
    val selectedSlotDate: Long? = null,
    val selectedSlotTime: Long? = null,
    val candidateSlotStartMillis: List<Long> = emptyList(),
    val rejectedCandidateSlotStartMillis: List<Long> = emptyList(),
    val rejectionReason: AutoScheduleRejectionReason? = null,
    val inputs: AutoScheduleInputsSnapshot
)

data class AutoScheduleInputsSnapshot(
    val priorityScore: Float,
    val energyScore: Float,
    val sleepPressurePoints: Int,
    val hasDependencies: Boolean,
    val estimatedDurationMinutes: Int,
    val tagProfileHints: List<String>,
    val confidence: Float = 0f,
    val deadlinePressure: Float = 0f
)
