package com.neuroflow.app.data.repository

import com.neuroflow.app.data.local.dao.WoopDao
import com.neuroflow.app.data.local.entity.WoopEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WoopRepository @Inject constructor(
    private val woopDao: WoopDao
) {
    suspend fun upsert(woop: WoopEntity) = woopDao.upsert(woop)
    suspend fun getByTaskId(taskId: String): WoopEntity? = woopDao.getByTaskId(taskId)
    fun observeByTaskId(taskId: String): Flow<WoopEntity?> = woopDao.observeByTaskId(taskId)
    suspend fun deleteByTaskId(taskId: String) = woopDao.deleteByTaskId(taskId)
}
