package com.resz073.sleeptracker.show

import com.resz073.sleeptracker.engine.Js
import com.resz073.sleeptracker.engine.Plan
import com.resz073.sleeptracker.engine.Planner
import com.resz073.sleeptracker.engine.SleepModel
import com.resz073.sleeptracker.engine.Time
import com.resz073.sleeptracker.engine.Words
import java.time.LocalDate

/** The tonight card: asleep, a bedtime to aim for, or nothing set yet */
class Tonight(
    val mode: Mode,
    val label: String,
    val big: String,
    val bigSmall: Boolean,
    val lines: List<Rich>,
    val strip: Drawing?,
) {
    enum class Mode { ASLEEP, PLAN, NONE }

    /** Button labels, in order */
    val buttons: List<String> get() = when (mode) {
        Mode.ASLEEP -> listOf("I’m awake", "Cancel")
        Mode.PLAN -> listOf("Going to sleep")
        Mode.NONE -> listOf("Going to sleep", "Set wake-up")
    }

    fun marked() = label + big + lines.joinToString("") { it.marked() } + buttons.joinToString("")
}

/** Last night: logged (ring, times, against the goal, rating), or a prompt to log it */
class LastNight(
    val logged: Boolean,
    val ring: Drawing?,
    val times: String,
    val versusGoal: Rich,
    val cycles: String,
    /** Rounded rating for the stars (0 when not rated) and the exact one for its label */
    val stars: Int,
    val rating: Double,
    val unrated: Boolean,
    val wakeDay: LocalDate,
) {
    fun marked(): String = if (!logged) "Last night" + "Not logged yet." + "Log last night"
    else "Last night" + times + versusGoal.marked() + cycles + (if (rating != 0.0) "★★★★★" else "") + (if (unrated) "How did you sleep?12345" else "")

    /** "Rated 3.5 out of 5" */
    val ratingLabel: String get() = "Rated " + Js.num(Js.round(rating * 10) / 10.0) + " out of 5"
}

/** The last 14 days card */
class Debt(val value: String, val tone: Tone, val label: String, val stats: List<Stat>, val mini: Drawing) {
    val className: String get() = when (tone) { Tone.BAD -> "bad"; Tone.GOOD -> "good"; else -> "" }
    fun statsMarked() = stats.joinToString("") { it.marked() }
}

/** What the Today screen shows (version 1's app.js renderTonight and renderToday) */
object TodayShow {
    /** The date under the title: "Saturday 14 November" */
    fun header(m: SleepModel): String = Words.dayLong(m.today)

    fun tonight(m: SleepModel, plan: Plan?): Tonight {
        val t = m.today
        val pend = m.pending
        if (pend != null) {
            val el = m.asleepFor()
            val pd = pend.toLocalDate()
            val line = rich { b(Words.hm(el)); t(" so far" + (if (pd < t.minusDays(1)) ", started " + Words.day(pd) else "")) }
            return Tonight(Tonight.Mode.ASLEEP, "Sleeping since", Words.clock(Time.tod(pend)), false, listOf(line), Charts.strip(m, plan, false))
        }
        if (plan != null) {
            val d = Planner.untilBedtime(plan, m.now)
            val first = rich {
                when {
                    Planner.pastBedtime(d) -> { b(Words.short(1440 - d)); t(" past bedtime.") }
                    d < 5 -> t("It’s bedtime.")
                    else -> { t("In "); b(Words.short(d)); t(".") }
                }
                t(" Wake up at "); b(Words.clock(plan.wake)); t(".")
            }
            val lines = if (plan.steps > 0) listOf(first, rich { t("A step toward your "); b(Words.clock(plan.target)); t(" target.") }) else listOf(first)
            return Tonight(Tonight.Mode.PLAN, "Bedtime tonight", Words.clock(plan.tonight), false, lines, Charts.strip(m, plan, false))
        }
        return Tonight(Tonight.Mode.NONE, "Bedtime tonight", "Not set yet", true, listOf(Rich.of("Log 3 nights, or set your wake-up time in Schedule.")), null)
    }

    /** Whether to ask for a birth year (until it's set or "Not now" was chosen on this phone) */
    fun goalCard(m: SleepModel, dismissed: Boolean): Boolean = m.prefs.born == null && !dismissed

    /** Last night's card, or null while asleep with last night not logged */
    fun lastNight(m: SleepModel): LastNight? {
        val t = m.today
        val ln = m.nightByWake[t]
        if (ln != null) {
            val g = m.goalOn(t)
            val d = ln.minutes
            val diff = g - d
            val vs = if (Math.abs(diff) < 5) "Right on your goal" else Words.short(diff) + (if (diff > 0) " under your goal" else " over your goal")
            return LastNight(
                true, Charts.ring(d, g), Words.clock(Time.tod(ln.bed)) + " – " + Words.clock(Time.tod(ln.wake)), rich { c(vs, deficitTone(diff)) },
                (if (ln.parts.size > 1) "In " + ln.parts.size + " parts, about " else "About ") + Words.cycles(d) + " sleep cycles",
                Js.round(ln.quality).toInt(), ln.quality, ln.unrated, t,
            )
        }
        if (m.pending != null) return null
        return LastNight(false, null, "", Rich.EMPTY, "", 0, 0.0, false, t)
    }

    /** Nights not logged in the last two weeks, oldest first: the night and its name ("Thu 12 – Fri 13") */
    fun missing(m: SleepModel): List<Pair<LocalDate, String>> = m.missingRecent().map { it to Words.night(it) }

    fun debt(m: SleepModel): Debt {
        val t = m.today
        val st = m.twoWeeks()
        val days = (14 downTo 1).map { t.minusDays(it.toLong()) } + t
        val mini = Charts.mini(m, days)
        if (st.days == 0) return Debt("–", Tone.NONE, if (m.start != null) "No complete days yet" else "Log your first night to start", emptyList(), mini)
        val stats = ArrayList<Stat>()
        stats += Stat(Words.hm(st.average), "average per day")
        stats += Stat(st.metGoal.toString() + " of " + st.days, "days at goal")
        if (st.missing > 0) stats += Stat(st.missing.toString(), Words.nightsNotLogged(st.missing), Tone.WARN)
        return Debt(Words.hm(st.balance), deficitTone(st.balance), if (st.balance > 0) "sleep debt" else "ahead of your goal", stats, mini)
    }
}
