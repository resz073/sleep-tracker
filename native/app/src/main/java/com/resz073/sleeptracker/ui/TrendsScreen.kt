package com.resz073.sleeptracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.resz073.sleeptracker.MainViewModel
import com.resz073.sleeptracker.data.SleepState
import com.resz073.sleeptracker.engine.PeriodKind
import com.resz073.sleeptracker.engine.Periods
import com.resz073.sleeptracker.engine.Prefs
import com.resz073.sleeptracker.engine.SleepModel
import com.resz073.sleeptracker.show.LegendItem
import com.resz073.sleeptracker.show.PeriodRow
import com.resz073.sleeptracker.show.QualityShow
import com.resz073.sleeptracker.show.Rich
import com.resz073.sleeptracker.show.TimingShow
import com.resz073.sleeptracker.show.TrendsShow
import com.resz073.sleeptracker.show.TrendsView
import com.resz073.sleeptracker.show.WeekendShow
import java.time.LocalDate

private val KINDS = listOf(PeriodKind.MONTH, PeriodKind.WEEK, PeriodKind.LAST_3_DAYS)

/** Trends: sleep against the goal by month, week or the last 3 days as candles, then timing, weekends and quality */
@Composable
fun TrendsScreen(state: SleepState.Ready, vm: MainViewModel) {
    val m = state.model
    val c = LocalAppColors.current
    val tab = vm.trendsTab
    var month by rememberSaveable { mutableStateOf<Long?>(null) }
    var week by rememberSaveable { mutableStateOf<Long?>(null) }
    var pick by rememberSaveable { mutableStateOf(-1) }
    var listOpen by rememberSaveable { mutableStateOf(false) }
    val kind = KINDS[tab.coerceIn(0, 2)]
    val selected = when (kind) { PeriodKind.MONTH -> month; PeriodKind.WEEK -> week; else -> null }?.let { LocalDate.ofEpochDay(it) }
    fun select(p: LocalDate) {
        if (kind == PeriodKind.MONTH) month = p.toEpochDay() else if (kind == PeriodKind.WEEK) week = p.toEpochDay()
        pick = -1
    }
    val view = TrendsShow.view(m, kind, selected, pick, listOpen)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
        ScreenTitle("Trends")
        Seg(listOf("Month", "Week", "3 days"), tab, { vm.openTrendsTab(it); pick = -1; listOpen = false }, Modifier.padding(start = SIDE, end = SIDE, top = 12.dp))
        if (view == null) {
            Muted("Your trends appear after you log your first night.", Modifier.padding(start = SIDE, end = SIDE, top = 20.dp), size = 15f)
        } else {
            // The figures stay while a day is picked; the picked day shows in the line under them
            val base = if (pick >= 0) TrendsShow.view(m, kind, selected, -1, false) ?: view else view
            PeriodPart(view, base, m, kind, pick, { select(it) }, { pick = if (it == pick) -1 else it }, { listOpen = !listOpen }, vm)
        }
        Column(Modifier.padding(start = SIDE, end = SIDE, top = 20.dp)) {
            TimingPart(TrendsShow.timing(m))
            HorizontalDivider(Modifier.padding(vertical = 20.dp), color = c.line)
            WeekendPart(TrendsShow.weekend(m), m.prefs.weekend, vm::setWeekend)
            HorizontalDivider(Modifier.padding(vertical = 20.dp), color = c.line)
            QualityPart(TrendsShow.quality(m))
        }
    }
}

@Composable
private fun PeriodPart(
    v: TrendsView, base: TrendsView, m: SleepModel, kind: PeriodKind, pick: Int,
    onSelect: (LocalDate) -> Unit, onPick: (Int) -> Unit, onToggleList: () -> Unit, vm: MainViewModel,
) {
    val c = LocalAppColors.current
    val unit = if (kind == PeriodKind.MONTH) "month" else "week"
    // Month or week, with arrows; the last 3 days have none
    if (kind != PeriodKind.LAST_3_DAYS) {
        Row(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            NavArrow(false, "Earlier $unit", v.canGoBack, { onSelect(Periods.step(kind, v.period, -1)) })
            Text(v.title, style = serif(22f, FontWeight.Medium, 28f), color = c.ink, modifier = Modifier.padding(horizontal = 4.dp))
            NavArrow(true, "Later $unit", v.canGoForward, { onSelect(Periods.step(kind, v.period, 1)) })
        }
    } else {
        Text(
            v.title, style = serif(22f, FontWeight.Medium, 28f), color = c.ink,
            modifier = Modifier.padding(start = SIDE, end = SIDE, top = 12.dp).heightIn(min = 48.dp).padding(top = 10.dp),
        )
    }
    Column(Modifier.padding(horizontal = SIDE)) {
        val big = v.balance.spans.firstOrNull()
        if (big != null) {
            Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.Bottom) {
                Text(big.text, style = serif(46f, FontWeight.Normal, 52f), color = toneColor(big.tone, c) ?: c.ink, modifier = Modifier.alignByBaseline())
                Spacer(Modifier.width(10.dp))
                Text(v.balance.spans.drop(1).joinToString("") { it.text }.trim(), style = font(15f), color = c.mute, modifier = Modifier.alignByBaseline())
            }
        }
        val stats = base.stats
        if (stats != null) StatGrid(stats, Modifier.padding(top = 14.dp), columns = 3, bigSize = 22f, labelSize = 13f)
        else Muted(base.readout.text, Modifier.padding(top = 10.dp), size = 15f)
        // The picked day, with Log it when its night isn't logged
        val days = Periods.days(kind, v.period)
        val picked = if (pick in days.indices) pick else -1
        Box(Modifier.padding(top = 14.dp).heightIn(min = 22.dp)) {
            if (picked >= 0) {
                val logNight = v.logNight
                if (logNight != null) Column {
                    RichText(Rich(v.readout.spans.dropLast(1)), font(15f), c.ink, boldColor = c.ink)
                    PillButton("Log it", { vm.prefill(logNight) }, kind = Btn.TEXT, size = 15f)
                }
                else RichText(v.readout, font(15f), c.ink, boldColor = c.ink)
            }
        }
        Box(Modifier.padding(top = 8.dp)) {
            CandleChart(m, days, picked, kind != PeriodKind.MONTH, v.chart.label, Periods.goalLabel(m, days), onPick)
        }
        val legend = ArrayList<LegendItem>()
        legend += LegendItem(LegendItem.Mark.SURPLUS, "Met your goal")
        legend += LegendItem(LegendItem.Mark.DEFICIT, "Under your goal")
        if (v.legend.any { it.mark == LegendItem.Mark.LIVE }) legend += LegendItem(LegendItem.Mark.LIVE, "So far")
        if (v.legend.any { it.mark == LegendItem.Mark.MISSING }) legend += LegendItem(LegendItem.Mark.MISSING, "Night not logged")
        Legend(legend)
    }
    v.allLabel?.let { PillButton(it, onToggleList, Modifier.padding(start = 12.dp, top = 12.dp), kind = Btn.TEXT, size = 15f) }
    if (v.rows.isNotEmpty()) Column(Modifier.padding(horizontal = 12.dp)) {
        v.rows.forEachIndexed { i, r ->
            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = c.line)
            PeriodRowView(r) { onSelect(r.period) }
        }
    }
}

@Composable
private fun PeriodRowView(r: PeriodRow, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = if (r.selected) c.sel else Color.Transparent, contentColor = c.ink, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.title, style = font(16f, FontWeight.Bold), color = if (r.selected) c.onSel else c.ink)
                    r.tag?.let { Tag(it, Modifier.padding(start = 8.dp)) }
                }
                RichText(r.line, font(14f), c.mute)
            }
            r.balance?.let { balance ->
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(balance, style = serif(18f, FontWeight.Normal, 22f), color = toneColor(r.balanceTone, c) ?: c.ink)
                    Text(r.balanceLabel ?: "", style = font(13f), color = c.mute)
                }
            }
        }
    }
}

@Composable
private fun TimingPart(t: TimingShow) {
    val c = LocalAppColors.current
    Row(verticalAlignment = Alignment.Bottom) {
        SectionTitle("Timing", Modifier.alignByBaseline())
        Spacer(Modifier.width(10.dp))
        Text("Your last 14 nights", style = font(14f), color = c.mute, modifier = Modifier.alignByBaseline())
    }
    val stats = t.stats
    if (stats == null || stats.size < 4) {
        Muted(t.note ?: "", Modifier.padding(top = 8.dp), size = 15f)
        return
    }
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        for (k in 0..1) {
            val steady = stats[k + 2]
            Column(Modifier.weight(1f)) {
                Text(glued(stats[k].big), style = serif(28f, FontWeight.Normal, 32f), color = c.ink)
                Text(stats[k].label, style = font(13f), color = c.mute)
                Text(steady.big, style = font(15f, FontWeight.Bold), color = toneColor(steady.tone, c) ?: c.ink, modifier = Modifier.padding(top = 8.dp))
                Text(steady.label, style = font(13f), color = c.mute)
            }
        }
    }
}

@Composable
private fun WeekendPart(w: WeekendShow, weekend: String, onWeekend: (String) -> Unit) {
    val c = LocalAppColors.current
    SectionTitle("Workdays and weekend")
    MenuField(
        listOf("fri-sat" to "Weekend: Fri & Sat", "sat-sun" to "Weekend: Sat & Sun", "fri" to "Weekend: Fri only"),
        weekend.takeIf { it in Prefs.WEEKENDS } ?: "fri-sat", "Weekend", onWeekend, Modifier.padding(top = 10.dp), small = true,
    )
    val cells = w.cells
    if (cells != null) {
        Box(Modifier.padding(top = 16.dp)) { NightAxis() }
        cells.forEachIndexed { i, cell ->
            Column(Modifier.semantics(mergeDescendants = true) {}) {
                Row(Modifier.fillMaxWidth().padding(top = if (i == 0) 8.dp else 14.dp), verticalAlignment = Alignment.Bottom) {
                    Text(cell.label, style = font(15f, FontWeight.Bold), color = c.ink, modifier = Modifier.weight(1f).alignByBaseline())
                    Text(glued(cell.big), style = font(14f), color = c.ink, modifier = Modifier.alignByBaseline())
                }
                clockRange(cell.big)?.let { (bed, wake) -> Box(Modifier.padding(top = 6.dp)) { RangeBar(bed, wake) } }
                Text(glued(cell.sub), style = font(13f), color = c.mute, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
    RichText(w.text, font(15f), if (cells != null) c.ink else c.mute, Modifier.padding(top = 12.dp), boldColor = c.ink)
}

@Composable
private fun QualityPart(q: QualityShow) {
    val c = LocalAppColors.current
    SectionTitle("Sleep quality")
    q.lead?.let { RichText(it, font(15f), c.mute, Modifier.padding(top = 6.dp), boldColor = c.ink) }
    q.rows.forEach { (label, cells) ->
        Text(label, style = font(15f, FontWeight.Bold), color = c.ink, modifier = Modifier.padding(top = 16.dp))
        cells.forEachIndexed { i, cell ->
            val value = cell.big.removePrefix("★").trim()
            Column(Modifier.semantics(mergeDescendants = true) {}) {
                Row(Modifier.fillMaxWidth().padding(top = if (i == 0) 8.dp else 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(cell.label, style = font(14f), color = c.ink, modifier = Modifier.weight(1f))
                    Star(true, 15.dp, c.acc, c.acc)
                    Spacer(Modifier.width(4.dp))
                    Text(value, style = font(14f, FontWeight.Bold), color = c.ink)
                    Spacer(Modifier.width(10.dp))
                    Text(cell.sub, style = font(13f), color = c.mute)
                }
                Box(Modifier.padding(top = 6.dp)) { RatingBar(value.toDoubleOrNull() ?: 0.0) }
            }
        }
    }
    q.note?.let { Muted(it, Modifier.padding(top = 10.dp), size = 15f) }
}
