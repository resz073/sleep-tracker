package com.resz073.sleeptracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.resz073.sleeptracker.MainViewModel
import com.resz073.sleeptracker.R
import com.resz073.sleeptracker.data.SleepState
import com.resz073.sleeptracker.engine.Words
import com.resz073.sleeptracker.engine.hmin
import com.resz073.sleeptracker.show.HistoryRow
import com.resz073.sleeptracker.show.HistoryShow
import java.time.LocalDate

/** "23:00" as the app writes clock times: "11:00 PM" */
fun clockText(hhmm: String): String = try { Words.clock(hmin(hhmm)) } catch (e: RuntimeException) { hhmm }

/** History: a week at a time, newest first; tap an entry to edit it, ✕ to delete it (with Undo) */
@Composable
fun HistoryScreen(sleep: SleepState, vm: MainViewModel) {
    val c = LocalAppColors.current
    if (sleep !is SleepState.Ready) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.acc) }
        return
    }
    val m = sleep.model
    var week by rememberSaveable { mutableStateOf<Long?>(null) }
    val v = HistoryShow.view(m, week?.let { LocalDate.ofEpochDay(it) })
    HeroSheet(
        glow = false,
        gap = 18.dp,
        sheetPadding = PaddingValues(top = 8.dp, bottom = 96.dp), // room under the last entry for the Add entry button
        top = {
            ScreenTitle("History")
            if (!v.empty) {
                Row(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    NavArrow(false, "Earlier week", v.canGoBack, { week = HistoryShow.step(m, v.week, -1)?.toEpochDay() })
                    Text(v.title, style = serif(22f, FontWeight.Medium, 28f), color = c.ink, modifier = Modifier.padding(horizontal = 4.dp))
                    NavArrow(true, "Later week", v.canGoForward, { week = HistoryShow.step(m, v.week, 1)?.toEpochDay() })
                }
                Row(Modifier.padding(start = SIDE, end = SIDE, top = 2.dp), verticalAlignment = Alignment.Bottom) {
                    Text(v.total, style = serif(30f, FontWeight.Normal, 38f), color = c.ink, modifier = Modifier.alignByBaseline())
                    Spacer(Modifier.width(10.dp))
                    Text(v.count, style = font(15f), color = c.mute, modifier = Modifier.alignByBaseline())
                }
            }
        },
        sheet = {
            if (v.empty) {
                Column(Modifier.padding(start = SIDE, end = SIDE, top = 16.dp)) {
                    SectionTitle("No entries yet")
                    Muted("Tap Add entry to log a night, or use Going to sleep on Today.", Modifier.padding(top = 8.dp), size = 15f)
                }
            } else v.rows.forEach { r ->
                HistoryRowView(r, vm.form?.editing == r.id, { vm.startEdit(r.id) }, { vm.delete(r.id) })
                HorizontalDivider(color = c.line)
            }
        },
    )
}

@Composable
private fun HistoryRowView(r: HistoryRow, editing: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    val c = LocalAppColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (editing) c.sel else Color.Transparent)
            .clickable(onClick = onEdit)
            .semantics { contentDescription = "Edit sleep from " + r.time }
            .padding(start = SIDE, end = 10.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(r.length, style = serif(24f, FontWeight.Normal, 30f), color = c.ink, modifier = Modifier.alignByBaseline())
                if (r.nap) Tag("Nap", Modifier.padding(start = 10.dp).alignByBaseline())
                r.rating?.let { rating ->
                    Spacer(Modifier.width(10.dp))
                    Row(Modifier.alignByBaseline(), verticalAlignment = Alignment.CenterVertically) {
                        Star(true, 15.dp, c.acc, c.acc)
                        Spacer(Modifier.width(3.dp))
                        Text(rating.removePrefix("★").trim(), style = font(15f, FontWeight.Bold), color = c.ink)
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(r.cycles, style = font(13f), color = c.mute, modifier = Modifier.alignByBaseline())
            }
            Text(glued(r.time), style = font(14f), color = c.ink, modifier = Modifier.padding(top = 4.dp))
            r.countsAs?.let { Text(glued(it), style = font(13f), color = c.mute, modifier = Modifier.padding(top = 2.dp)) }
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(44.dp).semantics { contentDescription = "Delete sleep from " + r.time }) {
            Icon(painterResource(R.drawable.ic_close), contentDescription = null, tint = c.mute, modifier = Modifier.size(20.dp))
        }
    }
}
