package com.resz073.sleeptracker.engine

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks the Kotlin sleep engine against the current app. The reference results in
 * src/test/resources/golden come from running src/app.js itself on the same accounts
 * (native/scripts/golden/make.mjs), in several time zones, so any difference is a porting mistake.
 */
class GoldenTest {
    @Test
    fun sameResultsAsTheCurrentApp() {
        val problems = ArrayList<String>()
        var cases = 0
        for (file in resource("golden/index.txt").lines().filter { it.isNotBlank() }) {
            val root = TestJson.parse(resource("golden/$file")) as Map<*, *>
            for (raw in root["cases"] as List<*>) {
                @Suppress("UNCHECKED_CAST") val c = raw as Map<String, Any?>
                cases++
                val label = "${c["name"]} (${c["zone"]}, ${c["now"]})"
                val actual = try {
                    Snapshot.of(c)
                } catch (e: Throwable) {
                    problems += "$label: crashed: $e at ${e.stackTrace.take(4).joinToString(" < ")}"
                    continue
                }
                val found = ArrayList<String>()
                compare("", c["expect"], actual, found)
                found.take(6).forEach { problems += "$label: $it" }
                if (found.size > 6) problems += "$label: and ${found.size - 6} more differences"
            }
        }
        assertTrue("$cases cases checked", cases >= 50)
        assertTrue("${problems.size} differences from the current app:\n" + problems.take(80).joinToString("\n"), problems.isEmpty())
    }

    private fun resource(name: String): String =
        (javaClass.classLoader!!.getResourceAsStream(name) ?: error("missing test resource $name")).use { it.readBytes().toString(Charsets.UTF_8) }

    private fun show(v: Any?): String = (if (v is String) "\"$v\"" else v.toString()).let { if (it.length > 160) it.take(160) + "…" else it }

    private fun compare(path: String, e: Any?, a: Any?, out: MutableList<String>) {
        when {
            e is Map<*, *> && a is Map<*, *> -> (e.keys + a.keys).forEach { k -> compare("$path.$k", e[k], a[k], out) }
            e is List<*> && a is List<*> -> {
                if (e.size != a.size) out += "$path: expected ${e.size} items, got ${a.size}: expected ${show(e)}, got ${show(a)}"
                else e.indices.forEach { compare("$path[$it]", e[it], a[it], out) }
            }
            e is Number && a is Number -> {
                val x = e.toDouble()
                val y = a.toDouble()
                if (x != y && Math.abs(x - y) > 1e-9 * Math.max(1.0, Math.abs(x))) out += "$path: expected $x, got $y"
            }
            e != a -> out += "$path: expected ${show(e)}, got ${show(a)}"
        }
    }
}
