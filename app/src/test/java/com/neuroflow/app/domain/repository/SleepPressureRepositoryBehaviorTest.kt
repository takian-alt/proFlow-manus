package com.neuroflow.app.domain.repository

import com.neuroflow.app.data.local.UserPreferences
import com.neuroflow.app.data.local.UserPreferencesDataStore
import com.neuroflow.app.data.local.entity.SleepLogEntity
import com.neuroflow.app.data.repository.SleepLogRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest

class SleepPressureRepositoryBehaviorTest : StringSpec({

    fun setupPrefs(
        initial: UserPreferences
    ): Pair<UserPreferencesDataStore, MutableStateFlow<UserPreferences>> {
        val preferencesDataStore = mockk<UserPreferencesDataStore>()
        var prefs = initial
        val flow = MutableStateFlow(prefs)

        every { preferencesDataStore.preferencesFlow } returns flow
        coEvery { preferencesDataStore.updatePreferences(any()) } coAnswers {
            val update = firstArg<(UserPreferences) -> UserPreferences>()
            prefs = update(prefs)
            flow.value = prefs
        }

        return preferencesDataStore to flow
    }

    fun <T> withDefaultTimeZone(zoneId: String, block: () -> T): T {
        val original = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(zoneId))
        return try {
            block()
        } finally {
            TimeZone.setDefault(original)
        }
    }

    "without sleep logs, pressure only accumulates while awake" {
        runTest {
            val trackingStart = 1_700_000_000_000L
            val now = trackingStart + 180L * 60_000L

            val (preferencesDataStore, _) = setupPrefs(
                UserPreferences(
                    sleepPressureTrackingStartedAtMillis = trackingStart,
                    sleepPressureLastComputedAtMillis = trackingStart,
                    sleepPressurePoints = 0
                )
            )

            val sleepLogRepository = mockk<SleepLogRepository>()
            coEvery { sleepLogRepository.getOverlapping(any(), any()) } returns emptyList()

            val repository = SleepPressureRepository(preferencesDataStore, sleepLogRepository)
            val snapshot = repository.refreshCurrentPressure(now)

            snapshot.pressurePoints shouldBe 180
        }
    }

    "sleep logs reduce pressure and removing logs changes recomputed pressure" {
        runTest {
            val trackingStart = 1_710_000_000_000L
            val now = trackingStart + 180L * 60_000L

            val (preferencesDataStore, _) = setupPrefs(
                UserPreferences(
                    sleepPressureTrackingStartedAtMillis = trackingStart,
                    sleepPressureLastComputedAtMillis = trackingStart,
                    sleepPressurePoints = 0
                )
            )

            val sleepLogRepository = mockk<SleepLogRepository>()
            val sleepLog = SleepLogEntity(
                id = "log-1",
                startAt = trackingStart + 30L * 60_000L,
                endAt = trackingStart + 120L * 60_000L,
                durationMinutes = 90,
                source = "MANUAL",
                notes = "",
                createdAt = trackingStart
            )

            var logs = mutableListOf(sleepLog)
            coEvery { sleepLogRepository.getOverlapping(any(), any()) } coAnswers {
                val from = firstArg<Long>()
                val to = secondArg<Long>()
                logs
                    .filter { it.endAt > from && it.startAt < to }
                    .sortedBy { it.startAt }
            }

            val repository = SleepPressureRepository(preferencesDataStore, sleepLogRepository)

            val withLog = repository.refreshCurrentPressure(now)
            withLog.pressurePoints shouldBe 60

            logs.clear()
            val withoutLog = repository.refreshCurrentPressure(now)
            withoutLog.pressurePoints shouldBe 180
            withoutLog.pressurePoints shouldBeGreaterThan withLog.pressurePoints
        }
    }

    "add sleep log rejects overlapping period" {
        runTest {
            val trackingStart = 1_720_000_000_000L

            val (preferencesDataStore, _) = setupPrefs(
                UserPreferences(
                    sleepPressureTrackingStartedAtMillis = trackingStart,
                    sleepPressureLastComputedAtMillis = trackingStart,
                    sleepPressurePoints = 120
                )
            )

            val sleepLogRepository = mockk<SleepLogRepository>()
            val existing = SleepLogEntity(
                id = "existing-log",
                startAt = trackingStart + 60L * 60_000L,
                endAt = trackingStart + 120L * 60_000L,
                durationMinutes = 60,
                source = "MANUAL",
                notes = "",
                createdAt = trackingStart
            )

            coEvery { sleepLogRepository.getOverlapping(any(), any()) } returns listOf(existing)
            coEvery { sleepLogRepository.addLog(any(), any(), any(), any()) } returns existing

            val repository = SleepPressureRepository(preferencesDataStore, sleepLogRepository)
            val result = repository.addSleepLog(
                startAtMillis = existing.startAt + 10L * 60_000L,
                endAtMillis = existing.endAt - 10L * 60_000L
            )

            result shouldBe SleepPressureRepository.AddSleepLogResult.OverlapsExisting
            coVerify(exactly = 0) {
                sleepLogRepository.addLog(any(), any(), any(), any())
            }
        }
    }

    "add sleep log rejects durations longer than max limit" {
        runTest {
            val trackingStart = 1_730_000_000_000L

            val (preferencesDataStore, _) = setupPrefs(
                UserPreferences(
                    sleepPressureTrackingStartedAtMillis = trackingStart,
                    sleepPressureLastComputedAtMillis = trackingStart,
                    sleepPressurePoints = 120
                )
            )

            val sleepLogRepository = mockk<SleepLogRepository>()
            coEvery { sleepLogRepository.getOverlapping(any(), any()) } returns emptyList()
            coEvery { sleepLogRepository.addLog(any(), any(), any(), any()) } returns SleepLogEntity(
                id = "new-log",
                startAt = trackingStart,
                endAt = trackingStart + 60_000L,
                durationMinutes = 1,
                source = "MANUAL",
                notes = "",
                createdAt = trackingStart
            )

            val repository = SleepPressureRepository(preferencesDataStore, sleepLogRepository)

            val tooLongEnd = trackingStart + (SleepPressureRepository.MAX_SLEEP_LOG_DURATION_MILLIS + 60_000L)
            val result = repository.addSleepLog(trackingStart, tooLongEnd)

            result shouldBe SleepPressureRepository.AddSleepLogResult.TooLong()
            coVerify(exactly = 0) {
                sleepLogRepository.addLog(any(), any(), any(), any())
            }
        }
    }

    "add sleep log normalizes zero-length interval to one minute before overlap check" {
        runTest {
            val trackingStart = 1_740_000_000_000L

            val (preferencesDataStore, _) = setupPrefs(
                UserPreferences(
                    sleepPressureTrackingStartedAtMillis = trackingStart,
                    sleepPressureLastComputedAtMillis = trackingStart,
                    sleepPressurePoints = 0
                )
            )

            val sleepLogRepository = mockk<SleepLogRepository>()
            coEvery { sleepLogRepository.getOverlapping(any(), any()) } returns emptyList()
            coEvery { sleepLogRepository.addLog(any(), any(), any(), any()) } returns SleepLogEntity(
                id = "new-log",
                startAt = trackingStart,
                endAt = trackingStart + 60_000L,
                durationMinutes = 1,
                source = "MANUAL",
                notes = "",
                createdAt = trackingStart
            )

            val repository = SleepPressureRepository(preferencesDataStore, sleepLogRepository)

            val result = repository.addSleepLog(trackingStart, trackingStart)

            (result is SleepPressureRepository.AddSleepLogResult.Added) shouldBe true
            coVerify(atLeast = 1) {
                sleepLogRepository.getOverlapping(trackingStart, trackingStart + 60_000L)
            }
            coVerify(exactly = 1) {
                sleepLogRepository.addLog(trackingStart, trackingStart + 60_000L, any(), any())
            }
        }
    }

    "first refresh without tracking start uses configured wake-up hour anchor" {
        runTest {
            val zoneId = ZoneId.systemDefault()
            val date = LocalDate.of(2026, 4, 13)
            val now = date.atTime(11, 40).atZone(zoneId).toInstant().toEpochMilli()
            val expectedStart = date.atTime(6, 0).atZone(zoneId).toInstant().toEpochMilli()
            val expectedPressure = ((now - expectedStart) / 60_000L).toInt()

            val (preferencesDataStore, _) = setupPrefs(
                UserPreferences(
                    wakeUpHour = 6,
                    sleepPressureTrackingStartedAtMillis = 0L,
                    sleepPressureLastComputedAtMillis = 0L,
                    sleepPressurePoints = 0
                )
            )

            val sleepLogRepository = mockk<SleepLogRepository>()
            coEvery { sleepLogRepository.getOverlapping(any(), any()) } returns emptyList()

            val repository = SleepPressureRepository(preferencesDataStore, sleepLogRepository)
            val snapshot = repository.refreshCurrentPressure(now)

            snapshot.pressurePoints shouldBe expectedPressure
        }
    }

    "clear reset recomputes from wake-up anchor instead of zeroing at current time" {
        runTest {
            val zoneId = ZoneId.systemDefault()
            val date = LocalDate.of(2026, 4, 13)
            val now = date.atTime(11, 40).atZone(zoneId).toInstant().toEpochMilli()
            val expectedStart = date.atTime(6, 0).atZone(zoneId).toInstant().toEpochMilli()
            val expectedPressure = ((now - expectedStart) / 60_000L).toInt()

            val (preferencesDataStore, _) = setupPrefs(
                UserPreferences(
                    wakeUpHour = 6,
                    sleepPressureTrackingStartedAtMillis = 0L,
                    sleepPressureLastComputedAtMillis = 0L,
                    sleepPressurePoints = 0
                )
            )

            val sleepLogRepository = mockk<SleepLogRepository>()
            coEvery { sleepLogRepository.deleteAll() } returns Unit

            val repository = SleepPressureRepository(preferencesDataStore, sleepLogRepository)
            val snapshot = repository.clearSleepLogsAndReset(now)

            snapshot.pressurePoints shouldBe expectedPressure
            snapshot.refreshedAtMillis shouldBe now
        }
    }

    "auto fallback sleep applies after wake plus 12h when no update happened yesterday" {
        runTest {
            val zoneId = ZoneId.systemDefault()
            val today = LocalDate.of(2026, 4, 13)
            val trackingStart = today.minusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            val now = today.atTime(19, 0).atZone(zoneId).toInstant().toEpochMilli()

            val (preferencesDataStore, _) = setupPrefs(
                UserPreferences(
                    wakeUpHour = 6,
                    sleepHour = 23,
                    sleepPressureTrackingStartedAtMillis = trackingStart,
                    sleepPressureLastComputedAtMillis = today.minusDays(2)
                        .atTime(10, 0)
                        .atZone(zoneId)
                        .toInstant()
                        .toEpochMilli(),
                    sleepPressurePoints = 0
                )
            )

            val sleepLogRepository = mockk<SleepLogRepository>()
            coEvery { sleepLogRepository.getOverlapping(any(), any()) } returns emptyList()
            coEvery { sleepLogRepository.addLog(any(), any(), any(), any()) } coAnswers {
                val start = firstArg<Long>()
                val end = secondArg<Long>()
                SleepLogEntity(
                    id = "auto-fallback",
                    startAt = start,
                    endAt = end,
                    durationMinutes = ((end - start) / 60_000L).toInt(),
                    source = "AUTO_DEFAULT",
                    notes = "",
                    createdAt = trackingStart
                )
            }

            val repository = SleepPressureRepository(preferencesDataStore, sleepLogRepository)
            val snapshot = repository.refreshCurrentPressure(now)

            // Awake 00:00->23:00 = 1380; default sleep 23:00->06:00 gives recovery 1302;
            // awake 06:00->19:00 = 780 => total 858.
            snapshot.pressurePoints shouldBe 858
            coVerify(exactly = 1) {
                sleepLogRepository.addLog(any(), any(), "AUTO_DEFAULT", any())
            }
        }
    }

    "auto fallback sleep does not apply before wake plus 12h" {
        runTest {
            val zoneId = ZoneId.systemDefault()
            val today = LocalDate.of(2026, 4, 13)
            val trackingStart = today.minusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            val now = today.atTime(13, 0).atZone(zoneId).toInstant().toEpochMilli()

            val (preferencesDataStore, _) = setupPrefs(
                UserPreferences(
                    wakeUpHour = 6,
                    sleepHour = 23,
                    sleepPressureTrackingStartedAtMillis = trackingStart,
                    sleepPressureLastComputedAtMillis = today.minusDays(2)
                        .atTime(10, 0)
                        .atZone(zoneId)
                        .toInstant()
                        .toEpochMilli(),
                    sleepPressurePoints = 0
                )
            )

            val sleepLogRepository = mockk<SleepLogRepository>()
            coEvery { sleepLogRepository.getOverlapping(any(), any()) } returns emptyList()

            val repository = SleepPressureRepository(preferencesDataStore, sleepLogRepository)
            val snapshot = repository.refreshCurrentPressure(now)

            // No fallback before wake+12h -> pure awake from tracking start.
            snapshot.pressurePoints shouldBe 2220
            coVerify(exactly = 0) {
                sleepLogRepository.addLog(any(), any(), any(), any())
            }
        }
    }

    "auto fallback sleep applies even when sleepPressureLastComputedAtMillis was set yesterday" {
        runTest {
            val zoneId = ZoneId.systemDefault()
            val today = LocalDate.of(2026, 4, 13)
            val trackingStart = today.minusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            val now = today.atTime(19, 0).atZone(zoneId).toInstant().toEpochMilli()
            val yesterdayNoon = today.minusDays(1).atTime(12, 0).atZone(zoneId).toInstant().toEpochMilli()

            val (preferencesDataStore, _) = setupPrefs(
                UserPreferences(
                    wakeUpHour = 6,
                    sleepHour = 23,
                    sleepPressureTrackingStartedAtMillis = trackingStart,
                    sleepPressureLastComputedAtMillis = yesterdayNoon,
                    sleepPressurePoints = 0
                )
            )

            val sleepLogRepository = mockk<SleepLogRepository>()
            coEvery { sleepLogRepository.getOverlapping(any(), any()) } returns emptyList()
            coEvery { sleepLogRepository.addLog(any(), any(), any(), any()) } coAnswers {
                val start = firstArg<Long>()
                val end = secondArg<Long>()
                SleepLogEntity(
                    id = "auto-fallback",
                    startAt = start,
                    endAt = end,
                    durationMinutes = ((end - start) / 60_000L).toInt(),
                    source = "AUTO_DEFAULT",
                    notes = "",
                    createdAt = trackingStart
                )
            }

            val repository = SleepPressureRepository(preferencesDataStore, sleepLogRepository)
            val snapshot = repository.refreshCurrentPressure(now)

            // sleepPressureLastComputedAtMillis being set yesterday (from a prior app open) must NOT
            // block the auto fallback — only a manual sleep log should block it.
            // Awake 00:00->23:00 = 1380; default sleep 23:00->06:00 gives recovery 1302;
            // awake 06:00->19:00 = 780 => total 858.
            snapshot.pressurePoints shouldBe 858
            coVerify(exactly = 1) {
                sleepLogRepository.addLog(any(), any(), "AUTO_DEFAULT", any())
            }
        }
    }

    "auto fallback sleep does not apply when there is a manual log today" {
        runTest {
            val zoneId = ZoneId.systemDefault()
            val today = LocalDate.of(2026, 4, 13)
            val trackingStart = today.minusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            val now = today.atTime(19, 0).atZone(zoneId).toInstant().toEpochMilli()

            val manualTodayStart = today.atTime(8, 0).atZone(zoneId).toInstant().toEpochMilli()
            val manualTodayEnd = today.atTime(8, 15).atZone(zoneId).toInstant().toEpochMilli()

            val (preferencesDataStore, _) = setupPrefs(
                UserPreferences(
                    wakeUpHour = 6,
                    sleepHour = 23,
                    sleepPressureTrackingStartedAtMillis = trackingStart,
                    sleepPressureLastComputedAtMillis = today.minusDays(2)
                        .atTime(10, 0)
                        .atZone(zoneId)
                        .toInstant()
                        .toEpochMilli(),
                    sleepPressurePoints = 0
                )
            )

            val sleepLogRepository = mockk<SleepLogRepository>()
            coEvery { sleepLogRepository.getOverlapping(any(), any()) } returns listOf(
                SleepLogEntity(
                    id = "manual-today",
                    startAt = manualTodayStart,
                    endAt = manualTodayEnd,
                    durationMinutes = 15,
                    source = "MANUAL",
                    notes = "",
                    createdAt = trackingStart
                )
            )

            val repository = SleepPressureRepository(preferencesDataStore, sleepLogRepository)
            val snapshot = repository.refreshCurrentPressure(now)

            // Manual log is respected, auto default fallback is skipped.
            snapshot.pressurePoints shouldBe 2550
            coVerify(exactly = 0) {
                sleepLogRepository.addLog(any(), any(), any(), any())
            }
        }
    }

    "auto fallback is blocked by a one-minute log today even if it is before tracking start" {
        runTest {
            val zoneId = ZoneId.systemDefault()
            val today = LocalDate.of(2026, 4, 13)
            val trackingStart = today.atTime(5, 30).atZone(zoneId).toInstant().toEpochMilli()
            val now = today.atTime(19, 0).atZone(zoneId).toInstant().toEpochMilli()

            val oneMinuteLogStart = today.atTime(5, 0).atZone(zoneId).toInstant().toEpochMilli()
            val oneMinuteLogEnd = today.atTime(5, 1).atZone(zoneId).toInstant().toEpochMilli()
            val logs = listOf(
                SleepLogEntity(
                    id = "one-minute-log",
                    startAt = oneMinuteLogStart,
                    endAt = oneMinuteLogEnd,
                    durationMinutes = 1,
                    source = "MANUAL",
                    notes = "",
                    createdAt = trackingStart
                )
            )

            val (preferencesDataStore, _) = setupPrefs(
                UserPreferences(
                    wakeUpHour = 6,
                    sleepHour = 23,
                    sleepPressureTrackingStartedAtMillis = trackingStart,
                    sleepPressureLastComputedAtMillis = today.minusDays(2)
                        .atTime(10, 0)
                        .atZone(zoneId)
                        .toInstant()
                        .toEpochMilli(),
                    sleepPressurePoints = 0
                )
            )

            val sleepLogRepository = mockk<SleepLogRepository>()
            coEvery { sleepLogRepository.getOverlapping(any(), any()) } coAnswers {
                val from = firstArg<Long>()
                val to = secondArg<Long>()
                logs
                    .filter { it.endAt > from && it.startAt < to }
                    .sortedBy { it.startAt }
            }

            val repository = SleepPressureRepository(preferencesDataStore, sleepLogRepository)
            val snapshot = repository.refreshCurrentPressure(now)

            // From 05:30 to 19:00 is 810 awake minutes; fallback must be blocked.
            snapshot.pressurePoints shouldBe 810
            coVerify(exactly = 0) {
                sleepLogRepository.addLog(any(), any(), any(), any())
            }
        }
    }

    "auto fallback is blocked by manual log since default sleep hour even if not today" {
        runTest {
            val zoneId = ZoneId.systemDefault()
            val today = LocalDate.of(2026, 4, 13)
            val trackingStart = today.atTime(0, 30).atZone(zoneId).toInstant().toEpochMilli()
            val now = today.atTime(19, 0).atZone(zoneId).toInstant().toEpochMilli()

            val manualPrevNightStart = today.minusDays(1).atTime(23, 30).atZone(zoneId).toInstant().toEpochMilli()
            val manualPrevNightEnd = today.minusDays(1).atTime(23, 31).atZone(zoneId).toInstant().toEpochMilli()
            val logs = listOf(
                SleepLogEntity(
                    id = "manual-prev-night",
                    startAt = manualPrevNightStart,
                    endAt = manualPrevNightEnd,
                    durationMinutes = 1,
                    source = "MANUAL",
                    notes = "",
                    createdAt = trackingStart
                )
            )

            val (preferencesDataStore, _) = setupPrefs(
                UserPreferences(
                    wakeUpHour = 6,
                    sleepHour = 23,
                    sleepPressureTrackingStartedAtMillis = trackingStart,
                    sleepPressureLastComputedAtMillis = today.minusDays(2)
                        .atTime(10, 0)
                        .atZone(zoneId)
                        .toInstant()
                        .toEpochMilli(),
                    sleepPressurePoints = 0
                )
            )

            val sleepLogRepository = mockk<SleepLogRepository>()
            coEvery { sleepLogRepository.getOverlapping(any(), any()) } coAnswers {
                val from = firstArg<Long>()
                val to = secondArg<Long>()
                logs
                    .filter { it.endAt > from && it.startAt < to }
                    .sortedBy { it.startAt }
            }

            val repository = SleepPressureRepository(preferencesDataStore, sleepLogRepository)
            val snapshot = repository.refreshCurrentPressure(now)

            // 00:30 -> 19:00 awake only, fallback must be blocked by manual log at 23:30 yesterday.
            snapshot.pressurePoints shouldBe 1110
            coVerify(exactly = 0) {
                sleepLogRepository.addLog(any(), any(), any(), any())
            }
        }
    }

    "default wake anchor handles spring-forward DST with elapsed minutes" {
        withDefaultTimeZone("America/New_York") {
            runTest {
                val zoneId = ZoneId.systemDefault()
                val date = LocalDate.of(2026, 3, 8)
                val now = date.atTime(10, 0).atZone(zoneId).toInstant().toEpochMilli()
                val expectedStart = date.atTime(1, 0).atZone(zoneId).toInstant().toEpochMilli()
                val expectedPressure = ((now - expectedStart) / 60_000L).toInt()

                val (preferencesDataStore, _) = setupPrefs(
                    UserPreferences(
                        wakeUpHour = 1,
                        sleepPressureTrackingStartedAtMillis = 0L,
                        sleepPressureLastComputedAtMillis = 0L,
                        sleepPressurePoints = 0
                    )
                )

                val sleepLogRepository = mockk<SleepLogRepository>()
                coEvery { sleepLogRepository.getOverlapping(any(), any()) } returns emptyList()
                coEvery { sleepLogRepository.addLog(any(), any(), any(), any()) } returns SleepLogEntity(
                    id = "unexpected-fallback",
                    startAt = now,
                    endAt = now + 60_000L,
                    durationMinutes = 1,
                    source = "AUTO_DEFAULT",
                    notes = "",
                    createdAt = now
                )

                val repository = SleepPressureRepository(preferencesDataStore, sleepLogRepository)
                val snapshot = repository.refreshCurrentPressure(now)

                snapshot.pressurePoints shouldBe expectedPressure
                coVerify(exactly = 0) {
                    sleepLogRepository.addLog(any(), any(), "AUTO_DEFAULT", any())
                }
            }
        }
    }

    "default wake anchor handles fall-back DST with repeated hour" {
        withDefaultTimeZone("America/New_York") {
            runTest {
                val zoneId = ZoneId.systemDefault()
                val date = LocalDate.of(2026, 11, 1)
                val now = date.atTime(10, 0).atZone(zoneId).toInstant().toEpochMilli()
                val expectedStart = date.atTime(0, 0).atZone(zoneId).toInstant().toEpochMilli()
                val expectedPressure = ((now - expectedStart) / 60_000L).toInt()

                val (preferencesDataStore, _) = setupPrefs(
                    UserPreferences(
                        wakeUpHour = 0,
                        sleepPressureTrackingStartedAtMillis = 0L,
                        sleepPressureLastComputedAtMillis = 0L,
                        sleepPressurePoints = 0
                    )
                )

                val sleepLogRepository = mockk<SleepLogRepository>()
                coEvery { sleepLogRepository.getOverlapping(any(), any()) } returns emptyList()
                coEvery { sleepLogRepository.addLog(any(), any(), any(), any()) } returns SleepLogEntity(
                    id = "unexpected-fallback",
                    startAt = now,
                    endAt = now + 60_000L,
                    durationMinutes = 1,
                    source = "AUTO_DEFAULT",
                    notes = "",
                    createdAt = now
                )

                val repository = SleepPressureRepository(preferencesDataStore, sleepLogRepository)
                val snapshot = repository.refreshCurrentPressure(now)

                snapshot.pressurePoints shouldBe expectedPressure
                coVerify(exactly = 0) {
                    sleepLogRepository.addLog(any(), any(), "AUTO_DEFAULT", any())
                }
            }
        }
    }
})
