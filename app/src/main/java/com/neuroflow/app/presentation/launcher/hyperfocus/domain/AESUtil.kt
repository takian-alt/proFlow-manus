package com.neuroflow.app.presentation.launcher.hyperfocus.domain

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class EncryptionException(message: String, cause: Throwable? = null) : Exception(message, cause)

object AESUtil {

    private const val KEY_ALIAS = "hyperfocus_code_key"
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val IV_SIZE_BYTES = 12

    fun encrypt(plaintext: String): String {
        return try {
            val key = getOrCreateKey()
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            val combined = iv + ciphertext
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: EncryptionException) {
            throw e
        } catch (e: Exception) {
            throw EncryptionException("Encryption failed", e)
        }
    }

    fun decrypt(encoded: String): String {
        return try {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
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

    private fun getOrCreateKey(): SecretKey {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            val existingKey = keyStore.getKey(KEY_ALIAS, null)
            if (existingKey != null) {
                return existingKey as SecretKey
            }
            generateNewKey(keyStore)
        } catch (e: EncryptionException) {
            throw e
        } catch (e: Exception) {
            throw EncryptionException("Failed to get or create key", e)
        }
    }

    /**
     * Deletes the existing key (if any) and generates a fresh one.
     * Called when the existing key is invalidated (e.g. new biometric enrolled).
     * After this, all previously encrypted codes are unrecoverable — callers must regenerate the pool.
     */
    fun resetKey() {
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS)
            }
            generateNewKey(keyStore)
        } catch (_: Exception) { /* best effort */ }
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
}
