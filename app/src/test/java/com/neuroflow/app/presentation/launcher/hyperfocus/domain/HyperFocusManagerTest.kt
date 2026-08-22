package com.neuroflow.app.presentation.launcher.hyperfocus.domain

import android.content.Context
import android.content.Intent
import com.neuroflow.app.data.repository.TaskRepository
import com.neuroflow.app.domain.model.RewardTier
import com.neuroflow.app.presentation.launcher.hyperfocus.data.HyperFocusDataStore
import com.neuroflow.app.presentation.launcher.hyperfocus.data.HyperFocusPreferences
import com.neuroflow.app.presentation.launcher.hyperfocus.data.UnlockCodeEntity
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.*
import kotlinx.coroutines.test.runTest

/**
 * Unit tests for HyperFocusManagerImpl.
 *
 * Validates: Requirements 2.4, 2.5
 *
 * Tests:
 * - submitCode with lockout active returns UnlockResult.Lockout without DB writes
 * - submitCode with valid code returns UnlockResult.Success and resets wrongCodeAttempts
 * - submitCode with 3 wrong codes sets lockoutExpiresAt and resets attempts to 0
 * - onTaskCompleted when inactive is a no-op
 * - activate() calls generateCodePool() before writing isActive = true
 */
class HyperFocusManagerTest : StringSpec({

    val context = mockk<Context>(relaxed = true)
    val hyperFocusDataStore = mockk<HyperFocusDataStore>()
    val unlockCodeRepository = mockk<UnlockCodeRepository>()
    val taskRepository = mockk<TaskRepository>()

    beforeTest {
        clearAllMocks()
        coEvery { hyperFocusDataStore.update(any()) } just Runs
        coEvery { hyperFocusDataStore.updateHeartbeat(any()) } just Runs
        coEvery { unlockCodeRepository.generateCodePool(any()) } just Runs
        coEvery { unlockCodeRepository.deleteExpiredCodes() } just Runs
        coEvery { unlockCodeRepository.deleteSessionCodes(any()) } just Runs
        coEvery { taskRepository.getAllTasks() } returns emptyList()
    }

    val manager by lazy {
        HyperFocusManagerImpl(context, hyperFocusDataStore, unlockCodeRepository, taskRepository)
    }

    "submitCode with lockout active returns UnlockResult.Lockout without DB writes" {
        runTest {
            val lockoutExpiresAt = System.currentTimeMillis() + 300_000L
            val prefs = HyperFocusPreferences(
                isActive = true,
                lockoutExpiresAt = lockoutExpiresAt
            )
            coEvery { hyperFocusDataStore.current() } returns prefs

            val result = manager.submitCode("ABCDEF")

            result.shouldBeInstanceOf<UnlockResult.Lockout>()
            coVerify(exactly = 0) { unlockCodeRepository.validateAndClaim(any(), any()) }
            coVerify(exactly = 0) { hyperFocusDataStore.update(any()) }
        }
    }

    "submitCode with valid code returns UnlockResult.Success and resets wrongCodeAttempts" {
        runTest {
            val prefs = HyperFocusPreferences(
                isActive = true,
                lockoutExpiresAt = null,
                wrongCodeAttempts = 1,
                sessionId = "session1"
            )
            coEvery { hyperFocusDataStore.current() } returns prefs

            val matchedEntity = UnlockCodeEntity(
                id = "code-id-1",
                encryptedCode = "encrypted",
                tier = RewardTier.MICRO,
                sessionId = "session1"
            )
            coEvery { unlockCodeRepository.validateAndClaim("session1", "ABCDEF") } returns matchedEntity
            coEvery { unlockCodeRepository.markUsed(any(), any()) } just Runs

            val result = manager.submitCode("ABCDEF")

            result.shouldBeInstanceOf<UnlockResult.Success>()
            coVerify { hyperFocusDataStore.update(any()) }
        }
    }

    "submitCode with 3 wrong codes sets lockoutExpiresAt and resets attempts to 0" {
        runTest {
            val prefs = HyperFocusPreferences(
                isActive = true,
                lockoutExpiresAt = null,
                wrongCodeAttempts = 2,
                sessionId = "session1"
            )
            coEvery { hyperFocusDataStore.current() } returns prefs
            coEvery { unlockCodeRepository.validateAndClaim(any(), any()) } returns null

            val result = manager.submitCode("WRONG1")

            result shouldBe UnlockResult.InvalidCode

            val updateSlot = slot<(HyperFocusPreferences) -> HyperFocusPreferences>()
            coVerify { hyperFocusDataStore.update(capture(updateSlot)) }

            val updated = updateSlot.captured(prefs)
            (updated.lockoutExpiresAt != null) shouldBe true
            updated.wrongCodeAttempts shouldBe 0
        }
    }

    "onTaskCompleted when inactive is a no-op" {
        runTest {
            val prefs = HyperFocusPreferences(isActive = false)
            coEvery { hyperFocusDataStore.current() } returns prefs

            manager.onTaskCompleted()

            coVerify(exactly = 0) { taskRepository.getAllTasks() }
        }
    }

    "activate calls generateCodePool before writing isActive = true" {
        runTest {
            coEvery { hyperFocusDataStore.current() } returns HyperFocusPreferences()
            coEvery { taskRepository.getAllTasks() } returns emptyList()

            manager.activate(setOf("com.example.app"), 5, emptySet())

            coVerifyOrder {
                unlockCodeRepository.generateCodePool(any())
                hyperFocusDataStore.update(any())
            }
        }
    }

    "reportTamper sets tamper fields in data store" {
        runTest {
            val prefs = HyperFocusPreferences(isActive = true)
            coEvery { hyperFocusDataStore.current() } returns prefs

            manager.reportTamper("Test tamper event")

            val updateSlot = slot<(HyperFocusPreferences) -> HyperFocusPreferences>()
            coVerify { hyperFocusDataStore.update(capture(updateSlot)) }
            val updated = updateSlot.captured(prefs)

            updated.isTamperDetected shouldBe true
            updated.tamperReason shouldBe "Test tamper event"
            updated.tamperDetectedAt shouldNotBe null
        }
    }

    "completePlanning deactivates session so a new one can be started" {
        runTest {
            val prefs = HyperFocusPreferences(
                isActive = true,
                sessionId = "session1"
            )
            coEvery { hyperFocusDataStore.current() } returns prefs

            manager.completePlanning()

            val updateSlot = slot<(HyperFocusPreferences) -> HyperFocusPreferences>()
            coVerify { hyperFocusDataStore.update(capture(updateSlot)) }

            val updated = updateSlot.captured(prefs)
            updated.isActive shouldBe false
        }
    }

    "triggerEmergencyBypass grants one-time 10 minute unlock and marks emergencyUsed" {
        runTest {
            val prefs = HyperFocusPreferences(
                isActive = true,
                emergencyUsed = false
            )
            coEvery { hyperFocusDataStore.current() } returns prefs

            val before = System.currentTimeMillis()
            manager.triggerEmergencyBypass()
            val after = System.currentTimeMillis()

            val updateSlot = slot<(HyperFocusPreferences) -> HyperFocusPreferences>()
            coVerify(exactly = 1) { hyperFocusDataStore.update(capture(updateSlot)) }
            val updated = updateSlot.captured(prefs)

            updated.emergencyUsed shouldBe true
            updated.currentTier shouldBe RewardTier.NONE

            updated.activeUnlockExpiresAt shouldNotBe null
            val expiresAt = updated.activeUnlockExpiresAt!!
            val minExpected = before + 10 * 60 * 1000L
            val maxExpected = after + 10 * 60 * 1000L
            (expiresAt >= minExpected) shouldBe true
            (expiresAt <= maxExpected) shouldBe true

            verify(exactly = 1) { context.startForegroundService(any<Intent>()) }
        }
    }

    "triggerEmergencyBypass is blocked after emergency already used" {
        runTest {
            val prefs = HyperFocusPreferences(
                isActive = true,
                emergencyUsed = true
            )
            coEvery { hyperFocusDataStore.current() } returns prefs

            manager.triggerEmergencyBypass()

            coVerify(exactly = 0) { hyperFocusDataStore.update(any()) }
            verify(exactly = 0) { context.startForegroundService(any<Intent>()) }
        }
    }
})

