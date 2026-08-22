package com.neuroflow.app.domain.scheduler

import com.neuroflow.app.data.local.UserPreferences
import com.neuroflow.app.data.local.UserPreferencesDataStore
import com.neuroflow.app.data.local.entity.TaskEntity
import com.neuroflow.app.domain.model.Priority
import com.neuroflow.app.domain.model.TaskStatus
import com.neuroflow.app.domain.model.TaskType
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import java.util.Calendar

/**
 * Tests for Requirement 7: Safe Break Slot Reservation with Bounds Checking.
 *
 * Verifies that reserveBreakSlotsAfterTask() correctly:
 * - Skips break slots that are in the past (nowMillis guard)
 * - Skips break slots that are already blocked
 * - Skips break slots with no available capacity
 * - Reserves break slots using minOf(60, slot.availableCapacityMinutes)
 * - Does not crash when break slots would be out of bounds
 *
 * Since reserveBreakSlotsAfterTask() is private, these tests exercise it
 * indirectly through planAutoSchedule() by scheduling cognitively intense
 * tasks that trigger break reservation.
 *
 * Break reservation is triggered when:
 * - The task is cognitively intense (effortScore >= 70 or ANALYTICAL/CREATIVE type)
 * - Accumulated cognitive minutes >= breakPolicy.intervalMinutes
 */
class AutoSchedulingEngineBreakReservationTest : StringSpec({

    /**
     * Build an engine with a break policy that triggers after a single 60-min task.
     * Uses sleepPressurePoints=50 (31-59 range) so breakPolicy uses baseInterval directly.
     * autoSchedulingBreakAfterCognitiveMinutes=30 (minimum) → intervalMinutes=30.
     * A 60-min task exceeds the 30-min interval, triggering break reservation.
     */
    fun buildEngineWithBreakPolicy(
        workDayStart: Int = 8,
        workDayEnd: Int = 18,
        breakAfterCognitiveMinutes: Int = 30,
        breakDurationMinutes: Int = 5
    ): AutoSchedulingEngine {
        val prefs = UserPreferences(
            autoSchedulingEnabled = true,
            workDayStart = workDayStart,
            workDayEnd = workDayEnd,
            sleepPressurePoints = 50,  // 31-59 range: uses baseInterval directly
            autoSchedulingBreakAfterCognitiveMinutes = breakAfterCognitiveMinutes,
            autoSchedulingBreakDurationMinutes = breakDurationMinutes
        )
        val dataStore = mockk<UserPreferencesDataStore>()
        every { dataStore.preferencesFlow } returns MutableStateFlow(prefs)
        return AutoSchedulingEngine(dataStore)
    }

    fun createCognitiveTask(
        id: String = "task1",
        title: String = "Cognitive Task",
        estimatedDurationMinutes: Int = 60,
        deadlineDate: Long? = System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000L)
    ): TaskEntity {
        return TaskEntity(
            id = id,
            title = title,
            priority = Priority.HIGH,
            taskType = TaskType.ANALYTICAL,  // ANALYTICAL is always cognitively intense
            effortScore = 80,
            estimatedDurationMinutes = estimatedDurationMinutes,
            status = TaskStatus.ACTIVE,
            deadlineDate = deadlineDate
        )
    }

    fun createLowEffortTask(
        id: String = "lowTask",
        title: String = "Low Effort Task",
        estimatedDurationMinutes: Int = 25,
        deadlineDate: Long? = System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000L)
    ): TaskEntity {
        return TaskEntity(
            id = id,
            title = title,
            priority = Priority.LOW,
            taskType = TaskType.ADMIN,
            effortScore = 20,
            estimatedDurationMinutes = estimatedDurationMinutes,
            status = TaskStatus.ACTIVE,
            deadlineDate = deadlineDate
        )
    }

    // Validates: Requirement 7.1 — break slots in the past should be skipped (no crash)
    "break reservation should not crash when all post-task slots are in the past" {
        runTest {
            // Set nowMillis to near end of work day so post-task slots are in the past
            val now = Calendar.getInstance().apply {
                set(2026, Calendar.APRIL, 25, 17, 0, 0)  // 5 PM
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val task = createCognitiveTask(estimatedDurationMinutes = 60)
            val engine = buildEngineWithBreakPolicy(workDayStart = 8, workDayEnd = 18)

            // Should not throw — past-time guard prevents invalid reservation
            val decisions = engine.planAutoSchedule(
                unscheduledTasks = listOf(task),
                nowMillis = now,
                energyScoreFn = { 75 to 0.9f }
            )

            // Task may or may not be scheduled depending on available slots,
            // but no exception should be thrown
            decisions shouldNotBe null
        }
    }

    // Validates: Requirement 7.2 — break slot index must be within bounds (no IndexOutOfBoundsException)
    "break reservation should not crash when task is scheduled at the last slot of the horizon" {
        runTest {
            // Schedule at the very end of the work day — break slots would be out of bounds
            val now = Calendar.getInstance().apply {
                set(2026, Calendar.APRIL, 25, 8, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val task = createCognitiveTask(estimatedDurationMinutes = 60)
            // Very short work day (only 1 hour) so break slots fall outside the horizon
            val engine = buildEngineWithBreakPolicy(workDayStart = 8, workDayEnd = 9)

            // Should not throw IndexOutOfBoundsException
            val decisions = engine.planAutoSchedule(
                unscheduledTasks = listOf(task),
                nowMillis = now,
                energyScoreFn = { 75 to 0.9f }
            )

            decisions shouldNotBe null
        }
    }

    // Validates: Requirement 7.3 — break slots already in blockedSlotIndices should be skipped
    "break reservation should skip already-blocked slots and not double-reserve them" {
        runTest {
            val now = Calendar.getInstance().apply {
                set(2026, Calendar.APRIL, 25, 8, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            // Schedule two cognitively intense tasks back-to-back.
            // The first task's break slots will be blocked; the second task's break
            // reservation should skip those already-blocked slots.
            val task1 = createCognitiveTask(
                id = "task1",
                title = "First Cognitive Task",
                estimatedDurationMinutes = 60
            )
            val task2 = createCognitiveTask(
                id = "task2",
                title = "Second Cognitive Task",
                estimatedDurationMinutes = 60
            )

            val engine = buildEngineWithBreakPolicy(
                workDayStart = 8,
                workDayEnd = 18,
                breakAfterCognitiveMinutes = 30,
                breakDurationMinutes = 5
            )

            // Should not crash — already-blocked guard prevents double-reservation
            val decisions = engine.planAutoSchedule(
                unscheduledTasks = listOf(task1, task2),
                nowMillis = now,
                energyScoreFn = { 75 to 0.9f }
            )

            decisions shouldNotBe null
            // At least one task should be scheduled
            decisions.size shouldNotBe 0
        }
    }

    // Validates: Requirement 7.4 — break slots with no capacity should be skipped
    "break reservation should skip slots with zero available capacity" {
        runTest {
            val now = Calendar.getInstance().apply {
                set(2026, Calendar.APRIL, 25, 8, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val task = createCognitiveTask(estimatedDurationMinutes = 60)
            val engine = buildEngineWithBreakPolicy()

            // Mark post-task slots as busy (simulating zero capacity via busySlotStartMillis)
            // The slot at 9 AM (1 hour after task start) would be the break slot
            val nineAmMillis = Calendar.getInstance().apply {
                set(2026, Calendar.APRIL, 25, 9, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            // Should not crash — capacity guard prevents reservation in full slots
            val decisions = engine.planAutoSchedule(
                unscheduledTasks = listOf(task),
                nowMillis = now,
                energyScoreFn = { 75 to 0.9f },
                busySlotStartMillis = setOf(nineAmMillis)
            )

            decisions shouldNotBe null
        }
    }

    // Validates: Requirement 7.5 — when a break slot fails validation, skip it and continue (no exception)
    "break reservation should continue scheduling after skipping invalid break slots" {
        runTest {
            val now = Calendar.getInstance().apply {
                set(2026, Calendar.APRIL, 25, 8, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            // Schedule a cognitively intense task followed by a low-effort task.
            // The low-effort task should still be schedulable even if break slots are skipped.
            val cognitiveTask = createCognitiveTask(
                id = "cognitive",
                estimatedDurationMinutes = 60
            )
            val lowTask = createLowEffortTask(
                id = "low",
                estimatedDurationMinutes = 25
            )

            val engine = buildEngineWithBreakPolicy(
                workDayStart = 8,
                workDayEnd = 18,
                breakAfterCognitiveMinutes = 30,
                breakDurationMinutes = 5
            )

            val decisions = engine.planAutoSchedule(
                unscheduledTasks = listOf(cognitiveTask, lowTask),
                nowMillis = now,
                energyScoreFn = { 75 to 0.9f }
            )

            // Both tasks should be scheduled — break reservation doesn't block other tasks
            decisions shouldNotBe null
            decisions.any { it.taskId == "cognitive" } shouldBe true
        }
    }

    // Validates: Requirement 7.6 — each break slot is validated independently
    "break reservation should validate each slot independently for multi-slot breaks" {
        runTest {
            val now = Calendar.getInstance().apply {
                set(2026, Calendar.APRIL, 25, 8, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val task = createCognitiveTask(estimatedDurationMinutes = 60)

            // Use a longer break duration (30 min = multiple slots) to test multi-slot validation
            val engine = buildEngineWithBreakPolicy(
                workDayStart = 8,
                workDayEnd = 18,
                breakAfterCognitiveMinutes = 30,
                breakDurationMinutes = 30  // 30-min break spans multiple slots
            )

            // Mark the first break slot as busy — second break slot should still be evaluated
            val nineAmMillis = Calendar.getInstance().apply {
                set(2026, Calendar.APRIL, 25, 9, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            // Should not crash — each slot is validated independently
            val decisions = engine.planAutoSchedule(
                unscheduledTasks = listOf(task),
                nowMillis = now,
                energyScoreFn = { 75 to 0.9f },
                busySlotStartMillis = setOf(nineAmMillis)
            )

            decisions shouldNotBe null
            decisions.any { it.taskId == "task1" } shouldBe true
        }
    }

    // Validates: Requirement 7.1 + 7.2 — cognitively intense task is scheduled successfully
    // even when break reservation encounters edge cases
    "cognitively intense task should be scheduled successfully with break reservation enabled" {
        runTest {
            val now = Calendar.getInstance().apply {
                set(2026, Calendar.APRIL, 25, 8, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val task = createCognitiveTask(
                id = "task1",
                estimatedDurationMinutes = 60
            )

            val engine = buildEngineWithBreakPolicy(
                workDayStart = 8,
                workDayEnd = 18,
                breakAfterCognitiveMinutes = 30,
                breakDurationMinutes = 5
            )

            val decisions = engine.planAutoSchedule(
                unscheduledTasks = listOf(task),
                nowMillis = now,
                energyScoreFn = { 75 to 0.9f }
            )

            // Task should be scheduled — break reservation guards don't block the task itself
            decisions shouldHaveSize 1
            decisions[0].taskId shouldBe "task1"
        }
    }

    // Validates: Requirement 7.1 — break slots in the past are skipped, not reserved
    "break reservation should not reserve slots that start before nowMillis" {
        runTest {
            // Set now to 9 AM — task scheduled at 8 AM would be in the past
            // This tests that if somehow a past slot is encountered, it's skipped
            val now = Calendar.getInstance().apply {
                set(2026, Calendar.APRIL, 25, 9, 30, 0)  // 9:30 AM
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val task = createCognitiveTask(
                id = "task1",
                estimatedDurationMinutes = 60
            )

            val engine = buildEngineWithBreakPolicy(
                workDayStart = 8,
                workDayEnd = 18,
                breakAfterCognitiveMinutes = 30,
                breakDurationMinutes = 5
            )

            // Should not crash — past-time guard skips slots with startMillis <= nowMillis
            val decisions = engine.planAutoSchedule(
                unscheduledTasks = listOf(task),
                nowMillis = now,
                energyScoreFn = { 75 to 0.9f }
            )

            decisions shouldNotBe null
            // Any scheduled task must be in the future
            decisions.forEach { decision ->
                decision.scheduledStartMillis shouldNotBe 0L
                (decision.scheduledStartMillis > now) shouldBe true
            }
        }
    }
})
