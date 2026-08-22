package com.neuroflow.app.presentation.launcher.domain

import android.appwidget.AppWidgetHost
import android.content.Context

/**
 * AppWidgetHost wrapper for home screen widgets.
 *
 * Phase 1 scaffolding: Host is created and lifecycle methods are wired,
 * but no widgets are bound yet. This prevents future structural changes
 * when widgets are added in later phases.
 *
 * Host ID: 1337 (fixed identifier for this launcher's widget host)
 */
class AppWidgetHostWrapper(
    context: Context,
    hostId: Int = 1337
) : AppWidgetHost(context, hostId) {

    /**
     * Start listening for widget updates.
     * Safe to call even when no widgets are bound.
     * Should be called in LauncherActivity.onStart().
     */
    override fun startListening() {
        try {
            super.startListening()
        } catch (e: Exception) {
            // Fail silently - no widgets bound yet in Phase 1
            android.util.Log.w("AppWidgetHostWrapper", "Error starting widget host", e)
        }
    }

    /**
     * Stop listening for widget updates.
     * Should be called in LauncherActivity.onStop().
     */
    override fun stopListening() {
        try {
            super.stopListening()
        } catch (e: Exception) {
            // Fail silently
            android.util.Log.w("AppWidgetHostWrapper", "Error stopping widget host", e)
        }
    }
}
