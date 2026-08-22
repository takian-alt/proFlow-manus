package com.neuroflow.app.presentation.launcher.components

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Widget host ID for the launcher.
 * Must be unique across the app.
 */
const val LAUNCHER_WIDGET_HOST_ID = 1337

/**
 * Widget slot data class.
 * Represents a widget bound to the launcher.
 *
 * @property appWidgetId Unique widget instance ID
 * @property providerInfo Widget provider information
 */
data class WidgetSlot(
    val appWidgetId: Int,
    val providerInfo: AppWidgetProviderInfo
)

/**
 * Composable widget host view.
 * Displays an Android AppWidget in Compose.
 *
 * @param widgetSlot Widget slot to display
 * @param appWidgetHost AppWidgetHost instance
 * @param modifier Modifier for the widget container
 */
@Composable
fun WidgetHostView(
    widgetSlot: WidgetSlot,
    appWidgetHost: AppWidgetHost,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appWidgetManager = AppWidgetManager.getInstance(context)

    AndroidView(
        factory = { ctx ->
            appWidgetHost.createView(
                ctx,
                widgetSlot.appWidgetId,
                widgetSlot.providerInfo
            ) as AppWidgetHostView
        },
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
    )
}

/**
 * Widget slot row composable.
 * Displays active widgets above the dock.
 *
 * Phase 2 implementation: Shows widgets if any are bound.
 *
 * @param widgets List of active widget slots
 * @param appWidgetHost AppWidgetHost instance
 * @param onAddWidget Callback to add a new widget
 * @param onRemoveWidget Callback to remove a widget
 * @param modifier Modifier for the row container
 */
@Composable
fun WidgetSlotRow(
    widgets: List<WidgetSlot>,
    appWidgetHost: AppWidgetHost? = null,
    onAddWidget: () -> Unit = {},
    onRemoveWidget: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (widgets.isEmpty()) {
        // Empty state - no widgets
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        widgets.forEach { widget ->
            if (appWidgetHost != null) {
                WidgetHostView(
                    widgetSlot = widget,
                    appWidgetHost = appWidgetHost,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
