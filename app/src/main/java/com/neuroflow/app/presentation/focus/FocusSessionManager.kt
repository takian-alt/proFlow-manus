package com.neuroflow.app.presentation.focus

import com.neuroflow.app.data.local.entity.TimeSessionEntity
import com.neuroflow.app.data.repository.SessionRepository
import com.neuroflow.app.data.repository.TaskRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FocusSessionManager @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val taskRepository: TaskRepository
) {

    data class RestoredSessionState(
        val sessionId: String,
        val isPaused: Boolean,
        val elapsedSeconds: Long
    )

    suspend fun restoreActiveSession(taskId: String, now: Long): RestoredSessionState? {
        val openSession = sessionRepository.getOpenSessionForTask(taskId) ?: return null
        val isPaused = openSession.pausedAt != null

        // Elapsed = (now - startedAt) - totalPausedMs - (time since last pause if paused)
        val pausedSinceMs = if (isPaused) now - openSession.pausedAt!! else 0L
        val elapsedMs = (now - openSession.startedAt) - openSession.totalPausedMs - pausedSinceMs
        val elapsedSec = maxOf(0L, elapsedMs / 1000L)

        return RestoredSessionState(
            sessionId = openSession.id,
            isPaused = isPaused,
            elapsedSeconds = elapsedSec
        )
    }

    suspend fun startSession(taskId: String, now: Long): TimeSessionEntity {
        val session = TimeSessionEntity(
            taskId = taskId,
            startedAt = now,
            endedAt = null,
            pausedAt = null,
            totalPausedMs = 0L,
            pauseResumeCount = 0,
            appSwitchCount = 0,
            interruptionBurstCount = 0,
            sessionType = "MANUAL"
        )
        sessionRepository.insert(session)
        return session
    }

    suspend fun togglePause(taskId: String, currentlyPaused: Boolean, now: Long): Boolean? {
        val session = sessionRepository.getOpenSessionForTask(taskId) ?: return null
        return if (!currentlyPaused) {
            sessionRepository.update(
                session.copy(
                    pausedAt = now,
                    pauseResumeCount = session.pauseResumeCount + 1
                )
            )
            true
        } else {
            val pausedDuration = now - (session.pausedAt ?: now)
            sessionRepository.update(
                session.copy(
                    pausedAt = null,
                    totalPausedMs = session.totalPausedMs + pausedDuration,
                    pauseResumeCount = session.pauseResumeCount + 1
                )
            )
            false
        }
    }

    suspend fun pauseAllOpenSessions(now: Long) {
        val openSessions = sessionRepository.getOpenSessions()
        openSessions.forEach { session ->
            if (session.pausedAt == null) {
                sessionRepository.update(
                    session.copy(
                        pausedAt = now,
                        pauseResumeCount = session.pauseResumeCount + 1
                    )
                )
            }
        }
    }

    suspend fun recordInterruptionBurst(taskId: String, appSwitchDelta: Int = 1) {
        val session = sessionRepository.getOpenSessionForTask(taskId) ?: return
        sessionRepository.update(
            session.copy(
                appSwitchCount = (session.appSwitchCount + appSwitchDelta).coerceAtLeast(0),
                interruptionBurstCount = session.interruptionBurstCount + 1
            )
        )
    }

    suspend fun finalizeSession(taskId: String, now: Long): Float? {
        val session = sessionRepository.getOpenSessionForTask(taskId) ?: return null
        val extraPausedMs = if (session.pausedAt != null) now - session.pausedAt else 0L
        val totalPaused = session.totalPausedMs + extraPausedMs
        val elapsedMs = (now - session.startedAt) - totalPaused
        val durationMinutes = maxOf(0f, elapsedMs / 60_000f)

        sessionRepository.update(
            session.copy(
                endedAt = now,
                pausedAt = null,
                totalPausedMs = totalPaused,
                durationMinutes = durationMinutes
            )
        )

        val task = taskRepository.getById(taskId)
        task?.let {
            taskRepository.update(
                it.copy(
                    totalTimeTrackedMinutes = it.totalTimeTrackedMinutes + durationMinutes,
                    sessionCount = it.sessionCount + 1,
                    lastSessionDurationMinutes = durationMinutes,
                    updatedAt = now
                )
            )
        }

        return durationMinutes
    }
}
