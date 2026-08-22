package com.neuroflow.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.neuroflow.app.data.local.entity.EnergyPredictionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EnergyPredictionDao {
    @Insert
    suspend fun insert(entity: EnergyPredictionEntity)

    @Query("SELECT * FROM energy_predictions ORDER BY predictedAtMillis DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 100): List<EnergyPredictionEntity>

    @Query("SELECT * FROM energy_predictions WHERE predictedAtMillis >= :startMillis AND predictedAtMillis <= :endMillis ORDER BY predictedAtMillis DESC")
    suspend fun getInRange(startMillis: Long, endMillis: Long): List<EnergyPredictionEntity>

    @Query("SELECT COUNT(*) FROM energy_predictions")
    suspend fun count(): Int

    @Query("DELETE FROM energy_predictions WHERE createdAtMillis < :beforeMillis")
    suspend fun deleteOlderThan(beforeMillis: Long): Int

    @Query("DELETE FROM energy_predictions")
    suspend fun deleteAll(): Int

    @Query("SELECT * FROM energy_predictions WHERE predictedAtMillis >= :afterMillis ORDER BY predictedAtMillis DESC")
    fun observeAfter(afterMillis: Long): Flow<List<EnergyPredictionEntity>>
}
