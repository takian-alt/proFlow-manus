package com.neuroflow.app.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AutonomyNudgeEngineNamingTest {

    @Test
    fun `unique work name and tag are stable and task scoped`() {
        val taskId = "task-42"

        val uniqueWork = AutonomyNudgeEngine.uniqueWorkName(taskId)
        val tag = AutonomyNudgeEngine.workTag(taskId)

        assertEquals("autonomy_nudge_task-42", uniqueWork)
        assertEquals("autonomy_nudge_task-42", tag)
    }

    @Test
    fun `different task ids produce different work names`() {
        val one = AutonomyNudgeEngine.uniqueWorkName("one")
        val two = AutonomyNudgeEngine.uniqueWorkName("two")

        assertNotEquals(one, two)
    }
}
