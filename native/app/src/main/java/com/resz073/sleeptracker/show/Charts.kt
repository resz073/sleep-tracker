package com.resz073.sleeptracker.show

import com.resz073.sleeptracker.engine.DayStatus
import com.resz073.sleeptracker.engine.History
import com.resz073.sleeptracker.engine.Js
import com.resz073.sleeptracker.engine.PeriodKind
import com.resz073.sleeptracker.engine.Plan
import com.resz073.sleeptracker.engine.SleepModel
import com.resz073.sleeptracker.engine.Time
import com.resz073.sleeptracker.engine.Words
import com.resz073.sleeptracker.engine.adj
import java.time.LocalDate

/**
 * Version 1's charts, shape by shape (version 1's app.js strip, the ring in renderToday, mini, draw, mono and
 * diary). Numbers are worked out in the same order as there, so the shapes come out the same.
 */
object Charts {
    private fun n(x: Double) = Js.num(x)

    /** The strip on Today and Schedule: 6 PM to 2 PM, the planned night in cycles, sleep logged, now. [target]: the plan's target instead of tonight's step */
    fun strip(m: SleepModel, plan: Plan?, target: Boolean): Drawing {
        val zone = m.zone
        val now = m.now.ms
        val pend = m.pending
        val f = if (pend != null) Time.ms(pend, zone) else now
        val fd = Time.at(f, zone)
        val d0 = fd.toLocalDate()
        val ws = Time.ms((if (fd.hour < 14) d0.minusDays(1) else d0).atTime(18, 0), zone)
        val we = ws + 1200 * 60000L
        fun x(v: Double) = 4 + (316 - 4) * Math.max(0.0, Math.min(1200.0, v)) / 1200
        fun off(v: Long) = (v - ws) / 60000.0
        val y = 22
        val h = 16
        val b = DrawingBuilder()
        b.rect("x" to 4, "y" to y, "width" to 312, "height" to h, "rx" to 8, "fill" to "var(--sky-line)")
        var lab = "Tonight"
        if (plan != null) {
            val bt = if (target) plan.target else plan.tonight
            val cy = if (target) plan.targetCycles else plan.cycles
            val a = bt - 1080
            val e = plan.wake + 360
            val la = Math.min(e, a + plan.latency)
            val len = (e - la).toDouble() / cy
            b.rect("x" to x(a.toDouble()), "y" to y, "width" to Math.max(0.0, x(la.toDouble()) - x(a.toDouble()) - 1), "height" to h, "rx" to 4, "fill" to "var(--sky-acc)", "fill-opacity" to ".18")
            for (k in 0 until cy) {
                val ca = x(la + k * len) + 1
                val cb = x(la + (k + 1) * len) - 1
                b.rect("x" to ca, "y" to y, "width" to Math.max(1.0, cb - ca), "height" to h, "rx" to 4, "fill" to "var(--sky-acc)", "fill-opacity" to ".36")
            }
            val bl = Math.max(26.0, Math.min(294.0, x(a.toDouble())))
            val wl = Math.max(26.0, Math.min(294.0, x(e.toDouble())))
            b.text(Words.clock(bt), "x" to bl, "y" to 14, "font-size" to 11, "font-weight" to 800, "text-anchor" to "middle", "fill" to "var(--sky-ink)")
            if (Math.abs(wl - bl) > 56) b.text(Words.clock(plan.wake), "x" to wl, "y" to 14, "font-size" to 11, "font-weight" to 800, "text-anchor" to "middle", "fill" to "var(--sky-dawn)")
            lab = "Planned sleep " + Words.clock(bt) + " to " + Words.clock(plan.wake) + ", about " + cy + " sleep cycles"
        }
        for (e in m.entries) {
            val a = Math.max(Time.ms(e.bed, zone), ws)
            val c = Math.min(Time.ms(e.wake, zone), we)
            if (c > a) b.rect("x" to x(off(a)), "y" to y, "width" to Math.max(2.0, x(off(c)) - x(off(a))), "height" to h, "rx" to 4, "fill" to "var(--sky-acc)")
        }
        if (pend != null) {
            val a = Math.max(Time.ms(pend, zone), ws)
            val c = Math.min(now, we)
            if (c > a) b.rect("x" to x(off(a)), "y" to y, "width" to Math.max(2.0, x(off(c)) - x(off(a))), "height" to h, "rx" to 4, "fill" to "var(--sky-acc)")
        }
        if (now > ws && now < we) {
            val nx = x(off(now))
            b.line("x1" to nx, "x2" to nx, "y1" to y - 5, "y2" to y + h + 5, "stroke" to "var(--sky-dawn)", "stroke-width" to 2.5, "stroke-linecap" to "round")
        }
        listOf(Triple(0, "6 PM", "start"), Triple(360, "12 AM", "middle"), Triple(720, "6 AM", "middle"), Triple(1080, "12 PM", "middle")).forEach { (mm, l, an) ->
            b.text(l, "x" to x(mm.toDouble()), "y" to 56, "font-size" to 10, "text-anchor" to an, "fill" to "var(--sky-mute)")
        }
        return b.build("0 0 320 62", lab)
    }

    /** Last night against the goal, as a ring */
    fun ring(minutes: Int, goal: Int): Drawing {
        val c = 2 * Math.PI * 40
        val pc = Math.min(1.0, minutes.toDouble() / goal)
        val b = DrawingBuilder()
        b.circle("cx" to 48, "cy" to 48, "r" to 40, "fill" to "none", "stroke" to "var(--card2)", "stroke-width" to 10)
        b.circle("cx" to 48, "cy" to 48, "r" to 40, "fill" to "none", "stroke" to (if (minutes >= goal) "var(--good)" else "var(--acc)"), "stroke-width" to 10,
            "stroke-linecap" to "round", "stroke-dasharray" to Js.toFixed(c * pc, 1) + " " + Js.toFixed(c, 1), "transform" to "rotate(-90 48 48)")
        b.text(Words.hm(minutes), "x" to 48, "y" to 50, "text-anchor" to "middle", "font-size" to 16, "font-weight" to 800, "fill" to "var(--ink)")
        b.text("of " + Words.short(goal), "x" to 48, "y" to 66, "text-anchor" to "middle", "font-size" to 11, "fill" to "var(--mute)")
        return b.build("0 0 96 96", Words.hm(minutes) + " of your " + Words.goal(goal) + " goal")
    }

    /** The bars on Today: each of [days] against the goal */
    fun mini(m: SleepModel, days: List<LocalDate>): Drawing {
        val w = 320
        val h = 64
        val pb = 16
        val pt = 4
        val count = days.size
        val slot = w.toDouble() / count
        val bw = Math.min(14.0, slot - 6)
        val goals = days.map { m.goalOn(it) }
        val max = maxOf(600, goals.max(), days.maxOf { m.sleptOn(it) })
        fun y(v: Int) = h - pb - (Math.min(v, max).toDouble() / max) * (h - pb - pt)
        val b = DrawingBuilder()
        b.path("d" to goals.mapIndexed { i, g -> (if (i > 0) "H" + Js.toFixed(slot * i, 1) + "V" else "M0,") + Js.toFixed(y(g), 1) }.joinToString("") + "H" + w,
            "fill" to "none", "stroke" to "var(--good)", "stroke-width" to 1.2, "stroke-dasharray" to "4 3")
        days.forEachIndexed { i, d ->
            val cx = slot * i + slot / 2
            val st = m.status(d)
            val v = m.sleptOn(d)
            if (st == DayStatus.DONE) {
                b.rect("x" to cx - bw / 2, "y" to y(v), "width" to bw, "height" to Math.max(2.0, h - pb - y(v)), "rx" to 3, "fill" to (if (v >= goals[i]) "var(--good)" else "var(--acc)"),
                    title = Words.day(d) + ": " + Words.hm(v))
            } else if (st == DayStatus.OPEN && v != 0) {
                b.rect("x" to cx - bw / 2 + 1, "y" to y(v) + 1, "width" to bw - 2, "height" to Math.max(2.0, h - pb - y(v) - 1), "rx" to 3, "fill" to "none", "stroke" to "var(--acc)",
                    "stroke-width" to 1.5, "stroke-dasharray" to "3 2", title = (if (d == m.today) "Today" else Words.day(d)) + " so far: " + Words.hm(v))
            } else if (st == DayStatus.MISSING) {
                b.rect("x" to cx - 4, "y" to h - pb - 3, "width" to 8, "height" to 3, "rx" to 1.5, "fill" to "var(--warn)")
            }
            if (i == 0 || i == count - 1) {
                b.text(if (d == m.today) "Today" else Words.dayShort(d), "x" to (if (i == 0) 0 else w), "y" to h - 2, "font-size" to 10,
                    "text-anchor" to (if (i == 0) "start" else "end"), "fill" to "var(--mute)")
            }
        }
        return b.build("0 0 320 64", null)
    }

    /** A smooth line through points ([x, y]) that never overshoots them (monotone cubic) */
    fun mono(p: List<DoubleArray>): String {
        val size = p.size
        val dx = DoubleArray(size - 1)
        val ms = DoubleArray(size - 1)
        val t = DoubleArray(size)
        for (i in 0 until size - 1) { dx[i] = p[i + 1][0] - p[i][0]; ms[i] = (p[i + 1][1] - p[i][1]) / dx[i] }
        t[0] = ms[0]
        t[size - 1] = ms[size - 2]
        for (i in 1 until size - 1) t[i] = if (ms[i - 1] * ms[i] <= 0) 0.0 else (ms[i - 1] + ms[i]) / 2
        for (i in 0 until size - 1) {
            if (ms[i] == 0.0) { t[i] = 0.0; t[i + 1] = 0.0; continue }
            val a = t[i] / ms[i]
            val b = t[i + 1] / ms[i]
            val h = a * a + b * b
            if (h > 9) { val k = 3 / Math.sqrt(h); t[i] = k * a * ms[i]; t[i + 1] = k * b * ms[i] }
        }
        val d = StringBuilder("M" + n(p[0][0]) + "," + n(p[0][1]))
        for (i in 0 until size - 1) {
            val h = dx[i] / 3
            d.append(" C" + n(p[i][0] + h) + "," + n(p[i][1] + t[i] * h) + " " + n(p[i + 1][0] - h) + "," + n(p[i + 1][1] - t[i + 1] * h) + " " + n(p[i + 1][0]) + "," + n(p[i + 1][1]))
        }
        return d.toString()
    }

    private class Point(val d: LocalDate, val s: DayStatus, val v: Int)

    /** The Trends chart: sleep each day of [days] against the goal, deficit and surplus shaded, [pick] marked */
    fun trends(m: SleepModel, kind: PeriodKind, days: List<LocalDate>, pick: Int): Drawing {
        val t = m.today
        val count = days.size
        val wk = kind != PeriodKind.MONTH
        val w = 320
        val h = if (wk) 200 else 184
        val pl = 30
        val pr = 10
        val pt = 18
        val pb = if (wk) 32 else 22
        val pts = days.map { Point(it, m.status(it), m.sleptOn(it)) }
        val vals = pts.filter { it.s == DayStatus.DONE || (it.s == DayStatus.OPEN && it.v != 0) }.map { it.v }
        val goals = days.map { m.goalOn(it) }
        val flat = goals.all { it == goals[0] }
        val lo = if (vals.isNotEmpty()) Math.min(240, Math.floorDiv(vals.min(), 120) * 120) else 240
        val hi = Math.max(600, Math.ceil((maxOf(0, vals.maxOrNull() ?: 0, goals.max()) + 30) / 120.0).toInt() * 120)
        fun x(i: Int) = pl + 8 + (w - pl - pr - 16).toDouble() * i / (count - 1)
        fun y(v: Double) = h - pb - (v - lo) / (hi - lo) * (h - pb - pt)
        fun yi(v: Int) = y(v.toDouble())
        fun gy(i: Int) = yi(goals[i])
        val base = h - pb
        fun mid(i: Int) = (x(i) + x(i + 1)) / 2
        fun step(x0: Double, x1: Double): String {
            val d = StringBuilder("M" + n(x0) + "," + n(gy(0)))
            for (i in 1 until count) d.append(" H" + n(mid(i - 1)) + " V" + n(gy(i)))
            return d.append(" H" + n(x1)).toString()
        }
        val gl = step(0.0, w.toDouble())
        val b = DrawingBuilder()
        b.clip("cd", gl + " V" + h + " H0 Z")
        b.clip("cs", "$gl V0 H0 Z")
        var g = lo
        while (g <= hi) {
            if (!(flat && g == goals[0])) {
                b.line("x1" to pl, "x2" to w - pr, "y1" to yi(g), "y2" to yi(g), "stroke" to (if (g == lo) "var(--line)" else "var(--grid)"))
                if (Math.abs(g - goals[0]) >= 50) b.text(n(g / 60.0) + "h", "x" to pl - 5, "y" to yi(g) + 3, "font-size" to 8.5, "text-anchor" to "end", "fill" to "var(--mute)")
            }
            g += 120
        }
        val runs = ArrayList<List<DoubleArray>>()
        var r = ArrayList<DoubleArray>()
        pts.forEachIndexed { i, p ->
            if (p.s == DayStatus.DONE) r.add(doubleArrayOf(x(i), yi(p.v), i.toDouble()))
            else { if (r.isNotEmpty()) runs.add(r); r = ArrayList() }
        }
        if (r.isNotEmpty()) runs.add(r)
        for (run in runs) {
            if (run.size == 1) {
                val q = run[0]
                val gg = gy(q[2].toInt())
                b.line("x1" to q[0], "x2" to q[0], "y1" to gg, "y2" to q[1], "stroke" to (if (q[1] > gg) "var(--bad)" else "var(--good)"), "stroke-width" to 3, "stroke-opacity" to ".35")
                continue
            }
            val a = run[0][2].toInt()
            val z = run[run.size - 1][2].toInt()
            val back = StringBuilder(" L" + n(x(z)) + "," + n(gy(z)))
            for (k in z downTo a + 1) back.append(" H" + n(mid(k - 1)) + " V" + n(gy(k - 1)))
            val area = mono(run) + back + " H" + n(x(a)) + " Z"
            b.path("d" to area, "fill" to "var(--bad-soft)", "clip-path" to "url(#cd)")
            b.path("d" to area, "fill" to "var(--good-soft)", "clip-path" to "url(#cs)")
        }
        b.path("d" to step(pl.toDouble(), (w - pr).toDouble()), "fill" to "none", "stroke" to "var(--good)", "stroke-width" to 1.5, "stroke-dasharray" to "5 4")
        b.text(Words.hours(goals[0]), "x" to pl - 5, "y" to gy(0) + 3, "font-size" to 8.5, "text-anchor" to "end", "fill" to "var(--mute)")
        if (pick >= 0) b.line("x1" to x(pick), "x2" to x(pick), "y1" to pt, "y2" to base, "stroke" to "var(--mute)", "stroke-dasharray" to "2 3", "stroke-opacity" to ".7")
        for (run in runs) if (run.size > 1) {
            b.path("d" to mono(run), "fill" to "none", "stroke" to "var(--acc)", "stroke-width" to 2.2, "stroke-linecap" to "round", "stroke-linejoin" to "round")
        }
        // Days still in progress: sleep so far, hollow, joined by a dashed line
        pts.forEachIndexed { i, p ->
            if (p.s != DayStatus.OPEN || p.v == 0) return@forEachIndexed
            val q = if (i > 0) pts[i - 1] else null
            val px = x(i)
            val py = yi(p.v)
            if (q != null && (q.s == DayStatus.DONE || (q.s == DayStatus.OPEN && q.v != 0))) {
                b.line("x1" to x(i - 1), "y1" to yi(q.v), "x2" to px, "y2" to py, "stroke" to "var(--acc)", "stroke-width" to 2, "stroke-dasharray" to "3 3", "stroke-linecap" to "round")
            }
            b.circle("cx" to px, "cy" to py, "r" to (if (wk) 4 else 3) + (if (i == pick) 1.5 else 0.0), "fill" to "var(--card)", "stroke" to "var(--acc)", "stroke-width" to 2)
            if (wk) {
                var ly = if (p.v >= goals[i]) py - 9 else py + 16
                if (ly > base - 3) ly = py - 9
                if (ly < pt - 4) ly = py + 16
                b.text(Js.toFixed(p.v / 60.0, 1), "x" to px, "y" to ly, "font-size" to 9, "text-anchor" to "middle", "fill" to "var(--mute)")
            }
        }
        val ti = days.indexOf(t)
        pts.forEachIndexed { i, p ->
            val px = x(i)
            if (p.s == DayStatus.DONE) {
                val py = yi(p.v)
                val on = i == pick
                b.circle("cx" to px, "cy" to py, "r" to (if (wk) 3.5 else 2.6) + (if (on) 1.5 else 0.0), "fill" to "var(--acc)", "stroke" to "var(--card)", "stroke-width" to (if (on) 2 else 0))
                if (wk) {
                    var ly = if (p.v >= goals[i]) py - 8 else py + 15
                    if (ly > base - 3) ly = py - 8
                    if (ly < pt - 4) ly = py + 15
                    b.text(Js.toFixed(p.v / 60.0, 1), "x" to px, "y" to ly, "font-size" to 9, "text-anchor" to "middle", "fill" to "var(--ink)")
                }
            }
            if (p.d in m.missing) {
                val stepW = (w - pl - pr - 16).toDouble() / (count - 1)
                val nx = if (i > 0) (x(i - 1) + x(i)) / 2 else Math.max(pl + 3.0, x(0) - stepW / 2)
                val half = if (wk) 6 else 2
                b.line("x1" to nx - half, "x2" to nx + half, "y1" to base - 3, "y2" to base - 3, "stroke" to "var(--warn)", "stroke-width" to 3, "stroke-linecap" to "round")
            }
            val isT = p.d == t
            val fc = if (isT) "var(--ink)" else "var(--mute)"
            val fw = if (isT) 800 else null
            if (wk) {
                val op = if (p.s == DayStatus.FUTURE) ".55" else null
                b.text(Words.weekday(p.d), "x" to px, "y" to h - 18, "font-size" to 8.5, "text-anchor" to "middle", "fill" to fc, "font-weight" to fw, "fill-opacity" to op)
                b.text(p.d.dayOfMonth.toString(), "x" to px, "y" to h - 6, "font-size" to 8.5, "text-anchor" to "middle", "fill" to fc, "font-weight" to fw, "fill-opacity" to op)
            } else {
                val dn = i + 1
                if (isT || ((dn == 1 || dn % 5 == 0) && !(ti >= 0 && Math.abs(i - ti) <= 1))) {
                    b.text(dn.toString(), "x" to px, "y" to h - 7, "font-size" to 8.5, "text-anchor" to "middle", "fill" to fc, "font-weight" to fw)
                }
            }
        }
        val label = when (kind) {
            PeriodKind.MONTH -> "Sleep each day of the selected month against your sleep goal"
            PeriodKind.WEEK -> "Sleep each day of the selected week against your sleep goal"
            PeriodKind.LAST_3_DAYS -> "Sleep on each of the last 3 days against your sleep goal"
        }
        return b.build("0 0 $w $h", label)
    }

    /** Schedule's last 14 nights, newest first, 6 PM to 2 PM, with the planned sleep window shaded */
    fun diary(m: SleepModel, plan: Plan?): Drawing {
        val t = m.today
        val zone = m.zone
        val rows = (0 until 14).map { t.minusDays(it.toLong()) }
        val w = 320
        val x0 = 46
        val x1 = w - 34
        val rowH = 14
        val gap = 6
        val top = 24
        val h = top + rows.size * (rowH + gap) + 2
        fun x(v: Double) = x0 + (x1 - x0) * Math.max(0.0, Math.min(1200.0, v)) / 1200
        val b = DrawingBuilder()
        if (plan != null) {
            val a = plan.target - 1080
            val e = plan.wake + 360
            b.rect("x" to x(a.toDouble()), "y" to top - 5, "width" to Math.max(0.0, x(e.toDouble()) - x(a.toDouble())), "height" to h - top + 3, "rx" to 6, "fill" to "var(--acc-soft)")
        }
        listOf(0 to "6 PM", 180 to "", 360 to "12 AM", 540 to "", 720 to "6 AM", 900 to "", 1080 to "12 PM").forEach { (mm, l) ->
            b.line("x1" to x(mm.toDouble()), "x2" to x(mm.toDouble()), "y1" to top - 5, "y2" to h - 2, "stroke" to "var(--line)", "stroke-opacity" to (if (l.isNotEmpty()) 1.0 else 0.5))
            if (l.isNotEmpty()) b.text(l, "x" to x(mm.toDouble()), "y" to 12, "font-size" to 9.5, "text-anchor" to "middle", "fill" to "var(--mute)")
        }
        val pend = m.pending
        rows.forEachIndexed { i, wd ->
            val y = top + i * (rowH + gap)
            val ws = Time.ms(wd.minusDays(1).atTime(18, 0), zone)
            val we = ws + 1200 * 60000L
            val night = m.nightByWake[wd]
            val miss = wd in m.missing
            val isT = wd == t
            b.text(Words.weekday(wd) + " " + wd.dayOfMonth, "x" to 0, "y" to y + rowH - 3, "font-size" to 10,
                "fill" to (if (miss) "var(--warn)" else if (isT) "var(--ink)" else "var(--mute)"), "font-weight" to (if (isT) 800 else null))
            for (e in m.entries) {
                val a = Math.max(Time.ms(e.bed, zone), ws)
                val c = Math.min(Time.ms(e.wake, zone), we)
                if (c <= a) continue
                val nap = History.isNap(e)
                val xa = x((a - ws) / 60000.0)
                val xb = x((c - ws) / 60000.0)
                b.rect("x" to xa, "y" to y + (if (nap) 4 else 0), "width" to Math.max(2.0, xb - xa), "height" to (if (nap) rowH - 8 else rowH), "rx" to 3, "fill" to "var(--acc)",
                    "fill-opacity" to (if (nap) ".5" else null), title = Words.clock(Time.tod(e.bed)) + " – " + Words.clock(Time.tod(e.wake)))
            }
            if (pend != null) {
                val a = Math.max(Time.ms(pend, zone), ws)
                val c = Math.min(m.now.ms, we)
                if (c > a) {
                    val xa = x((a - ws) / 60000.0)
                    val xb = x((c - ws) / 60000.0)
                    b.rect("x" to xa, "y" to y + 1, "width" to Math.max(2.0, xb - xa), "height" to rowH - 2, "rx" to 3, "fill" to "none", "stroke" to "var(--acc)", "stroke-width" to 1.5, "stroke-dasharray" to "3 2")
                }
            }
            if (miss) b.text("Not logged", "x" to (x0 + x1) / 2.0, "y" to y + rowH - 3, "font-size" to 10, "text-anchor" to "middle", "fill" to "var(--warn)")
            if (night != null && plan != null) {
                val dl = adj(Time.tod(night.bed)) - plan.target
                val ok = Math.abs(dl) <= 30
                b.text(if (ok) "✓" else (if (dl > 0) "+" else "−") + Words.tiny(dl), "x" to w, "y" to y + rowH - 3, "font-size" to 10, "text-anchor" to "end", "font-weight" to 800,
                    "fill" to (if (ok) "var(--good)" else "var(--mute)"))
            }
        }
        return b.build("0 0 $w $h", "Sleep on each of the last 14 nights, shown from 6 PM to 2 PM, with your planned sleep window shaded")
    }
}
