package com.neuroflow.app.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.neuroflow.app.R
import com.neuroflow.app.data.local.UserPreferences
import com.neuroflow.app.data.local.entity.effectiveReminderTargetMillis
import com.neuroflow.app.data.repository.TaskRepository
import com.neuroflow.app.domain.engine.TaskScoringEngine
import com.neuroflow.app.data.local.UserPreferencesDataStore
import com.neuroflow.app.domain.model.TaskStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit

const val CHANNEL_TASKS = "tasks"
const val CHANNEL_FOCUS = "focus"
const val CHANNEL_DAILY = "daily_plan"
const val WORK_DAILY_PLAN = "daily_plan"
const val WORK_STREAK_CHECK = "streak_check"
const val WORK_DEADLINE_ESCALATION = "deadline_escalation"

internal data class NotificationWorkerPlan(
    val dailyPlanEnabled: Boolean,
    val dailyPlanDelayMs: Long,
    val streakEnabled: Boolean,
    val streakDelayMs: Long,
    val escalationEnabled: Boolean
)

internal fun delayUntilHour(hour: Int, nowMillis: Long = System.currentTimeMillis()): Long {
    val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val target = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    if (target.before(now)) target.add(Calendar.DAY_OF_YEAR, 1)
    return target.timeInMillis - now.timeInMillis
}

internal fun buildNotificationWorkerPlan(
    prefs: UserPreferences,
    nowMillis: Long = System.currentTimeMillis()
): NotificationWorkerPlan {
    return NotificationWorkerPlan(
        dailyPlanEnabled = prefs.dailyPlanNotificationsEnabled,
        dailyPlanDelayMs = delayUntilHour(prefs.dailyPlanNotificationHour, nowMillis),
        streakEnabled = prefs.streakNotificationsEnabled,
        streakDelayMs = delayUntilHour(prefs.streakCheckNotificationHour, nowMillis),
        escalationEnabled = prefs.deadlineEscalationNotificationsEnabled
    )
}

fun scheduleNotificationWorkers(context: Context, prefs: UserPreferences) {
    val workManager = WorkManager.getInstance(context)
    val plan = buildNotificationWorkerPlan(prefs)

    if (plan.dailyPlanEnabled) {
        val dailyPlanRequest = PeriodicWorkRequestBuilder<DailyPlanWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(plan.dailyPlanDelayMs, TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniquePeriodicWork(
            WORK_DAILY_PLAN,
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            dailyPlanRequest
        )
    } else {
        workManager.cancelUniqueWork(WORK_DAILY_PLAN)
    }

    if (plan.streakEnabled) {
        val streakCheckRequest = PeriodicWorkRequestBuilder<StreakCheckWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(plan.streakDelayMs, TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniquePeriodicWork(
            WORK_STREAK_CHECK,
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            streakCheckRequest
        )
    } else {
        workManager.cancelUniqueWork(WORK_STREAK_CHECK)
    }

    if (plan.escalationEnabled) {
        val escalationRequest = PeriodicWorkRequestBuilder<DeadlineEscalationWorker>(4, TimeUnit.HOURS).build()
        workManager.enqueueUniquePeriodicWork(
            WORK_DEADLINE_ESCALATION,
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            escalationRequest
        )
    } else {
        workManager.cancelUniqueWork(WORK_DEADLINE_ESCALATION)
    }
}

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
        if (!prefs.dailyPlanNotificationsEnabled) return Result.success()
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
        // Use a stable ID per worker type so daily plan notifications are updated in place
        // rather than accumulating as separate notifications.
        manager.notify(NOTIFICATION_ID_DAILY_PLAN, notification)
    }

    companion object {
        private const val NOTIFICATION_ID_DAILY_PLAN = 10_000
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
        var prefs = preferencesDataStore.preferencesFlow.first()
        if (!prefs.streakNotificationsEnabled) return Result.success()
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
            // Re-read prefs so the risk-notification check below uses the updated streak value,
            // not the stale pre-reset value that still shows the old (broken) streak count.
            prefs = preferencesDataStore.preferencesFlow.first()
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
        // Use a stable ID per worker type so the same notification is updated (not duplicated)
        manager.notify(NOTIFICATION_ID_STREAK, notification)
    }

    companion object {
        // Stable notification ID: streak check always updates the same notification slot
        private const val NOTIFICATION_ID_STREAK = 10_001
    }
}

@HiltWorker
class DeadlineEscalationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val taskRepository: TaskRepository,
    private val preferencesDataStore: UserPreferencesDataStore
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = preferencesDataStore.preferencesFlow.first()
        if (!prefs.deadlineEscalationNotificationsEnabled) return Result.success()

        val now = System.currentTimeMillis()
        val threshold = 48 * 3_600_000L  // 48 hours in ms

        val escalated = taskRepository.getActiveTasks().filter { task ->
            task.quadrant == com.neuroflow.app.domain.model.Quadrant.SCHEDULE &&
            task.recurrence == com.neuroflow.app.domain.model.Recurrence.NONE &&
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
        // Use a stable ID per worker type so repeat escalation runs update the same notification
        // instead of spamming a new one every 4 hours.
        manager.notify(NOTIFICATION_ID_ESCALATION, notification)
    }

    companion object {
        private const val NOTIFICATION_ID_ESCALATION = 10_002
    }
}

@HiltWorker
class TaskReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val taskRepository: TaskRepository,
    private val preferencesDataStore: UserPreferencesDataStore
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = preferencesDataStore.preferencesFlow.first()
        if (!prefs.deadlineReminderNotificationsEnabled) return Result.success()

        val taskId = inputData.getString("taskId") ?: return Result.failure()
        val task = taskRepository.getById(taskId) ?: return Result.success()
        if (task.status != TaskStatus.ACTIVE) return Result.success()
        val taskTitle = inputData.getString("taskTitle") ?: task.title
        val minutesBefore = inputData.getLong("minutesBefore", 0L)
        val expectedTargetMs = inputData.getLong("targetMs", -1L)

        val actualTargetMs = task.effectiveReminderTargetMillis() ?: return Result.success()

        // Skip stale reminder work if target moved significantly after scheduling.
        if (expectedTargetMs > 0 && kotlin.math.abs(actualTargetMs - expectedTargetMs) > 5 * 60_000L) {
            return Result.success()
        }

        val now = System.currentTimeMillis()
        // Do not emit reminder notifications once the task is already overdue for too long.
        if (actualTargetMs < now - 10 * 60_000L) return Result.success()

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
        // Use a stable ID derived from the task ID so each task gets exactly one reminder
        // notification slot that is updated (not duplicated) on re-delivery.
        val taskId = inputData.getString("taskId") ?: ""
        manager.notify(taskId.hashCode(), notification)
    }
}
