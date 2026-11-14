package com.resz073.sleeptracker.remind

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.resz073.sleeptracker.MainActivity
import com.resz073.sleeptracker.R
import com.resz073.sleeptracker.data.Backend
import com.resz073.sleeptracker.data.FileCopy
import com.resz073.sleeptracker.engine.Now
import com.resz073.sleeptracker.engine.Planner
import com.resz073.sleeptracker.engine.Reminder
import com.resz073.sleeptracker.engine.Reminders
import com.resz073.sleeptracker.engine.SleepDoc
import com.resz073.sleeptracker.engine.SleepModel

/**
 * Reminder notifications, as the current app sends them: wind-down, bedtime (with a Going to sleep button)
 * and morning (with Log it), planned for the next 7 days by the sleep engine (engine/Reminders.kt).
 * Each is an alarm that shows its notification when it's due. They're planned again whenever the data or
 * the schedule changes, when one is shown, after a restart, and when the clock or time zone changes.
 */
object Alarms {
    const val CHANNEL = "reminders"
    const val EXTRA_ID = "reminder-id"
    const val EXTRA_KIND = "reminder-kind"
    /** For MainActivity: what was tapped ("tap", or a button: "sleep", "log") */
    const val EXTRA_ACTION = "reminder-action"
    private const val EXTRA_TITLE = "reminder-title"
    private const val EXTRA_BODY = "reminder-body"
    private const val EXTRA_TYPE = "reminder-type"
    private const val ACTION_REMIND = "com.resz073.sleeptracker.REMIND"
    /** The ids the engine uses: 100–106 wind-down, 110–116 bedtime, 120–126 morning (the current app clears 100–139) */
    private val IDS = 100 until 140

    fun notificationsAllowed(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** On-time reminders need Android's "Alarms & reminders" permission (Android 12 and later); without it Android can hold them back */
    fun exactAllowed(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 31) context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() ?: false else true

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val ch = NotificationChannel(CHANNEL, "Sleep reminders", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Wind-down, bedtime and morning reminders"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
    }

    /**
     * Plans the reminders for [model] (null: signed out, so none) with the switches set on this phone, and
     * schedules them unless exactly these are already set. [force] schedules them anyway (after a restart,
     * Android has dropped them).
     */
    fun update(context: Context, model: SleepModel?, force: Boolean) {
        val allowed = notificationsAllowed(context)
        val exact = exactAllowed(context)
        val list = if (model == null || !allowed) emptyList() else Reminders.plan(model, Planner.plan(model), ReminderPrefs.read(context).switches)
        val key = list.joinToString("|") { "${it.id},${it.at},${it.title},${it.body}" } + "|exact=$exact"
        val prefs = context.getSharedPreferences(ReminderPrefs.FILE, Context.MODE_PRIVATE)
        if (!force && prefs.getString("scheduled", null) == key) return
        schedule(context, list, exact)
        prefs.edit().putString("scheduled", key).apply()
    }

    /** [update] from this phone's copy of the signed-in account (for the receivers, when the app isn't open) */
    fun updateFromCopy(context: Context, force: Boolean) {
        if (!Backend.ready) return
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        val model = uid?.let { u ->
            val now = Now.system()
            SleepModel(SleepDoc.fromText(FileCopy(context, u).read(), now), now)
        }
        update(context, model, force)
    }

    @SuppressLint("ScheduleExactAlarm")
    private fun schedule(context: Context, list: List<Reminder>, exact: Boolean) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        for (id in IDS) alarm(context, id, null, PendingIntent.FLAG_NO_CREATE)?.let { am.cancel(it); it.cancel() }
        for (r in list) {
            val pi = alarm(context, r.id, r, PendingIntent.FLAG_UPDATE_CURRENT) ?: continue
            try {
                if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.at, pi)
                else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.at, pi)
            } catch (e: SecurityException) {
                // On-time permission taken away since it was checked: Android sends it when it can
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.at, pi)
            }
        }
    }

    private fun alarm(context: Context, id: Int, r: Reminder?, flag: Int): PendingIntent? {
        val i = Intent(context, ReminderReceiver::class.java).setAction(ACTION_REMIND)
        if (r != null) i.putExtra(EXTRA_ID, r.id).putExtra(EXTRA_TITLE, r.title).putExtra(EXTRA_BODY, r.body).putExtra(EXTRA_KIND, r.kind.key).putExtra(EXTRA_TYPE, r.kind.type)
        return PendingIntent.getBroadcast(context, id, i, flag or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Shows a reminder that's come due */
    @SuppressLint("MissingPermission")
    fun show(context: Context, intent: Intent) {
        if (!notificationsAllowed(context)) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        ensureChannel(context)
        val id = intent.getIntExtra(EXTRA_ID, 0)
        val kind = intent.getStringExtra(EXTRA_KIND)
        fun open(action: String, code: Int): PendingIntent {
            val i = Intent(context, MainActivity::class.java).setAction("com.resz073.sleeptracker.REMINDER_" + action)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_ACTION, action).putExtra(EXTRA_KIND, kind).putExtra(EXTRA_ID, id)
            return PendingIntent.getActivity(context, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val b = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_moon)
            .setColor(0xFF4D52CF.toInt())
            .setContentTitle(intent.getStringExtra(EXTRA_TITLE))
            .setContentText(intent.getStringExtra(EXTRA_BODY))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(open("tap", id * 10))
        when (intent.getStringExtra(EXTRA_TYPE)) {
            "BEDTIME" -> b.addAction(0, "Going to sleep", open("sleep", id * 10 + 1))
            "MORNING" -> b.addAction(0, "Log it", open("log", id * 10 + 2))
        }
        try {
            NotificationManagerCompat.from(context).notify(id, b.build())
        } catch (e: SecurityException) {
            // notifications turned off in the meantime
        }
    }
}
