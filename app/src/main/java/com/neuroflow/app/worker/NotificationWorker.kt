package com.neuroflow.app.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.neuroflow.app.R
import com.neuroflow.app.data.repository.TaskRepository
import com.neuroflow.app.domain.engine.TaskScoringEngine
import com.neuroflow.app.data.local.UserPreferencesDataStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

const val CHANNEL_TASKS = "tasks"
const val CHANNEL_FOCUS = "focus"
const val CHANNEL_DAILY = "daily_plan"

fun createNotificationChannels(context: Context) {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    val taskChannel = NotificationChannel(
        CHANNEL_TASKS, "Task Reminders", NotificationManager.IMPORTANCE_HIGH
    ).apply { description = "Reminders for upcoming task deadlines" }

    val focusChannel = NotificationChannel(
        CHANNEL_FOCUS, "Focus & Streaks", NotificationManager.IMPORTANCE_DEFAULT
    ).apply { description = "Focus time and streak notifications" }

    val dailyChannel = NotificationChannel(
        CHANNEL_DAILY, "Daily Plan", NotificationManager.IMPORTANCE_DEFAULT
    ).apply { description = "Morning planning notifications" }

    val autonomyNudgeChannel = NotificationChannel(
        "autonomy_nudge", "Autonomy Nudges", NotificationManager.IMPORTANCE_DEFAULT
    ).apply { description = "Gentle nudges when a task hasn't been started" }

    manager.createNotificationChannels(listOf(taskChannel, focusChannel, dailyChannel, autonomyNudgeChannel))
}

@HiltWorker
class DailyPlanWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val taskRepository: TaskRepository,
    private val preferencesDataStore: UserPreferencesDataStore
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = preferencesDataStore.preferencesFlow.first()
        val tasks = taskRepository.getActiveTasks()
        val topTasks = TaskScoringEngine.sortedByScore(tasks, prefs).take(3)

        if (topTasks.isNotEmpty()) {
            val taskNames = topTasks.joinToString(", ") { it.title }
            showNotification(
                "Your top tasks today",
                "Your top 3 tasks: $taskNames. Tap to confirm.",
                CHANNEL_DAILY
            )
        }

        return Result.success()
    }

    private fun showNotification(title: String, message: String, channelId: String) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}

@HiltWorker
class StreakCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val taskRepository: TaskRepository,
    private val preferencesDataStore: UserPreferencesDataStore
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = preferencesDataStore.preferencesFlow.first()
        val today = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val yesterday = today - 86_400_000L

        val completedToday = taskRepository.getCompletedTasks().any { task ->
            task.completedAt != null && task.completedAt >= today
        }

        // If nothing was completed today AND last active was before yesterday → streak is broken
        if (!completedToday && prefs.lastActiveDate < yesterday && prefs.dailyStreak > 0) {
            preferencesDataStore.updatePreferences { it.copy(dailyStreak = 0) }
        }

        // Notify if streak is at risk (active streak, nothing done today yet, evening check)
        if (!completedToday && prefs.dailyStreak > 3) {
            showNotification(
                "Streak at risk! 🔥",
                "Your ${prefs.dailyStreak}-day streak ends tonight. Complete 1 task!",
                CHANNEL_FOCUS
            )
        }

        return Result.success()
    }

    private fun showNotification(title: String, message: String, channelId: String) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}

@HiltWorker
class DeadlineEscalationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val taskRepository: TaskRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val now = System.currentTimeMillis()
        val threshold = 48 * 3_600_000L  // 48 hours in ms

        val escalated = taskRepository.getActiveTasks().filter { task ->
            task.quadrant == com.neuroflow.app.domain.model.Quadrant.SCHEDULE &&
            !task.isScheduleLocked &&
            task.deadlineDate != null &&
            (task.deadlineDate + (task.deadlineTime ?: 0L) - now) in 0L..threshold
        }

        escalated.forEach { task ->
            taskRepository.update(
                task.copy(
                    quadrant = com.neuroflow.app.domain.model.Quadrant.DO_FIRST,
                    updatedAt = now
                )
            )
        }

        if (escalated.isNotEmpty()) {
            val names = escalated.take(3).joinToString(", ") { it.title }
            val more = if (escalated.size > 3) " +${escalated.size - 3} more" else ""
            showNotification(
                "⚠️ ${escalated.size} task${if (escalated.size > 1) "s" else ""} moved to DO FIRST",
                "$names$more — deadline within 48 hours.",
                CHANNEL_TASKS
            )
        }

        return Result.success()
    }

    private fun showNotification(title: String, message: String, channelId: String) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}

@HiltWorker
class TaskReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val taskRepository: TaskRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getString("taskId") ?: return Result.failure()
        val taskTitle = inputData.getString("taskTitle") ?: run {
            taskRepository.getById(taskId)?.title ?: return Result.failure()
        }
        val minutesBefore = inputData.getLong("minutesBefore", 0L)

        val timeLabel = when (minutesBefore) {
            15L   -> "15 minutes"
            30L   -> "30 minutes"
            60L   -> "1 hour"
            1440L -> "1 day"
            else  -> "$minutesBefore minutes"
        }

        showNotification(
            "⏰ Task due in $timeLabel",
            taskTitle,
            CHANNEL_TASKS
        )

        return Result.success()
    }

    private fun showNotification(title: String, message: String, channelId: String) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
