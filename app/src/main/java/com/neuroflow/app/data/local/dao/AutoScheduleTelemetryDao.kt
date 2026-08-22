package com.neuroflow.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.neuroflow.app.data.local.entity.AutoScheduleTelemetryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AutoScheduleTelemetryDao {
    @Insert
    suspend fun insert(entity: AutoScheduleTelemetryEntity)

    @Insert
    suspend fun insertAll(entities: List<AutoScheduleTelemetryEntity>)

    @Query("SELECT * FROM auto_schedule_telemetry WHERE taskId = :taskId ORDER BY generatedAtMillis DESC LIMIT :limit")
    suspend fun getForTask(taskId: String, limit: Int = 100): List<AutoScheduleTelemetryEntity>

    @Query("SELECT * FROM auto_schedule_telemetry ORDER BY generatedAtMillis DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 100): List<AutoScheduleTelemetryEntity>

    @Query("SELECT * FROM auto_schedule_telemetry WHERE generatedAtMillis >= :afterMillis ORDER BY generatedAtMillis DESC")
    fun observeAfter(afterMillis: Long): Flow<List<AutoScheduleTelemetryEntity>>

    @Query("UPDATE auto_schedule_telemetry SET userAdjustment = :adjustment, outcome = :outcome, userFeedbackAtMillis = :feedbackAtMillis WHERE id = :id")
    suspend fun recordFeedback(id: String, adjustment: String?, outcome: String?, feedbackAtMillis: Long)

    @Query("DELETE FROM auto_schedule_telemetry WHERE generatedAtMillis < :beforeMillis")
    suspend fun deleteOlderThan(beforeMillis: Long): Int

    @Query("DELETE FROM auto_schedule_telemetry")
    suspend fun deleteAll(): Int
}
