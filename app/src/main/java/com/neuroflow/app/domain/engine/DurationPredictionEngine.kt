package com.neuroflow.app.domain.engine

import com.neuroflow.app.data.local.entity.TaskEntity
import com.neuroflow.app.domain.model.EnergyLevel
import com.neuroflow.app.domain.model.TaskType
import kotlin.math.roundToInt

/**
 * Conservative, explainable duration prediction. It intentionally uses only local task
 * history and bounded heuristics so a new user receives a useful estimate without remote ML.
 */
object DurationPredictionEngine {
    fun predictMinutes(
        task: TaskEntity,
        availableEnergyScore: Int? = null,
        learnedMultiplier: Float = 1f
    ): Int {
        val fallback = when {
            task.effortScore >= 80 -> 60
            task.effortScore >= 60 -> 45
            else -> 30
        }
        val explicitEstimate = task.estimatedDurationMinutes.takeIf { it > 0 } ?: fallback
        val historyBlend = task.actualDurationMinutes?.takeIf { it > 0f }?.let {
            (explicitEstimate + it) / 2f
        } ?: explicitEstimate.toFloat()
        val errorMultiplier = 1f + maxOf(task.estimationErrorMape ?: 0f, task.estimationErrorSmape ?: 0f)
            .coerceIn(0f, 100f) / 100f
        val typeMultiplier = when (task.taskType) {
            TaskType.ANALYTICAL -> 1.15f
            TaskType.CREATIVE -> 1.10f
            TaskType.ADMIN -> 0.90f
            TaskType.PHYSICAL -> 1.0f
        }
        val tagMultiplier = when {
            task.tags.split(",").any { it.trim().equals("coding", ignoreCase = true) } -> 1.40f
            task.tags.split(",").any { it.trim().equals("admin", ignoreCase = true) } -> 0.80f
            else -> 1.0f
        }
        val energyMultiplier = when {
            availableEnergyScore != null && availableEnergyScore < 35 -> 1.35f
            task.energyLevel == EnergyLevel.LOW -> 1.15f
            else -> 1.0f
        }
        val repeatedMissMultiplier = if (task.postponeCount >= 2) 0.85f else 1.0f
        return (historyBlend * errorMultiplier * typeMultiplier * tagMultiplier * energyMultiplier * repeatedMissMultiplier * learnedMultiplier.coerceIn(0.75f, 1.45f))
            .roundToInt()
            .coerceAtLeast(15)
            .coerceAtMost(360)
    }
}
