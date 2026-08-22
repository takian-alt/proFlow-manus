package com.neuroflow.app.presentation.launcher.hyperfocus.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.neuroflow.app.domain.model.HyperFocusState
import com.neuroflow.app.domain.model.RewardTier

@Entity(tableName = "hyperfocus_sessions")
data class HyperFocusSessionEntity(
    @PrimaryKey val id: String,           // UUID
    val startedAt: Long,
    val state: HyperFocusState,
    val blockedPackages: String,          // JSON-serialized List<String>
    val dailyTaskTarget: Int,
    val tasksCompletedAtStart: Int,
    val currentTier: RewardTier,
    val fullyUnlockedAt: Long? = null,
    val endedAt: Long? = null
)
