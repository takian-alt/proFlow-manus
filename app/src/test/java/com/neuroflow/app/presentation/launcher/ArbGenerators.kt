package com.neuroflow.app.presentation.launcher

import android.graphics.drawable.ColorDrawable
import android.os.UserHandle
import androidx.compose.ui.unit.LayoutDirection
import com.neuroflow.app.data.local.entity.TaskEntity
import com.neuroflow.app.domain.model.*
import com.neuroflow.app.presentation.launcher.data.AppInfo
import com.neuroflow.app.presentation.launcher.data.LauncherPreferences
import com.neuroflow.app.presentation.launcher.data.FolderDefinition
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import java.util.UUID

/**
 * Kotest Arb generators for launcher property-based tests.
 * All generators produce valid data within specified ranges.
 */

/**
 * Generates random TaskEntity with valid field ranges.
 *
 * @param status Optional fixed status (null = random)
 * @param quadrant Optional fixed quadrant (null = random)
 * @param isFrog Optional fixed frog flag (null = random)
 */
fun Arb.Companion.taskEntity(
    status: TaskStatus? = null,
    quadrant: Quadrant? = null,
    isFrog: Boolean? = null
): Arb<TaskEntity> = arbitrary { rs ->
    val now = System.currentTimeMillis()
    TaskEntity(
        id = UUID.randomUUID().toString(),
        title = Arb.string(5..50).bind(),
        description = Arb.string(0..200).bind(),

        // Classification
        quadrant = quadrant ?: Arb.enum<Quadrant>().bind(),
        priority = Arb.enum<Priority>().bind(),
        tags = Arb.string(0..100).bind(),

        // Timing
        deadlineDate = Arb.long(now, now + 365L * 24 * 60 * 60 * 1000).orNull(0.3).bind(),
        deadlineTime = Arb.long(0L, 24L * 60 * 60 * 1000).orNull(0.5).bind(),
        scheduledDate = Arb.long(now, now + 90L * 24 * 60 * 60 * 1000).orNull(0.4).bind(),
        scheduledTime = Arb.long(0L, 24L * 60 * 60 * 1000).orNull(0.5).bind(),
        isScheduleLocked = Arb.bool().bind(),
        estimatedDurationMinutes = Arb.int(0..480).bind(),
        recurrence = Arb.enum<Recurrence>().bind(),
        recurrenceIntervalDays = Arb.int(1..365).bind(),

        // Reminders
        reminderFlags = Arb.int(0..15).bind(),

        // Scoring inputs (0-100 ranges as per spec)
        impactScore = Arb.int(0..100).bind(),
        valueScore = Arb.int(0..100).bind(),
        effortScore = Arb.int(1..5).bind(), // Note: spec says 1-5 for effort
        parentTaskId = Arb.string(10..36).orNull(0.8).bind(),
        blockingTaskIds = Arb.string(0..200).bind(),
        dependsOnTaskIds = Arb.string(0..200).bind(),
        waitingFor = Arb.string(0..200).bind(),

        // Time tracking
        totalTimeTrackedMinutes = Arb.float(0f..10000f).bind(),
        sessionCount = Arb.int(0..1000).bind(),
        lastSessionDurationMinutes = Arb.float(1f..480f).orNull(0.5).bind(),

        // Adaptive difficulty
        actualDurationMinutes = Arb.float(1f..480f).orNull(0.6).bind(),
        estimationErrorMape = Arb.float(0f..200f).orNull(0.7).bind(),
        estimationErrorSmape = Arb.float(0f..100f).orNull(0.7).bind(),

        // State
        status = status ?: Arb.enum<TaskStatus>().bind(),
        completedAt = Arb.long(now - 365L * 24 * 60 * 60 * 1000, now).orNull(0.7).bind(),
        createdAt = Arb.long(now - 365L * 24 * 60 * 60 * 1000, now).bind(),
        updatedAt = Arb.long(now - 30L * 24 * 60 * 60 * 1000, now).bind(),

        // Neuroscience extras
        energyLevel = Arb.enum<EnergyLevel>().bind(),
        taskType = Arb.enum<TaskType>().bind(),
        contextTag = Arb.string(0..50).bind(),
        goalId = Arb.string(10..36).orNull(0.6).bind(),
        ifThenPlan = Arb.string(0..500).bind(),
        isHabitual = Arb.bool().bind(),
        habitStreak = Arb.int(0..1000).bind(),
        isFrog = isFrog ?: Arb.bool().bind(),
        postponeCount = Arb.int(0..50).bind(),
        focusModePoints = Arb.int(0..10000).bind(),

        // Science-backed productivity boosters
        enjoymentScore = Arb.int(0..100).bind(),
        isPublicCommitment = Arb.bool().bind(),
        isAnxietyTask = Arb.bool().bind(),
        goalRiskLevel = Arb.int(0..2).bind(),

        // Habit anchor date
        habitDate = Arb.long(now, now + 90L * 24 * 60 * 60 * 1000).orNull(0.5).bind(),

        // Behavioral motivation engine fields
        affectiveForecastError = Arb.float(-100f..100f).orNull(0.7).bind(),
        woopPromptShown = Arb.bool().bind()
    )
}

/**
 * Generates random FolderDefinition with 1-20 packages.
 */
fun Arb.Companion.folderDefinition(): Arb<FolderDefinition> = arbitrary {
    FolderDefinition(
        id = UUID.randomUUID().toString(),
        name = Arb.string(1..30).bind(),
        packages = Arb.list(Arb.string(10..50), 1..20).bind(),
        gridIndex = Arb.int(0..100).bind()
    )
}

/**
 * Generates random LauncherPreferences with valid field ranges.
 * - cardAlpha: 0.5-1.0
 * - drawerColumns: 3-5
 * - distractionScores: 0-100
 */
fun Arb.Companion.launcherPreferences(): Arb<LauncherPreferences> = arbitrary {
    LauncherPreferences(
        dockPackages = Arb.list(Arb.string(10..50), 0..5).bind(),
        folders = Arb.list(Arb.folderDefinition(), 0..10).bind(),
        hiddenPackages = Arb.set(Arb.string(10..50), 0..20).bind(),
        lockedPackages = Arb.set(Arb.string(10..50), 0..20).bind(),
        recentPackages = Arb.list(Arb.string(10..50), 0..10).bind(),
        cardAlpha = Arb.float(0.5f..1.0f).bind(),
        clockStyle = Arb.enum<com.neuroflow.app.presentation.launcher.data.ClockStyle>().bind(),
        iconPackPackageName = Arb.string(10..50).orNull(0.7).bind(),
        iconShape = Arb.enum<com.neuroflow.app.presentation.launcher.data.IconShape>().bind(),
        drawerColumns = Arb.int(3..5).bind(),
        distractionScores = Arb.map(
            Arb.string(10..50),
            Arb.int(0..100),
            minSize = 0,
            maxSize = 30
        ).bind(),
        backupMetadata = Arb.bind(
            Arb.long(System.currentTimeMillis() - 365L * 24 * 60 * 60 * 1000, System.currentTimeMillis()),
            Arb.int(1..10)
        ) { timestamp, version ->
            com.neuroflow.app.presentation.launcher.data.BackupMetadata(timestamp, version)
        }.orNull(0.5).bind(),
        webSearchUrl = Arb.string(10..100).bind(),
        showTaskScore = Arb.bool().bind(),
        skippedTaskIds = Arb.set(Arb.string(10..36), 0..20).bind()
    )
}

/**
 * Generates random AppInfo with optional fixed distraction score.
 *
 * @param distractionScore Optional fixed distraction score (null = random 0-100)
 */
fun Arb.Companion.appInfo(
    distractionScore: Int? = null
): Arb<AppInfo> = arbitrary {
    // Note: UserHandle and Drawable are Android classes that can't be easily mocked
    // For property tests, we use a mock UserHandle (system user) and ColorDrawable
    AppInfo(
        label = Arb.string(1..50).bind(),
        packageName = Arb.string(10..50).bind(),
        className = Arb.string(10..100).bind(),
        userHandle = android.os.Process.myUserHandle(), // System user handle for tests
        icon = ColorDrawable(Arb.int().bind()), // Simple drawable for tests
        distractionScore = distractionScore ?: Arb.int(0..100).bind(),
        isWorkProfile = Arb.bool().bind()
    )
}

/**
 * Generates non-negative badge count (0-999).
 */
fun Arb.Companion.badgeCount(): Arb<Int> = Arb.int(0..999)

/**
 * Generates layout direction (LTR or RTL).
 */
fun Arb.Companion.layoutDirection(): Arb<LayoutDirection> = Arb.enum<LayoutDirection>()
