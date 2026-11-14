package com.resz073.sleeptracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.resz073.sleeptracker.MainViewModel
import com.resz073.sleeptracker.data.SleepState
import com.resz073.sleeptracker.engine.Planner
import com.resz073.sleeptracker.engine.Prefs
import com.resz073.sleeptracker.show.DiaryCard
import com.resz073.sleeptracker.show.PlanCard
import com.resz073.sleeptracker.show.ScheduleShow
import com.resz073.sleeptracker.show.StepRow
import com.resz073.sleeptracker.show.WakeSetting

/** Schedule: the target bedtime, the steps to get there, the last 14 nights, and the plan's settings */
@Composable
fun ScheduleScreen(state: SleepState.Ready, vm: MainViewModel) {
    val m = state.model
    val plan = Planner.plan(m)
    val c = LocalAppColors.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val sky = ScheduleShow.sky(m, plan)
        SkyCard {
            Text("Your bedtime", style = font(15f, FontWeight.Bold), color = c.skyMute)
            Text(sky.big, style = font(if (sky.bigSmall) 34f else 56f, FontWeight.ExtraBold).copy(lineHeight = if (sky.bigSmall) 38.sp else 58.sp), color = c.skyInk)
            sky.lines.forEach { RichText(it, font(15f), c.skyMute, boldColor = c.skyInk) }
            sky.strip?.let { DrawingView(it, Modifier.padding(top = 10.dp, bottom = 8.dp)) }
            sky.stepLine?.let { RichText(it, font(15f), c.skyMute, boldColor = c.skyInk) }
        }
        ScheduleShow.plan(m, plan)?.let { PlanCardView(it) }
        ScheduleShow.diary(m, plan)?.let { DiaryCardView(it) }
        PlanSettings(ScheduleShow.wake(m), vm)
    }
}

@Composable
private fun PlanCardView(p: PlanCard) {
    val c = LocalAppColors.current
    AppCard {
        CardTitle(p.title)
        RichText(p.lead, font(14f), c.mute, Modifier.padding(bottom = 8.dp), boldColor = c.ink)
        p.steps.forEachIndexed { i, s ->
            if (i > 0) HorizontalDivider(color = c.line)
            Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(20.dp)) { Dot(s.dot) }
                Spacer(Modifier.width(10.dp))
                Text(s.text, style = font(15f), color = c.ink, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                Text(s.time, style = font(15f, FontWeight.ExtraBold), color = c.ink)
            }
        }
        p.stepsNote?.let { Muted(it, Modifier.padding(top = 6.dp)) }
        p.steady?.let { Text(it, style = font(15f), color = c.ink) }
        p.notes.forEach { Note(it) }
    }
}

@Composable
private fun Dot(d: StepRow.Dot) {
    val c = LocalAppColors.current
    val m = Modifier.padding(start = 4.dp).size(12.dp)
    when (d) {
        StepRow.Dot.DOT -> Box(m.background(c.acc, CircleShape))
        StepRow.Dot.HOLLOW -> Box(m.border(2.dp, c.acc, CircleShape))
        StepRow.Dot.END -> Box(m.background(c.good, CircleShape))
    }
}

@Composable
private fun DiaryCardView(d: DiaryCard) {
    AppCard {
        CardTitle("Your last 14 nights")
        d.lead?.let { Muted(it, Modifier.padding(bottom = 8.dp), size = 14f) }
        Legend(d.legend)
        DrawingView(d.chart)
    }
}

@Composable
private fun PlanSettings(w: WakeSetting, vm: MainViewModel) {
    val c = LocalAppColors.current
    val ctx = LocalContext.current
    val dark = isSystemInDarkTheme()
    AppCard {
        CardTitle("Plan settings")
        Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Wake-up time", style = font(15f, FontWeight.ExtraBold), color = c.ink)
            Seg(listOf("Automatic", "Set a time"), if (w.fixed) 1 else 0, { if (it == 0) vm.setWakeAutomatic() else vm.setWakeFixed() })
            val time = w.time
            if (time != null) {
                Surface(
                    onClick = { Pickers.time(ctx, time, dark) { vm.setWakeTime(it) } },
                    shape = RoundedCornerShape(14.dp), color = c.bg, contentColor = c.ink, border = BorderStroke(1.5.dp, c.line), modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(Modifier.heightIn(min = 48.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                        Text(clockText(time), style = font(15f), color = c.ink)
                    }
                }
            }
            Muted(w.note)
        }
        HorizontalDivider(color = c.line)
        Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Time to fall asleep", style = font(15f, FontWeight.ExtraBold), color = c.ink)
            MenuField(Prefs.LATENCIES.map { it to "$it minutes" }, w.latency, "15 minutes", { vm.setLatency(it) }, Modifier.fillMaxWidth())
        }
    }
}
