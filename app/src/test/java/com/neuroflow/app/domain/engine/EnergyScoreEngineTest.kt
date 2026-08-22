package com.neuroflow.app.domain.engine

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.util.Calendar
import kotlin.math.abs

class EnergyScoreEngineTest : StringSpec({

    "circadian factor follows configured peak amplitudes" {
        closeTo(EnergyScoreEngine.circadianFactor(105), 1.0f)
        closeTo(EnergyScoreEngine.circadianFactor(645), 0.8f)
        closeTo(EnergyScoreEngine.circadianFactor(840), 0.6f)
        closeTo(EnergyScoreEngine.circadianFactor(400), 0.0f)
    }

    "raw and usable energy combine peak and fatigue" {
        val snapshot = EnergyScoreEngine.EnergySnapshot(
            peakEnergy = detectionResult(),
            sleepPressurePoints = 1500,
            nowMillis = millisAtMinuteOfDay(105)
        )

        val result = EnergyScoreEngine.calculateDetailed(snapshot)

        closeTo(result.reservoirFactor, 0.97375f)
        closeTo(result.circadianFactor, 1.0f)
        closeTo(result.peakScore, 97.375f)
        closeTo(result.fatiguePenalty, 56.64419f, tolerance = 0.01f)
        closeTo(result.rawEnergy, 40.73081f, tolerance = 0.01f)
        closeTo(result.usableEnergy, 70.3654f, tolerance = 0.01f)
    }

    "sleep pressure penalty clamps at soft max" {
        val result = EnergyScoreEngine.calculateDetailed(
            EnergyScoreEngine.EnergySnapshot(
                sleepPressurePoints = 9999,
                softMaxReference = 3000
            )
        )

        closeTo(result.fatiguePenalty, 100.0f)
        closeTo(result.rawEnergy, -100.0f)
        closeTo(result.usableEnergy, 0.0f)
    }

    "calculate returns raw energy for compatibility" {
        val snapshot = EnergyScoreEngine.EnergySnapshot(
            peakEnergy = detectionResult(confidence = 1.0f),
            sleepPressurePoints = 800,
            nowMillis = millisAtMinuteOfDay(105)
        )

        val rawFromCalculate = EnergyScoreEngine.calculate(snapshot)
        val rawFromDetailed = EnergyScoreEngine.calculateDetailed(snapshot).rawEnergy

        closeTo(rawFromCalculate, rawFromDetailed)
    }

    "usable energy is normalized from raw energy bounds" {
        val max = EnergyScoreEngine.calculateDetailed(
            EnergyScoreEngine.EnergySnapshot(
                peakEnergy = detectionResult(confidence = 1.0f),
                sleepPressurePoints = 0,
                nowMillis = millisAtMinuteOfDay(105)
            )
        )
        val min = EnergyScoreEngine.calculateDetailed(
            EnergyScoreEngine.EnergySnapshot(
                sleepPressurePoints = 9999
            )
        )

        closeTo(max.usableEnergy, ((max.rawEnergy + 100f) / 2f).coerceIn(0f, 100f))
        closeTo(min.usableEnergy, ((min.rawEnergy + 100f) / 2f).coerceIn(0f, 100f))
    }

})

private fun closeTo(actual: Float, expected: Float, tolerance: Float = 0.001f) {
    (abs(actual - expected) <= tolerance) shouldBe true
}

private fun detectionResult(confidence: Float = 1.0f): PeakEnergyEngine.DetectionResult {
    return PeakEnergyEngine.DetectionResult(
        chronotype = MEQChronotypeDetector.Chronotype.DEFINITE_MORNING,
        wakeUpHour = 6,
        peakOffsetHours = 0f,
        peakHourOfDay = 6,
        peakMinuteOfDay = 0,
        peakValue = PeakEnergyEngine.PEAK_VALUE,
        confidence = confidence
    )
}

private fun millisAtMinuteOfDay(minuteOfDay: Int): Long {
    val safeMinuteOfDay = ((minuteOfDay % 1440) + 1440) % 1440
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, safeMinuteOfDay / 60)
        set(Calendar.MINUTE, safeMinuteOfDay % 60)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return calendar.timeInMillis
}
