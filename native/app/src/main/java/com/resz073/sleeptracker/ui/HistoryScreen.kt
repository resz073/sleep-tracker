package com.resz073.sleeptracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.resz073.sleeptracker.R
import com.resz073.sleeptracker.data.SleepState
import com.resz073.sleeptracker.engine.Entry
import com.resz073.sleeptracker.engine.History
import com.resz073.sleeptracker.engine.Time
import java.time.ZoneId

/** Step 1: every entry in the account, newest first, grouped by month. Read only. */
@Composable
fun HistoryScreen(sleep: SleepState) {
    val c = LocalAppColors.current
    when (sleep) {
        is SleepState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = c.acc)
        }
        is SleepState.Failed -> Column(Modifier.padding(16.dp)) {
            AppCard {
                CardTitle("Couldn't load your entries")
                Spacer(Modifier.height(6.dp))
                Muted(sleep.message)
            }
        }
        is SleepState.Ready -> {
            val months = sleep.entries.groupBy { it.wake.toLocalDate().withDayOfMonth(1) }.toList()
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    AppCard {
                        CardTitle(if (sleep.entries.size == 1) "1 entry" else "${sleep.entries.size} entries")
                        Spacer(Modifier.height(4.dp))
                        Muted("From your account, newest first. Adding and editing come in a later step.")
                    }
                }
                items(months, key = { it.first.toString() }) { (month, entries) ->
                    Column {
                        Text(
                            monthTitle(month),
                            style = MaterialTheme.typography.titleSmall,
                            color = c.mute,
                            modifier = Modifier.padding(start = 6.dp, bottom = 6.dp),
                        )
                        AppCard {
                            entries.forEachIndexed { i, e ->
                                if (i > 0) HorizontalDivider(color = c.line, modifier = Modifier.padding(vertical = 10.dp))
                                EntryRow(e, sleep.zone)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(e: Entry, zone: ZoneId) {
    val c = LocalAppColors.current
    // Sleep that starts between noon and 7 PM is a nap
    val nap = History.isNap(e)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(dayShort(e.wake.toLocalDate()), style = MaterialTheme.typography.titleSmall, color = c.ink)
                if (nap) {
                    Spacer(Modifier.width(8.dp))
                    Surface(shape = RoundedCornerShape(50), color = c.accSoft) {
                        Text("Nap", style = MaterialTheme.typography.labelSmall, color = c.ink, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                }
            }
            Text("${clock(e.bed)} – ${clock(e.wake)}", style = MaterialTheme.typography.bodyMedium, color = c.mute)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(hm(Time.minutes(e.bed, e.wake, zone)), style = MaterialTheme.typography.titleSmall, color = c.ink)
            if (e.q > 0) {
                Row {
                    for (i in 1..5) {
                        Icon(
                            painterResource(R.drawable.ic_star),
                            contentDescription = null,
                            tint = if (i <= e.q) c.acc else c.line,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }
        }
    }
}
