package com.neuroflow.app.data.repository

import com.neuroflow.app.data.local.dao.SleepLogDao
import com.neuroflow.app.data.local.entity.SleepLogEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SleepLogRepository @Inject constructor(
    private val sleepLogDao: SleepLogDao
) {

    companion object {
        private const val MIN_DURATION_MILLIS = 60_000L
    }

    fun observeAll(): Flow<List<SleepLogEntity>> = sleepLogDao.observeAll()

    suspend fun getAll(): List<SleepLogEntity> = sleepLogDao.getAll()

    suspend fun getOverlapping(fromMillis: Long, toMillis: Long): List<SleepLogEntity> =
        sleepLogDao.getOverlapping(fromMillis, toMillis)

    suspend fun addLog(
        startAt: Long,
        endAt: Long,
        source: String = "MANUAL",
        notes: String = ""
    ): SleepLogEntity {
        val safeStart = minOf(startAt, endAt)
        val safeEnd = maxOf(startAt, endAt)
        val normalizedEnd = if (safeEnd <= safeStart) safeStart + MIN_DURATION_MILLIS else safeEnd
        val durationMinutes = ((normalizedEnd - safeStart) / 60_000L).toInt().coerceAtLeast(1)
        val log = SleepLogEntity(
            startAt = safeStart,
            endAt = normalizedEnd,
            durationMinutes = durationMinutes,
            source = source,
            notes = notes
        )
        sleepLogDao.upsert(log)
        return log
    }

    suspend fun deleteById(id: String) = sleepLogDao.deleteById(id)

    suspend fun deleteAll() = sleepLogDao.deleteAll()
}
