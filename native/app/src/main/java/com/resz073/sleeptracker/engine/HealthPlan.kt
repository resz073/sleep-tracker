package com.resz073.sleeptracker.engine

/**
 * What to send to Health Connect: entries that are new or changed since they were last saved there
 * ([saved]: entry id to the change time saved), and the ids of saved entries that are gone (deleted, or
 * replaced by a duplicate). Each entry is saved with its id and change time, so saving it again updates it.
 * [everything]: save every entry again (when saving is turned on: Health Connect may have lost them, or
 * this phone's notes may be from a backup of another phone).
 */
class HealthPlan(val save: List<Entry>, val remove: List<String>, val saved: Map<String, Long>) {
    companion object {
        fun of(entries: List<Entry>, saved: Map<String, Long>, everything: Boolean = false): HealthPlan {
            val now = LinkedHashMap<String, Long>()
            for (e in entries) now[e.id] = e.t.toLong()
            return HealthPlan(entries.filter { everything || saved[it.id] != it.t.toLong() }, saved.keys.filter { it !in now }, now)
        }

        /** Saved state as stored on the phone: "id|time" */
        fun read(lines: Set<String>): Map<String, Long> =
            lines.mapNotNull { s -> s.lastIndexOf('|').takeIf { it > 0 }?.let { i -> s.substring(i + 1).toLongOrNull()?.let { s.substring(0, i) to it } } }.toMap()

        fun write(saved: Map<String, Long>): Set<String> = saved.map { (id, t) -> "$id|$t" }.toSet()
    }
}
