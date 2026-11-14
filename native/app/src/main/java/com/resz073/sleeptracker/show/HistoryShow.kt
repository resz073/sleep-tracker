package com.resz073.sleeptracker.show

import com.resz073.sleeptracker.engine.History
import com.resz073.sleeptracker.engine.HistoryWeek
import com.resz073.sleeptracker.engine.Js
import com.resz073.sleeptracker.engine.SleepModel
import com.resz073.sleeptracker.engine.Time
import com.resz073.sleeptracker.engine.Words
import java.time.LocalDate

/** An entry in History */
class HistoryRow(val id: String, val length: String, val nap: Boolean, val rating: String?, val time: String, val cycles: String, val countsAs: String?) {
    fun marked() = length + (if (nap) "Nap" else "") + (rating ?: "Not rated") + time + cycles + (countsAs ?: "") + "✕"
}

/** A week of History (Saturday to Friday), newest entries first */
class HistoryView(
    val week: LocalDate?,
    val title: String,
    val total: String,
    val count: String,
    val canGoBack: Boolean,
    val canGoForward: Boolean,
    val rows: List<HistoryRow>,
) {
    val empty: Boolean get() = week == null

    fun marked() = if (empty) "No entries yet" + "Tap Add entry to log a night, or use Going to sleep on Today."
    else "‹[b]" + title + "[/b]›[b]" + total + "[/b]" + count + rows.joinToString("") { it.marked() }
}

/** What the History screen shows (src/app.js renderHistory) */
object HistoryShow {
    private fun weekOf(weeks: List<HistoryWeek>, selected: LocalDate?): HistoryWeek? = weeks.firstOrNull { it.start == selected } ?: weeks.firstOrNull()

    /** The [selected] week, or the newest one with entries */
    fun view(m: SleepModel, selected: LocalDate?): HistoryView {
        val weeks = History.weeks(m)
        val w = weekOf(weeks, selected) ?: return HistoryView(null, "", "", "", false, false, emptyList())
        val i = weeks.indexOf(w)
        val zone = m.zone
        val rows = w.entries.map { e ->
            val d = Time.minutes(e.bed, e.wake, zone)
            val sp = Time.split(e.bed, e.wake, zone)
            val bd = e.bed.toLocalDate()
            val wd = e.wake.toLocalDate()
            HistoryRow(
                e.id, Words.hm(d), History.isNap(e), if (e.q != 0) "★ " + e.q else null,
                Words.day(bd) + ", " + Words.clock(Time.tod(e.bed)) + " – " + (if (wd != bd) Words.day(wd) + ", " else "") + Words.clock(Time.tod(e.wake)),
                Js.toFixed(d / 90.0, 1) + " cycles",
                if (sp.size > 1) "Counts as " + sp.joinToString(" and ") { Words.hm(it.second) + " on " + Words.dayShort(it.first) } else null,
            )
        }
        val n = w.entries.size
        return HistoryView(w.start, Words.week(w.start), Words.hm(w.minutes), n.toString() + (if (n > 1) " entries" else " entry"), i < weeks.size - 1, i > 0, rows)
    }

    /** The week before ([by] -1) or after (+1) [week] that has entries, or null */
    fun step(m: SleepModel, week: LocalDate?, by: Int): LocalDate? {
        val weeks = History.weeks(m)
        val w = weekOf(weeks, week) ?: return null
        return weeks.getOrNull(weeks.indexOf(w) + (if (by < 0) 1 else -1))?.start
    }
}
