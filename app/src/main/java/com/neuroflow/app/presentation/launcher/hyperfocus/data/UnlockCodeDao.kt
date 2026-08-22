package com.neuroflow.app.presentation.launcher.hyperfocus.data

import androidx.room.*
import com.neuroflow.app.domain.model.RewardTier

@Dao
interface UnlockCodeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(codes: List<UnlockCodeEntity>)

    @Query("SELECT * FROM unlock_codes WHERE sessionId = :sessionId AND isUsed = 0")
    suspend fun getUnusedBySession(sessionId: String): List<UnlockCodeEntity>

    @Query("SELECT COUNT(*) FROM unlock_codes WHERE sessionId = :sessionId AND tier = :tier AND isUsed = 0")
    suspend fun countUnusedByTier(sessionId: String, tier: RewardTier): Int

    @Query("SELECT * FROM unlock_codes WHERE id = :id")
    suspend fun getById(id: String): UnlockCodeEntity?

    @Query("UPDATE unlock_codes SET isUsed = 1, usedAt = :usedAt, unlockedUntil = :unlockedUntil WHERE id = :id")
    suspend fun markUsed(id: String, usedAt: Long, unlockedUntil: Long?)

    @Query("DELETE FROM unlock_codes WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: String)

    @Query("DELETE FROM unlock_codes WHERE createdAt < :expiryTime")
    suspend fun deleteExpiredCodes(expiryTime: Long)
}
