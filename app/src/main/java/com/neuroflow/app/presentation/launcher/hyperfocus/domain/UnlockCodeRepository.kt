package com.neuroflow.app.presentation.launcher.hyperfocus.domain

import com.neuroflow.app.domain.model.RewardTier
import com.neuroflow.app.presentation.launcher.hyperfocus.data.UnlockCodeDao
import com.neuroflow.app.presentation.launcher.hyperfocus.data.UnlockCodeEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

interface UnlockCodeRepository {
    suspend fun generateCodePool(sessionId: String)
    suspend fun getClaimableCode(sessionId: String, tier: RewardTier): Pair<String, String>?
    suspend fun getCodeById(codeId: String): String?
    suspend fun countUnusedByTier(sessionId: String, tier: RewardTier): Int
    suspend fun validateAndClaim(sessionId: String, enteredCode: String): UnlockCodeEntity?
    suspend fun markUsed(codeId: String, unlockedUntil: Long?)
    suspend fun deleteSessionCodes(sessionId: String)
    suspend fun deleteExpiredCodes()
}

@Singleton
class UnlockCodeRepositoryImpl @Inject constructor(
    private val dao: UnlockCodeDao
) : UnlockCodeRepository {

    // Visually unambiguous characters only (excludes 0, O, 1, I)
    private val CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    private fun generateCode(): String =
        (1..6).map { CODE_CHARS.random() }.joinToString("")

    override suspend fun generateCodePool(sessionId: String) {
        // One code per tier — user earns exactly one unlock per tier per session
        val entities = mutableListOf<UnlockCodeEntity>()
        for (tier in listOf(RewardTier.MICRO, RewardTier.PARTIAL, RewardTier.EARNED, RewardTier.FULL)) {
            val plaintext = generateCode()
            val encrypted = AESUtil.encrypt(plaintext)
            entities.add(UnlockCodeEntity(
                id = UUID.randomUUID().toString(),
                encryptedCode = encrypted,
                tier = tier,
                sessionId = sessionId,
                createdAt = System.currentTimeMillis()
            ))
        }
        dao.insertAll(entities)
    }

    override suspend fun getClaimableCode(sessionId: String, tier: RewardTier): Pair<String, String>? {
        val unused = dao.getUnusedBySession(sessionId)
        val picked = unused.firstOrNull { it.tier == tier } ?: return null
        val plaintext = AESUtil.decrypt(picked.encryptedCode)
        return picked.id to plaintext
    }

    override suspend fun countUnusedByTier(sessionId: String, tier: RewardTier): Int {
        return dao.countUnusedByTier(sessionId, tier)
    }

    override suspend fun getCodeById(codeId: String): String? {
        val entity = dao.getById(codeId) ?: return null
        return runCatching { AESUtil.decrypt(entity.encryptedCode) }.getOrNull()
    }

    override suspend fun validateAndClaim(sessionId: String, enteredCode: String): UnlockCodeEntity? {
        val normalized = enteredCode.trim().uppercase()
        val unused = dao.getUnusedBySession(sessionId)

        // Try to match against existing codes
        for (entity in unused) {
            val decrypted = runCatching { AESUtil.decrypt(entity.encryptedCode) }.getOrNull()
            if (decrypted == normalized) {
                return entity
            }
        }

        // If no match AND all decryptions failed (key was invalidated), regenerate pool
        val allDecryptFailed = unused.isNotEmpty() && unused.all { entity ->
            runCatching { AESUtil.decrypt(entity.encryptedCode) }.isFailure
        }
        if (allDecryptFailed) {
            AESUtil.resetKey()
            dao.deleteBySession(sessionId)
            generateCodePool(sessionId)
        }

        return null
    }

    override suspend fun markUsed(codeId: String, unlockedUntil: Long?) {
        dao.markUsed(codeId, System.currentTimeMillis(), unlockedUntil)
    }

    override suspend fun deleteSessionCodes(sessionId: String) {
        dao.deleteBySession(sessionId)
    }

    override suspend fun deleteExpiredCodes() {
        val expiry = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        dao.deleteExpiredCodes(expiry)
    }
}
