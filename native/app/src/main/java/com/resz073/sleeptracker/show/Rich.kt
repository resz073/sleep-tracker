package com.resz073.sleeptracker.show

/** The current app's colours for meaning: good (on or over the goal), bad (under), warn (a night not logged) */
enum class Tone { NONE, GOOD, BAD, WARN }

/** A run of text, maybe bold, maybe coloured */
data class Span(val text: String, val bold: Boolean = false, val tone: Tone = Tone.NONE)

/** Text with emphasis, as the current app writes it with <b> and coloured <span>s */
class Rich(val spans: List<Span>) {
    val text: String get() = spans.joinToString("") { it.text }

    /** The text with its emphasis written in, the way the reference results record a screen: [b]…[/b], [b.bad]…[/b], [warn]…[/warn] */
    fun marked(): String = spans.joinToString("") { markSpan(it) }

    operator fun plus(o: Rich) = Rich(spans + o.spans)

    companion object {
        val EMPTY = Rich(emptyList())
        fun of(s: String) = if (s.isEmpty()) EMPTY else Rich(listOf(Span(s)))
    }
}

internal fun toneName(t: Tone): String = when (t) { Tone.GOOD -> "good"; Tone.BAD -> "bad"; Tone.WARN -> "warn"; Tone.NONE -> "" }

internal fun markSpan(s: Span): String = when {
    s.bold && s.tone != Tone.NONE -> "[b." + toneName(s.tone) + "]" + s.text + "[/b]"
    s.bold -> "[b]" + s.text + "[/b]"
    s.tone != Tone.NONE -> "[" + toneName(s.tone) + "]" + s.text + "[/" + toneName(s.tone) + "]"
    else -> s.text
}

class RichBuilder {
    private val spans = ArrayList<Span>()
    /** Plain text */
    fun t(s: String) { if (s.isNotEmpty()) spans += Span(s) }
    /** Bold, maybe coloured */
    fun b(s: String, tone: Tone = Tone.NONE) { spans += Span(s, true, tone) }
    /** Coloured */
    fun c(s: String, tone: Tone) { spans += Span(s, false, tone) }
    fun add(r: Rich) { spans += r.spans }
    fun build() = Rich(spans.toList())
}

fun rich(block: RichBuilder.() -> Unit): Rich = RichBuilder().apply(block).build()

/** A figure and what it is ("7h 30m", "average per day"); the figure may be coloured */
data class Stat(val big: String, val label: String, val tone: Tone = Tone.NONE) {
    fun marked() = markSpan(Span(big, true, tone)) + label
}

/** The colour of a balance: positive is a deficit (the current app's cls) */
fun deficitTone(m: Int): Tone = if (m > 0) Tone.BAD else Tone.GOOD
fun deficitTone(m: Double): Tone = if (m > 0) Tone.BAD else Tone.GOOD

/** A key in a chart's legend */
data class LegendItem(val mark: Mark, val text: String) {
    enum class Mark(val key: String) { LINE("line"), GOAL("goal"), DEFICIT("def"), SURPLUS("sur"), LIVE("live"), MISSING("miss"), SLEEP("sleep"), NAP("nap"), PLAN("plan") }
}
