package com.resz073.sleeptracker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.resz073.sleeptracker.MainActivity
import com.resz073.sleeptracker.R
import com.resz073.sleeptracker.data.Offline
import com.resz073.sleeptracker.remind.Alarms
import com.resz073.sleeptracker.show.WidgetView

/** The home screen widget: tonight's bedtime (or the sleep in progress), the last 14 days, and a Going to sleep / I'm awake button */
class SleepWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val done = goAsync()
        val app = context.applicationContext
        Thread {
            try {
                Extras.update(app, Offline.model(app), force = true)
            } catch (e: Exception) {
                // drawn next time
            } finally {
                done.finish()
            }
        }.start()
    }

    companion object {
        fun draw(context: Context, v: WidgetView) {
            val mgr = AppWidgetManager.getInstance(context) ?: return
            val ids = mgr.getAppWidgetIds(ComponentName(context, SleepWidget::class.java))
            if (ids.isEmpty()) return
            val views = RemoteViews(context.packageName, R.layout.widget_sleep)
            views.setTextViewText(R.id.w_label, v.label)
            views.setTextViewText(R.id.w_big, v.big)
            views.setTextViewTextSize(R.id.w_big, TypedValue.COMPLEX_UNIT_SP, if (v.bigSmall) 24f else 34f)
            views.setTextViewText(R.id.w_line, v.line)
            val debt = v.debt
            if (debt != null) {
                views.setTextViewText(R.id.w_debt, debt)
                views.setViewVisibility(R.id.w_debt, View.VISIBLE)
            } else views.setViewVisibility(R.id.w_debt, View.GONE)
            val action = v.action
            if (action != null && v.button != null) {
                views.setTextViewText(R.id.w_button, v.button)
                views.setViewVisibility(R.id.w_button, View.VISIBLE)
                views.setOnClickPendingIntent(R.id.w_button, open(context, action, if (action == "awake") 402 else 401))
            } else views.setViewVisibility(R.id.w_button, View.GONE)
            views.setOnClickPendingIntent(R.id.w_root, open(context, "tap", 400))
            mgr.updateAppWidget(ids, views)
        }

        /** Opens the app to do [action], as a reminder's buttons do */
        fun open(context: Context, action: String, code: Int): PendingIntent {
            val i = Intent(context, MainActivity::class.java).setAction("com.resz073.sleeptracker.OPEN_" + action)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(Alarms.EXTRA_ACTION, action)
            return PendingIntent.getActivity(context, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }
}
