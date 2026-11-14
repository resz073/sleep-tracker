package com.resz073.sleeptracker.engine

import com.resz073.sleeptracker.show.WidgetShow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** The native extras: what the widget and tile show, and what's sent to Health Connect */
class ExtrasTest {
    private val zone = ZoneId.of("Asia/Dhaka")
    private fun at(s: String) = LocalDateTime.parse(s)
    private fun entry(id: String, bed: String, wake: String, t: Double) = Entry(id, at(bed), at(wake), 0, t)

    private fun case(name: String): Map<String, Any?> = GoldenTest.cases.first { it["name"] == name }
    private fun model(c: Map<String, Any?>): SleepModel {
        val now = Now((c["nowMs"] as Number).toLong(), ZoneId.of(c["zone"] as String))
        return SleepModel(SleepDoc.read(c["doc"], now), now)
    }

    @Test
    fun healthConnectGetsNewAndChangedEntriesAndLosesDeletedOnes() {
        val a = entry("a", "2026-11-12T23:00", "2026-11-13T07:00", 100.0)
        val b = entry("b", "2026-11-13T23:00", "2026-11-14T07:00", 200.0)
        val first = HealthPlan.of(listOf(a, b), emptyMap())
        assertEquals(listOf("a", "b"), first.save.map { it.id })
        assertTrue(first.remove.isEmpty())
        // b edited, a deleted, c added
        val b2 = b.copy(wake = at("2026-11-14T07:30"), t = 300.0)
        val c = entry("c", "2026-11-14T23:00", "2026-11-15T07:00", 400.0)
        val next = HealthPlan.of(listOf(b2, c), first.saved)
        assertEquals(listOf("b", "c"), next.save.map { it.id })
        assertEquals(listOf("a"), next.remove)
        assertEquals(mapOf("b" to 300L, "c" to 400L), next.saved)
        // nothing changed: nothing to send
        val same = HealthPlan.of(listOf(b2, c), next.saved)
        assertTrue(same.save.isEmpty() && same.remove.isEmpty())
        // saving turned on again: every entry is sent again
        val again = HealthPlan.of(listOf(b2, c), next.saved, everything = true)
        assertEquals(listOf(b2, c), again.save)
        assertEquals(emptyList<String>(), again.remove)
        // stored and read back, ids with "|" in them included
        val odd = mapOf("x|y" to 5L, "z" to 1767225600123L)
        assertEquals(odd, HealthPlan.read(HealthPlan.write(odd)))
        assertEquals(emptyMap<String, Long>(), HealthPlan.read(setOf("broken", "|7", "q|notanumber")))
    }

    @Test
    fun widgetShowsTonightTheSleepInProgressOrHowToStart() {
        val plan = model(case("dhaka 120 days, evening")).let { WidgetShow.of(it, Planner.plan(it)) }
        assertEquals("Bedtime tonight", plan.label)
        assertEquals("11:25 PM", plan.big)
        assertEquals("Wake up at 6:55 AM.", plan.line)
        assertEquals("Last 14 days: 4h 13m sleep debt", plan.debt)
        assertEquals("sleep", plan.action)

        val asleep = model(case("asleep since 11:10 PM")).let { WidgetShow.of(it, Planner.plan(it)) }
        assertEquals("Sleeping since", asleep.label)
        assertEquals("11:10 PM", asleep.big)
        assertEquals("awake", asleep.action)
        assertEquals("I’m awake", asleep.button)

        val empty = model(case("empty, no document")).let { WidgetShow.of(it, Planner.plan(it)) }
        assertEquals("Not set yet", empty.big)
        assertTrue(empty.bigSmall)
        assertNull(empty.debt)
        assertEquals("sleep", empty.action)

        val out = WidgetShow.of(null, null)
        assertNull(out.action)
        assertEquals("Going to sleep" to "Sign in first", WidgetShow.tile(null, null))
        assertEquals("I’m awake" to "Since 11:10 PM", model(case("asleep since 11:10 PM")).let { WidgetShow.tile(it, Planner.plan(it)) })
    }
}
