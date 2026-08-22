package com.neuroflow.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.neuroflow.app.domain.model.EnergyLevel
import com.neuroflow.app.domain.model.Priority
import com.neuroflow.app.domain.model.Quadrant
import com.neuroflow.app.domain.model.Recurrence
import com.neuroflow.app.domain.model.TaskStatus
import com.neuroflow.app.domain.model.TaskType
import java.util.UUID

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",

    // Classification
    val quadrant: Quadrant = Quadrant.SCHEDULE,
    val priority: Priority = Priority.MEDIUM,
    val tags: String = "",

    // Timing
    val deadlineDate: Long? = null,
    val deadlineTime: Long? = null,
    val scheduledDate: Long? = null,
    val scheduledTime: Long? = null,
    val isScheduleLocked: Boolean = false,
    val estimatedDurationMinutes: Int = 0,
    val recurrence: Recurrence = Recurrence.NONE,
    val recurrenceIntervalDays: Int = 1, // used when recurrence == CUSTOM

    // Reminders (bitmask: 15min=1, 30min=2, 1hr=4, 1day=8)
    val reminderFlags: Int = 0,

    // Scoring inputs
    val impactScore: Int = 50,          // 0-100: strategic value/impact
    val valueScore: Int = 50,           // 0-100: intrinsic value (separate from impact)
    val effortScore: Int = 50,          // 0-100: effort required (higher = harder, lowers priority for quick wins)
    val parentTaskId: String? = null,
    val blockingTaskIds: String = "",   // legacy: kept for migration compat
    val dependsOnTaskIds: String = "",  // IDs of tasks THIS task depends on (must be done first)
    val waitingFor: String = "",        // external dependency description (non-task)

    // Time tracking
    val totalTimeTrackedMinutes: Float = 0f,
    val sessionCount: Int = 0,
    val lastSessionDurationMinutes: Float? = null,

    // Adaptive difficulty
    val actualDurationMinutes: Float? = null,
    val estimationErrorMape: Float? = null,
    val estimationErrorSmape: Float? = null,

    // State
    val status: TaskStatus = TaskStatus.ACTIVE,
    val completedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),

    // Neuroscience extras
    val energyLevel: EnergyLevel = EnergyLevel.MEDIUM,
    val taskType: TaskType = TaskType.ANALYTICAL,   // circadian rhythm matching
    val contextTag: String = "",
    val goalId: String? = null,
    val ifThenPlan: String = "",
    val isHabitual: Boolean = false,
    val habitStreak: Int = 0,
    val isFrog: Boolean = false,
    val postponeCount: Int = 0,
    val focusModePoints: Int = 0,

    // Science-backed productivity boosters
    val enjoymentScore: Int = 50,       // 0-100: how enjoyable the task is (temptation bundling)
    val isPublicCommitment: Boolean = false, // commitment device — social accountability
    val isAnxietyTask: Boolean = false,  // stress inoculation — prevents avoidance spiral
    val goalRiskLevel: Int = 0,          // 0=none, 1=at risk, 2=critical — loss aversion boost

    // Habit anchor date — for recurring tasks this is always set and shifts by the recurrence interval
    // on each completion. Independent of deadline/scheduledDate so habits work without a deadline.
    val habitDate: Long? = null,

    // Behavioral motivation engine fields
    val affectiveForecastError: Float? = null,  // REAL nullable — difference between predicted and actual enjoyment
    val woopPromptShown: Boolean = false,        // INTEGER NOT NULL DEFAULT 0 — whether WOOP prompt has been shown

    // Digital Wellbeing distraction score (0–100), refreshed by DistractionSyncWorker
    // -1 = not yet computed (no usage data / permission not granted)
    val distractionScore: Float = -1f
)
