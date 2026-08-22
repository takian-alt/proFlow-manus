package com.neuroflow.app.presentation.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neuroflow.app.BuildConfig
import com.neuroflow.app.domain.model.AppTheme
import com.neuroflow.app.presentation.common.EnergyInsight
import com.neuroflow.app.presentation.common.theme.NeuroFlowColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPriorityWeights: () -> Unit,
    onNavigateToLauncherSettings: () -> Unit = {},
    onNavigateToAppGuide: () -> Unit = {},
    onNavigateToPrivacyPermissions: () -> Unit = {},
    onNavigateToMEQQuiz: () -> Unit = {},
    onNavigateToSleepLogs: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val peakDetection by viewModel.peakDetection.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }
    var showClearTelemetryDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Profile & Identity
            SettingsSection("Profile & Identity") {
                // Sync local state when prefs load from DataStore (avoids stale initial value)
                var identityLabel by remember { mutableStateOf(prefs.identityLabel) }
                LaunchedEffect(prefs.identityLabel) { identityLabel = prefs.identityLabel }
                OutlinedTextField(
                    value = identityLabel,
                    onValueChange = {
                        identityLabel = it
                        viewModel.updatePreferences { p -> p.copy(identityLabel = it) }
                    },
                    label = { Text("I am a...") },
                    placeholder = { Text("e.g. Deep Worker, Consistent Learner") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                var topGoal by remember { mutableStateOf(prefs.topGoal) }
                LaunchedEffect(prefs.topGoal) { topGoal = prefs.topGoal }
                OutlinedTextField(
                    value = topGoal,
                    onValueChange = {
                        topGoal = it
                        viewModel.updatePreferences { p -> p.copy(topGoal = it) }
                    },
                    label = { Text("My top goal") },
                    placeholder = { Text("What are you working towards?") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                SettingsNumberRow("Default Wake Hour", prefs.wakeUpHour, 0, 23, formatHour = true) {
                    viewModel.updatePreferences { p -> p.copy(wakeUpHour = it) }
                }
                SettingsNumberRow("Default Sleep Hour", prefs.sleepHour, 0, 23, formatHour = true) {
                    viewModel.updatePreferences { p -> p.copy(sleepHour = it) }
                }
                SettingsNumberRow("Peak Energy Start", prefs.peakEnergyStart, 0, 23, formatHour = true) {
                    viewModel.updatePreferences { p -> p.copy(peakEnergyStart = it) }
                }
                SettingsNumberRow("Peak Energy End", prefs.peakEnergyEnd, 0, 23, formatHour = true) {
                    // Must be after peak start
                    viewModel.updatePreferences { p -> p.copy(peakEnergyEnd = maxOf(it, p.peakEnergyStart + 1)) }
                }
                if (prefs.peakEnergyEnd <= prefs.peakEnergyStart) {
                    Text(
                        "Peak end must be after peak start",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
            }

            // Work Schedule
            SettingsCompactBox(title = "Work Schedule", collapsedByDefault = true) {
                SettingsNumberRow("Work Day Start", prefs.workDayStart, 0, 23, formatHour = true) {
                    viewModel.updatePreferences { p -> p.copy(workDayStart = it) }
                }
                SettingsNumberRow("Work Day End", prefs.workDayEnd, 0, 23, formatHour = true) {
                    // Must be after work start
                    viewModel.updatePreferences { p -> p.copy(workDayEnd = maxOf(it, p.workDayStart + 1)) }
                }
                if (prefs.workDayEnd <= prefs.workDayStart) {
                    Text(
                        "Work end must be after work start",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                } else {
                    Text(
                        "Work window: ${hourLabel(prefs.workDayStart)} – ${hourLabel(prefs.workDayEnd)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                SettingsNumberRow("Pomodoro (minutes)", prefs.defaultPomodoroMinutes, 5, 60) {
                    viewModel.updatePreferences { p -> p.copy(defaultPomodoroMinutes = it) }
                }
                SettingsNumberRow("Break (minutes)", prefs.defaultBreakMinutes, 1, 30) {
                    viewModel.updatePreferences { p -> p.copy(defaultBreakMinutes = it) }
                }
            }

            // Focus Behaviour + Neuro Booster automation
            SettingsSection("Focus Behaviour") {
                SettingsToggleRow(
                    label = "WOOP planning prompt",
                    description = "Show the WOOP sheet when opening a task for the first time",
                    checked = prefs.woopEnabled,
                    onCheckedChange = { viewModel.updatePreferences { p -> p.copy(woopEnabled = it) } }
                )
                Spacer(modifier = Modifier.height(8.dp))
                SettingsToggleRow(
                    label = "Auto-start tracker",
                    description = "Automatically starts tracking after 8 seconds on the focus screen",
                    checked = prefs.autoTrackerEnabled,
                    onCheckedChange = { viewModel.updatePreferences { p -> p.copy(autoTrackerEnabled = it) } }
                )
            }

            SettingsSection("Notifications") {
                SettingsToggleRow(
                    label = "Daily plan notification",
                    description = "Send your top-3 planning notification each morning",
                    checked = prefs.dailyPlanNotificationsEnabled,
                    onCheckedChange = {
                        viewModel.updatePreferences { p -> p.copy(dailyPlanNotificationsEnabled = it) }
                    }
                )
                if (prefs.dailyPlanNotificationsEnabled) {
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsNumberRow(
                        label = "Daily plan hour",
                        value = prefs.dailyPlanNotificationHour,
                        min = 0,
                        max = 23,
                        formatHour = true
                    ) {
                        viewModel.updatePreferences { p -> p.copy(dailyPlanNotificationHour = it) }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                SettingsToggleRow(
                    label = "Streak check notification",
                    description = "Warn when your streak is at risk in the evening",
                    checked = prefs.streakNotificationsEnabled,
                    onCheckedChange = {
                        viewModel.updatePreferences { p -> p.copy(streakNotificationsEnabled = it) }
                    }
                )
                if (prefs.streakNotificationsEnabled) {
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsNumberRow(
                        label = "Streak check hour",
                        value = prefs.streakCheckNotificationHour,
                        min = 0,
                        max = 23,
                        formatHour = true
                    ) {
                        viewModel.updatePreferences { p -> p.copy(streakCheckNotificationHour = it) }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                SettingsToggleRow(
                    label = "Autonomy nudge notifications",
                    description = "Allow 2-hour nudges for untouched tasks",
                    checked = prefs.autonomyNudgeNotificationsEnabled,
                    onCheckedChange = {
                        viewModel.updatePreferences { p -> p.copy(autonomyNudgeNotificationsEnabled = it) }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                SettingsToggleRow(
                    label = "Deadline reminder notifications",
                    description = "Allow 15m/30m/1h/1d deadline reminders",
                    checked = prefs.deadlineReminderNotificationsEnabled,
                    onCheckedChange = {
                        viewModel.updatePreferences { p -> p.copy(deadlineReminderNotificationsEnabled = it) }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                SettingsToggleRow(
                    label = "Deadline escalation alerts",
                    description = "Allow schedule-to-do-first escalation notifications",
                    checked = prefs.deadlineEscalationNotificationsEnabled,
                    onCheckedChange = {
                        viewModel.updatePreferences { p -> p.copy(deadlineEscalationNotificationsEnabled = it) }
                    }
                )
            }

            // Features → Energy
            SettingsSection("Energy") {
                SettingsToggleRow(
                    label = "Use MEQ quiz peak",
                    description = "When off, manual Peak Energy Start/End are used even if quiz results exist",
                    checked = prefs.quizPeakEnabled,
                    onCheckedChange = { enabled ->
                        viewModel.updatePreferences { p -> p.copy(quizPeakEnabled = enabled) }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (prefs.quizPeakEnabled) {
                        "Scoring source: MEQ quiz peak when available"
                    } else {
                        "Scoring source: manual peak window"
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                val effectiveProfile = peakDetection?.effectiveProfile
                val detectedMinute = EnergyInsight.detectedPeakMinuteOfDayOrNull(prefs)
                val effectiveMinute = effectiveProfile?.anchorMinuteOfDay ?: EnergyInsight.effectivePeakMinuteOfDay(prefs)
                Text(
                    text = "Effective peak point: ${EnergyInsight.minuteLabel(effectiveMinute)}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                if (detectedMinute != null) {
                    Text(
                        text = "Detected peak point: ${EnergyInsight.minuteLabel(detectedMinute)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = EnergyInsight.profileConfidenceLine(effectiveProfile),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = EnergyInsight.profileModeLabel(
                        manualOverrideEnabled = prefs.manualPeakProfileEnabled,
                        profile = effectiveProfile
                    ),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                val chronotype = prefs.quizChronotype ?: prefs.manualChronotype
                val isMorningType = EnergyInsight.isMorningType(chronotype)
                if (isMorningType) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = EnergyInsight.profileSummary(effectiveProfile),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = EnergyInsight.adaptiveHint(effectiveProfile),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = EnergyInsight.backtestSummary(effectiveProfile),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Adaptive morning diagnostics appear after selecting a morning chronotype and collecting session data.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!prefs.quizPeakEnabled) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tip: turn quiz peak on to use personalized morning adjustments from sleep behavior.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                SettingsCompactBox(title = "Profile Controls", collapsedByDefault = true) {
                    SettingsToggleRow(
                        label = "Manual profile override",
                        description = "Replace adaptive profile with your custom anchor, windows, and amplitudes",
                        checked = prefs.manualPeakProfileEnabled,
                        onCheckedChange = {
                            viewModel.updatePreferences { p -> p.copy(manualPeakProfileEnabled = it) }
                        }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Profile type", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("AUTO", "WORKDAY", "WEEKEND").forEach { mode ->
                            FilterChip(
                                selected = prefs.manualPeakProfileType == mode,
                                enabled = prefs.manualPeakProfileEnabled,
                                onClick = { viewModel.updatePreferences { p -> p.copy(manualPeakProfileType = mode) } },
                                label = { Text(mode.lowercase().replaceFirstChar { c -> c.uppercase() }) }
                            )
                        }
                    }

                    val selectedChronotype = prefs.quizChronotype ?: prefs.manualChronotype
                    val chronotypePreset = manualProfilePresetForChronotype(
                        chronotype = selectedChronotype,
                        wakeUpHour = prefs.wakeUpHour
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Chronotype presets", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "Current preset: ${chronotypePreset.label}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            enabled = prefs.manualPeakProfileEnabled,
                            onClick = {
                                val preset = manualProfilePresetForChronotype(
                                    chronotype = "INTERMEDIATE",
                                    wakeUpHour = prefs.wakeUpHour
                                )
                                viewModel.updatePreferences { p ->
                                    p.copy(
                                        manualPeakProfileType = "AUTO",
                                        manualPeakAnchorMinuteOfDay = preset.anchorMinuteOfDay,
                                        manualPeakWindow1StartOffsetMinutes = preset.window1StartOffsetMinutes,
                                        manualPeakWindow2StartOffsetMinutes = preset.window2StartOffsetMinutes,
                                        manualPeakWindow3StartOffsetMinutes = preset.window3StartOffsetMinutes,
                                        manualPeakWindow1DurationMinutes = preset.window1DurationMinutes,
                                        manualPeakWindow2DurationMinutes = preset.window2DurationMinutes,
                                        manualPeakWindow3DurationMinutes = preset.window3DurationMinutes,
                                        manualPeakWindow1Amplitude = preset.window1Amplitude,
                                        manualPeakWindow2Amplitude = preset.window2Amplitude,
                                        manualPeakWindow3Amplitude = preset.window3Amplitude
                                    )
                                }
                            }
                        ) {
                            Text("Apply Intermediate")
                        }
                        OutlinedButton(
                            enabled = prefs.manualPeakProfileEnabled,
                            onClick = {
                                val preset = manualProfilePresetForChronotype(
                                    chronotype = "DEFINITE_EVENING",
                                    wakeUpHour = prefs.wakeUpHour
                                )
                                viewModel.updatePreferences { p ->
                                    p.copy(
                                        manualPeakProfileType = "AUTO",
                                        manualPeakAnchorMinuteOfDay = preset.anchorMinuteOfDay,
                                        manualPeakWindow1StartOffsetMinutes = preset.window1StartOffsetMinutes,
                                        manualPeakWindow2StartOffsetMinutes = preset.window2StartOffsetMinutes,
                                        manualPeakWindow3StartOffsetMinutes = preset.window3StartOffsetMinutes,
                                        manualPeakWindow1DurationMinutes = preset.window1DurationMinutes,
                                        manualPeakWindow2DurationMinutes = preset.window2DurationMinutes,
                                        manualPeakWindow3DurationMinutes = preset.window3DurationMinutes,
                                        manualPeakWindow1Amplitude = preset.window1Amplitude,
                                        manualPeakWindow2Amplitude = preset.window2Amplitude,
                                        manualPeakWindow3Amplitude = preset.window3Amplitude
                                    )
                                }
                            }
                        ) {
                            Text("Apply Night Owl")
                        }
                    }
                    Text(
                        text = "Presets use wake-up anchored peaks. You can edit each window start offset below.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val anchorHour = (prefs.manualPeakAnchorMinuteOfDay / 60).coerceIn(0, 23)
                    val anchorMinute = (prefs.manualPeakAnchorMinuteOfDay % 60).coerceIn(0, 59)
                    SettingsNumberRow(
                        "Anchor Hour",
                        anchorHour,
                        0,
                        23,
                        formatHour = true,
                        enabled = prefs.manualPeakProfileEnabled
                    ) { hour ->
                        viewModel.updatePreferences { p ->
                            p.copy(manualPeakAnchorMinuteOfDay = hour * 60 + anchorMinute)
                        }
                    }
                    SettingsNumberRow(
                        "Anchor Minute",
                        anchorMinute,
                        0,
                        59,
                        enabled = prefs.manualPeakProfileEnabled
                    ) { minute ->
                        viewModel.updatePreferences { p ->
                            p.copy(manualPeakAnchorMinuteOfDay = anchorHour * 60 + minute)
                        }
                    }
                    SettingsNumberRow(
                        "Window 1 Offset (min)",
                        prefs.manualPeakWindow1StartOffsetMinutes,
                        0,
                        1439,
                        enabled = prefs.manualPeakProfileEnabled
                    ) {
                        viewModel.updatePreferences { p -> p.copy(manualPeakWindow1StartOffsetMinutes = it) }
                    }
                    SettingsNumberRow(
                        "Window 2 Offset (min)",
                        prefs.manualPeakWindow2StartOffsetMinutes,
                        0,
                        1439,
                        enabled = prefs.manualPeakProfileEnabled
                    ) {
                        viewModel.updatePreferences { p -> p.copy(manualPeakWindow2StartOffsetMinutes = it) }
                    }
                    SettingsNumberRow(
                        "Window 3 Offset (min)",
                        prefs.manualPeakWindow3StartOffsetMinutes,
                        0,
                        1439,
                        enabled = prefs.manualPeakProfileEnabled
                    ) {
                        viewModel.updatePreferences { p -> p.copy(manualPeakWindow3StartOffsetMinutes = it) }
                    }
                    SettingsNumberRow(
                        "Window 1 Duration (min)",
                        prefs.manualPeakWindow1DurationMinutes,
                        30,
                        360,
                        enabled = prefs.manualPeakProfileEnabled
                    ) {
                        viewModel.updatePreferences { p -> p.copy(manualPeakWindow1DurationMinutes = it) }
                    }
                    SettingsNumberRow(
                        "Window 2 Duration (min)",
                        prefs.manualPeakWindow2DurationMinutes,
                        30,
                        360,
                        enabled = prefs.manualPeakProfileEnabled
                    ) {
                        viewModel.updatePreferences { p -> p.copy(manualPeakWindow2DurationMinutes = it) }
                    }
                    SettingsNumberRow(
                        "Window 3 Duration (min)",
                        prefs.manualPeakWindow3DurationMinutes,
                        30,
                        360,
                        enabled = prefs.manualPeakProfileEnabled
                    ) {
                        viewModel.updatePreferences { p -> p.copy(manualPeakWindow3DurationMinutes = it) }
                    }
                    SettingsFloatSliderRow(
                        label = "Window 1 Amplitude",
                        value = prefs.manualPeakWindow1Amplitude,
                        enabled = prefs.manualPeakProfileEnabled,
                        onValueChange = { viewModel.updatePreferences { p -> p.copy(manualPeakWindow1Amplitude = it) } }
                    )
                    SettingsFloatSliderRow(
                        label = "Window 2 Amplitude",
                        value = prefs.manualPeakWindow2Amplitude,
                        enabled = prefs.manualPeakProfileEnabled,
                        onValueChange = { viewModel.updatePreferences { p -> p.copy(manualPeakWindow2Amplitude = it) } }
                    )
                    SettingsFloatSliderRow(
                        label = "Window 3 Amplitude",
                        value = prefs.manualPeakWindow3Amplitude,
                        enabled = prefs.manualPeakProfileEnabled,
                        onValueChange = { viewModel.updatePreferences { p -> p.copy(manualPeakWindow3Amplitude = it) } }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Profile shape preview", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ProfileWindowPreviewRow(
                        label = "W1",
                        startOffsetMinutes = prefs.manualPeakWindow1StartOffsetMinutes,
                        durationMinutes = prefs.manualPeakWindow1DurationMinutes,
                        amplitude = prefs.manualPeakWindow1Amplitude
                    )
                    ProfileWindowPreviewRow(
                        label = "W2",
                        startOffsetMinutes = prefs.manualPeakWindow2StartOffsetMinutes,
                        durationMinutes = prefs.manualPeakWindow2DurationMinutes,
                        amplitude = prefs.manualPeakWindow2Amplitude
                    )
                    ProfileWindowPreviewRow(
                        label = "W3",
                        startOffsetMinutes = prefs.manualPeakWindow3StartOffsetMinutes,
                        durationMinutes = prefs.manualPeakWindow3DurationMinutes,
                        amplitude = prefs.manualPeakWindow3Amplitude
                    )
                    OutlinedButton(
                        enabled = prefs.manualPeakProfileEnabled,
                        onClick = {
                            val preset = manualProfilePresetForChronotype(
                                chronotype = selectedChronotype,
                                wakeUpHour = prefs.wakeUpHour
                            )
                            viewModel.updatePreferences { p ->
                                p.copy(
                                    manualPeakProfileType = "AUTO",
                                    manualPeakAnchorMinuteOfDay = preset.anchorMinuteOfDay,
                                    manualPeakWindow1StartOffsetMinutes = preset.window1StartOffsetMinutes,
                                    manualPeakWindow2StartOffsetMinutes = preset.window2StartOffsetMinutes,
                                    manualPeakWindow3StartOffsetMinutes = preset.window3StartOffsetMinutes,
                                    manualPeakWindow1DurationMinutes = preset.window1DurationMinutes,
                                    manualPeakWindow2DurationMinutes = preset.window2DurationMinutes,
                                    manualPeakWindow3DurationMinutes = preset.window3DurationMinutes,
                                    manualPeakWindow1Amplitude = preset.window1Amplitude,
                                    manualPeakWindow2Amplitude = preset.window2Amplitude,
                                    manualPeakWindow3Amplitude = preset.window3Amplitude
                                )
                            }
                        }
                    ) {
                        Text("Reset to chronotype defaults")
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.updatePreferences { p ->
                                p.copy(
                                    morningTuneSleepWeight = 0.30f,
                                    morningTuneWakeWeight = 0.25f,
                                    morningTuneBehaviorWeight = 0.25f,
                                    morningTuneBaseWeight = 0.20f,
                                    morningTuneUpdatedAtMillis = 0L
                                )
                            }
                        }
                    ) {
                        Text("Reset auto-tune weights")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                SettingsCompactBox(title = "Moment Sensitivity", collapsedByDefault = true) {
                    Text(
                        "Tune how strongly live context signals affect moment energy adjustments.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SettingsFloatSliderRow(
                        label = "Interruption sensitivity",
                        value = prefs.momentInterruptionSensitivity,
                        minValue = 0.5f,
                        maxValue = 2.0f,
                        onValueChange = {
                            viewModel.updatePreferences { p -> p.copy(momentInterruptionSensitivity = it) }
                        }
                    )
                    SettingsFloatSliderRow(
                        label = "Notification sensitivity",
                        value = prefs.momentNotificationSensitivity,
                        minValue = 0.5f,
                        maxValue = 2.0f,
                        onValueChange = {
                            viewModel.updatePreferences { p -> p.copy(momentNotificationSensitivity = it) }
                        }
                    )
                    SettingsFloatSliderRow(
                        label = "Task pressure sensitivity",
                        value = prefs.momentTaskPressureSensitivity,
                        minValue = 0.5f,
                        maxValue = 2.0f,
                        onValueChange = {
                            viewModel.updatePreferences { p -> p.copy(momentTaskPressureSensitivity = it) }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                SettingsCompactBox(title = "Telemetry & Privacy", collapsedByDefault = true) {
                    SettingsToggleRow(
                        label = "Energy telemetry (local only)",
                        description = "Store prediction snapshots on-device for calibration and diagnostics",
                        checked = prefs.energyTelemetryEnabled,
                        onCheckedChange = {
                            viewModel.updatePreferences { p -> p.copy(energyTelemetryEnabled = it) }
                        }
                    )
                    if (prefs.energyTelemetryEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Retention (days)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(7, 14, 30, 60, 90).forEach { days ->
                                FilterChip(
                                    selected = prefs.energyTelemetryRetentionDays == days,
                                    onClick = {
                                        viewModel.updatePreferences { p -> p.copy(energyTelemetryRetentionDays = days) }
                                    },
                                    label = { Text("$days") }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Telemetry remains on this device. It is never required for live scoring.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showClearTelemetryDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeuroFlowColors.TrackingRed)
                    ) {
                        Icon(Icons.Filled.DeleteSweep, "Clear telemetry")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clear Energy Telemetry")
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                onClick = onNavigateToMEQQuiz
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Energy", fontWeight = FontWeight.Bold)
                        Text("Chronotype assessment & peak energy tracking", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Filled.ChevronRight, "Navigate")
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                onClick = onNavigateToSleepLogs
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Sleep Logs", fontWeight = FontWeight.Bold)
                        Text("Track sleep sessions and fatigue", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Filled.ChevronRight, "Navigate")
                }
            }

            // Priority Weights
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                onClick = onNavigateToPriorityWeights
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Priority Weights", fontWeight = FontWeight.Bold)
                        Text("Customize task scoring weights", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Filled.ChevronRight, "Navigate")
                }
            }

            // Launcher
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                onClick = onNavigateToLauncherSettings
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Launcher", fontWeight = FontWeight.Bold)
                        Text(
                            "Home screen pages, icons, dock, distraction scoring",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Filled.ChevronRight, "Navigate")
                }
            }

            // User Guide
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                onClick = onNavigateToAppGuide
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("User Guide", fontWeight = FontWeight.Bold)
                        Text(
                            "Start here: quick setup and daily workflow",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Filled.ChevronRight, "Navigate")
                }
            }

            // Privacy & Permissions
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                onClick = onNavigateToPrivacyPermissions
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Privacy & Permissions", fontWeight = FontWeight.Bold)
                        Text(
                            "Why the app asks for powerful Android permissions",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Filled.ChevronRight, "Navigate")
                }
            }

            // Appearance
            SettingsSection("Appearance") {
                Text("Theme", fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppTheme.entries.forEach { theme ->
                        FilterChip(
                            selected = prefs.theme == theme,
                            onClick = { viewModel.setTheme(theme) },
                            label = { Text(theme.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
            }

            // Data
            SettingsSection("Data") {
                OutlinedButton(
                    onClick = { showClearDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeuroFlowColors.TrackingRed)
                ) {
                    Icon(Icons.Filled.DeleteForever, "Clear")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear All Data")
                }
            }

            // About
            SettingsSection("About") {
                Text("proFlow v${BuildConfig.VERSION_NAME}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear All Data?") },
            text = {
                Text(
                    "This will permanently delete all app data on this device and reset backup state so old data is not restored after reinstall. This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllData()
                    showClearDialog = false
                }) { Text("Delete", color = NeuroFlowColors.TrackingRed) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showClearTelemetryDialog) {
        AlertDialog(
            onDismissRequest = { showClearTelemetryDialog = false },
            title = { Text("Clear Energy Telemetry?") },
            text = {
                Text("This removes saved energy predictions used for diagnostics and calibration. Live scoring will continue.")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearEnergyTelemetry()
                    showClearTelemetryDialog = false
                }) { Text("Clear", color = NeuroFlowColors.TrackingRed) }
            },
            dismissButton = {
                TextButton(onClick = { showClearTelemetryDialog = false }) { Text("Cancel") }
            }
        )
    }
}

private data class ManualProfilePreset(
    val label: String,
    val anchorMinuteOfDay: Int,
    val window1StartOffsetMinutes: Int,
    val window2StartOffsetMinutes: Int,
    val window3StartOffsetMinutes: Int,
    val window1DurationMinutes: Int,
    val window2DurationMinutes: Int,
    val window3DurationMinutes: Int,
    val window1Amplitude: Float,
    val window2Amplitude: Float,
    val window3Amplitude: Float
)

private fun manualProfilePresetForChronotype(
    chronotype: String?,
    wakeUpHour: Int
): ManualProfilePreset {
    val wakeMinute = wakeUpHour.coerceIn(0, 23) * 60
    fun anchored(offsetMinutes: Int): Int = (wakeMinute + offsetMinutes).mod(24 * 60)

    return when (chronotype) {
        "INTERMEDIATE" -> ManualProfilePreset(
            label = "Intermediate",
            anchorMinuteOfDay = anchored(150), // +2.5h
            window1StartOffsetMinutes = 0,
            window2StartOffsetMinutes = 480,
            window3StartOffsetMinutes = 720,
            window1DurationMinutes = 210,      // 3.5h
            window2DurationMinutes = 150,      // 2.5h
            window3DurationMinutes = 60,       // 1h
            window1Amplitude = 1.0f,
            window2Amplitude = 0.78f,
            window3Amplitude = 0.58f
        )

        "MODERATE_EVENING", "DEFINITE_EVENING" -> ManualProfilePreset(
            label = "Night Owl",
            anchorMinuteOfDay = anchored(330), // +5.5h
            window1StartOffsetMinutes = 0,
            window2StartOffsetMinutes = 330,
            window3StartOffsetMinutes = 600,
            window1DurationMinutes = 180,      // 3h
            window2DurationMinutes = 240,      // 4h
            window3DurationMinutes = 60,       // 1h
            window1Amplitude = 1.0f,
            window2Amplitude = 0.85f,
            window3Amplitude = 0.6f
        )

        else -> ManualProfilePreset(
            label = "Morning",
            anchorMinuteOfDay = anchored(0),
            window1StartOffsetMinutes = 0,
            window2StartOffsetMinutes = 570,
            window3StartOffsetMinutes = 810,
            window1DurationMinutes = 210,
            window2DurationMinutes = 150,
            window3DurationMinutes = 60,
            window1Amplitude = 1.0f,
            window2Amplitude = 0.8f,
            window3Amplitude = 0.6f
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SettingsCompactBox(
    title: String,
    collapsedByDefault: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable(title, collapsedByDefault) { mutableStateOf(!collapsedByDefault) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(2.dp))
                content()
            }
        }
    }
}

@Composable
private fun SettingsNumberRow(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    formatHour: Boolean = false,
    enabled: Boolean = true,
    onValueChange: (Int) -> Unit
) {
    val displayValue = if (formatHour) hourLabel(value) else "$value"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (enabled && value > min) onValueChange(value - 1) }, enabled = enabled, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Remove, "Decrease", modifier = Modifier.size(18.dp))
            }
            Text(displayValue, fontWeight = FontWeight.Bold, modifier = Modifier.width(52.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 13.sp, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = { if (enabled && value < max) onValueChange(value + 1) }, enabled = enabled, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Add, "Increase", modifier = Modifier.size(18.dp))
            }
        }
    }
}

private fun hourLabel(hour: Int) = when {
    hour == 0  -> "12 am"
    hour < 12  -> "$hour am"
    hour == 12 -> "12 pm"
    else       -> "${hour - 12} pm"
}

@Composable
private fun SettingsToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsFloatSliderRow(
    label: String,
    value: Float,
    enabled: Boolean = true,
    minValue: Float = 0.2f,
    maxValue: Float = 1f,
    onValueChange: (Float) -> Unit
) {
    val safe = value.coerceIn(minValue, maxValue)
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 14.sp, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(String.format("%.2f", safe), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = safe,
            enabled = enabled,
            onValueChange = { onValueChange(it.coerceIn(minValue, maxValue)) },
            valueRange = minValue..maxValue
        )
    }
}

@Composable
private fun ProfileWindowPreviewRow(
    label: String,
    startOffsetMinutes: Int,
    durationMinutes: Int,
    amplitude: Float
) {
    val widthWeight = durationMinutes.coerceIn(30, 360).toFloat() / 360f
    val alpha = amplitude.coerceIn(0.2f, 1f)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$label +${startOffsetMinutes}m", fontSize = 12.sp, modifier = Modifier.width(80.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(widthWeight)
                    .padding(end = 4.dp)
                    .height(8.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                    shape = RoundedCornerShape(4.dp)
                ) {}
            }
        }
        Text(String.format("%.2f", alpha), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
