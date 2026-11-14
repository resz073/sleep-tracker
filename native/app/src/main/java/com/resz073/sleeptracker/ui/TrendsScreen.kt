package com.resz073.sleeptracker.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.resz073.sleeptracker.MainViewModel
import com.resz073.sleeptracker.data.SleepState
import com.resz073.sleeptracker.engine.PeriodKind
import com.resz073.sleeptracker.engine.Periods
import com.resz073.sleeptracker.engine.Prefs
import com.resz073.sleeptracker.show.Cell
import com.resz073.sleeptracker.show.PeriodRow
import com.resz073.sleeptracker.show.QualityShow
import com.resz073.sleeptracker.show.Rich
import com.resz073.sleeptracker.show.TimingShow
import com.resz073.sleeptracker.show.TrendsShow
import com.resz073.sleeptracker.show.TrendsView
import com.resz073.sleeptracker.show.WeekendShow
import java.time.LocalDate

private val KINDS = listOf(PeriodKind.MONTH, PeriodKind.WEEK, PeriodKind.LAST_3_DAYS)

/** Trends: sleep against the goal by month, week or the last 3 days, then timing, weekends and quality */
@Composable
fun TrendsScreen(state: SleepState.Ready, vm: MainViewModel) {
    val m = state.model
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
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppCard {
            Seg(listOf("Month", "Week", "3 days"), tab, { vm.openTrendsTab(it); pick = -1; listOpen = false })
            if (view == null) Muted("Your trends appear after you log your first night.", Modifier.padding(top = 14.dp))
            else TrendsBody(view, kind, { select(it) }, { if (it >= 0) pick = it }, { listOpen = !listOpen }, vm)
        }
        TimingCard(TrendsShow.timing(m))
        WeekendCard(TrendsShow.weekend(m), m.prefs.weekend, vm::setWeekend)
        QualityCard(TrendsShow.quality(m))
    }
}

@Composable
private fun TrendsBody(v: TrendsView, kind: PeriodKind, onSelect: (LocalDate) -> Unit, onPick: (Int) -> Unit, onToggleList: () -> Unit, vm: MainViewModel) {
    val c = LocalAppColors.current
    val nav = kind != PeriodKind.LAST_3_DAYS
    Row(Modifier.fillMaxWidth().padding(top = 14.dp).heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        if (nav) NavButton("‹", "Previous " + (if (kind == PeriodKind.MONTH) "month" else "week"), v.canGoBack) { onSelect(Periods.step(kind, v.period, -1)) }
        Text(v.title, style = font(16f, FontWeight.ExtraBold), color = c.ink, modifier = Modifier.padding(horizontal = 6.dp))
        if (nav) NavButton("›", "Next " + (if (kind == PeriodKind.MONTH) "month" else "week"), v.canGoForward) { onSelect(Periods.step(kind, v.period, 1)) }
        Spacer(Modifier.weight(1f))
        Balance(v.balance)
    }
    Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp).heightIn(min = 50.dp)) {
        val stats = v.stats
        val logNight = v.logNight
        when {
            stats != null -> StatGrid(stats, Modifier.padding(top = 4.dp))
            // A picked day whose night isn't logged: the readout's last words become a button
            logNight != null -> Column {
                RichText(Rich(v.readout.spans.dropLast(1)), font(14f), c.ink, Modifier.padding(top = 6.dp))
                PillButton("Log it", { vm.prefill(logNight) }, kind = Btn.TEXT)
            }
            else -> RichText(v.readout, font(14f), if (v.readout.text == "No complete days yet.") c.mute else c.ink, Modifier.padding(top = 6.dp))
        }
    }
    Legend(v.legend)
    DrawingView(v.chart, onTap = { x -> onPick(TrendsShow.dayAt(x, v.days)) })
    v.allLabel?.let { PillButton(it, onToggleList, kind = Btn.TEXT) }
    v.rows.forEachIndexed { i, r ->
        if (i > 0 && !r.selected) HorizontalDivider(color = c.line)
        PeriodRowView(r) { onSelect(r.period) }
    }
}

/** The round ‹ and › buttons */
@Composable
fun NavButton(text: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Surface(
        onClick = onClick, enabled = enabled, shape = CircleShape, color = c.accSoft, contentColor = c.acc,
        modifier = Modifier.size(36.dp).alpha(if (enabled) 1f else 0.35f).semantics { contentDescription = label },
    ) {
        Box(contentAlignment = Alignment.Center) { Text(text, style = font(20f, FontWeight.ExtraBold), color = c.acc) }
    }
}

@Composable
private fun Balance(r: Rich) {
    if (r.spans.isEmpty()) return
    val c = LocalAppColors.current
    val big = r.spans.first()
    Column(horizontalAlignment = Alignment.End) {
        Text(big.text, style = font(17f, FontWeight.ExtraBold), color = toneColor(big.tone, c) ?: c.ink)
        Text(r.spans.drop(1).joinToString("") { it.text }, style = font(12.5f, FontWeight.Bold), color = c.mute)
    }
}

@Composable
private fun PeriodRowView(r: PeriodRow, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Surface(onClick = onClick, shape = RoundedCornerShape(14.dp), color = if (r.selected) c.accSoft else Color.Transparent, contentColor = c.ink, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.title, style = font(15f, FontWeight.ExtraBold), color = c.ink)
                    r.tag?.let { Tag(it) }
                }
                RichText(r.line, font(13f), c.mute)
            }
            r.balance?.let { balance ->
                Spacer(Modifier.width(10.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(balance, style = font(15f, FontWeight.ExtraBold), color = toneColor(r.balanceTone, c) ?: c.ink)
                    Text(r.balanceLabel ?: "", style = font(13f), color = c.mute)
                }
            }
        }
    }
}

/** A small label next to a title ("This month", "Nap") */
@Composable
fun Tag(text: String) {
    val c = LocalAppColors.current
    Surface(shape = RoundedCornerShape(50), color = c.accSoft, contentColor = c.acc, modifier = Modifier.padding(start = 8.dp)) {
        Text(text, style = font(11.5f, FontWeight.ExtraBold), color = c.acc, modifier = Modifier.padding(horizontal = 9.dp, vertical = 1.dp))
    }
}

@Composable
private fun TimingCard(t: TimingShow) {
    AppCard {
        CardTitle("Timing")
        Muted("Your last 14 nights", Modifier.padding(bottom = 12.dp), size = 14f)
        val stats = t.stats
        if (stats != null) StatGrid(stats, columns = 2, bigSize = 22f, labelSize = 13f) else Muted(t.note ?: "")
    }
}

@Composable
private fun WeekendCard(w: WeekendShow, weekend: String, onWeekend: (String) -> Unit) {
    val c = LocalAppColors.current
    AppCard {
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Workdays and weekend", style = font(17f, FontWeight.ExtraBold), color = c.ink, modifier = Modifier.weight(1f))
            MenuField(
                listOf("fri-sat" to "Weekend: Fri & Sat", "sat-sun" to "Weekend: Sat & Sun", "fri" to "Weekend: Fri only"),
                weekend.takeIf { it in Prefs.WEEKENDS } ?: "fri-sat", "Weekend", onWeekend, small = true,
            )
        }
        w.cells?.let { Cells(it) }
        RichText(w.text, font(13f), c.mute)
    }
}

@Composable
private fun QualityCard(q: QualityShow) {
    val c = LocalAppColors.current
    AppCard {
        CardTitle("Sleep quality")
        q.lead?.let { RichText(it, font(14f), c.mute, Modifier.padding(bottom = 12.dp), boldColor = c.ink) }
        q.rows.forEach { (label, cells) ->
            Text(label, style = font(14f, FontWeight.ExtraBold), color = c.ink, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
            Cells(cells)
        }
        q.note?.let { Muted(it) }
    }
}

@Composable
private fun Cells(cells: List<Cell>) {
    val c = LocalAppColors.current
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        cells.forEach { cell ->
            Surface(
                shape = RoundedCornerShape(16.dp), color = if (cell.best) c.accSoft else Color.Transparent, contentColor = c.ink,
                border = BorderStroke(1.5.dp, if (cell.best) c.acc else c.line), modifier = Modifier.weight(1f),
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(cell.label, style = font(13f, FontWeight.Bold), color = c.mute)
                    Text(cell.big, style = font(18f, FontWeight.ExtraBold), color = c.ink)
                    Text(cell.sub, style = font(13f), color = c.mute, textAlign = TextAlign.Start)
                }
            }
        }
    }
}
