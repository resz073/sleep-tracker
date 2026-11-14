package com.resz073.sleeptracker.engine

import java.time.LocalDateTime
import java.time.LocalTime

/** Which reminders are on (set per phone) */
data class ReminderSwitches(val windDown: Boolean, val bedtime: Boolean, val morning: Boolean) {
    val any: Boolean get() = windDown || bedtime || morning
}

enum class ReminderKind(val key: String, val type: String?) {
    WIND_DOWN("wind", null), BEDTIME("bed", "BEDTIME"), MORNING("morning", "MORNING")
}

/** A notification to schedule: [at] is milliseconds since 1970 */
data class Reminder(val id: Int, val kind: ReminderKind, val title: String, val body: String, val at: Long)

/**
 * Reminders for the next 7 days, re-planned whenever the data or the schedule changes: wind-down 30 minutes
 * before tonight's bedtime, bedtime, and a morning reminder an hour after wake-up while last night isn't
 * logged. While asleep, tonight's bedtime reminders are skipped.
 */
object Reminders {
    fun plan(model: SleepModel, plan: Plan?, on: ReminderSwitches): List<Reminder> {
        val out = ArrayList<Reminder>()
        if (plan == null || !on.any) return out
        val now = model.now
        val bm = mod(plan.tonight).toInt()
        fun atClock(dayOffset: Int, minutes: Int): Long =
            Time.ms(LocalDateTime.of(now.today.plusDays(dayOffset.toLong()), LocalTime.of(minutes / 60, minutes % 60)), now.zone)
        var d0 = if (atClock(0, bm) <= now.ms) 1 else 0
        if (model.pending != null && atClock(d0, bm) - now.ms < 12 * 3600000L) d0++ // asleep already: skip tonight's
        val clock = Words.clock(bm)
        for (i in 0 until 7) {
            val b = atClock(d0 + i, bm)
            if (on.windDown) out += Reminder(100 + i, ReminderKind.WIND_DOWN, "Wind down", "Bedtime is at $clock. Time to start winding down.", b - 30 * 60000L)
            if (on.bedtime) out += Reminder(110 + i, ReminderKind.BEDTIME, "Time for bed", "It's $clock. Tap Going to sleep when you lie down.", b)
        }
        if (on.morning) {
            val wm = mod(plan.wake + 60).toInt()
            val m0 = if (atClock(0, wm) <= now.ms || model.nightByWake.containsKey(model.today)) 1 else 0
            for (i in 0 until 7) out += Reminder(120 + i, ReminderKind.MORNING, "How did you sleep?", "Log last night's sleep.", atClock(m0 + i, wm))
        }
        return out.filter { it.at > now.ms + 60000 }
    }
}
