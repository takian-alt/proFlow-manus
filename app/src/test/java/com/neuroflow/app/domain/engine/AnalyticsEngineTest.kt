package com.neuroflow.app.domain.engine

import com.neuroflow.app.data.local.entity.TaskEntity
import com.neuroflow.app.domain.model.TaskStatus
import org.junit.Assert.*
import org.junit.Test

class AnalyticsEngineTest {

    @Test
    fun `computeMape returns correct value`() {
        val mape = AnalyticsEngine.computeMape(estimated = 60f, actual = 90f)
        assertEquals(50f, mape, 0.1f) // |90-60|/60 * 100 = 50%
    }

    @Test
    fun `computeMape returns 0 when estimated is 0`() {
        assertEquals(0f, AnalyticsEngine.computeMape(0f, 50f), 0.01f)
    }

    @Test
    fun `computeSmape returns correct value`() {
        val smape = AnalyticsEngine.computeSmape(estimated = 60f, actual = 90f)
        // |90-60| / ((60+90)/2) * 100 = 30/75 * 100 = 40%
        assertEquals(40f, smape, 0.1f)
    }

    @Test
    fun `computeSmape returns 0 when both are 0`() {
        assertEquals(0f, AnalyticsEngine.computeSmape(0f, 0f), 0.01f)
    }

    @Test
    fun `computeWeightedMape with known values`() {
        val tasks = listOf(
            TaskEntity(
                title = "Task 1",
                estimatedDurationMinutes = 60,
                actualDurationMinutes = 90f,
                status = TaskStatus.COMPLETED
            ),
            TaskEntity(
                title = "Task 2",
                estimatedDurationMinutes = 30,
                actualDurationMinutes = 30f,
                status = TaskStatus.COMPLETED
            )
        )
        val wMape = AnalyticsEngine.computeWeightedMape(tasks)
        // Task1: MAPE=50%, weight=60; Task2: MAPE=0%, weight=30
        // Weighted = (50*60 + 0*30) / (60+30) = 3000/90 = 33.33%
        assertEquals(33.33f, wMape, 0.5f)
    }

    @Test
    fun `mapeGrade returns correct labels`() {
        assertEquals("✨ Excellent time estimation!", AnalyticsEngine.mapeGrade(5f))
        assertEquals("👍 Good estimation, minor drift", AnalyticsEngine.mapeGrade(15f))
        assertEquals("⚠ Moderate estimation error", AnalyticsEngine.mapeGrade(35f))
        assertEquals("🚨 High estimation error — recalibrate", AnalyticsEngine.mapeGrade(60f))
    }

    @Test
    fun `computeWeightedMape with no valid tasks returns 0`() {
        val tasks = listOf(
            TaskEntity(title = "No estimate", estimatedDurationMinutes = 0)
        )
        assertEquals(0f, AnalyticsEngine.computeWeightedMape(tasks), 0.01f)
    }
}
