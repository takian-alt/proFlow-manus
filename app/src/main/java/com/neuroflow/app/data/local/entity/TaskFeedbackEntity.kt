package com.neuroflow.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "task_feedback",
    indices = [Index(value = ["taskId"]), Index(value = ["createdAtMillis"])]
)
data class TaskFeedbackEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val taskId: String,
    val kind: String, // MISSED, FOCUS_TIME, FOCUS_ENERGY, FOCUS_SCHEDULE, POSTPONED
    val value: String,
    val createdAtMillis: Long = System.currentTimeMillis()
)
