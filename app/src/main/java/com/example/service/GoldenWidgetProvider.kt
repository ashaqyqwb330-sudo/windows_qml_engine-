package com.example.service

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.R

class GoldenWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_layout)

        // Capture action PendingIntent
        val captureIntent = Intent(context, WidgetActionActivity::class.java).apply {
            putExtra("action_type", "smart_capture")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingCapture = PendingIntent.getActivity(
            context, 101, captureIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_capture, pendingCapture)

        // Execute action PendingIntent
        val executeIntent = Intent(context, WidgetActionActivity::class.java).apply {
            putExtra("action_type", "execute_commands")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingExecute = PendingIntent.getActivity(
            context, 102, executeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_execute, pendingExecute)

        // Report action PendingIntent
        val reportIntent = Intent(context, WidgetActionActivity::class.java).apply {
            putExtra("action_type", "tree_report")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingReport = PendingIntent.getActivity(
            context, 103, reportIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_report, pendingReport)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
