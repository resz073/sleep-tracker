package com.resz073.sleeptracker.show

import com.resz073.sleeptracker.engine.DayStatus
import com.resz073.sleeptracker.engine.Js
import com.resz073.sleeptracker.engine.Patterns
import com.resz073.sleeptracker.engine.PeriodKind
import com.resz073.sleeptracker.engine.Periods
import com.resz073.sleeptracker.engine.QualityTest
import com.resz073.sleeptracker.engine.SleepModel
import com.resz073.sleeptracker.engine.Stats
import com.resz073.sleeptracker.engine.Steadiness
import com.resz073.sleeptracker.engine.Words
import java.time.LocalDate

/** A month or week in the "Show all" list */
class PeriodRow(
    val period: LocalDate,
    val title: String,
    val tag: String?,
    val line: Rich,
    val balance: String?,
    val balanceTone: Tone,
    val balanceLabel: String?,
    val selected: Boolean,
) {
    fun marked() = "[b]" + title + "[/b]" + (tag ?: "") + line.marked() + (if (balance != null) markSpan(Span(balance, true, balanceTone)) + (balanceLabel ?: "") else "")
}

/** The Trends card for one period */
class TrendsView(
    val kind: PeriodKind,
    val period: LocalDate,
    val title: String,
    val canGoBack: Boolean,
    val canGoForward: Boolean,
    /** "6h 13m sleep debt", coloured; empty without complete days */
    val balance: Rich,
    /** The figures, or the picked day ("Sat 14 Nov: 7h 30m, …"); ends with "Log it" when [logNight] is set */
    val readout: Rich,
    val logNight: LocalDate?,
    /** The figures shown as a grid (null when a day is picked or there's nothing yet) */
    val stats: List<Stat>?,
    val legend: List<LegendItem>,
    val chart: Drawing,
    /** Days in the period (for a tap on the chart) */
    val days: Int,
    /** "Show all months" / "Hide all months" (null for the last 3 days, or with no data) */
    val allLabel: String?,
    val rows: List<PeriodRow>,
)

/** A figure in two halves to compare (Workdays / Weekend, Before 12 AM / After 12 AM) */
class Cell(val label: String, val big: String, val sub: String, val best: Boolean) {
    fun marked() = label + "[b]" + big + "[/b]" + sub
}

class TimingShow(val stats: List<Stat>?, val note: String?) {
    fun marked() = stats?.joinToString("") { it.marked() } ?: (note ?: "")
}

class WeekendShow(val cells: List<Cell>?, val text: Rich) {
    fun marked() = (cells?.joinToString("") { it.marked() } ?: "") + text.marked()
}

class QualityShow(val lead: Rich?, val rows: List<Pair<String, List<Cell>>>, val note: String?) {
    fun marked() = (lead?.marked() ?: "") + rows.joinToString("") { (l, c) -> l + c.joinToString("") { it.marked() } } + (note ?: "")
    val best: List<Boolean> get() = rows.flatMap { r -> r.second.map { it.best } }
}

/** What the Trends screen shows (version 1's app.js renderTrends, prow and renderPatterns) */
object TrendsShow {
    /** The period shown: [selected] kept between the first period with data and the current one */
    fun period(m: SleepModel, kind: PeriodKind, selected: LocalDate?): LocalDate? {
        val start = m.start ?: return null
        val last = Periods.current(kind, m.today)
        if (kind == PeriodKind.LAST_3_DAYS) return last
        val first = Periods.first(kind, start)
        var p = selected ?: last
        if (p > last) p = last
        if (p < first) p = first
        return p
    }

    private fun figures(stats: List<Stat>) = rich { stats.forEach { b(it.big, it.tone); t(it.label) } }

    /** The card for a period, with day [pick] (or -1) picked and the list of periods open or not; null before the first night */
    fun view(m: SleepModel, kind: PeriodKind, selected: LocalDate?, pick: Int, listOpen: Boolean): TrendsView? {
        val start = m.start ?: return null
        val t = m.today
        val nav = kind != PeriodKind.LAST_3_DAYS
        val p = period(m, kind, selected)!!
        val first = Periods.first(kind, start)
        val last = Periods.current(kind, t)
        val days = Periods.days(kind, p)
        val st = m.stats(days)
        val pk = if (pick >= days.size) -1 else pick
        val balance = if (st.days > 0) rich { b(Words.hm(st.balance), deficitTone(st.balance)); t(if (st.balance > 0) "sleep debt" else "ahead of goal") } else Rich.EMPTY
        var logNight: LocalDate? = null
        var stats: List<Stat>? = null
        val readout: Rich
        if (pk >= 0) {
            val d = days[pk]
            val s = m.status(d)
            val v = m.sleptOn(d)
            val g = m.goalOn(d)
            readout = rich {
                b(if (d == t) "Today" else Words.day(d)); t(": ")
                when (s) {
                    DayStatus.DONE -> {
                        t(Words.hm(v) + ", ")
                        c(if (Math.abs(g - v) < 5) "right on your " + Words.short(g) + " goal" else Words.short(g - v) + (if (g - v > 0) " under" else " over") + " your " + Words.short(g) + " goal", deficitTone(g - v))
                    }
                    DayStatus.OPEN -> t(if (v != 0) Words.hm(v) + " so far, still counting" else "day not finished")
                    DayStatus.MISSING -> {
                        val w = m.nightToLog(d)
                        c("incomplete", Tone.WARN)
                        if (w != null) { t(", the night " + Words.night(w) + " isn’t logged. "); t("Log it"); logNight = w }
                    }
                    DayStatus.FUTURE -> t("upcoming")
                    DayStatus.BEFORE -> t("before you started tracking")
                }
            }
        } else {
            val live = Periods.liveToday(m, days)
            if (st.days > 0 || st.missing > 0 || live != 0) {
                val list = ArrayList<Stat>()
                list += Stat(if (st.days > 0) Words.hm(st.average) else "–", "average per day")
                list += Stat(st.metGoal.toString() + " of " + st.days, "days at goal")
                if (live != 0) list += Stat(Words.hm(live), "today so far")
                if (st.missing > 0) list += Stat(st.missing.toString(), Words.nightsNotLogged(st.missing), Tone.WARN)
                stats = list
                readout = figures(list)
            } else readout = Rich.of("No complete days yet.")
        }
        val legend = ArrayList<LegendItem>()
        legend += LegendItem(LegendItem.Mark.LINE, "Actual")
        legend += LegendItem(LegendItem.Mark.GOAL, Periods.goalLabel(m, days))
        legend += LegendItem(LegendItem.Mark.DEFICIT, "Deficit")
        legend += LegendItem(LegendItem.Mark.SURPLUS, "Surplus")
        if (Periods.anyLive(m, days)) legend += LegendItem(LegendItem.Mark.LIVE, "So far")
        if (st.missing > 0) legend += LegendItem(LegendItem.Mark.MISSING, "Night not logged")
        var allLabel: String? = null
        var rows: List<PeriodRow> = emptyList()
        if (nav) {
            val per = Periods.withData(m, kind)
            if (per.isNotEmpty()) allLabel = (if (listOpen) "Hide all " else "Show all ") + (if (kind == PeriodKind.MONTH) "months" else "weeks")
            if (listOpen) rows = per.map { (q, s2) -> row(kind, q, s2, q == last, q == p) }
        }
        return TrendsView(kind, p, Periods.title(kind, p), nav && p > first, nav && p < last, balance, readout, logNight, stats, legend,
            Charts.trends(m, kind, days, pk), days.size, allLabel, rows)
    }

    private fun row(kind: PeriodKind, q: LocalDate, s: Stats, current: Boolean, selected: Boolean): PeriodRow {
        val line = rich {
            t(if (s.days > 0) "Avg " + Words.hm(s.average) + " a day, goal met " + s.metGoal + " of " + s.days + " days" else "No complete days")
            if (s.missing > 0) { t(", "); c(s.missing.toString() + " " + Words.nightsNotLogged(s.missing), Tone.WARN) }
        }
        val tag = if (current) (if (kind == PeriodKind.MONTH) "This month" else "This week") else null
        return PeriodRow(q, Periods.title(kind, q), tag, line, if (s.days > 0) Words.hm(s.balance) else null, deficitTone(s.balance),
            if (s.days > 0) (if (s.balance > 0) "sleep debt" else "ahead") else null, selected)
    }

    /** The day under a tap at [x] (chart coordinates), or -1 */
    fun dayAt(x: Float, days: Int): Int {
        if (days < 2) return -1
        return Math.max(0, Math.min(days - 1, Math.round((x - 30 - 8) / ((320 - 30 - 10 - 16f) / (days - 1)))))
    }

    private fun steady(v: Double, label: String): Stat {
        val s = Patterns.steadiness(v)
        val tone = when (s) { Steadiness.STEADY -> Tone.GOOD; Steadiness.VARIES -> Tone.WARN; Steadiness.IRREGULAR -> Tone.BAD }
        val word = when (s) { Steadiness.STEADY -> "Steady"; Steadiness.VARIES -> "Varies"; Steadiness.IRREGULAR -> "Irregular" }
        return Stat(word, label + " varies ±" + Words.short(v), tone)
    }

    fun timing(m: SleepModel): TimingShow {
        val tm = Patterns.timing(m) ?: return TimingShow(null, "Appears after 3 nights are logged.")
        return TimingShow(listOf(Stat(Words.clock(tm.bed), "Usual bedtime"), Stat(Words.clock(tm.wake), "Usual wake-up"), steady(tm.bedSpread, "Bedtime"), steady(tm.wakeSpread, "Wake-up")), null)
    }

    fun weekend(m: SleepModel): WeekendShow {
        val w = Patterns.weekend(m) ?: return WeekendShow(null, Rich.of("Needs 2 workday and 2 weekend nights from the last 4 weeks."))
        val a = w.workdays
        val b = w.weekend
        val cells = listOf(
            Cell("Workdays", Words.clock(a.bed) + " – " + Words.clock(a.wake), Words.hm(a.minutes) + " average, " + a.nights + " nights", false),
            Cell("Weekend", Words.clock(b.bed) + " – " + Words.clock(b.wake), Words.hm(b.minutes) + " average, " + b.nights + " nights", false),
        )
        val sh = w.shift
        val text = if (Math.abs(sh) < 15) Rich.of("Your weekend timing matches your workdays.")
        else rich { t("Your weekend sleep runs "); b(Words.short(sh) + " " + (if (sh > 0) "later" else "earlier"), if (Math.abs(sh) >= 60) Tone.WARN else Tone.NONE); t(" than on workdays.") }
        return WeekendShow(cells, text)
    }

    fun quality(m: SleepModel): QualityShow {
        val q = Patterns.quality(m) ?: return QualityShow(null, emptyList(), "Rate your nights to see what goes with good sleep.")
        val lead = rich { t("Average "); b("★ " + Js.toFixed(q.average, 1)); t(" from " + q.nights + " rated night" + (if (q.nights > 1) "s" else "") + " in the last 60") }
        val rows = q.splits.map { s ->
            val (label, yes, no) = when (s.test) {
                QualityTest.BEDTIME_BEFORE_MIDNIGHT -> Triple("Bedtime", "Before 12 AM", "After 12 AM")
                QualityTest.MET_GOAL -> Triple("Night length", "Met your goal", "Under your goal")
                QualityTest.NEAR_USUAL_BEDTIME -> Triple("Bedtime compared with your usual " + Words.clock(q.usualBed), "Within 30 min", "Off by more")
            }
            label to listOf(
                Cell(yes, "★ " + Js.toFixed(s.yes, 1), s.yesNights.toString() + " nights", s.yes - s.no >= .3),
                Cell(no, "★ " + Js.toFixed(s.no, 1), s.noNights.toString() + " nights", s.no - s.yes >= .3),
            )
        }
        return QualityShow(lead, rows, if (rows.isEmpty()) "Comparisons appear once there are 3 rated nights on each side." else null)
    }
}
