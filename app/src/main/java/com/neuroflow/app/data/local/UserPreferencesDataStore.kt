package com.neuroflow.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.neuroflow.app.domain.scheduler.TaskTagSchedulingProfile
import com.neuroflow.app.domain.model.AppTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

data class UserPreferences(
    val wakeUpHour: Int = 7,
    val sleepHour: Int = 23,
    val peakEnergyStart: Int = 9,
    val peakEnergyEnd: Int = 12,
    val workDayStart: Int = 8,
    val workDayEnd: Int = 20,
    val defaultPomodoroMinutes: Int = 25,
    val defaultBreakMinutes: Int = 5,
    val identityLabel: String = "",
    val topGoal: String = "",
    val dailyStreak: Int = 0,
    val lastActiveDate: Long = 0L,
    val longestStreak: Int = 0,
    val totalTasksCompleted: Int = 0,
    val totalFocusMinutes: Int = 0,
    val weightQuadrant: Float = 1.0f,
    val weightDeadlineUrgency: Float = 1.0f,
    val weightPriorityLevel: Float = 1.0f,
    val weightDuration: Float = 1.0f,
    val weightImpact: Float = 1.0f,
    val weightFocusMode: Float = 1.0f,
    val onboardingCompleted: Boolean = false,
    val theme: AppTheme = AppTheme.SYSTEM,
    val weeklyIntent: String = "",
    val weeklyIntentIsoWeek: Int = 0,
    val weeklyIntentIsoYear: Int = 0,
    val lastFreshStartShownWeek: Int = 0,
    val lastFreshStartShownYear: Int = 0,
    val lastAppOpenMillis: Long = 0L,
    // Dynamic peak energy detection
    val detectedPeakStart: Int = -1,      // -1 = not yet detected
    val detectedPeakEnd: Int = -1,
    val detectedPeakMinuteOfDay: Int = -1,
    val peakDetectionConfidence: Float = 0f,  // 0.0–1.0
    // Blended effective peak (manual lerp'd with detected, based on confidence)
    val effectivePeakStart: Int = -1,     // -1 = use peakEnergyStart
    val effectivePeakEnd: Int = -1,       // -1 = use peakEnergyEnd
    val effectivePeakMinuteOfDay: Int = -1,
    val quizPeakEnabled: Boolean = true,
    // Subliminal affirmations — stored as JSON array string
    val affirmations: List<String> = emptyList(),
    // Persistent task tag catalog shown in task creation and history filters
    val tagCatalog: List<String> = emptyList(),
    // Auto scheduling controls
    val autoSchedulingEnabled: Boolean = true,
    val autoSchedulingHorizonDays: Int = 3,
    val autoSchedulingBreakAfterCognitiveMinutes: Int = 90,
    val autoSchedulingBreakDurationMinutes: Int = 15,
    val autoSchedulingBackgroundThrottleMinutes: Int = 30,
    val autoSchedulingRequiresReview: Boolean = true,
    val autoSchedulingMode: String = "BALANCED",
    val autoSchedulingBufferPercent: Int = 30,
    val autoSchedulingMaxTasksPerDay: Int = 0,
    val autoSchedulingMaxDeepWorkMinutesPerDay: Int = 0,
    val autoSchedulingProtectedRestStartMinute: Int = -1,
    val autoSchedulingProtectedRestEndMinute: Int = -1,
    val calendarIntegrationEnabled: Boolean = false,
    val calendarExportAcceptedSchedules: Boolean = false,
    // Top 3 goals for the year (JSON array)
    val yearlyGoals: List<String> = emptyList(),
    // Top 3 goals for the current week (JSON array)
    val weeklyGoals: List<String> = emptyList(),
    // Tracks when we last showed the yearly goals refill prompt
    val lastYearlyGoalShownYear: Int = 0,
    // Tracks when we last showed the weekly goals refill prompt (ISO week + year)
    val lastWeeklyGoalShownWeek: Int = 0,
    val lastWeeklyGoalShownYear: Int = 0,
    // Focus behaviour toggles
    val woopEnabled: Boolean = true,
    val autoTrackerEnabled: Boolean = false,
    // Notification preferences
    val dailyPlanNotificationsEnabled: Boolean = true,
    val streakNotificationsEnabled: Boolean = true,
    val autonomyNudgeNotificationsEnabled: Boolean = true,
    val deadlineReminderNotificationsEnabled: Boolean = true,
    val deadlineEscalationNotificationsEnabled: Boolean = true,
    val dailyPlanNotificationHour: Int = 7,
    val streakCheckNotificationHour: Int = 21,
    val userGuidePromptShown: Boolean = false,
    // Left page quick note
    val leftPageQuickNote: String = "",
    // MEQ Chronotype - manual user selection from onboarding
    val manualChronotype: String? = null,
    // MEQ Chronotype - result from completed quiz
    val quizChronotype: String? = null,
    // Quiz progress - JSON string storing which questions answered and selected answers
    val quizProgress: String = "{}",
    // Sleep pressure state (recomputed from logs + elapsed awake time)
    val sleepPressurePoints: Int = 0,
    val sleepPressureTrackingStartedAtMillis: Long = 0L,
    val sleepPressureLastComputedAtMillis: Long = 0L,
    val autoFallbackSleepInsertionEnabled: Boolean = true,
    // Moment model sensitivity controls
    val momentInterruptionSensitivity: Float = 1.0f,
    val momentNotificationSensitivity: Float = 1.0f,
    val momentTaskPressureSensitivity: Float = 1.0f,
    // Local telemetry privacy controls
    val energyTelemetryEnabled: Boolean = true,
    val energyTelemetryRetentionDays: Int = 30,
    // Morning calibration auto-tune coefficients (bounded adaptive blend)
    val morningTuneSleepWeight: Float = 0.30f,
    val morningTuneWakeWeight: Float = 0.25f,
    val morningTuneBehaviorWeight: Float = 0.25f,
    val morningTuneBaseWeight: Float = 0.20f,
    val morningTuneUpdatedAtMillis: Long = 0L,
    val morningTuneVersion: Int = 1,
    val adaptivePeakFreezeEnabled: Boolean = false,
    val peakQualityDegradeStreak: Int = 0,
    val peakConfidenceAbstentionEnabled: Boolean = false,
    val peakConfidenceAbstentionReason: String = "",
    val peakConfidenceAbstentionTriggerCount: Int = 0,
    val peakConfidenceAbstentionRecoveryCount: Int = 0,
    val peakConfidenceAbstentionLastChangedAtMillis: Long = 0L,
    val peakConfidenceAbstentionReasonFreezeCount: Int = 0,
    val peakConfidenceAbstentionReasonLowSamplesCount: Int = 0,
    val peakConfidenceAbstentionReasonLowCoverageCount: Int = 0,
    val peakConfidenceAbstentionReasonWakeVarianceCount: Int = 0,
    val peakConfidenceAbstentionReasonDivergenceCount: Int = 0,
    val peakConfidenceAbstentionReasonOtherCount: Int = 0,
    // Manual peak profile override controls
    val manualPeakProfileEnabled: Boolean = false,
    val manualPeakProfileType: String = "AUTO",
    val manualPeakAnchorMinuteOfDay: Int = 360,
    val manualPeakWindow1StartOffsetMinutes: Int = 0,
    val manualPeakWindow2StartOffsetMinutes: Int = 570,
    val manualPeakWindow3StartOffsetMinutes: Int = 810,
    val manualPeakWindow1DurationMinutes: Int = 210,
    val manualPeakWindow2DurationMinutes: Int = 150,
    val manualPeakWindow3DurationMinutes: Int = 60,
    val manualPeakWindow1Amplitude: Float = 1.0f,
    val manualPeakWindow2Amplitude: Float = 0.8f,
    val manualPeakWindow3Amplitude: Float = 0.6f
)

@Singleton
class UserPreferencesDataStore @Inject constructor(
    private val context: Context
) {
    private object Keys {
        val WAKE_UP_HOUR = intPreferencesKey("wake_up_hour")
        val SLEEP_HOUR = intPreferencesKey("sleep_hour")
        val PEAK_ENERGY_START = intPreferencesKey("peak_energy_start")
        val PEAK_ENERGY_END = intPreferencesKey("peak_energy_end")
        val WORK_DAY_START = intPreferencesKey("work_day_start")
        val WORK_DAY_END = intPreferencesKey("work_day_end")
        val DEFAULT_POMODORO_MINUTES = intPreferencesKey("default_pomodoro_minutes")
        val DEFAULT_BREAK_MINUTES = intPreferencesKey("default_break_minutes")
        val IDENTITY_LABEL = stringPreferencesKey("identity_label")
        val TOP_GOAL = stringPreferencesKey("top_goal")
        val DAILY_STREAK = intPreferencesKey("daily_streak")
        val LAST_ACTIVE_DATE = longPreferencesKey("last_active_date")
        val LONGEST_STREAK = intPreferencesKey("longest_streak")
        val TOTAL_TASKS_COMPLETED = intPreferencesKey("total_tasks_completed")
        val TOTAL_FOCUS_MINUTES = intPreferencesKey("total_focus_minutes")
        val WEIGHT_QUADRANT = floatPreferencesKey("weight_quadrant")
        val WEIGHT_DEADLINE_URGENCY = floatPreferencesKey("weight_deadline_urgency")
        val WEIGHT_PRIORITY_LEVEL = floatPreferencesKey("weight_priority_level")
        val WEIGHT_DURATION = floatPreferencesKey("weight_duration")
        val WEIGHT_IMPACT = floatPreferencesKey("weight_impact")
        val WEIGHT_FOCUS_MODE = floatPreferencesKey("weight_focus_mode")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val THEME = stringPreferencesKey("theme")
        val WEEKLY_INTENT = stringPreferencesKey("weekly_intent")
        val WEEKLY_INTENT_ISO_WEEK = intPreferencesKey("weekly_intent_iso_week")
        val WEEKLY_INTENT_ISO_YEAR = intPreferencesKey("weekly_intent_iso_year")
        val LAST_FRESH_START_SHOWN_WEEK = intPreferencesKey("last_fresh_start_shown_week")
        val LAST_FRESH_START_SHOWN_YEAR = intPreferencesKey("last_fresh_start_shown_year")
        val LAST_APP_OPEN_MILLIS = longPreferencesKey("last_app_open_millis")
        val DETECTED_PEAK_START = intPreferencesKey("detected_peak_start")
        val DETECTED_PEAK_END = intPreferencesKey("detected_peak_end")
        val DETECTED_PEAK_MINUTE_OF_DAY = intPreferencesKey("detected_peak_minute_of_day")
        val PEAK_DETECTION_CONFIDENCE = floatPreferencesKey("peak_detection_confidence")
        val EFFECTIVE_PEAK_START = intPreferencesKey("effective_peak_start")
        val EFFECTIVE_PEAK_END = intPreferencesKey("effective_peak_end")
        val EFFECTIVE_PEAK_MINUTE_OF_DAY = intPreferencesKey("effective_peak_minute_of_day")
        val QUIZ_PEAK_ENABLED = booleanPreferencesKey("quiz_peak_enabled")
        val AFFIRMATIONS = stringPreferencesKey("affirmations")
        val TAG_CATALOG = stringPreferencesKey("tag_catalog")
        val AUTO_SCHEDULING_ENABLED = booleanPreferencesKey("auto_scheduling_enabled")
        val AUTO_SCHEDULING_HORIZON_DAYS = intPreferencesKey("auto_scheduling_horizon_days")
        val AUTO_SCHEDULING_BREAK_AFTER_COGNITIVE_MINUTES = intPreferencesKey("auto_scheduling_break_after_cognitive_minutes")
        val AUTO_SCHEDULING_BREAK_DURATION_MINUTES = intPreferencesKey("auto_scheduling_break_duration_minutes")
        val AUTO_SCHEDULING_BACKGROUND_THROTTLE_MINUTES = intPreferencesKey("auto_scheduling_background_throttle_minutes")
        val AUTO_SCHEDULING_REQUIRES_REVIEW = booleanPreferencesKey("auto_scheduling_requires_review")
        val AUTO_SCHEDULING_MODE = stringPreferencesKey("auto_scheduling_mode")
        val AUTO_SCHEDULING_BUFFER_PERCENT = intPreferencesKey("auto_scheduling_buffer_percent")
        val AUTO_SCHEDULING_MAX_TASKS_PER_DAY = intPreferencesKey("auto_scheduling_max_tasks_per_day")
        val AUTO_SCHEDULING_MAX_DEEP_WORK_MINUTES_PER_DAY = intPreferencesKey("auto_scheduling_max_deep_work_minutes_per_day")
        val AUTO_SCHEDULING_PROTECTED_REST_START_MINUTE = intPreferencesKey("auto_scheduling_protected_rest_start_minute")
        val AUTO_SCHEDULING_PROTECTED_REST_END_MINUTE = intPreferencesKey("auto_scheduling_protected_rest_end_minute")
        val CALENDAR_INTEGRATION_ENABLED = booleanPreferencesKey("calendar_integration_enabled")
        val CALENDAR_EXPORT_ACCEPTED_SCHEDULES = booleanPreferencesKey("calendar_export_accepted_schedules")
        val YEARLY_GOALS = stringPreferencesKey("yearly_goals")
        val WEEKLY_GOALS = stringPreferencesKey("weekly_goals")
        val LAST_YEARLY_GOAL_SHOWN_YEAR = intPreferencesKey("last_yearly_goal_shown_year")
        val LAST_WEEKLY_GOAL_SHOWN_WEEK = intPreferencesKey("last_weekly_goal_shown_week")
        val LAST_WEEKLY_GOAL_SHOWN_YEAR = intPreferencesKey("last_weekly_goal_shown_year")
        val WOOP_ENABLED = booleanPreferencesKey("woop_enabled")
        val AUTO_TRACKER_ENABLED = booleanPreferencesKey("auto_tracker_enabled")
        val DAILY_PLAN_NOTIFICATIONS_ENABLED = booleanPreferencesKey("daily_plan_notifications_enabled")
        val STREAK_NOTIFICATIONS_ENABLED = booleanPreferencesKey("streak_notifications_enabled")
        val AUTONOMY_NUDGE_NOTIFICATIONS_ENABLED = booleanPreferencesKey("autonomy_nudge_notifications_enabled")
        val DEADLINE_REMINDER_NOTIFICATIONS_ENABLED = booleanPreferencesKey("deadline_reminder_notifications_enabled")
        val DEADLINE_ESCALATION_NOTIFICATIONS_ENABLED = booleanPreferencesKey("deadline_escalation_notifications_enabled")
        val DAILY_PLAN_NOTIFICATION_HOUR = intPreferencesKey("daily_plan_notification_hour")
        val STREAK_CHECK_NOTIFICATION_HOUR = intPreferencesKey("streak_check_notification_hour")
        val USER_GUIDE_PROMPT_SHOWN = booleanPreferencesKey("user_guide_prompt_shown")
        val LEFT_PAGE_QUICK_NOTE = stringPreferencesKey("left_page_quick_note")
        val MANUAL_CHRONOTYPE = stringPreferencesKey("manual_chronotype")
        val QUIZ_CHRONOTYPE = stringPreferencesKey("quiz_chronotype")
        val QUIZ_PROGRESS = stringPreferencesKey("quiz_progress")
        val SLEEP_PRESSURE_POINTS = intPreferencesKey("sleep_pressure_points")
        val SLEEP_PRESSURE_TRACKING_STARTED_AT = longPreferencesKey("sleep_pressure_tracking_started_at")
        val SLEEP_PRESSURE_LAST_COMPUTED_AT = longPreferencesKey("sleep_pressure_last_computed_at")
        val AUTO_FALLBACK_SLEEP_INSERTION_ENABLED = booleanPreferencesKey("auto_fallback_sleep_insertion_enabled")
        val MOMENT_INTERRUPTION_SENSITIVITY = floatPreferencesKey("moment_interruption_sensitivity")
        val MOMENT_NOTIFICATION_SENSITIVITY = floatPreferencesKey("moment_notification_sensitivity")
        val MOMENT_TASK_PRESSURE_SENSITIVITY = floatPreferencesKey("moment_task_pressure_sensitivity")
        val ENERGY_TELEMETRY_ENABLED = booleanPreferencesKey("energy_telemetry_enabled")
        val ENERGY_TELEMETRY_RETENTION_DAYS = intPreferencesKey("energy_telemetry_retention_days")
        val MORNING_TUNE_SLEEP_WEIGHT = floatPreferencesKey("morning_tune_sleep_weight")
        val MORNING_TUNE_WAKE_WEIGHT = floatPreferencesKey("morning_tune_wake_weight")
        val MORNING_TUNE_BEHAVIOR_WEIGHT = floatPreferencesKey("morning_tune_behavior_weight")
        val MORNING_TUNE_BASE_WEIGHT = floatPreferencesKey("morning_tune_base_weight")
        val MORNING_TUNE_UPDATED_AT = longPreferencesKey("morning_tune_updated_at")
        val MORNING_TUNE_VERSION = intPreferencesKey("morning_tune_version")
        val ADAPTIVE_PEAK_FREEZE_ENABLED = booleanPreferencesKey("adaptive_peak_freeze_enabled")
        val PEAK_QUALITY_DEGRADE_STREAK = intPreferencesKey("peak_quality_degrade_streak")
        val PEAK_CONFIDENCE_ABSTENTION_ENABLED = booleanPreferencesKey("peak_confidence_abstention_enabled")
        val PEAK_CONFIDENCE_ABSTENTION_REASON = stringPreferencesKey("peak_confidence_abstention_reason")
        val PEAK_CONFIDENCE_ABSTENTION_TRIGGER_COUNT = intPreferencesKey("peak_confidence_abstention_trigger_count")
        val PEAK_CONFIDENCE_ABSTENTION_RECOVERY_COUNT = intPreferencesKey("peak_confidence_abstention_recovery_count")
        val PEAK_CONFIDENCE_ABSTENTION_LAST_CHANGED_AT = longPreferencesKey("peak_confidence_abstention_last_changed_at")
        val PEAK_CONFIDENCE_ABSTENTION_REASON_FREEZE_COUNT = intPreferencesKey("peak_confidence_abstention_reason_freeze_count")
        val PEAK_CONFIDENCE_ABSTENTION_REASON_LOW_SAMPLES_COUNT = intPreferencesKey("peak_confidence_abstention_reason_low_samples_count")
        val PEAK_CONFIDENCE_ABSTENTION_REASON_LOW_COVERAGE_COUNT = intPreferencesKey("peak_confidence_abstention_reason_low_coverage_count")
        val PEAK_CONFIDENCE_ABSTENTION_REASON_WAKE_VARIANCE_COUNT = intPreferencesKey("peak_confidence_abstention_reason_wake_variance_count")
        val PEAK_CONFIDENCE_ABSTENTION_REASON_DIVERGENCE_COUNT = intPreferencesKey("peak_confidence_abstention_reason_divergence_count")
        val PEAK_CONFIDENCE_ABSTENTION_REASON_OTHER_COUNT = intPreferencesKey("peak_confidence_abstention_reason_other_count")
        val MANUAL_PEAK_PROFILE_ENABLED = booleanPreferencesKey("manual_peak_profile_enabled")
        val MANUAL_PEAK_PROFILE_TYPE = stringPreferencesKey("manual_peak_profile_type")
        val MANUAL_PEAK_ANCHOR_MINUTE_OF_DAY = intPreferencesKey("manual_peak_anchor_minute_of_day")
        val MANUAL_PEAK_WINDOW_1_START_OFFSET = intPreferencesKey("manual_peak_window_1_start_offset")
        val MANUAL_PEAK_WINDOW_2_START_OFFSET = intPreferencesKey("manual_peak_window_2_start_offset")
        val MANUAL_PEAK_WINDOW_3_START_OFFSET = intPreferencesKey("manual_peak_window_3_start_offset")
        val MANUAL_PEAK_WINDOW_1_DURATION = intPreferencesKey("manual_peak_window_1_duration")
        val MANUAL_PEAK_WINDOW_2_DURATION = intPreferencesKey("manual_peak_window_2_duration")
        val MANUAL_PEAK_WINDOW_3_DURATION = intPreferencesKey("manual_peak_window_3_duration")
        val MANUAL_PEAK_WINDOW_1_AMPLITUDE = floatPreferencesKey("manual_peak_window_1_amplitude")
        val MANUAL_PEAK_WINDOW_2_AMPLITUDE = floatPreferencesKey("manual_peak_window_2_amplitude")
        val MANUAL_PEAK_WINDOW_3_AMPLITUDE = floatPreferencesKey("manual_peak_window_3_amplitude")
    }

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            wakeUpHour = prefs[Keys.WAKE_UP_HOUR] ?: 7,
            sleepHour = prefs[Keys.SLEEP_HOUR] ?: 23,
            peakEnergyStart = prefs[Keys.PEAK_ENERGY_START] ?: 9,
            peakEnergyEnd = prefs[Keys.PEAK_ENERGY_END] ?: 12,
            workDayStart = prefs[Keys.WORK_DAY_START] ?: 8,
            workDayEnd = prefs[Keys.WORK_DAY_END] ?: 20,
            defaultPomodoroMinutes = prefs[Keys.DEFAULT_POMODORO_MINUTES] ?: 25,
            defaultBreakMinutes = prefs[Keys.DEFAULT_BREAK_MINUTES] ?: 5,
            identityLabel = prefs[Keys.IDENTITY_LABEL] ?: "",
            topGoal = prefs[Keys.TOP_GOAL] ?: "",
            dailyStreak = prefs[Keys.DAILY_STREAK] ?: 0,
            lastActiveDate = prefs[Keys.LAST_ACTIVE_DATE] ?: 0L,
            longestStreak = prefs[Keys.LONGEST_STREAK] ?: 0,
            totalTasksCompleted = prefs[Keys.TOTAL_TASKS_COMPLETED] ?: 0,
            totalFocusMinutes = prefs[Keys.TOTAL_FOCUS_MINUTES] ?: 0,
            weightQuadrant = prefs[Keys.WEIGHT_QUADRANT] ?: 1.0f,
            weightDeadlineUrgency = prefs[Keys.WEIGHT_DEADLINE_URGENCY] ?: 1.0f,
            weightPriorityLevel = prefs[Keys.WEIGHT_PRIORITY_LEVEL] ?: 1.0f,
            weightDuration = prefs[Keys.WEIGHT_DURATION] ?: 1.0f,
            weightImpact = prefs[Keys.WEIGHT_IMPACT] ?: 1.0f,
            weightFocusMode = prefs[Keys.WEIGHT_FOCUS_MODE] ?: 1.0f,
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            theme = try {
                AppTheme.valueOf(prefs[Keys.THEME] ?: AppTheme.SYSTEM.name)
            } catch (_: Exception) { AppTheme.SYSTEM },
            weeklyIntent = prefs[Keys.WEEKLY_INTENT] ?: "",
            weeklyIntentIsoWeek = prefs[Keys.WEEKLY_INTENT_ISO_WEEK] ?: 0,
            weeklyIntentIsoYear = prefs[Keys.WEEKLY_INTENT_ISO_YEAR] ?: 0,
            lastFreshStartShownWeek = prefs[Keys.LAST_FRESH_START_SHOWN_WEEK] ?: 0,
            lastFreshStartShownYear = prefs[Keys.LAST_FRESH_START_SHOWN_YEAR] ?: 0,
            lastAppOpenMillis = prefs[Keys.LAST_APP_OPEN_MILLIS] ?: 0L,
            detectedPeakStart = prefs[Keys.DETECTED_PEAK_START] ?: -1,
            detectedPeakEnd = prefs[Keys.DETECTED_PEAK_END] ?: -1,
            detectedPeakMinuteOfDay = prefs[Keys.DETECTED_PEAK_MINUTE_OF_DAY] ?: -1,
            peakDetectionConfidence = prefs[Keys.PEAK_DETECTION_CONFIDENCE] ?: 0f,
            effectivePeakStart = prefs[Keys.EFFECTIVE_PEAK_START] ?: -1,
            effectivePeakEnd = prefs[Keys.EFFECTIVE_PEAK_END] ?: -1,
            effectivePeakMinuteOfDay = prefs[Keys.EFFECTIVE_PEAK_MINUTE_OF_DAY] ?: -1,
            quizPeakEnabled = prefs[Keys.QUIZ_PEAK_ENABLED] ?: true,
            affirmations = parseAffirmations(prefs[Keys.AFFIRMATIONS]),
            tagCatalog = withStarterTagCatalog(parseAffirmations(prefs[Keys.TAG_CATALOG])),
            autoSchedulingEnabled = prefs[Keys.AUTO_SCHEDULING_ENABLED] ?: true,
            autoSchedulingHorizonDays = (prefs[Keys.AUTO_SCHEDULING_HORIZON_DAYS] ?: 3).coerceIn(1, 7),
            autoSchedulingBreakAfterCognitiveMinutes = (prefs[Keys.AUTO_SCHEDULING_BREAK_AFTER_COGNITIVE_MINUTES] ?: 90).coerceIn(30, 180),
            autoSchedulingBreakDurationMinutes = (prefs[Keys.AUTO_SCHEDULING_BREAK_DURATION_MINUTES] ?: 15).coerceIn(5, 30),
            autoSchedulingBackgroundThrottleMinutes = (prefs[Keys.AUTO_SCHEDULING_BACKGROUND_THROTTLE_MINUTES] ?: 30).coerceIn(5, 120),
            autoSchedulingRequiresReview = prefs[Keys.AUTO_SCHEDULING_REQUIRES_REVIEW] ?: true,
            yearlyGoals = parseAffirmations(prefs[Keys.YEARLY_GOALS]),
            weeklyGoals = parseAffirmations(prefs[Keys.WEEKLY_GOALS]),
            lastYearlyGoalShownYear = prefs[Keys.LAST_YEARLY_GOAL_SHOWN_YEAR] ?: 0,
            lastWeeklyGoalShownWeek = prefs[Keys.LAST_WEEKLY_GOAL_SHOWN_WEEK] ?: 0,
            lastWeeklyGoalShownYear = prefs[Keys.LAST_WEEKLY_GOAL_SHOWN_YEAR] ?: 0,
            woopEnabled = prefs[Keys.WOOP_ENABLED] ?: true,
            autoTrackerEnabled = prefs[Keys.AUTO_TRACKER_ENABLED] ?: false,
            dailyPlanNotificationsEnabled = prefs[Keys.DAILY_PLAN_NOTIFICATIONS_ENABLED] ?: true,
            streakNotificationsEnabled = prefs[Keys.STREAK_NOTIFICATIONS_ENABLED] ?: true,
            autonomyNudgeNotificationsEnabled = prefs[Keys.AUTONOMY_NUDGE_NOTIFICATIONS_ENABLED] ?: true,
            deadlineReminderNotificationsEnabled = prefs[Keys.DEADLINE_REMINDER_NOTIFICATIONS_ENABLED] ?: true,
            deadlineEscalationNotificationsEnabled = prefs[Keys.DEADLINE_ESCALATION_NOTIFICATIONS_ENABLED] ?: true,
            dailyPlanNotificationHour = prefs[Keys.DAILY_PLAN_NOTIFICATION_HOUR] ?: 7,
            streakCheckNotificationHour = prefs[Keys.STREAK_CHECK_NOTIFICATION_HOUR] ?: 21,
            userGuidePromptShown = prefs[Keys.USER_GUIDE_PROMPT_SHOWN] ?: false,
            leftPageQuickNote = prefs[Keys.LEFT_PAGE_QUICK_NOTE] ?: "",
            manualChronotype = prefs[Keys.MANUAL_CHRONOTYPE],
            quizChronotype = prefs[Keys.QUIZ_CHRONOTYPE],
            quizProgress = prefs[Keys.QUIZ_PROGRESS] ?: "{}",
            sleepPressurePoints = prefs[Keys.SLEEP_PRESSURE_POINTS] ?: 0,
            sleepPressureTrackingStartedAtMillis = prefs[Keys.SLEEP_PRESSURE_TRACKING_STARTED_AT] ?: 0L,
            sleepPressureLastComputedAtMillis = prefs[Keys.SLEEP_PRESSURE_LAST_COMPUTED_AT] ?: 0L,
            autoFallbackSleepInsertionEnabled = prefs[Keys.AUTO_FALLBACK_SLEEP_INSERTION_ENABLED] ?: true,
            momentInterruptionSensitivity = (prefs[Keys.MOMENT_INTERRUPTION_SENSITIVITY] ?: 1.0f).coerceIn(0.5f, 2.0f),
            momentNotificationSensitivity = (prefs[Keys.MOMENT_NOTIFICATION_SENSITIVITY] ?: 1.0f).coerceIn(0.5f, 2.0f),
            momentTaskPressureSensitivity = (prefs[Keys.MOMENT_TASK_PRESSURE_SENSITIVITY] ?: 1.0f).coerceIn(0.5f, 2.0f),
            energyTelemetryEnabled = prefs[Keys.ENERGY_TELEMETRY_ENABLED] ?: true,
            energyTelemetryRetentionDays = (prefs[Keys.ENERGY_TELEMETRY_RETENTION_DAYS] ?: 30).coerceIn(1, 90),
            morningTuneSleepWeight = prefs[Keys.MORNING_TUNE_SLEEP_WEIGHT] ?: 0.30f,
            morningTuneWakeWeight = prefs[Keys.MORNING_TUNE_WAKE_WEIGHT] ?: 0.25f,
            morningTuneBehaviorWeight = prefs[Keys.MORNING_TUNE_BEHAVIOR_WEIGHT] ?: 0.25f,
            morningTuneBaseWeight = prefs[Keys.MORNING_TUNE_BASE_WEIGHT] ?: 0.20f,
            morningTuneUpdatedAtMillis = prefs[Keys.MORNING_TUNE_UPDATED_AT] ?: 0L,
            morningTuneVersion = prefs[Keys.MORNING_TUNE_VERSION] ?: 1,
            adaptivePeakFreezeEnabled = prefs[Keys.ADAPTIVE_PEAK_FREEZE_ENABLED] ?: false,
            peakQualityDegradeStreak = prefs[Keys.PEAK_QUALITY_DEGRADE_STREAK] ?: 0,
            peakConfidenceAbstentionEnabled = prefs[Keys.PEAK_CONFIDENCE_ABSTENTION_ENABLED] ?: false,
            peakConfidenceAbstentionReason = prefs[Keys.PEAK_CONFIDENCE_ABSTENTION_REASON] ?: "",
            peakConfidenceAbstentionTriggerCount = (prefs[Keys.PEAK_CONFIDENCE_ABSTENTION_TRIGGER_COUNT] ?: 0).coerceAtLeast(0),
            peakConfidenceAbstentionRecoveryCount = (prefs[Keys.PEAK_CONFIDENCE_ABSTENTION_RECOVERY_COUNT] ?: 0).coerceAtLeast(0),
            peakConfidenceAbstentionLastChangedAtMillis = prefs[Keys.PEAK_CONFIDENCE_ABSTENTION_LAST_CHANGED_AT] ?: 0L,
            peakConfidenceAbstentionReasonFreezeCount = (prefs[Keys.PEAK_CONFIDENCE_ABSTENTION_REASON_FREEZE_COUNT] ?: 0).coerceAtLeast(0),
            peakConfidenceAbstentionReasonLowSamplesCount = (prefs[Keys.PEAK_CONFIDENCE_ABSTENTION_REASON_LOW_SAMPLES_COUNT] ?: 0).coerceAtLeast(0),
            peakConfidenceAbstentionReasonLowCoverageCount = (prefs[Keys.PEAK_CONFIDENCE_ABSTENTION_REASON_LOW_COVERAGE_COUNT] ?: 0).coerceAtLeast(0),
            peakConfidenceAbstentionReasonWakeVarianceCount = (prefs[Keys.PEAK_CONFIDENCE_ABSTENTION_REASON_WAKE_VARIANCE_COUNT] ?: 0).coerceAtLeast(0),
            peakConfidenceAbstentionReasonDivergenceCount = (prefs[Keys.PEAK_CONFIDENCE_ABSTENTION_REASON_DIVERGENCE_COUNT] ?: 0).coerceAtLeast(0),
            peakConfidenceAbstentionReasonOtherCount = (prefs[Keys.PEAK_CONFIDENCE_ABSTENTION_REASON_OTHER_COUNT] ?: 0).coerceAtLeast(0),
            manualPeakProfileEnabled = prefs[Keys.MANUAL_PEAK_PROFILE_ENABLED] ?: false,
            manualPeakProfileType = prefs[Keys.MANUAL_PEAK_PROFILE_TYPE] ?: "AUTO",
            manualPeakAnchorMinuteOfDay = prefs[Keys.MANUAL_PEAK_ANCHOR_MINUTE_OF_DAY] ?: 360,
            manualPeakWindow1StartOffsetMinutes = prefs[Keys.MANUAL_PEAK_WINDOW_1_START_OFFSET] ?: 0,
            manualPeakWindow2StartOffsetMinutes = prefs[Keys.MANUAL_PEAK_WINDOW_2_START_OFFSET] ?: 570,
            manualPeakWindow3StartOffsetMinutes = prefs[Keys.MANUAL_PEAK_WINDOW_3_START_OFFSET] ?: 810,
            manualPeakWindow1DurationMinutes = prefs[Keys.MANUAL_PEAK_WINDOW_1_DURATION] ?: 210,
            manualPeakWindow2DurationMinutes = prefs[Keys.MANUAL_PEAK_WINDOW_2_DURATION] ?: 150,
            manualPeakWindow3DurationMinutes = prefs[Keys.MANUAL_PEAK_WINDOW_3_DURATION] ?: 60,
            manualPeakWindow1Amplitude = prefs[Keys.MANUAL_PEAK_WINDOW_1_AMPLITUDE] ?: 1.0f,
            manualPeakWindow2Amplitude = prefs[Keys.MANUAL_PEAK_WINDOW_2_AMPLITUDE] ?: 0.8f,
            manualPeakWindow3Amplitude = prefs[Keys.MANUAL_PEAK_WINDOW_3_AMPLITUDE] ?: 0.6f
        )
    }

    private fun parseAffirmations(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            List(arr.length()) { arr.getString(it) }
        } catch (_: Exception) { emptyList() }
    }

    private fun encodeAffirmations(list: List<String>): String {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        return arr.toString()
    }

    suspend fun updatePreferences(update: (UserPreferences) -> UserPreferences) {
        context.dataStore.edit { prefs ->
            val current = UserPreferences(
                wakeUpHour = prefs[Keys.WAKE_UP_HOUR] ?: 7,
                sleepHour = prefs[Keys.SLEEP_HOUR] ?: 23,
                peakEnergyStart = prefs[Keys.PEAK_ENERGY_START] ?: 9,
                peakEnergyEnd = prefs[Keys.PEAK_ENERGY_END] ?: 12,
                workDayStart = prefs[Keys.WORK_DAY_START] ?: 8,
                workDayEnd = prefs[Keys.WORK_DAY_END] ?: 20,
                defaultPomodoroMinutes = prefs[Keys.DEFAULT_POMODORO_MINUTES] ?: 25,
                defaultBreakMinutes = prefs[Keys.DEFAULT_BREAK_MINUTES] ?: 5,
                identityLabel = prefs[Keys.IDENTITY_LABEL] ?: "",
                topGoal = prefs[Keys.TOP_GOAL] ?: "",
                dailyStreak = prefs[Keys.DAILY_STREAK] ?: 0,
                lastActiveDate = prefs[Keys.LAST_ACTIVE_DATE] ?: 0L,
                longestStreak = prefs[Keys.LONGEST_STREAK] ?: 0,
                totalTasksCompleted = prefs[Keys.TOTAL_TASKS_COMPLETED] ?: 0,
                totalFocusMinutes = prefs[Keys.TOTAL_FOCUS_MINUTES] ?: 0,
                weightQuadrant = prefs[Keys.WEIGHT_QUADRANT] ?: 1.0f,
                weightDeadlineUrgency = prefs[Keys.WEIGHT_DEADLINE_URGENCY] ?: 1.0f,
                weightPriorityLevel = prefs[Keys.WEIGHT_PRIORITY_LEVEL] ?: 1.0f,
                weightDuration = prefs[Keys.WEIGHT_DURATION] ?: 1.0f,
                weightImpact = prefs[Keys.WEIGHT_IMPACT] ?: 1.0f,
                weightFocusMode = prefs[Keys.WEIGHT_FOCUS_MODE] ?: 1.0f,
                onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
                theme = try {
                    AppTheme.valueOf(prefs[Keys.THEME] ?: AppTheme.SYSTEM.name)
                } catch (_: Exception) { AppTheme.SYSTEM },
                weeklyIntent = prefs[Keys.WEEKLY_INTENT] ?: "",
                weeklyIntentIsoWeek = prefs[Keys.WEEKLY_INTENT_ISO_WEEK] ?: 0,
                weeklyIntentIsoYear = prefs[Keys.WEEKLY_INTENT_ISO_YEAR] ?: 0,
                lastFreshStartShownWeek = prefs[Keys.LAST_FRESH_START_SHOWN_WEEK] ?: 0,
                lastFreshStartShownYear = prefs[Keys.LAST_FRESH_START_SHOWN_YEAR] ?: 0,
                lastAppOpenMillis = prefs[Keys.LAST_APP_OPEN_MILLIS] ?: 0L,
                detectedPeakStart = prefs[Keys.DETECTED_PEAK_START] ?: -1,
                detectedPeakEnd = prefs[Keys.DETECTED_PEAK_END] ?: -1,
                detectedPeakMinuteOfDay = prefs[Keys.DETECTED_PEAK_MINUTE_OF_DAY] ?: -1,
                peakDetectionConfidence = prefs[Keys.PEAK_DETECTION_CONFIDENCE] ?: 0f,
                effectivePeakStart = prefs[Keys.EFFECTIVE_PEAK_START] ?: -1,
                effectivePeakEnd = prefs[Keys.EFFECTIVE_PEAK_END] ?: -1,
                effectivePeakMinuteOfDay = prefs[Keys.EFFECTIVE_PEAK_MINUTE_OF_DAY] ?: -1,
                quizPeakEnabled = prefs[Keys.QUIZ_PEAK_ENABLED] ?: true,
                affirmations = parseAffirmations(prefs[Keys.AFFIRMATIONS]),
                tagCatalog = withStarterTagCatalog(parseAffirmations(prefs[Keys.TAG_CATALOG])),
                autoSchedulingEnabled = prefs[Keys.AUTO_SCHEDULING_ENABLED] ?: true,
                autoSchedulingHorizonDays = (prefs[Keys.AUTO_SCHEDULING_HORIZON_DAYS] ?: 3).coerceIn(1, 7),
                autoSchedulingBreakAfterCognitiveMinutes = (prefs[Keys.AUTO_SCHEDULING_BREAK_AFTER_COGNITIVE_MINUTES] ?: 90).coerceIn(30, 180),
                autoSchedulingBreakDurationMinutes = (prefs[Keys.AUTO_SCHEDULING_BREAK_DURATION_MINUTES] ?: 15).coerceIn(5, 30),
                autoSchedulingBackgroundThrottleMinutes = (prefs[Keys.AUTO_SCHEDULING_BACKGROUND_THROTTLE_MINUTES] ?: 30).coerceIn(5, 120),
                autoSchedulingRequiresReview = prefs[Keys.AUTO_SCHEDULING_REQUIRES_REVIEW] ?: true,
                autoSchedulingMode = prefs[Keys.AUTO_SCHEDULING_MODE] ?: "BALANCED",
                autoSchedulingBufferPercent = (prefs[Keys.AUTO_SCHEDULING_BUFFER_PERCENT] ?: 30).coerceIn(0, 80),
                autoSchedulingMaxTasksPerDay = (prefs[Keys.AUTO_SCHEDULING_MAX_TASKS_PER_DAY] ?: 0).coerceAtLeast(0),
                autoSchedulingMaxDeepWorkMinutesPerDay = (prefs[Keys.AUTO_SCHEDULING_MAX_DEEP_WORK_MINUTES_PER_DAY] ?: 0).coerceAtLeast(0),
                autoSchedulingProtectedRestStartMinute = (prefs[Keys.AUTO_SCHEDULING_PROTECTED_REST_START_MINUTE] ?: -1).coerceIn(-1, 1_439),
                autoSchedulingProtectedRestEndMinute = (prefs[Keys.AUTO_SCHEDULING_PROTECTED_REST_END_MINUTE] ?: -1).coerceIn(-1, 1_440),
                calendarIntegrationEnabled = prefs[Keys.CALENDAR_INTEGRATION_ENABLED] ?: false,
                calendarExportAcceptedSchedules = prefs[Keys.CALENDAR_EXPORT_ACCEPTED_SCHEDULES] ?: false,
                yearlyGoals = parseAffirmations(prefs[Keys.YEARLY_GOALS]),
                weeklyGoals = parseAffirmations(prefs[Keys.WEEKLY_GOALS]),
                lastYearlyGoalShownYear = prefs[Keys.LAST_YEARLY_GOAL_SHOWN_YEAR] ?: 0,
                lastWeeklyGoalShownWeek = prefs[Keys.LAST_WEEKLY_GOAL_SHOWN_WEEK] ?: 0,
                lastWeeklyGoalShownYear = prefs[Keys.LAST_WEEKLY_GOAL_SHOWN_YEAR] ?: 0,
                woopEnabled = prefs[Keys.WOOP_ENABLED] ?: true,
                autoTrackerEnabled = prefs[Keys.AUTO_TRACKER_ENABLED] ?: false,
                dailyPlanNotificationsEnabled = prefs[Keys.DAILY_PLAN_NOTIFICATIONS_ENABLED] ?: true,
                streakNotificationsEnabled = prefs[Keys.STREAK_NOTIFICATIONS_ENABLED] ?: true,
                autonomyNudgeNotificationsEnabled = prefs[Keys.AUTONOMY_NUDGE_NOTIFICATIONS_ENABLED] ?: true,
                deadlineReminderNotificationsEnabled = prefs[Keys.DEADLINE_REMINDER_NOTIFICATIONS_ENABLED] ?: true,
                deadlineEscalationNotificationsEnabled = prefs[Keys.DEADLINE_ESCALATION_NOTIFICATIONS_ENABLED] ?: true,
                dailyPlanNotificationHour = prefs[Keys.DAILY_PLAN_NOTIFICATION_HOUR] ?: 7,
                streakCheckNotificationHour = prefs[Keys.STREAK_CHECK_NOTIFICATION_HOUR] ?: 21,
                userGuidePromptShown = prefs[Keys.USER_GUIDE_PROMPT_SHOWN] ?: false,
                leftPageQuickNote = prefs[Keys.LEFT_PAGE_QUICK_NOTE] ?: "",
                manualChronotype = prefs[Keys.MANUAL_CHRONOTYPE],
                quizChronotype = prefs[Keys.QUIZ_CHRONOTYPE],
                quizProgress = prefs[Keys.QUIZ_PROGRESS] ?: "{}",
                sleepPressurePoints = prefs[Keys.SLEEP_PRESSURE_POINTS] ?: 0,
                sleepPressureTrackingStartedAtMillis = prefs[Keys.SLEEP_PRESSURE_TRACKING_STARTED_AT] ?: 0L,
                sleepPressureLastComputedAtMillis = prefs[Keys.SLEEP_PRESSURE_LAST_COMPUTED_AT] ?: 0L,
                autoFallbackSleepInsertionEnabled = prefs[Keys.AUTO_FALLBACK_SLEEP_INSERTION_ENABLED] ?: true,
                momentInterruptionSensitivity = (prefs[Keys.MOMENT_INTERRUPTION_SENSITIVITY] ?: 1.0f).coerceIn(0.5f, 2.0f),
                momentNotificationSensitivity = (prefs[Keys.MOMENT_NOTIFICATION_SENSITIVITY] ?: 1.0f).coerceIn(0.5f, 2.0f),
                momentTaskPressureSensitivity = (prefs[Keys.MOMENT_TASK_PRESSURE_SENSITIVITY] ?: 1.0f).coerceIn(0.5f, 2.0f),
                energyTelemetryEnabled = prefs[Keys.ENERGY_TELEMETRY_ENABLED] ?: true,
                energyTelemetryRetentionDays = (prefs[Keys.ENERGY_TELEMETRY_RETENTION_DAYS] ?: 30).coerceIn(1, 90),
                morningTuneSleepWeight = prefs[Keys.MORNING_TUNE_SLEEP_WEIGHT] ?: 0.30f,
                morningTuneWakeWeight = prefs[Keys.MORNING_TUNE_WAKE_WEIGHT] ?: 0.25f,
                morningTuneBehaviorWeight = prefs[Keys.MORNING_TUNE_BEHAVIOR_WEIGHT] ?: 0.25f,
                morningTuneBaseWeight = prefs[Keys.MORNING_TUNE_BASE_WEIGHT] ?: 0.20f,
                morningTuneUpdatedAtMillis = prefs[Keys.MORNING_TUNE_UPDATED_AT] ?: 0L,
                morningTuneVersion = prefs[Keys.MORNING_TUNE_VERSION] ?: 1,
                adaptivePeakFreezeEnabled = prefs[Keys.ADAPTIVE_PEAK_FREEZE_ENABLED] ?: false,
                peakQualityDegradeStreak = prefs[Keys.PEAK_QUALITY_DEGRADE_STREAK] ?: 0,
                peakConfidenceAbstentionEnabled = prefs[Keys.PEAK_CONFIDENCE_ABSTENTION_ENABLED] ?: false,
                peakConfidenceAbstentionReason = prefs[Keys.PEAK_CONFIDENCE_ABSTENTION_REASON] ?: "",
                peakConfidenceAbstentionTriggerCount = (prefs[Keys.PEAK_CONFIDENCE_ABSTENTION_TRIGGER_COUNT] ?: 0).coerceAtLeast(0),
                peakConfidenceAbstentionRecoveryCount = (prefs[Keys.PEAK_CONFIDENCE_ABSTENTION_RECOVERY_COUNT] ?: 0).coerceAtLeast(0),
                peakConfidenceAbstentionLastChangedAtMillis = prefs[Keys.PEAK_CONFIDENCE_ABSTENTION_LAST_CHANGED_AT] ?: 0L,
                peakConfidenceAbstentionReasonFreezeCount = (prefs[Keys.PEAK_CONFIDENCE_ABSTENTION_REASON_FREEZE_COUNT] ?: 0).coerceAtLeast(0),
                peakConfidenceAbstentionReasonLowSamplesCount = (prefs[Keys.PEAK_CONFIDENCE_ABSTENTION_REASON_LOW_SAMPLES_COUNT] ?: 0).coerceAtLeast(0),
                peakConfidenceAbstentionReasonLowCoverageCount = (prefs[Keys.PEAK_CONFIDENCE_ABSTENTION_REASON_LOW_COVERAGE_COUNT] ?: 0).coerceAtLeast(0),
                peakConfidenceAbstentionReasonWakeVarianceCount = (prefs[Keys.PEAK_CONFIDENCE_ABSTENTION_REASON_WAKE_VARIANCE_COUNT] ?: 0).coerceAtLeast(0),
                peakConfidenceAbstentionReasonDivergenceCount = (prefs[Keys.PEAK_CONFIDENCE_ABSTENTION_REASON_DIVERGENCE_COUNT] ?: 0).coerceAtLeast(0),
                peakConfidenceAbstentionReasonOtherCount = (prefs[Keys.PEAK_CONFIDENCE_ABSTENTION_REASON_OTHER_COUNT] ?: 0).coerceAtLeast(0),
                manualPeakProfileEnabled = prefs[Keys.MANUAL_PEAK_PROFILE_ENABLED] ?: false,
                manualPeakProfileType = prefs[Keys.MANUAL_PEAK_PROFILE_TYPE] ?: "AUTO",
                manualPeakAnchorMinuteOfDay = prefs[Keys.MANUAL_PEAK_ANCHOR_MINUTE_OF_DAY] ?: 360,
                manualPeakWindow1StartOffsetMinutes = prefs[Keys.MANUAL_PEAK_WINDOW_1_START_OFFSET] ?: 0,
                manualPeakWindow2StartOffsetMinutes = prefs[Keys.MANUAL_PEAK_WINDOW_2_START_OFFSET] ?: 570,
                manualPeakWindow3StartOffsetMinutes = prefs[Keys.MANUAL_PEAK_WINDOW_3_START_OFFSET] ?: 810,
                manualPeakWindow1DurationMinutes = prefs[Keys.MANUAL_PEAK_WINDOW_1_DURATION] ?: 210,
                manualPeakWindow2DurationMinutes = prefs[Keys.MANUAL_PEAK_WINDOW_2_DURATION] ?: 150,
                manualPeakWindow3DurationMinutes = prefs[Keys.MANUAL_PEAK_WINDOW_3_DURATION] ?: 60,
                manualPeakWindow1Amplitude = prefs[Keys.MANUAL_PEAK_WINDOW_1_AMPLITUDE] ?: 1.0f,
                manualPeakWindow2Amplitude = prefs[Keys.MANUAL_PEAK_WINDOW_2_AMPLITUDE] ?: 0.8f,
                manualPeakWindow3Amplitude = prefs[Keys.MANUAL_PEAK_WINDOW_3_AMPLITUDE] ?: 0.6f
            )
            val updated = update(current)
            prefs[Keys.WAKE_UP_HOUR] = updated.wakeUpHour
            prefs[Keys.SLEEP_HOUR] = updated.sleepHour.coerceIn(0, 23)
            prefs[Keys.PEAK_ENERGY_START] = updated.peakEnergyStart
            prefs[Keys.PEAK_ENERGY_END] = updated.peakEnergyEnd
            prefs[Keys.WORK_DAY_START] = updated.workDayStart
            prefs[Keys.WORK_DAY_END] = updated.workDayEnd
            prefs[Keys.DEFAULT_POMODORO_MINUTES] = updated.defaultPomodoroMinutes
            prefs[Keys.DEFAULT_BREAK_MINUTES] = updated.defaultBreakMinutes
            prefs[Keys.IDENTITY_LABEL] = updated.identityLabel
            prefs[Keys.TOP_GOAL] = updated.topGoal
            prefs[Keys.DAILY_STREAK] = updated.dailyStreak
            prefs[Keys.LAST_ACTIVE_DATE] = updated.lastActiveDate
            prefs[Keys.LONGEST_STREAK] = updated.longestStreak
            prefs[Keys.TOTAL_TASKS_COMPLETED] = updated.totalTasksCompleted
            prefs[Keys.TOTAL_FOCUS_MINUTES] = updated.totalFocusMinutes
            prefs[Keys.WEIGHT_QUADRANT] = updated.weightQuadrant
            prefs[Keys.WEIGHT_DEADLINE_URGENCY] = updated.weightDeadlineUrgency
            prefs[Keys.WEIGHT_PRIORITY_LEVEL] = updated.weightPriorityLevel
            prefs[Keys.WEIGHT_DURATION] = updated.weightDuration
            prefs[Keys.WEIGHT_IMPACT] = updated.weightImpact
            prefs[Keys.WEIGHT_FOCUS_MODE] = updated.weightFocusMode
            prefs[Keys.ONBOARDING_COMPLETED] = updated.onboardingCompleted
            prefs[Keys.THEME] = updated.theme.name
            prefs[Keys.WEEKLY_INTENT] = updated.weeklyIntent
            prefs[Keys.WEEKLY_INTENT_ISO_WEEK] = updated.weeklyIntentIsoWeek
            prefs[Keys.WEEKLY_INTENT_ISO_YEAR] = updated.weeklyIntentIsoYear
            prefs[Keys.LAST_FRESH_START_SHOWN_WEEK] = updated.lastFreshStartShownWeek
            prefs[Keys.LAST_FRESH_START_SHOWN_YEAR] = updated.lastFreshStartShownYear
            prefs[Keys.LAST_APP_OPEN_MILLIS] = updated.lastAppOpenMillis
            prefs[Keys.DETECTED_PEAK_START] = updated.detectedPeakStart
            prefs[Keys.DETECTED_PEAK_END] = updated.detectedPeakEnd
            prefs[Keys.DETECTED_PEAK_MINUTE_OF_DAY] = updated.detectedPeakMinuteOfDay
            prefs[Keys.PEAK_DETECTION_CONFIDENCE] = updated.peakDetectionConfidence
            prefs[Keys.EFFECTIVE_PEAK_START] = updated.effectivePeakStart
            prefs[Keys.EFFECTIVE_PEAK_END] = updated.effectivePeakEnd
            prefs[Keys.EFFECTIVE_PEAK_MINUTE_OF_DAY] = updated.effectivePeakMinuteOfDay
            prefs[Keys.QUIZ_PEAK_ENABLED] = updated.quizPeakEnabled
            prefs[Keys.AFFIRMATIONS] = encodeAffirmations(updated.affirmations)
            prefs[Keys.TAG_CATALOG] = encodeAffirmations(withStarterTagCatalog(updated.tagCatalog))
            prefs[Keys.AUTO_SCHEDULING_ENABLED] = updated.autoSchedulingEnabled
            prefs[Keys.AUTO_SCHEDULING_HORIZON_DAYS] = updated.autoSchedulingHorizonDays.coerceIn(1, 7)
            prefs[Keys.AUTO_SCHEDULING_BREAK_AFTER_COGNITIVE_MINUTES] = updated.autoSchedulingBreakAfterCognitiveMinutes.coerceIn(30, 180)
            prefs[Keys.AUTO_SCHEDULING_BREAK_DURATION_MINUTES] = updated.autoSchedulingBreakDurationMinutes.coerceIn(5, 30)
            prefs[Keys.AUTO_SCHEDULING_BACKGROUND_THROTTLE_MINUTES] = updated.autoSchedulingBackgroundThrottleMinutes.coerceIn(5, 120)
                prefs[Keys.AUTO_SCHEDULING_REQUIRES_REVIEW] = updated.autoSchedulingRequiresReview
                prefs[Keys.AUTO_SCHEDULING_MODE] = updated.autoSchedulingMode
                prefs[Keys.AUTO_SCHEDULING_BUFFER_PERCENT] = updated.autoSchedulingBufferPercent.coerceIn(0, 80)
                prefs[Keys.AUTO_SCHEDULING_MAX_TASKS_PER_DAY] = updated.autoSchedulingMaxTasksPerDay.coerceAtLeast(0)
                prefs[Keys.AUTO_SCHEDULING_MAX_DEEP_WORK_MINUTES_PER_DAY] = updated.autoSchedulingMaxDeepWorkMinutesPerDay.coerceAtLeast(0)
                prefs[Keys.AUTO_SCHEDULING_PROTECTED_REST_START_MINUTE] = updated.autoSchedulingProtectedRestStartMinute.coerceIn(-1, 1_439)
                prefs[Keys.AUTO_SCHEDULING_PROTECTED_REST_END_MINUTE] = updated.autoSchedulingProtectedRestEndMinute.coerceIn(-1, 1_440)
                prefs[Keys.CALENDAR_INTEGRATION_ENABLED] = updated.calendarIntegrationEnabled
                prefs[Keys.CALENDAR_EXPORT_ACCEPTED_SCHEDULES] = updated.calendarExportAcceptedSchedules

            prefs[Keys.YEARLY_GOALS] = encodeAffirmations(updated.yearlyGoals)
            prefs[Keys.WEEKLY_GOALS] = encodeAffirmations(updated.weeklyGoals)
            prefs[Keys.LAST_YEARLY_GOAL_SHOWN_YEAR] = updated.lastYearlyGoalShownYear
            prefs[Keys.LAST_WEEKLY_GOAL_SHOWN_WEEK] = updated.lastWeeklyGoalShownWeek
            prefs[Keys.LAST_WEEKLY_GOAL_SHOWN_YEAR] = updated.lastWeeklyGoalShownYear
            prefs[Keys.WOOP_ENABLED] = updated.woopEnabled
            prefs[Keys.AUTO_TRACKER_ENABLED] = updated.autoTrackerEnabled
            prefs[Keys.DAILY_PLAN_NOTIFICATIONS_ENABLED] = updated.dailyPlanNotificationsEnabled
            prefs[Keys.STREAK_NOTIFICATIONS_ENABLED] = updated.streakNotificationsEnabled
            prefs[Keys.AUTONOMY_NUDGE_NOTIFICATIONS_ENABLED] = updated.autonomyNudgeNotificationsEnabled
            prefs[Keys.DEADLINE_REMINDER_NOTIFICATIONS_ENABLED] = updated.deadlineReminderNotificationsEnabled
            prefs[Keys.DEADLINE_ESCALATION_NOTIFICATIONS_ENABLED] = updated.deadlineEscalationNotificationsEnabled
            prefs[Keys.DAILY_PLAN_NOTIFICATION_HOUR] = updated.dailyPlanNotificationHour.coerceIn(0, 23)
            prefs[Keys.STREAK_CHECK_NOTIFICATION_HOUR] = updated.streakCheckNotificationHour.coerceIn(0, 23)
            prefs[Keys.USER_GUIDE_PROMPT_SHOWN] = updated.userGuidePromptShown
            prefs[Keys.LEFT_PAGE_QUICK_NOTE] = updated.leftPageQuickNote
            if (updated.manualChronotype != null) {
                prefs[Keys.MANUAL_CHRONOTYPE] = updated.manualChronotype
            } else {
                prefs.remove(Keys.MANUAL_CHRONOTYPE)
            }
            if (updated.quizChronotype != null) {
                prefs[Keys.QUIZ_CHRONOTYPE] = updated.quizChronotype
            } else {
                prefs.remove(Keys.QUIZ_CHRONOTYPE)
            }
            prefs[Keys.QUIZ_PROGRESS] = updated.quizProgress
            prefs[Keys.SLEEP_PRESSURE_POINTS] = updated.sleepPressurePoints
            prefs[Keys.SLEEP_PRESSURE_TRACKING_STARTED_AT] = updated.sleepPressureTrackingStartedAtMillis
            prefs[Keys.SLEEP_PRESSURE_LAST_COMPUTED_AT] = updated.sleepPressureLastComputedAtMillis
            prefs[Keys.AUTO_FALLBACK_SLEEP_INSERTION_ENABLED] = updated.autoFallbackSleepInsertionEnabled
            prefs[Keys.MOMENT_INTERRUPTION_SENSITIVITY] = updated.momentInterruptionSensitivity.coerceIn(0.5f, 2.0f)
            prefs[Keys.MOMENT_NOTIFICATION_SENSITIVITY] = updated.momentNotificationSensitivity.coerceIn(0.5f, 2.0f)
            prefs[Keys.MOMENT_TASK_PRESSURE_SENSITIVITY] = updated.momentTaskPressureSensitivity.coerceIn(0.5f, 2.0f)
            prefs[Keys.ENERGY_TELEMETRY_ENABLED] = updated.energyTelemetryEnabled
            prefs[Keys.ENERGY_TELEMETRY_RETENTION_DAYS] = updated.energyTelemetryRetentionDays.coerceIn(1, 90)
            prefs[Keys.MORNING_TUNE_SLEEP_WEIGHT] = updated.morningTuneSleepWeight
            prefs[Keys.MORNING_TUNE_WAKE_WEIGHT] = updated.morningTuneWakeWeight
            prefs[Keys.MORNING_TUNE_BEHAVIOR_WEIGHT] = updated.morningTuneBehaviorWeight
            prefs[Keys.MORNING_TUNE_BASE_WEIGHT] = updated.morningTuneBaseWeight
            prefs[Keys.MORNING_TUNE_UPDATED_AT] = updated.morningTuneUpdatedAtMillis
            prefs[Keys.MORNING_TUNE_VERSION] = updated.morningTuneVersion
            prefs[Keys.ADAPTIVE_PEAK_FREEZE_ENABLED] = updated.adaptivePeakFreezeEnabled
            prefs[Keys.PEAK_QUALITY_DEGRADE_STREAK] = updated.peakQualityDegradeStreak.coerceAtLeast(0)
            prefs[Keys.PEAK_CONFIDENCE_ABSTENTION_ENABLED] = updated.peakConfidenceAbstentionEnabled
            prefs[Keys.PEAK_CONFIDENCE_ABSTENTION_REASON] = updated.peakConfidenceAbstentionReason
            prefs[Keys.PEAK_CONFIDENCE_ABSTENTION_TRIGGER_COUNT] = updated.peakConfidenceAbstentionTriggerCount.coerceAtLeast(0)
            prefs[Keys.PEAK_CONFIDENCE_ABSTENTION_RECOVERY_COUNT] = updated.peakConfidenceAbstentionRecoveryCount.coerceAtLeast(0)
            prefs[Keys.PEAK_CONFIDENCE_ABSTENTION_LAST_CHANGED_AT] = updated.peakConfidenceAbstentionLastChangedAtMillis
            prefs[Keys.PEAK_CONFIDENCE_ABSTENTION_REASON_FREEZE_COUNT] = updated.peakConfidenceAbstentionReasonFreezeCount.coerceAtLeast(0)
            prefs[Keys.PEAK_CONFIDENCE_ABSTENTION_REASON_LOW_SAMPLES_COUNT] = updated.peakConfidenceAbstentionReasonLowSamplesCount.coerceAtLeast(0)
            prefs[Keys.PEAK_CONFIDENCE_ABSTENTION_REASON_LOW_COVERAGE_COUNT] = updated.peakConfidenceAbstentionReasonLowCoverageCount.coerceAtLeast(0)
            prefs[Keys.PEAK_CONFIDENCE_ABSTENTION_REASON_WAKE_VARIANCE_COUNT] = updated.peakConfidenceAbstentionReasonWakeVarianceCount.coerceAtLeast(0)
            prefs[Keys.PEAK_CONFIDENCE_ABSTENTION_REASON_DIVERGENCE_COUNT] = updated.peakConfidenceAbstentionReasonDivergenceCount.coerceAtLeast(0)
            prefs[Keys.PEAK_CONFIDENCE_ABSTENTION_REASON_OTHER_COUNT] = updated.peakConfidenceAbstentionReasonOtherCount.coerceAtLeast(0)
            prefs[Keys.MANUAL_PEAK_PROFILE_ENABLED] = updated.manualPeakProfileEnabled
            prefs[Keys.MANUAL_PEAK_PROFILE_TYPE] = updated.manualPeakProfileType
            prefs[Keys.MANUAL_PEAK_ANCHOR_MINUTE_OF_DAY] = updated.manualPeakAnchorMinuteOfDay.coerceIn(0, 1439)
            prefs[Keys.MANUAL_PEAK_WINDOW_1_START_OFFSET] = updated.manualPeakWindow1StartOffsetMinutes.coerceIn(0, 1439)
            prefs[Keys.MANUAL_PEAK_WINDOW_2_START_OFFSET] = updated.manualPeakWindow2StartOffsetMinutes.coerceIn(0, 1439)
            prefs[Keys.MANUAL_PEAK_WINDOW_3_START_OFFSET] = updated.manualPeakWindow3StartOffsetMinutes.coerceIn(0, 1439)
            prefs[Keys.MANUAL_PEAK_WINDOW_1_DURATION] = updated.manualPeakWindow1DurationMinutes.coerceIn(30, 360)
            prefs[Keys.MANUAL_PEAK_WINDOW_2_DURATION] = updated.manualPeakWindow2DurationMinutes.coerceIn(30, 360)
            prefs[Keys.MANUAL_PEAK_WINDOW_3_DURATION] = updated.manualPeakWindow3DurationMinutes.coerceIn(30, 360)
            prefs[Keys.MANUAL_PEAK_WINDOW_1_AMPLITUDE] = updated.manualPeakWindow1Amplitude.coerceIn(0.2f, 1f)
            prefs[Keys.MANUAL_PEAK_WINDOW_2_AMPLITUDE] = updated.manualPeakWindow2Amplitude.coerceIn(0.2f, 1f)
            prefs[Keys.MANUAL_PEAK_WINDOW_3_AMPLITUDE] = updated.manualPeakWindow3Amplitude.coerceIn(0.2f, 1f)
        }
    }

    suspend fun mergeTagCatalog(tags: Collection<String>) {
        val cleaned = tags.mapNotNull { it.trim().takeIf(String::isNotBlank) }
        if (cleaned.isEmpty()) return

        updatePreferences { prefs ->
            prefs.copy(tagCatalog = mergeTags(prefs.tagCatalog, cleaned))
        }
    }

    suspend fun removeTagFromCatalog(tag: String) {
        val cleaned = tag.trim()
        if (cleaned.isBlank()) return

        updatePreferences { prefs ->
            prefs.copy(tagCatalog = prefs.tagCatalog.filterNot { it.equals(cleaned, ignoreCase = true) })
        }
    }

    private fun mergeTags(existing: List<String>, incoming: Collection<String>): List<String> {
        val merged = mutableListOf<String>()
        val seen = mutableSetOf<String>()

        fun addTag(tag: String) {
            val key = tag.lowercase()
            if (seen.add(key)) {
                merged += tag
            }
        }

        existing.forEach(::addTag)
        incoming.forEach(::addTag)
        return withStarterTagCatalog(merged)
    }

    private fun withStarterTagCatalog(existing: List<String>): List<String> {
        val merged = mutableListOf<String>()
        val seen = mutableSetOf<String>()

        fun addTag(tag: String) {
            val cleaned = tag.trim()
            if (cleaned.isBlank()) return
            val key = cleaned.lowercase()
            if (seen.add(key)) {
                merged += cleaned
            }
        }

        existing.forEach(::addTag)
        TaskTagSchedulingProfile.starterTags.forEach(::addTag)
        return merged.sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}
