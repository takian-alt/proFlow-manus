package com.neuroflow.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "sleep_logs",
    indices = [
        Index(value = ["startAt"]),
        Index(value = ["endAt"])
    ]
)
data class SleepLogEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val startAt: Long,
    val endAt: Long,
    val durationMinutes: Int,
    val source: String = "MANUAL",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
