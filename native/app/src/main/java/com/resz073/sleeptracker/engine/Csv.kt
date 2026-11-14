package com.resz073.sleeptracker.engine

/** Backup files: the current app's CSV export, and importing one (or a file in the same columns). */
object Csv {
    private val TIME = Regex("""^(\d{1,2}):(\d\d)$""")
    private val LINE = Regex("\r?\n")

    /** bed_date,bed_time,wake_date,wake_time,minutes,quality, one row per sleep in bedtime order */
    fun export(model: SleepModel): String {
        val rows = StringBuilder("bed_date,bed_time,wake_date,wake_time,minutes,quality\n")
        for (e in model.entries.sortedBy { Time.ms(it.bed, model.zone) }) {
            rows.append(Time.day(e.bed.toLocalDate())).append(',').append(Time.clockText(e.bed)).append(',')
                .append(Time.day(e.wake.toLocalDate())).append(',').append(Time.clockText(e.wake)).append(',')
                .append(Time.minutes(e.bed, e.wake, model.zone)).append(',').append(if (e.q != 0) e.q.toString() else "").append('\n')
        }
        return rows.toString()
    }

    /** What an import did. [problem] is set when the file couldn't be read at all. */
    class Import(val added: List<Entry>, val skipped: Int, val problem: String?) {
        /** The message the current app shows afterwards */
        val message: String
            get() = problem ?: if (added.isNotEmpty()) "Imported " + added.size + (if (skipped > 0) ", skipped $skipped already logged or invalid" else "") else "Nothing new to import"
    }

    /**
     * Reads a CSV with bed_date, bed_time, wake_date and wake_time columns (quality optional, in any order).
     * Rows that aren't valid, end in the future or overlap a sleep already logged (or an earlier row) are skipped.
     */
    fun import(text: String, model: SleepModel, newId: () -> String): Import {
        val lines = text.removePrefix("﻿").split(LINE).filter { Js.trim(it).isNotEmpty() }
        if (lines.size < 2) return Import(emptyList(), 0, "That file has no entries")
        fun cells(l: String) = l.split(",").map { Js.trim(it).let { s -> s.removePrefix("\"").let { x -> if (x.endsWith("\"")) x.dropLast(1) else x } } }
        val head = cells(lines[0].lowercase())
        val bi = head.indexOf("bed_date"); val bti = head.indexOf("bed_time"); val wi = head.indexOf("wake_date"); val wti = head.indexOf("wake_time"); val qi = head.indexOf("quality")
        if (bi < 0 || bti < 0 || wi < 0 || wti < 0) return Import(emptyList(), 0, "Columns not recognised. Use a CSV exported from Sleep Tracker.")
        fun tm(s: String?): String { val m = TIME.matchEntire(s ?: "") ?: return ""; return Time.pad2(m.groupValues[1].toInt()) + ":" + m.groupValues[2] }
        val zone = model.zone
        val taken = model.entries.toMutableList()
        val added = ArrayList<Entry>()
        var skip = 0
        for (l in lines.drop(1)) {
            val c = cells(l)
            val q = if (qi >= 0) Js.toNumber(c.getOrNull(qi)) else 0.0 // a missing cell reads as 0, like an unrated row
            val bed = Time.parse((c.getOrNull(bi) ?: "") + "T" + tm(c.getOrNull(bti)), zone)
            val wake = Time.parse((c.getOrNull(wi) ?: "") + "T" + tm(c.getOrNull(wti)), zone)
            if (bed == null || wake == null || !SleepDoc.valid(bed, wake, zone) || Time.ms(wake, zone) > model.now.ms + 60000 ||
                Entries.overlap(bed, wake, taken, null, zone) != null) { skip++; continue }
            val e = Entry(newId(), bed, wake, if (q >= 1 && q <= 5) Js.round(q).toInt() else 0, model.now.ms.toDouble())
            taken += e
            added += e
        }
        return Import(added, skip, null)
    }
}
