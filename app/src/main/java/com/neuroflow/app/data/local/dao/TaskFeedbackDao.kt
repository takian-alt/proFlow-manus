package com.neuroflow.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.neuroflow.app.data.local.entity.TaskFeedbackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskFeedbackDao {
    @Insert
    suspend fun insert(feedback: TaskFeedbackEntity)

    @Query("SELECT * FROM task_feedback WHERE taskId = :taskId ORDER BY createdAtMillis DESC")
    fun observeForTask(taskId: String): Flow<List<TaskFeedbackEntity>>

    @Query("SELECT * FROM task_feedback WHERE kind = :kind ORDER BY createdAtMillis DESC LIMIT :limit")
    suspend fun getRecentByKind(kind: String, limit: Int = 200): List<TaskFeedbackEntity>

    @Query("SELECT value, COUNT(*) AS count FROM task_feedback WHERE taskId = :taskId AND kind = :kind GROUP BY value ORDER BY count DESC")
    suspend fun summarizeForTask(taskId: String, kind: String): List<FeedbackCount>

    @Query("DELETE FROM task_feedback WHERE createdAtMillis < :beforeMillis")
    suspend fun deleteOlderThan(beforeMillis: Long): Int

    data class FeedbackCount(val value: String, val count: Int)
}
