package com.neuroflow.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "schedule_plan_versions",
    indices = [Index(value = ["createdAtMillis"])]
)
data class SchedulePlanVersionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val createdAtMillis: Long = System.currentTimeMillis(),
    val source: String, // AUTO_APPLIED, REVIEW_APPROVED, MANUAL
    val summaryJson: String,
    val taskCount: Int
)
