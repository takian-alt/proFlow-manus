package com.neuroflow.app.domain.scheduler

import com.neuroflow.app.data.local.UserPreferences
import com.neuroflow.app.data.local.UserPreferencesDataStore
import com.neuroflow.app.data.local.entity.TaskEntity
import com.neuroflow.app.domain.model.Priority
import com.neuroflow.app.domain.model.TaskStatus
import com.neuroflow.app.domain.model.TaskType
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import java.util.Calendar

class AutoSchedulingEngineDependencyTest : StringSpec({

    fun buildEngine(prefs: UserPreferences = UserPreferences()): AutoSchedulingEngine {
        val dataStore = mockk<UserPreferencesDataStore>()
        every { dataStore.preferencesFlow } returns MutableStateFlow(prefs)
        return AutoSchedulingEngine(dataStore)
    }

    fun createTask(
        id: String,
        title: String,
        dependsOnTaskIds: String = "",
        waitingFor: String = "",
        status: TaskStatus = TaskStatus.ACTIVE,
        deadlineDate: Long? = System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000L) // 7 days from now
    ): TaskEntity {
        return TaskEntity(
            id = id,
            title = title,
            priority = Priority.MEDIUM,
            taskType = TaskType.ADMIN,
            effortScore = 50,
            estimatedDurationMinutes = 60,
            dependsOnTaskIds = dependsOnTaskIds,
            waitingFor = waitingFor,
            status = status,
            deadlineDate = deadlineDate
        )
    }

    "task with no dependencies should be scheduled" {
        runTest {
            val now = Calendar.getInstance().apply {
                set(2026, Calendar.APRIL, 25, 8, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val task = createTask(
                id = "task1",
                title = "Independent task"
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
                energyScoreFn = { 70 to 0.9f }
            )

            decisions shouldHaveSize 1
            decisions[0].taskId shouldBe "task1"
        }
    }

    "task with empty dependsOnTaskIds should be scheduled" {
        runTest {
            val now = Calendar.getInstance().apply {
                set(2026, Calendar.APRIL, 25, 8, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val task = createTask(
                id = "task1",
                title = "Task with empty dependencies",
                dependsOnTaskIds = ""
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
                energyScoreFn = { 70 to 0.9f }
            )

            decisions shouldHaveSize 1
        }
    }

    "task with whitespace-only dependsOnTaskIds should be scheduled" {
        runTest {
            val now = Calendar.getInstance().apply {
                set(2026, Calendar.APRIL, 25, 8, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val task = createTask(
                id = "task1",
                title = "Task with whitespace dependencies",
                dependsOnTaskIds = "  ,  ,  "
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
                energyScoreFn = { 70 to 0.9f }
            )

            decisions shouldHaveSize 1
        }
    }

    "task with non-blank waitingFor should NOT be scheduled" {
        runTest {
            val now = Calendar.getInstance().apply {
                set(2026, Calendar.APRIL, 25, 8, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val task = createTask(
                id = "task1",
                title = "Task waiting for external dependency",
                waitingFor = "Waiting for client approval"
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
                energyScoreFn = { 70 to 0.9f }
            )

            decisions.shouldBeEmpty()
        }
    }

    "task with completed dependency should be scheduled" {
        runTest {
            val now = Calendar.getInstance().apply {
                set(2026, Calendar.APRIL, 25, 8, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val completedTask = createTask(
                id = "task1",
                title = "Completed dependency",
                status = TaskStatus.COMPLETED
            )

            val dependentTask = createTask(
                id = "task2",
                title = "Dependent task",
                dependsOnTaskIds = "task1",
                status = TaskStatus.ACTIVE
            )

            val engine = buildEngine(
                UserPreferences(
                    autoSchedulingEnabled = true,
                    workDayStart = 8,
                    workDayEnd = 18
                )
            )

            // Pass all tasks for dependency checking (completed tasks are needed for validation)
            val allTasks = listOf(completedTask, dependentTask)
            val decisions = engine.planAutoSchedule(
                unscheduledTasks = allTasks,
                nowMillis = now,
                energyScoreFn = { 70 to 0.9f }
            )

            // Should schedule the dependent task since its dependency is completed
            decisions.any { it.taskId == "task2" } shouldBe true
        }
    }

    "task with incomplete dependency should NOT be scheduled" {
        runTest {
            val now = Calendar.getInstance().apply {
                set(2026, Calendar.APRIL, 25, 8, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val incompleteTask = createTask(
                id = "task1",
                title = "Incomplete dependency",
                status = TaskStatus.ACTIVE
            )

            val dependentTask = createTask(
                id = "task2",
                title = "Dependent task",
                dependsOnTaskIds = "task1"
            )

            val engine = buildEngine(
                UserPreferences(
                    autoSchedulingEnabled = true,
                    workDayStart = 8,
                    workDayEnd = 18
                )
            )

            val decisions = engine.planAutoSchedule(
                unscheduledTasks = listOf(incompleteTask, dependentTask),
                nowMillis = now,
                energyScoreFn = { 70 to 0.9f }
            )

            // Only the independent task should be scheduled
            decisions shouldHaveSize 1
            decisions[0].taskId shouldBe "task1"
        }
    }

    "task with non-existent dependency should NOT be scheduled" {
        runTest {
            val now = Calendar.getInstance().apply {
                set(2026, Calendar.APRIL, 25, 8, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val dependentTask = createTask(
                id = "task1",
                title = "Task with missing dependency",
                dependsOnTaskIds = "nonexistent-task-id"
            )

            val engine = buildEngine(
                UserPreferences(
                    autoSchedulingEnabled = true,
                    workDayStart = 8,
                    workDayEnd = 18
                )
            )

            val decisions = engine.planAutoSchedule(
                unscheduledTasks = listOf(dependentTask),
                nowMillis = now,
                energyScoreFn = { 70 to 0.9f }
            )

            decisions.shouldBeEmpty()
        }
    }

    "task with multiple completed dependencies should be scheduled" {
        runTest {
            val now = Calendar.getInstance().apply {
                set(2026, Calendar.APRIL, 25, 8, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val completedTask1 = createTask(
                id = "task1",
                title = "Completed dependency 1",
                status = TaskStatus.COMPLETED
            )

            val completedTask2 = createTask(
                id = "task2",
                title = "Completed dependency 2",
                status = TaskStatus.COMPLETED
            )

            val dependentTask = createTask(
                id = "task3",
                title = "Task with multiple dependencies",
                dependsOnTaskIds = "task1, task2",
                status = TaskStatus.ACTIVE
            )

            val engine = buildEngine(
                UserPreferences(
                    autoSchedulingEnabled = true,
                    workDayStart = 8,
                    workDayEnd = 18
                )
            )

            // Pass all tasks for dependency checking
            val allTasks = listOf(completedTask1, completedTask2, dependentTask)
            val decisions = engine.planAutoSchedule(
                unscheduledTasks = allTasks,
                nowMillis = now,
                energyScoreFn = { 70 to 0.9f }
            )

            // Should schedule the dependent task since all dependencies are completed
            decisions.any { it.taskId == "task3" } shouldBe true
        }
    }

    "task with one incomplete dependency among multiple should NOT be scheduled" {
        runTest {
            val now = Calendar.getInstance().apply {
                set(2026, Calendar.APRIL, 25, 8, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val completedTask = createTask(
                id = "task1",
                title = "Completed dependency",
                status = TaskStatus.COMPLETED
            )

            val incompleteTask = createTask(
                id = "task2",
                title = "Incomplete dependency",
                status = TaskStatus.ACTIVE
            )

            val dependentTask = createTask(
                id = "task3",
                title = "Task with mixed dependencies",
                dependsOnTaskIds = "task1, task2",
                status = TaskStatus.ACTIVE
            )

            val engine = buildEngine(
                UserPreferences(
                    autoSchedulingEnabled = true,
                    workDayStart = 8,
                    workDayEnd = 18
                )
            )

            // Pass all tasks for dependency checking
            val allTasks = listOf(completedTask, incompleteTask, dependentTask)
            val decisions = engine.planAutoSchedule(
                unscheduledTasks = allTasks,
                nowMillis = now,
                energyScoreFn = { 70 to 0.9f }
            )

            // The incomplete task should be scheduled
            decisions.any { it.taskId == "task2" } shouldBe true
            // The dependent task should NOT be scheduled (has incomplete dependency)
            decisions.any { it.taskId == "task3" } shouldBe false
        }
    }

    "task with self-reference should NOT be scheduled" {
        runTest {
            val now = Calendar.getInstance().apply {
                set(2026, Calendar.APRIL, 25, 8, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val selfReferencingTask = createTask(
                id = "task1",
                title = "Self-referencing task",
                dependsOnTaskIds = "task1"
            )

            val engine = buildEngine(
                UserPreferences(
                    autoSchedulingEnabled = true,
                    workDayStart = 8,
                    workDayEnd = 18
                )
            )

            val decisions = engine.planAutoSchedule(
                unscheduledTasks = listOf(selfReferencingTask),
                nowMillis = now,
                energyScoreFn = { 70 to 0.9f }
            )

            decisions.shouldBeEmpty()
        }
    }

    "task with circular dependency (A->B->A) should NOT be scheduled" {
        runTest {
            val now = Calendar.getInstance().apply {
                set(2026, Calendar.APRIL, 25, 8, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val taskA = createTask(
                id = "taskA",
                title = "Task A",
                dependsOnTaskIds = "taskB"
            )

            val taskB = createTask(
                id = "taskB",
                title = "Task B",
                dependsOnTaskIds = "taskA"
            )

            val engine = buildEngine(
                UserPreferences(
                    autoSchedulingEnabled = true,
                    workDayStart = 8,
                    workDayEnd = 18
                )
            )

            val decisions = engine.planAutoSchedule(
                unscheduledTasks = listOf(taskA, taskB),
                nowMillis = now,
                energyScoreFn = { 70 to 0.9f }
            )

            decisions.shouldBeEmpty()
        }
    }

    "task with circular dependency (A->B->C->A) should NOT be scheduled" {
        runTest {
            val now = Calendar.getInstance().apply {
                set(2026, Calendar.APRIL, 25, 8, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val taskA = createTask(
                id = "taskA",
                title = "Task A",
                dependsOnTaskIds = "taskB"
            )

            val taskB = createTask(
                id = "taskB",
                title = "Task B",
                dependsOnTaskIds = "taskC"
            )

            val taskC = createTask(
                id = "taskC",
                title = "Task C",
                dependsOnTaskIds = "taskA"
            )

            val engine = buildEngine(
                UserPreferences(
                    autoSchedulingEnabled = true,
                    workDayStart = 8,
                    workDayEnd = 18
                )
            )

            val decisions = engine.planAutoSchedule(
                unscheduledTasks = listOf(taskA, taskB, taskC),
                nowMillis = now,
                energyScoreFn = { 70 to 0.9f }
            )

            decisions.shouldBeEmpty()
        }
    }

    "task with dependency chain (A->B->C, all completed) should be scheduled" {
        runTest {
            val now = Calendar.getInstance().apply {
                set(2026, Calendar.APRIL, 25, 8, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val taskC = createTask(
                id = "taskC",
                title = "Task C",
                status = TaskStatus.COMPLETED
            )

            val taskB = createTask(
                id = "taskB",
                title = "Task B",
                dependsOnTaskIds = "taskC",
                status = TaskStatus.COMPLETED
            )

            val taskA = createTask(
                id = "taskA",
                title = "Task A",
                dependsOnTaskIds = "taskB",
                status = TaskStatus.ACTIVE
            )

            val engine = buildEngine(
                UserPreferences(
                    autoSchedulingEnabled = true,
                    workDayStart = 8,
                    workDayEnd = 18
                )
            )

            // Pass all tasks for dependency checking
            val allTasks = listOf(taskA, taskB, taskC)
            val decisions = engine.planAutoSchedule(
                unscheduledTasks = allTasks,
                nowMillis = now,
                energyScoreFn = { 70 to 0.9f }
            )

            // Should schedule taskA since its entire dependency chain is completed
            decisions.any { it.taskId == "taskA" } shouldBe true
        }
    }

    "dependency IDs with extra whitespace should be parsed correctly" {
        runTest {
            val now = Calendar.getInstance().apply {
                set(2026, Calendar.APRIL, 25, 8, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val completedTask = createTask(
                id = "task1",
                title = "Completed dependency",
                status = TaskStatus.COMPLETED
            )

            val dependentTask = createTask(
                id = "task2",
                title = "Dependent task with whitespace",
                dependsOnTaskIds = "  task1  ,  ",
                status = TaskStatus.ACTIVE
            )

            val engine = buildEngine(
                UserPreferences(
                    autoSchedulingEnabled = true,
                    workDayStart = 8,
                    workDayEnd = 18
                )
            )

            // Pass all tasks for dependency checking
            val allTasks = listOf(completedTask, dependentTask)
            val decisions = engine.planAutoSchedule(
                unscheduledTasks = allTasks,
                nowMillis = now,
                energyScoreFn = { 70 to 0.9f }
            )

            // Should schedule the dependent task (whitespace should be trimmed correctly)
            decisions.any { it.taskId == "task2" } shouldBe true
        }
    }
})
