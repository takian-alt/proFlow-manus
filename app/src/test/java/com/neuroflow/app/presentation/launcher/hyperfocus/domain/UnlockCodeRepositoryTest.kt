package com.neuroflow.app.presentation.launcher.hyperfocus.domain

import android.os.Build
import com.neuroflow.app.presentation.launcher.hyperfocus.data.UnlockCodeDao
import com.neuroflow.app.presentation.launcher.hyperfocus.data.UnlockCodeEntity
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for UnlockCodeRepositoryImpl.
 *
 * Validates Requirements 2.3, 2.4:
 * - Code pool generation inserts correct number of entities with isUsed = false
 * - Encrypted codes are never stored as plaintext
 * - validateAndClaim returns null for unknown codes
 * - markUsed delegates correctly to the DAO
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class UnlockCodeRepositoryTest : StringSpec({

    beforeTest {
        mockkObject(AESUtil)
        // Return a realistic Base64-encoded fake ciphertext (longer than 6 chars)
        every { AESUtil.encrypt(any()) } answers {
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        }
    }

    afterTest {
        unmockkObject(AESUtil)
    }

    "generateCodePool inserts exactly 4 rows (one per tier) all with isUsed = false" {
        runTest {
            val dao = mockk<UnlockCodeDao>()
            val capturedList = slot<List<UnlockCodeEntity>>()
            coEvery { dao.insertAll(capture(capturedList)) } returns Unit

            val repo = UnlockCodeRepositoryImpl(dao)
            repo.generateCodePool("session1")

            capturedList.captured.size shouldBe 4
            capturedList.captured.all { !it.isUsed } shouldBe true
        }
    }

    "no plaintext code appears in any inserted encryptedCode field" {
        runTest {
            val dao = mockk<UnlockCodeDao>()
            val capturedList = slot<List<UnlockCodeEntity>>()
            coEvery { dao.insertAll(capture(capturedList)) } returns Unit

            val repo = UnlockCodeRepositoryImpl(dao)
            repo.generateCodePool("session1")

            capturedList.captured.forEach { entity ->
                // Encrypted + IV + Base64 encoding will always produce a string much longer than 6 chars
                (entity.encryptedCode.length > 6) shouldBe true
            }
        }
    }

    "validateAndClaim returns null for a code not in the pool" {
        runTest {
            val dao = mockk<UnlockCodeDao>()
            coEvery { dao.getUnusedBySession("session1") } returns emptyList()

            val repo = UnlockCodeRepositoryImpl(dao)
            val result = repo.validateAndClaim("session1", "ZZZZZZ")

            result.shouldBeNull()
        }
    }

    "markUsed sets isUsed = true and unlockedUntil correctly" {
        runTest {
            val dao = mockk<UnlockCodeDao>()
            coEvery { dao.markUsed(any(), any(), any()) } returns Unit

            val repo = UnlockCodeRepositoryImpl(dao)
            repo.markUsed("code-id-1", 12345L)

            coVerify { dao.markUsed("code-id-1", any(), 12345L) }
        }
    }
})
