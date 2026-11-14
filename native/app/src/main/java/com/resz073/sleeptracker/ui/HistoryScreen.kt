package com.resz073.sleeptracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.resz073.sleeptracker.MainViewModel
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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp)) {
        AppCard {
            if (v.empty) {
                CardTitle("No entries yet")
                Muted("Tap Add entry to log a night, or use Going to sleep on Today.")
            } else {
                Row(Modifier.fillMaxWidth().padding(bottom = 6.dp).heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                    NavButton("‹", "Earlier week", v.canGoBack) { week = HistoryShow.step(m, v.week, -1)?.toEpochDay() }
                    Text(v.title, style = font(16f, FontWeight.ExtraBold), color = c.ink, modifier = Modifier.padding(horizontal = 6.dp))
                    NavButton("›", "Later week", v.canGoForward) { week = HistoryShow.step(m, v.week, 1)?.toEpochDay() }
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(v.total, style = font(17f, FontWeight.ExtraBold), color = c.ink)
                        Text(v.count, style = font(12.5f, FontWeight.Bold), color = c.mute)
                    }
                }
                v.rows.forEachIndexed { i, r ->
                    if (i > 0) HorizontalDivider(color = c.line)
                    HistoryRowView(r, vm.form?.editing == r.id, { vm.startEdit(r.id) }, { vm.delete(r.id) })
                }
            }
        }
        Spacer(Modifier.height(104.dp)) // room for the Add entry button
    }
}

@Composable
private fun HistoryRowView(r: HistoryRow, editing: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    val c = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            onClick = onEdit, shape = RoundedCornerShape(14.dp), color = if (editing) c.accSoft else Color.Transparent, contentColor = c.ink,
            modifier = Modifier.weight(1f).padding(vertical = 2.dp).semantics { contentDescription = "Edit sleep from " + r.time },
        ) {
            Column(Modifier.padding(horizontal = 8.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.length, style = font(18f, FontWeight.ExtraBold), color = c.ink)
                    if (r.nap) Tag("Nap")
                    Spacer(Modifier.weight(1f))
                    val rating = r.rating
                    if (rating != null) Text(rating, style = font(15f, FontWeight.ExtraBold), color = c.acc) else Muted("Not rated")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Muted(r.time, Modifier.weight(1f))
                    Spacer(Modifier.width(10.dp))
                    Muted(r.cycles)
                }
                r.countsAs?.let { Muted(it) }
            }
        }
        Surface(onClick = onDelete, shape = RoundedCornerShape(12.dp), color = Color.Transparent, contentColor = c.mute, modifier = Modifier.semantics { contentDescription = "Delete sleep from " + r.time }) {
            Box(Modifier.padding(horizontal = 12.dp, vertical = 14.dp), contentAlignment = Alignment.Center) {
                Text("✕", style = font(16f), color = c.mute)
            }
        }
    }
}
