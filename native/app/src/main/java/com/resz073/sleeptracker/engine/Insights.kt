package com.resz073.sleeptracker.engine

import java.time.LocalDate

/* ---------- patterns (the Trends screen's lower cards) ---------- */

/** Usual bedtime and wake-up over the last 14 nights (medians, night scale) and how much each varies (± minutes) */
data class Timing(val bed: Double, val wake: Double, val bedSpread: Double, val wakeSpread: Double)

enum class Steadiness { STEADY, VARIES, IRREGULAR }

/** One side of the week: usual bedtime and wake-up, average night, average middle of the night */
data class WeekSide(val bed: Double, val wake: Double, val minutes: Double, val middle: Double, val nights: Int)

/** Workdays against the weekend; [shift] is how much later (or earlier, negative) weekend sleep runs */
data class WeekendShift(val workdays: WeekSide, val weekend: WeekSide, val shift: Double)

enum class QualityTest { BEDTIME_BEFORE_MIDNIGHT, MET_GOAL, NEAR_USUAL_BEDTIME }

/** Average rating of nights that pass a test against those that don't */
data class QualitySplit(val test: QualityTest, val yes: Double, val yesNights: Int, val no: Double, val noNights: Int)

/** Ratings over the last 60 nights; [splits] only for tests with 3 rated nights on each side */
data class Quality(val nights: Int, val average: Double, val usualBed: Double, val splits: List<QualitySplit>)

object Patterns {
    /** Needs 3 nights in the last 14 */
    fun timing(model: SleepModel): Timing? {
        val n14 = model.recentNights(14)
        if (n14.size < 3) return null
        val bed = n14.map { adj(Time.tod(it.bed)).toDouble() }
        val wake = n14.map { Time.tod(it.wake).toDouble() }
        return Timing(median(bed), median(wake), sd(bed), sd(wake))
    }

    fun steadiness(spread: Double): Steadiness = if (spread <= 30) Steadiness.STEADY else if (spread <= 60) Steadiness.VARIES else Steadiness.IRREGULAR

    /** Needs 2 workday and 2 weekend nights in the last 4 weeks. A weekend night is one whose wake-up day is a weekend day. */
    fun weekend(model: SleepModel): WeekendShift? {
        val wd = model.prefs.weekendDays
        val n28 = model.recentNights(28)
        val a = n28.filter { Time.weekday(it.wakeDay) !in wd }
        val b = n28.filter { Time.weekday(it.wakeDay) in wd }
        if (a.size < 2 || b.size < 2) return null
        fun side(g: List<Night>) = WeekSide(
            median(g.map { adj(Time.tod(it.bed)).toDouble() }),
            median(g.map { Time.tod(it.wake).toDouble() }),
            mean(g.map { it.minutes.toDouble() }),
            mean(g.map { adj(Time.tod(it.bed)) + it.span / 2.0 }),
            g.size
        )
        val sa = side(a)
        val sb = side(b)
        return WeekendShift(sa, sb, sb.middle - sa.middle)
    }

    /** Null until a night in the last 60 is rated */
    fun quality(model: SleepModel): Quality? {
        val r = model.recentNights(60).filter { it.quality != 0.0 }
        if (r.isEmpty()) return null
        val ub = mean(r.map { adj(Time.tod(it.bed)).toDouble() })
        val tests = listOf<Pair<QualityTest, (Night) -> Boolean>>(
            QualityTest.BEDTIME_BEFORE_MIDNIGHT to { n -> adj(Time.tod(n.bed)) < 1440 },
            QualityTest.MET_GOAL to { n -> n.minutes >= model.goalOn(n.wakeDay) },
            QualityTest.NEAR_USUAL_BEDTIME to { n -> Math.abs(adj(Time.tod(n.bed)) - ub) <= 30 }
        )
        val splits = tests.mapNotNull { (test, f) ->
            val yes = r.filter(f)
            val no = r.filterNot(f)
            if (yes.size < 3 || no.size < 3) null
            else QualitySplit(test, mean(yes.map { it.quality }), yes.size, mean(no.map { it.quality }), no.size)
        }
        return Quality(r.size, mean(r.map { it.quality }), ub, splits)
    }
}

/* ---------- trends periods ---------- */

/** The Trends tabs: a month, a week (Saturday to Friday), or the last 3 days. A period is named by its first day. */
enum class PeriodKind { MONTH, WEEK, LAST_3_DAYS }

object Periods {
    /** The period that holds today */
    fun current(kind: PeriodKind, today: LocalDate): LocalDate = when (kind) {
        PeriodKind.MONTH -> today.withDayOfMonth(1)
        PeriodKind.WEEK -> Time.weekStart(today)
        PeriodKind.LAST_3_DAYS -> today.minusDays(2)
    }

    /** The first period with data (for months and weeks) */
    fun first(kind: PeriodKind, start: LocalDate): LocalDate = when (kind) {
        PeriodKind.MONTH -> start.withDayOfMonth(1)
        PeriodKind.WEEK -> Time.weekStart(start)
        PeriodKind.LAST_3_DAYS -> start
    }

    fun step(kind: PeriodKind, p: LocalDate, n: Int): LocalDate = when (kind) {
        PeriodKind.MONTH -> p.plusMonths(n.toLong())
        PeriodKind.WEEK -> p.plusDays(7L * n)
        PeriodKind.LAST_3_DAYS -> p.plusDays(3L * n)
    }

    fun days(kind: PeriodKind, p: LocalDate): List<LocalDate> {
        val n = when (kind) {
            PeriodKind.MONTH -> p.lengthOfMonth()
            PeriodKind.WEEK -> 7
            PeriodKind.LAST_3_DAYS -> 3
        }
        return (0 until n).map { p.plusDays(it.toLong()) }
    }

    /** "November 2026", "14 Nov – 20 Nov", "12 Nov – 14 Nov" */
    fun title(kind: PeriodKind, p: LocalDate): String = when (kind) {
        PeriodKind.MONTH -> Words.month(p)
        PeriodKind.WEEK -> Words.week(p)
        PeriodKind.LAST_3_DAYS -> Words.dayShort(p) + " – " + Words.dayShort(p.plusDays(2))
    }

    /** Months or weeks with a complete day or a night not logged, newest first (the "Show all" list) */
    fun withData(model: SleepModel, kind: PeriodKind): List<Pair<LocalDate, Stats>> {
        val start = model.start ?: return emptyList()
        if (kind == PeriodKind.LAST_3_DAYS) return emptyList()
        val first = first(kind, start)
        val out = ArrayList<Pair<LocalDate, Stats>>()
        var q = current(kind, model.today)
        while (q >= first) {
            val s = model.stats(days(kind, q))
            if (s.days > 0 || s.missing > 0) out += q to s
            q = step(kind, q, -1)
        }
        return out
    }

    /** The goal label of the chart legend: "Goal 8h", or "Goal (changed)" if it changed within the period */
    fun goalLabel(model: SleepModel, days: List<LocalDate>): String {
        val gs = days.map { model.goalOn(it) }
        return if (gs.all { it == gs[0] }) "Goal " + Words.short(gs[0]) else "Goal (changed)"
    }

    /** Minutes asleep so far today, if today is in these days and still counting (0 otherwise) */
    fun liveToday(model: SleepModel, days: List<LocalDate>): Int {
        val t = model.today
        return if (t in days && model.status(t) == DayStatus.OPEN) model.sleptOn(t) else 0
    }

    /** Whether any of these days is still counting with some sleep */
    fun anyLive(model: SleepModel, days: List<LocalDate>): Boolean = days.any { model.status(it) == DayStatus.OPEN && model.sleptOn(it) != 0 }
}

/* ---------- history ---------- */

/** A week of the history list: entries newest first, and the minutes they add up to */
data class HistoryWeek(val start: LocalDate, val entries: List<Entry>, val minutes: Int)

object History {
    /** Weeks (Saturday to Friday, by wake-up date) that have entries, newest first */
    fun weeks(model: SleepModel): List<HistoryWeek> {
        val rev = model.entries.asReversed()
        // sortedDescending, not reversed(): on Android before 15 a Java reversed() would crash
        return model.entries.map { Time.weekStart(it.wake.toLocalDate()) }.distinct().sortedDescending().map { w ->
            val list = rev.filter { Time.weekStart(it.wake.toLocalDate()) == w }
            HistoryWeek(w, list, list.sumOf { Time.minutes(it.bed, it.wake, model.zone) })
        }
    }

    fun isNap(e: Entry): Boolean = Time.nightOf(e.bed) == null
}
