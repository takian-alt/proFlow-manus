package com.neuroflow.app.domain.scheduler

import com.neuroflow.app.data.local.UserPreferences
import com.neuroflow.app.data.local.UserPreferencesDataStore
import com.neuroflow.app.data.local.entity.TaskEntity
import com.neuroflow.app.domain.model.TaskStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import java.util.Calendar

private const val SCHEDULING_BLOCK_MINUTES = 30

/**
 * Phase 3: AutoSchedulingEngine
 *
 * Orchestrates auto-scheduling decisions by:
 * 1. Building capacity horizon across 3-day window with time slots
 * 2. Scoring task-slot fit using tag profiles + energy context
 * 3. Inserting recovery breaks based on cognitive load
 * 4. Generating ranked scheduling decisions with telemetry
 *
 * Consumes:
 * - AutoSchedulingContracts (safety gates, gating functions)
 * - TaskTagSchedulingProfile (tag-to-profile mapping)
 * - UserPreferencesDataStore (auto-scheduling settings)
 */
@Singleton
class AutoSchedulingEngine @Inject constructor(
    private val preferencesDataStore: UserPreferencesDataStore
) {

    // ==================== Data Classes ====================

    data class TimeSlot(
        val startMillis: Long,
        val endMillis: Long,
        val dayIndex: Int,
        val hourOfDay: Int,
        val availableCapacityMinutes: Int,
        val availableEnergy: Int,
        val energyProfile: EnergyProfile,
        var assignedMinutes: Int = 0,
        var reservedBreakMinutes: Int = 0,
        val cognitiveLoadPercent: Int = 0
    )

    data class EnergyProfile(
        val zone: EnergyZone,
        val confidence: Float,
        val circadianBonus: Float
    ) {
        companion object {
            fun from(
                energyScore: Int,
                momentConfidence: Float,
                circadianFactor: Float,
                hourKey: Int = -1,
                // W11 fix: hysteresis state is now passed in per planning cycle instead of
                // living in a companion-object map. This eliminates the shared-mutable-state
                // race condition while preserving the hysteresis behaviour.
                hysteresisState: MutableMap<Int, EnergyZone> = mutableMapOf()
            ): EnergyProfile {
                val previousZone = if (hourKey >= 0) hysteresisState[hourKey] else null
                val zone = when {
                    // Upward transitions require crossing ABOVE the upper hysteresis threshold
                    previousZone == EnergyZone.HIGH && energyScore >= 83 -> EnergyZone.PEAK
                    previousZone == EnergyZone.MODERATE && energyScore >= 63 -> EnergyZone.HIGH
                    previousZone == EnergyZone.LOW && energyScore >= 43 -> EnergyZone.MODERATE
                    previousZone == EnergyZone.CRITICAL && energyScore >= 23 -> EnergyZone.LOW
                    // Downward transitions require dropping BELOW the lower hysteresis threshold
                    previousZone == EnergyZone.PEAK && energyScore >= 77 -> EnergyZone.PEAK
                    previousZone == EnergyZone.HIGH && energyScore >= 57 -> EnergyZone.HIGH
                    previousZone == EnergyZone.MODERATE && energyScore >= 37 -> EnergyZone.MODERATE
                    previousZone == EnergyZone.LOW && energyScore >= 17 -> EnergyZone.LOW
                    // No hysteresis context — use standard thresholds
                    else -> when (energyScore) {
                        in 80..100 -> EnergyZone.PEAK
                        in 60..79 -> EnergyZone.HIGH
                        in 40..59 -> EnergyZone.MODERATE
                        in 1..39 -> EnergyZone.LOW
                        else -> EnergyZone.CRITICAL
                    }
                }
                if (hourKey >= 0) hysteresisState[hourKey] = zone
                return EnergyProfile(zone, momentConfidence, circadianFactor)
            }
        }
    }

    enum class EnergyZone { PEAK, HIGH, MODERATE, LOW, CRITICAL }

    data class TaskSlotFitScore(
        val taskId: String,
        val slotIndex: Int,
        val overallScore: Float,
        val energyMatch: Float,
        val tagFit: Float,
        val deadlineUrgency: Float,
        val fragmentationTolerance: Float,
        val breakPlacementBonus: Float = 0.0f
    ) : Comparable<TaskSlotFitScore> {
        override fun compareTo(other: TaskSlotFitScore): Int = other.overallScore.compareTo(this.overallScore)
    }

    data class ScheduleDecision(
        val taskId: String,
        val assignedSlotIndex: Int,
        val scheduledStartMillis: Long,
        val estimatedDurationMinutes: Int,
        val assignmentReason: String,
        val fitScore: TaskSlotFitScore,
        val telemetry: AutoScheduleDecisionTelemetry
    )

    data class CapacityHorizon(
        val slots: List<TimeSlot>,
        val horizonStartMillis: Long,
        val horizonEndMillis: Long,
        val totalAvailableMinutes: Int,
        val averageEnergyLevel: Int
    )

    data class DeadlinePressure(
        val daysUntilDeadline: Long,
        val pressureLevel: PressureLevel, // URGENT, HIGH, MODERATE, LOW
        val recommendedPacingDensity: Float // 0.3 (spread out) to 1.0 (pack tight)
    )

    enum class PressureLevel { URGENT, HIGH, MODERATE, LOW }

    // ==================== Public API ====================

    /**
     * Main entry point: Generate auto-schedule decisions for eligible unscheduled tasks.
     * Returns ranked list of scheduling assignments ready for transactional apply (Phase 4).
     *
     * **Mis-planning prevention strategy:**
     * 1. Respect energy boundaries: don't schedule high-effort tasks in low-energy slots
     * 2. Leave buffer capacity: never fill slots > 70% to allow for breaks and unexpected tasks
     * 3. Cluster compatible tasks: group low-fragmentation tasks together
     * 4. Respect dependencies: tasks are only eligible if their blockers are complete
     * 5. Prevent high-effort clustering: avoid stacking multiple high-effort tasks
     */
    suspend fun planAutoSchedule(
        unscheduledTasks: List<TaskEntity>,
        nowMillis: Long,
        energyScoreFn: suspend (Long) -> Pair<Int, Float>,
        busySlotStartMillis: Set<Long> = emptySet()
    ): List<ScheduleDecision> {
        // Build dependency graph once for all tasks (performance optimization)
        val taskMap = unscheduledTasks.associateBy { it.id }
        val dependencyGraph = buildDependencyGraph(unscheduledTasks)
        val blockedTaskIds = detectBlockedTasks(dependencyGraph, taskMap)

        // Filter: only include tasks eligible for auto-scheduling per Phase 1 contracts
        val eligibleTasks = unscheduledTasks.filter { task ->
            AutoSchedulingContracts.isMutableByAutoScheduler(task) &&
                !AutoSchedulingContracts.hasManualScheduleData(task) &&
                task.id !in blockedTaskIds
        }

        if (eligibleTasks.isEmpty()) {
            return emptyList()
        }

        val prefs = preferencesDataStore.preferencesFlow.first()
        if (!prefs.autoSchedulingEnabled) {
            return emptyList()
        }

        // Build capacity horizon
        val horizon = calculateCapacityHorizon(
            nowMillis = nowMillis,
            horizonDays = prefs.autoSchedulingHorizonDays,
            prefs = prefs,
            energyScoreFn = energyScoreFn
        )

        if (horizon.slots.isEmpty()) {
            return emptyList()
        }

        // **Mis-planning prevention: Sort tasks by urgency to assign deadline-critical tasks first**
        val sortedTasks = eligibleTasks.sortedWith(
            compareByDescending<TaskEntity> { priorityLevelScore(it) + impactPriorityBlend(it) }
                .thenBy { it.deadlineDate ?: Long.MAX_VALUE }
        )

        // Score all task-slot combinations per task
        val fitScoresByTask = mutableMapOf<String, MutableList<TaskSlotFitScore>>()
        for (task in sortedTasks) {
            val tagProfiles = TaskTagSchedulingProfile.profilesFor(task.tags)
            val deadlinePressure = calculateDeadlinePressure(task, nowMillis)

            for ((slotIndex, slot) in horizon.slots.withIndex()) {
                if (slot.startMillis <= nowMillis) {
                    continue
                }

                if (slot.availableCapacityMinutes <= 0) {
                    continue
                }

                if (busySlotStartMillis.contains(slot.startMillis)) {
                    continue
                }

                val deadlineMillis = task.deadlineDate?.plus(task.deadlineTime ?: 0L)
                if (deadlineMillis != null && !isAspirationalDeadline(task) && slot.startMillis > deadlineMillis) {
                    continue
                }

                // **Mis-planning prevention: Skip slots that are already over-packed**
                val slotUtilization = calculateSlotUtilization(slot)
                val utilizationLimit = utilizationLimit(slot, prefs)
                if (slotUtilization > utilizationLimit) { // Leave buffer
                    continue
                }

                // **W3 fix: Soft energy penalty instead of binary rejection**
                // Tasks that marginally fail energy checks get a fit-score penalty
                // instead of outright rejection. Only hard-block extreme mismatches.
                val (energyAllowed, energyPenalty) = energyDemandFitPenalty(task, slot)
                if (!energyAllowed) {
                    continue // Hard block: extreme mismatch (e.g., ANALYTICAL in CRITICAL)
                }

                val realisticDuration = calculateRealisticDuration(task, slot)
                if (!AutoSchedulingContracts.respectsPlacementConstraints(
                        task = task,
                        startMillis = slot.startMillis,
                        endMillis = slot.endMillis,
                        durationMinutes = realisticDuration
                    )
                ) {
                    continue
                }
                val projectedEndMillis = slot.startMillis + realisticDuration * 60_000L
                if (isHardDeadline(task) && deadlineMillis != null && projectedEndMillis > deadlineMillis) {
                    continue
                }
                if (overlapsProtectedRest(slot.startMillis, projectedEndMillis, prefs)) continue

                val fitScore = scoreTaskSlotFit(
                    task = task,
                    slot = slot,
                    slotIndex = slotIndex,
                    tagProfiles = tagProfiles,
                    deadlinePressure = deadlinePressure,
                    prefs = prefs,
                    nowMillis = nowMillis,
                    energyPenalty = energyPenalty
                )
                fitScoresByTask.getOrPut(task.id) { mutableListOf() }.add(fitScore)
            }
        }

        // Greedy assignment: iterate by task urgency and select best viable slot per task.
        val decisions = mutableListOf<ScheduleDecision>()
        val blockedSlotIndices = mutableSetOf<Int>()
        val assignedTasksByDay = mutableMapOf<Int, Int>()
        val deepWorkMinutesByDay = mutableMapOf<Int, Int>()
        val highCognitiveMinutesByDay = mutableMapOf<Int, Int>()
        val highCognitiveHoursByDay = mutableMapOf<Int, MutableSet<Int>>()
        // W5 fix: Track cognitive minutes as a session cluster, not per-task.
        // Only trigger a break when the accumulated cluster exceeds the threshold.
        val highCognitiveMinutesSinceBreakByDay = mutableMapOf<Int, Int>()
        // W7 fix: Track assigned categories per slot for cohesion bonus
        val assignedCategoryBySlot = mutableMapOf<Int, TaskCategory>()
        val breakPolicy = resolveBreakPolicy(prefs)

        sortedTasks.forEach { task ->
            if (decisions.any { it.taskId == task.id }) {
                return@forEach
            }

            val taskFits = fitScoresByTask[task.id].orEmpty().sortedWith(
                // Always prioritize by score first, then by timing
                // This ensures we pick the BEST slot, not just the earliest
                compareByDescending<TaskSlotFitScore> { it.overallScore }
                    .thenBy { horizon.slots[it.slotIndex].startMillis }
            )

            if (taskFits.isEmpty()) {
                return@forEach
            }

            val deadlineMillis = task.deadlineDate?.plus(task.deadlineTime ?: 0L)

            // Collect all viable candidates, categorized by timing preference
            val todayPreferredCandidates = mutableListOf<Pair<TaskSlotFitScore, Int>>()
            val todayFallbackCandidates = mutableListOf<Pair<TaskSlotFitScore, Int>>()
            val futurePreferredCandidates = mutableListOf<Pair<TaskSlotFitScore, Int>>()
            val futureFallbackCandidates = mutableListOf<Pair<TaskSlotFitScore, Int>>()

            taskFits.forEach { fitScore ->
                if (blockedSlotIndices.contains(fitScore.slotIndex)) {
                    return@forEach
                }

                val slot = horizon.slots[fitScore.slotIndex]
                val estimatedDuration = calculateRealisticDuration(task, slot)

                // FIX #6: Support long-running tasks (>3 hours)
                // For tasks >3 hours, allow higher utilization limits and cross-day scheduling
                val isLongRunningTask = estimatedDuration > 180

                if (!hasContiguousAvailability(
                        slots = horizon.slots,
                        startIndex = fitScore.slotIndex,
                        durationMinutes = estimatedDuration,
                        blockedSlotIndices = blockedSlotIndices,
                        busySlotStartMillis = busySlotStartMillis,
                        allowCrossDay = isLongRunningTask  // Allow long tasks to span days
                    )
                ) {
                    return@forEach
                }

                if (prefs.autoSchedulingMaxTasksPerDay > 0 &&
                    (assignedTasksByDay[slot.dayIndex] ?: 0) >= prefs.autoSchedulingMaxTasksPerDay
                ) {
                    return@forEach
                }
                if (prefs.autoSchedulingMaxDeepWorkMinutesPerDay > 0 &&
                    isCognitivelyIntense(task) &&
                    (deepWorkMinutesByDay[slot.dayIndex] ?: 0) + estimatedDuration > prefs.autoSchedulingMaxDeepWorkMinutesPerDay
                ) {
                    return@forEach
                }

                if (!canAssignWithoutBurnout(
                        task = task,
                        slot = slot,
                        durationMinutes = estimatedDuration,
                        nowMillis = nowMillis,
                        prefs = prefs,
                        highCognitiveMinutesByDay = highCognitiveMinutesByDay,
                        highCognitiveHoursByDay = highCognitiveHoursByDay,
                        blockedSlotIndices = blockedSlotIndices,
                        slots = horizon.slots
                    )
                ) {
                    return@forEach
                }

                val preferredLatestStartMillis = resolvePreferredLatestStartMillis(
                    task = task,
                    nowMillis = nowMillis,
                    deadlineMillis = deadlineMillis,
                    estimatedDurationMinutes = estimatedDuration
                )

                val isTodaySlot = slot.dayIndex == 0
                val isWithinPreferredWindow = slot.startMillis <= preferredLatestStartMillis

                // Categorize candidates by timing preference
                when {
                    isTodaySlot && isWithinPreferredWindow -> {
                        todayPreferredCandidates.add(fitScore to estimatedDuration)
                    }
                    isTodaySlot && !isWithinPreferredWindow -> {
                        todayFallbackCandidates.add(fitScore to estimatedDuration)
                    }
                    !isTodaySlot && isWithinPreferredWindow -> {
                        futurePreferredCandidates.add(fitScore to estimatedDuration)
                    }
                    else -> {
                        futureFallbackCandidates.add(fitScore to estimatedDuration)
                    }
                }
            }

            // Select candidate balancing timing preference with score quality.
            // Preferred slots are chosen if fit score is high; fallback slots are used only if quality is acceptable.
            val tagProfilesForTask = TaskTagSchedulingProfile.profilesFor(task.tags)
            fun effectiveFitScore(fit: TaskSlotFitScore): Float =
                (fit.overallScore + categoryCohesionBonus(task, fit.slotIndex, tagProfilesForTask, assignedCategoryBySlot))
                    .coerceIn(0f, 1f)

            val bestTodayPref = todayPreferredCandidates.maxByOrNull { effectiveFitScore(it.first) }
            val bestFuturePref = futurePreferredCandidates.maxByOrNull { effectiveFitScore(it.first) }
            val bestTodayFallback = todayFallbackCandidates.maxByOrNull { effectiveFitScore(it.first) }
            val bestFutureFallback = futureFallbackCandidates.maxByOrNull { effectiveFitScore(it.first) }

            // Pick candidate: prefer future preferred slot over today's fallback if future fit score is significantly higher (> 0.25 delta)
            val chosen = when {
                bestTodayPref != null -> bestTodayPref
                bestFuturePref != null && (bestTodayFallback == null || effectiveFitScore(bestFuturePref.first) > effectiveFitScore(bestTodayFallback.first) + 0.25f) -> bestFuturePref
                bestTodayFallback != null -> bestTodayFallback
                bestFuturePref != null -> bestFuturePref
                else -> bestFutureFallback
            } ?: return@forEach

            val baseFitScore = chosen.first
            val cohesionBonus = categoryCohesionBonus(task, baseFitScore.slotIndex, tagProfilesForTask, assignedCategoryBySlot)
            val fitScore = baseFitScore.copy(
                overallScore = (baseFitScore.overallScore + cohesionBonus).coerceIn(0f, 1f)
            )
            val estimatedDuration = chosen.second
            val slot = horizon.slots[fitScore.slotIndex]

            decisions.add(
                ScheduleDecision(
                    taskId = fitScore.taskId,
                    assignedSlotIndex = fitScore.slotIndex,
                    scheduledStartMillis = slot.startMillis,
                    estimatedDurationMinutes = estimatedDuration,
                    assignmentReason = buildAssignmentReason(fitScore),
                    fitScore = fitScore,
                    telemetry = AutoScheduleDecisionTelemetry(
                        taskId = fitScore.taskId,
                        generatedAtMillis = System.currentTimeMillis(),
                        horizonDays = prefs.autoSchedulingHorizonDays,
                        wasApplied = false,
                        candidateSlotStartMillis = taskFits.map { horizon.slots[it.slotIndex].startMillis },
                        rejectedCandidateSlotStartMillis = taskFits
                            .filter { it.slotIndex != fitScore.slotIndex }
                            .map { horizon.slots[it.slotIndex].startMillis },
                        inputs = AutoScheduleInputsSnapshot(
                            priorityScore = task.impactScore.toFloat(),
                            energyScore = slot.availableEnergy.toFloat(),
                            sleepPressurePoints = prefs.sleepPressurePoints,
                            hasDependencies = task.dependsOnTaskIds.isNotEmpty(),
                            estimatedDurationMinutes = estimatedDuration,
                            tagProfileHints = TaskTagSchedulingProfile.profilesFor(task.tags).map { it.tag },
                            confidence = slot.energyProfile.confidence,
                            deadlinePressure = deadlinePressure.recommendedPacingDensity
                        )
                    )
                )
            )

            assignedTasksByDay[slot.dayIndex] = (assignedTasksByDay[slot.dayIndex] ?: 0) + 1
            if (isCognitivelyIntense(task)) {
                deepWorkMinutesByDay[slot.dayIndex] = (deepWorkMinutesByDay[slot.dayIndex] ?: 0) + estimatedDuration
            }

            // Reserve occupied scheduling blocks for this assignment.
            occupyTaskSlots(
                slots = horizon.slots,
                startIndex = fitScore.slotIndex,
                durationMinutes = estimatedDuration,
                blockedSlotIndices = blockedSlotIndices
            )

            // W7 fix: Track assigned category for cohesion bonus in subsequent assignments
            val taskCategory = task.determineCategory()
            val neededSlots = slotSpanForDuration(estimatedDuration)
            for (offset in 0 until neededSlots) {
                val idx = fitScore.slotIndex + offset
                if (idx < horizon.slots.size) {
                    assignedCategoryBySlot[idx] = taskCategory
                }
            }

            trackCognitiveLoad(
                task = task,
                slot = slot,
                durationMinutes = estimatedDuration,
                highCognitiveMinutesByDay = highCognitiveMinutesByDay,
                highCognitiveHoursByDay = highCognitiveHoursByDay
            )

            if (isCognitivelyIntense(task)) {
                // Continuous tracker across day boundaries using key -1 for global continuous streak
                val accumulated = (highCognitiveMinutesSinceBreakByDay[-1] ?: 0) + estimatedDuration
                if (accumulated >= breakPolicy.intervalMinutes) {
                    val breakReserved = reserveBreakSlotsAfterTask(
                        slots = horizon.slots,
                        startIndex = fitScore.slotIndex,
                        taskDurationMinutes = estimatedDuration,
                        breakMinutes = breakPolicy.durationMinutes,
                        blockedSlotIndices = blockedSlotIndices,
                        nowMillis = nowMillis
                    )
                    if (breakReserved) {
                        highCognitiveMinutesSinceBreakByDay[-1] = 0
                    } else {
                        android.util.Log.w(
                            "AutoSchedulingEngine",
                            "Break reservation failed for task ${task.id} - cognitive load continues to accumulate"
                        )
                    }
                } else {
                    highCognitiveMinutesSinceBreakByDay[-1] = accumulated
                }
            }
        }

        return decisions.sortedByDescending { it.fitScore.overallScore }
    }

    /**
     * **Dynamic rescheduling**: Called when a previously-planned task wasn't completed.
     * Adjusts duration based on actual incomplete time and reschedules with updated urgency.
     *
     * **Anti-procrastination**: Schedules earlier if deadline pressure increased,
     * or earlier in the day to prevent "I'll do it tomorrow" procrastination.
     */
    suspend fun replanIncompleteTask(
        task: TaskEntity,
        timeSpentMinutes: Int, // How long was worked on before incompleteness detected
        nowMillis: Long,
        energyScoreFn: suspend (Long) -> Pair<Int, Float>,
        busySlotStartMillis: Set<Long> = emptySet()
    ): ScheduleDecision? {
        val prefs = preferencesDataStore.preferencesFlow.first()
        if (!prefs.autoSchedulingEnabled) {
            return null
        }

        // Respect locked tasks - don't replan them even if missed
        if (task.isScheduleLocked) {
            android.util.Log.i(
                "AutoSchedulingEngine",
                "Replanning skipped for task ${task.id}: task is locked"
            )
            return null
        }

        // Calculate how much time is actually remaining using task history.
        val remainingMinutes = estimateRemainingMinutes(task, timeSpentMinutes)

        // Calculate deadline pressure to adjust scheduling aggressiveness
        val deadlinePressure = calculateDeadlinePressure(task, nowMillis)

        // With high deadline pressure, schedule as soon as possible (PEAK energy preferred)
        val pressureAdjustment = when (deadlinePressure.pressureLevel) {
            PressureLevel.URGENT -> 1.5f // Compress more aggressively
            PressureLevel.HIGH -> 1.2f
            PressureLevel.MODERATE -> 1.0f
            PressureLevel.LOW -> 0.8f // Can afford to wait for better energy
        }

        // Build horizon
        val horizon = calculateCapacityHorizon(
            nowMillis = nowMillis,
            horizonDays = prefs.autoSchedulingHorizonDays,
            prefs = prefs,
            energyScoreFn = energyScoreFn
        )

        val projectedDurationMinutes = (remainingMinutes * pressureAdjustment).toInt().coerceAtLeast(15)

        // Find the best available slot with priority to PEAK energy when under pressure
        val tagProfiles = TaskTagSchedulingProfile.profilesFor(task.tags)
        val deadlineMillis = task.deadlineDate?.plus(task.deadlineTime ?: 0L)
        val allCandidates = horizon.slots.withIndex()
            .filter { (slotIndex, slot) ->
                slot.startMillis > nowMillis &&
                slot.availableCapacityMinutes > 0 &&
                !busySlotStartMillis.contains(slot.startMillis) &&
                hasContiguousAvailability(
                    slots = horizon.slots,
                    startIndex = slotIndex,
                    durationMinutes = projectedDurationMinutes,
                    blockedSlotIndices = emptySet(),
                    busySlotStartMillis = busySlotStartMillis
                ) &&
                // Filter by energy demand satisfaction
                isEnergyDemandSatisfied(task, slot) &&
                // Honor earliest start, day, avoid-window, and session-length constraints.
                AutoSchedulingContracts.respectsPlacementConstraints(
                    task = task,
                    startMillis = slot.startMillis,
                    endMillis = slot.endMillis,
                    durationMinutes = projectedDurationMinutes
                ) &&
                (task.deadlineDate == null || !task.isHardDeadline ||
                    slot.startMillis + projectedDurationMinutes * 60_000L <= deadlineMillis!!) &&
                // Filter by capacity (allow up to 85% when deadline is urgent, otherwise zone-specific limit)
                calculateSlotUtilization(slot) <= (if (deadlinePressure.pressureLevel == PressureLevel.URGENT) 0.85f else when (slot.energyProfile.zone) {
                    EnergyZone.CRITICAL -> 0.50f
                    else -> 0.70f
                })
            }
        val prioritizedCandidates = allCandidates.let { candidates ->
            val today = candidates.filter { (_, slot) -> slot.dayIndex == 0 }
            if (today.isNotEmpty()) today else candidates
        }
        val bestCandidate = prioritizedCandidates
            .maxByOrNull { (slotIdx, slot) ->
                // Rank slots: prefer PEAK energy when under deadline pressure
                val energyBonus = when (slot.energyProfile.zone) {
                    EnergyZone.PEAK -> 2.0f * pressureAdjustment
                    EnergyZone.HIGH -> 1.5f * pressureAdjustment
                    EnergyZone.MODERATE -> 1.0f
                    else -> 0.0f
                }
                // Also prefer earlier slots to prevent procrastination
                val dayBonus = if (slot.dayIndex == 0) 0.5f else if (slot.dayIndex == 1) 0.2f else 0f
                val deadlineBufferBonus = deadlineMillis?.let {
                    calculateDeadlineSafetyBufferFit(
                        task = task,
                        slotStartMillis = slot.startMillis,
                        deadlineMillis = it,
                        nowMillis = nowMillis
                    )
                } ?: 0.7f
                // W13 fix: Include category and chronotype fit in replan slot ranking
                // so replanned tasks get optimal time placement, not just energy-based.
                val tagProfiles = TaskTagSchedulingProfile.profilesFor(task.tags)
                val categoryFitBonus = calculateCategoryFit(task, slot, prefs)
                val chronoFitBonus = calculateChronotypeTaskFit(task, slot, prefs, tagProfiles)
                energyBonus + dayBonus + (deadlineBufferBonus * 1.2f) +
                    (categoryFitBonus * 0.4f) + (chronoFitBonus * 0.3f)
            } ?: return null

        val slotIndex = bestCandidate.index
        val bestSlot = bestCandidate.value
        val fitScore = scoreTaskSlotFit(
            task = task,
            slot = bestSlot,
            slotIndex = slotIndex,
            tagProfiles = tagProfiles,
            deadlinePressure = deadlinePressure,
            prefs = prefs,
            nowMillis = nowMillis
        )

        return ScheduleDecision(
            taskId = task.id,
            assignedSlotIndex = slotIndex,
            scheduledStartMillis = bestSlot.startMillis,
            estimatedDurationMinutes = projectedDurationMinutes,
            assignmentReason = "rescheduled incomplete: ${remainingMinutes}min remaining, ${deadlinePressure.pressureLevel}",
            fitScore = fitScore,
            telemetry = AutoScheduleDecisionTelemetry(
                taskId = task.id,
                        generatedAtMillis = System.currentTimeMillis(),
                horizonDays = prefs.autoSchedulingHorizonDays,
                wasApplied = false,
                candidateSlotStartMillis = allCandidates.map { it.value.startMillis },
                rejectedCandidateSlotStartMillis = allCandidates
                    .filter { it.index != slotIndex }
                    .map { it.value.startMillis },
                inputs = AutoScheduleInputsSnapshot(
                    priorityScore = task.impactScore.toFloat(),
                    energyScore = bestSlot.availableEnergy.toFloat(),
                    sleepPressurePoints = prefs.sleepPressurePoints,
                    hasDependencies = false,
                    estimatedDurationMinutes = remainingMinutes,
                    tagProfileHints = tagProfiles.map { it.tag },
                    confidence = bestSlot.energyProfile.confidence,
                    deadlinePressure = deadlinePressure.recommendedPacingDensity
                )
            )
        )
    }

    /**
     * **Anti-procrastination spacing**: Returns optimal task distribution.
     * - Close deadline → pack tasks tighter, fill momentum windows
     * - Far deadline → spread out, maintain anti-procrastination pace (1-2 tasks per day)
     * - Prevents "I have time" procrastination trap
     */
    suspend fun calculateOptimalSpacing(
        unscheduledTasks: List<TaskEntity>,
        nowMillis: Long
    ): Map<String, Float> {
        val prefs = preferencesDataStore.preferencesFlow.first()

        // Calculate deadline pressure for each task
        val spacingFactors = mutableMapOf<String, Float>()

        for (task in unscheduledTasks) {
            val pressure = calculateDeadlinePressure(task, nowMillis)

            // Spacing factor = how to distribute tasks across available time
            // 1.0 = normal packing
            // 0.5 = spread out (anti-procrastination)
            // 1.5 = compress aggressively (deadline urgent)
            val spacingFactor = when (pressure.pressureLevel) {
                PressureLevel.URGENT -> 1.5f // Pack tight, maintain momentum
                PressureLevel.HIGH -> 1.2f   // Moderately compress
                PressureLevel.MODERATE -> 0.9f // Gentle spacing
                PressureLevel.LOW -> 0.5f   // Anti-procrastination: spread far out, but not too sparse
            }

            spacingFactors[task.id] = spacingFactor
        }

        return spacingFactors
    }

    // ==================== Private Implementation ====================

    /**
     * W3 fix: Converted from binary accept/reject to soft scoring penalty.
     * Instead of outright rejecting tasks from energy-mismatched slots, returns a
     * fit penalty (0.0 = perfect match, negative = penalty). Duration and effort
     * are factored — a 15-minute admin task in a LOW slot gets a small penalty,
     * while a 3-hour analytical task gets heavily penalized.
     *
     * Returns a Pair: first = whether the task is allowed at all (hard block for extreme
     * mismatches), second = fit penalty to add to the scoring.
     */
    private fun energyDemandFitPenalty(task: TaskEntity, slot: TimeSlot): Pair<Boolean, Float> {
        // Hard block: ANALYTICAL tasks in CRITICAL/LOW energy are always rejected
        if (task.taskType == com.neuroflow.app.domain.model.TaskType.ANALYTICAL &&
            slot.energyProfile.zone !in listOf(EnergyZone.PEAK, EnergyZone.HIGH, EnergyZone.MODERATE)
        ) {
            return false to 0f
        }

        val energyScore = slot.availableEnergy
        // Duration factor: short tasks (≤20 min) are more forgivable in mismatched slots
        val durationMinutes = if (task.estimatedDurationMinutes > 0) task.estimatedDurationMinutes else 30
        val durationFactor = when {
            durationMinutes <= 15 -> 0.3f   // Very short: minimal penalty
            durationMinutes <= 30 -> 0.5f   // Short: reduced penalty
            durationMinutes <= 60 -> 0.8f   // Medium: moderate penalty
            else -> 1.0f                     // Long: full penalty
        }

        return when {
            // High-effort tasks (80+): best in PEAK/HIGH, penalized elsewhere
            task.effortScore >= 80 -> when {
                energyScore >= 60 || slot.energyProfile.zone in listOf(EnergyZone.PEAK, EnergyZone.HIGH) -> true to 0f
                energyScore >= 40 -> true to (-0.08f * durationFactor)  // Moderate penalty
                energyScore >= 20 -> true to (-0.15f * durationFactor)  // Heavy penalty
                else -> false to 0f  // Hard block: critical energy for hard task
            }

            // Medium-effort tasks (60-79): flexible, penalized in LOW/CRITICAL
            task.effortScore >= 60 -> when {
                energyScore >= 40 || slot.energyProfile.zone in listOf(EnergyZone.PEAK, EnergyZone.HIGH, EnergyZone.MODERATE) -> true to 0f
                energyScore >= 20 -> true to (-0.06f * durationFactor)
                else -> true to (-0.12f * durationFactor)
            }

            // Low-effort tasks: can land anywhere with minimal/no penalty
            else -> true to 0f
        }
    }

    /**
     * Backward-compatible wrapper: returns true if the task can be scheduled in the slot.
     * Uses energyDemandFitPenalty internally but preserves the boolean interface for
     * code paths that only need accept/reject (e.g., replanIncompleteTask).
     */
    private fun isEnergyDemandSatisfied(task: TaskEntity, slot: TimeSlot): Boolean {
        return energyDemandFitPenalty(task, slot).first
    }

    /**
     * **Dynamic rescheduling helper**: Calculate deadline pressure to determine pacing.
     * - URGENT: deadline < 1 day away
     * - HIGH: deadline 1–3 days away
     * - MODERATE: deadline 3–7 days away
     * - LOW: deadline > 7 days away
     *
     * Returns pacing density (how tightly to pack tasks).
     */
    private fun calculateDeadlinePressure(task: TaskEntity, nowMillis: Long): DeadlinePressure {
        if (task.deadlineDate == null) {
            return DeadlinePressure(Long.MAX_VALUE, PressureLevel.LOW, 0.5f)
        }

        val deadlineMillis = task.deadlineDate + (task.deadlineTime ?: 0L)
        val daysUntil = (deadlineMillis - nowMillis) / (24 * 60 * 60 * 1000L)

        return when {
            daysUntil <= 1 -> DeadlinePressure(
                daysUntil,
                PressureLevel.URGENT,
                1.5f // Pack tight to maintain momentum
            )
            daysUntil <= 3 -> DeadlinePressure(
                daysUntil,
                PressureLevel.HIGH,
                1.2f // Moderately compress
            )
            daysUntil <= 7 -> DeadlinePressure(
                daysUntil,
                PressureLevel.MODERATE,
                0.9f // Gentle spacing, but don't procrastinate
            )
            else -> DeadlinePressure(
                daysUntil,
                PressureLevel.LOW,
                0.5f // Anti-procrastination: spread tasks, prevent "I have time" trap
            )
        }
    }

    /**
     * Mis-planning prevention: Calculate current slot utilization to prevent over-packing.
     * Returns fraction 0.0-1.0 of capacity used.
     */
    private fun isHardDeadline(task: TaskEntity): Boolean =
        task.isHardDeadline || task.deadlineType.equals("STRICT", ignoreCase = true)

    private fun isAspirationalDeadline(task: TaskEntity): Boolean =
        task.deadlineType.equals("ASPIRATIONAL", ignoreCase = true)

    private fun utilizationLimit(slot: TimeSlot, prefs: UserPreferences): Float {
        val modeLimit = when (prefs.autoSchedulingMode.uppercase()) {
            "CONSERVATIVE" -> 0.55f
            "AGGRESSIVE" -> 0.85f
            "RECOVERY" -> 0.45f
            "DEEP_WORK" -> 0.65f
            else -> 0.70f
        }
        val bufferAdjustment = (30 - prefs.autoSchedulingBufferPercent.coerceIn(0, 80)) / 100f
        val modeLimitWithBuffer = (modeLimit + bufferAdjustment).coerceIn(0.30f, 0.90f)
        return if (slot.energyProfile.zone == EnergyZone.CRITICAL) {
            min(0.50f, modeLimitWithBuffer)
        } else {
            modeLimitWithBuffer
        }
    }

    private fun overlapsProtectedRest(startMillis: Long, endMillis: Long, prefs: UserPreferences): Boolean {
        val restStart = prefs.autoSchedulingProtectedRestStartMinute
        val restEnd = prefs.autoSchedulingProtectedRestEndMinute
        if (restStart !in 0..1_439 || restEnd !in 0..1_440) return false

        val startCalendar = Calendar.getInstance().apply { timeInMillis = startMillis }
        val endCalendar = Calendar.getInstance().apply { timeInMillis = endMillis }
        if (startCalendar.get(Calendar.DAY_OF_YEAR) != endCalendar.get(Calendar.DAY_OF_YEAR) ||
            startCalendar.get(Calendar.YEAR) != endCalendar.get(Calendar.YEAR)
        ) return true

        val startMinute = startCalendar.get(Calendar.HOUR_OF_DAY) * 60 + startCalendar.get(Calendar.MINUTE)
        val endMinute = (endCalendar.get(Calendar.HOUR_OF_DAY) * 60 + endCalendar.get(Calendar.MINUTE))
            .coerceAtMost(1_440)
        return if (restStart <= restEnd) {
            startMinute < restEnd && endMinute > restStart
        } else {
            startMinute < restEnd || endMinute > restStart
        }
    }

    private fun calculateSlotUtilization(slot: TimeSlot): Float {
        if (slot.availableCapacityMinutes <= 0) {
            return 1.0f
        }
        val usedMinutes = slot.assignedMinutes + slot.reservedBreakMinutes
        return min(1.0f, usedMinutes.toFloat() / max(1, slot.availableCapacityMinutes))
    }

    /**
     * Mis-planning prevention: Calculate realistic task duration based on effort and slot energy.
     * Don't optimistically assume tasks will complete in estimated time if energy is low.
     * Accounts for task type - ANALYTICAL tasks typically take longer than ADMIN tasks.
     *
     * FIX #6: Cap at 360 minutes (6 hours) instead of 180 to support long-running tasks
     */
    private fun calculateRealisticDuration(task: TaskEntity, slot: TimeSlot): Int {
        val energyScore = slot.availableEnergy
        return com.neuroflow.app.domain.engine.DurationPredictionEngine.predictMinutes(task, energyScore)
    }

    private fun estimateRemainingMinutes(task: TaskEntity, timeSpentMinutes: Int): Int {
        val effectiveSpend = when {
            timeSpentMinutes > 0 -> timeSpentMinutes
            task.totalTimeTrackedMinutes > 0f -> task.totalTimeTrackedMinutes.roundToInt().coerceAtMost(task.estimatedDurationMinutes)
            else -> 0
        }

        val baseEstimate = if (task.estimatedDurationMinutes > 0) {
            task.estimatedDurationMinutes
        } else {
            when {
                task.effortScore >= 80 -> 60
                task.effortScore >= 60 -> 45
                else -> 30
            }
        }

        val adjustedEstimate = if (task.actualDurationMinutes != null && task.actualDurationMinutes > 0) {
            ((baseEstimate + task.actualDurationMinutes.toInt()) / 2f).roundToInt()
        } else {
            baseEstimate
        }

        return (adjustedEstimate - effectiveSpend).coerceAtLeast(15)
    }

    private fun hasContiguousAvailability(
        slots: List<TimeSlot>,
        startIndex: Int,
        durationMinutes: Int,
        blockedSlotIndices: Set<Int>,
        busySlotStartMillis: Set<Long>,
        allowCrossDay: Boolean = false  // FIX #6: Allow long tasks to span days
    ): Boolean {
        val neededSlots = slotSpanForDuration(durationMinutes)
        if (startIndex + neededSlots > slots.size) return false

        val dayIndex = slots[startIndex].dayIndex
        for (offset in 0 until neededSlots) {
            val idx = startIndex + offset
            val slot = slots[idx]

            // FIX #6: For long-running tasks, allow cross-day scheduling
            if (!allowCrossDay && slot.dayIndex != dayIndex) return false

            // W9 fix: When cross-day scheduling is allowed, validate that each slot
            // falls within working hours. Tasks >3 hours shouldn't scatter across sleep hours.
            if (allowCrossDay && slot.dayIndex != dayIndex) {
                // hourOfDay is the actual clock hour of the slot.
                // Slots outside the planning window (built from resolveDailyPlanningWindow)
                // shouldn't exist in the horizon, but double-check for safety.
                if (slot.hourOfDay < 6 || slot.hourOfDay > 22) return false
            }

            if (slot.availableCapacityMinutes <= 0) return false
            if (idx in blockedSlotIndices) return false
            if (busySlotStartMillis.contains(slot.startMillis)) return false

            // FIX #6: For long-running tasks, use higher utilization limit (85%)
            val contiguousUtilizationLimit = if (allowCrossDay) {
                0.85f  // Long tasks can use more capacity
            } else {
                when (slot.energyProfile.zone) {
                    EnergyZone.CRITICAL -> 0.50f
                    else -> 0.70f
                }
            }
            if (calculateSlotUtilization(slot) > contiguousUtilizationLimit) return false
        }

        return true
    }

    private fun slotSpanForDuration(durationMinutes: Int): Int {
        return ((durationMinutes.coerceAtLeast(1) + SCHEDULING_BLOCK_MINUTES - 1) /
            SCHEDULING_BLOCK_MINUTES).coerceAtLeast(1)
    }

    private fun occupyTaskSlots(
        slots: List<TimeSlot>,
        startIndex: Int,
        durationMinutes: Int,
        blockedSlotIndices: MutableSet<Int>
    ) {
        val neededSlots = slotSpanForDuration(durationMinutes)
        val startDayIndex = slots[startIndex].dayIndex

        // FIX #6: Support long-running tasks that span multiple days
        for (offset in 0 until neededSlots) {
            val idx = startIndex + offset
            if (idx >= slots.size) break
            val slot = slots[idx]

            // Allow cross-day occupation for long tasks
            // (no day index check - let long tasks span days)

            val minutesToAssign = min(
                durationMinutes - (offset * SCHEDULING_BLOCK_MINUTES),  // Remaining task minutes for this block
                slot.availableCapacityMinutes - slot.assignedMinutes  // Available space in slot
            ).coerceAtLeast(0)

            slot.assignedMinutes += minutesToAssign

            // Only block slot if utilization has hit capacity limits
            val limit = when (slot.energyProfile.zone) {
                EnergyZone.CRITICAL -> 0.50f
                else -> 0.70f
            }
            if (calculateSlotUtilization(slot) >= limit) {
                blockedSlotIndices += idx
            }
        }
    }

    private fun reserveBreakSlotsAfterTask(
        slots: List<TimeSlot>,
        startIndex: Int,
        taskDurationMinutes: Int,
        breakMinutes: Int,
        blockedSlotIndices: MutableSet<Int>,
        nowMillis: Long
    ): Boolean {
        var remainingBreak = breakMinutes.coerceAtLeast(0)
        if (remainingBreak <= 0) return false

        val dayIndex = slots[startIndex].dayIndex
        var idx = startIndex + slotSpanForDuration(taskDurationMinutes)
        var slotsReserved = 0

        while (idx < slots.size && remainingBreak > 0) {
            // Guard 1: Bounds check (explicit, even though while condition covers it)
            if (idx >= slots.size) break

            val slot = slots[idx]
            if (slot.dayIndex != dayIndex) break

            // Guard 2: Past time check — skip slots that are already in the past
            if (slot.startMillis <= nowMillis) {
                android.util.Log.d(
                    "AutoSchedulingEngine",
                    "Break reservation skipped for slot $idx: slot is in the past (startMillis: ${slot.startMillis}, nowMillis: $nowMillis)"
                )
                idx++
                continue
            }

            // Guard 3: Already blocked check — skip slots already reserved
            if (idx in blockedSlotIndices) {
                android.util.Log.d(
                    "AutoSchedulingEngine",
                    "Break reservation skipped for slot $idx: slot already blocked"
                )
                idx++
                continue
            }

            // Guard 4: Capacity check — skip slots with no available capacity
            if (slot.availableCapacityMinutes <= 0) {
                android.util.Log.d(
                    "AutoSchedulingEngine",
                    "Break reservation skipped for slot $idx: no available capacity"
                )
                idx++
                continue
            }

            blockedSlotIndices += idx
            // W14 fix: Reserve only the actual break minutes needed, not the full 60-minute slot.
            // Previously this always reserved 60 min even when break policy only needed 15 min.
            val actualReserved = minOf(remainingBreak, slot.availableCapacityMinutes)
            slot.reservedBreakMinutes = actualReserved
            remainingBreak -= actualReserved
            slotsReserved++
            idx++
        }

        if (slotsReserved > 0) {
            android.util.Log.i(
                "AutoSchedulingEngine",
                "Reserved $slotsReserved break slot(s) after task (${breakMinutes - remainingBreak} minutes reserved)"
            )
            return true
        } else {
            android.util.Log.w(
                "AutoSchedulingEngine",
                "Failed to reserve any break slots - no available slots found"
            )
            return false
        }
    }

    private suspend fun calculateCapacityHorizon(
        nowMillis: Long,
        horizonDays: Int,
        prefs: UserPreferences,
        energyScoreFn: suspend (Long) -> Pair<Int, Float>
    ): CapacityHorizon {
        // W11 fix: Create a fresh hysteresis map per planning cycle (instance-level, not shared).
        // This eliminates the companion-object shared-state race condition while preserving
        // the zone-hysteresis behaviour across sequential slot creation within one cycle.
        val hysteresisState = mutableMapOf<Int, EnergyZone>()

        val slots = mutableListOf<TimeSlot>()
        val (dayStartHour, dayEndHourInclusive) = resolveDailyPlanningWindow(prefs)

        var totalMinutes = 0
        var totalEnergy = 0
        var slotCount = 0

        for (dayIdx in 0 until horizonDays) {
            // DST-safe: create a fresh Calendar instance for each day to avoid DST carry-over.
            // Using Calendar.add(HOUR_OF_DAY, 1) instead of set() ensures DST transitions are
            // handled correctly: spring-forward skips the missing hour, fall-back handles the
            // repeated hour with distinct timeInMillis values.
            val dayCal = Calendar.getInstance().apply {
                timeInMillis = nowMillis
                // Advance to the target day
                add(Calendar.DAY_OF_YEAR, dayIdx)
                // Reset to start of day
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val windowStart = Calendar.getInstance().apply {
                timeInMillis = dayCal.timeInMillis
                set(Calendar.HOUR_OF_DAY, dayStartHour)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val windowEndExclusive = Calendar.getInstance().apply {
                timeInMillis = dayCal.timeInMillis
                set(Calendar.HOUR_OF_DAY, dayEndHourInclusive + 1)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val firstSlot = Calendar.getInstance().apply {
                timeInMillis = if (dayIdx == 0) nowMillis else windowStart.timeInMillis
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (dayIdx == 0) {
                    val currentMinute = get(Calendar.MINUTE)
                    val nextBlockMinute = ((currentMinute / SCHEDULING_BLOCK_MINUTES) + 1) * SCHEDULING_BLOCK_MINUTES
                    if (nextBlockMinute >= 60) {
                        add(Calendar.HOUR_OF_DAY, 1)
                        set(Calendar.MINUTE, 0)
                    } else {
                        set(Calendar.MINUTE, nextBlockMinute)
                    }
                }
            }
            val firstSlotMillis = maxOf(firstSlot.timeInMillis, windowStart.timeInMillis)
            val slotCal = Calendar.getInstance().apply { timeInMillis = firstSlotMillis }

            while (slotCal.timeInMillis < windowEndExclusive.timeInMillis) {
                val slotStartMillis = slotCal.timeInMillis

                // Skip if slot is in the past (safety check)
                if (slotStartMillis <= nowMillis) {
                    slotCal.add(Calendar.MINUTE, SCHEDULING_BLOCK_MINUTES)
                    continue
                }

                val slotEndMillis = Calendar.getInstance().apply {
                    timeInMillis = slotStartMillis
                    add(Calendar.MINUTE, SCHEDULING_BLOCK_MINUTES)
                }.timeInMillis

                // Query energy for this 30-minute block.
                val (energyScore, confidence) = energyScoreFn(slotStartMillis)
                // W11 fix: Pass hourKey and per-cycle hysteresisState for zone tracking
                val slotHourKey = dayIdx * 24 * 60 +
                    slotCal.get(Calendar.HOUR_OF_DAY) * 60 + slotCal.get(Calendar.MINUTE)
                val energyProfile = EnergyProfile.from(energyScore, confidence, 0.0f, slotHourKey, hysteresisState)

                // Capacity is time-based only — each slot is a 30-minute scheduling block.
                // Energy zone affects utilization limits (in planAutoSchedule), not available time.
                val availableCapacityMinutes = SCHEDULING_BLOCK_MINUTES

                val slot = TimeSlot(
                    startMillis = slotStartMillis,
                    endMillis = slotEndMillis,
                    dayIndex = dayIdx,
                    hourOfDay = dayCal.get(Calendar.HOUR_OF_DAY),
                    availableCapacityMinutes = availableCapacityMinutes,
                    availableEnergy = energyScore,
                    energyProfile = energyProfile
                )

                slots.add(slot)
                totalMinutes += availableCapacityMinutes
                totalEnergy += energyScore
                slotCount++

                // Calendar.add() preserves correct behavior across DST transitions.
                slotCal.add(Calendar.MINUTE, SCHEDULING_BLOCK_MINUTES)
            }
        }

        return CapacityHorizon(
            slots = slots,
            horizonStartMillis = slots.firstOrNull()?.startMillis ?: nowMillis,
            horizonEndMillis = slots.lastOrNull()?.endMillis ?: nowMillis,
            totalAvailableMinutes = totalMinutes,
            averageEnergyLevel = if (slotCount > 0) totalEnergy / slotCount else 0
        )
    }

    private fun scoreTaskSlotFit(
        task: TaskEntity,
        slot: TimeSlot,
        slotIndex: Int,
        tagProfiles: List<TagSchedulingProfile>,
        deadlinePressure: DeadlinePressure,
        prefs: UserPreferences,
        nowMillis: Long,
        energyPenalty: Float = 0f,
        assignedCategoryBySlot: Map<Int, TaskCategory> = emptyMap()
    ): TaskSlotFitScore {
        var score = 0.0f

        // W3 fix: Apply energy demand soft penalty from energyDemandFitPenalty()
        score += energyPenalty

        // 1. Energy match: task energy demand vs slot energy availability (adjusted weight: 0.13)
        val taskEnergyDemand = when {
            task.effortScore in 80..100 -> 0.8f
            task.effortScore in 60..79 -> 0.6f
            task.effortScore in 40..59 -> 0.4f
            else -> 0.2f
        }

        val slotEnergySupply = when (slot.energyProfile.zone) {
            EnergyZone.PEAK -> 1.0f
            EnergyZone.HIGH -> 0.8f
            EnergyZone.MODERATE -> 0.6f
            EnergyZone.LOW -> 0.3f
            EnergyZone.CRITICAL -> 0.0f
        }

        val energyMatch = if (taskEnergyDemand > 0.0f) {
            min(1.0f, slotEnergySupply / taskEnergyDemand)
        } else {
            1.0f
        }
        score += energyMatch * 0.13f

        val confidenceReliability = slot.energyProfile.confidence.coerceIn(0.2f, 1.0f)
        score += confidenceReliability * 0.02f

        // 2. Tag-based fit: profile window + context alignment (adjusted weight: 0.07)
        var tagFit = 0.5f
        if (tagProfiles.isNotEmpty()) {
            val avgTagSuitability = tagProfiles.map { profile ->
                val windowMatch = when (profile.preferredWindow) {
                    TagPreferredWindow.MORNING -> if (slot.hourOfDay in 6..11) 1.0f else 0.5f
                    TagPreferredWindow.MIDDAY -> if (slot.hourOfDay in 11..14) 1.0f else 0.6f
                    TagPreferredWindow.EVENING -> if (slot.hourOfDay in 17..21) 1.0f else 0.4f
                    TagPreferredWindow.FLEXIBLE -> 0.8f
                }

                val energyDemandMatch = when (profile.energyDemand) {
                    TagEnergyDemand.HIGH -> when (slot.energyProfile.zone) {
                        EnergyZone.PEAK, EnergyZone.HIGH -> 1.0f
                        EnergyZone.MODERATE -> 0.65f
                        EnergyZone.LOW -> 0.35f
                        EnergyZone.CRITICAL -> 0.15f
                    }
                    TagEnergyDemand.MEDIUM -> when (slot.energyProfile.zone) {
                        EnergyZone.CRITICAL -> 0.25f
                        EnergyZone.LOW -> 0.65f
                        else -> 0.95f
                    }
                    TagEnergyDemand.LOW -> when (slot.energyProfile.zone) {
                        EnergyZone.CRITICAL -> 0.55f
                        else -> 1.0f
                    }
                }

                val contextMatch = when {
                    profile.preferredContext.isNullOrBlank() -> 0.85f
                    task.contextTag.isBlank() -> 0.6f
                    profile.preferredContext.equals(task.contextTag, ignoreCase = true) -> 1.0f
                    else -> 0.35f
                }

                (windowMatch * 0.45f) + (energyDemandMatch * 0.35f) + (contextMatch * 0.20f)
            }.average()

            val avgWindowMatch = tagProfiles.map { profile ->
                when (profile.preferredWindow) {
                    TagPreferredWindow.MORNING -> if (slot.hourOfDay in 6..11) 1.0f else 0.5f
                    TagPreferredWindow.MIDDAY -> if (slot.hourOfDay in 11..14) 1.0f else 0.6f
                    TagPreferredWindow.EVENING -> if (slot.hourOfDay in 17..21) 1.0f else 0.4f
                    TagPreferredWindow.FLEXIBLE -> 0.8f
                }
            }.average()
            val avgFragmentation = tagProfiles.map { it.fragmentationTolerance }.average()
            tagFit = (avgTagSuitability.toFloat() * 0.75f) + ((avgWindowMatch.toFloat() * avgFragmentation.toFloat()) * 0.25f)
        }
        score += tagFit * 0.04f

        // 2.5. Smart Category alignment: core behavior category fit (weight: 0.18)
        val categoryFit = calculateCategoryFit(task, slot, prefs)
        score += categoryFit * 0.18f

        // 3. Deadline urgency: tasks with near deadlines get higher score (adjusted weight: 0.10)
        var deadlineUrgency = 0.0f
        if (task.deadlineDate != null) {
            val deadlineMillis = task.deadlineDate + (task.deadlineTime ?: 0L)
            val daysUntilDeadline = (deadlineMillis - slot.startMillis) / (24 * 60 * 60 * 1000L)
            deadlineUrgency = when {
                daysUntilDeadline <= 1L -> 1.0f
                daysUntilDeadline <= 3L -> 0.75f
                daysUntilDeadline <= 7L -> 0.45f
                else -> 0.25f
            }
        }
        score += deadlineUrgency * 0.10f

        // 4. Deadline placement / spacing: account for pacing density and prevent procrastination (adjusted weight: 0.08)
        val deadlinePlacement = calculateDeadlinePlacementScore(slot.dayIndex, deadlinePressure)
        score += deadlinePlacement * 0.08f

        // 5. Priority / value pressure: higher-impact and explicitly high-priority work should land sooner (adjusted weight: 0.08)
        val priorityPressure = (priorityLevelScore(task) * 0.6f + impactPriorityBlend(task) * 0.4f).coerceIn(0f, 1f)
        score += priorityPressure * 0.08f

        // 6. Temporal proximity: all else equal, choose the nearest viable free slot (adjusted weight: 0.08)
        val proximity = calculateProximityScore(slot.startMillis, nowMillis)
        score += proximity * 0.08f

        // 6.5 Priority timing fit: keep high-priority work from drifting too far out (adjusted weight: 0.06)
        val priorityTimingFit = calculatePriorityTimingFit(task, slot.startMillis, nowMillis)
        score += priorityTimingFit * 0.06f

        // 7. Circadian + task-type fit (adjusted weight: 0.05)
        val chronoFit = calculateChronotypeTaskFit(task, slot, prefs, tagProfiles)
        score += chronoFit * 0.05f

        // 8.5. Respect preferred wake/work windows to reduce burnout from off-hours work (adjusted weight: 0.01)
        val wakeWorkFit = calculateWakeWorkAlignment(slot, prefs)
        score += wakeWorkFit * 0.01f

        // 8. Sleep pressure realism: heavy cognitive tasks are less effective late with high pressure (adjusted weight: 0.03)
        val sleepPressureFit = calculateSleepPressureFit(task, slot, prefs)
        score += sleepPressureFit * 0.03f

        // 9. Duration fit: avoid squeezing long work into fragile windows (adjusted weight: 0.03)
        val durationFit = calculateDurationFit(task, slot)
        score += durationFit * 0.03f

        // 10. Fragmentation fit (adjusted weight: 0.01)
        val fragmentationTolerance = if (tagProfiles.isNotEmpty()) {
            tagProfiles.map { it.fragmentationTolerance }.average().toFloat()
        } else {
            0.5f
        }
        score += fragmentationTolerance * 0.01f

        // 11. Hard tasks should prefer true peak windows (+0.05 bonus)
        if (task.effortScore >= 80 && slot.energyProfile.zone == EnergyZone.PEAK) {
            score += 0.05f
        }

        // 6. Micro-adjustments (+0.05 bonus)
        if (slot.dayIndex == 0 && slot.hourOfDay in 9..11) {
            score += 0.05f
        }

        score += categoryCohesionBonus(task, slotIndex, tagProfiles, assignedCategoryBySlot)

        return TaskSlotFitScore(
            taskId = task.id,
            slotIndex = slotIndex,
            overallScore = score.coerceIn(0.0f, 1.0f),
            energyMatch = energyMatch,
            tagFit = tagFit,
            deadlineUrgency = deadlineUrgency,
            fragmentationTolerance = fragmentationTolerance
        )
    }

    private fun categoryCohesionBonus(
        task: TaskEntity,
        slotIndex: Int,
        tagProfiles: List<TagSchedulingProfile>,
        assignedCategoryBySlot: Map<Int, TaskCategory>
    ): Float {
        if (assignedCategoryBySlot.isEmpty()) return 0f
        val taskCategory = task.determineCategory()
        val adjacentSameCategory = listOf(slotIndex - 1, slotIndex + 1).count { adjIdx ->
            assignedCategoryBySlot[adjIdx] == taskCategory
        }
        if (adjacentSameCategory == 0) return 0f
        val fragTolerance = if (tagProfiles.isNotEmpty()) {
            tagProfiles.map { it.fragmentationTolerance }.average().toFloat()
        } else {
            0.5f
        }
        return adjacentSameCategory * 0.04f * (1f - fragTolerance)
    }

    private fun calculateCategoryFit(
        task: TaskEntity,
        slot: TimeSlot,
        prefs: UserPreferences
    ): Float {
        val category = task.determineCategory()
        val hour = slot.hourOfDay

        val peakStart = if (prefs.effectivePeakStart >= 0) prefs.effectivePeakStart else prefs.peakEnergyStart
        val peakEnd = if (prefs.effectivePeakEnd >= 0) prefs.effectivePeakEnd else prefs.peakEnergyEnd
        val inPeakWindow = isHourInWindow(hour, peakStart, peakEnd)

        val (chronotypeStart, chronotypeEnd) = resolveChronotypePeakWindow(prefs)
        val inChronotypeWindow = isHourInWindow(hour, chronotypeStart, chronotypeEnd)
        val isPeakHour = inPeakWindow || inChronotypeWindow

        // Chronotype-aware morning anchor: "morning" means shortly after the user wakes,
        // not a hardcoded clock hour. A night owl waking at 10 has their morning at 10–13.
        val wakeHour = prefs.wakeUpHour.coerceIn(0, 23)
        val morningWindowStart = wakeHour
        val morningWindowEnd = (wakeHour + 3).coerceAtMost(23)   // first 3h after wake
        val isMorningWindow = isHourInWindow(hour, morningWindowStart, morningWindowEnd)

        // Late-afternoon window: 2–5h before the user's chronotype peak ends (body temp peak)
        // For most people this is 16–19, but shifts for evening types.
        val afternoonWindowStart = (chronotypeEnd - 5).coerceAtLeast(12)
        val afternoonWindowEnd = (chronotypeEnd - 1).coerceAtLeast(afternoonWindowStart + 1)
        val isAfternoonWindow = isHourInWindow(hour, afternoonWindowStart, afternoonWindowEnd)

        // Wind-down window: 1–2h before sleep
        val sleepHour = prefs.sleepHour.coerceIn(18, 27) // allow past midnight
        val windDownStart = (sleepHour - 2).coerceIn(18, 23)
        val isWindDown = hour >= windDownStart

        return when (category) {
            TaskCategory.MINDFULNESS -> {
                // Mindfulness: right after waking (calm, pre-peak) or wind-down before sleep.
                // Strong penalty during cognitive peak — that time is too valuable for deep work.
                when {
                    isMorningWindow -> 1.0f
                    isWindDown -> 0.9f
                    isPeakHour -> 0.1f  // Don't waste peak on low-demand mindfulness
                    else -> 0.55f
                }
            }
            TaskCategory.EXERCISE -> {
                // Exercise: morning (cortisol peak, energy priming) or late afternoon
                // (body temperature peak → best physical performance).
                // Avoid scheduling during cognitive peak — wastes it on non-cognitive work.
                when {
                    isMorningWindow -> 1.0f
                    isAfternoonWindow -> 0.95f
                    isPeakHour -> 0.25f  // Cognitive peak is better used for hard work
                    hour in morningWindowEnd..afternoonWindowStart -> 0.6f
                    else -> 0.3f
                }
            }
            TaskCategory.PHYSICAL -> {
                // Physical: chores, errands, manual tasks. Flexible but avoid burning
                // cognitive peak on low-cognitive-demand work.
                val inCognitivePeakZone = slot.energyProfile.zone == EnergyZone.PEAK
                when {
                    inCognitivePeakZone -> 0.2f  // Save peak for hard/analytical work
                    hour in (wakeHour + 1)..(sleepHour.coerceAtMost(22) - 1) -> 0.9f
                    else -> 0.55f
                }
            }
            TaskCategory.ANALYTICAL, TaskCategory.HARD_WORK -> {
                // Analytical & Hard Work: must land in peak cognitive windows.
                // Both the user-configured peak AND their chronotype window are considered.
                val inPeakZone = slot.energyProfile.zone == EnergyZone.PEAK
                val inHighZone = slot.energyProfile.zone == EnergyZone.HIGH

                var baseFit = when {
                    inPeakWindow && inChronotypeWindow -> 0.85f
                    inPeakWindow || inChronotypeWindow -> 0.70f
                    else -> 0.40f
                }
                if (inPeakZone) baseFit += 0.15f
                else if (inHighZone) baseFit += 0.05f
                else if (slot.energyProfile.zone == EnergyZone.LOW ||
                         slot.energyProfile.zone == EnergyZone.CRITICAL) {
                    baseFit -= 0.35f
                }
                baseFit.coerceIn(0.0f, 1.0f)
            }
            TaskCategory.CREATIVE -> {
                // Creative: best mid-morning (post-peak warm-up) or late afternoon/evening.
                // Chronotype-aware: use the hour just after the chronotype peak ends.
                val postPeakStart = chronotypeEnd
                val postPeakEnd = (chronotypeEnd + 3).coerceAtMost(23)
                val isPostPeak = isHourInWindow(hour, postPeakStart, postPeakEnd)
                when {
                    isPostPeak -> 1.0f
                    isAfternoonWindow -> 0.95f
                    isPeakHour -> 0.75f  // Acceptable but not ideal — peak is better for hard work
                    else -> 0.55f
                }
            }
            TaskCategory.ROUTINE -> {
                // Routine/Admin: low cognitive demand. Best in energy valleys so peaks
                // are preserved for hard/analytical work.
                val inValleyZone = slot.energyProfile.zone in listOf(EnergyZone.LOW, EnergyZone.CRITICAL)
                // Post-lunch dip is relative to wake time: roughly 7h after waking
                val dipStart = (wakeHour + 6).coerceIn(12, 15)
                val dipEnd = (dipStart + 2).coerceAtMost(17)
                val inPostLunchDip = isHourInWindow(hour, dipStart, dipEnd)
                when {
                    inValleyZone || inPostLunchDip -> 1.0f
                    isPeakHour -> 0.3f  // Save peaks for hard/analytical work
                    else -> 0.75f
                }
            }
            TaskCategory.FLEXIBLE -> 0.75f
        }
    }

    private fun calculateDeadlinePlacementScore(dayIndex: Int, deadlinePressure: DeadlinePressure): Float {
        return when (deadlinePressure.pressureLevel) {
            PressureLevel.URGENT -> when (dayIndex) {
                0 -> 1.0f
                1 -> 0.5f
                else -> 0.25f
            }
            PressureLevel.HIGH -> when (dayIndex) {
                0 -> 1.0f
                1 -> 0.82f
                2 -> 0.65f
                else -> 0.45f
            }
            PressureLevel.MODERATE -> when (dayIndex) {
                0 -> 1.0f
                1 -> 0.86f
                2 -> 0.72f
                else -> 0.55f
            }
            PressureLevel.LOW -> when (dayIndex) {
                0 -> 1.0f
                1 -> 0.8f
                2 -> 0.62f
                else -> 0.45f
            }
        }
    }

    private fun priorityLevelScore(task: TaskEntity): Float {
        return when (task.priority) {
            com.neuroflow.app.domain.model.Priority.HIGH -> 1.0f
            com.neuroflow.app.domain.model.Priority.MEDIUM -> 0.65f
            com.neuroflow.app.domain.model.Priority.LOW -> 0.35f
        }
    }

    private fun impactPriorityBlend(task: TaskEntity): Float {
        val impactBlend = ((task.impactScore + task.valueScore) / 200f).coerceIn(0f, 1f)
        return ((impactBlend * 0.7f) + (priorityLevelScore(task) * 0.3f)).coerceIn(0f, 1f)
    }

    private fun calculateProximityScore(slotStartMillis: Long, nowMillis: Long): Float {
        val hoursAway = ((slotStartMillis - nowMillis).coerceAtLeast(0L) / 3_600_000f)
        return when {
            hoursAway <= 2f -> 1.0f
            hoursAway <= 6f -> 0.85f
            hoursAway <= 12f -> 0.68f
            hoursAway <= 24f -> 0.48f
            else -> 0.25f
        }
    }

    private fun calculatePriorityTimingFit(task: TaskEntity, slotStartMillis: Long, nowMillis: Long): Float {
        val hoursAway = ((slotStartMillis - nowMillis).coerceAtLeast(0L) / 3_600_000f)
        return when (task.priority) {
            com.neuroflow.app.domain.model.Priority.HIGH -> when {
                hoursAway <= 2f -> 1.0f
                hoursAway <= 6f -> 0.96f
                hoursAway <= 12f -> 0.85f
                hoursAway <= 24f -> 0.7f
                hoursAway <= 36f -> 0.45f
                else -> 0.2f
            }
            com.neuroflow.app.domain.model.Priority.MEDIUM -> when {
                hoursAway <= 4f -> 1.0f
                hoursAway <= 12f -> 0.88f
                hoursAway <= 24f -> 0.72f
                hoursAway <= 48f -> 0.52f
                else -> 0.3f
            }
            com.neuroflow.app.domain.model.Priority.LOW -> when {
                hoursAway <= 6f -> 0.9f
                hoursAway <= 24f -> 0.85f
                hoursAway <= 48f -> 0.75f
                else -> 0.62f
            }
        }
    }

    private fun resolvePreferredLatestStartMillis(
        task: TaskEntity,
        nowMillis: Long,
        deadlineMillis: Long?,
        estimatedDurationMinutes: Int
    ): Long {
        val priorityWindowMillis = when (task.priority) {
            com.neuroflow.app.domain.model.Priority.HIGH -> 24L * 60L * 60L * 1000L
            com.neuroflow.app.domain.model.Priority.MEDIUM -> 36L * 60L * 60L * 1000L
            com.neuroflow.app.domain.model.Priority.LOW -> 60L * 60L * 60L * 1000L
        }

        val preferred = nowMillis + priorityWindowMillis
        if (deadlineMillis == null) {
            return preferred
        }

        val safetyBufferMinutes = calculateDeadlineSafetyBufferMinutes(
            task = task,
            nowMillis = nowMillis,
            deadlineMillis = deadlineMillis,
            estimatedDurationMinutes = estimatedDurationMinutes
        )

        val deadlineBufferedLatestStart = deadlineMillis - (safetyBufferMinutes * 60_000L)
        return min(preferred, deadlineBufferedLatestStart)
    }

    private fun calculateDeadlineSafetyBufferMinutes(
        task: TaskEntity,
        nowMillis: Long,
        deadlineMillis: Long,
        estimatedDurationMinutes: Int
    ): Int {
        val minutesUntilDeadline = ((deadlineMillis - nowMillis) / 60_000L).toInt().coerceAtLeast(0)

        // Base buffer proportional to task duration (not fixed)
        // Longer tasks need more buffer for unexpected issues
        val durationBasedBuffer = (estimatedDurationMinutes * 0.5f).roundToInt()

        // Use additive factors instead of multiplicative to avoid excessive compounding
        var bufferAdjustment = 0

        // Priority adjustment: HIGH priority gets +20 min, MEDIUM +10 min, LOW +0 min
        bufferAdjustment += when (task.priority) {
            com.neuroflow.app.domain.model.Priority.HIGH -> 20
            com.neuroflow.app.domain.model.Priority.MEDIUM -> 10
            com.neuroflow.app.domain.model.Priority.LOW -> 0
        }

        // Effort adjustment: High-effort tasks get +15 min, medium +10 min
        bufferAdjustment += when {
            task.effortScore >= 80 -> 15
            task.effortScore >= 60 -> 10
            else -> 0
        }

        // Historical error adjustment: Add up to +30 min based on past estimation errors
        if (task.estimationErrorMape != null && task.estimationErrorMape > 0f) {
            val errorMinutes = (task.estimationErrorMape / 100f * estimatedDurationMinutes)
                .roundToInt()
                .coerceAtMost(30)
            bufferAdjustment += errorMinutes
        }

        val targetBuffer = (durationBasedBuffer + bufferAdjustment)
            .coerceIn(30, 120)  // Min 30 min, max 2 hours (reduced from 3)

        // Minimum buffer: at least 25% of task duration
        val minimumBuffer = (estimatedDurationMinutes * 0.25f).roundToInt().coerceAtLeast(15)

        return when {
            minutesUntilDeadline <= minimumBuffer -> (minutesUntilDeadline / 2).coerceAtLeast(15)
            minutesUntilDeadline <= targetBuffer ->
                max(minimumBuffer, (minutesUntilDeadline * 0.4f).roundToInt())
            else -> targetBuffer
        }
    }

    private fun calculateDeadlineSafetyBufferFit(
        task: TaskEntity,
        slotStartMillis: Long,
        deadlineMillis: Long,
        nowMillis: Long
    ): Float {
        val minutesBeforeDeadline = ((deadlineMillis - slotStartMillis) / 60_000L).toInt()
        if (minutesBeforeDeadline <= 0) return 0.0f

        val targetBuffer = calculateDeadlineSafetyBufferMinutes(
            task = task,
            nowMillis = nowMillis,
            deadlineMillis = deadlineMillis,
            estimatedDurationMinutes = if (task.estimatedDurationMinutes > 0) task.estimatedDurationMinutes else 45
        )

        return when {
            minutesBeforeDeadline < 30 -> 0.2f
            minutesBeforeDeadline < targetBuffer / 2 -> 0.45f
            minutesBeforeDeadline <= targetBuffer + 60 -> 1.0f
            minutesBeforeDeadline <= targetBuffer + 240 -> 0.82f
            else -> 0.65f
        }
    }

    private data class BreakPolicy(
        val intervalMinutes: Int,
        val durationMinutes: Int
    )

    private fun resolveBreakPolicy(prefs: UserPreferences): BreakPolicy {
        val baseInterval = prefs.autoSchedulingBreakAfterCognitiveMinutes.coerceIn(30, 180)
        val baseDuration = prefs.autoSchedulingBreakDurationMinutes.coerceIn(5, 30)

        // Convert raw pressure points (0..14400) to a 0..100 fatigue percentage before
        // comparing against thresholds. Directly comparing raw points against percentage
        // thresholds (e.g. >= 75) would only ever trigger for < 2 minutes of wake time.
        val fatigue = com.neuroflow.app.domain.engine.SleepPressureDetector.fatiguePercent(
            prefs.sleepPressurePoints
        )

        return when {
            fatigue >= 75 -> BreakPolicy(
                intervalMinutes = min(baseInterval, 75),
                durationMinutes = max(baseDuration, 20)
            )
            fatigue >= 60 -> BreakPolicy(
                intervalMinutes = min(baseInterval, 90),
                durationMinutes = max(baseDuration, 15)
            )
            fatigue <= 30 -> BreakPolicy(
                intervalMinutes = max(baseInterval, 120),
                durationMinutes = max(baseDuration, 10)
            )
            else -> BreakPolicy(baseInterval, baseDuration)
        }
    }

    private fun calculateChronotypeTaskFit(
        task: TaskEntity,
        slot: TimeSlot,
        prefs: UserPreferences,
        tagProfiles: List<TagSchedulingProfile>
    ): Float {
        val peakStart = if (prefs.effectivePeakStart >= 0) prefs.effectivePeakStart else prefs.peakEnergyStart
        val peakEnd = if (prefs.effectivePeakEnd >= 0) prefs.effectivePeakEnd else prefs.peakEnergyEnd
        val inPeakWindow = isHourInWindow(slot.hourOfDay, peakStart, peakEnd)

        val (chronotypeStart, chronotypeEnd) = resolveChronotypePeakWindow(prefs)
        val inChronotypeWindow = isHourInWindow(slot.hourOfDay, chronotypeStart, chronotypeEnd)

        val typeFit = when (task.taskType) {
            com.neuroflow.app.domain.model.TaskType.ANALYTICAL -> when {
                inPeakWindow && inChronotypeWindow -> 1.0f
                inPeakWindow -> 0.85f
                inChronotypeWindow -> 0.8f
                else -> 0.45f
            }
            com.neuroflow.app.domain.model.TaskType.CREATIVE -> when {
                slot.hourOfDay in 10..16 -> 1.0f
                inChronotypeWindow -> 0.85f
                else -> 0.65f
            }
            com.neuroflow.app.domain.model.TaskType.ADMIN -> if (slot.hourOfDay in 10..18) 1.0f else 0.7f
            com.neuroflow.app.domain.model.TaskType.PHYSICAL -> when {
                slot.energyProfile.zone == EnergyZone.PEAK -> 0.72f
                slot.hourOfDay in 7..20 && slot.energyProfile.zone in listOf(EnergyZone.HIGH, EnergyZone.MODERATE) -> 1.0f
                slot.hourOfDay in 7..20 -> 0.82f
                else -> 0.6f
            }
        }

        val explicitEnergyFit = when (task.energyLevel) {
            com.neuroflow.app.domain.model.EnergyLevel.HIGH -> if (slot.energyProfile.zone in listOf(EnergyZone.PEAK, EnergyZone.HIGH)) 1.0f else 0.4f
            com.neuroflow.app.domain.model.EnergyLevel.MEDIUM -> if (slot.energyProfile.zone == EnergyZone.CRITICAL) 0.4f else 0.9f
            com.neuroflow.app.domain.model.EnergyLevel.LOW -> if (slot.energyProfile.zone == EnergyZone.CRITICAL) 0.6f else 1.0f
        }

        val tagWindowFit = if (tagProfiles.isEmpty()) 0.85f else {
            tagProfiles.map { profile ->
                when (profile.preferredWindow) {
                    TagPreferredWindow.MORNING -> if (slot.hourOfDay in 6..11) 1.0f else 0.55f
                    TagPreferredWindow.MIDDAY -> if (slot.hourOfDay in 11..14) 1.0f else 0.65f
                    TagPreferredWindow.EVENING -> if (slot.hourOfDay in 17..21) 1.0f else 0.5f
                    TagPreferredWindow.FLEXIBLE -> 0.9f
                }
            }.average().toFloat()
        }

        return ((typeFit * 0.45f) + (explicitEnergyFit * 0.35f) + (tagWindowFit * 0.2f)).coerceIn(0f, 1f)
    }

    private fun calculateWakeWorkAlignment(slot: TimeSlot, prefs: UserPreferences): Float {
        val wakeBufferedStart = (prefs.wakeUpHour + 1).coerceIn(0, 23)
        val sleepGuardEnd = (prefs.sleepHour - 1).coerceIn(0, 23)
        val workStart = prefs.workDayStart.coerceIn(0, 23)
        val workEndInclusive = (prefs.workDayEnd - 1).coerceIn(0, 23)

        val withinWakeWindow = isHourInWindow(slot.hourOfDay, wakeBufferedStart, sleepGuardEnd + 1)
        val withinWorkWindow = isHourInWindow(slot.hourOfDay, workStart, workEndInclusive + 1)

        return when {
            withinWakeWindow && withinWorkWindow -> 1.0f
            withinWakeWindow -> 0.75f
            else -> 0.35f
        }
    }

    private fun resolveChronotypePeakWindow(prefs: UserPreferences): Pair<Int, Int> {
        val mapped = when (prefs.quizChronotype ?: prefs.manualChronotype) {
            "DEFINITE_MORNING" -> 6 to 11
            "MODERATE_MORNING" -> 7 to 12
            "INTERMEDIATE" -> 9 to 14
            "MODERATE_EVENING" -> 13 to 18
            "DEFINITE_EVENING" -> 15 to 21
            else -> null
        }

        val (start, end) = mapped
            ?: if (prefs.effectivePeakStart >= 0 && prefs.effectivePeakEnd >= 0) {
                prefs.effectivePeakStart to prefs.effectivePeakEnd
            } else {
                prefs.peakEnergyStart to prefs.peakEnergyEnd
            }

        val safeStart = start.coerceIn(0, 23)
        val safeEnd = end.coerceIn(1, 24)
        return if (safeEnd <= safeStart) {
            safeStart to (safeStart + 4).coerceAtMost(24)
        } else {
            safeStart to safeEnd
        }
    }

    private fun isHourInWindow(hour: Int, startInclusive: Int, endExclusive: Int): Boolean {
        val normalizedHour = hour.coerceIn(0, 23)
        val start = startInclusive.coerceIn(0, 23)
        val end = endExclusive.coerceIn(0, 24)
        if (start == end) return true
        return if (start < end) {
            normalizedHour in start until end
        } else {
            normalizedHour >= start || normalizedHour < end
        }
    }

    private fun resolveDailyPlanningWindow(prefs: UserPreferences): Pair<Int, Int> {
        val wakeBufferedStart = (prefs.wakeUpHour + 1).coerceIn(0, 23)
        val workStart = prefs.workDayStart.coerceIn(0, 23)
        val workEndInclusive = (prefs.workDayEnd - 1).coerceIn(0, 23)
        val sleepGuardEnd = (prefs.sleepHour - 1).coerceIn(0, 23)

        var start = max(workStart, wakeBufferedStart)
        var end = min(workEndInclusive, sleepGuardEnd)

        if (end < start || (end - start) < 4) {
            start = max(6, wakeBufferedStart)
            end = min(22, max(start + 4, sleepGuardEnd))
            if (end < start) {
                start = 7
                end = 21
            }
        }

        return start.coerceIn(0, 23) to end.coerceIn(start.coerceIn(0, 23), 23)
    }

    private fun canAssignWithoutBurnout(
        task: TaskEntity,
        slot: TimeSlot,
        durationMinutes: Int,
        nowMillis: Long,
        prefs: UserPreferences,
        highCognitiveMinutesByDay: MutableMap<Int, Int>,
        highCognitiveHoursByDay: MutableMap<Int, MutableSet<Int>>,
        blockedSlotIndices: Set<Int>,
        slots: List<TimeSlot>
    ): Boolean {
        if (!isCognitivelyIntense(task)) return true

        val deadlineMillis = task.deadlineDate?.plus(task.deadlineTime ?: 0L)
        val urgentDeadline = deadlineMillis != null && (deadlineMillis - nowMillis) <= (24 * 60 * 60 * 1000L)

        val baseBudget = calculateDailyCognitiveBudgetMinutes(prefs)
        val budget = if (urgentDeadline) (baseBudget * 1.2f).roundToInt() else baseBudget
        val used = highCognitiveMinutesByDay[slot.dayIndex] ?: 0

        // Long-task exception: if the task itself exceeds the daily budget, allow it through
        // as long as no other cognitive work has been assigned yet that day. Without this,
        // tasks longer than the budget (e.g. a 6-hour deep-work block) can never be scheduled.
        val isLongTask = durationMinutes > budget
        if (isLongTask) {
            return used == 0  // Only allow if the day is still cognitively empty
        }

        if (used + durationMinutes > budget) {
            return false
        }

        // Check adjacency: look backward in already-assigned hours AND forward in remaining slots
        val sameDayHighHours = highCognitiveHoursByDay[slot.dayIndex].orEmpty()
        val hasAdjacentBefore = (slot.hourOfDay - 1 in sameDayHighHours)

        val hasAdjacentAfter = slot.hourOfDay + 1 in sameDayHighHours
        val adjacentHeavy = hasAdjacentBefore || hasAdjacentAfter
        if (!urgentDeadline && adjacentHeavy) {
            return false
        }

        val nearBedtime = slot.hourOfDay >= (prefs.sleepHour - 2).coerceAtLeast(18)
        // Convert raw pressure points to a 0..100 fatigue percentage before thresholding.
        val fatigue = com.neuroflow.app.domain.engine.SleepPressureDetector.fatiguePercent(
            prefs.sleepPressurePoints
        )
        if (!urgentDeadline && fatigue >= 70 && nearBedtime) {
            return false
        }

        return true
    }

    private fun trackCognitiveLoad(
        task: TaskEntity,
        slot: TimeSlot,
        durationMinutes: Int,
        highCognitiveMinutesByDay: MutableMap<Int, Int>,
        highCognitiveHoursByDay: MutableMap<Int, MutableSet<Int>>
    ) {
        if (!isCognitivelyIntense(task)) return
        highCognitiveMinutesByDay[slot.dayIndex] =
            (highCognitiveMinutesByDay[slot.dayIndex] ?: 0) + durationMinutes
        highCognitiveHoursByDay.getOrPut(slot.dayIndex) { mutableSetOf() }.add(slot.hourOfDay)
    }

    private fun isCognitivelyIntense(task: TaskEntity): Boolean {
        return task.effortScore >= 70 || task.taskType in listOf(
            com.neuroflow.app.domain.model.TaskType.ANALYTICAL,
            com.neuroflow.app.domain.model.TaskType.CREATIVE
        )
    }

    private fun calculateDailyCognitiveBudgetMinutes(prefs: UserPreferences): Int {
        // Convert raw pressure points to a 0..100 fatigue percentage before thresholding.
        val fatigue = com.neuroflow.app.domain.engine.SleepPressureDetector.fatiguePercent(
            prefs.sleepPressurePoints
        )
        val base = when {
            fatigue >= 80 -> 75
            fatigue >= 65 -> 100
            fatigue >= 50 -> 130
            else -> 170
        }

        val awakeHours = ((prefs.sleepHour - prefs.wakeUpHour + 24) % 24).coerceAtLeast(1)
        val awakeAdjustment = when {
            awakeHours <= 13 -> 0.75f
            awakeHours <= 15 -> 0.9f
            awakeHours >= 18 -> 1.1f
            else -> 1.0f
        }

        return (base * awakeAdjustment).roundToInt().coerceAtLeast(60)
    }

    private fun calculateSleepPressureFit(task: TaskEntity, slot: TimeSlot, prefs: UserPreferences): Float {
        // Convert raw pressure points to a 0..100 fatigue percentage before thresholding.
        val pressure = com.neuroflow.app.domain.engine.SleepPressureDetector.fatiguePercent(
            prefs.sleepPressurePoints
        ).coerceIn(0, 100)
        val lateEvening = slot.hourOfDay >= (prefs.sleepHour - 2).coerceAtLeast(18)
        if (!lateEvening || pressure < 40) return 1.0f

        val cognitivelyHeavy = task.effortScore >= 70 || task.taskType == com.neuroflow.app.domain.model.TaskType.ANALYTICAL
        val penalty = when {
            pressure >= 80 && cognitivelyHeavy -> 0.35f
            pressure >= 65 && cognitivelyHeavy -> 0.55f
            pressure >= 80 -> 0.65f
            else -> 0.8f
        }
        return penalty
    }

    private fun calculateDurationFit(task: TaskEntity, slot: TimeSlot): Float {
        val estimate = if (task.estimatedDurationMinutes > 0) task.estimatedDurationMinutes else 30
        val capacity = slot.availableCapacityMinutes.coerceAtLeast(1)
        val ratio = estimate.toFloat() / capacity.toFloat()
        return when {
            ratio <= 0.75f -> 1.0f
            ratio <= 1.0f -> 0.8f
            ratio <= 1.35f -> 0.55f
            else -> 0.3f
        }
    }
    private fun buildAssignmentReason(fitScore: TaskSlotFitScore): String {
        return when {
            fitScore.deadlineUrgency > 0.8f -> "deadline urgency (${(fitScore.deadlineUrgency * 100).roundToInt()}%)"
            fitScore.energyMatch > 0.8f -> "strong energy-demand match"
            fitScore.tagFit > 0.75f -> "optimal tag-window alignment"
            else -> "composite fit score: ${(fitScore.overallScore * 100).roundToInt()}%"
        }
    }

    /**
     * Build dependency graph for all tasks (performance optimization).
     * Returns map of task ID to set of dependency IDs.
     */
    private fun buildDependencyGraph(tasks: List<TaskEntity>): Map<String, Set<String>> {
        val explicitBefore = tasks.associate { task ->
            task.id to task.doBeforeTaskIds.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
        }
        return tasks.associate { task ->
            val directDependencies = task.dependsOnTaskIds.split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toMutableSet()
            directDependencies += task.doAfterTaskIds.split(",").map { it.trim() }.filter { it.isNotBlank() }
            directDependencies += explicitBefore
                .filter { (_, successors) -> task.id in successors }
                .keys
            task.id to directDependencies
        }
    }

    /**
     * Detect all blocked tasks in one pass (performance optimization).
     * Returns set of task IDs that are blocked by dependencies, external waits, or circular deps.
     */
    private fun detectBlockedTasks(
        dependencyGraph: Map<String, Set<String>>,
        taskMap: Map<String, TaskEntity>
    ): Set<String> {
        val blocked = mutableSetOf<String>()

        // Check each task for blocking conditions
        for ((taskId, task) in taskMap) {
            // Check for external dependency (waitingFor field)
            if (task.waitingFor.isNotBlank()) {
                blocked.add(taskId)
                android.util.Log.i(
                    "AutoSchedulingEngine",
                    "Task $taskId blocked: waiting for external dependency '${task.waitingFor}'"
                )
                continue
            }

            val dependencies = dependencyGraph[taskId] ?: emptySet()

            // Check for self-reference
            if (taskId in dependencies) {
                blocked.add(taskId)
                android.util.Log.w(
                    "AutoSchedulingEngine",
                    "Task $taskId blocked: self-reference detected"
                )
                continue
            }

            // Check if any dependency is incomplete
            var hasIncompleteDependency = false
            for (depId in dependencies) {
                val depTask = taskMap[depId]

                if (depTask == null) {
                    blocked.add(taskId)
                    hasIncompleteDependency = true
                    android.util.Log.w(
                        "AutoSchedulingEngine",
                        "Task $taskId blocked: dependency task '$depId' not found"
                    )
                    break
                }

                if (depTask.status != TaskStatus.COMPLETED) {
                    blocked.add(taskId)
                    hasIncompleteDependency = true
                    android.util.Log.i(
                        "AutoSchedulingEngine",
                        "Task $taskId blocked: dependency task '$depId' not completed (status: ${depTask.status})"
                    )
                    break
                }
            }

            if (hasIncompleteDependency) {
                continue
            }
        }

        // Detect circular dependencies using DFS; block every task in the cycle.
        val visited = mutableSetOf<String>()
        val recursionStack = mutableListOf<String>()

        fun markCycleFrom(taskId: String) {
            val cycleStart = recursionStack.indexOf(taskId)
            if (cycleStart < 0) return
            recursionStack.subList(cycleStart, recursionStack.size).forEach { cycleTaskId ->
                if (blocked.add(cycleTaskId)) {
                    android.util.Log.w(
                        "AutoSchedulingEngine",
                        "Task $cycleTaskId blocked: circular dependency detected"
                    )
                }
            }
        }

        fun hasCycle(taskId: String): Boolean {
            if (taskId in recursionStack) {
                markCycleFrom(taskId)
                return true
            }

            if (taskId in visited) {
                return false
            }

            visited.add(taskId)
            recursionStack.add(taskId)

            val dependencies = dependencyGraph[taskId] ?: emptySet()
            for (depId in dependencies) {
                if (hasCycle(depId)) {
                    return true
                }
            }

            recursionStack.removeAt(recursionStack.lastIndex)
            return false
        }

        for (taskId in taskMap.keys) {
            if (taskId !in visited) {
                hasCycle(taskId)
            }
        }

        return blocked
    }
}
