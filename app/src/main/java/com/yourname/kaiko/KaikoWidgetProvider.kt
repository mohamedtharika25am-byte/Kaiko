package com.yourname.kaiko

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews

/**
 * 1x1 Home Screen SOS AppWidgetProvider.
 * Provides a single-tap instant emergency trigger dispatching to TriggerManager.
 */
class KaikoWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_TRIGGER_WIDGET = "com.yourname.kaiko.ACTION_TRIGGER_WIDGET"
        private const val TAG = "KAIKO_DEBUG"
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Update all active instances of the widget on the home screen
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action == ACTION_TRIGGER_WIDGET) {
            Log.d(TAG, "Home screen SOS widget tapped. Initiating emergency alert via widget...")
            // Invoke shared trigger action
            TriggerManager.fireAlert(context, "widget")
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_layout)

        // Intent broadcast targeted to this widget provider
        val intent = Intent(context, KaikoWidgetProvider::class.java).apply {
            action = ACTION_TRIGGER_WIDGET
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Attach pending intent to the widget SOS button
        views.setOnClickPendingIntent(R.id.btn_widget_sos, pendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
