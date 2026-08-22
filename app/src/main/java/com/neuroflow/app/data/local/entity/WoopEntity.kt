package com.neuroflow.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "woop_data")
data class WoopEntity(
    @PrimaryKey val taskId: String,   // 1:1 with TaskEntity
    val wish: String = "",
    val outcome: String = "",
    val obstacle: String = "",
    val plan: String = "",            // if-then plan (auto-generated or user-edited)
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
