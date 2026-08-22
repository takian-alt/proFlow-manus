package com.neuroflow.app.data.local.dao

import androidx.room.*
import com.neuroflow.app.data.local.entity.TaskEntity
import com.neuroflow.app.domain.model.Quadrant
import com.neuroflow.app.domain.model.TaskStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity)

    @Update
    suspend fun update(task: TaskEntity)

    @Delete
    suspend fun delete(task: TaskEntity)

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observeById(id: String): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE status = :status ORDER BY createdAt DESC")
    fun observeByStatus(status: TaskStatus): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE quadrant = :quadrant AND status = 'ACTIVE' ORDER BY createdAt DESC")
    fun observeByQuadrant(quadrant: Quadrant): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE status = 'ACTIVE' ORDER BY createdAt DESC")
    fun observeActiveTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE status = 'COMPLETED' ORDER BY completedAt DESC")
    fun observeCompletedTasks(): Flow<List<TaskEntity>>

    @Query("""
        SELECT * FROM tasks
        WHERE scheduledDate >= :dayStart AND scheduledDate < :dayEnd
        AND status = 'ACTIVE'
        ORDER BY scheduledTime ASC
    """)
    fun observeTasksForDate(dayStart: Long, dayEnd: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE status = 'ACTIVE'")
    suspend fun getActiveTasks(): List<TaskEntity>

    @Query("SELECT * FROM tasks")
    suspend fun getAllTasks(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE status = 'COMPLETED'")
    suspend fun getCompletedTasks(): List<TaskEntity>

    @Query("SELECT COUNT(*) FROM tasks WHERE status = 'ACTIVE' AND quadrant = :quadrant")
    fun observeQuadrantCount(quadrant: Quadrant): Flow<Int>

    @Query("SELECT * FROM tasks WHERE contextTag = :tag AND status = 'ACTIVE'")
    fun observeByContextTag(tag: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE goalId = :goalId AND status = 'ACTIVE'")
    fun observeByGoalId(goalId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE parentTaskId = :parentId")
    fun observeSubtasks(parentId: String): Flow<List<TaskEntity>>

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()

    @Query("UPDATE tasks SET estimationErrorMape = NULL, estimationErrorSmape = NULL, actualDurationMinutes = NULL WHERE status = 'COMPLETED'")
    suspend fun resetEstimationErrors()
}
