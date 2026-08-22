package com.neuroflow.app.data.local.dao

import androidx.room.*
import com.neuroflow.app.data.local.entity.WoopEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WoopDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(woop: WoopEntity)

    @Query("SELECT * FROM woop_data WHERE taskId = :taskId")
    suspend fun getByTaskId(taskId: String): WoopEntity?

    @Query("SELECT * FROM woop_data WHERE taskId = :taskId")
    fun observeByTaskId(taskId: String): Flow<WoopEntity?>

    @Query("DELETE FROM woop_data WHERE taskId = :taskId")
    suspend fun deleteByTaskId(taskId: String)
}
