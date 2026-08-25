package com.neuroflow.app.domain.scheduler

import com.neuroflow.app.data.local.entity.ScheduleAdjustmentEntity
import com.neuroflow.app.data.local.entity.TaskEntity
import com.neuroflow.app.data.local.entity.TaskFeedbackEntity
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Small on-device correction profile. Every learned value is bounded and derived from
 * recent local events, so one accidental rating cannot dominate future plans.
 */
data class CorrectionProfile(
    val durationMultiplierByTag: Map<String, Float> = emptyMap(),
    val durationMultiplierByTaskType: Map<String, Float> = emptyMap(),
    val preferredHourByTag: Map<String, Int> = emptyMap(),
    val preferredHourByTaskType: Map<String, Int> = emptyMap(),
    val missRiskByTag: Map<String, Float> = emptyMap(),
    val missRiskByTaskType: Map<String, Float> = emptyMap()
) {
    fun durationMultiplier(task: TaskEntity): Float {
        val tagMultipliers = task.tags.split(',')
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .mapNotNull { durationMultiplierByTag[it] }
        val tagMultiplier = tagMultipliers.maxOrNull() ?: 1f
        val typeMultiplier = durationMultiplierByTaskType[task.taskType.name] ?: 1f
        return maxOf(tagMultiplier, typeMultiplier).coerceIn(0.75f, 1.45f)
    }

    fun timeOfDayFit(task: TaskEntity, hourOfDay: Int): Float {
        val tags = task.tags.split(',').map { it.trim().lowercase() }.filter { it.isNotBlank() }
        val preferredTagHours = tags.mapNotNull { preferredHourByTag[it] }
        val preferredHour = preferredTagHours.firstOrNull() ?: preferredHourByTaskType[task.taskType.name]
            ?: return 0f
        return when {
            abs(preferredHour - hourOfDay) == 0 -> 0.10f
            abs(preferredHour - hourOfDay) == 1 -> 0.05f
            else -> -0.03f
        }
    }

    fun missRisk(task: TaskEntity): Float {
        val tags = task.tags.split(',').map { it.trim().lowercase() }.filter { it.isNotBlank() }
        val tagRisk = tags.mapNotNull { missRiskByTag[it] }.maxOrNull() ?: 0f
        val typeRisk = missRiskByTaskType[task.taskType.name] ?: 0f
        return maxOf(tagRisk, typeRisk).coerceIn(0f, 1f)
    }

    companion object {
        fun from(
            tasks: List<TaskEntity>,
            feedback: List<TaskFeedbackEntity>,
            adjustments: List<ScheduleAdjustmentEntity>
        ): CorrectionProfile {
            val taskById = tasks.associateBy { it.id }
            val feedbackByKey = mutableMapOf<String, MutableList<TaskFeedbackEntity>>()
            val missByKey = mutableMapOf<String, Int>()
            val totalByKey = mutableMapOf<String, Int>()
            val hourVotesByKey = mutableMapOf<String, MutableMap<Int, Int>>()

            fun keys(task: TaskEntity): List<String> = buildList {
                task.tags.split(',').map { it.trim().lowercase() }.filter { it.isNotBlank() }.forEach { add("tag:$it") }
                add("type:${task.taskType.name}")
            }

            feedback.forEach { event ->
                val task = taskById[event.taskId] ?: return@forEach
                keys(task).forEach { key ->
                    feedbackByKey.getOrPut(key) { mutableListOf() }.add(event)
                    totalByKey[key] = (totalByKey[key] ?: 0) + 1
                    if (event.kind == "MISSED" || event.kind == "POSTPONED" ||
                        event.value in setOf("bad_time", "too_big", "wrong_priority")
                    ) {
                        missByKey[key] = (missByKey[key] ?: 0) + 1
                    }
                }
            }

            adjustments.filter { !it.undone }.forEach { adjustment ->
                val task = taskById[adjustment.taskId] ?: return@forEach
                val hour = adjustment.newScheduledTime?.let { millis ->
                    ((millis / 3_600_000L) % 24L).toInt()
                } ?: return@forEach
                keys(task).forEach { key ->
                    val votes = hourVotesByKey.getOrPut(key) { mutableMapOf() }
                    votes[hour] = (votes[hour] ?: 0) + 1
                }
            }

            fun keyMap(prefix: String, transform: (List<TaskFeedbackEntity>) -> Float): Map<String, Float> =
                feedbackByKey.mapNotNull { (key, events) ->
                    if (!key.startsWith(prefix)) return@mapNotNull null
                    key.removePrefix(prefix) to transform(events).coerceIn(0.75f, 1.45f)
                }.toMap()

            val durationByKey = keyMap("tag:") { events -> durationMultiplier(events) }
            val typeDuration = keyMap("type:") { events -> durationMultiplier(events) }
            val missByTag = missByKey.mapNotNull { (key, misses) ->
                if (!key.startsWith("tag:")) null else key.removePrefix("tag:") to
                    (misses.toFloat() / (totalByKey[key] ?: 1)).coerceIn(0f, 1f)
            }.toMap()
            val missByType = missByKey.mapNotNull { (key, misses) ->
                if (!key.startsWith("type:")) null else key.removePrefix("type:") to
                    (misses.toFloat() / (totalByKey[key] ?: 1)).coerceIn(0f, 1f)
            }.toMap()

            fun preferredHours(prefix: String): Map<String, Int> = hourVotesByKey.mapNotNull { (key, votes) ->
                if (!key.startsWith(prefix)) null else votes.maxByOrNull { it.value }?.key?.let { hour ->
                    key.removePrefix(prefix) to hour
                }
            }.toMap()

            return CorrectionProfile(
                durationMultiplierByTag = durationByKey,
                durationMultiplierByTaskType = typeDuration,
                preferredHourByTag = preferredHours("tag:"),
                preferredHourByTaskType = preferredHours("type:"),
                missRiskByTag = missByTag,
                missRiskByTaskType = missByType
            )
        }

        private fun durationMultiplier(events: List<TaskFeedbackEntity>): Float {
            val ratings = events.filter { it.kind == "FOCUS_TIME" }
                .mapNotNull { it.value.toFloatOrNull() }
            if (ratings.isEmpty()) return 1f
            val average = ratings.average().toFloat()
            return (1f - average * 0.06f).coerceIn(0.75f, 1.45f)
        }
    }
}
