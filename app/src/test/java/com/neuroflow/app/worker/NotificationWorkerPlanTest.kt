package com.neuroflow.app.worker

import com.neuroflow.app.data.local.UserPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class NotificationWorkerPlanTest {

    @Test
    fun `delayUntilHour returns positive delay to same day target`() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 26, 6, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val delay = delayUntilHour(7, now)

        assertTrue(delay > 0)
        assertTrue(delay <= 30 * 60 * 1000L)
    }

    @Test
    fun `delayUntilHour rolls to next day when hour already passed`() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 26, 22, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val delay = delayUntilHour(21, now)

        assertTrue(delay > 23 * 60 * 60 * 1000L)
        assertTrue(delay <= 24 * 60 * 60 * 1000L)
    }

    @Test
    fun `buildNotificationWorkerPlan reflects toggles and hours`() {
        val prefs = UserPreferences(
            dailyPlanNotificationsEnabled = true,
            streakNotificationsEnabled = false,
            deadlineEscalationNotificationsEnabled = true,
            dailyPlanNotificationHour = 8,
            streakCheckNotificationHour = 20
        )

        val now = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 26, 7, 15, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val plan = buildNotificationWorkerPlan(prefs, now)

        assertTrue(plan.dailyPlanEnabled)
        assertFalse(plan.streakEnabled)
        assertTrue(plan.escalationEnabled)
        assertTrue(plan.dailyPlanDelayMs > 0)
        assertTrue(plan.streakDelayMs > 0)
    }

    @Test
    fun `delayUntilHour clamps invalid hour inputs`() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 26, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val low = delayUntilHour(-5, now)
        val high = delayUntilHour(40, now)

        assertEquals(delayUntilHour(0, now), low)
        assertEquals(delayUntilHour(23, now), high)
    }
}
