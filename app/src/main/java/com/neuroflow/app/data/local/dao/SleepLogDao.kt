package com.neuroflow.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.neuroflow.app.data.local.entity.SleepLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SleepLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: SleepLogEntity)

    @Query("SELECT * FROM sleep_logs ORDER BY startAt DESC")
    fun observeAll(): Flow<List<SleepLogEntity>>

    @Query("SELECT * FROM sleep_logs ORDER BY startAt DESC")
    suspend fun getAll(): List<SleepLogEntity>

    @Query("SELECT * FROM sleep_logs WHERE endAt > :fromMillis AND startAt < :toMillis ORDER BY startAt ASC")
    suspend fun getOverlapping(fromMillis: Long, toMillis: Long): List<SleepLogEntity>

    @Query("DELETE FROM sleep_logs WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM sleep_logs")
    suspend fun deleteAll()
}
