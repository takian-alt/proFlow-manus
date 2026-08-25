package com.neuroflow.app.domain.scheduler

import com.neuroflow.app.data.local.UserPreferences
import com.neuroflow.app.data.local.UserPreferencesDataStore
import com.neuroflow.app.data.local.entity.ScheduleAdjustmentEntity
import com.neuroflow.app.data.local.entity.TaskEntity
import com.neuroflow.app.data.local.entity.TaskFeedbackEntity
import com.neuroflow.app.domain.model.TaskStatus
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import java.util.Calendar

class AutoSchedulingConstraintTest : StringSpec({
    fun engine(prefs: UserPreferences = UserPreferences()): AutoSchedulingEngine {
        val store = mockk<UserPreferencesDataStore>()
        every { store.preferencesFlow } returns MutableStateFlow(prefs)
        return AutoSchedulingEngine(store)
    }

    fun dayAt(hour: Int, minute: Int = 0): Long = Calendar.getInstance().apply {
        set(2026, Calendar.APRIL, 27, hour, minute, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun task(
        earliestStartDate: Long? = null,
        earliestStartTime: Long? = null,
        preferredWeekdaysMask: Int = 0,
        avoidStartTime: Long? = null,
        avoidEndTime: Long? = null,
        maxSessionLengthMinutes: Int = 0,
        minimumFocusBlockMinutes: Int = 0
    ): TaskEntity = TaskEntity(
        id = "constraint-task",
        title = "Constraint task",
        deadlineDate = dayAt(18).plus(2 * 24 * 60 * 60 * 1000L),
        estimatedDurationMinutes = 25,
        status = TaskStatus.ACTIVE,
        earliestStartDate = earliestStartDate,
        earliestStartTime = earliestStartTime,
        preferredWeekdaysMask = preferredWeekdaysMask,
        avoidStartTime = avoidStartTime,
        avoidEndTime = avoidEndTime,
        maxSessionLengthMinutes = maxSessionLengthMinutes,
        minimumFocusBlockMinutes = minimumFocusBlockMinutes
    )

    "placement constraints reject a block before earliest start" {
        val start = dayAt(9)
        AutoSchedulingContracts.respectsPlacementConstraints(
            task = task(
                earliestStartDate = dayAt(10),
                earliestStartTime = 0L
            ),
            startMillis = start,
            endMillis = start + 30 * 60_000L,
            durationMinutes = 25
        ) shouldBe false
    }

    "placement constraints accept a preferred weekday" {
        val mondayBit = 1 shl (Calendar.MONDAY - 1)
        val start = dayAt(9)
        AutoSchedulingContracts.respectsPlacementConstraints(
            task = task(preferredWeekdaysMask = mondayBit),
            startMillis = start,
            endMillis = start + 30 * 60_000L,
            durationMinutes = 25
        ) shouldBe true
    }

    "placement constraints reject avoid-window overlap" {
        val start = dayAt(13, 30)
        AutoSchedulingContracts.respectsPlacementConstraints(
            task = task(
                avoidStartTime = 13 * 60 * 60_000L,
                avoidEndTime = 14 * 60 * 60_000L
            ),
            startMillis = start,
            endMillis = start + 30 * 60_000L,
            durationMinutes = 25
        ) shouldBe false
    }

    "planner aligns generated slots to 30-minute boundaries" {
        runTest {
            val now = dayAt(8, 10)
            val decision = engine(
                UserPreferences(
                    autoSchedulingEnabled = true,
                    workDayStart = 8,
                    workDayEnd = 18
                )
            ).planAutoSchedule(
                unscheduledTasks = listOf(task()),
                nowMillis = now,
                energyScoreFn = { 70 to 0.9f }
            ).firstOrNull()

            decision shouldNotBe null
            val calendar = Calendar.getInstance().apply { timeInMillis = decision!!.scheduledStartMillis }
            calendar.get(Calendar.MINUTE) % 30 shouldBe 0
        }
    }
})


class TaskSplitPlannerTest : StringSpec({
    "splits a long task into linked bounded parts" {
        val parent = TaskEntity(
            id = "report",
            title = "Write report",
            estimatedDurationMinutes = 180,
            canSplit = true
        )
        val parts = TaskSplitPlanner.createParts(parent)
        parts.size shouldBe 4
        parts.sumOf { it.estimatedDurationMinutes } shouldBe 180
        parts.all { it.parentTaskId == parent.id && !it.canSplit } shouldBe true
    }

    "does not split short or non-splittable tasks" {
        val task = TaskEntity(
            id = "short",
            title = "Short task",
            estimatedDurationMinutes = 60,
            canSplit = false
        )
        TaskSplitPlanner.createParts(task) shouldBe listOf(task)
    }
})


class DurationPredictionEngineTest : StringSpec({
    "coding tag applies a longer duration multiplier" {
        val task = TaskEntity(
            id = "coding",
            title = "Coding task",
            tags = "coding",
            estimatedDurationMinutes = 60
        )
        com.neuroflow.app.domain.engine.DurationPredictionEngine.predictMinutes(task) shouldBe 97
    }

    "repeated postponements shorten the next block" {
        val task = TaskEntity(
            id = "postponed",
            title = "Postponed task",
            estimatedDurationMinutes = 120,
            postponeCount = 3
        )
        com.neuroflow.app.domain.engine.DurationPredictionEngine.predictMinutes(task) shouldBe 102
    }
})


class CorrectionProfileTest : StringSpec({
    "repeated duration feedback increases the learned prediction" {
        val task = TaskEntity(
            id = "coding-profile",
            title = "Coding",
            tags = "coding",
            estimatedDurationMinutes = 60
        )
        val profile = CorrectionProfile.from(
            tasks = listOf(task),
            feedback = listOf(
                TaskFeedbackEntity(taskId = task.id, kind = "FOCUS_TIME", value = "-2"),
                TaskFeedbackEntity(taskId = task.id, kind = "FOCUS_TIME", value = "-1")
            ),
            adjustments = emptyList()
        )
        profile.durationMultiplier(task) shouldBe 1.09f
    }

    "repeated moved-later adjustments produce a learned preferred hour" {
        val task = TaskEntity(
            id = "admin-profile",
            title = "Admin",
            tags = "admin",
            estimatedDurationMinutes = 30
        )
        val profile = CorrectionProfile.from(
            tasks = listOf(task),
            feedback = emptyList(),
            adjustments = listOf(
                ScheduleAdjustmentEntity(
                    taskId = task.id,
                    newScheduledTime = 10 * 3_600_000L,
                    source = "MANUAL",
                    reason = "moved_later"
                ),
                ScheduleAdjustmentEntity(
                    taskId = task.id,
                    newScheduledTime = 10 * 3_600_000L,
                    source = "MANUAL",
                    reason = "moved_later"
                )
            )
        )
        profile.timeOfDayFit(task, 10) shouldBe 0.10f
        profile.timeOfDayFit(task, 18) shouldBe -0.03f
    }
})

class AutoSchedulingScenarioTest : StringSpec({
    fun dayAt(hour: Int, minute: Int = 0): Long = Calendar.getInstance().apply {
        set(2026, Calendar.APRIL, 27, hour, minute, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    "strict deadline rejects a block that would finish late" {
        val start = dayAt(17, 45)
        AutoSchedulingContracts.respectsPlacementConstraints(
            task = TaskEntity(
                id = "strict",
                title = "Strict deadline",
                deadlineDate = dayAt(18),
                deadlineType = "STRICT",
                isHardDeadline = true,
                estimatedDurationMinutes = 30
            ),
            startMillis = start,
            endMillis = start + 30 * 60_000L,
            durationMinutes = 30
        ) shouldBe false
    }

    "planner avoids a calendar-conflict block" {
        runTest {
            val store = mockk<UserPreferencesDataStore>()
            every { store.preferencesFlow } returns MutableStateFlow(
                UserPreferences(autoSchedulingEnabled = true, workDayStart = 8, workDayEnd = 12)
            )
            val start = dayAt(8)
            val decision = AutoSchedulingEngine(store).planAutoSchedule(
                unscheduledTasks = listOf(
                    TaskEntity(
                        id = "calendar-task",
                        title = "Calendar task",
                        deadlineDate = dayAt(17),
                        estimatedDurationMinutes = 30,
                        status = TaskStatus.ACTIVE
                    )
                ),
                nowMillis = dayAt(7),
                energyScoreFn = { 70 to 0.9f },
                busySlotStartMillis = setOf(start)
            ).firstOrNull()

            decision shouldNotBe null
            decision!!.scheduledStartMillis shouldNotBe start
        }
    }
})


class SchedulePlanVersionCodecTest : StringSpec({
    "encodes and compares plan versions" {
        val previous = "a,100,1000|b,100,2000|c,100,3000"
        val current = "a,100,1500|b,100,2000|d,100,4000"
        val diff = SchedulePlanVersionCodec.compare(previous, current)
        diff.moved shouldBe setOf("a")
        diff.unchanged shouldBe setOf("b")
        diff.added shouldBe setOf("d")
        diff.removed shouldBe setOf("c")
    }
})
