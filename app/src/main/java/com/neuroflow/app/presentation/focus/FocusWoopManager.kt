package com.neuroflow.app.presentation.focus

import com.neuroflow.app.data.local.UserPreferencesDataStore
import com.neuroflow.app.data.local.dao.TaskFeedbackDao
import com.neuroflow.app.data.local.entity.TaskFeedbackEntity
import com.neuroflow.app.data.local.entity.WoopEntity
import com.neuroflow.app.data.repository.TaskRepository
import com.neuroflow.app.data.repository.WoopRepository
import com.neuroflow.app.domain.engine.WoopEngine
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FocusWoopManager @Inject constructor(
    private val woopRepository: WoopRepository,
    private val taskRepository: TaskRepository,
    private val preferencesDataStore: UserPreferencesDataStore,
    private val taskFeedbackDao: TaskFeedbackDao
) {

    data class WoopLoadResult(
        val showWoopPrompt: Boolean,
        val woopData: WoopEntity?,
        val dreadedTaskInsight: String?,
        val affectiveForecastError: Float?
    )

    suspend fun load(taskId: String): WoopLoadResult {
        val woopData = woopRepository.getByTaskId(taskId)
        val task = taskRepository.getById(taskId)
        val prefs = preferencesDataStore.preferencesFlow.first()
        val showWoopPrompt = prefs.woopEnabled && WoopEngine.shouldShowPrompt(woopData, task?.woopPromptShown ?: false)
        val completedTasks = taskRepository.getCompletedTasks()
        val dreadedTaskInsight = WoopEngine.dreadedTaskInsight(completedTasks)

        return WoopLoadResult(
            showWoopPrompt = showWoopPrompt,
            woopData = woopData,
            dreadedTaskInsight = dreadedTaskInsight,
            affectiveForecastError = task?.affectiveForecastError
        )
    }

    suspend fun submit(taskId: String, wish: String, outcome: String, obstacle: String, plan: String): WoopEntity? {
        val woop = WoopEntity(taskId = taskId, wish = wish, outcome = outcome, obstacle = obstacle, plan = plan)
        woopRepository.upsert(woop)
        val task = taskRepository.getById(taskId) ?: return null
        taskRepository.update(task.copy(woopPromptShown = true, updatedAt = System.currentTimeMillis()))
        return woop
    }

    suspend fun dismiss(taskId: String): Boolean {
        val task = taskRepository.getById(taskId) ?: return false
        taskRepository.update(task.copy(woopPromptShown = true, updatedAt = System.currentTimeMillis()))
        return true
    }

    suspend fun submitAffordanceRating(taskId: String, feedback: FocusFeedback): Boolean {
        val task = taskRepository.getById(taskId) ?: return false
        taskRepository.update(
            task.copy(
                // Preserve the original affective-forecast field as the schedule-quality signal.
                affectiveForecastError = feedback.scheduleRating,
                updatedAt = System.currentTimeMillis()
            )
        )
        taskFeedbackDao.insertAll(
            listOf(
                TaskFeedbackEntity(taskId, "FOCUS_SCHEDULE", feedback.scheduleRating.toString()),
                TaskFeedbackEntity(taskId, "FOCUS_TIME", feedback.durationRating.toString()),
                TaskFeedbackEntity(taskId, "FOCUS_ENERGY", feedback.energyRating.toString())
            )
        )
        return true
    }

    suspend fun recordFeedback(taskId: String, kind: String, value: String): Boolean {
        if (taskRepository.getById(taskId) == null) return false
        taskFeedbackDao.insert(TaskFeedbackEntity(taskId = taskId, kind = kind, value = value))
        return true
    }
}
