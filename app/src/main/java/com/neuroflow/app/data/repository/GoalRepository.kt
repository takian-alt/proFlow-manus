package com.neuroflow.app.data.repository

import com.neuroflow.app.data.local.dao.GoalDao
import com.neuroflow.app.data.local.entity.GoalEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoalRepository @Inject constructor(
    private val goalDao: GoalDao
) {
    fun observeActiveGoals(): Flow<List<GoalEntity>> = goalDao.observeActiveGoals()
    fun observeAll(): Flow<List<GoalEntity>> = goalDao.observeAll()

    suspend fun getById(id: String): GoalEntity? = goalDao.getById(id)
    suspend fun insert(goal: GoalEntity) = goalDao.insert(goal)
    suspend fun update(goal: GoalEntity) = goalDao.update(goal)
    suspend fun delete(goal: GoalEntity) = goalDao.delete(goal)
    suspend fun deleteAll() = goalDao.deleteAll()
}
