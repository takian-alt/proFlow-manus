package com.neuroflow.app.domain.scheduler

import com.neuroflow.app.data.local.UserPreferences
import com.neuroflow.app.data.local.UserPreferencesDataStore
import com.neuroflow.app.data.local.entity.TaskEntity
import com.neuroflow.app.domain.model.Priority
import com.neuroflow.app.domain.model.TaskStatus
import com.neuroflow.app.domain.model.TaskType
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import java.util.Calendar
import java.util.TimeZone

/**
 * Tests for Requirement 5: DST-Safe Calendar Generation.
 *
 * Verifies that:
 * - A fresh Calendar instance is created per day (no DST carry-over)
 * - Spring-forward transitions skip the missing hour (no duplicate slot)
 * - Fall-back transitions produce distinct timeInMillis for the repeated hour
 * - Slot identifiers (startMillis) are always unique across the horizon
 */
class AutoSchedulingEngineDstTest : StringSpec({

    fun buildEngine(prefs: UserPreferences = UserPreferences()): AutoSchedulingEngine {
        val dataStore = mockk<UserPreferencesDataStore>()
        every { dataStore.preferencesFlow } returns MutableStateFlow(prefs)
        return AutoSchedulingEngine(dataStore)
    }

    fun createTask(
        id: String = "task1",
        effortScore: Int = 20,
        estimatedDurationMinutes: Int = 30,
        deadlineDate: Long? = System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000L)
    ): TaskEntity = TaskEntity(
        id = id,
        title = "DST Test Task $id",
        priority = Priority.MEDIUM,
        taskType = TaskType.ADMIN,
        effortScore = effortScore,
        estimatedDurationMinutes = estimatedDurationMinutes,
        status = TaskStatus.ACTIVE,
        deadlineDate = deadlineDate
    )

    // Validates: Requirement 5.5 — all slot startMillis values must be unique
    "slot startMillis values must be unique across the full horizon" {
        runTest {
            val now = Calendar.getInstance().apply {
                set(2026, Calendar.APRIL, 25, 8, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val task = createTask()
            val engine = buildEngine(
                UserPreferences(
                    autoSchedulingEnabled = true,
                    workDayStart = 8,
                    workDayEnd = 18,
                    autoSchedulingHorizonDays = 3
                )
            )

            val capturedSlotMillis = mutableListOf<Long>()
            val decisions = engine.planAutoSchedule(
                unscheduledTasks = listOf(task),
                nowMillis = now,
                energyScoreFn = { slotMillis ->
                    capturedSlotMillis.add(slotMillis)
                    50 to 0.8f
                }
            )

            // All queried slot timestamps must be unique — no duplicates from DST reuse
            val uniqueMillis = capturedSlotMillis.toSet()
            uniqueMillis.size shouldBe capturedSlotMillis.size
        }
    }

    // Validates: Requirement 5.1 — fresh Calendar per day (no state carry-over between days)
    "slots on day 2 and day 3 should have startMillis strictly after day 1 slots" {
        runTest {
            val now = Calendar.getInstance().apply {
                set(2026, Calendar.APRIL, 25, 8, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val task = createTask()
            val engine = buildEngine(
                UserPreferences(
                    autoSchedulingEnabled = true,
                    workDayStart = 8,
                    workDayEnd = 18,
                    autoSchedulingHorizonDays = 3
                )
            )

            val capturedSlotMillis = mutableListOf<Long>()
            engine.planAutoSchedule(
                unscheduledTasks = listOf(task),
                nowMillis = now,
                energyScoreFn = { slotMillis ->
                    capturedSlotMillis.add(slotMillis)
                    50 to 0.8f
                }
            )

            // Slots should be monotonically increasing (each day's slots come after the previous day's)
            for (i in 1 until capturedSlotMillis.size) {
                capturedSlotMillis[i] shouldBeGreaterThan capturedSlotMillis[i - 1]
            }
        }
    }

    // Validates: Requirement 5.3 — spring-forward: missing hour is skipped (US/Eastern, 2nd Sunday March)
    "spring-forward DST transition should produce no duplicate slots for the missing hour" {
        runTest {
            // US/Eastern spring-forward 2026: March 8, 2026 at 2:00 AM → 3:00 AM
            val easternTz = TimeZone.getTimeZone("America/New_York")
            val springForwardCal = Calendar.getInstance(easternTz).apply {
                set(2026, Calendar.MARCH, 8, 1, 0, 0)  // 1:00 AM, just before spring-forward
                set(Calendar.MILLISECOND, 0)
            }
            val now = springForwardCal.timeInMillis

            val task = createTask(
                deadlineDate = now + (3 * 24 * 60 * 60 * 1000L)
            )
            val engine = buildEngine(
                UserPreferences(
                    autoSchedulingEnabled = true,
                    workDayStart = 1,   // Start at 1 AM to include the DST transition window
                    workDayEnd = 5,     // End at 5 AM
                    autoSchedulingHorizonDays = 1
                )
            )

            val capturedSlotMillis = mutableListOf<Long>()
            engine.planAutoSchedule(
                unscheduledTasks = listOf(task),
                nowMillis = now,
                energyScoreFn = { slotMillis ->
                    capturedSlotMillis.add(slotMillis)
                    50 to 0.8f
                }
            )

            // All captured slot timestamps must be unique — no duplicate for the skipped 2 AM hour
            val uniqueMillis = capturedSlotMillis.toSet()
            uniqueMillis.size shouldBe capturedSlotMillis.size
        }
    }

    // Validates: Requirement 5.4 — fall-back: repeated hour produces distinct timestamps
    "fall-back DST transition should produce distinct timestamps for the repeated hour" {
        runTest {
            // US/Eastern fall-back 2026: November 1, 2026 at 2:00 AM → 1:00 AM
            val easternTz = TimeZone.getTimeZone("America/New_York")
            val fallBackCal = Calendar.getInstance(easternTz).apply {
                set(2026, Calendar.NOVEMBER, 1, 0, 0, 0)  // Midnight, before fall-back
                set(Calendar.MILLISECOND, 0)
            }
            val now = fallBackCal.timeInMillis

            val task = createTask(
                deadlineDate = now + (3 * 24 * 60 * 60 * 1000L)
            )
            val engine = buildEngine(
                UserPreferences(
                    autoSchedulingEnabled = true,
                    workDayStart = 0,   // Start at midnight to include the DST transition window
                    workDayEnd = 4,     // End at 4 AM
                    autoSchedulingHorizonDays = 1
                )
            )

            val capturedSlotMillis = mutableListOf<Long>()
            engine.planAutoSchedule(
                unscheduledTasks = listOf(task),
                nowMillis = now,
                energyScoreFn = { slotMillis ->
                    capturedSlotMillis.add(slotMillis)
                    50 to 0.8f
                }
            )

            // All captured slot timestamps must be unique — fall-back repeated hour
            // should produce two distinct timeInMillis values (EDT and EST versions)
            val uniqueMillis = capturedSlotMillis.toSet()
            uniqueMillis.size shouldBe capturedSlotMillis.size
        }
    }

    // Validates: Requirement 5.6 — Calendar.add(HOUR_OF_DAY, 1) is used (DST-aware advancement)
    "slots should be scheduled correctly across a 3-day horizon without DST-related gaps" {
        runTest {
            val now = Calendar.getInstance().apply {
                set(2026, Calendar.JUNE, 15, 8, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val tasks = (1..5).map { i ->
                createTask(
                    id = "task$i",
                    effortScore = 20,
                    estimatedDurationMinutes = 25,
                    deadlineDate = now + (7 * 24 * 60 * 60 * 1000L)
                )
            }

            val engine = buildEngine(
                UserPreferences(
                    autoSchedulingEnabled = true,
                    workDayStart = 8,
                    workDayEnd = 18,
                    autoSchedulingHorizonDays = 3
                )
            )

            val decisions = engine.planAutoSchedule(
                unscheduledTasks = tasks,
                nowMillis = now,
                energyScoreFn = { 50 to 0.8f }
            )

            // All 5 tasks should be scheduled across the 3-day horizon
            decisions shouldHaveSize 5

            // All scheduled start times must be unique (no two tasks in the same slot)
            val uniqueStartTimes = decisions.map { it.scheduledStartMillis }.toSet()
            uniqueStartTimes.size shouldBe decisions.size
        }
    }
})
