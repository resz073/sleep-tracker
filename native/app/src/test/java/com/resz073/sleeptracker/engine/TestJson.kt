package com.resz073.sleeptracker.engine

/** A small JSON reader for the reference files: whole numbers become Long and others Double, as Firestore gives them. */
internal object TestJson {
    fun parse(s: String): Any? {
        val p = Reader(s)
        val v = p.value()
        p.ws()
        require(p.i == s.length) { "extra text at ${p.i}" }
        return v
    }

    private class Reader(val s: String) {
        var i = 0

        fun ws() { while (i < s.length && (s[i] == ' ' || s[i] == '\t' || s[i] == '\r' || s[i] == '\n')) i++ }

        fun value(): Any? {
            ws()
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

        fun obj(): Map<String, Any?> {
            i++
            val m = LinkedHashMap<String, Any?>()
            ws()
            if (s[i] == '}') { i++; return m }
            while (true) {
                ws()
                val k = str()
                ws(); expect(':')
                m[k] = value()
                ws()
                if (s[i] == ',') { i++; continue }
                expect('}')
                return m
            }
        }

        fun arr(): List<Any?> {
            i++
            val l = ArrayList<Any?>()
            ws()
            if (s[i] == ']') { i++; return l }
            while (true) {
                l += value()
                ws()
                if (s[i] == ',') { i++; continue }
                expect(']')
                return l
            }
        }

        fun str(): String {
            expect('"')
            val b = StringBuilder()
            while (true) {
                val c = s[i++]
                when (c) {
                    '"' -> return b.toString()
                    '\\' -> when (val e = s[i++]) {
                        'n' -> b.append('\n'); 't' -> b.append('\t'); 'r' -> b.append('\r'); 'b' -> b.append('\b'); 'f' -> b.append('\u000C')
                        'u' -> { b.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                        else -> b.append(e)
                    }
                    else -> b.append(c)
                }
            }
        }

        fun num(): Any {
            val st = i
            while (i < s.length && s[i] in "+-0123456789.eE") i++
            val t = s.substring(st, i)
            return if (t.any { it == '.' || it == 'e' || it == 'E' }) t.toDouble() else t.toLong()
        }

        fun lit(w: String, v: Any?): Any? {
            require(s.startsWith(w, i)) { "expected $w at $i" }
            i += w.length
            return v
        }

        fun expect(c: Char) {
            require(s[i] == c) { "expected $c at $i" }
            i++
        }
    }
}
