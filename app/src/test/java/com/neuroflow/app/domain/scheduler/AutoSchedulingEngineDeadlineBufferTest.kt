package com.neuroflow.app.domain.scheduler

import com.neuroflow.app.data.local.UserPreferences
import com.neuroflow.app.data.local.UserPreferencesDataStore
import com.neuroflow.app.data.local.entity.TaskEntity
import com.neuroflow.app.domain.model.Priority
import com.neuroflow.app.domain.model.TaskType
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import io.mockk.every
import io.mockk.mockk
import java.util.Calendar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest

class AutoSchedulingEngineDeadlineBufferTest : StringSpec({

    fun buildEngine(prefs: UserPreferences = UserPreferences()): AutoSchedulingEngine {
        val dataStore = mockk<UserPreferencesDataStore>()
        every { dataStore.preferencesFlow } returns MutableStateFlow(prefs)
        return AutoSchedulingEngine(dataStore)
    }

    fun splitToDateAndTime(millis: Long): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        val dateCal = (cal.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return dateCal.timeInMillis to (millis - dateCal.timeInMillis)
    }

    fun roundDownToHour(millis: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    "planAutoSchedule avoids placing work at deadline hour when deadline is close" {
        runTest {
            val now = Calendar.getInstance().apply {
                set(2026, Calendar.APRIL, 25, 8, 10, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val deadlineMillis = now + (12L * 60L * 60L * 1000L)
            val (deadlineDate, deadlineTime) = splitToDateAndTime(deadlineMillis)
            val deadlineHourStart = roundDownToHour(deadlineMillis)

            val task = TaskEntity(
                title = "Prepare pitch deck",
                priority = Priority.MEDIUM,
                taskType = TaskType.ADMIN,
                effortScore = 55,
                estimatedDurationMinutes = 60,
                deadlineDate = deadlineDate,
                deadlineTime = deadlineTime,
                tags = "creative"
            )

            val engine = buildEngine(
                UserPreferences(
                    autoSchedulingEnabled = true,
                    workDayStart = 8,
                    workDayEnd = 22,
                    peakEnergyStart = 18,
                    peakEnergyEnd = 22
                )
            )

            val decisions = engine.planAutoSchedule(
                unscheduledTasks = listOf(task),
                nowMillis = now,
                energyScoreFn = { slotMillis ->
                    if (roundDownToHour(slotMillis) == deadlineHourStart) {
                        98 to 1.0f
                    } else {
                        35 to 0.8f
                    }
                }
            )

            val decision = decisions.firstOrNull()
            decision shouldNotBe null
            decision!!.scheduledStartMillis shouldBeLessThanOrEqual (deadlineMillis - (2L * 60L * 60L * 1000L))
        }
    }

    "replanIncompleteTask uses deadline buffer instead of waiting for the deadline hour" {
        runTest {
            val now = Calendar.getInstance().apply {
                set(2026, Calendar.APRIL, 25, 10, 5, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val deadlineMillis = now + (8L * 60L * 60L * 1000L)
            val (deadlineDate, deadlineTime) = splitToDateAndTime(deadlineMillis)
            val deadlineHourStart = roundDownToHour(deadlineMillis)

            val task = TaskEntity(
                title = "Submit quarterly report",
                priority = Priority.HIGH,
                taskType = TaskType.ANALYTICAL,
                effortScore = 82,
                estimatedDurationMinutes = 90,
                deadlineDate = deadlineDate,
                deadlineTime = deadlineTime,
                tags = "coding"
            )

            val engine = buildEngine(
                UserPreferences(
                    autoSchedulingEnabled = true,
                    workDayStart = 8,
                    workDayEnd = 22,
                    peakEnergyStart = 16,
                    peakEnergyEnd = 20
                )
            )

            val decision = engine.replanIncompleteTask(
                task = task,
                timeSpentMinutes = 20,
                nowMillis = now,
                energyScoreFn = { slotMillis ->
                    if (roundDownToHour(slotMillis) == deadlineHourStart) {
                        96 to 1.0f
                    } else {
                        72 to 0.85f
                    }
                }
            )

            decision shouldNotBe null
            decision!!.scheduledStartMillis shouldBeLessThanOrEqual (deadlineMillis - (2L * 60L * 60L * 1000L))
        }
    }

    "planAutoSchedule fills today first and overflows to next day only when today is saturated" {
        runTest {
            val now = Calendar.getInstance().apply {
                set(2026, Calendar.APRIL, 25, 7, 30, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val tasks = (1..7).map { index ->
                taskWithDeadline(
                    title = "Task $index",
                    deadlineMillis = now + 48L * 60L * 60L * 1000L,
                    priority = Priority.MEDIUM,
                    effort = 40,
                    estimatedMinutes = 60,
                    tags = "admin",
                    type = TaskType.ADMIN
                )
            }

            val engine = buildEngine(
                UserPreferences(
                    autoSchedulingEnabled = true,
                    workDayStart = 8,
                    workDayEnd = 14,
                    wakeUpHour = 7,
                    sleepHour = 23,
                    peakEnergyStart = 9,
                    peakEnergyEnd = 11,
                    sleepPressurePoints = 20,
                    autoSchedulingBreakAfterCognitiveMinutes = 180,
                    autoSchedulingBreakDurationMinutes = 5
                )
            )

            val decisions = engine.planAutoSchedule(
                unscheduledTasks = tasks,
                nowMillis = now,
                energyScoreFn = { 85 to 0.9f }
            )

            decisions.size shouldBe 7
            val dayIndexes = decisions.map { dayIndexFrom(now, it.scheduledStartMillis) }
            (dayIndexes.count { it == 0 } >= 6) shouldBe true
            (dayIndexes.count { it == 1 } >= 1) shouldBe true
        }
    }

    "deadline safety wins even when deadline hour has strongest peak score" {
        runTest {
            val now = Calendar.getInstance().apply {
                set(2026, Calendar.APRIL, 25, 8, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val deadlineMillis = now + (12L * 60L * 60L * 1000L)
            val deadlineHourStart = roundDownToHour(deadlineMillis)

            val task = taskWithDeadline(
                title = "Deep analysis",
                deadlineMillis = deadlineMillis,
                priority = Priority.HIGH,
                effort = 86,
                estimatedMinutes = 90,
                tags = "coding,deep work",
                type = TaskType.ANALYTICAL
            )

            val engine = buildEngine(
                UserPreferences(
                    autoSchedulingEnabled = true,
                    workDayStart = 8,
                    workDayEnd = 22,
                    wakeUpHour = 7,
                    sleepHour = 23,
                    peakEnergyStart = 18,
                    peakEnergyEnd = 22,
                    sleepPressurePoints = 35
                )
            )

            val decisions = engine.planAutoSchedule(
                unscheduledTasks = listOf(task),
                nowMillis = now,
                energyScoreFn = { slotMillis ->
                    if (roundDownToHour(slotMillis) == deadlineHourStart) 100 to 1.0f else 70 to 0.9f
                }
            )

            val decision = decisions.firstOrNull()
            decision shouldNotBe null
            decision!!.scheduledStartMillis shouldBeLessThanOrEqual (deadlineMillis - (2L * 60L * 60L * 1000L))
        }
    }

    "planAutoSchedule keeps long tasks from overlapping later slots in the same day" {
        runTest {
            val now = Calendar.getInstance().apply {
                set(2026, Calendar.APRIL, 25, 7, 30, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val longTask = taskWithDeadline(
                title = "Long strategy block",
                deadlineMillis = now + (12L * 60L * 60L * 1000L),
                priority = Priority.HIGH,
                effort = 55,
                estimatedMinutes = 540,
                tags = "deep work",
                type = TaskType.ANALYTICAL
            )
            val shortTask = taskWithDeadline(
                title = "Follow-up note",
                deadlineMillis = now + (48L * 60L * 60L * 1000L),
                priority = Priority.LOW,
                effort = 35,
                estimatedMinutes = 120,
                tags = "creative",
                type = TaskType.CREATIVE
            )

            val engine = buildEngine(
                UserPreferences(
                    autoSchedulingEnabled = true,
                    workDayStart = 8,
                    workDayEnd = 18,
                    wakeUpHour = 7,
                    sleepHour = 23,
                    peakEnergyStart = 8,
                    peakEnergyEnd = 12,
                    sleepPressurePoints = 10,
                    autoSchedulingBreakAfterCognitiveMinutes = 999,
                    autoSchedulingBreakDurationMinutes = 5
                )
            )

            val decisions = engine.planAutoSchedule(
                unscheduledTasks = listOf(longTask, shortTask),
                nowMillis = now,
                energyScoreFn = { 90 to 1.0f }
            )

            decisions.size shouldBe 2
            val longDecision = decisions.first { it.taskId == longTask.id }
            val shortDecision = decisions.first { it.taskId == shortTask.id }
            dayIndexFrom(now, shortDecision.scheduledStartMillis).toLong() shouldBeGreaterThanOrEqual 1L
        }
    }

    "tasks without a real deadline date are not eligible for auto scheduling" {
        runTest {
            val now = Calendar.getInstance().apply {
                set(2026, Calendar.APRIL, 25, 9, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val task = TaskEntity(
                title = "Time-only deadline",
                priority = Priority.MEDIUM,
                taskType = TaskType.ADMIN,
                effortScore = 30,
                estimatedDurationMinutes = 30,
                deadlineTime = 2L * 60L * 60L * 1000L,
                tags = "admin"
            )

            val engine = buildEngine(
                UserPreferences(
                    autoSchedulingEnabled = true,
                    workDayStart = 8,
                    workDayEnd = 18
                )
            )

            val decisions = engine.planAutoSchedule(
                unscheduledTasks = listOf(task),
                nowMillis = now,
                energyScoreFn = { 80 to 1.0f }
            )

            decisions shouldBe emptyList()
        }
    }
})

private fun taskWithDeadline(
    title: String,
    deadlineMillis: Long,
    priority: Priority,
    effort: Int,
    estimatedMinutes: Int,
    tags: String,
    type: TaskType
): TaskEntity {
    val (deadlineDate, deadlineTime) = splitToDateAndTimeForTask(deadlineMillis)
    return TaskEntity(
        title = title,
        priority = priority,
        taskType = type,
        effortScore = effort,
        estimatedDurationMinutes = estimatedMinutes,
        deadlineDate = deadlineDate,
        deadlineTime = deadlineTime,
        tags = tags
    )
}

private fun splitToDateAndTimeForTask(millis: Long): Pair<Long, Long> {
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    val dateCal = (cal.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return dateCal.timeInMillis to (millis - dateCal.timeInMillis)
}

private fun dayIndexFrom(baseNowMillis: Long, scheduledMillis: Long): Int {
    val baseDayStart = Calendar.getInstance().apply {
        timeInMillis = baseNowMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val scheduledDayStart = Calendar.getInstance().apply {
        timeInMillis = scheduledMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    return ((scheduledDayStart - baseDayStart) / (24L * 60L * 60L * 1000L)).toInt()
}
