package com.neuroflow.app.domain.engine

import com.neuroflow.app.data.local.UserPreferences
import com.neuroflow.app.data.local.entity.TaskEntity
import com.neuroflow.app.domain.model.EnergyLevel
import com.neuroflow.app.domain.model.Priority
import com.neuroflow.app.domain.model.Quadrant
import com.neuroflow.app.domain.model.TaskStatus
import org.junit.Assert.*
import org.junit.Test

class TaskScoringEngineTest {

    private val defaultPrefs = UserPreferences()
    private val now = System.currentTimeMillis()

    private fun makeTask(
        quadrant: Quadrant = Quadrant.SCHEDULE,
        priority: Priority = Priority.MEDIUM,
        deadlineDate: Long? = null,
        estimatedDuration: Int = 0,
        impactScore: Int = 50,
        isFrog: Boolean = false,
        postponeCount: Int = 0
    ) = TaskEntity(
        title = "Test Task",
        quadrant = quadrant,
        priority = priority,
        deadlineDate = deadlineDate,
        estimatedDurationMinutes = estimatedDuration,
        impactScore = impactScore,
        isFrog = isFrog,
        postponeCount = postponeCount
    )

    @Test
    fun `DO_FIRST quadrant scores higher than ELIMINATE`() {
        val doFirst = makeTask(quadrant = Quadrant.DO_FIRST)
        val eliminate = makeTask(quadrant = Quadrant.ELIMINATE)
        val scoreDoFirst = TaskScoringEngine.score(doFirst, defaultPrefs, now)
        val scoreEliminate = TaskScoringEngine.score(eliminate, defaultPrefs, now)
        assertTrue("DO_FIRST ($scoreDoFirst) should score higher than ELIMINATE ($scoreEliminate)",
            scoreDoFirst > scoreEliminate)
    }

    @Test
    fun `HIGH priority scores higher than LOW`() {
        val high = makeTask(priority = Priority.HIGH)
        val low = makeTask(priority = Priority.LOW)
        assertTrue(TaskScoringEngine.score(high, defaultPrefs, now) >
                TaskScoringEngine.score(low, defaultPrefs, now))
    }

    @Test
    fun `overdue deadline scores higher than far-future deadline`() {
        val overdue = makeTask(deadlineDate = now - 3_600_000) // 1 hour ago
        val future = makeTask(deadlineDate = now + 30 * 86_400_000L) // 30 days
        assertTrue(TaskScoringEngine.score(overdue, defaultPrefs, now) >
                TaskScoringEngine.score(future, defaultPrefs, now))
    }

    @Test
    fun `postponed tasks score higher than non-postponed`() {
        val postponed = makeTask(postponeCount = 5)
        val fresh = makeTask(postponeCount = 0)
        assertTrue(TaskScoringEngine.score(postponed, defaultPrefs, now) >
                TaskScoringEngine.score(fresh, defaultPrefs, now))
    }

    @Test
    fun `sortedByScore filters out completed tasks`() {
        val tasks = listOf(
            makeTask(priority = Priority.HIGH),
            makeTask(priority = Priority.LOW).copy(status = TaskStatus.COMPLETED)
        )
        val sorted = TaskScoringEngine.sortedByScore(tasks, defaultPrefs, now)
        assertEquals(1, sorted.size)
        assertEquals(Priority.HIGH, sorted[0].priority)
    }

    @Test
    fun `autoAssignQuadrant assigns DO_FIRST for urgent important tasks`() {
        val task = makeTask(priority = Priority.HIGH, impactScore = 80, deadlineDate = now + 24 * 3_600_000)
        val quadrant = TaskScoringEngine.autoAssignQuadrant(task, now)
        assertEquals(Quadrant.DO_FIRST, quadrant)
    }

    @Test
    fun `autoAssignQuadrant assigns SCHEDULE for important non-urgent tasks`() {
        val task = makeTask(priority = Priority.LOW, impactScore = 80, deadlineDate = now + 30 * 86_400_000L)
        val quadrant = TaskScoringEngine.autoAssignQuadrant(task, now)
        assertEquals(Quadrant.SCHEDULE, quadrant)
    }

    @Test
    fun `autoAssignQuadrant assigns ELIMINATE for non-urgent non-important`() {
        val task = makeTask(priority = Priority.LOW, impactScore = 20)
        val quadrant = TaskScoringEngine.autoAssignQuadrant(task, now)
        assertEquals(Quadrant.ELIMINATE, quadrant)
    }
}
