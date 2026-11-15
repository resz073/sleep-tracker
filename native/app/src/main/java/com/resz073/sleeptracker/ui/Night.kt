package com.resz073.sleeptracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.resz073.sleeptracker.R
import com.resz073.sleeptracker.engine.DayStatus
import com.resz073.sleeptracker.engine.SleepModel
import com.resz073.sleeptracker.engine.Words
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** A sine wave from [x0] to [x1] around [y]: [periods] waves of [amp] */
private fun wave(x0: Float, x1: Float, y: Float, amp: Float, periods: Int, step: Float): Path {
    val p = Path()
    val n = ((x1 - x0) / step).toInt().coerceAtLeast(16)
    for (i in 0..n) {
        val x = x0 + (x1 - x0) * i / n
        val yy = y - amp * sin((x - x0) / (x1 - x0) * periods * 2 * PI).toFloat()
        if (i == 0) p.moveTo(x, yy) else p.lineTo(x, yy)
    }
    return p
}

/**
 * The night ahead, on Today and Schedule: a dotted line from now, then one wave for each sleep cycle from
 * bedtime to wake-up. While asleep, [progress] (0 to 1) fills the waves so far.
 */
@Composable
fun NightWave(bed: String, wake: String?, cycles: Int, label: String?, modifier: Modifier = Modifier, progress: Float? = null) {
    val c = LocalAppColors.current
    val measurer = rememberTextMeasurer()
    val style = font(13f, FontWeight.Bold)
    Canvas(modifier.fillMaxWidth().height(72.dp).semantics { if (label != null) contentDescription = label }) {
        val w = size.width
        val y = 30.dp.toPx()
        val x0 = 60.dp.toPx()
        val x1 = w - 8.dp.toPx()
        drawLine(
            c.mute, Offset(8.dp.toPx(), y), Offset(52.dp.toPx(), y), 2.dp.toPx(), StrokeCap.Round,
            PathEffect.dashPathEffect(floatArrayOf(0.1f, 6.dp.toPx())),
        )
        val path = wave(x0, x1, y, 9.dp.toPx(), cycles.coerceAtLeast(1), 1.5.dp.toPx())
        val stroke = Stroke(5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        if (progress == null) drawPath(path, c.acc, style = stroke)
        else {
            drawPath(path, c.acc.copy(alpha = 0.28f), style = stroke)
            clipRect(right = x0 + (x1 - x0) * progress.coerceIn(0f, 1f)) { drawPath(path, c.acc, style = stroke) }
        }
        drawCircle(c.ink, 5.dp.toPx(), Offset(8.dp.toPx(), y))
        val base = 66.dp.toPx()
        val b = measurer.measure(bed, style.copy(color = c.ink))
        drawText(b, topLeft = Offset((x0 - b.size.width / 2f).coerceAtLeast(0f), base - b.firstBaseline))
        if (wake != null) {
            val e = measurer.measure(wake, style.copy(color = c.acc))
            drawText(e, topLeft = Offset(w - 4.dp.toPx() - e.size.width, base - e.firstBaseline))
        }
    }
}

/** The app's mark on the sign-in screen: three waves */
@Composable
fun WaveMark(modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    Canvas(modifier.size(72.dp, 24.dp)) {
        drawPath(
            wave(3.dp.toPx(), 69.dp.toPx(), 12.dp.toPx(), 6.dp.toPx(), 3, 1.dp.toPx()), c.acc,
            style = Stroke(4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/**
 * Trends: each day of [days] as a candle against the goal. Days that met the goal are lit (with a flame),
 * days under it are not, a day still going is an outline, and a night not logged is a dash. Tapping a day
 * calls [onPick] with its index.
 */
@Composable
fun CandleChart(m: SleepModel, days: List<LocalDate>, pick: Int, week: Boolean, label: String?, onPick: (Int) -> Unit) {
    val c = LocalAppColors.current
    val measurer = rememberTextMeasurer()
    val count = days.size
    if (count == 0) return
    val today = m.today
    val status = days.map { m.status(it) }
    val slept = days.map { m.sleptOn(it) }
    val goals = days.map { m.goalOn(it) }
    val missing = days.map { it in m.missing }
    val top = maxOf(600, slept.max() + 30, goals.max() + 60)
    val axis = if (week) 36.dp else 18.dp
    val small = font(12f)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(170.dp + axis)
            .semantics { if (label != null) contentDescription = label }
            .pointerInput(count) { detectTapGestures { o -> onPick((o.x / size.width * count).toInt().coerceIn(0, count - 1)) } },
    ) {
        val w = size.width
        val plot = 170.dp.toPx()
        val slot = w / count
        val r = (slot * 0.6f).coerceIn(6.dp.toPx(), 16.dp.toPx()) / 2
        fun y(v: Int) = plot - plot * v / top
        if (pick in 0 until count) drawRoundRect(c.ink.copy(alpha = 0.07f), Offset(slot * pick, 0f), Size(slot, plot), CornerRadius(4.dp.toPx()))
        drawLine(c.line, Offset(0f, plot), Offset(w, plot), 1.dp.toPx())
        val goal = Path().apply {
            moveTo(0f, y(goals[0]))
            for (i in 1 until count) {
                lineTo(slot * i, y(goals[i - 1]))
                lineTo(slot * i, y(goals[i]))
            }
            lineTo(w, y(goals[count - 1]))
        }
        drawPath(goal, c.goal, style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))))
        for (i in 0 until count) {
            val cx = slot * i + slot / 2
            val v = slept[i]
            val s = status[i]
            if (s == DayStatus.DONE || (s == DayStatus.OPEN && v > 0)) {
                val t = y(v).coerceAtMost(plot - 2.dp.toPx())
                val bar = Path().apply { addRoundRect(RoundRect(Rect(cx - r, t, cx + r, plot), topLeft = CornerRadius(r), topRight = CornerRadius(r))) }
                if (s == DayStatus.DONE) {
                    val met = v >= goals[i]
                    drawPath(bar, if (met) c.met else c.under)
                    if (met) flame(Offset(cx, t - 8.5.dp.toPx()), c)
                } else drawPath(bar, c.met, style = Stroke(1.5.dp.toPx()))
            }
            if (missing[i]) {
                val mx = if (i == 0) slot / 2 else slot * i
                val half = if (week) 10.dp.toPx() else slot / 2
                drawLine(c.mute, Offset(mx - half, plot - 4.5.dp.toPx()), Offset(mx + half, plot - 4.5.dp.toPx()), 3.dp.toPx(), StrokeCap.Round)
            }
        }
        val ti = days.indexOf(today)
        for (i in 0 until count) {
            val d = days[i]
            val isToday = d == today
            val cx = slot * i + slot / 2
            val style = small.copy(color = if (isToday) c.ink else c.mute, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal)
            if (week) {
                val faded = if (status[i] == DayStatus.FUTURE && !isToday) 0.6f else 1f
                val a = measurer.measure(Words.weekday(d), style)
                val b = measurer.measure(d.dayOfMonth.toString(), style)
                drawText(a, topLeft = Offset(cx - a.size.width / 2f, plot + 4.dp.toPx()), alpha = faded)
                drawText(b, topLeft = Offset(cx - b.size.width / 2f, plot + 4.dp.toPx() + a.size.height), alpha = faded)
            } else {
                val dn = i + 1
                if (isToday || ((dn == 1 || dn % 5 == 0) && !(ti >= 0 && abs(i - ti) <= 1))) {
                    val t = measurer.measure(dn.toString(), style)
                    drawText(t, topLeft = Offset((cx - t.size.width / 2f).coerceIn(0f, w - t.size.width), plot + 3.dp.toPx()))
                }
            }
        }
    }
}

/** A lit candle's flame: a dot, glowing in dark mode */
private fun DrawScope.flame(at: Offset, c: AppColors) {
    val r = 3.5.dp.toPx()
    if (c.dark) {
        drawCircle(c.acc.copy(alpha = 0.28f), r * 2.2f, at)
        drawCircle(c.acc, r, at)
    } else drawCircle(c.met, r, at)
}

/** "11:15 PM" as minutes after midnight, or null */
fun clockMinutes(s: String): Int? {
    val g = Regex("""(\d{1,2}):(\d{2})\s*(AM|PM)""").find(s)?.groupValues ?: return null
    return (g[1].toInt() % 12) * 60 + g[2].toInt() + (if (g[3] == "PM") 720 else 0)
}

/** The times in "11:15 PM – 6:55 AM", or null */
fun clockRange(s: String): Pair<Int, Int>? {
    val parts = s.split("–")
    if (parts.size != 2) return null
    val a = clockMinutes(parts[0]) ?: return null
    val b = clockMinutes(parts[1]) ?: return null
    return a to b
}

private const val AXIS_START = 22 * 60
private const val AXIS_LENGTH = 600

/** Where [minute] falls on the 10 PM to 8 AM axis, 0 to 1 */
private fun onAxis(minute: Int): Float = (((minute - AXIS_START) % 1440 + 1440) % 1440).coerceAtMost(AXIS_LENGTH) / AXIS_LENGTH.toFloat()

/** The labels over the weekend bars: 10 PM to 8 AM */
@Composable
fun NightAxis() {
    val c = LocalAppColors.current
    val measurer = rememberTextMeasurer()
    val style = font(12f).copy(color = c.mute)
    Canvas(Modifier.fillMaxWidth().height(16.dp)) {
        val labels = listOf("10 PM", "12 AM", "2 AM", "4 AM", "6 AM", "8 AM")
        labels.forEachIndexed { i, l ->
            val t = measurer.measure(l, style)
            val x = size.width * i / (labels.size - 1)
            val left = when (i) { 0 -> 0f; labels.size - 1 -> size.width - t.size.width; else -> x - t.size.width / 2f }
            drawText(t, topLeft = Offset(left, 0f))
        }
    }
}

/** A night from [bed] to [wake] (minutes after midnight) on the 10 PM to 8 AM axis */
@Composable
fun RangeBar(bed: Int, wake: Int) {
    val c = LocalAppColors.current
    Canvas(Modifier.fillMaxWidth().height(10.dp)) {
        val h = size.height
        drawRoundRect(c.raised, Offset.Zero, size, CornerRadius(h / 2))
        val a = onAxis(bed) * size.width
        val b = onAxis(wake) * size.width
        if (b > a) drawRoundRect(c.met, Offset(a, 0f), Size(b - a, h), CornerRadius(h / 2))
    }
}

/** An average rating (out of 5) as a bar */
@Composable
fun RatingBar(rating: Double) {
    val c = LocalAppColors.current
    Box(Modifier.fillMaxWidth().height(8.dp).background(c.raised, RoundedCornerShape(4.dp))) {
        Box(Modifier.fillMaxWidth((rating / 5).toFloat().coerceIn(0f, 1f)).fillMaxHeight().background(c.acc, RoundedCornerShape(4.dp)))
    }
}

/** The ‹ and › buttons for the period or week */
@Composable
fun NavArrow(forward: Boolean, label: String, enabled: Boolean, onClick: () -> Unit, size: Dp = 48.dp) {
    val c = LocalAppColors.current
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(size).alpha(if (enabled) 1f else 0.3f).semantics { contentDescription = label }) {
        Icon(painterResource(if (forward) R.drawable.ic_chevron_right else R.drawable.ic_chevron_left), contentDescription = null, tint = c.ink, modifier = Modifier.size(22.dp))
    }
}
