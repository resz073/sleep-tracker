package com.resz073.sleeptracker.engine

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The current app's English wording for clock times, lengths of sleep and dates, in British style
 * ("Sat 14 Nov", "Sept"). Spelled out here rather than taken from the phone's locale data, which differs
 * between Android versions, so every phone shows the same text as the current app.
 */
object Words {
    private val MONTHS = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sept", "Oct", "Nov", "Dec")
    private val MONTHS_LONG = arrayOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    private val DAYS = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    private val DAYS_LONG = arrayOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

    private fun pad(n: Long) = if (n < 10) "0$n" else n.toString()

    /** A clock time from minutes after midnight, wrapping past midnight: 1385 → "11:05 PM" */
    fun clock(m: Double): String {
        val x = mod(m)
        val h = (x / 60) % 12
        return (if (h == 0L) 12 else h).toString() + ":" + pad(x % 60) + (if (x / 60 >= 12) " PM" else " AM")
    }
    fun clock(m: Int): String = clock(m.toDouble())
    fun clock(m: Long): String = clock(m.toDouble())

    /** A length, sign dropped: 425 → "7h 05m" */
    fun hm(m: Double): String {
        val x = Math.abs(Js.round(m))
        return (x / 60).toString() + "h " + pad(x % 60) + "m"
    }
    fun hm(m: Int): String = hm(m.toDouble())

    /** A short length: 45 → "45 min", 420 → "7h", 425 → "7h 05m" */
    fun short(m: Double): String {
        val x = Math.abs(Js.round(m))
        if (x < 60) return "$x min"
        val r = x % 60
        return (x / 60).toString() + "h" + (if (r != 0L) " " + pad(r) + "m" else "")
    }
    fun short(m: Int): String = short(m.toDouble())

    /** A tiny length: 45 → "45m", 60 → "1h", 65 → "1h05" */
    fun tiny(m: Double): String {
        val x = Math.abs(Js.round(m))
        if (x < 60) return x.toString() + "m"
        val r = x % 60
        return (x / 60).toString() + "h" + (if (r != 0L) pad(r) else "")
    }
    fun tiny(m: Int): String = tiny(m.toDouble())

    /** A goal in words: 480 → "8 hours", 450 → "7.5 hours", 465 → "7 hours 45 minutes" */
    fun goal(m: Int): String = when (m % 60) {
        0 -> (m / 60).toString() + " hours"
        30 -> ((m - 30) / 60).toString() + ".5 hours"
        else -> Math.floorDiv(m, 60).toString() + " hours " + (m % 60) + " minutes"
    }

    /** A goal in hours, for the chart: 450 → "7.5h", 470 → "7.83h" */
    fun hours(m: Int): String = BigDecimal(Js.toFixed(m / 60.0, 2)).stripTrailingZeros().toPlainString() + "h"

    /** "7–9 hours" */
    fun range(lo: Int, hi: Int): String = Js.num(lo / 60.0) + "–" + Js.num(hi / 60.0) + " hours"

    /** Sleep cycles of 90 minutes, one decimal: 425 → "4.7" */
    fun cycles(minutes: Int): String = Js.toFixed(minutes / 90.0, 1)

    /** "Sat 14 Nov" */
    fun day(d: LocalDate): String = DAYS[Time.weekday(d)] + " " + d.dayOfMonth + " " + MONTHS[d.monthValue - 1]

    /** "14 Nov" */
    fun dayShort(d: LocalDate): String = d.dayOfMonth.toString() + " " + MONTHS[d.monthValue - 1]

    /** "Saturday 14 November" */
    fun dayLong(d: LocalDate): String = DAYS_LONG[Time.weekday(d)] + " " + d.dayOfMonth + " " + MONTHS_LONG[d.monthValue - 1]

    /** "Sat" */
    fun weekday(d: LocalDate): String = DAYS[Time.weekday(d)]

    /** "November 2026" */
    fun month(d: LocalDate): String = MONTHS_LONG[d.monthValue - 1] + " " + d.year

    /** "14 Nov – 20 Nov" */
    fun week(start: LocalDate): String = dayShort(start) + " – " + dayShort(start.plusDays(6))

    /** A night by its wake-up date: "Fri 13 – Sat 14" */
    fun night(wakeDay: LocalDate): String {
        val p = wakeDay.minusDays(1)
        return DAYS[Time.weekday(p)] + " " + p.dayOfMonth + " – " + DAYS[Time.weekday(wakeDay)] + " " + wakeDay.dayOfMonth
    }

    /** "Sat 14 Nov, 11:05 PM" */
    fun moment(t: LocalDateTime): String = day(t.toLocalDate()) + ", " + clock(Time.tod(t))

    fun nightsNotLogged(n: Int): String = if (n > 1) "nights not logged" else "night not logged"
}
