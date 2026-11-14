package com.resz073.sleeptracker.engine

/**
 * JSON text for the copy kept on this phone, written exactly as JSON.stringify writes it. Reading gives
 * maps, lists, strings, booleans and numbers (Long for whole numbers, Double otherwise), as Firestore does.
 */
object Json {
    fun parse(s: String): Any? {
        val r = Reader(s)
        val v = r.value()
        r.ws()
        if (r.i != s.length) throw IllegalArgumentException("Unexpected text at ${r.i}")
        return v
    }

    fun stringify(v: Any?): String = StringBuilder().also { write(it, v) }.toString()

    private fun write(b: StringBuilder, v: Any?) {
        when (v) {
            null -> b.append("null")
            is String -> b.append(Js.quote(v))
            is Boolean -> b.append(v)
            is Int, is Long, is Short, is Byte -> b.append(v.toString())
            is Number -> v.toDouble().let { if (it.isNaN() || it.isInfinite()) b.append("null") else b.append(Js.num(it)) }
            is Map<*, *> -> {
                b.append('{')
                var first = true
                for ((k, x) in v) {
                    if (!first) b.append(',')
                    first = false
                    b.append(Js.quote(k.toString())).append(':')
                    write(b, x)
                }
                b.append('}')
            }
            is List<*> -> {
                b.append('[')
                v.forEachIndexed { i, x -> if (i > 0) b.append(','); write(b, x) }
                b.append(']')
            }
            else -> b.append(Js.quote(v.toString()))
        }
    }

    private class Reader(val s: String) {
        var i = 0

        fun ws() { while (i < s.length && (s[i] == ' ' || s[i] == '\t' || s[i] == '\r' || s[i] == '\n')) i++ }

        private fun fail(what: String): Nothing = throw IllegalArgumentException("Expected $what at $i")

        fun value(): Any? {
            ws()
            if (i >= s.length) fail("a value")
            return when (s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> num()
            }
        }

        private fun obj(): Map<String, Any?> {
            i++
            val m = LinkedHashMap<String, Any?>()
            ws()
            if (i < s.length && s[i] == '}') { i++; return m }
            while (true) {
                ws()
                if (i >= s.length || s[i] != '"') fail("a key")
                val k = str()
                ws(); expect(':')
                m[k] = value()
                ws()
                if (i < s.length && s[i] == ',') { i++; continue }
                expect('}')
                return m
            }
        }

        private fun arr(): List<Any?> {
            i++
            val l = ArrayList<Any?>()
            ws()
            if (i < s.length && s[i] == ']') { i++; return l }
            while (true) {
                l += value()
                ws()
                if (i < s.length && s[i] == ',') { i++; continue }
                expect(']')
                return l
            }
        }

        private fun str(): String {
            expect('"')
            val b = StringBuilder()
            while (true) {
                if (i >= s.length) fail("the end of a string")
                when (val c = s[i++]) {
                    '"' -> return b.toString()
                    '\\' -> {
                        if (i >= s.length) fail("an escape")
                        when (val e = s[i++]) {
                            'n' -> b.append('\n'); 't' -> b.append('\t'); 'r' -> b.append('\r'); 'b' -> b.append('\b'); 'f' -> b.append('\u000C')
                            'u' -> {
                                if (i + 4 > s.length) fail("4 hex digits")
                                b.append(s.substring(i, i + 4).toIntOrNull(16)?.toChar() ?: fail("4 hex digits"))
                                i += 4
                            }
                            else -> b.append(e)
                        }
                    }
                    else -> b.append(c)
                }
            }
        }

        private fun num(): Any {
            val st = i
            while (i < s.length && s[i] in "+-0123456789.eE") i++
            val t = s.substring(st, i)
            if (t.isEmpty()) fail("a value")
            return if (t.any { it == '.' || it == 'e' || it == 'E' }) t.toDoubleOrNull() ?: fail("a number")
            else t.toLongOrNull() ?: t.toDoubleOrNull() ?: fail("a number")
        }

        private fun lit(w: String, v: Any?): Any? {
            if (!s.startsWith(w, i)) fail(w)
            i += w.length
            return v
        }

        private fun expect(c: Char) {
            if (i >= s.length || s[i] != c) fail("'$c'")
            i++
        }
    }
}
