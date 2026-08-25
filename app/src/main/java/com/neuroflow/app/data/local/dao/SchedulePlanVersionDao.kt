package com.neuroflow.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.neuroflow.app.data.local.entity.SchedulePlanVersionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SchedulePlanVersionDao {
    @Insert
    suspend fun insert(version: SchedulePlanVersionEntity)

    @Query("SELECT * FROM schedule_plan_versions ORDER BY createdAtMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int = 20): Flow<List<SchedulePlanVersionEntity>>

    @Query("SELECT * FROM schedule_plan_versions WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SchedulePlanVersionEntity?

    @Query("DELETE FROM schedule_plan_versions WHERE createdAtMillis < :beforeMillis")
    suspend fun deleteOlderThan(beforeMillis: Long): Int
}
