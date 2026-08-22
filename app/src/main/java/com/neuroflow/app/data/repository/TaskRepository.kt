package com.neuroflow.app.data.repository

import com.neuroflow.app.data.local.dao.TaskDao
import com.neuroflow.app.data.local.entity.TaskEntity
import com.neuroflow.app.data.local.entity.splitToLocalDateAndTime
import com.neuroflow.app.domain.model.Quadrant
import com.neuroflow.app.domain.model.Recurrence
import com.neuroflow.app.domain.model.TaskStatus
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.neuroflow.app.presentation.launcher.hyperfocus.domain.HyperFocusManager
import com.neuroflow.app.presentation.launcher.work.ScheduleAutoTasksWorker
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

private fun Calendar.addRecurrenceStep(recurrence: Recurrence, customDays: Int) {
    when (recurrence) {
        Recurrence.DAILY -> add(Calendar.DAY_OF_YEAR, 1)
        Recurrence.WEEKLY -> add(Calendar.WEEK_OF_YEAR, 1)
        Recurrence.MONTHLY -> add(Calendar.MONTH, 1)
        Recurrence.CUSTOM -> add(Calendar.DAY_OF_YEAR, customDays.coerceAtLeast(1))
        Recurrence.NONE -> Unit
    }
}

private fun nextRecurringAnchorAfter(
    recurrence: Recurrence,
    customDays: Int,
    currentAnchor: Long,
    now: Long
): Long {
    val cal = Calendar.getInstance().apply { timeInMillis = currentAnchor }
    // Always move to the next cycle, then roll forward if the anchor is still in the past.
    cal.addRecurrenceStep(recurrence, customDays)
    while (cal.timeInMillis <= now) {
        cal.addRecurrenceStep(recurrence, customDays)
    }
    return cal.timeInMillis
}

private fun shiftDateAndOptionalTime(
    date: Long?,
    time: Long?,
    deltaMs: Long
): Pair<Long?, Long?> {
    if (date == null) return null to null
    val source = date + (time ?: 0L)
    val shifted = source + deltaMs
    val (shiftedDate, shiftedTime) = splitToLocalDateAndTime(shifted)
    return shiftedDate to if (time == null) null else shiftedTime
}

@Singleton
class TaskRepository @Inject constructor(
    private val taskDao: TaskDao,
    private val hyperFocusManager: Lazy<HyperFocusManager>,
    private val workManager: WorkManager
) {
    fun observeAll(): Flow<List<TaskEntity>> = taskDao.observeAll()
    fun observeActiveTasks(): Flow<List<TaskEntity>> = taskDao.observeActiveTasks()
    fun observeCompletedTasks(): Flow<List<TaskEntity>> = taskDao.observeCompletedTasks()
    fun observeByQuadrant(quadrant: Quadrant): Flow<List<TaskEntity>> = taskDao.observeByQuadrant(quadrant)
    fun observeByStatus(status: TaskStatus): Flow<List<TaskEntity>> = taskDao.observeByStatus(status)
    fun observeTasksForDate(date: Long): Flow<List<TaskEntity>> {
        val dayStart = date.toDayStart()
        val dayEnd = Calendar.getInstance().apply {
            timeInMillis = dayStart
            add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
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

    suspend fun insert(task: TaskEntity) {
        taskDao.insert(task)
    }
    suspend fun update(task: TaskEntity) {
        val old = taskDao.getById(task.id)
        taskDao.update(task)

        // Trigger rescheduling if relevant fields changed
        if (old != null && shouldTriggerReschedule(old, task)) {
            enqueueAutoSchedulingRecheck()
        }
    }

    private fun shouldTriggerReschedule(old: TaskEntity, new: TaskEntity): Boolean {
        return old.deadlineDate != new.deadlineDate ||
               old.deadlineTime != new.deadlineTime ||
               old.priority != new.priority ||
               old.estimatedDurationMinutes != new.estimatedDurationMinutes ||
               old.effortScore != new.effortScore ||
               old.tags != new.tags ||
               old.taskType != new.taskType ||
               old.energyLevel != new.energyLevel ||
               old.earliestStartDate != new.earliestStartDate ||
               old.earliestStartTime != new.earliestStartTime ||
               old.preferredWeekdaysMask != new.preferredWeekdaysMask ||
               old.avoidStartTime != new.avoidStartTime ||
               old.avoidEndTime != new.avoidEndTime ||
               old.isHardDeadline != new.isHardDeadline ||
               old.canSplit != new.canSplit ||
               old.maxSessionLengthMinutes != new.maxSessionLengthMinutes ||
               old.minimumFocusBlockMinutes != new.minimumFocusBlockMinutes ||
               // Task was unscheduled (manual or incomplete)
               (old.scheduledDate != null && new.scheduledDate == null)
    }

    suspend fun markIncomplete(task: TaskEntity, timeSpentMinutes: Int) {
        update(task.copy(
            scheduledDate = null,
            scheduledTime = null,
            totalTimeTrackedMinutes = task.totalTimeTrackedMinutes + timeSpentMinutes,
            updatedAt = System.currentTimeMillis()
        ))
        // Rescheduling trigger already called in update()
    }

    suspend fun postponeTask(task: TaskEntity) {
        update(task.copy(
            postponeCount = task.postponeCount + 1,
            scheduledDate = null,
            scheduledTime = null,
            updatedAt = System.currentTimeMillis()
        ))
        // Rescheduling trigger already called in update()
    }
    suspend fun delete(task: TaskEntity) {
        if (hyperFocusManager.get().isTaskDeletionBlocked(task.id)) return
        taskDao.delete(task)
    }
    suspend fun deleteAll() = taskDao.deleteAll()
    suspend fun resetEstimationErrors() = taskDao.resetEstimationErrors()

    private fun enqueueAutoSchedulingRecheck() {
        val request = OneTimeWorkRequestBuilder<ScheduleAutoTasksWorker>().build()
        workManager.enqueueUniqueWork(
            "${ScheduleAutoTasksWorker.WORK_NAME}_trigger",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    /**
     * Marks [task] as completed and, if it has recurrence, inserts the next occurrence.
     * Uses calendar-aware stepping (daily/weekly/monthly/custom days) and guarantees
     * the new occurrence anchor lands strictly in the future.
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
        enqueueAutoSchedulingRecheck()

        if (task.recurrence == Recurrence.NONE) return null

        // Anchor for recurrence progression: prefer habitDate, then scheduled/deadline with time, then now.
        val currentAnchor =
            task.habitDate
                ?: task.scheduledDate?.let { it + (task.scheduledTime ?: 0L) }
                ?: task.deadlineDate?.let { it + (task.deadlineTime ?: 0L) }
                ?: now
        val nextAnchor = nextRecurringAnchorAfter(
            recurrence = task.recurrence,
            customDays = task.recurrenceIntervalDays,
            currentAnchor = currentAnchor,
            now = now
        )
        val deltaMs = nextAnchor - currentAnchor
        val (nextDeadlineDate, nextDeadlineTime) = shiftDateAndOptionalTime(
            date = task.deadlineDate,
            time = task.deadlineTime,
            deltaMs = deltaMs
        )
        val (nextScheduledDate, nextScheduledTime) = shiftDateAndOptionalTime(
            date = task.scheduledDate,
            time = task.scheduledTime,
            deltaMs = deltaMs
        )

        val newId = UUID.randomUUID().toString()
        insert(
            task.copy(
                id = newId,
                status = TaskStatus.ACTIVE,
                completedAt = null,
                habitDate = nextAnchor,
                deadlineDate = nextDeadlineDate,
                deadlineTime = nextDeadlineTime,
                scheduledDate = nextScheduledDate,
                scheduledTime = nextScheduledTime,
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
