package com.resz073.sleeptracker.show

import com.resz073.sleeptracker.engine.Plan
import com.resz073.sleeptracker.engine.Planner
import com.resz073.sleeptracker.engine.SleepModel
import com.resz073.sleeptracker.engine.Words
import java.time.LocalDate

/** The bedtime card at the top of Schedule */
class ScheduleSky(val big: String, val bigSmall: Boolean, val lines: List<Rich>, val strip: Drawing?, val stepLine: Rich?) {
    fun marked() = "Your bedtime" + big + lines.joinToString("") { it.marked() } + (stepLine?.marked() ?: "")
}

/** A step of the plan: tonight, then every 4 nights or so until the target */
class StepRow(val dot: Dot, val text: String, val time: String, val day: LocalDate?) {
    enum class Dot(val key: String) { DOT("dot"), HOLLOW("hollow"), END("end") }
}

class PlanCard(val title: String, val lead: Rich, val steps: List<StepRow>, val stepsNote: String?, val steady: String?, val notes: List<Rich>) {
    fun marked() = title + lead.marked() + steps.joinToString("") { it.text + "[b]" + it.time + "[/b]" } + (stepsNote ?: "") + (steady ?: "") + notes.joinToString("") { it.marked() }
}

class DiaryCard(val lead: String?, val legend: List<LegendItem>, val chart: Drawing) {
    fun marked() = "Your last 14 nights" + (lead ?: "") + legend.joinToString("") { it.text }
}

/** The wake-up setting: automatic or a set time ("HH:MM"), what it means, and the time to fall asleep */
class WakeSetting(val fixed: Boolean, val time: String?, val note: String, val latency: Int)

/** What the Schedule screen shows (version 1's app.js renderSchedule and diary) */
object ScheduleShow {
    fun sky(m: SleepModel, plan: Plan?): ScheduleSky {
        if (plan == null) return ScheduleSky("Not set yet", true, listOf(Rich.of("Log at least 3 nights, or set your wake-up time below. Your bedtime appears here.")), null, null)
        val line = rich { t("Wake up at "); b(Words.clock(plan.wake)); t(" after "); b(Words.goal(plan.goal)); t(" of sleep, about " + plan.targetCycles + " sleep cycles.") }
        val step = if (plan.steps > 0) rich { t("Tonight, aim for "); b(Words.clock(plan.tonight)); t(". You’ll get to " + Words.clock(plan.target) + " 15 minutes at a time.") } else null
        return ScheduleSky(Words.clock(plan.target), false, listOf(line), Charts.strip(m, plan, true), step)
    }

    fun plan(m: SleepModel, plan: Plan?): PlanCard? {
        if (plan == null) return null
        val t = m.today
        val lead = rich {
            t("Your target is "); b(Words.clock(plan.target))
            t(": " + Words.goal(plan.goal) + " of sleep, your goal, plus " + plan.latency + " minutes to fall asleep before your " + Words.clock(plan.wake) + " wake-up.")
        }
        val steps = ArrayList<StepRow>()
        var stepsNote: String? = null
        var steady: String? = null
        if (plan.steps > 0) {
            steps += StepRow(StepRow.Dot.DOT, "Tonight", Words.clock(plan.tonight), t)
            for (s in Planner.steps(plan, t)) {
                steps += StepRow(if (s.isTarget) StepRow.Dot.END else StepRow.Dot.HOLLOW, (if (s.isTarget) "Target, from about " else "From about ") + Words.day(s.from), Words.clock(s.bedtime), s.from)
            }
            stepsNote = "Each step is 15 minutes " + (if (plan.direction > 0) "earlier" else "later") + ". The next step comes once your usual bedtime catches up, about every 4 nights."
        } else steady = "Your usual bedtime is already close to your target. Going to bed at the same time every night matters most now."
        val notes = ArrayList<Rich>()
        notes += rich { t("Wind down from "); b(Words.clock(Planner.windDown(plan))); t(", 30 minutes before bed.") }
        val splitN = Planner.splitNights(plan)
        if (splitN >= 3) notes += Rich.of(splitN.toString() + " of your last 14 nights were split into parts. One block from " + Words.clock(plan.target) + " to " + Words.clock(plan.wake) + " fits your plan.")
        Planner.lateWeekendWake(m, plan)?.let { bw ->
            notes += Rich.of("On weekends you wake up around " + Words.clock(bw) + ". Keeping weekend wake-ups within an hour of " + Words.clock(plan.wake) + " makes the plan easier to keep.")
        }
        return PlanCard(if (plan.steps > 0) "Getting to your bedtime" else "Keep it steady", lead, steps, stepsNote, steady, notes)
    }

    fun diary(m: SleepModel, plan: Plan?): DiaryCard? {
        if (m.nights.isEmpty()) return null
        val lead = if (plan != null && plan.recentNights > 0) "In bed within 30 minutes of " + Words.clock(plan.target) + " on " + plan.onTarget + " of your last " + plan.recentNights + " nights." else null
        val legend = ArrayList<LegendItem>()
        legend += LegendItem(LegendItem.Mark.SLEEP, "Sleep")
        legend += LegendItem(LegendItem.Mark.NAP, "Nap")
        if (plan != null) legend += LegendItem(LegendItem.Mark.PLAN, "Planned sleep")
        return DiaryCard(lead, legend, Charts.diary(m, plan))
    }

    fun wake(m: SleepModel): WakeSetting {
        val fixed = m.prefs.wake
        val aw = Planner.autoWake(m)
        val note = when {
            fixed != null -> "Your plan is built around this time every day."
            aw != null -> "Uses your usual wake-up from the last 14 nights, now " + Words.clock(aw) + "."
            else -> "Log 3 nights to find your usual wake-up time, or set one."
        }
        return WakeSetting(fixed != null, fixed, note, m.prefs.lat)
    }
}
