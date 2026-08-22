package com.neuroflow.app.presentation.launcher.hyperfocus.domain

import android.os.StrictMode
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class EncryptionException(message: String, cause: Throwable? = null) : Exception(message, cause)

object AESUtil {

    private const val KEY_ALIAS = "hyperfocus_code_key"
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val IV_SIZE_BYTES = 12
    @Volatile
    private var fallbackKey: SecretKey? = null

    suspend fun encrypt(plaintext: String): String = withContext(Dispatchers.IO) {
        runWithAllowedDiskReads {
            try {
                val key = getOrCreateKey()
                val cipher = Cipher.getInstance(ALGORITHM)
                cipher.init(Cipher.ENCRYPT_MODE, key)
                val iv = cipher.iv
                val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
                val combined = iv + ciphertext
                Base64.getEncoder().withoutPadding().encodeToString(combined)
            } catch (e: EncryptionException) {
                throw e
            } catch (e: Exception) {
                throw EncryptionException("Encryption failed", e)
            }
        }
    }

    suspend fun decrypt(encoded: String): String = withContext(Dispatchers.IO) {
        runWithAllowedDiskReads {
            try {
                val combined = Base64.getDecoder().decode(encoded)
                val iv = combined.copyOfRange(0, IV_SIZE_BYTES)
                val ciphertext = combined.copyOfRange(IV_SIZE_BYTES, combined.size)
                val key = getOrCreateKey()
                val cipher = Cipher.getInstance(ALGORITHM)
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
                val plaintext = cipher.doFinal(ciphertext)
                String(plaintext, Charsets.UTF_8)
            } catch (e: EncryptionException) {
                throw e
            } catch (e: Exception) {
                throw EncryptionException("Decryption failed", e)
            }
        }
    }

    private suspend fun getOrCreateKey(): SecretKey {
        return withContext(Dispatchers.IO) {
            try {
                val keyStore = KeyStore.getInstance("AndroidKeyStore")
                keyStore.load(null)
                val existingKey = keyStore.getKey(KEY_ALIAS, null)
                if (existingKey != null) {
                    return@withContext existingKey as SecretKey
                }
                generateNewKey(keyStore)
            } catch (e: EncryptionException) {
                throw e
            } catch (_: java.security.KeyStoreException) {
                getOrCreateFallbackKey()
            } catch (_: java.security.NoSuchAlgorithmException) {
                getOrCreateFallbackKey()
            } catch (e: Exception) {
                getOrCreateFallbackKey()
            }
        }
    }

    /**
     * Deletes the existing key (if any) and generates a fresh one.
     * Called when the existing key is invalidated (e.g. new biometric enrolled).
     * After this, all previously encrypted codes are unrecoverable — callers must regenerate the pool.
     */
    suspend fun resetKey() {
        withContext(Dispatchers.IO) {
            runWithAllowedDiskReads {
                try {
                    val keyStore = KeyStore.getInstance("AndroidKeyStore")
                    keyStore.load(null)
                    if (keyStore.containsAlias(KEY_ALIAS)) {
                        keyStore.deleteEntry(KEY_ALIAS)
                    }
                    generateNewKey(keyStore)
                } catch (_: Exception) { /* best effort */ }
            }
        }
    }

    private inline fun <T> runWithAllowedDiskReads(block: () -> T): T {
        val oldPolicy = StrictMode.allowThreadDiskReads()
        return try {
            block()
        } finally {
            StrictMode.setThreadPolicy(oldPolicy)
        }
    }

    private fun generateNewKey(keyStore: KeyStore): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun getOrCreateFallbackKey(): SecretKey {
        val existing = fallbackKey
        if (existing != null) return existing
        synchronized(this) {
            val cached = fallbackKey
            if (cached != null) return cached
            val generator = KeyGenerator.getInstance("AES")
            generator.init(256)
            val generated = generator.generateKey()
            fallbackKey = SecretKeySpec(generated.encoded, "AES")
            return fallbackKey as SecretKey
        }
    }
}
