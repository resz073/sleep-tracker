package com.resz073.sleeptracker.engine

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * Checks the Kotlin sleep engine against version 1 of the app. The reference results in
 * src/test/resources/golden were saved by running version 1's own code (the web app's src/app.js, in
 * the repository's history) on the same accounts, in several time zones, so any difference is a change
 * from how version 1 worked.
 */
class GoldenTest {
    companion object {
        /** Every reference case, read once */
        val cases: List<Map<String, Any?>> by lazy {
            resource("golden/index.txt").lines().filter { it.isNotBlank() }.flatMap { file ->
                @Suppress("UNCHECKED_CAST")
                ((Json.parse(resource("golden/$file")) as Map<*, *>)["cases"] as List<Map<String, Any?>>)
            }
        }

        private fun resource(name: String): String =
            (GoldenTest::class.java.classLoader!!.getResourceAsStream(name) ?: error("missing test resource $name")).use { it.readBytes().toString(Charsets.UTF_8) }

        fun label(c: Map<String, Any?>) = "${c["name"]} (${c["zone"]}, ${c["now"]})"
    }

    @Test
    fun sameResultsAsTheCurrentApp() {
        val problems = ArrayList<String>()
        for (c in cases) {
            val actual = try {
                Snapshot.of(c)
            } catch (e: Throwable) {
                problems += "${label(c)}: crashed: $e at ${e.stackTrace.take(4).joinToString(" < ")}"
                continue
            }
            val found = ArrayList<String>()
            compare("", c["expect"], actual, found)
            found.take(6).forEach { problems += "${label(c)}: $it" }
            if (found.size > 6) problems += "${label(c)}: and ${found.size - 6} more differences"
        }
        assertTrue("${cases.size} cases checked", cases.size >= 70)
        assertTrue("${problems.size} differences from version 1:\n" + problems.take(80).joinToString("\n"), problems.isEmpty())
    }

    /** What the native app writes to the account must be exactly what version 1 writes, and read back unchanged */
    @Test
    fun writesWhatTheCurrentAppWrites() {
        val problems = ArrayList<String>()
        for (c in cases) {
            val now = Now((c["nowMs"] as Number).toLong(), ZoneId.of(c["zone"] as String))
            val doc = SleepDoc.read(c["doc"], now)
            val written = doc.toFirestore()
            if (Json.stringify(written) != doc.canon()) problems += "${label(c)}: written ${Json.stringify(written).take(200)} but version 1 writes ${doc.canon().take(200)}"
            if (SleepDoc.read(written, now).canon() != doc.canon()) problems += "${label(c)}: what's written doesn't read back the same"
            if (SleepDoc.fromText(doc.canon(), now).canon() != doc.canon()) problems += "${label(c)}: the copy on the phone doesn't read back the same"
            fun wholeNumbers(v: Any?, path: String) {
                when (v) {
                    is Map<*, *> -> v.forEach { (k, x) -> wholeNumbers(x, "$path.$k") }
                    is List<*> -> v.forEachIndexed { i, x -> wholeNumbers(x, "$path[$i]") }
                    is Double -> if (v == Math.rint(v)) problems += "${label(c)}: $path is written as a decimal ($v), version 1 writes an integer"
                    is Int -> problems += "${label(c)}: $path is an Int, expected Long"
                }
            }
            wholeNumbers(written, "")
        }
        assertTrue(problems.take(40).joinToString("\n"), problems.isEmpty())
    }

    private fun show(v: Any?): String = (if (v is String) "\"$v\"" else v.toString()).let { if (it.length > 160) it.take(160) + "…" else it }

    /** Chart attributes (and clip paths) are compared number by number, allowing for rounding in the last digits */
    private val LOOSE = Regex("""\.(shapes\[\d+]\[1]\.[^.\[]+|clips\.[^.\[]+)$""")
    private val NUM = Regex("""-?\d+(?:\.\d+)?(?:e[-+]?\d+)?""")

    private fun sameNumbers(e: String, a: String): Boolean {
        if (NUM.replace(e, "#") != NUM.replace(a, "#")) return false
        val x = NUM.findAll(e).map { it.value.toDouble() }.toList()
        val y = NUM.findAll(a).map { it.value.toDouble() }.toList()
        return x.size == y.size && x.indices.all { Math.abs(x[it] - y[it]) <= 1e-6 * Math.max(1.0, Math.abs(x[it])) }
    }

    private fun compare(path: String, e: Any?, a: Any?, out: MutableList<String>) {
        when {
            e is String && a is String && LOOSE.containsMatchIn(path) -> if (!sameNumbers(e, a)) out += "$path: expected ${show(e)}, got ${show(a)}"
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
