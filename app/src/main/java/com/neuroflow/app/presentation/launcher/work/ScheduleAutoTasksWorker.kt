package com.neuroflow.app.presentation.launcher.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkerParameters
import com.neuroflow.app.data.local.UserPreferencesDataStore
import com.neuroflow.app.data.local.dao.AutoScheduleTelemetryDao
import com.neuroflow.app.data.local.entity.AutoScheduleTelemetryEntity
import com.neuroflow.app.data.repository.TaskRepository
import com.neuroflow.app.domain.engine.EnergyScoreEngine
import com.neuroflow.app.domain.model.TaskStatus
import com.neuroflow.app.domain.repository.EnergyScoreRepository
import com.neuroflow.app.domain.repository.PeakEnergyRepository
import com.neuroflow.app.domain.scheduler.AutoSchedulingEngine
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt
import java.util.concurrent.TimeUnit

/**
 * Phase 4: ScheduleAutoTasksWorker
 *
 * Background worker that executes Phase 3 AutoSchedulingEngine decisions transactionally.
 *
 * Runs periodically (every 15 minutes by default per autoSchedulingBackgroundThrottleMinutes)
 * to:
 * 1. Query unscheduled, eligible tasks
 * 2. Generate scheduling decisions via AutoSchedulingEngine
 * 3. Apply decisions transactionally to task repository
 * 4. Persist telemetry for monitoring, explanations, and future learning
 *
 * Gated by:
 * - autoSchedulingEnabled preference
 * - Background throttle minimum (prevents excessive runs)
 * - Task eligibility checks (Phase 1 contracts)
 *
 * Produces:
 * - Updated tasks with scheduledDate/scheduledTime set
 * - Durable telemetry rows for scheduler monitoring and learning
 */
private const val SCHEDULING_BLOCK_MINUTES = 30

@HiltWorker
class ScheduleAutoTasksWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted private val params: WorkerParameters,
    private val taskRepository: TaskRepository,
    private val autoSchedulingEngine: AutoSchedulingEngine,
    private val preferencesDataStore: UserPreferencesDataStore,
    private val energyScoreRepository: EnergyScoreRepository,
    private val peakEnergyRepository: PeakEnergyRepository,
    private val autoScheduleTelemetryDao: AutoScheduleTelemetryDao
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "schedule_auto_tasks_work"
        const val FOREGROUND_TICK_WORK_NAME = "schedule_auto_tasks_foreground_tick"

        // Concurrent worker protection: track active worker runs
        @Volatile
        private var isWorkerRunning = false
        private val workerLock = Any()

        fun buildPeriodicWorkRequest(throttleMinutes: Int): PeriodicWorkRequest {
            val throttleInterval = throttleMinutes.coerceIn(15, 240)
            return PeriodicWorkRequest.Builder(
                ScheduleAutoTasksWorker::class.java,
                throttleInterval.toLong(),
                TimeUnit.MINUTES
            ).build()
        }

        fun buildOneTimeWorkRequest(): OneTimeWorkRequest {
            return OneTimeWorkRequest.Builder(ScheduleAutoTasksWorker::class.java).build()
        }
    }

    override suspend fun doWork(): Result {
        // FIX #2: Concurrent worker protection - prevent overlapping runs
        synchronized(workerLock) {
            if (isWorkerRunning) {
                android.util.Log.w(
                    "ScheduleAutoTasks",
                    "Worker already running - skipping this execution to prevent concurrent conflicts"
                )
                return Result.success()
            }
            isWorkerRunning = true
        }

        return try {
            // Check if auto-scheduling is enabled
            val prefs = preferencesDataStore.preferencesFlow.first()
            if (!prefs.autoSchedulingEnabled) {
                return Result.success()
            }

            // Query all active tasks for unscheduled and missed scheduled assignments
            val allTasks = taskRepository.getActiveTasks()
            val nowMillis = System.currentTimeMillis()
            val blockedTaskIds = buildBlockedTaskIds(allTasks)

            // Separate tasks into categories
            val unscheduledTasks = allTasks.filter { task ->
                task.scheduledDate == null && task.scheduledTime == null
            }.filterNot { it.id in blockedTaskIds }

            val busySlotStartMillis = buildBusySlotIndex(allTasks, nowMillis)

            // 30-minute post-scheduled-end grace period threshold (30 * 60 * 1000L)
            val THIRTY_MINUTES_MILLIS = 30 * 60_000L

            // Uncompleted / Missed tasks: Active tasks where scheduled end time + 30 minutes has passed.
            // Applies to ALL scheduled tasks (whether manual or auto-scheduled), provided the task is unlocked.
            val missedTasks = allTasks.filter { task ->
                task.status == TaskStatus.ACTIVE &&
                    !task.isScheduleLocked &&
                    task.scheduledDate != null &&
                    task.scheduledTime != null &&
                    let {
                        val scheduledStart = task.scheduledDate + task.scheduledTime
                        val estimatedDuration = if (task.estimatedDurationMinutes > 0) task.estimatedDurationMinutes else 30
                        val scheduledEnd = scheduledStart + (estimatedDuration * 60_000L)
                        nowMillis > (scheduledEnd + THIRTY_MINUTES_MILLIS)
                    }
            }.filterNot { it.id in blockedTaskIds }

            // Auto-scheduled tasks that may need replanning (future active tasks)
            val autoScheduledTasks = allTasks.filter { task ->
                task.status == TaskStatus.ACTIVE &&
                    task.scheduledDate != null &&
                    task.scheduledTime != null &&
                    task.isAutoScheduled &&  // Only replan auto-scheduled tasks
                    (task.scheduledDate + task.scheduledTime) >= nowMillis
            }.filterNot { it.id in blockedTaskIds }

            val peakDetection = peakEnergyRepository.getPeakEnergyDetection()

            // FIX #1: Capture energy snapshot at worker start for consistency
            // This prevents race conditions where energy changes during multi-task scheduling
            val energySnapshotMillis = System.currentTimeMillis()
            val liveEnergyModel = energyScoreRepository.observeEnergy(refreshIntervalMillis = 0).first()

            val energyScoreFn: suspend (Long) -> Pair<Int, Float> = { slotMillis ->
                val baseline = EnergyScoreEngine.calculateDetailed(
                    EnergyScoreEngine.EnergySnapshot(
                        peakEnergy = peakDetection,
                        sleepPressurePoints = prefs.sleepPressurePoints,
                        nowMillis = slotMillis
                    )
                )

                val projectedEnergy = baseline.usableEnergy.coerceIn(0f, 100f)

                // FIX #1: Calculate hours ahead from snapshot time, not current time
                // This ensures consistent energy blending throughout the scheduling run
                val hoursAhead = ((slotMillis - energySnapshotMillis).coerceAtLeast(0L) / 3_600_000f)
                val liveWeight = when {
                    hoursAhead <= 2f -> 0.35f
                    hoursAhead <= 6f -> 0.20f
                    hoursAhead <= 12f -> 0.10f
                    else -> 0.0f
                }

                // Check if live energy data is stale (>5 minutes old)
                val SIGNAL_FRESHNESS_THRESHOLD_MILLIS = 5 * 60_000L
                val isLiveDataStale = liveEnergyModel.overallFreshnessAgeMillis > SIGNAL_FRESHNESS_THRESHOLD_MILLIS

                // Reduce live weight if data is stale
                val effectiveLiveWeight = if (isLiveDataStale) {
                    val staleFactor = if (liveEnergyModel.overallFreshnessAgeMillis > 10 * 60_000L) {
                        0.25f  // Very stale (>10 min): reduce to 25%
                    } else {
                        0.5f   // Moderately stale (5-10 min): reduce to 50%
                    }
                    liveWeight * staleFactor
                } else {
                    liveWeight
                }

                val blendedEnergy = (
                    projectedEnergy * (1f - effectiveLiveWeight) +
                        (liveEnergyModel.availableEnergy.toFloat() * effectiveLiveWeight)
                    ).coerceIn(0f, 100f)

                val blendedConfidence = (
                    peakDetection.confidence.coerceIn(0.2f, 1f) * (1f - effectiveLiveWeight * 0.5f) +
                        liveEnergyModel.momentConfidence.coerceIn(0f, 1f) * (effectiveLiveWeight * 0.5f)
                    ).coerceIn(0.2f, 1f)

                // Log energy blending weights at DEBUG level
                android.util.Log.d(
                    "ScheduleAutoTasks",
                    "Energy blending for slot at ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date(slotMillis))}: " +
                            "hoursAhead=${String.format("%.1f", hoursAhead)}, " +
                            "liveWeight=${String.format("%.2f", liveWeight)}, " +
                            "effectiveLiveWeight=${String.format("%.2f", effectiveLiveWeight)}, " +
                            "baselineWeight=${String.format("%.2f", 1f - effectiveLiveWeight)}, " +
                            "baseline=${String.format("%.1f", projectedEnergy)}, " +
                            "live=${liveEnergyModel.availableEnergy}, " +
                            "blended=${String.format("%.1f", blendedEnergy)}, " +
                            "dataStale=$isLiveDataStale (age=${liveEnergyModel.overallFreshnessAgeMillis / 60_000}min)"
                )

                Pair(blendedEnergy.roundToInt(), blendedConfidence)
            }

            // Replan missed tasks first to keep past assignments from becoming stale.
            val missedDecisions = missedTasks.mapNotNull { missedTask ->
                val estimatedWorkDone = missedTask.lastSessionDurationMinutes?.roundToInt()
                    ?: missedTask.totalTimeTrackedMinutes.roundToInt()

                // Build set of all slots occupied by this missed task
                val missedTaskSlots = buildTaskOccupiedSlots(missedTask)

                autoSchedulingEngine.replanIncompleteTask(
                    missedTask,
                    timeSpentMinutes = estimatedWorkDone,
                    nowMillis = nowMillis,
                    energyScoreFn = energyScoreFn,
                    busySlotStartMillis = busySlotStartMillis - missedTaskSlots
                )
            }
            if (missedDecisions.isNotEmpty()) {
                applyDecisionsTransactionally(missedDecisions, allTasks)
            }

            // Replan existing auto-scheduled tasks if conditions warrant it
            // This enables true dynamic scheduling - tasks move to better slots as conditions change
            val replanCandidates = autoScheduledTasks.filter { task ->
                shouldReplanAutoScheduledTask(task, nowMillis, prefs)
            }

            if (replanCandidates.isNotEmpty()) {
                // Temporarily unschedule these tasks for replanning
                val unscheduledForReplan = replanCandidates.map { it.copy(
                    scheduledDate = null,
                    scheduledTime = null
                )}

                // Build set of all slots occupied by tasks being replanned
                val replanSlots = replanCandidates.flatMap { task ->
                    buildTaskOccupiedSlots(task)
                }.toSet()

                // Replan them with current conditions
                val replanDecisions = autoSchedulingEngine.planAutoSchedule(
                    unscheduledTasks = unscheduledForReplan,
                    nowMillis = nowMillis,
                    energyScoreFn = energyScoreFn,
                    busySlotStartMillis = busySlotStartMillis - replanSlots
                )

                if (replanDecisions.isNotEmpty()) {
                    applyDecisionsTransactionally(replanDecisions, allTasks)
                }
            }

            if (unscheduledTasks.isEmpty()) {
                return Result.success()
            }

            // Generate scheduling decisions for fresh unscheduled work.
            val decisions = autoSchedulingEngine.planAutoSchedule(
                unscheduledTasks = unscheduledTasks,
                nowMillis = nowMillis,
                energyScoreFn = energyScoreFn,
                busySlotStartMillis = busySlotStartMillis
            )

            if (decisions.isEmpty()) {
                return Result.success()
            }

            // Apply decisions transactionally
            applyDecisionsTransactionally(decisions, allTasks)

            Result.success()
        } catch (e: Exception) {
            // Log exception (in production, send to telemetry service)
            e.printStackTrace()
            // Retry on transient failures
            Result.retry()
        } finally {
            // FIX #2: Release worker lock
            synchronized(workerLock) {
                isWorkerRunning = false
            }
        }
    }

    private fun buildBlockedTaskIds(tasks: List<com.neuroflow.app.data.local.entity.TaskEntity>): Set<String> {
        val taskMap = tasks.associateBy { it.id }
        return tasks.filter { task ->
            // Check for external dependency (waitingFor field)
            if (task.waitingFor.isNotBlank()) {
                return@filter true
            }

            // Check for task dependencies
            val deps = dependencyIds(task)
            deps.any { depId ->
                val depTask = taskMap[depId]
                // Block if dependency doesn't exist OR is not completed
                depTask == null || depTask.status != TaskStatus.COMPLETED
            }
        }.map { it.id }.toSet()
    }

    private fun dependencyIds(task: com.neuroflow.app.data.local.entity.TaskEntity): Set<String> {
        return task.dependsOnTaskIds
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
    }

    private suspend fun applyDecisionsTransactionally(
        decisions: List<AutoSchedulingEngine.ScheduleDecision>,
        allTasks: List<com.neuroflow.app.data.local.entity.TaskEntity>
    ) {
        val nowMillis = System.currentTimeMillis()
        decisions.forEach { decision ->
            // Find the task to update
            val taskToUpdate = allTasks.find { it.id == decision.taskId } ?: return@forEach

            // Extract date and time from scheduled start millis
            val (scheduledDate, scheduledTime) = splitMillisToDateAndTime(decision.scheduledStartMillis)

            // Update task with scheduled date/time
            // NOTE: Do NOT overwrite estimatedDurationMinutes with decision.estimatedDurationMinutes
            // The decision duration is a scheduling-adjusted value (accounting for energy levels, task type, etc.)
            // We preserve the original estimatedDurationMinutes to maintain the task's original estimate
            val updatedTask = taskToUpdate.copy(
                scheduledDate = scheduledDate,
                scheduledTime = scheduledTime,
                isAutoScheduled = true,
                lastAutoScheduledAt = nowMillis,  // Track when task was auto-scheduled for replanning cooldown
                updatedAt = nowMillis
            )

            // Persist update
            taskRepository.update(updatedTask)

            // Persist telemetry as applied, then keep the local debug log for diagnostics.
            val appliedDecision = decision.copy(
                telemetry = decision.telemetry.copy(
                    wasApplied = true,
                    selectedSlotDate = scheduledDate,
                    selectedSlotTime = scheduledTime
                )
            )
            persistTelemetry(appliedDecision)
            logTelemetry(appliedDecision)
        }
    }

    private fun splitMillisToDateAndTime(millis: Long): Pair<Long, Long> {
        // FIX #5: DST-safe date/time splitting
        // Use Calendar.getInstance() which respects system timezone and DST rules
        // This ensures correct behavior during DST transitions (spring forward/fall back)
        val cal = java.util.Calendar.getInstance().apply {
            timeInMillis = millis
        }

        // Extract date (start of day) - Calendar handles DST automatically
        val dateStart = java.util.Calendar.getInstance().apply {
            timeInMillis = millis
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val dateMillis = dateStart.timeInMillis

        // Extract time of day offset
        // During DST transitions, this correctly captures the actual time offset
        val timeOffset = millis - dateMillis

        return dateMillis to timeOffset
    }

    private suspend fun persistTelemetry(decision: AutoSchedulingEngine.ScheduleDecision) {
        val telemetry = decision.telemetry
        autoScheduleTelemetryDao.insert(
            AutoScheduleTelemetryEntity(
                taskId = telemetry.taskId,
                generatedAtMillis = telemetry.generatedAtMillis,
                horizonDays = telemetry.horizonDays,
                wasApplied = telemetry.wasApplied,
                selectedSlotDate = telemetry.selectedSlotDate,
                selectedSlotTime = telemetry.selectedSlotTime,
                candidateSlotStartMillisJson = telemetry.candidateSlotStartMillis.toJsonArray(),
                rejectedCandidateSlotStartMillisJson = telemetry.rejectedCandidateSlotStartMillis.toJsonArray(),
                rejectionReason = telemetry.rejectionReason?.name,
                assignmentReason = decision.assignmentReason,
                fitScore = decision.fitScore.overallScore,
                energyMatch = decision.fitScore.energyMatch,
                tagFit = decision.fitScore.tagFit,
                deadlineUrgency = decision.fitScore.deadlineUrgency,
                confidence = telemetry.inputs.confidence,
                energyScore = telemetry.inputs.energyScore,
                deadlinePressure = telemetry.inputs.deadlinePressure,
                estimatedDurationMinutes = telemetry.inputs.estimatedDurationMinutes
            )
        )
    }

    private fun List<Long>.toJsonArray(): String =
        joinToString(prefix = "[", postfix = "]") { it.toString() }

    private fun logTelemetry(decision: AutoSchedulingEngine.ScheduleDecision) {
        android.util.Log.d(
            "ScheduleAutoTasks",
            "Applied decision: task=${decision.taskId}, slot=${decision.assignedSlotIndex}, " +
                    "reason=${decision.assignmentReason}, wasApplied=${decision.telemetry.wasApplied}"
        )
    }

    private fun buildTaskOccupiedSlots(task: com.neuroflow.app.data.local.entity.TaskEntity): Set<Long> {
        val scheduledDate = task.scheduledDate ?: return emptySet()
        val scheduledTime = task.scheduledTime ?: return emptySet()
        val startMillis = scheduledDate + scheduledTime

        val roundedStart = roundDownToSchedulingBlock(startMillis)
        val estimated = task.estimatedDurationMinutes.coerceAtLeast(SCHEDULING_BLOCK_MINUTES)
        val slotCount = ((estimated + SCHEDULING_BLOCK_MINUTES - 1) / SCHEDULING_BLOCK_MINUTES).coerceAtLeast(1)

        val slots = mutableSetOf<Long>()

        // FIX #5: Handle midnight boundary correctly
        // Use Calendar to advance hours, which correctly handles day transitions
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = roundedStart }

        repeat(slotCount) { idx ->
            slots += cal.timeInMillis
            cal.add(java.util.Calendar.MINUTE, SCHEDULING_BLOCK_MINUTES)  // DST-safe block advancement
        }
        return slots
    }

    private fun buildBusySlotIndex(
        tasks: List<com.neuroflow.app.data.local.entity.TaskEntity>,
        nowMillis: Long
    ): Set<Long> {
        val busy = mutableSetOf<Long>()
        tasks.forEach { task ->
            val scheduledDate = task.scheduledDate ?: return@forEach
            val scheduledTime = task.scheduledTime ?: return@forEach
            val startMillis = scheduledDate + scheduledTime
            if (startMillis < nowMillis - 60 * 60 * 1000L) return@forEach

            val roundedStart = roundDownToSchedulingBlock(startMillis)
            val estimated = task.estimatedDurationMinutes.coerceAtLeast(SCHEDULING_BLOCK_MINUTES)
            val slotCount = ((estimated + SCHEDULING_BLOCK_MINUTES - 1) / SCHEDULING_BLOCK_MINUTES).coerceAtLeast(1)

            // FIX #5: Handle midnight boundary correctly
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = roundedStart }

            repeat(slotCount) { idx ->
                busy += cal.timeInMillis
                cal.add(java.util.Calendar.MINUTE, SCHEDULING_BLOCK_MINUTES)  // DST-safe block advancement
            }
        }
        return busy
    }

    private fun roundDownToSchedulingBlock(millis: Long): Long {
        // DST-safe 30-minute rounding. Calendar preserves the local timezone rules.
        val cal = java.util.Calendar.getInstance().apply {
            timeInMillis = millis
            val blockMinute = (get(java.util.Calendar.MINUTE) / SCHEDULING_BLOCK_MINUTES) * SCHEDULING_BLOCK_MINUTES
            set(java.util.Calendar.MINUTE, blockMinute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    /**
     * Determines if an auto-scheduled task should be replanned.
     *
     * Replanning triggers:
     * 1. Deadline approaching (< 6 hours) but scheduled too late
     * 2. Task scheduled in LOW energy but PEAK energy now available earlier
     * 3. Task postponed multiple times (procrastination pattern)
     * 4. Scheduled time conflicts with new higher-priority task
     * 5. Energy prediction changed significantly since scheduling
     */
    private fun shouldReplanAutoScheduledTask(
        task: com.neuroflow.app.data.local.entity.TaskEntity,
        nowMillis: Long,
        prefs: com.neuroflow.app.data.local.UserPreferences
    ): Boolean {
        val scheduledMillis = (task.scheduledDate ?: return false) + (task.scheduledTime ?: 0L)
        val hoursUntilScheduled = (scheduledMillis - nowMillis) / 3_600_000f

        // Don't replan tasks scheduled very soon (< 2 hours) - too disruptive
        if (hoursUntilScheduled < 2f) {
            android.util.Log.d(
                "ScheduleAutoTasks",
                "Replanning skipped for task ${task.id}: scheduled too soon (${String.format("%.1f", hoursUntilScheduled)} hours)"
            )
            return false
        }

        // 1. Deadline approaching but scheduled too late
        val deadlineMillis = task.deadlineDate?.let { it + (task.deadlineTime ?: 0L) }
        if (deadlineMillis != null) {
            val hoursUntilDeadline = (deadlineMillis - nowMillis) / 3_600_000f

            // If deadline < 6 hours away and task scheduled > 50% of remaining time
            if (hoursUntilDeadline < 6f && hoursUntilScheduled > hoursUntilDeadline * 0.5f) {
                android.util.Log.i(
                    "ScheduleAutoTasks",
                    "Replanning task ${task.id}: deadline approaching (${String.format("%.1f", hoursUntilDeadline)} hours) but scheduled too late"
                )
                return true
            }

            // If deadline < 12 hours and task is LOW priority but should be HIGH
            if (hoursUntilDeadline < 12f && task.priority == com.neuroflow.app.domain.model.Priority.LOW) {
                android.util.Log.i(
                    "ScheduleAutoTasks",
                    "Replanning task ${task.id}: deadline approaching (${String.format("%.1f", hoursUntilDeadline)} hours) and priority should be elevated"
                )
                return true
            }
        }

        // 2. Task postponed multiple times (procrastination pattern)
        if (task.postponeCount >= 3) {
            android.util.Log.i(
                "ScheduleAutoTasks",
                "Replanning task ${task.id}: postponed ${task.postponeCount} times (procrastination pattern)"
            )
            return true
        }

        // 3. Task scheduled far in future but could be done sooner
        // Only replan if scheduled > 24 hours away and not locked
        if (hoursUntilScheduled > 24f && !task.isScheduleLocked) {
            // Check if this is a high-priority or high-impact task
            val isImportant = task.priority == com.neuroflow.app.domain.model.Priority.HIGH ||
                             task.impactScore >= 80 ||
                             task.isFrog

            if (isImportant) {
                android.util.Log.i(
                    "ScheduleAutoTasks",
                    "Replanning task ${task.id}: important task scheduled far in future (${String.format("%.1f", hoursUntilScheduled)} hours)"
                )
                return true
            }
        }

        // 4. Periodic refresh: replan tasks every 6 hours to adapt to changing conditions
        // This ensures tasks continuously optimize as energy predictions improve
        val taskAge = nowMillis - (task.lastAutoScheduledAt ?: task.updatedAt)

        // FIX #3: Allow urgent replanning to bypass 6-hour cooldown
        // Check if deadline is urgent (< 6 hours) - if so, reduce cooldown to 1 hour
        val isUrgentDeadline = deadlineMillis?.let {
            (it - nowMillis) < 6 * 60 * 60 * 1000L
        } ?: false

        val cooldownMillis = if (isUrgentDeadline) {
            1 * 60 * 60 * 1000L  // 1 hour cooldown for urgent tasks
        } else {
            6 * 60 * 60 * 1000L  // 6 hour cooldown for normal tasks
        }

        if (taskAge > cooldownMillis && prefs.autoSchedulingEnabled) {
            // Only replan a subset to avoid thrashing
            // Use time-based rotation: each 6-hour cycle targets a different 20% of tasks
            // This ensures all tasks eventually get replanned, not just the same 20%
            val cycleNumber = (nowMillis / (6 * 60 * 60 * 1000L)) % 5
            val taskBucket = (task.id.hashCode().toLong() and 0x7FFFFFFF) % 5
            val shouldRefresh = cycleNumber == taskBucket

            if (shouldRefresh) {
                val cooldownType = if (isUrgentDeadline) "urgent (1h)" else "normal (6h)"
                android.util.Log.i(
                    "ScheduleAutoTasks",
                    "Replanning task ${task.id}: periodic refresh (${taskAge / 3_600_000} hours since last auto-schedule, cycle $cycleNumber, cooldown: $cooldownType)"
                )
                return true
            } else {
                android.util.Log.d(
                    "ScheduleAutoTasks",
                    "Replanning skipped for task ${task.id}: not in current refresh cycle (cycle $cycleNumber, task bucket $taskBucket)"
                )
            }
        } else if (taskAge <= cooldownMillis) {
            val cooldownType = if (isUrgentDeadline) "urgent (1h)" else "normal (6h)"
            android.util.Log.d(
                "ScheduleAutoTasks",
                "Replanning skipped for task ${task.id}: cooldown period active (${taskAge / 3_600_000} hours since last auto-schedule, cooldown: $cooldownType)"
            )
        }

        return false
    }
}
