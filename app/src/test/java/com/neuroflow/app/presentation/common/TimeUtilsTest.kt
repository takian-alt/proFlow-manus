package com.neuroflow.app.presentation.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.*

class TimeUtilsTest {

    @Test
    fun `formatRelativeTime for today with time shows hours`() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 24, 8, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val target = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 24, 17, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        assertEquals("In 9 hrs", formatRelativeTime(target, hasTime = true, now = now))
    }

    @Test
    fun `formatRelativeTime for tomorrow plus hours is 1 day 9 hrs, not just tomorrow`() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 24, 8, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val target = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 25, 17, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        assertEquals("In 1 day 9 hrs", formatRelativeTime(target, hasTime = true, now = now))
    }

    @Test
    fun `formatRelativeTime no time same day returns Today`() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 24, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val target = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 24, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        assertEquals("Today", formatRelativeTime(target, hasTime = false, now = now))
    }

    @Test
    fun `formatRelativeTime no time next day returns Tomorrow`() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 24, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val target = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 25, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        assertEquals("Tomorrow", formatRelativeTime(target, hasTime = false, now = now))
    }
}
