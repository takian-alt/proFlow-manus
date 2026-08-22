package com.neuroflow.app.domain.repository

import com.neuroflow.app.data.local.UserPreferences
import com.neuroflow.app.data.local.UserPreferencesDataStore
import com.neuroflow.app.data.local.entity.SleepLogEntity
import com.neuroflow.app.data.local.entity.TaskEntity
import com.neuroflow.app.data.local.entity.TimeSessionEntity
import com.neuroflow.app.data.repository.SessionRepository
import com.neuroflow.app.data.repository.SleepLogRepository
import com.neuroflow.app.data.repository.TaskRepository
import com.neuroflow.app.domain.engine.MEQChronotypeDetector
import com.neuroflow.app.domain.engine.PeakEnergyEngine
import com.neuroflow.app.presentation.launcher.data.NotificationBadgeManager
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout

class EnergyScoreRepositoryContractTest : StringSpec({

    "observeEnergy exposes stale freshness fields and keeps energy bounds" {
        runTest {
            val now = System.currentTimeMillis()
            val staleSessionStart = now - (20L * 60_000L)
            val staleTaskUpdate = now - (30L * 60_000L)
            val staleDetectionAt = now - (40L * 60_000L)

            val preferencesDataStore = mockk<UserPreferencesDataStore>()
            every { preferencesDataStore.preferencesFlow } returns MutableStateFlow(
                UserPreferences(
                    sleepPressurePoints = 900,
                    energyTelemetryEnabled = false
                )
            )

            val peakEnergyRepository = mockk<PeakEnergyRepository>()
            every { peakEnergyRepository.peakEnergyFlow } returns MutableStateFlow(
                detectionResult(
                    confidence = 0.78f,
                    detectedAtMillis = staleDetectionAt
                )
            )

            val sessionRepository = mockk<SessionRepository>()
            every { sessionRepository.observeAll() } returns MutableStateFlow(
                listOf(
                    TimeSessionEntity(
                        taskId = "task-1",
                        startedAt = staleSessionStart,
                        endedAt = staleSessionStart + (12L * 60_000L),
                        durationMinutes = 12f
                    )
                )
            )

            val taskRepository = mockk<TaskRepository>()
            every { taskRepository.observeAll() } returns MutableStateFlow(
                listOf(
                    TaskEntity(
                        title = "Contract task",
                        createdAt = staleTaskUpdate,
                        updatedAt = staleTaskUpdate
                    )
                )
            )

            val badgeManager = NotificationBadgeManager()
            val energyMetricsRepository = mockk<EnergyMetricsRepository>(relaxed = true)
            coEvery { energyMetricsRepository.enforceRetentionPolicy(any(), any()) } returns 0

            val sleepLogRepository = mockk<SleepLogRepository>()
            every { sleepLogRepository.observeAll() } returns MutableStateFlow(emptyList())

            val repository = EnergyScoreRepository(
                preferencesDataStore = preferencesDataStore,
                peakEnergyRepository = peakEnergyRepository,
                sessionRepository = sessionRepository,
                taskRepository = taskRepository,
                sleepLogRepository = sleepLogRepository,
                notificationBadgeManager = badgeManager,
                energyMetricsRepository = energyMetricsRepository
            )

            val model = withTimeout(2_000L) {
                repository.observeEnergy(refreshIntervalMillis = 60_000L).first()
            }

            (model.availableEnergy in 0..100) shouldBe true
            model.hasRecentData shouldBe false
            model.momentSignalSummary.isNotBlank() shouldBe true
            model.sessionDataAgeMillis shouldBeGreaterThan (19L * 60_000L)
            model.taskDataAgeMillis shouldBeGreaterThan (29L * 60_000L)
            model.overallFreshnessAgeMillis shouldBeGreaterThan (5L * 60_000L)

            coVerify(exactly = 0) {
                energyMetricsRepository.recordEnergyPrediction(
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
                )
            }
            coVerify(exactly = 0) { energyMetricsRepository.enforceRetentionPolicy(any(), any()) }
        }
    }

    "observeEnergy enforces telemetry retention with clamped setting" {
        runTest {
            val now = System.currentTimeMillis()

            val preferencesDataStore = mockk<UserPreferencesDataStore>()
            every { preferencesDataStore.preferencesFlow } returns MutableStateFlow(
                UserPreferences(
                    sleepPressurePoints = 700,
                    energyTelemetryEnabled = true,
                    energyTelemetryRetentionDays = 999
                )
            )

            val peakEnergyRepository = mockk<PeakEnergyRepository>()
            every { peakEnergyRepository.peakEnergyFlow } returns MutableStateFlow(
                detectionResult(
                    confidence = 0.82f,
                    detectedAtMillis = now - (60_000L)
                )
            )

            val sessionRepository = mockk<SessionRepository>()
            every { sessionRepository.observeAll() } returns MutableStateFlow(emptyList())

            val taskRepository = mockk<TaskRepository>()
            every { taskRepository.observeAll() } returns MutableStateFlow(emptyList())

            val badgeManager = NotificationBadgeManager()
            val energyMetricsRepository = mockk<EnergyMetricsRepository>(relaxed = true)
            coEvery { energyMetricsRepository.enforceRetentionPolicy(any(), any()) } returns 0

            val sleepLogRepository2 = mockk<SleepLogRepository>()
            every { sleepLogRepository2.observeAll() } returns MutableStateFlow(emptyList())

            val repository = EnergyScoreRepository(
                preferencesDataStore = preferencesDataStore,
                peakEnergyRepository = peakEnergyRepository,
                sessionRepository = sessionRepository,
                taskRepository = taskRepository,
                sleepLogRepository = sleepLogRepository2,
                notificationBadgeManager = badgeManager,
                energyMetricsRepository = energyMetricsRepository
            )

            withTimeout(2_000L) {
                repository.observeEnergy(refreshIntervalMillis = 60_000L).first()
            }

            coVerify(exactly = 1) {
                energyMetricsRepository.recordEnergyPrediction(
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
                )
            }
            coVerify(exactly = 1) {
                energyMetricsRepository.enforceRetentionPolicy(
                    EnergyMetricsRepository.MAX_RETENTION_DAYS,
                    any()
                )
            }
        }
    }
})

private fun detectionResult(
    confidence: Float,
    detectedAtMillis: Long
): PeakEnergyEngine.DetectionResult {
    return PeakEnergyEngine.DetectionResult(
        chronotype = MEQChronotypeDetector.Chronotype.INTERMEDIATE,
        wakeUpHour = 7,
        peakOffsetHours = 2.5f,
        peakHourOfDay = 9,
        peakMinuteOfDay = 540,
        confidence = confidence,
        detectedAtMillis = detectedAtMillis
    )
}
