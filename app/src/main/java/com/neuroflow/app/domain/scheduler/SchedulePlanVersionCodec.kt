package com.neuroflow.app.domain.scheduler

import com.neuroflow.app.data.local.entity.TaskEntity

/** Compact, delimiter-safe snapshot for local plan history. IDs are UUID-like and do not contain the separators. */
object SchedulePlanVersionCodec {
    data class Entry(val taskId: String, val scheduledDate: Long?, val scheduledTime: Long?)
    data class VersionDiff(
        val added: Set<String>,
        val moved: Set<String>,
        val removed: Set<String>,
        val unchanged: Set<String>
    )

    fun compare(previous: String, current: String): VersionDiff {
        val oldByTask = decode(previous).associateBy { it.taskId }
        val newByTask = decode(current).associateBy { it.taskId }
        val allIds = oldByTask.keys + newByTask.keys
        return VersionDiff(
            added = allIds.filter { it !in oldByTask && it in newByTask }.toSet(),
            moved = allIds.filter {
                oldByTask[it] != null && newByTask[it] != null && oldByTask[it] != newByTask[it]
            }.toSet(),
            removed = allIds.filter { it in oldByTask && it !in newByTask }.toSet(),
            unchanged = allIds.filter {
                oldByTask[it] != null && oldByTask[it] == newByTask[it]
            }.toSet()
        )
    }

    fun encode(tasks: List<TaskEntity>): String = tasks
        .filter { it.scheduledDate != null && it.scheduledTime != null }
        .joinToString("|") { task ->
            "${task.id},${task.scheduledDate},${task.scheduledTime}"
        }

    fun decode(snapshot: String): List<Entry> = snapshot
        .split('|')
        .mapNotNull { row ->
            val parts = row.split(',')
            if (parts.size != 3) return@mapNotNull null
            val date = parts[1].takeUnless { it == "null" }?.toLongOrNull()
            val time = parts[2].takeUnless { it == "null" }?.toLongOrNull()
            if (date == null || time == null) null else Entry(parts[0], date, time)
        }
}
