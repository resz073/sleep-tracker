package com.resz073.sleeptracker.engine

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * The moment the engine works from and the time zone it reads local times in. Everything in the engine
 * takes one of these instead of reading the clock, so results can be checked for any moment and zone.
 */
class Now(val ms: Long, val zone: ZoneId) {
    val local: LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), zone)
    val today: LocalDate get() = local.toLocalDate()
    val year: Int get() = local.year
    val minuteOfDay: Int get() = local.hour * 60 + local.minute
    val isMorning: Boolean get() = local.hour < 12

    /** The current minute, as the app writes it when "I'm awake" is tapped */
    val minute: LocalDateTime get() = local.truncatedTo(ChronoUnit.MINUTES)

    companion object {
        fun system(): Now = Now(System.currentTimeMillis(), ZoneId.systemDefault())
    }
}

/**
 * Local times as the app stores them ("2026-11-14T23:05"), and the calendar rules of the current app:
 * which night a sleep belongs to, how a sleep splits across midnight, and weeks that start on Saturday.
 */
object Time {
    private val DT = Regex("""^(\d{4})-(\d\d)-(\d\d)T(\d\d):(\d\d)$""")
    private val DAY = Regex("""^(\d{4})-(\d\d)-(\d\d)$""")

    /** Whether text has the stored shape "YYYY-MM-DDTHH:MM", without checking it's a real time. */
    fun shaped(s: String?): Boolean = s != null && DT.matches(s)

    /**
     * A stored local time, or null unless it's a real date and a time the clock shows in this zone
     * (not skipped when clocks go forward). Years before 1000 aren't accepted, as in the current app.
     */
    fun parse(s: String?, zone: ZoneId): LocalDateTime? {
        val m = DT.matchEntire(s ?: return null) ?: return null
        val v = m.groupValues
        val y = v[1].toInt(); val mo = v[2].toInt(); val d = v[3].toInt(); val h = v[4].toInt(); val mi = v[5].toInt()
        if (y < 1000 || mo !in 1..12 || h > 23 || mi > 59 || d < 1 || d > YearMonth.of(y, mo).lengthOfMonth()) return null
        val t = LocalDateTime.of(y, mo, d, h, mi)
        return if (exists(t, zone)) t else null
    }

    /** A stored date "YYYY-MM-DD", or null unless it's a real date from the year 1000 on. */
    fun parseDay(s: String?): LocalDate? {
        val m = DAY.matchEntire(s ?: return null) ?: return null
        val v = m.groupValues
        val y = v[1].toInt(); val mo = v[2].toInt(); val d = v[3].toInt()
        if (y < 1000 || mo !in 1..12 || d < 1 || d > YearMonth.of(y, mo).lengthOfMonth()) return null
        return LocalDate.of(y, mo, d)
    }

    /** Whether the clock shows this time in this zone (it doesn't in the hour skipped when clocks go forward). */
    fun exists(t: LocalDateTime, zone: ZoneId): Boolean = zone.rules.getValidOffsets(t).isNotEmpty()

    /**
     * Milliseconds since 1970, as JavaScript's Date reads a local time: a time the clock shows twice (when it
     * goes back) means the first one, and a time it skips (when it goes forward) moves later by the skip.
     */
    fun ms(t: LocalDateTime, zone: ZoneId): Long = t.atZone(zone).toInstant().toEpochMilli()

    /** Minutes really slept between two local times, counting clock changes. */
    fun minutes(bed: LocalDateTime, wake: LocalDateTime, zone: ZoneId): Int = Js.round((ms(wake, zone) - ms(bed, zone)) / 60000.0).toInt()

    fun at(ms: Long, zone: ZoneId): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), zone)

    /** Minutes after midnight on the clock */
    fun tod(t: LocalDateTime): Int = t.hour * 60 + t.minute

    /** "2026-11-14T23:05" */
    fun text(t: LocalDateTime): String = day(t.toLocalDate()) + "T" + pad2(t.hour) + ":" + pad2(t.minute)

    /** "2026-11-14" */
    fun day(d: LocalDate): String = d.year.toString().padStart(4, '0') + "-" + pad2(d.monthValue) + "-" + pad2(d.dayOfMonth)

    /** "23:05" */
    fun clockText(t: LocalDateTime): String = pad2(t.hour) + ":" + pad2(t.minute)

    internal fun pad2(n: Int): String = if (n in 0..9) "0$n" else n.toString()

    /**
     * How a sleep's minutes fall on each calendar day. A day starts at midnight; if a clock change skips
     * midnight, at midnight read with the clock's earlier offset, as JavaScript's Date does.
     */
    fun split(bed: LocalDateTime, wake: LocalDateTime, zone: ZoneId): List<Pair<LocalDate, Int>> {
        val out = ArrayList<Pair<LocalDate, Int>>(2)
        var a = ms(bed, zone)
        val b = ms(wake, zone)
        while (a < b) {
            val d = at(a, zone).toLocalDate()
            val next = ms(d.plusDays(1).atStartOfDay(), zone)
            val end = minOf(next, b)
            out += d to Js.round((end - a) / 60000.0).toInt()
            a = end
        }
        return out
    }

    /**
     * Which night a sleep belongs to, named by that night's wake-up date. Sleep starting 7 PM to noon is
     * part of the night ending that morning (so split sleep is grouped); starts from noon to 7 PM are naps (null).
     */
    fun nightOf(bed: LocalDateTime): LocalDate? {
        val tb = tod(bed)
        return when {
            tb in 720 until 1140 -> null
            tb >= 1140 -> bed.toLocalDate().plusDays(1)
            else -> bed.toLocalDate()
        }
    }

    /** Day of the week counted the JavaScript way: Sunday 0 to Saturday 6 */
    fun weekday(d: LocalDate): Int = d.dayOfWeek.value % 7

    /** The Saturday a week starts on (weeks run Saturday to Friday) */
    fun weekStart(d: LocalDate): LocalDate = d.minusDays(((weekday(d) + 1) % 7).toLong())
}

/* Minute sums used across the engine, with the current app's rounding */

/** Clock minutes on a night scale: times before noon count as after midnight (+1440). */
internal fun adj(m: Int): Int = if (m < 720) m + 1440 else m
internal fun adj(m: Long): Long = if (m < 720) m + 1440 else m

/** Minutes wrapped onto the clock, 0 to 1439 */
internal fun mod(m: Double): Long = ((Js.round(m) % 1440) + 1440) % 1440
internal fun mod(m: Int): Long = mod(m.toDouble())

/** Rounded to 5 minutes */
internal fun r5(m: Double): Long = Js.round(m / 5) * 5

internal fun mean(a: List<Double>): Double {
    var s = 0.0
    for (x in a) s += x
    return s / a.size
}

/** Population standard deviation (0 for fewer than 2 values) */
internal fun sd(a: List<Double>): Double {
    if (a.size < 2) return 0.0
    val m = mean(a)
    return Math.sqrt(mean(a.map { (it - m) * (it - m) }))
}

internal fun median(a: List<Double>): Double {
    val s = a.sorted()
    val k = s.size shr 1
    return if (s.size % 2 == 1) s[k] else (s[k - 1] + s[k]) / 2
}

/** "07:30" to 450 */
internal fun hmin(s: String): Int = s.substring(0, 2).toInt() * 60 + s.substring(3, 5).toInt()

/** 450 to "07:30" */
internal fun hhmm(m: Double): String {
    val x = mod(m)
    return Time.pad2((x / 60).toInt()) + ":" + Time.pad2((x % 60).toInt())
}
