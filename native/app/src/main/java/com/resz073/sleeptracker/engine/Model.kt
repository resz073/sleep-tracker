package com.resz073.sleeptracker.engine

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.TreeSet

enum class DayStatus {
    /** Before the first logged night */
    BEFORE,
    FUTURE,
    /** Today, or still counting: a sleep in progress, or last night can still be logged this morning */
    OPEN,
    /** Complete: the night ending that morning and the night ending the next morning are both logged */
    DONE,
    /** Incomplete because a night next to it isn't logged */
    MISSING
}

/**
 * One night: every sleep that started 7 PM to noon and ended the same morning, named by its wake-up date.
 * [bed] is the first bedtime, [wake] the final wake-up, [minutes] the time actually asleep (gaps left out),
 * [span] the minutes from first bedtime to final wake-up. [quality] is the average rating of the rated parts.
 */
class Night(
    val wakeDay: LocalDate,
    val bed: LocalDateTime,
    val wake: LocalDateTime,
    val minutes: Int,
    val span: Int,
    val parts: List<Entry>,
    val quality: Double,
    val unrated: Boolean
)

/** Totals over some days. Only complete days count; [missing] is nights not logged among those days. */
data class Stats(val minutes: Int, val days: Int, val metGoal: Int, val missing: Int, val balance: Int) {
    /** Average minutes a day (only when [days] > 0) */
    val average: Double get() = minutes.toDouble() / days
    /** Positive: sleep debt. Zero or less: ahead of the goal. */
    val inDebt: Boolean get() = balance > 0
}

/**
 * The day accounting of the current app. A day is complete when the night ending that morning and the
 * night ending the next morning are both logged; complete days count toward averages and sleep debt.
 * A missed night (named by its wake-up date) leaves the day before and the day after incomplete, and is
 * counted once, as a night not logged. Today, and any day touched by a sleep still in progress, is open.
 * Until noon, last night can still be logged, so it isn't counted as missing yet and yesterday stays open.
 */
class SleepModel(val doc: SleepDoc, val now: Now) {
    val zone = now.zone
    val today: LocalDate = now.today
    val prefs: Prefs get() = doc.prefs

    /** The sleeps that count, sorted by wake-up */
    val entries: List<Entry> = doc.active(zone)

    /** Minutes asleep on each calendar day (a sleep across midnight counts on both days; naps included) */
    val byDay: Map<LocalDate, Int>

    /** Nights in date order */
    val nights: List<Night>
    val nightByWake: Map<LocalDate, Night>

    /** The wake-up date of the first logged night */
    val start: LocalDate?

    /** When the sleep in progress started (ignored if it's more than 5 minutes ahead of now) */
    val pending: LocalDateTime?

    /** It's morning and last night isn't logged yet */
    val waiting: Boolean

    /** Nights not logged, by wake-up date, from the day after the first night to today */
    val missing: Set<LocalDate>

    private val pendingDay: LocalDate?

    init {
        val days = HashMap<LocalDate, Int>()
        val groups = LinkedHashMap<LocalDate, MutableList<Entry>>()
        for (e in entries) {
            for ((d, m) in Time.split(e.bed, e.wake, zone)) days[d] = (days[d] ?: 0) + m
            val w = Time.nightOf(e.bed) ?: continue
            groups.getOrPut(w) { ArrayList() } += e
        }
        byDay = days
        nights = groups.map { (w, parts) ->
            var bed = parts[0].bed
            var wake = parts[0].wake
            var mins = 0
            for (e in parts) {
                if (e.bed < bed) bed = e.bed
                if (e.wake > wake) wake = e.wake
                mins += Time.minutes(e.bed, e.wake, zone)
            }
            val rated = parts.filter { it.q != 0 }
            Night(w, bed, wake, mins, Time.minutes(bed, wake, zone), parts, if (rated.isNotEmpty()) mean(rated.map { it.q.toDouble() }) else 0.0, parts.any { it.q == 0 })
        }.sortedBy { it.wakeDay }
        nightByWake = nights.associateBy { it.wakeDay }
        start = nights.firstOrNull()?.wakeDay
        pending = doc.pending.bed?.takeIf { Time.ms(it, zone) <= now.ms + 300000 }
        pendingDay = pending?.toLocalDate()
        waiting = now.isMorning && !nightByWake.containsKey(today)
        val inProgress = pending?.let { Time.nightOf(it) }
        val mn = TreeSet<LocalDate>()
        if (start != null) {
            var w = start.plusDays(1)
            while (w <= today) {
                if (!nightByWake.containsKey(w) && !(inProgress != null && w >= inProgress) && !(waiting && w == today)) mn += w
                w = w.plusDays(1)
            }
        }
        missing = mn
    }

    fun goalOn(d: LocalDate): Int = Goals.goalOn(prefs.goals, d)

    /** Minutes asleep on a day */
    fun sleptOn(d: LocalDate): Int = byDay[d] ?: 0

    fun status(d: LocalDate): DayStatus = when {
        start == null || d < start -> DayStatus.BEFORE
        d > today -> DayStatus.FUTURE
        d == today || (pendingDay != null && d >= pendingDay) || (waiting && d == today.minusDays(1)) -> DayStatus.OPEN
        nightByWake.containsKey(d) && nightByWake.containsKey(d.plusDays(1)) -> DayStatus.DONE
        else -> DayStatus.MISSING
    }

    fun stats(days: List<LocalDate>): Stats {
        var tot = 0; var n = 0; var met = 0; var miss = 0; var need = 0
        for (d in days) {
            if (status(d) == DayStatus.DONE) {
                val v = sleptOn(d)
                val g = goalOn(d)
                tot += v; n++; need += g
                if (v >= g) met++
            }
            if (d in missing) miss++
        }
        return Stats(tot, n, met, miss, need - tot)
    }

    /** Nights not logged in the last two weeks, oldest first (the chips on Today) */
    fun missingRecent(): List<LocalDate> {
        val lo = today.minusDays(13)
        return missing.filter { it >= lo && it < today }
    }

    /** Nights with a wake-up date in the last [days] days, today included */
    fun recentNights(days: Int): List<Night> {
        val lo = today.minusDays(days.toLong())
        return nights.filter { it.wakeDay > lo && it.wakeDay <= today }
    }

    /** Usual bedtime and wake-up ("23:00", "07:00"): medians of the last 14 logged nights, to 5 minutes */
    fun usual(): Pair<String, String> {
        val n = nights.takeLast(14)
        if (n.isEmpty()) return "23:00" to "07:00"
        return hhmm(r5(median(n.map { adj(Time.tod(it.bed)).toDouble() })).toDouble()) to hhmm(r5(median(n.map { Time.tod(it.wake).toDouble() })).toDouble())
    }

    /** The night to log for an incomplete day: that night, or the next one */
    fun nightToLog(d: LocalDate): LocalDate? = when {
        d in missing -> d
        d.plusDays(1) in missing -> d.plusDays(1)
        else -> null
    }

    /** Today's summary over the 14 days before today. Its night count covers the last 14 nights, today's included. */
    fun twoWeeks(): Stats {
        val days = (14 downTo 1).map { today.minusDays(it.toLong()) }
        val lo = today.minusDays(14)
        return stats(days).copy(missing = missing.count { it > lo })
    }

    /** Minutes asleep so far in the sleep in progress */
    fun asleepFor(): Double = pending?.let { Math.max(0.0, (now.ms - Time.ms(it, zone)) / 60000.0) } ?: 0.0
}
