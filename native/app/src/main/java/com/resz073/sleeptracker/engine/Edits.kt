package com.resz073.sleeptracker.engine

import kotlin.random.Random

/** A change to the account's data and the message version 1 shows for it (null: none) */
class Change(val doc: SleepDoc, val message: String?)

/**
 * Version 1's buttons and settings, as changes to the account's data. Each change carries its time
 * ("t"), which decides which copy wins when copies are merged. A null result means nothing changes.
 */
object Edits {
    private val HM = Regex("""^([01]\d|2[0-3]):[0-5]\d$""")
    private const val DIGITS = "0123456789abcdefghijklmnopqrstuvwxyz"

    /** A new entry id like version 1's: the time in base 36 and 6 random characters */
    fun newId(now: Now, random: Random = Random): String =
        java.lang.Long.toString(now.ms, 36) + (1..6).map { DIGITS[random.nextInt(36)] }.joinToString("")

    /** Replaces the item with this id (moving it to the end), or adds it */
    fun put(doc: SleepDoc, item: Item): SleepDoc = doc.copy(items = doc.items.filter { it.id != item.id } + item)

    private fun t(now: Now) = now.ms.toDouble()

    /** "Going to sleep" (again: restarts from now) */
    fun goToSleep(doc: SleepDoc, now: Now): Change {
        val bed = now.minute
        return Change(doc.copy(pending = Pending(bed, t(now))), "Sleep started at " + Words.clock(Time.tod(bed)))
    }

    fun cancelSleep(doc: SleepDoc, now: Now): Change = Change(doc.copy(pending = Pending(null, t(now))), "Cancelled")

    /** What "I'm awake" did: [doc] has the sleep ended, and logged unless [outcome] says otherwise */
    class Woke(val doc: SleepDoc, val outcome: WakeUp, val message: String?)

    /**
     * "I'm awake". The sleep in progress ends either way. It's logged unless it's under 10 minutes, or it
     * needs checking (over 24 hours, or overlapping): then version 1 opens the add-entry form with
     * these times (outcome [WakeUp.Check]) instead of saving. Null when nobody is asleep.
     */
    fun wakeUp(model: SleepModel, newId: () -> String): Woke? {
        if (model.doc.pending.bed == null) return null
        val outcome = Entries.wakeUp(model, newId)
        val ended = model.doc.copy(pending = Pending(null, t(model.now)))
        val doc = if (outcome is WakeUp.Logged) put(ended, outcome.entry) else ended
        return Woke(doc, outcome, Entries.wakeUpMessage(outcome))
    }

    /** Rates last night's unrated parts (the "How did you sleep?" buttons). Null when last night isn't logged. */
    fun rate(model: SleepModel, q: Int): Change? {
        val night = model.nightByWake[model.today] ?: return null
        var doc = model.doc
        for (e in night.parts) if (e.q == 0) doc = put(doc, e.copy(q = q, t = t(model.now)))
        return Change(doc, "Rated $q out of 5")
    }

    /**
     * The add-entry form's Save: a new entry, or [editing] an existing one. Null (nothing saved) unless the
     * times pass the form's check: real, wake-up after bedtime within 24 hours, not ahead, no overlap.
     */
    fun save(model: SleepModel, bedDate: String, bedTime: String, wakeDate: String, wakeTime: String, q: Int, editing: String?, newId: () -> String): Change? {
        if (Entries.check(bedDate, bedTime, wakeDate, wakeTime, editing, model) !is EntryCheck.Fine) return null
        val bed = Time.parse(bedDate + "T" + bedTime, model.zone) ?: return null
        val wake = Time.parse(wakeDate + "T" + wakeTime, model.zone) ?: return null
        val edit = editing?.takeIf { it.isNotEmpty() }
        val entry = Entry(edit ?: newId(), bed, wake, q, t(model.now))
        return Change(put(model.doc, entry), if (edit != null) "Entry updated" else "Entry saved")
    }

    /** What a deletion removed, to put back with [restore] (Undo) */
    class Deletion(val doc: SleepDoc, val removed: Entry, val message: String)

    /** Deletes an entry, leaving a marker so the deletion reaches the account's other copies */
    fun delete(doc: SleepDoc, id: String, now: Now): Deletion? {
        val e = doc.items.firstOrNull { it.id == id && it is Entry } as Entry? ?: return null
        return Deletion(put(doc, Deleted(id, t(now))), e, "Entry deleted")
    }

    /** Undo after a deletion */
    fun restore(doc: SleepDoc, removed: Entry, now: Now): Change = Change(put(doc, removed.copy(t = t(now))), "Entry restored")

    /** The weekend menu (saved even when it's the same choice) */
    fun setWeekend(doc: SleepDoc, weekend: String, now: Now): Change? =
        if (weekend in Prefs.WEEKENDS) Change(doc.copy(prefs = doc.prefs.copy(weekend = weekend, t = t(now))), null) else null

    /** Wake-up "Automatic" */
    fun setWakeAutomatic(doc: SleepDoc, now: Now): Change? =
        if (doc.prefs.wake != null) Change(doc.copy(prefs = doc.prefs.copy(wake = null, t = t(now))), "Wake-up set to automatic") else null

    /** Wake-up "Set a time": starts from the usual wake-up, or 7:00 AM */
    fun setWakeFixed(model: SleepModel, now: Now): Change? {
        if (model.prefs.wake != null) return null
        val aw = Planner.plan(model)?.autoWake ?: 420
        return Change(model.doc.copy(prefs = model.prefs.copy(wake = hhmm(aw.toDouble()), t = t(now))), null)
    }

    /** The wake-up time field ("HH:MM") */
    fun setWakeTime(doc: SleepDoc, time: String, now: Now): Change? =
        if (HM.matches(time) && time != doc.prefs.wake) Change(doc.copy(prefs = doc.prefs.copy(wake = time, t = t(now))), "Wake-up set to " + Words.clock(hmin(time)))
        else null

    /** Time to fall asleep */
    fun setLatency(doc: SleepDoc, minutes: Int, now: Now): Change? =
        if (minutes in Prefs.LATENCIES && minutes != doc.prefs.lat) Change(doc.copy(prefs = doc.prefs.copy(lat = minutes, t = t(now))), null) else null

    /** The goal's − and + buttons (15 minutes) */
    fun nudgeGoal(doc: SleepDoc, by: Int, now: Now): Change? = Goals.nudge(doc.prefs, by, now)?.let { Change(doc.copy(prefs = it), null) }

    /** The birth year menu */
    fun setBorn(doc: SleepDoc, year: Any?, now: Now): Change? = Goals.setBorn(doc.prefs, year, now)?.let {
        Change(
            doc.copy(prefs = it.prefs),
            if (it.first) "Your goal is " + Words.goal(Goals.goalOn(it.prefs.goals, now.today)) + ". Adjust it below." else "Birth year updated"
        )
    }

    /** Imports a CSV backup; the data changes only if something was added */
    fun import(model: SleepModel, text: String, newId: () -> String): Change {
        val imp = Csv.import(text, model, newId)
        var doc = model.doc
        for (e in imp.added) doc = put(doc, e)
        return Change(doc, imp.message)
    }
}
