package com.neuroflow.app.worker

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.neuroflow.app.R
import com.neuroflow.app.data.local.UserPreferencesDataStore
import com.neuroflow.app.data.repository.TaskRepository
import com.neuroflow.app.domain.engine.TaskScoringEngine
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class FocusWidgetUpdateWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val taskRepository: TaskRepository,
    private val preferencesDataStore: UserPreferencesDataStore
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        try {
            val tasks = taskRepository.getActiveTasks()
            val preferences = preferencesDataStore.preferencesFlow.first()
            val sorted = TaskScoringEngine.sortedByScore(tasks, preferences)
            val topTask = sorted.firstOrNull()

            val views = RemoteViews(appContext.packageName, R.layout.widget_focus)

            if (topTask != null) {
                views.setTextViewText(R.id.widget_task_title, topTask.title)
            } else {
                views.setTextViewText(R.id.widget_task_title, "No tasks — add one in NeuroFlow")
            }

            val componentName = ComponentName(
                appContext,
                "com.neuroflow.app.presentation.widget.FocusWidgetProvider"
            )
            AppWidgetManager.getInstance(appContext).updateAppWidget(componentName, views)
        } catch (_: Exception) {
            // Stale widget preferred over crash
        }

        return Result.success()
    }
}
