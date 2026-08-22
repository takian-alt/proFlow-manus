package com.neuroflow.app.domain.scheduler

import com.neuroflow.app.data.local.entity.TaskEntity
import kotlin.math.ceil

object TaskSplitPlanner {
    private const val DEFAULT_CHUNK_MINUTES = 50
    private const val SPLIT_THRESHOLD_MINUTES = 90

    fun shouldSplit(task: TaskEntity): Boolean {
        val duration = task.estimatedDurationMinutes
        val threshold = task.maxSessionLengthMinutes.takeIf { it > 0 } ?: SPLIT_THRESHOLD_MINUTES
        return task.canSplit && duration > threshold
    }

    fun createParts(task: TaskEntity): List<TaskEntity> {
        if (!shouldSplit(task)) return listOf(task)
        val chunkMinutes = task.maxSessionLengthMinutes
            .takeIf { it > 0 }
            ?.coerceIn(15, 120)
            ?: DEFAULT_CHUNK_MINUTES
        val count = ceil(task.estimatedDurationMinutes / chunkMinutes.toDouble()).toInt()
        return (0 until count).map { index ->
            val remaining = task.estimatedDurationMinutes - index * chunkMinutes
            task.copy(
                id = "${task.id}-part-${index + 1}",
                title = "${task.title} · Part ${index + 1}/$count",
                estimatedDurationMinutes = minOf(chunkMinutes, remaining),
                parentTaskId = task.id,
                canSplit = false,
                maxSessionLengthMinutes = 0,
                doBeforeTaskIds = "",
                doAfterTaskIds = ""
            )
        }
    }
}
