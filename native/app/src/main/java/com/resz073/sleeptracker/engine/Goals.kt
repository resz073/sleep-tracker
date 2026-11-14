package com.resz073.sleeptracker.engine

import java.time.LocalDate

/**
 * Sleep a night by age, from the National Sleep Foundation (2015): the recommended range, and the wider
 * range it says "may be appropriate". The goal can be set anywhere in the wider range, in 15-minute steps.
 */
data class AgeBand(val lo: Int, val hi: Int, val name: String, val recLo: Int, val recHi: Int, val okLo: Int, val okHi: Int, val default: Int)

/**
 * The sleep goal. Each change applies from the day it was made, so changing the goal never rewrites past
 * days; the very first goal also covers the days before it. Without a birth year the goal is 8 hours.
 */
object Goals {
    const val DEFAULT = 480
    const val FIRST_DAY = "0000-01-01"
    const val MIN_AGE = 13
    /** The oldest age offered in the birth year menu */
    const val MAX_AGE = 100

    val BANDS = listOf(
        AgeBand(13, 17, "13–17", 480, 600, 420, 660, 540),
        AgeBand(18, 25, "18–25", 420, 540, 360, 660, 480),
        AgeBand(26, 64, "26–64", 420, 540, 360, 600, 480),
        AgeBand(65, 150, "65 and over", 420, 480, 300, 540, 450)
    )

    /** The goal for a day ("YYYY-MM-DD") */
    fun goalOn(goals: List<Goal>, day: String): Int {
        var g = DEFAULT
        for (x in goals) { if (x.from <= day) g = x.min else break }
        return g
    }
    fun goalOn(goals: List<Goal>, day: LocalDate): Int = goalOn(goals, Time.day(day))

    /** The age reached this year */
    fun age(born: Int?, year: Int): Int? = born?.let { year - it }

    fun band(born: Int?, year: Int): AgeBand? = age(born, year)?.let { a -> BANDS.firstOrNull { a >= it.lo && a <= it.hi } }

    /** A birth year as stored, or null unless the person is 13 to 120 this year */
    fun normBorn(v: Any?, year: Int): Int? {
        val x = Js.toNumber(v)
        if (x.isNaN()) return null
        val y = Js.round(x)
        return if (y >= year - 120 && y <= year - MIN_AGE) y.toInt() else null
    }

    /**
     * Sets the goal from today, kept inside the age range in 15-minute steps. A change made the same day as
     * the last one replaces it, so trying a few values keeps only the last. Null when nothing changes
     * (no birth year, or the same goal).
     */
    fun change(prefs: Prefs, minutes: Double, now: Now): Prefs? {
        val b = band(prefs.born, now.year) ?: return null
        val min = Math.max(b.okLo.toLong(), Math.min(b.okHi.toLong(), Js.round(minutes / 15) * 15)).toInt()
        val t = Time.day(now.today)
        val gs = prefs.goals.toMutableList()
        val last = gs.lastOrNull()
        if (last != null && last.on == t) {
            if (last.min == min) return null
            if (last.from != FIRST_DAY && gs.size > 1 && gs[gs.size - 2].min == min) gs.removeAt(gs.size - 1) // back to the earlier goal
            else gs[gs.size - 1] = last.copy(min = min)
        } else {
            if (gs.isNotEmpty() && goalOn(prefs.goals, t) == min) return null
            gs += Goal(if (gs.isNotEmpty()) t else FIRST_DAY, min, t)
        }
        return prefs.copy(goals = gs, t = now.ms.toDouble())
    }

    /** The − and + buttons: 15 minutes less or more than today's goal */
    fun nudge(prefs: Prefs, by: Int, now: Now): Prefs? = change(prefs, (goalOn(prefs.goals, now.today) + by).toDouble(), now)

    /**
     * Keeps the goal inside the range for the person's age, e.g. after a birthday moves them to a new range,
     * and gives a first goal (the age's default) once a birth year is known. Null when nothing changes.
     */
    fun fit(prefs: Prefs, now: Now): Prefs? {
        val b = band(prefs.born, now.year) ?: return null
        if (prefs.goals.isEmpty()) return change(prefs, b.default.toDouble(), now)
        val g = goalOn(prefs.goals, now.today)
        return if (g < b.okLo || g > b.okHi) change(prefs, g.toDouble(), now) else null
    }

    /** The birth year menu: sets the year, then fits the goal to it. Null when the year isn't valid or hasn't changed. */
    fun setBorn(prefs: Prefs, year: Any?, now: Now): BornChange? {
        val v = normBorn(year, now.year) ?: return null
        if (v == prefs.born) return null
        val set = prefs.copy(born = v, t = now.ms.toDouble())
        val fitted = fit(set, now)
        return BornChange(fitted ?: set, first = prefs.born == null, goalChanged = fitted != null)
    }

    class BornChange(val prefs: Prefs, val first: Boolean, val goalChanged: Boolean)
}
