package com.neuroflow.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.neuroflow.app.data.local.UserPreferencesDataStore
import com.neuroflow.app.data.local.entity.TaskEntity
import com.neuroflow.app.data.repository.TaskRepository
import com.neuroflow.app.domain.model.AppTheme
import com.neuroflow.app.domain.model.Quadrant
import com.neuroflow.app.presentation.common.NeuroFlowApp
import com.neuroflow.app.presentation.common.theme.NeuroFlowTheme
import com.neuroflow.app.presentation.onboarding.OnboardingScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var preferencesDataStore: UserPreferencesDataStore
    @Inject lateinit var taskRepository: TaskRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val preferences by preferencesDataStore.preferencesFlow.collectAsState(initial = null)
            val scope = rememberCoroutineScope()

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
                        // Normal app
                        else -> NeuroFlowApp()
                    }
                }
            }
        }
    }
}
