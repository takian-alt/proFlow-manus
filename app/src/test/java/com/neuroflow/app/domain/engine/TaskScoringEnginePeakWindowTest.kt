package com.neuroflow.app.domain.engine

import com.neuroflow.app.data.local.UserPreferences
import com.neuroflow.app.data.local.entity.TaskEntity
import com.neuroflow.app.domain.model.EnergyLevel
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeLessThan
import java.util.Calendar

class TaskScoringEnginePeakWindowTest : StringSpec({

    "energy matching respects peak window that wraps past midnight" {
        val prefs = UserPreferences(
            quizPeakEnabled = false,
            peakEnergyStart = 22,
            peakEnergyEnd = 2
        )

        val highEnergyTask = baseTask().copy(energyLevel = EnergyLevel.HIGH)
        val lowEnergyTask = baseTask().copy(energyLevel = EnergyLevel.LOW)

        val wrappedPeakMillis = millisAtHourMinute(hour = 1, minute = 30)
        val highDuringWrappedPeak = TaskScoringEngine.score(highEnergyTask, prefs, nowMillis = wrappedPeakMillis)
        val lowDuringWrappedPeak = TaskScoringEngine.score(lowEnergyTask, prefs, nowMillis = wrappedPeakMillis)

        highDuringWrappedPeak shouldBeGreaterThan lowDuringWrappedPeak

        val lowEnergySlotMillis = millisAtHourMinute(hour = 14, minute = 0)
        val highDuringLowEnergySlot = TaskScoringEngine.score(highEnergyTask, prefs, nowMillis = lowEnergySlotMillis)
        val lowDuringLowEnergySlot = TaskScoringEngine.score(lowEnergyTask, prefs, nowMillis = lowEnergySlotMillis)

        highDuringLowEnergySlot shouldBeLessThan lowDuringLowEnergySlot
    }

    "confidence-gated abstention suppresses adaptive peak-window bias in ranking" {
        val adaptivePrefs = UserPreferences(
            quizPeakEnabled = true,
            quizChronotype = "INTERMEDIATE",
            effectivePeakMinuteOfDay = 60,
            effectivePeakStart = 1,
            effectivePeakEnd = 4,
            peakEnergyStart = 9,
            peakEnergyEnd = 12,
            peakConfidenceAbstentionEnabled = false
        )
        val abstentionPrefs = adaptivePrefs.copy(peakConfidenceAbstentionEnabled = true)

        val highEnergyTask = baseTask().copy(energyLevel = EnergyLevel.HIGH)
        val lowEnergyTask = baseTask().copy(energyLevel = EnergyLevel.LOW)
        val nowInAdaptiveWindow = millisAtHourMinute(hour = 1, minute = 30)

        val adaptiveHigh = TaskScoringEngine.score(highEnergyTask, adaptivePrefs, nowMillis = nowInAdaptiveWindow)
        val adaptiveLow = TaskScoringEngine.score(lowEnergyTask, adaptivePrefs, nowMillis = nowInAdaptiveWindow)
        val abstainHigh = TaskScoringEngine.score(highEnergyTask, abstentionPrefs, nowMillis = nowInAdaptiveWindow)
        val abstainLow = TaskScoringEngine.score(lowEnergyTask, abstentionPrefs, nowMillis = nowInAdaptiveWindow)

        val adaptiveGap = adaptiveHigh - adaptiveLow
        val abstainGap = abstainHigh - abstainLow

        adaptiveGap shouldBeGreaterThan abstainGap
    }
})

private fun baseTask(): TaskEntity {
    return TaskEntity(
        title = "Peak window test",
        energyLevel = EnergyLevel.MEDIUM
    )
}

private fun millisAtHourMinute(hour: Int, minute: Int): Long {
    return Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
