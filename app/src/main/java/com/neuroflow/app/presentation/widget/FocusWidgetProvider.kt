package com.neuroflow.app.presentation.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import android.widget.RemoteViews
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.neuroflow.app.MainActivity
import com.neuroflow.app.R
import com.neuroflow.app.worker.FocusWidgetUpdateWorker

class FocusWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // Trigger an immediate update via WorkManager
        val request = OneTimeWorkRequestBuilder<FocusWidgetUpdateWorker>().build()
        WorkManager.getInstance(context).enqueue(request)

        // Set up the Start button tap intent for each widget instance
        appWidgetIds.forEach { widgetId ->
            val views = RemoteViews(context.packageName, R.layout.widget_focus)
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context, widgetId, launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_start_button, pendingIntent)
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // Handle Start tap — launch MainActivity
        if (intent.action == "com.neuroflow.app.WIDGET_START") {
            val taskId = intent.getStringExtra("taskId")
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                taskId?.let { putExtra("taskId", it) }
            }
            context.startActivity(launchIntent)
        }
    }
}
