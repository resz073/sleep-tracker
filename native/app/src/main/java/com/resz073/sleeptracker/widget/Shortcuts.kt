package com.resz073.sleeptracker.widget

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.resz073.sleeptracker.MainActivity
import com.resz073.sleeptracker.R
import com.resz073.sleeptracker.engine.SleepModel
import com.resz073.sleeptracker.remind.Alarms

/** The shortcuts on the app icon's long-press menu: Going to sleep (or I'm awake while asleep), and Add entry */
object Shortcuts {
    fun update(context: Context, model: SleepModel?) {
        try {
            if (model == null) {
                ShortcutManagerCompat.removeAllDynamicShortcuts(context)
                return
            }
            val first = if (model.pending != null) item(context, "awake", "I’m awake", "Log the sleep in progress", R.drawable.ic_shortcut_awake)
            else item(context, "sleep", "Going to sleep", "Start a sleep now", R.drawable.ic_shortcut_sleep)
            ShortcutManagerCompat.setDynamicShortcuts(context, listOf(first, item(context, "add", "Add entry", "Log a night or a nap", R.drawable.ic_shortcut_add)))
        } catch (e: Exception) {
            // Android limits how often shortcuts change in the background: they're set again next time
        }
    }

    private fun item(context: Context, id: String, short: String, long: String, icon: Int): ShortcutInfoCompat =
        ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(short)
            .setLongLabel(long)
            .setIcon(IconCompat.createWithResource(context, icon))
            .setIntent(Intent(context, MainActivity::class.java).setAction("com.resz073.sleeptracker.OPEN_" + id).putExtra(Alarms.EXTRA_ACTION, id))
            .build()
}
