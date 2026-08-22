package com.neuroflow.app.presentation.history

import com.neuroflow.app.data.local.entity.TaskEntity
import com.neuroflow.app.domain.model.TaskStatus
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.util.Calendar

class HistoryDateRangeFilterTest : StringSpec({

    val now = 1_710_000_000_000L
    val oneDayMs = 86_400_000L

    fun completedTask(id: String, completedAt: Long?) = TaskEntity(
        id = id,
        title = "Task $id",
        status = TaskStatus.COMPLETED,
        completedAt = completedAt
    )

    "ALL returns the full list" {
        val tasks = listOf(
            completedTask("a", now),
            completedTask("b", now - oneDayMs),
            completedTask("c", null)
        )

        val filtered = filterTasksByDateRange(tasks, HistoryDateRange.ALL, now)

        filtered.shouldContainExactly(tasks)
    }

    "TODAY keeps only tasks completed since start of day" {
        val startOfDay = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val tasks = listOf(
            completedTask("today-1", startOfDay + 1_000L),
            completedTask("today-2", startOfDay + 30_000L),
            completedTask("old", startOfDay - 1L)
        )

        val filtered = filterTasksByDateRange(tasks, HistoryDateRange.TODAY, now)

        filtered.map { it.id }.shouldContainExactly(listOf("today-1", "today-2"))
    }

    "LAST_7_DAYS excludes tasks older than 7-day window" {
        val tasks = listOf(
            completedTask("in-range", now - 3 * oneDayMs),
            completedTask("edge", now - 6 * oneDayMs),
            completedTask("out-range", now - 7 * oneDayMs - 1)
        )

        val filtered = filterTasksByDateRange(tasks, HistoryDateRange.LAST_7_DAYS, now)

        filtered.map { it.id }.shouldContainExactly(listOf("in-range", "edge"))
    }

    "LAST_30_DAYS excludes null completion timestamps" {
        val tasks = listOf(
            completedTask("in-range", now - 10 * oneDayMs),
            completedTask("null", null)
        )

        val filtered = filterTasksByDateRange(tasks, HistoryDateRange.LAST_30_DAYS, now)

        filtered.shouldHaveSize(1)
        filtered.first().id shouldBe "in-range"
    }
})
