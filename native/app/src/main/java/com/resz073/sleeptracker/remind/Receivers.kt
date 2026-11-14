package com.resz073.sleeptracker.remind

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.resz073.sleeptracker.data.Offline
import com.resz073.sleeptracker.widget.Extras

/** A reminder is due: show it, then plan the next ones from this phone's copy (so they keep coming even if the app isn't opened) */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Alarms.show(context, intent)
        replan(context)
    }
}

/** After a restart (Android drops alarms), an app update, a clock or time zone change, or on-time reminders being allowed */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        replan(context)
    }
}

private fun BroadcastReceiver.replan(context: Context) {
    val done = goAsync()
    val app = context.applicationContext
    Thread {
        try {
            val model = Offline.model(app)
            Alarms.update(app, model, force = true)
            Extras.update(app, model)
        } catch (e: Exception) {
            // nothing to plan from: the app plans them next time it's opened
        } finally {
            done.finish()
        }
    }.start()
}
