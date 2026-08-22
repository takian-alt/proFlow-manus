package com.neuroflow.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "schedule_adjustments",
    indices = [Index(value = ["taskId"]), Index(value = ["createdAtMillis"])]
)
data class ScheduleAdjustmentEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val taskId: String,
    val previousScheduledDate: Long? = null,
    val previousScheduledTime: Long? = null,
    val newScheduledDate: Long? = null,
    val newScheduledTime: Long? = null,
    val source: String, // MANUAL, AUTO_APPROVED, FOCUS, UNDO
    val reason: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val undone: Boolean = false
)
