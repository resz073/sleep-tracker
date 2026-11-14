package com.resz073.sleeptracker.engine

import java.time.LocalDateTime
import java.time.ZoneId

/** Something stored in the account's entry list: a sleep, or the marker a deleted sleep leaves behind. */
sealed class Item {
    abstract val id: String
    /** When this was last changed (milliseconds since 1970). The newest change wins when copies are merged. */
    abstract val t: Double
}

/** One logged sleep. Bed and wake are local clock times; q is the 1–5 rating, or 0 when not rated. */
data class Entry(override val id: String, val bed: LocalDateTime, val wake: LocalDateTime, val q: Int, override val t: Double) : Item()

/** Left in place of a deleted sleep, so the deletion reaches the account's other copies. */
data class Deleted(override val id: String, override val t: Double) : Item()

/** "Going to sleep" was tapped at [bed] and "I'm awake" not yet; bed is null when nobody is asleep. */
data class Pending(val bed: LocalDateTime?, val t: Double)

/** A sleep goal in minutes, which applies from the day [from] ("0000-01-01" for the very first goal); [on] is the day it was set. */
data class Goal(val from: String, val min: Int, val on: String)

/** Settings saved in the account. [wake] is a fixed "HH:MM" wake-up time, or null for automatic. */
data class Prefs(val weekend: String, val wake: String?, val lat: Int, val born: Int?, val goals: List<Goal>, val t: Double) {
    /** The weekend days, Sunday 0 to Saturday 6 */
    val weekendDays: List<Int> get() = WEEKENDS[weekend] ?: WEEKENDS.getValue("fri-sat")

    companion object {
        val WEEKENDS = mapOf("fri-sat" to listOf(5, 6), "sat-sun" to listOf(6, 0), "fri" to listOf(5))
        /** Minutes to fall asleep that can be chosen */
        val LATENCIES = listOf(5, 10, 15, 20, 30, 45)
    }
}

/**
 * The account's document (users/{uid}): entries, the sleep in progress and settings, as the current app
 * reads and writes it. [read] turns the stored map into this, accepting exactly what the current app accepts.
 */
data class SleepDoc(val items: List<Item>, val pending: Pending, val prefs: Prefs) {

    /** The sleeps that count: valid, under 24 hours, one per bed and wake time, sorted by wake-up. */
    fun active(zone: ZoneId): List<Entry> {
        val by = LinkedHashMap<String, Entry>()
        for (e in items) {
            if (e !is Entry || !valid(e.bed, e.wake, zone)) continue
            val k = Time.text(e.bed) + "|" + Time.text(e.wake)
            val o = by[k]
            if (o == null || e.t > o.t) by[k] = e
        }
        return by.values.sortedWith(compareBy<Entry> { Time.ms(it.wake, zone) }.thenBy { Time.ms(it.bed, zone) })
    }

    /** The document as one line of JSON in a fixed order, to tell whether two copies hold the same data. */
    fun canon(): String = buildString {
        append("{\"entries\":[")
        items.sortedWith { x, y -> x.id.compareTo(y.id) }.forEachIndexed { i, e -> if (i > 0) append(','); append(json(e)) }
        append("],\"pending\":").append(json(pending)).append(",\"prefs\":").append(json(prefs)).append('}')
    }

    companion object {
        private val HHMM = Regex("""^\d\d:\d\d$""")
        private val HM = Regex("""^([01]\d|2[0-3]):[0-5]\d$""")
        private val DAY = Regex("""^\d{4}-\d\d-\d\d$""")

        val BLANK_PENDING = Pending(null, 0.0)
        val BLANK_PREFS = Prefs("fri-sat", null, 15, null, emptyList(), 0.0)
        val BLANK = SleepDoc(emptyList(), BLANK_PENDING, BLANK_PREFS)

        /** valid(): real times in this zone, wake after bed, at most 24 hours */
        fun valid(bed: LocalDateTime, wake: LocalDateTime, zone: ZoneId): Boolean {
            if (!Time.exists(bed, zone) || !Time.exists(wake, zone)) return false
            val d = Time.minutes(bed, wake, zone)
            return d in 1..1440
        }

        /** Reads the stored document (as Firestore gives it: maps, lists, strings, numbers) the way the current app does. */
        fun read(raw: Any?, now: Now): SleepDoc {
            val d = raw as? Map<*, *> ?: return BLANK
            val items = ArrayList<Item>()
            (d["entries"] as? List<*>)?.forEach { x -> item(x, now.zone)?.let { items += it } }
            val pending = when (val p = d["pending"]) {
                is Map<*, *> -> Pending(Time.parse(p["bed"] as? String, now.zone), number(p["t"]))
                is List<*> -> BLANK_PENDING
                else -> BLANK_PENDING
            }
            val prefs = when (val p = d["prefs"]) {
                is Map<*, *> -> {
                    val lat = Js.toNumber(p["lat"])
                    Prefs(
                        weekend = (p["weekend"] as? String)?.takeIf { it in Prefs.WEEKENDS } ?: "fri-sat",
                        wake = (p["wake"] as? String)?.takeIf { HM.matches(it) },
                        lat = Prefs.LATENCIES.firstOrNull { it.toDouble() == lat } ?: 15,
                        born = Goals.normBorn(p["born"], now.year),
                        goals = goals(p["goals"]),
                        t = number(p["t"])
                    )
                }
                is List<*> -> Prefs("fri-sat", null, 15, null, emptyList(), 0.0)
                else -> BLANK_PREFS
            }
            return merge(BLANK, SleepDoc(items, pending, prefs))
        }

        /** Merges two copies entry by entry; the newest change wins, so nothing saved on either side is lost. */
        fun merge(a: SleepDoc, b: SleepDoc): SleepDoc {
            val m = LinkedHashMap<String, Item>()
            for (e in a.items) m[e.id] = win(m[e.id], e)
            for (e in b.items) m[e.id] = win(m[e.id], e)
            return SleepDoc(m.values.toList(), win(a.pending, b.pending), win(a.prefs, b.prefs))
        }

        private fun win(a: Item?, b: Item): Item = if (a == null || pick(a.t, b.t, json(a), json(b)) == 1) b else a
        private fun win(a: Pending, b: Pending): Pending = if (pick(a.t, b.t, json(a), json(b)) == 1) b else a
        private fun win(a: Prefs, b: Prefs): Prefs = if (pick(a.t, b.t, json(a), json(b)) == 1) b else a

        /** 0 keeps the first, 1 takes the second: the later change, or on a tie the larger JSON text */
        private fun pick(ta: Double, tb: Double, ja: String, jb: String): Int = if (ta != tb) (if (ta > tb) 0 else 1) else if (ja >= jb) 0 else 1

        /** +x || 0 */
        private fun number(v: Any?): Double = Js.toNumber(v).let { if (it.isNaN() || it == 0.0) 0.0 else it }

        private fun item(x: Any?, zone: ZoneId): Item? {
            val m = x as? Map<*, *> ?: return null
            val id = m["id"]
            if (Js.truthy(m["del"]) && id is String && id.isNotEmpty()) return Deleted(id, number(m["t"]))
            var bed = m["bed"] as? String
            var wake = m["wake"] as? String
            val d = m["d"]
            // The first versions stored a date and two clock times: {d: "2026-11-14", bed: "23:30", wake: "06:30"}
            if (Js.truthy(d) && bed != null && wake != null && HHMM.matches(bed) && HHMM.matches(wake)) {
                val day = Time.parseDay(d as? String) ?: return null
                bed = Time.day(if (bed > wake) day.minusDays(1) else day) + "T" + bed
                wake = Time.day(day) + "T" + wake
            }
            val b = Time.parse(bed, zone) ?: return null
            val w = Time.parse(wake, zone) ?: return null
            val q = Js.toNumber(m["q"])
            return Entry(
                id = if (id is String && id.isNotEmpty()) id else "k" + Time.text(b) + "|" + Time.text(w),
                bed = b, wake = w,
                q = if (q >= 1 && q <= 5) Js.round(q).toInt() else 0,
                t = number(m["t"])
            )
        }

        private fun goals(v: Any?): List<Goal> {
            val a = v as? List<*> ?: return emptyList()
            return a.mapNotNull { g ->
                val m = g as? Map<*, *> ?: return@mapNotNull null
                val from = m["from"] as? String ?: return@mapNotNull null
                val on = m["on"] as? String ?: return@mapNotNull null
                val min = Js.toNumber(m["min"])
                if (!DAY.matches(from) || !DAY.matches(on) || !(min >= 240 && min <= 720)) null else Goal(from, Js.round(min).toInt(), on)
            }.sortedWith(compareBy<Goal>({ it.from }, { it.on })).takeLast(200)
        }

        internal fun json(e: Item): String = when (e) {
            is Entry -> "{\"id\":" + Js.quote(e.id) + ",\"bed\":" + Js.quote(Time.text(e.bed)) + ",\"wake\":" + Js.quote(Time.text(e.wake)) +
                ",\"q\":" + e.q + ",\"t\":" + Js.num(e.t) + "}"
            is Deleted -> "{\"id\":" + Js.quote(e.id) + ",\"del\":1,\"t\":" + Js.num(e.t) + "}"
        }

        internal fun json(p: Pending): String = "{\"bed\":" + (p.bed?.let { Js.quote(Time.text(it)) } ?: "null") + ",\"t\":" + Js.num(p.t) + "}"

        internal fun json(p: Prefs): String = "{\"weekend\":" + Js.quote(p.weekend) + ",\"wake\":" + (p.wake?.let { Js.quote(it) } ?: "null") +
            ",\"lat\":" + p.lat + ",\"born\":" + (p.born?.toString() ?: "null") + ",\"goals\":[" +
            p.goals.joinToString(",") { "{\"from\":" + Js.quote(it.from) + ",\"min\":" + it.min + ",\"on\":" + Js.quote(it.on) + "}" } +
            "],\"t\":" + Js.num(p.t) + "}"
    }
}
