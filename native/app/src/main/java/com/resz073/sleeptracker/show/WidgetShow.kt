package com.resz073.sleeptracker.show

import com.resz073.sleeptracker.engine.Plan
import com.resz073.sleeptracker.engine.SleepModel
import com.resz073.sleeptracker.engine.Time
import com.resz073.sleeptracker.engine.Words

/**
 * The home screen widget, the Quick Settings tile and the app's shortcuts: tonight's bedtime (or the sleep
 * in progress), the last 14 days, and one button. Nothing on it counts down, so it stays right between updates.
 */
class WidgetView(
    val label: String,
    val big: String,
    val bigSmall: Boolean,
    val line: String,
    val debt: String?,
    /** What the button does: "sleep" (Going to sleep) or "awake" (I'm awake); null when signed out */
    val action: String?,
    val button: String?,
) {
    /** For telling whether it changed */
    val key: String get() = listOf(label, big, bigSmall, line, debt, action, button).joinToString("|")
}

object WidgetShow {
    fun of(m: SleepModel?, plan: Plan?): WidgetView {
        if (m == null) return WidgetView("Sleep Tracker", "Sign in", true, "Open the app to sign in.", null, null, null)
        val st = m.twoWeeks()
        val debt = if (st.days > 0) "Last 14 days: " + Words.hm(st.balance) + (if (st.balance > 0) " sleep debt" else " ahead of your goal") else null
        val pend = m.pending
        if (pend != null) return WidgetView("Sleeping since", Words.clock(Time.tod(pend)), false, "Tap I’m awake when you get up.", debt, "awake", "I’m awake")
        if (plan != null) return WidgetView("Bedtime tonight", Words.clock(plan.tonight), false, "Wake up at " + Words.clock(plan.wake) + ".", debt, "sleep", "Going to sleep")
        return WidgetView("Bedtime tonight", "Not set yet", true, "Log 3 nights, or set your wake-up time in Schedule.", debt, "sleep", "Going to sleep")
    }

    /** The Quick Settings tile: its label and the line under it */
    fun tile(m: SleepModel?, plan: Plan?): Pair<String, String?> {
        val pend = m?.pending
        return when {
            m == null -> "Going to sleep" to "Sign in first"
            pend != null -> "I’m awake" to "Since " + Words.clock(Time.tod(pend))
            plan != null -> "Going to sleep" to "Bedtime " + Words.clock(plan.tonight)
            else -> "Going to sleep" to null
        }
    }
}
