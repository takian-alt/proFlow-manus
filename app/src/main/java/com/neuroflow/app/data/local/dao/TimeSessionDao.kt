package com.neuroflow.app.data.local.dao

import androidx.room.*
import com.neuroflow.app.data.local.entity.TimeSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TimeSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: TimeSessionEntity)

    @Update
    suspend fun update(session: TimeSessionEntity)

    @Delete
    suspend fun delete(session: TimeSessionEntity)

    @Query("SELECT * FROM time_sessions WHERE taskId = :taskId ORDER BY startedAt DESC")
    fun observeByTaskId(taskId: String): Flow<List<TimeSessionEntity>>

    @Query("SELECT * FROM time_sessions WHERE taskId = :taskId ORDER BY startedAt DESC")
    suspend fun getByTaskId(taskId: String): List<TimeSessionEntity>

    @Query("SELECT * FROM time_sessions WHERE taskId = :taskId AND endedAt IS NULL LIMIT 1")
    suspend fun getOpenSessionForTask(taskId: String): TimeSessionEntity?

    @Query("SELECT * FROM time_sessions WHERE endedAt IS NULL ORDER BY startedAt DESC")
    fun observeOpenSessions(): Flow<List<TimeSessionEntity>>

    @Query("SELECT * FROM time_sessions WHERE endedAt IS NULL ORDER BY startedAt DESC")
    suspend fun getOpenSessions(): List<TimeSessionEntity>

    @Query("SELECT * FROM time_sessions ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<TimeSessionEntity>>

    @Query("SELECT * FROM time_sessions ORDER BY startedAt DESC")
    suspend fun getAll(): List<TimeSessionEntity>

    @Query("SELECT * FROM time_sessions WHERE startedAt >= :startOfDay AND startedAt < :endOfDay")
    fun observeSessionsForDay(startOfDay: Long, endOfDay: Long): Flow<List<TimeSessionEntity>>

    @Query("SELECT SUM(durationMinutes) FROM time_sessions WHERE startedAt >= :startOfDay AND startedAt < :endOfDay")
    fun observeTotalMinutesForDay(startOfDay: Long, endOfDay: Long): Flow<Float?>

    @Query("DELETE FROM time_sessions")
    suspend fun deleteAll()
}
