package com.resz073.sleeptracker.engine

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode

/**
 * The places where JavaScript and Kotlin give different answers for the same sum or text: rounding,
 * printing numbers, reading loosely typed values from the account, and JSON text. The engine uses these
 * so that it matches the current app (src/app.js) exactly, including how halves are rounded.
 */
internal object Js {
    /** Math.round: halves round up, so 2.5 → 3 and -2.5 → -2. (Kotlin's round() would give 2.) */
    fun round(x: Double): Long = Math.round(x)

    /** Number.prototype.toFixed: exact decimal value, halves away from zero, "-" kept for negatives. */
    fun toFixed(x: Double, digits: Int): String {
        val neg = x < 0
        val s = BigDecimal(if (neg) -x else x).setScale(digits, RoundingMode.HALF_UP).toPlainString()
        return if (neg) "-$s" else s
    }

    /** Number::toString: "7", "7.5", "0.001", "1e+21". */
    fun num(x: Double): String {
        if (x.isNaN()) return "NaN"
        if (x.isInfinite()) return if (x > 0) "Infinity" else "-Infinity"
        if (x == 0.0) return "0"
        if (x == Math.rint(x) && Math.abs(x) < 1e21) return BigDecimal(x).toBigInteger().toString()
        // The shortest digits that read back as the same number, placed as JavaScript places them
        val exact = BigDecimal(Math.abs(x))
        var d = exact
        for (p in 1..17) {
            val r = exact.round(MathContext(p, RoundingMode.HALF_EVEN))
            if (r.toDouble() == Math.abs(x)) { d = r; break }
        }
        d = d.stripTrailingZeros()
        val digits = d.unscaledValue().toString()
        val k = digits.length
        val n = k - d.scale() // x = 0.digits × 10^n
        val body = when {
            n in k..21 -> digits + "0".repeat(n - k)
            n in 1..21 -> digits.substring(0, n) + "." + digits.substring(n)
            n in -5..0 -> "0." + "0".repeat(-n) + digits
            else -> {
                val e = n - 1
                (if (k == 1) digits else digits[0] + "." + digits.substring(1)) + "e" + (if (e >= 0) "+" else "-") + Math.abs(e)
            }
        }
        return if (x < 0) "-$body" else body
    }

    /** String(value) for values read from the account (numbers, text, true/false, lists). */
    fun str(v: Any?): String = when (v) {
        null -> "null"
        is String -> v
        is Boolean -> v.toString()
        is Number -> num(v.toDouble())
        is List<*> -> v.joinToString(",") { if (it == null) "" else str(it) }
        is Map<*, *> -> "[object Object]"
        else -> v.toString()
    }

    /** Unary plus (ToNumber) on a value read from the account. A missing value reads as 0 here, like null. */
    fun toNumber(v: Any?): Double = when (v) {
        null -> 0.0
        is Boolean -> if (v) 1.0 else 0.0
        is Number -> v.toDouble()
        is String -> stringToNumber(v)
        is List<*> -> stringToNumber(str(v))
        else -> Double.NaN
    }

    private val DECIMAL = Regex("""[+-]?(\d+\.?\d*([eE][+-]?\d+)?|\.\d+([eE][+-]?\d+)?)""")
    private val HEX = Regex("0[xX][0-9a-fA-F]+")
    private val OCT = Regex("0[oO][0-7]+")
    private val BIN = Regex("0[bB][01]+")

    fun stringToNumber(text: String): Double {
        val s = trim(text)
        return when {
            s.isEmpty() -> 0.0
            DECIMAL.matches(s) -> s.toDouble()
            s == "Infinity" || s == "+Infinity" -> Double.POSITIVE_INFINITY
            s == "-Infinity" -> Double.NEGATIVE_INFINITY
            HEX.matches(s) -> BigInteger(s.substring(2), 16).toDouble()
            OCT.matches(s) -> BigInteger(s.substring(2), 8).toDouble()
            BIN.matches(s) -> BigInteger(s.substring(2), 2).toDouble()
            else -> Double.NaN
        }
    }

    /** Whether a value counts as true in an if. */
    fun truthy(v: Any?): Boolean = when (v) {
        null -> false
        is Boolean -> v
        is Number -> v.toDouble().let { it != 0.0 && !it.isNaN() }
        is String -> v.isNotEmpty()
        else -> true
    }

    /** String.prototype.trim, which removes a few more spaces than Kotlin's trim (and fewer controls). */
    fun trim(s: String): String {
        var a = 0
        var b = s.length
        while (a < b && space(s[a])) a++
        while (b > a && space(s[b - 1])) b--
        return s.substring(a, b)
    }

    private fun space(c: Char) = c == ' ' || c in '\t'..'\r' || c == ' ' || c == ' ' || c in ' '..' ' ||
        c == ' ' || c == ' ' || c == ' ' || c == ' ' || c == '　' || c == '﻿'

    /** JSON.stringify of a string. */
    fun quote(s: String): String {
        val b = StringBuilder(s.length + 2).append('"')
        for (i in s.indices) {
            val c = s[i]
            when {
                c == '"' -> b.append("\\\"")
                c == '\\' -> b.append("\\\\")
                c == '\b' -> b.append("\\b")
                c == '\u000C' -> b.append("\\f")
                c == '\n' -> b.append("\\n")
                c == '\r' -> b.append("\\r")
                c == '\t' -> b.append("\\t")
                c < ' ' -> b.append(hex(c))
                c.isHighSurrogate() && !(i + 1 < s.length && s[i + 1].isLowSurrogate()) -> b.append(hex(c))
                c.isLowSurrogate() && !(i > 0 && s[i - 1].isHighSurrogate()) -> b.append(hex(c))
                else -> b.append(c)
            }
        }
        return b.append('"').toString()
    }

    private fun hex(c: Char) = "\\u" + Integer.toHexString(c.code).padStart(4, '0')
}
