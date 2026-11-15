package com.resz073.sleeptracker.remind

import android.content.Context
import com.resz073.sleeptracker.engine.ReminderKind
import com.resz073.sleeptracker.engine.ReminderSwitches

/**
 * Which reminders are on, kept on this phone as version 1 keeps them (each phone has its own).
 * [asked]: the "Get reminders" card on Today has been answered, so it doesn't show again.
 */
data class ReminderPrefs(val wind: Boolean, val bed: Boolean, val morning: Boolean, val asked: Boolean) {
    val any: Boolean get() = wind || bed || morning
    val switches: ReminderSwitches get() = ReminderSwitches(wind, bed, morning)

    fun isOn(kind: ReminderKind): Boolean = when (kind) {
        ReminderKind.WIND_DOWN -> wind
        ReminderKind.BEDTIME -> bed
        ReminderKind.MORNING -> morning
    }

    fun with(kind: ReminderKind, on: Boolean): ReminderPrefs = when (kind) {
        ReminderKind.WIND_DOWN -> copy(wind = on)
        ReminderKind.BEDTIME -> copy(bed = on)
        ReminderKind.MORNING -> copy(morning = on)
    }

    companion object {
        const val FILE = "reminders"

        fun read(context: Context): ReminderPrefs {
            val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            return ReminderPrefs(p.getBoolean("wind", false), p.getBoolean("bed", false), p.getBoolean("morning", false), p.getBoolean("asked", false))
        }

        fun write(context: Context, r: ReminderPrefs) {
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
                .putBoolean("wind", r.wind).putBoolean("bed", r.bed).putBoolean("morning", r.morning).putBoolean("asked", r.asked).apply()
        }
    }
}
