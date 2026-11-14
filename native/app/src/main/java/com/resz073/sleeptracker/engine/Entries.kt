package com.resz073.sleeptracker.engine

import java.time.LocalDateTime
import java.time.ZoneId

/** What the add-entry form says about the times entered */
sealed interface EntryCheck {
    /** A field is empty or not a time yet */
    data object Incomplete : EntryCheck
    /** Wake-up must be after bedtime and within 24 hours */
    data object Invalid : EntryCheck
    /** Wake-up time is still ahead */
    data object Future : EntryCheck
    /** Overlaps a sleep already logged */
    data class Overlaps(val other: Entry) : EntryCheck
    data class Fine(val minutes: Int) : EntryCheck
}

/** What "I'm awake" does */
sealed interface WakeUp {
    data object NotAsleep : WakeUp
    /** Under 10 minutes: nothing is logged */
    data object TooShort : WakeUp
    /** The times need checking in the form (over 24 hours, or overlapping): [bed] and [wake] prefill it */
    data class Check(val bed: LocalDateTime, val wake: LocalDateTime) : WakeUp
    data class Logged(val entry: Entry, val minutes: Int) : WakeUp
}

/** What the add-entry form starts with: dates "YYYY-MM-DD" and times "HH:MM". [fresh]: nothing to log, so it's the last hour. */
data class Suggestion(val bedDate: String, val bedTime: String, val wakeDate: String, val wakeTime: String, val fresh: Boolean)

/** The rules for adding and finishing sleeps */
object Entries {
    /** The first logged sleep (in wake-up order) that overlaps these times, other than [except] */
    fun overlap(bed: LocalDateTime, wake: LocalDateTime, entries: List<Entry>, except: String?, zone: ZoneId): Entry? {
        val a = Time.ms(bed, zone)
        val b = Time.ms(wake, zone)
        return entries.firstOrNull { it.id != except && Time.ms(it.bed, zone) < b && Time.ms(it.wake, zone) > a }
    }

    /** The form's check of the times entered ([editing] is the entry being edited, which may overlap itself) */
    fun check(bedDate: String, bedTime: String, wakeDate: String, wakeTime: String, editing: String?, model: SleepModel): EntryCheck {
        val bedText = bedDate + "T" + bedTime
        val wakeText = wakeDate + "T" + wakeTime
        if (!Time.shaped(bedText) || !Time.shaped(wakeText)) return EntryCheck.Incomplete
        val zone = model.zone
        val bed = Time.parse(bedText, zone)
        val wake = Time.parse(wakeText, zone)
        if (bed == null || wake == null || !SleepDoc.valid(bed, wake, zone)) return EntryCheck.Invalid
        if (Time.ms(wake, zone) > model.now.ms + 60000) return EntryCheck.Future
        overlap(bed, wake, model.entries, editing, zone)?.let { return EntryCheck.Overlaps(it) }
        return EntryCheck.Fine(Time.minutes(bed, wake, zone))
    }

    /** "I'm awake": logs the sleep in progress up to this minute, unless it's too short or needs checking */
    fun wakeUp(model: SleepModel, newId: String): WakeUp {
        val bed = model.doc.pending.bed ?: return WakeUp.NotAsleep
        val zone = model.zone
        val wake = model.now.minute
        val d = Time.minutes(bed, wake, zone)
        if (d < 10) return WakeUp.TooShort
        if (!SleepDoc.valid(bed, wake, zone) || overlap(bed, wake, model.entries, null, zone) != null) return WakeUp.Check(bed, wake)
        return WakeUp.Logged(Entry(newId, bed, wake, 0, model.now.ms.toDouble()), d)
    }

    /** The message the current app shows after "I'm awake" */
    fun wakeUpMessage(w: WakeUp): String? = when (w) {
        WakeUp.NotAsleep -> null
        WakeUp.TooShort -> "Under 10 minutes, so nothing was logged"
        is WakeUp.Check -> "Check these times, then save"
        is WakeUp.Logged -> "Logged " + Words.hm(w.minutes)
    }

    /**
     * What the add-entry form suggests: last night at the usual times if it isn't logged (and nobody is
     * asleep), else the latest night not logged, else the last hour, like a nap.
     */
    fun suggest(model: SleepModel): Suggestion {
        val t = model.today
        val miss = model.missingRecent()
        val night = when {
            !model.nightByWake.containsKey(t) && model.pending == null -> t
            miss.isNotEmpty() -> miss.last()
            else -> null
        }
        if (night != null) {
            val (bt, wt) = model.usual()
            return Suggestion(Time.day(if (bt > wt) night.minusDays(1) else night), bt, Time.day(night), wt, false)
        }
        val end = Time.at(Math.floorDiv(model.now.ms, 300000L) * 300000L, model.zone)
        val st = Time.at(Math.floorDiv(model.now.ms, 300000L) * 300000L - 3600000L, model.zone)
        return Suggestion(Time.day(st.toLocalDate()), Time.clockText(st), Time.day(end.toLocalDate()), Time.clockText(end), true)
    }

    /** The night to prefill when a "log this night" chip or reminder is tapped: usual times, ending that morning */
    fun prefill(model: SleepModel, wakeDay: java.time.LocalDate): Suggestion {
        val (bt, wt) = model.usual()
        return Suggestion(Time.day(if (bt > wt) wakeDay.minusDays(1) else wakeDay), bt, Time.day(wakeDay), wt, false)
    }

    /** The message the add-entry form shows for a check (plain text; the current app bolds the length) */
    fun checkMessage(c: EntryCheck): String = when (c) {
        EntryCheck.Incomplete -> ""
        EntryCheck.Invalid -> "Wake-up must be after bedtime and within 24 hours."
        EntryCheck.Future -> "Wake-up time is still ahead."
        is EntryCheck.Overlaps -> "This overlaps your sleep from " + Words.moment(c.other.bed) + ". Edit that entry instead."
        is EntryCheck.Fine -> "Sleep time " + Words.hm(c.minutes) + ", about " + Words.cycles(c.minutes) + " cycles"
    }
}
