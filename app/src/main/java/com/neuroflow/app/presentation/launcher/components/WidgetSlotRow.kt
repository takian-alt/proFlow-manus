package com.neuroflow.app.presentation.launcher.components

import android.appwidget.AppWidgetProviderInfo
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * WidgetSlotRow - Container for home screen widgets.
 *
 * Phase 1 scaffolding: Renders an empty container when widget list is empty.
 * AppWidgetHostWrapper is already injected in LauncherModule and lifecycle methods
 * (startListening/stopListening) are already wired in LauncherActivity.onStart/onStop.
 *
 * Future phases will implement:
 * - Widget binding and rendering via AppWidgetHostView
 * - Widget configuration and management
 * - Widget drag-and-drop positioning
 *
 * Position: Between HabitQuickRow and DockRow in home screen layout.
 *
 * Requirements: 2.8, 2.9, 26.3, 26.6
 *
 * @param widgets List of bound AppWidgetProviderInfo (empty in Phase 1)
 * @param modifier Modifier for the widget container
 */
@Composable
fun WidgetSlotRow(
    widgets: List<AppWidgetProviderInfo>,
    modifier: Modifier = Modifier
) {
    // Phase 1: Render empty container when list is empty
    if (widgets.isEmpty()) {
        // Empty container with zero height - no visual presence
        Spacer(modifier = modifier.height(0.dp))
        return
    }

    // Future implementation: Render widgets via AppWidgetHostView
    // For now, show placeholder for non-empty list (should not occur in Phase 1)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = MaterialTheme.shapes.medium
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Widgets (${widgets.size})\nComing in future phase",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
