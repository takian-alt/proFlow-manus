package com.neuroflow.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Durable learning record for one autoscheduling decision.
 * Lists are stored as JSON so the scheduler can retain rejected alternatives
 * without adding a second relation or Room type converter.
 */
@Entity(
    tableName = "auto_schedule_telemetry",
    indices = [Index(value = ["taskId"]), Index(value = ["generatedAtMillis"])]
)
data class AutoScheduleTelemetryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val taskId: String,
    val generatedAtMillis: Long,
    val horizonDays: Int,
    val wasApplied: Boolean,
    val selectedSlotDate: Long? = null,
    val selectedSlotTime: Long? = null,
    val candidateSlotStartMillisJson: String = "[]",
    val rejectedCandidateSlotStartMillisJson: String = "[]",
    val rejectionReason: String? = null,
    val assignmentReason: String = "",
    val fitScore: Float = 0f,
    val energyMatch: Float = 0f,
    val tagFit: Float = 0f,
    val deadlineUrgency: Float = 0f,
    val confidence: Float = 0f,
    val energyScore: Float = 0f,
    val deadlinePressure: Float = 0f,
    val estimatedDurationMinutes: Int = 0,
    val userAdjustment: String? = null,
    val outcome: String? = null,
    val userFeedbackAtMillis: Long? = null
)
