package com.neuroflow.app.data.repository

import com.neuroflow.app.data.local.dao.TimeSessionDao
import com.neuroflow.app.data.local.entity.TimeSessionEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository @Inject constructor(
    private val timeSessionDao: TimeSessionDao
) {
    fun observeByTaskId(taskId: String): Flow<List<TimeSessionEntity>> =
        timeSessionDao.observeByTaskId(taskId)

    fun observeAll(): Flow<List<TimeSessionEntity>> = timeSessionDao.observeAll()

    fun observeSessionsForDay(startOfDay: Long, endOfDay: Long): Flow<List<TimeSessionEntity>> =
        timeSessionDao.observeSessionsForDay(startOfDay, endOfDay)

    fun observeTotalMinutesForDay(startOfDay: Long, endOfDay: Long): Flow<Float?> =
        timeSessionDao.observeTotalMinutesForDay(startOfDay, endOfDay)

    suspend fun getByTaskId(taskId: String): List<TimeSessionEntity> =
        timeSessionDao.getByTaskId(taskId)

    suspend fun getOpenSessionForTask(taskId: String): TimeSessionEntity? =
        timeSessionDao.getOpenSessionForTask(taskId)

    fun observeOpenSessions(): Flow<List<TimeSessionEntity>> =
        timeSessionDao.observeOpenSessions()

    suspend fun getOpenSessions(): List<TimeSessionEntity> =
        timeSessionDao.getOpenSessions()

    suspend fun insert(session: TimeSessionEntity) = timeSessionDao.insert(session)
    suspend fun update(session: TimeSessionEntity) = timeSessionDao.update(session)
    suspend fun delete(session: TimeSessionEntity) = timeSessionDao.delete(session)
    suspend fun deleteAll() = timeSessionDao.deleteAll()
}
