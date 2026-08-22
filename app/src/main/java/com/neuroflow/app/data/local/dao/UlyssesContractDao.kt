package com.neuroflow.app.data.local.dao

import androidx.room.*
import com.neuroflow.app.data.local.entity.UlyssesContractEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UlyssesContractDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contract: UlyssesContractEntity)

    @Update
    suspend fun update(contract: UlyssesContractEntity)

    @Query("SELECT * FROM ulysses_contracts WHERE outcome IS NULL")
    fun observeActive(): Flow<List<UlyssesContractEntity>>

    @Query("SELECT * FROM ulysses_contracts WHERE outcome IS NOT NULL ORDER BY createdAt DESC")
    fun observeArchived(): Flow<List<UlyssesContractEntity>>

    @Query("SELECT * FROM ulysses_contracts WHERE id = :id")
    suspend fun getById(id: String): UlyssesContractEntity?

    @Query("SELECT * FROM ulysses_contracts WHERE taskId = :taskId AND outcome IS NULL")
    suspend fun getActiveForTask(taskId: String): UlyssesContractEntity?
}
