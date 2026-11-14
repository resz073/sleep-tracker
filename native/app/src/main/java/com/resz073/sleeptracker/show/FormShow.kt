package com.resz073.sleeptracker.show

import com.resz073.sleeptracker.engine.Entries
import com.resz073.sleeptracker.engine.Entry
import com.resz073.sleeptracker.engine.EntryCheck
import com.resz073.sleeptracker.engine.Goals
import com.resz073.sleeptracker.engine.Now
import com.resz073.sleeptracker.engine.SleepModel
import com.resz073.sleeptracker.engine.Suggestion
import com.resz073.sleeptracker.engine.Time
import com.resz073.sleeptracker.engine.Words
import java.time.LocalDateTime

/**
 * The add/edit entry form as it's being filled in (src/app.js: bd, bt, wd, wt, q, edit, bdManual, fresh).
 * Dates are "YYYY-MM-DD" and times "HH:MM". Until the bedtime date is changed by hand ([bedDateManual]),
 * it follows the times: the day before the wake-up date when bedtime is later in the day than wake-up.
 */
data class EntryForm(
    val bedDate: String,
    val bedTime: String,
    val wakeDate: String,
    val wakeTime: String,
    val q: Int,
    /** The id of the entry being edited, or null when adding */
    val editing: String?,
    val bedDateManual: Boolean,
    /** Nothing to log, so the form shows the last hour and asks for times */
    val fresh: Boolean,
) {
    val title: String get() = if (editing != null) "Edit sleep" else "Add sleep"
    val saveLabel: String get() = if (editing != null) "Update" else "Save"

    fun check(m: SleepModel): EntryCheck = Entries.check(bedDate, bedTime, wakeDate, wakeTime, editing, m)
    fun canSave(m: SleepModel): Boolean = check(m) is EntryCheck.Fine

    /** The line under the form (calc) */
    fun message(m: SleepModel): Rich {
        val c = check(m)
        if (fresh && c !is EntryCheck.Fine) return Rich.of("Set the times for this entry.")
        return when (c) {
            EntryCheck.Incomplete -> Rich.EMPTY
            EntryCheck.Invalid -> rich { c("Wake-up must be after bedtime and within 24 hours.", Tone.BAD) }
            EntryCheck.Future -> Rich.of("Wake-up time is still ahead.")
            is EntryCheck.Overlaps -> rich { c("This overlaps your sleep from " + Words.moment(c.other.bed) + ". Edit that entry instead.", Tone.BAD) }
            is EntryCheck.Fine -> rich { t("Sleep time "); b(Words.hm(c.minutes)); t(", about " + Words.cycles(c.minutes) + " cycles") }
        }
    }

    private fun followTimes(): EntryForm {
        if (bedDateManual || wakeDate.isEmpty()) return this
        val wd = Time.parseDay(wakeDate) ?: return this
        return copy(bedDate = Time.day(if (bedTime > wakeTime) wd.minusDays(1) else wd))
    }

    fun withBedTime(t: String) = copy(bedTime = t, fresh = false).followTimes()
    fun withWakeTime(t: String) = copy(wakeTime = t, fresh = false).followTimes()
    fun withWakeDate(d: String) = copy(wakeDate = d, fresh = false).followTimes()
    fun withBedDate(d: String) = copy(bedDate = d, bedDateManual = true, fresh = false)
    fun withQuality(q: Int) = copy(q = q, fresh = false)

    /** The Now button: wake-up is this minute */
    fun withNow(now: Now) = copy(wakeTime = Time.clockText(now.local), wakeDate = Time.day(now.today), fresh = false).followTimes()

    companion object {
        /** Add entry: the suggested night or hour */
        fun add(s: Suggestion) = EntryForm(s.bedDate, s.bedTime, s.wakeDate, s.wakeTime, 3, null, s.fresh, s.fresh)

        /** Editing an entry */
        fun edit(e: Entry) = EntryForm(Time.day(e.bed.toLocalDate()), Time.clockText(e.bed), Time.day(e.wake.toLocalDate()), Time.clockText(e.wake), e.q, e.id, true, false)

        /** "I'm awake" found times to check: they go in the form, unrated */
        fun check(bed: LocalDateTime, wake: LocalDateTime) = EntryForm(Time.day(bed.toLocalDate()), Time.clockText(bed), Time.day(wake.toLocalDate()), Time.clockText(wake), 0, null, true, false)

        /** Quality choices, as in the current app's menu */
        val QUALITY = listOf(0 to "Not rated", 1 to "1 – Poor", 2 to "2", 3 to "3 – OK", 4 to "4", 5 to "5 – Great")
    }
}

/** The sleep goal setting */
class GoalSetting(val value: String, val note: Rich, val canDown: Boolean, val canUp: Boolean, val born: Int?, val years: List<Int>)

/** What Settings shows (src/app.js renderGoalUI and the birth year menu) */
object SettingsShow {
    fun goal(m: SleepModel): GoalSetting {
        val year = m.now.year
        val b = Goals.band(m.prefs.born, year)
        val g = m.goalOn(m.today)
        val note = if (b == null) Rich.of("Add your birth year to set a goal that fits your age. Until then, your goal is 8 hours.")
        else rich {
            t("Recommended for ages " + b.name + ": " + Words.range(b.recLo, b.recHi) + ".")
            if (g < b.recLo || g > b.recHi) { t(" "); c((if (g < b.recLo) "Less" else "More") + " than recommended for your age.", Tone.WARN) }
            if (g <= b.okLo || g >= b.okHi) t(" This is as far as the range goes.")
        }
        return GoalSetting(Words.hm(g), note, b != null && g > b.okLo, b != null && g < b.okHi, m.prefs.born, (year - Goals.MIN_AGE downTo year - Goals.MAX_AGE).toList())
    }
}
