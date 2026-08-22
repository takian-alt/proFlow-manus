package com.neuroflow.app.presentation.analytics

import com.neuroflow.app.data.local.entity.EnergyPredictionEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class AbstentionRateTrend7dTest {

    @Test
    fun `buildAbstentionRateTrend7d computes per-day abstention percentage`() {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.of(2026, 4, 16)
        val nowMillis = today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

        val predictions = listOf(
            predictionAt(id = "t1", millis = atDay(today, 9, zone), peakConfidence = 0.3f),
            predictionAt(id = "t2", millis = atDay(today, 11, zone), peakConfidence = 0.8f),
            predictionAt(id = "t3", millis = atDay(today, 17, zone), peakConfidence = 0.2f),
            predictionAt(id = "y1", millis = atDay(today.minusDays(1), 10, zone), peakConfidence = 0.6f),
            predictionAt(id = "y2", millis = atDay(today.minusDays(1), 13, zone), peakConfidence = 0.7f),
            predictionAt(id = "d2", millis = atDay(today.minusDays(2), 8, zone), peakConfidence = 0.1f)
        )

        val trend = buildAbstentionRateTrend7d(predictions, nowMillis)

        assertEquals(7, trend.size)
        assertEquals(100f, trend[4].second, 0.001f) // two days ago
        assertEquals(0f, trend[5].second, 0.001f)   // yesterday
        assertEquals(66.666f, trend[6].second, 0.01f) // today
    }

    @Test
    fun `buildAbstentionRateTrend7d ignores predictions older than 7-day window`() {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.of(2026, 4, 16)
        val nowMillis = today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

        val oldPrediction = predictionAt(
            id = "old",
            millis = atDay(today.minusDays(8), 12, zone),
            peakConfidence = 0.1f
        )

        val trend = buildAbstentionRateTrend7d(listOf(oldPrediction), nowMillis)

        assertEquals(7, trend.size)
        trend.forEach { (_, rate) ->
            assertEquals(0f, rate, 0.001f)
        }
    }

    private fun atDay(date: LocalDate, hour: Int, zone: ZoneId): Long {
        return date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    }

    private fun predictionAt(id: String, millis: Long, peakConfidence: Float): EnergyPredictionEntity {
        val zoned = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
        return EnergyPredictionEntity(
            id = id,
            predictedAtMillis = millis,
            predictedAtDayOfWeek = zoned.dayOfWeek.value,
            predictedAtHourOfDay = zoned.hour,
            peakDetectionAgeMillis = 0L,
            sleepLogAgeMillis = 0L,
            sessionDataAgeMillis = 0L,
            baselineRawEnergy = 0f,
            peakScore = 0f,
            fatiguePenalty = 0f,
            sleepPressurePoints = 0,
            fatiguePercent = 0,
            momentAdjustment = 0f,
            momentConfidence = 0f,
            momentSupportScore = 0f,
            momentPressureScore = 0f,
            adjustedRawEnergy = 0f,
            usableEnergy = 0,
            chronotype = "INTERMEDIATE",
            wakeUpHour = 7,
            peakMinuteOfDay = 420,
            peakConfidence = peakConfidence,
            recentFocusMinutes = 0f,
            recentInterruptionCount = 0,
            recentAppSwitchCount = 0,
            activeTaskCount = 0,
            notificationCount = 0,
            createdAtMillis = millis
        )
    }
}
