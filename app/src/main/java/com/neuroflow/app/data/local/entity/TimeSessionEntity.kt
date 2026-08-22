package com.neuroflow.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "time_sessions")
data class TimeSessionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val taskId: String,
    val startedAt: Long,
    val endedAt: Long? = null,       // null = session still open/active
    val pausedAt: Long? = null,      // null = not paused
    val totalPausedMs: Long = 0L,    // accumulated paused time
    val durationMinutes: Float = 0f, // filled on stop
    val sessionType: String = "MANUAL",
    val pomodoroNumber: Int = 0,
    val notes: String = ""
)
