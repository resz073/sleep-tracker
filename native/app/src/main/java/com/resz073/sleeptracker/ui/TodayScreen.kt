package com.resz073.sleeptracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.resz073.sleeptracker.MainViewModel
import com.resz073.sleeptracker.data.SleepState
import com.resz073.sleeptracker.engine.Planner
import com.resz073.sleeptracker.show.LastNight
import com.resz073.sleeptracker.show.TodayShow
import com.resz073.sleeptracker.show.Tonight

/** Today: tonight's bedtime (or the sleep in progress), last night, nights not logged, and the last 14 days */
@Composable
fun TodayScreen(state: SleepState.Ready, vm: MainViewModel, onSetWake: () -> Unit, onSetGoal: () -> Unit) {
    val m = state.model
    val plan = Planner.plan(m)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TonightCard(TodayShow.tonight(m, plan), vm, onSetWake)
        if (TodayShow.goalCard(m, vm.goalCardDismissed)) {
            AppCard {
                CardTitle("Set your sleep goal")
                Muted("How much sleep you need depends on your age. Add your birth year to pick a goal in the range recommended for you.")
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("Set my goal", onSetGoal, Modifier.weight(1f))
                    PillButton("Not now", vm::dismissGoalCard, Modifier.weight(1f), Btn.TONAL)
                }
            }
        }
        // The current app's card offering reminders, until it's answered
        if (!vm.reminders.asked && !vm.reminders.any) {
            AppCard {
                CardTitle("Get reminders")
                Muted("A nudge 30 minutes before bedtime, another at bedtime, and a morning reminder to log your sleep.")
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("Turn on", vm::turnOnReminders, Modifier.weight(1f))
                    PillButton("Not now", vm::remindersLater, Modifier.weight(1f), Btn.TONAL)
                }
            }
        }
        TodayShow.lastNight(m)?.let { LastNightCard(it, vm) }
        val missing = TodayShow.missing(m)
        if (missing.isNotEmpty()) MissingCard(missing.map { it.second }) { i -> vm.prefill(missing[i].first) }
        DebtCard(state)
    }
}

@Composable
private fun TonightCard(t: Tonight, vm: MainViewModel, onSetWake: () -> Unit) {
    val c = LocalAppColors.current
    SkyCard {
        Text(t.label, style = font(15f, FontWeight.Bold), color = c.skyMute)
        Text(t.big, style = font(if (t.bigSmall) 34f else 56f, FontWeight.ExtraBold).copy(lineHeight = if (t.bigSmall) 38.sp else 58.sp), color = c.skyInk)
        t.lines.forEach { RichText(it, font(15f), c.skyMute, boldColor = c.skyInk) }
        t.strip?.let { DrawingView(it, Modifier.padding(top = 10.dp, bottom = 8.dp)) }
        when (t.mode) {
            Tonight.Mode.ASLEEP -> Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("I’m awake", vm::wakeUp, Modifier.weight(1f), Btn.SKY_DAWN)
                PillButton("Cancel", vm::cancelSleep, Modifier.weight(1f), Btn.SKY_GHOST)
            }
            Tonight.Mode.PLAN -> PillButton("Going to sleep", vm::goToSleep, Modifier.fillMaxWidth().padding(top = 12.dp), Btn.SKY)
            Tonight.Mode.NONE -> Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("Going to sleep", vm::goToSleep, Modifier.weight(1f), Btn.SKY)
                PillButton("Set wake-up", onSetWake, Modifier.weight(1f), Btn.SKY_GHOST)
            }
        }
    }
}

@Composable
private fun LastNightCard(ln: LastNight, vm: MainViewModel) {
    val c = LocalAppColors.current
    AppCard {
        if (!ln.logged) {
            CardTitle("Last night")
            Muted("Not logged yet.", Modifier.padding(bottom = 12.dp))
            PillButton("Log last night", { vm.prefill(ln.wakeDay) }, kind = Btn.TONAL)
            return@AppCard
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ln.ring?.let { DrawingView(it, Modifier.size(96.dp)) }
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Last night", style = font(17f, FontWeight.ExtraBold), color = c.ink)
                Text(ln.times, style = font(16f, FontWeight.ExtraBold), color = c.ink)
                RichText(ln.versusGoal, font(15f, FontWeight.Bold), c.ink)
                Muted(ln.cycles)
                if (ln.rating != 0.0) {
                    val label = ln.ratingLabel
                    Text(
                        buildAnnotatedString {
                            append("★★★★★".take(ln.stars))
                            withStyle(SpanStyle(color = c.acc.copy(alpha = 0.3f))) { append("★★★★★".drop(ln.stars)) }
                        },
                        style = font(15f).copy(letterSpacing = 2.sp), color = c.acc,
                        modifier = Modifier.semantics { contentDescription = label },
                    )
                }
            }
        }
        if (ln.unrated) {
            Text("How did you sleep?", style = font(14f, FontWeight.ExtraBold), color = c.ink, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (q in 1..5) PillButton(q.toString(), { vm.rate(q) }, Modifier.weight(1f).height(44.dp), Btn.TONAL)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MissingCard(names: List<String>, onLog: (Int) -> Unit) {
    AppCard {
        CardTitle("Nights not logged")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            names.forEachIndexed { i, n -> WarnChip(n) { onLog(i) } }
        }
    }
}

@Composable
private fun DebtCard(state: SleepState.Ready) {
    val c = LocalAppColors.current
    val d = TodayShow.debt(state.model)
    AppCard {
        CardTitle("Last 14 days")
        Row(verticalAlignment = Alignment.Bottom) {
            Text(d.value, style = font(34f, FontWeight.ExtraBold), color = toneColor(d.tone, c) ?: c.ink, modifier = Modifier.alignByBaseline())
            Spacer(Modifier.width(8.dp))
            Text(d.label, style = font(15f, FontWeight.Bold), color = c.mute, modifier = Modifier.alignByBaseline())
        }
        DrawingView(d.mini, Modifier.padding(top = 12.dp))
        if (d.stats.isNotEmpty()) StatGrid(d.stats, Modifier.padding(top = 12.dp))
    }
}
