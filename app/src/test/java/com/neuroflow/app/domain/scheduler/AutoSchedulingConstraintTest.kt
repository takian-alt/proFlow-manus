package com.neuroflow.app.domain.scheduler

import com.neuroflow.app.data.local.UserPreferences
import com.neuroflow.app.data.local.UserPreferencesDataStore
import com.neuroflow.app.data.local.entity.TaskEntity
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

    "placement constraints reject preferred-weekday mismatch" {
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
