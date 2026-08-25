package com.neuroflow.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.neuroflow.app.data.local.entity.ScheduleAdjustmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleAdjustmentDao {
    @Insert
    suspend fun insert(adjustment: ScheduleAdjustmentEntity)

    @Query("SELECT * FROM schedule_adjustments WHERE undone = 0 ORDER BY createdAtMillis DESC LIMIT 1")
    fun observeLatestUndoable(): Flow<ScheduleAdjustmentEntity?>

    @Query("SELECT * FROM schedule_adjustments ORDER BY createdAtMillis DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 500): List<ScheduleAdjustmentEntity>

    @Query("SELECT * FROM schedule_adjustments WHERE taskId = :taskId ORDER BY createdAtMillis DESC LIMIT :limit")
    suspend fun getForTask(taskId: String, limit: Int = 50): List<ScheduleAdjustmentEntity>

    @Query("UPDATE schedule_adjustments SET undone = 1 WHERE id = :id")
    suspend fun markUndone(id: String)

    @Query("DELETE FROM schedule_adjustments WHERE createdAtMillis < :beforeMillis")
    suspend fun deleteOlderThan(beforeMillis: Long): Int
}
