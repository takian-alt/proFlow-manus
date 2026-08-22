package com.neuroflow.app.presentation.launcher.hyperfocus.domain

import android.os.Build
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for AESUtil.
 *
 * Validates: Requirements 2.3
 *
 * Tests:
 * - Round-trip encryption/decryption preserves plaintext
 * - Two encryptions of the same plaintext produce different ciphertext (IV randomness)
 * - Malformed input throws EncryptionException
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class AESUtilTest : StringSpec({

    "round-trip: decrypt(encrypt(plaintext)) should equal plaintext for ABC123" {
        val plaintext = "ABC123"
        AESUtil.decrypt(AESUtil.encrypt(plaintext)) shouldBe plaintext
    }

    "round-trip: decrypt(encrypt(plaintext)) should equal plaintext for XYZ789" {
        val plaintext = "XYZ789"
        AESUtil.decrypt(AESUtil.encrypt(plaintext)) shouldBe plaintext
    }

    "round-trip: decrypt(encrypt(plaintext)) should equal plaintext for MNPQRS" {
        val plaintext = "MNPQRS"
        AESUtil.decrypt(AESUtil.encrypt(plaintext)) shouldBe plaintext
    }

    "uniqueness: two encryptions of the same plaintext should produce different ciphertext" {
        val plaintext = "ABC123"
        val first = AESUtil.encrypt(plaintext)
        val second = AESUtil.encrypt(plaintext)
        first shouldNotBe second
    }

    "decrypt should throw EncryptionException for malformed input" {
        shouldThrow<EncryptionException> {
            AESUtil.decrypt("not-valid-base64!!!")
        }
    }
})

