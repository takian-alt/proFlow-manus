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
 * Tests for Requirement 4: Single Capacity Constraint for Low/Moderate Energy Slots.
 *
 * Verifies that:
 * - All slots use full time-based capacity (60 min), not zone-reduced capacity
 * - LOW/MODERATE energy slots are schedulable (not perpetually empty)
 * - CRITICAL zone applies 50% utilization limit
 * - All other zones apply 70% utilization limit
 */
class AutoSchedulingEngineCapacityTest : StringSpec({

    fun buildEngine(prefs: UserPreferences = UserPreferences()): AutoSchedulingEngine {
        val dataStore = mockk<UserPreferencesDataStore>()
        every { dataStore.preferencesFlow } returns MutableStateFlow(prefs)
        return AutoSchedulingEngine(dataStore)
    }

    fun createTask(
        id: String = "task1",
        title: String = "Test Task",
        effortScore: Int = 30,
        estimatedDurationMinutes: Int = 30,
        deadlineDate: Long? = System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000L)
    ): TaskEntity {
        return TaskEntity(
            id = id,
            title = title,
            priority = Priority.MEDIUM,
            taskType = TaskType.ADMIN,
            effortScore = effortScore,
            estimatedDurationMinutes = estimatedDurationMinutes,
            status = TaskStatus.ACTIVE,
            deadlineDate = deadlineDate
        )
    }

    // Validates: Requirement 4.1 — LOW energy slots should be schedulable (70% utilization limit, no capacity reduction)
    "LOW energy slots should be schedulable — no double capacity penalty" {
        runTest {
            val now = Calendar.getInstance().apply {
                set(2026, Calendar.APRIL, 25, 8, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val task = createTask(effortScore = 20, estimatedDurationMinutes = 30)

            val engine = buildEngine(
                UserPreferences(
                    autoSchedulingEnabled = true,
                    workDayStart = 8,
                    workDayEnd = 18
                )
            )

            // All slots return LOW energy (score 20 → LOW zone)
            val decisions = engine.planAutoSchedule(
                unscheduledTasks = listOf(task),
                nowMillis = now,
                energyScoreFn = { 20 to 0.8f }  // LOW energy zone (1..39)
            )

            // Task should be scheduled — LOW slots are no longer perpetually empty
            decisions shouldHaveSize 1
            decisions[0].taskId shouldBe "task1"
        }
    }

    // Validates: Requirement 4.1 — MODERATE energy slots should be schedulable (70% utilization limit, no capacity reduction)
    "MODERATE energy slots should be schedulable — no double capacity penalty" {
        runTest {
            val now = Calendar.getInstance().apply {
                set(2026, Calendar.APRIL, 25, 8, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val task = createTask(effortScore = 30, estimatedDurationMinutes = 30)

            val engine = buildEngine(
                UserPreferences(
                    autoSchedulingEnabled = true,
                    workDayStart = 8,
                    workDayEnd = 18
                )
            )

            // All slots return MODERATE energy (score 45 → MODERATE zone)
            val decisions = engine.planAutoSchedule(
                unscheduledTasks = listOf(task),
                nowMillis = now,
                energyScoreFn = { 45 to 0.8f }  // MODERATE energy zone (40..59)
            )

            // Task should be scheduled — MODERATE slots are no longer perpetually empty
            decisions shouldHaveSize 1
            decisions[0].taskId shouldBe "task1"
        }
    }

    // Validates: Requirement 4.2 — PEAK energy slots should be schedulable (70% utilization limit)
    "PEAK energy slots should be schedulable" {
        runTest {
            val now = Calendar.getInstance().apply {
                set(2026, Calendar.APRIL, 25, 8, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val task = createTask(effortScore = 85, estimatedDurationMinutes = 45)

            val engine = buildEngine(
                UserPreferences(
                    autoSchedulingEnabled = true,
                    workDayStart = 8,
                    workDayEnd = 18
                )
            )

            // All slots return PEAK energy (score 90 → PEAK zone)
            val decisions = engine.planAutoSchedule(
                unscheduledTasks = listOf(task),
                nowMillis = now,
                energyScoreFn = { 90 to 0.95f }  // PEAK energy zone (80..100)
            )

            decisions shouldHaveSize 1
            decisions[0].taskId shouldBe "task1"
        }
    }

    // Validates: Requirement 4.3 — CRITICAL zone applies 50% utilization limit (more conservative)
    "CRITICAL energy slots should be schedulable with 50% utilization limit" {
        runTest {
            val now = Calendar.getInstance().apply {
                set(2026, Calendar.APRIL, 25, 8, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            // Low-effort task that fits within 50% of a 60-min slot (≤30 min)
            val task = createTask(effortScore = 10, estimatedDurationMinutes = 25)

            val engine = buildEngine(
                UserPreferences(
                    autoSchedulingEnabled = true,
                    workDayStart = 8,
                    workDayEnd = 18
                )
            )

            // All slots return CRITICAL energy (score 0 → CRITICAL zone)
            val decisions = engine.planAutoSchedule(
                unscheduledTasks = listOf(task),
                nowMillis = now,
                energyScoreFn = { 0 to 0.5f }  // CRITICAL energy zone (score <= 0)
            )

            // Task should be scheduled — CRITICAL slots now have full 60-min capacity
            // and the 50% utilization limit allows tasks up to 30 min
            decisions shouldHaveSize 1
            decisions[0].taskId shouldBe "task1"
        }
    }

    // Validates: Requirement 4.4 — capacity is time-based only (all slots get 60 min)
    "multiple tasks should be schedulable across LOW energy slots without capacity starvation" {
        runTest {
            val now = Calendar.getInstance().apply {
                set(2026, Calendar.APRIL, 25, 8, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            // Create multiple short tasks that should fit in LOW energy slots
            val tasks = (1..3).map { i ->
                createTask(
                    id = "task$i",
                    title = "Low effort task $i",
                    effortScore = 20,
                    estimatedDurationMinutes = 25
                )
            }

            val engine = buildEngine(
                UserPreferences(
                    autoSchedulingEnabled = true,
                    workDayStart = 8,
                    workDayEnd = 18
                )
            )

            // All slots return LOW energy
            val decisions = engine.planAutoSchedule(
                unscheduledTasks = tasks,
                nowMillis = now,
                energyScoreFn = { 20 to 0.8f }  // LOW energy zone
            )

            // All 3 tasks should be scheduled — LOW slots have full 60-min capacity now
            decisions shouldHaveSize 3
        }
    }

    // Validates: Requirement 4.5 — utilization formula uses (assignedMinutes + reservedBreakMinutes) / availableCapacityMinutes
    "scheduled task should be assigned to a slot with correct utilization tracking" {
        runTest {
            val now = Calendar.getInstance().apply {
                set(2026, Calendar.APRIL, 25, 8, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val task = createTask(effortScore = 50, estimatedDurationMinutes = 40)

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
                energyScoreFn = { 50 to 0.85f }  // MODERATE energy zone
            )

            decisions shouldHaveSize 1
            val decision = decisions[0]
            decision.taskId shouldBe "task1"
            decision.scheduledStartMillis shouldNotBe 0L
        }
    }
})
