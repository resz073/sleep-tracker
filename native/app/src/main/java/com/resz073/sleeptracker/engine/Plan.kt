package com.resz073.sleeptracker.engine

import java.time.LocalDate

/**
 * Tonight's bedtime plan. Clock values are minutes after midnight; bedtimes are on the night scale, where
 * times after midnight run on past 1440 (1:00 AM is 1500).
 *
 * @property wake wake-up time: the fixed one, or the usual one ([autoWake])
 * @property autoWake usual wake-up over the last 14 nights (needs 3 nights)
 * @property target wake-up minus the goal minus the time to fall asleep ([latency])
 * @property usualBed usual bedtime over the last 7 nights (or 14), null with fewer than 3 nights
 * @property tonight a step of 15 minutes from [usualBed] toward [target], or the target itself when close
 * @property direction 1 when bedtime has to move earlier, -1 later, 0 when there are no steps
 * @property steps 15-minute steps still to go after tonight
 * @property sleepMinutes how long tonight's plan sleeps
 * @property cycles tonight's 90-minute sleep cycles; [targetCycles] at the target
 * @property recentNights nights in the last 7 days; [onTarget] of them in bed within 30 minutes of the target
 */
data class Plan(
    val wake: Int,
    val autoWake: Int?,
    val fixed: Boolean,
    val goal: Int,
    val latency: Int,
    val target: Int,
    val tonight: Int,
    val usualBed: Int?,
    val direction: Int,
    val steps: Int,
    val sleepMinutes: Int,
    val cycles: Int,
    val targetCycles: Int,
    val recentNights: Int,
    val onTarget: Int,
    val nights14: List<Night>
)

/** One step on the way to the target bedtime, from about the given day */
data class PlanStep(val from: LocalDate, val bedtime: Int, val isTarget: Boolean)

/** A row of the last-14-nights diary: [offTarget] is how far that night's bedtime was from the target */
data class DiaryRow(val wakeDay: LocalDate, val missing: Boolean, val offTarget: Int?)

/**
 * The bedtime schedule. Wake-up is the anchor: the time set, or the usual (median) wake-up over the last 14
 * nights. Target bedtime = wake-up minus today's goal minus time to fall asleep. Tonight's bedtime moves
 * toward the target 15 minutes at a time, starting from the usual bedtime over the last 7 nights; as that
 * shifts, the next step follows, so the plan adapts to what actually happens.
 */
object Planner {
    /** The usual wake-up over the last 14 nights, to 5 minutes (needs 3 nights) */
    fun autoWake(model: SleepModel): Int? {
        val n14 = model.recentNights(14)
        return if (n14.size >= 3) r5(median(n14.map { Time.tod(it.wake).toDouble() })).toInt() else null
    }

    /** Null until there's a wake-up time: one set, or 3 nights logged in the last 14 days */
    fun plan(model: SleepModel): Plan? {
        val t = model.today
        val n14 = model.recentNights(14)
        val n7 = n14.filter { it.wakeDay > t.minusDays(7) }
        val autoWake = autoWake(model)
        val fixed = model.prefs.wake
        val wake = if (fixed != null) hmin(fixed) else autoWake ?: return null
        val goal = model.goalOn(t)
        val lat = model.prefs.lat
        val target = adj(mod(wake - goal - lat)).toInt()
        val base = if (n7.size >= 3) n7 else if (n14.size >= 3) n14 else null
        val cur = base?.let { b -> r5(median(b.map { adj(Time.tod(it.bed)).toDouble() })).toInt() }
        var tonight = target
        var steps = 0
        var dir = 0
        if (cur != null && Math.abs(cur - target) > 15) {
            dir = Integer.signum(cur - target)
            tonight = cur - dir * 15
            steps = Math.ceil((Math.abs(cur - target) - 15) / 15.0).toInt()
        }
        val sleepT = wake + 1440 - tonight - lat
        val onT = n7.count { Math.abs(adj(Time.tod(it.bed)) - target) <= 30 }
        return Plan(
            wake, autoWake, fixed != null, goal, lat, target, tonight, cur, dir, steps, sleepT,
            Math.max(1, Js.round(sleepT / 90.0).toInt()), Math.max(1, Js.round(goal / 90.0).toInt()), n7.size, onT, n14
        )
    }

    /** Minutes from now until tonight's bedtime on the clock (0 to 1439) */
    fun untilBedtime(plan: Plan, now: Now): Int = ((mod(plan.tonight) - now.minuteOfDay + 1440) % 1440).toInt()

    /** Bedtime was less than 4 hours ago */
    fun pastBedtime(until: Int): Boolean = until > 1440 - 240

    /** The next steps to the target, shown as at most 3, then the target. A step comes about every 4 nights. */
    fun steps(plan: Plan, today: LocalDate): List<PlanStep> {
        val out = ArrayList<PlanStep>()
        val shown = Math.min(plan.steps, 3)
        for (k in 1..shown) {
            val last = k == plan.steps
            out += PlanStep(today.plusDays(4L * k), if (last) plan.target else plan.tonight - plan.direction * 15 * k, last)
        }
        if (plan.steps > shown) out += PlanStep(today.plusDays(4L * plan.steps), plan.target, true)
        return out
    }

    /** Wind down from 30 minutes before tonight's bedtime */
    fun windDown(plan: Plan): Int = plan.tonight - 30

    /** Nights in the last 14 that were split into parts (a note shows from 3) */
    fun splitNights(plan: Plan): Int = plan.nights14.count { it.parts.size > 1 }

    /** The usual weekend wake-up, when it's more than an hour after the plan's wake-up (needs 2 weekend nights in 4 weeks) */
    fun lateWeekendWake(model: SleepModel, plan: Plan): Double? {
        val wd = model.prefs.weekendDays
        val weekend = model.recentNights(28).filter { Time.weekday(it.wakeDay) in wd }
        if (weekend.size < 2) return null
        val bw = median(weekend.map { Time.tod(it.wake).toDouble() })
        return if (bw - plan.wake > 60) bw else null
    }

    /** The last 14 nights, newest first; null before any night is logged */
    fun diary(model: SleepModel, plan: Plan?): List<DiaryRow>? {
        if (model.nights.isEmpty()) return null
        return (0 until 14).map { i ->
            val w = model.today.minusDays(i.toLong())
            val n = model.nightByWake[w]
            DiaryRow(w, w in model.missing, if (n != null && plan != null) adj(Time.tod(n.bed)) - plan.target else null)
        }
    }
}
