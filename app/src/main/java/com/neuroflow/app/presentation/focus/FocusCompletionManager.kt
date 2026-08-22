package com.neuroflow.app.presentation.focus

import com.neuroflow.app.data.local.UserPreferencesDataStore
import com.neuroflow.app.data.local.dao.TaskFeedbackDao
import com.neuroflow.app.data.local.entity.TaskFeedbackEntity
import com.neuroflow.app.data.local.entity.TimeSessionEntity
import com.neuroflow.app.data.repository.SessionRepository
import com.neuroflow.app.data.repository.TaskRepository
import com.neuroflow.app.domain.engine.AnalyticsEngine
import com.neuroflow.app.domain.model.Recurrence
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FocusCompletionManager @Inject constructor(
    private val taskRepository: TaskRepository,
    private val sessionRepository: SessionRepository,
    private val preferencesDataStore: UserPreferencesDataStore,
    private val taskFeedbackDao: TaskFeedbackDao
) {

    data class CompletionOutcome(
        val pointsEarned: Int,
        val newHabitStreak: Int
    )

    suspend fun completeTask(taskId: String, manualMinutes: Float?): CompletionOutcome? {
        if (manualMinutes != null && manualMinutes > 0f) {
            val now = System.currentTimeMillis()
            val syntheticSession = TimeSessionEntity(
                taskId = taskId,
                startedAt = now - (manualMinutes * 60_000f).toLong(),
                endedAt = now,
                durationMinutes = manualMinutes,
                sessionType = "MANUAL_LOG"
            )
            sessionRepository.insert(syntheticSession)
        }

        val task = taskRepository.getById(taskId) ?: return null
        val sessions = sessionRepository.getByTaskId(taskId)
        val actualDuration = sessions
            .filter { it.endedAt != null && it.durationMinutes > 0f }
            .sumOf { it.durationMinutes.toDouble() }.toFloat()

        val mape = if (task.estimatedDurationMinutes > 0 && actualDuration > 0f)
            AnalyticsEngine.computeMape(task.estimatedDurationMinutes.toFloat(), actualDuration) else null
        val smape = if (task.estimatedDurationMinutes > 0 && actualDuration > 0f)
            AnalyticsEngine.computeSmape(task.estimatedDurationMinutes.toFloat(), actualDuration) else null

        val points = task.impactScore / 10
        val now = System.currentTimeMillis()

        val taskWithFocusData = task.copy(
            actualDurationMinutes = actualDuration,
            estimationErrorMape = mape,
            estimationErrorSmape = smape,
            focusModePoints = points,
            updatedAt = now
        )
        taskRepository.completeAndRecur(taskWithFocusData, now)
        taskFeedbackDao.insert(
            TaskFeedbackEntity(
                taskId = taskId,
                kind = "FOCUS_TIME",
                value = "actual=${actualDuration.toInt()};estimated=${task.estimatedDurationMinutes}"
            )
        )

        val newHabitStreak = if (task.recurrence != Recurrence.NONE)
            task.habitStreak + 1 else task.habitStreak

        preferencesDataStore.updatePreferences { prefs ->
            val now2 = System.currentTimeMillis()
            val todayStart = run {
                val cal = Calendar.getInstance()
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            val yesterdayStart = todayStart - 86_400_000L
            val newStreak = when {
                prefs.lastActiveDate >= todayStart -> prefs.dailyStreak
                prefs.lastActiveDate >= yesterdayStart -> prefs.dailyStreak + 1
                else -> 1
            }
            prefs.copy(
                totalTasksCompleted = prefs.totalTasksCompleted + 1,
                totalFocusMinutes = prefs.totalFocusMinutes + actualDuration.toInt(),
                dailyStreak = newStreak,
                lastActiveDate = now2,
                longestStreak = maxOf(prefs.longestStreak, newStreak)
            )
        }

        return CompletionOutcome(pointsEarned = points, newHabitStreak = newHabitStreak)
    }
}
