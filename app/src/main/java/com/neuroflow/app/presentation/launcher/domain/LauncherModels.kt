package com.neuroflow.app.presentation.launcher.domain

import androidx.compose.ui.graphics.Color
import com.neuroflow.app.data.local.entity.TaskEntity
import com.neuroflow.app.presentation.launcher.data.AppInfo
import java.util.UUID

/**
 * Folder definition for organizing apps on the home screen or dock.
 *
 * @property id Unique identifier for the folder
 * @property name User-visible folder name
 * @property packages Ordered list of package names contained in the folder
 * @property gridIndex Flat index in home screen grid (0-based, row-major)
 */
data class FolderDefinition(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val packages: List<String>,
    val gridIndex: Int
)

/**
 * Icon shape options for adaptive icon masking.
 * All shapes are pure data with no Android dependencies.
 */
enum class IconShape {
    CIRCLE,
    SQUIRCLE,
    ROUNDED_SQUARE,
    TEARDROP,
    SYSTEM_DEFAULT
}

/**
 * Clock display style options.
 */
enum class ClockStyle {
    DIGITAL,
    MINIMAL
}

/**
 * Task card visual style options.
 */
enum class CardStyle {
    ELEVATED,
    FLAT,
    OUTLINED
}

/**
 * Android navigation mode detected at runtime.
 */
enum class NavigationMode {
    THREE_BUTTON,
    TWO_BUTTON,
    GESTURE
}

/**
 * Launcher theme configuration.
 * Pure data class with no Android dependencies - all visual decisions read from this.
 *
 * @property cardAlpha Transparency for FocusTaskCard background (0.5-1.0)
 * @property clockStyle Clock display style
 * @property accentColor Accent color (overridden by Dynamic Color on API 31+)
 * @property showTaskScore Whether to display raw TaskScoringEngine score
 * @property taskCardStyle Visual style for task card
 * @property focusModeDimEnabled Whether to dim distracting apps during focus
 * @property iconPackPackageName Selected icon pack package name (null = system icons)
 * @property iconShape Selected icon shape for adaptive icon masking
 * @property drawerColumns Number of columns in app drawer grid (3-5)
 * @property distractionDimmingEnabled Whether distraction dimming is active
 */
data class LauncherTheme(
    val cardAlpha: Float = 0.85f,
    val clockStyle: ClockStyle = ClockStyle.DIGITAL,
    val accentColor: Color = Color.Unspecified,
    val showTaskScore: Boolean = false,
    val taskCardStyle: CardStyle = CardStyle.ELEVATED,
    val focusModeDimEnabled: Boolean = false,
    val iconPackPackageName: String? = null,
    val iconShape: IconShape = IconShape.SYSTEM_DEFAULT,
    val drawerColumns: Int = 4,
    val distractionDimmingEnabled: Boolean = false
)

/**
 * Backup payload for export/import.
 * Contains all launcher configuration that can be backed up.
 *
 * @property schemaVersion Backup schema version for migration support
 * @property dockPackages List of package names pinned to dock
 * @property folders List of folder definitions
 * @property hiddenPackages List of package names hidden from drawer
 * @property lockedPackages List of package names requiring biometric auth
 * @property iconPackPackageName Selected icon pack package name
 * @property iconShape Selected icon shape as string
 * @property drawerColumns App drawer grid column count
 * @property cardAlpha Task card transparency value
 * @property clockStyle Clock style as string
 */
data class BackupPayload(
    val schemaVersion: Int = 1,
    val dockPackages: List<String>,
    val folders: List<FolderDefinition>,
    val hiddenPackages: List<String>,
    val lockedPackages: List<String>,
    val iconPackPackageName: String?,
    val iconShape: String,
    val drawerColumns: Int,
    val cardAlpha: Float,
    val clockStyle: String
)

/**
 * UI state for the launcher home screen.
 * Aggregates all state needed by launcher composables.
 *
 * @property topTask Highest-scored active task (null if none)
 * @property ulyssesContract Ulysses contract for top task (null if none)
 * @property woopEntity WOOP entity for top task (null if none)
 * @property habitTasks List of recurring tasks due today (max 3)
 * @property dockApps List of apps pinned to dock
 * @property badgeCounts Map of package name to unread notification count
 * @property launcherTheme Current launcher theme configuration
 * @property focusActive Whether a focus session is currently active
 * @property freshStartPending Whether a fresh start overlay should be shown
 */
data class LauncherUiState(
    val topTask: TaskEntity? = null,
    val ulyssesContract: com.neuroflow.app.data.local.entity.UlyssesContractEntity? = null,
    val woopEntity: com.neuroflow.app.data.local.entity.WoopEntity? = null,
    val habitTasks: List<TaskEntity> = emptyList(),
    val dockApps: List<AppInfo> = emptyList(),
    val badgeCounts: Map<String, Int> = emptyMap(),
    val launcherTheme: LauncherTheme = LauncherTheme(),
    val focusActive: Boolean = false,
    val freshStartPending: Boolean = false
)
