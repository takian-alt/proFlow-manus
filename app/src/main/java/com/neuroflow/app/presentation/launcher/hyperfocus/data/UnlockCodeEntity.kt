package com.neuroflow.app.presentation.launcher.hyperfocus.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.neuroflow.app.domain.model.RewardTier

@Entity(tableName = "unlock_codes")
data class UnlockCodeEntity(
    @PrimaryKey val id: String,
    val encryptedCode: String,
    val tier: RewardTier,
    val sessionId: String,
    val isUsed: Boolean = false,
    val usedAt: Long? = null,
    val unlockedUntil: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
