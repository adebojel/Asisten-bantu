package com.asisten.bantu

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * Widget layar utama Kobra OS 3D Glassmorphism untuk pemicu suara instan & Mata Kedua.
 */
class KobraWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (ACTION_WIDGET_VOICE == intent.action) {
            if (AsistenAccessibilityService.isRunning()) {
                AsistenAccessibilityService.requestListen()
            } else {
                val launch = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("auto_listen", true)
                }
                context.startActivity(launch)
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        const val ACTION_WIDGET_VOICE = "com.asisten.bantu.ACTION_WIDGET_VOICE"

        fun updateWidget(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_kobra_hud)
            val statusText = if (AsistenAccessibilityService.isRunning()) {
                "SIAGA • Ketuk untuk bicara"
            } else {
                "Ketuk untuk buka Kobra OS"
            }
            views.setTextViewText(R.id.widget_subtitle, statusText)

            val voiceIntent = Intent(context, KobraWidgetProvider::class.java).apply {
                action = ACTION_WIDGET_VOICE
            }
            val voicePi = PendingIntent.getBroadcast(
                context,
                9101,
                voiceIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, voicePi)
            views.setOnClickPendingIntent(R.id.widget_orb_icon, voicePi)

            val visionIntent = Intent(context, VisionActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val visionPi = PendingIntent.getActivity(
                context,
                9102,
                visionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_vision_btn, visionPi)

            manager.updateAppWidget(appWidgetId, views)
        }
    }
}
