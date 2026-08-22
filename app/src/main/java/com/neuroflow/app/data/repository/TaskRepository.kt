package com.neuroflow.app.data.repository

import com.neuroflow.app.data.local.dao.TaskDao
import com.neuroflow.app.data.local.entity.TaskEntity
import com.neuroflow.app.domain.model.Quadrant
import com.neuroflow.app.domain.model.Recurrence
import com.neuroflow.app.domain.model.TaskStatus
import com.neuroflow.app.presentation.launcher.hyperfocus.domain.HyperFocusManager
import dagger.Lazy
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private fun Long.toDayStart(): Long {
    val cal = Calendar.getInstance().apply { timeInMillis = this@toDayStart }
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

@Singleton
class TaskRepository @Inject constructor(
    private val taskDao: TaskDao,
    private val hyperFocusManager: Lazy<HyperFocusManager>
) {
    fun observeAll(): Flow<List<TaskEntity>> = taskDao.observeAll()
    fun observeActiveTasks(): Flow<List<TaskEntity>> = taskDao.observeActiveTasks()
    fun observeCompletedTasks(): Flow<List<TaskEntity>> = taskDao.observeCompletedTasks()
    fun observeByQuadrant(quadrant: Quadrant): Flow<List<TaskEntity>> = taskDao.observeByQuadrant(quadrant)
    fun observeByStatus(status: TaskStatus): Flow<List<TaskEntity>> = taskDao.observeByStatus(status)
    fun observeTasksForDate(date: Long): Flow<List<TaskEntity>> {
        val dayStart = date.toDayStart()
        val dayEnd = dayStart + 86_400_000L
        return taskDao.observeTasksForDate(dayStart, dayEnd)
    }
    fun observeQuadrantCount(quadrant: Quadrant): Flow<Int> = taskDao.observeQuadrantCount(quadrant)
    fun observeById(id: String): Flow<TaskEntity?> = taskDao.observeById(id)
    fun observeByContextTag(tag: String): Flow<List<TaskEntity>> = taskDao.observeByContextTag(tag)
    fun observeByGoalId(goalId: String): Flow<List<TaskEntity>> = taskDao.observeByGoalId(goalId)
    fun observeSubtasks(parentId: String): Flow<List<TaskEntity>> = taskDao.observeSubtasks(parentId)

    suspend fun getById(id: String): TaskEntity? = taskDao.getById(id)
    suspend fun getActiveTasks(): List<TaskEntity> = taskDao.getActiveTasks()
    suspend fun getAllTasks(): List<TaskEntity> = taskDao.getAllTasks()
    suspend fun getCompletedTasks(): List<TaskEntity> = taskDao.getCompletedTasks()

    suspend fun insert(task: TaskEntity) = taskDao.insert(task)
    suspend fun update(task: TaskEntity) = taskDao.update(task)
    suspend fun delete(task: TaskEntity) = taskDao.delete(task)
    suspend fun deleteAll() = taskDao.deleteAll()
    suspend fun resetEstimationErrors() = taskDao.resetEstimationErrors()

    /**
     * Marks [task] as completed and, if it has a recurrence, inserts the next occurrence.
     * The next occurrence's [TaskEntity.habitDate] is always shifted by the recurrence interval.
     * deadline/scheduledDate are also shifted if they were set on the original.
     * Returns the new task ID if a recurrence was created, null otherwise.
     */
    suspend fun completeAndRecur(task: TaskEntity, now: Long): String? {
        update(
            task.copy(
                status = TaskStatus.COMPLETED,
                completedAt = now,
                isHabitual = task.recurrence != Recurrence.NONE,
                habitStreak = if (task.recurrence != Recurrence.NONE) task.habitStreak + 1 else task.habitStreak,
                updatedAt = now
            )
        )
        hyperFocusManager.get().onTaskCompleted()

        if (task.recurrence == Recurrence.NONE) return null

        val intervalMs = when (task.recurrence) {
            Recurrence.DAILY   -> 86_400_000L
            Recurrence.WEEKLY  -> 7 * 86_400_000L
            Recurrence.MONTHLY -> 30 * 86_400_000L
            Recurrence.CUSTOM  -> task.recurrenceIntervalDays * 86_400_000L
            Recurrence.NONE    -> 0L
        }

        // Anchor for the next occurrence: prefer habitDate, fall back to scheduledDate,
        // fall back to deadlineDate, fall back to now (so there's always a next-due date).
        val currentAnchor = task.habitDate ?: task.scheduledDate ?: task.deadlineDate ?: now
        val nextAnchor = currentAnchor + intervalMs

        val newId = UUID.randomUUID().toString()
        insert(
            task.copy(
                id = newId,
                status = TaskStatus.ACTIVE,
                completedAt = null,
                habitDate = nextAnchor,
                deadlineDate = task.deadlineDate?.plus(intervalMs),
                scheduledDate = task.scheduledDate?.plus(intervalMs),
                isScheduleLocked = task.isScheduleLocked,
                totalTimeTrackedMinutes = 0f,
                sessionCount = 0,
                lastSessionDurationMinutes = null,
                actualDurationMinutes = null,
                estimationErrorMape = null,
                estimationErrorSmape = null,
                focusModePoints = 0,
                postponeCount = 0,
                habitStreak = task.habitStreak + 1,
                isHabitual = true,
                createdAt = now,
                updatedAt = now
            )
        )
        return newId
    }
}
