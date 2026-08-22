package com.neuroflow.app.presentation.launcher.theme

import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.os.Build
import com.neuroflow.app.presentation.launcher.data.LauncherPreferences
import com.neuroflow.app.presentation.launcher.domain.LauncherTheme as LauncherThemeData

/**
 * CompositionLocal for providing LauncherTheme throughout the launcher UI.
 * All visual decisions should read from this CompositionLocal.
 */
val LocalLauncherTheme = compositionLocalOf { LauncherThemeData() }

/**
 * Maps LauncherPreferences to LauncherTheme data class.
 * Handles all Android/Compose wiring for theme configuration.
 *
 * @param preferences LauncherPreferences from PinnedAppsDataStore
 * @param isDarkTheme Whether dark theme is active (from system or user preference)
 * @return LauncherTheme data class with all visual configuration
 */
@Composable
fun mapPreferencesToTheme(
    preferences: LauncherPreferences,
    isDarkTheme: Boolean
): LauncherThemeData {
    val context = LocalContext.current

    // Apply Dynamic Color on API 31+ (Material You)
    val accentColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val colorScheme = if (isDarkTheme) {
            dynamicDarkColorScheme(context)
        } else {
            dynamicLightColorScheme(context)
        }
        colorScheme.primary
    } else {
        Color.Unspecified
    }

    // Map ClockStyle enum from PinnedAppsDataStore to domain enum
    val clockStyle = when (preferences.clockStyle) {
        com.neuroflow.app.presentation.launcher.data.ClockStyle.DIGITAL ->
            com.neuroflow.app.presentation.launcher.domain.ClockStyle.DIGITAL
        com.neuroflow.app.presentation.launcher.data.ClockStyle.MINIMAL ->
            com.neuroflow.app.presentation.launcher.domain.ClockStyle.MINIMAL
    }

    // Map IconShape enum from PinnedAppsDataStore to domain enum
    val iconShape = when (preferences.iconShape) {
        com.neuroflow.app.presentation.launcher.data.IconShape.CIRCLE ->
            com.neuroflow.app.presentation.launcher.domain.IconShape.CIRCLE
        com.neuroflow.app.presentation.launcher.data.IconShape.SQUIRCLE ->
            com.neuroflow.app.presentation.launcher.domain.IconShape.SQUIRCLE
        com.neuroflow.app.presentation.launcher.data.IconShape.ROUNDED_SQUARE ->
            com.neuroflow.app.presentation.launcher.domain.IconShape.ROUNDED_SQUARE
        com.neuroflow.app.presentation.launcher.data.IconShape.TEARDROP ->
            com.neuroflow.app.presentation.launcher.domain.IconShape.TEARDROP
        com.neuroflow.app.presentation.launcher.data.IconShape.SYSTEM_DEFAULT ->
            com.neuroflow.app.presentation.launcher.domain.IconShape.SYSTEM_DEFAULT
    }

    // Map CardStyle enum from PinnedAppsDataStore to domain enum
    val taskCardStyle = when (preferences.taskCardStyle) {
        com.neuroflow.app.presentation.launcher.data.CardStyle.ELEVATED ->
            com.neuroflow.app.presentation.launcher.domain.CardStyle.ELEVATED
        com.neuroflow.app.presentation.launcher.data.CardStyle.FLAT ->
            com.neuroflow.app.presentation.launcher.domain.CardStyle.FLAT
        com.neuroflow.app.presentation.launcher.data.CardStyle.OUTLINED ->
            com.neuroflow.app.presentation.launcher.domain.CardStyle.OUTLINED
    }

    return LauncherThemeData(
        cardAlpha = preferences.cardAlpha,
        clockStyle = clockStyle,
        accentColor = accentColor,
        showTaskScore = preferences.showTaskScore,
        taskCardStyle = taskCardStyle,
        focusModeDimEnabled = preferences.distractionDimmingEnabled, // Phase 5: enabled
        iconPackPackageName = preferences.iconPackPackageName,
        iconShape = iconShape,
        drawerColumns = preferences.drawerColumns,
        distractionDimmingEnabled = preferences.distractionDimmingEnabled // Phase 5: enabled
    )
}

/**
 * Provides LauncherTheme to the composable tree.
 * Should wrap the root launcher composable.
 *
 * @param theme LauncherTheme data class
 * @param content Composable content that will have access to LocalLauncherTheme
 */
@Composable
fun ProvideLauncherTheme(
    theme: LauncherThemeData,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalLauncherTheme provides theme) {
        content()
    }
}
