package com.resz073.sleeptracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.resz073.sleeptracker.MainViewModel
import com.resz073.sleeptracker.data.SleepState
import com.resz073.sleeptracker.engine.Planner
import com.resz073.sleeptracker.engine.Prefs
import com.resz073.sleeptracker.engine.Words
import com.resz073.sleeptracker.show.DiaryCard
import com.resz073.sleeptracker.show.PlanCard
import com.resz073.sleeptracker.show.ScheduleShow
import com.resz073.sleeptracker.show.StepRow
import com.resz073.sleeptracker.show.WakeSetting

/** Schedule: the target bedtime and the night it gives, then a sheet with the steps to get there, the last 14 nights and the plan's settings */
@Composable
fun ScheduleScreen(state: SleepState.Ready, vm: MainViewModel) {
    val m = state.model
    val plan = Planner.plan(m)
    val c = LocalAppColors.current
    val sky = ScheduleShow.sky(m, plan)
    HeroSheet(
        glow = true,
        top = {
            ScreenTitle("Schedule")
            Column(Modifier.padding(start = SIDE, end = SIDE, top = 16.dp)) {
                Text("Your bedtime", style = font(16f, FontWeight.Medium), color = c.mute)
                if (sky.bigSmall) Text(sky.big, style = serif(56f, FontWeight.Light, 60f), color = c.ink, modifier = Modifier.padding(top = 6.dp))
                else BigTime(sky.big, Modifier.padding(top = 4.dp))
                sky.lines.forEach {
                    if (sky.bigSmall) RichText(it, font(16f), c.mute, Modifier.padding(top = 12.dp), boldColor = c.ink)
                    else RichText(it, font(17f), c.ink, Modifier.padding(top = 14.dp), boldColor = c.ink)
                }
                if (plan != null) NightWave(Words.clock(plan.target), Words.clock(plan.wake), plan.targetCycles, sky.strip?.label, Modifier.padding(top = 20.dp))
                sky.stepLine?.let { RichText(it, font(15f), c.mute, Modifier.padding(top = 16.dp), boldColor = c.ink) }
            }
        },
        sheet = {
            ScheduleShow.plan(m, plan)?.let { p ->
                PlanPart(p)
                HorizontalDivider(Modifier.padding(vertical = 24.dp), color = c.line)
            }
            ScheduleShow.diary(m, plan)?.let { d ->
                DiaryPart(d)
                HorizontalDivider(Modifier.padding(vertical = 24.dp), color = c.line)
            }
            PlanSettings(ScheduleShow.wake(m), vm)
        },
    )
}

@Composable
private fun ColumnScope.PlanPart(p: PlanCard) {
    val c = LocalAppColors.current
    SectionTitle(p.title)
    RichText(p.lead, font(15f), c.mute, Modifier.padding(top = 8.dp), boldColor = c.ink)
    if (p.steps.isNotEmpty()) Column(Modifier.padding(top = 14.dp)) {
        p.steps.forEachIndexed { i, s ->
            Row(Modifier.fillMaxWidth().height(48.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
                StepDot(s.dot, first = i == 0, last = i == p.steps.size - 1)
                Spacer(Modifier.width(14.dp))
                Text(s.text, style = font(16f, if (s.dot == StepRow.Dot.DOT) FontWeight.Bold else FontWeight.Normal), color = c.ink, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                Text(glued(s.time), style = serif(20f, FontWeight.Normal, 24f), color = c.ink)
            }
        }
    }
    p.stepsNote?.let { Muted(it, Modifier.padding(top = 12.dp), size = 14f) }
    p.steady?.let { Text(it, style = font(15f), color = c.ink, modifier = Modifier.padding(top = 12.dp)) }
    // The first note is when to wind down, with the moon; the others are tips
    p.notes.forEachIndexed { i, n -> Note(n, moon = i == 0) }
}

/** A step's mark on the line from tonight to the target: tonight filled, the steps hollow, the target a ring with a dot */
@Composable
private fun StepDot(d: StepRow.Dot, first: Boolean, last: Boolean) {
    val c = LocalAppColors.current
    Canvas(Modifier.width(16.dp).fillMaxHeight()) {
        val center = Offset(size.width / 2, size.height / 2)
        if (!(first && last)) drawLine(
            c.line, Offset(center.x, if (first) center.y else 0f), Offset(center.x, if (last) center.y else size.height), 2.dp.toPx(),
        )
        when (d) {
            StepRow.Dot.DOT -> drawCircle(c.acc, 6.dp.toPx(), center)
            StepRow.Dot.HOLLOW -> {
                drawCircle(c.panel, 6.dp.toPx(), center)
                drawCircle(c.acc, 5.dp.toPx(), center, style = Stroke(2.dp.toPx()))
            }
            StepRow.Dot.END -> {
                drawCircle(c.panel, 8.dp.toPx(), center)
                drawCircle(c.acc, 7.dp.toPx(), center, style = Stroke(2.dp.toPx()))
                drawCircle(c.acc, 3.dp.toPx(), center)
            }
        }
    }
}

@Composable
private fun DiaryPart(d: DiaryCard) {
    val c = LocalAppColors.current
    SectionTitle("Your last 14 nights")
    d.lead?.let { Text(it, style = font(15f), color = c.mute, modifier = Modifier.padding(top = 8.dp)) }
    Legend(d.legend, Modifier.padding(top = 2.dp))
    DrawingView(d.chart, Modifier.padding(top = 12.dp))
}

@Composable
private fun PlanSettings(w: WakeSetting, vm: MainViewModel) {
    val c = LocalAppColors.current
    val ctx = LocalContext.current
    val dark = isSystemInDarkTheme()
    SectionTitle("Plan settings")
    Text("Wake-up time", style = font(15f, FontWeight.Bold), color = c.ink, modifier = Modifier.padding(top = 14.dp))
    Seg(listOf("Automatic", "Set a time"), if (w.fixed) 1 else 0, { if (it == 0) vm.setWakeAutomatic() else vm.setWakeFixed() }, Modifier.padding(top = 8.dp), container = c.raised)
    val time = w.time
    if (time != null) {
        Surface(
            onClick = { Pickers.time(ctx, time, dark) { vm.setWakeTime(it) } },
            shape = RoundedCornerShape(16.dp), color = c.raised, contentColor = c.ink,
            modifier = Modifier.padding(top = 10.dp).fillMaxWidth(),
        ) {
            Box(Modifier.heightIn(min = 52.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                Text(clockText(time), style = font(17f), color = c.ink)
            }
        }
    }
    Muted(w.note, Modifier.padding(top = 8.dp), size = 14f)
    Text("Time to fall asleep", style = font(15f, FontWeight.Bold), color = c.ink, modifier = Modifier.padding(top = 18.dp))
    MenuField(Prefs.LATENCIES.map { it to "$it minutes" }, w.latency, "15 minutes", { vm.setLatency(it) }, Modifier.padding(top = 8.dp).fillMaxWidth())
}
