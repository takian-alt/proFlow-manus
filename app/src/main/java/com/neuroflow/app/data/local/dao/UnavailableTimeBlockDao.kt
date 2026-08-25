package com.neuroflow.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.neuroflow.app.data.local.entity.UnavailableTimeBlockEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UnavailableTimeBlockDao {
    @Insert
    suspend fun insert(block: UnavailableTimeBlockEntity)

    @Delete
    suspend fun delete(block: UnavailableTimeBlockEntity)

    @Query("SELECT * FROM unavailable_time_blocks WHERE startMillis < :endMillis AND endMillis > :startMillis ORDER BY startMillis")
    suspend fun getOverlapping(startMillis: Long, endMillis: Long): List<UnavailableTimeBlockEntity>

    @Query("SELECT * FROM unavailable_time_blocks ORDER BY startMillis")
    fun observeAll(): Flow<List<UnavailableTimeBlockEntity>>

    @Query("DELETE FROM unavailable_time_blocks WHERE endMillis < :beforeMillis")
    suspend fun deleteOlderThan(beforeMillis: Long): Int
}
