package com.neuroflow.app.data.repository

import com.neuroflow.app.data.local.dao.UlyssesContractDao
import com.neuroflow.app.data.local.entity.UlyssesContractEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UlyssesContractRepository @Inject constructor(
    private val dao: UlyssesContractDao
) {
    suspend fun insert(contract: UlyssesContractEntity) = dao.insert(contract)
    suspend fun update(contract: UlyssesContractEntity) = dao.update(contract)
    fun observeActive(): Flow<List<UlyssesContractEntity>> = dao.observeActive()
    fun observeArchived(): Flow<List<UlyssesContractEntity>> = dao.observeArchived()
    suspend fun getById(id: String): UlyssesContractEntity? = dao.getById(id)
    suspend fun getActiveForTask(taskId: String): UlyssesContractEntity? = dao.getActiveForTask(taskId)
}
