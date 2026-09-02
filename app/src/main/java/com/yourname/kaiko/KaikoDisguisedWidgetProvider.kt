package com.yourname.kaiko

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews

/**
 * Disguised / Neutral Home Screen Widget Provider.
 * Visually appears as a minimal "Daily Memo" widget without any emergency or SOS text.
 * When tapped, reliably dispatches to TriggerManager with source "disguised_widget".
 */
class KaikoDisguisedWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_TRIGGER_DISGUISED_WIDGET = "com.yourname.kaiko.ACTION_TRIGGER_DISGUISED_WIDGET"
        private const val TAG = "KAIKO_DEBUG"
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TRIGGER_DISGUISED_WIDGET) {
            Log.d(TAG, "Disguised memo widget tapped. Initiating emergency alert flow...")
            TriggerManager.fireAlert(context, "disguised_widget")
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_disguised_layout)
        val intent = Intent(context, KaikoDisguisedWidgetProvider::class.java).apply {
            action = ACTION_TRIGGER_DISGUISED_WIDGET
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_widget_disguised, pendingIntent)
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
