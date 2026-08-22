package com.neuroflow.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.neuroflow.app.data.local.UserPreferencesDataStore
import com.neuroflow.app.data.local.entity.TaskEntity
import com.neuroflow.app.data.repository.TaskRepository
import com.neuroflow.app.domain.engine.FreshStartEngine
import com.neuroflow.app.domain.model.AppTheme
import com.neuroflow.app.domain.model.Quadrant
import com.neuroflow.app.presentation.common.GoalPeriod
import com.neuroflow.app.presentation.common.TopGoalsRefillCard
import com.neuroflow.app.presentation.common.NeuroFlowApp
import com.neuroflow.app.presentation.common.NewChapterCard
import com.neuroflow.app.presentation.common.theme.NeuroFlowTheme
import com.neuroflow.app.presentation.onboarding.OnboardingScreen
import com.neuroflow.app.worker.AutonomyNudgeWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var preferencesDataStore: UserPreferencesDataStore
    @Inject lateinit var taskRepository: TaskRepository

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* best effort */ }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)  // Update the intent so onCreate can also handle it
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val taskId = intent.getStringExtra("taskId") ?: intent.getStringExtra("task_id")
        when (intent.action) {
            "com.procus.ACTION_OPEN_FOCUS" -> {
                // Handle ACTION_OPEN_FOCUS from launcher
                // Navigation is handled by NeuroFlowApp via deep link
                // The intent is already set, so NeuroFlowApp will pick it up
                return
            }
            "RESCHEDULE_NUDGE" -> {
                if (taskId == null) return
                // Re-enqueue AutonomyNudgeWorker with 1-hour delay
                val request = OneTimeWorkRequestBuilder<AutonomyNudgeWorker>()
                    .setInitialDelay(1, TimeUnit.HOURS)
                    .setInputData(workDataOf("taskId" to taskId))
                    .addTag("autonomy_nudge_$taskId")
                    .build()
                WorkManager.getInstance(this).enqueue(request)
            }
            "SPLIT_TASK" -> {
                // TaskSplitter is called from AutonomyNudgeWorker context; here we just navigate
                // Navigation to the task is handled by NeuroFlowApp via deep link / intent extras
            }
            "WOOP_REFLECT" -> {
                // Navigate to MiniWoopReflectionScreen — handled by NeuroFlowApp nav
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        enableEdgeToEdge()

        // Handle ACTION_OPEN_FOCUS intent
        handleIntent(intent)

        setContent {
            val preferences by preferencesDataStore.preferencesFlow.collectAsState(initial = null)
            val scope = rememberCoroutineScope()
            // Track whether fresh-start card has been handled this session
            var freshStartHandled by remember { mutableStateOf(false) }
            // Track whether goals refill has been handled this session (skip = session-only)
            var yearlyGoalHandled by remember { mutableStateOf(false) }
            var weeklyGoalHandled by remember { mutableStateOf(false) }

            // Extract task_id from intent for ACTION_OPEN_FOCUS
            val initialTaskId = remember(intent) {
                if (intent.action == "com.procus.ACTION_OPEN_FOCUS") {
                    intent.getStringExtra("task_id")
                } else null
            }

            val darkTheme = when (preferences?.theme) {
                AppTheme.LIGHT -> false
                AppTheme.DARK  -> true
                else           -> isSystemInDarkTheme()
            }

            NeuroFlowTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val prefs = preferences
                    when {
                        // Still loading — render nothing to avoid flash
                        prefs == null -> Unit
                        // First launch — show onboarding
                        !prefs.onboardingCompleted -> OnboardingScreen(
                            onComplete = { data ->
                                scope.launch {
                                    // Convert energy period string → peak hour range
                                    val (peakStart, peakEnd) = when (data.peakEnergyPeriod) {
                                        "afternoon" -> 12 to 17
                                        "evening"   -> 17 to 21
                                        else        -> 9 to 12  // morning (default)
                                    }
                                    preferencesDataStore.updatePreferences { it.copy(
                                        identityLabel      = data.identityLabel,
                                        topGoal            = data.topGoal,
                                        wakeUpHour         = data.wakeUpHour,
                                        peakEnergyStart    = peakStart,
                                        peakEnergyEnd      = peakEnd,
                                        onboardingCompleted = true
                                    )}
                                    // Insert the "first task" as a DO_FIRST task if provided
                                    if (data.firstTask.isNotBlank()) {
                                        taskRepository.insert(
                                            TaskEntity(
                                                title       = data.firstTask,
                                                quadrant    = Quadrant.DO_FIRST,
                                                impactScore = 70,
                                                isFrog      = true
                                            )
                                        )
                                    }
                                }
                            }
                        )
                        // Fresh-start check — show NewChapterCard if applicable
                        !freshStartHandled && FreshStartEngine.isFreshStart(
                            nowMillis = System.currentTimeMillis(),
                            lastOpenMillis = prefs.lastAppOpenMillis,
                            dailyStreak = prefs.dailyStreak,
                            lastActiveDate = prefs.lastActiveDate,
                            lastFreshStartShownWeek = prefs.lastFreshStartShownWeek,
                            lastFreshStartShownYear = prefs.lastFreshStartShownYear
                        ) -> NewChapterCard(
                            onConfirm = { intent ->
                                freshStartHandled = true
                                scope.launch {
                                    val now = System.currentTimeMillis()
                                    preferencesDataStore.updatePreferences { p ->
                                        p.copy(
                                            weeklyIntent = intent,
                                            weeklyIntentIsoWeek = FreshStartEngine.isoWeekNumber(now),
                                            weeklyIntentIsoYear = FreshStartEngine.isoYear(now),
                                            lastFreshStartShownWeek = FreshStartEngine.isoWeekNumber(now),
                                            lastFreshStartShownYear = FreshStartEngine.isoYear(now),
                                            lastAppOpenMillis = now
                                        )
                                    }
                                }
                            },
                            onDismiss = {
                                freshStartHandled = true
                                scope.launch {
                                    val now = System.currentTimeMillis()
                                    preferencesDataStore.updatePreferences { p ->
                                        p.copy(
                                            lastFreshStartShownWeek = FreshStartEngine.isoWeekNumber(now),
                                            lastFreshStartShownYear = FreshStartEngine.isoYear(now),
                                            lastAppOpenMillis = now
                                        )
                                    }
                                }
                            }
                        )
                        // Normal app
                        else -> {
                            // Update lastAppOpenMillis on normal launch — set flag synchronously
                            // to prevent repeated writes on every recompose
                            if (!freshStartHandled) {
                                freshStartHandled = true
                                scope.launch {
                                    preferencesDataStore.updatePreferences { p ->
                                        p.copy(lastAppOpenMillis = System.currentTimeMillis())
                                    }
                                }
                            }
                            // Compute once per composition — stable within a session
                            val nowMillis = remember { System.currentTimeMillis() }
                            val currentYear = remember(nowMillis) { FreshStartEngine.isoYear(nowMillis) }
                            val currentWeek = remember(nowMillis) { FreshStartEngine.isoWeekNumber(nowMillis) }
                            val needsYearlyRefill = !yearlyGoalHandled &&
                                (prefs.yearlyGoals.all { it.isBlank() } || prefs.lastYearlyGoalShownYear != currentYear)
                            val needsWeeklyRefill = !weeklyGoalHandled && !needsYearlyRefill &&
                                (prefs.weeklyGoals.all { it.isBlank() } ||
                                    prefs.lastWeeklyGoalShownWeek != currentWeek ||
                                    prefs.lastWeeklyGoalShownYear != currentYear)

                            when {
                                needsYearlyRefill -> TopGoalsRefillCard(
                                    period = GoalPeriod.YEARLY,
                                    existingGoals = prefs.yearlyGoals,
                                    onConfirm = { goals ->
                                        yearlyGoalHandled = true
                                        scope.launch {
                                            preferencesDataStore.updatePreferences { p ->
                                                p.copy(
                                                    yearlyGoals = goals,
                                                    lastYearlyGoalShownYear = currentYear
                                                )
                                            }
                                        }
                                    },
                                    onSkip = { yearlyGoalHandled = true }
                                )
                                needsWeeklyRefill -> TopGoalsRefillCard(
                                    period = GoalPeriod.WEEKLY,
                                    existingGoals = prefs.weeklyGoals,
                                    onConfirm = { goals ->
                                        weeklyGoalHandled = true
                                        scope.launch {
                                            preferencesDataStore.updatePreferences { p ->
                                                p.copy(
                                                    weeklyGoals = goals,
                                                    lastWeeklyGoalShownWeek = currentWeek,
                                                    lastWeeklyGoalShownYear = currentYear
                                                )
                                            }
                                        }
                                    },
                                    onSkip = { weeklyGoalHandled = true }
                                )
                                else -> NeuroFlowApp(initialTaskId = initialTaskId)
                            }
                        }
                    }
                }
            }
        }
    }
}
