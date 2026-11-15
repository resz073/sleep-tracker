package com.resz073.sleeptracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.resz073.sleeptracker.MainViewModel
import com.resz073.sleeptracker.R
import com.resz073.sleeptracker.data.SleepState
import com.resz073.sleeptracker.engine.Plan
import com.resz073.sleeptracker.engine.Planner
import com.resz073.sleeptracker.engine.SleepModel
import com.resz073.sleeptracker.engine.Time
import com.resz073.sleeptracker.engine.Words
import com.resz073.sleeptracker.show.LastNight
import com.resz073.sleeptracker.show.Stat
import com.resz073.sleeptracker.show.TodayShow
import com.resz073.sleeptracker.show.Tonight

/** Today: tonight's bedtime (or the sleep in progress), then a sheet with the prompts, last night, nights not logged and the last 14 days */
@Composable
fun TodayScreen(state: SleepState.Ready, vm: MainViewModel, onSetWake: () -> Unit, onSetGoal: () -> Unit) {
    val m = state.model
    val plan = Planner.plan(m)
    val c = LocalAppColors.current
    val sections = ArrayList<@Composable () -> Unit>()
    if (TodayShow.goalCard(m, vm.goalCardDismissed)) sections.add {
        Prompt(
            "Set your sleep goal",
            "How much sleep you need depends on your age. Add your birth year to pick a goal in the range recommended for you.",
            "Set my goal", onSetGoal, vm::dismissGoalCard,
        )
    }
    // Version 1's offer of reminders, until it's answered
    if (!vm.reminders.asked && !vm.reminders.any) sections.add {
        Prompt(
            "Get reminders",
            "A nudge 30 minutes before bedtime, another at bedtime, and a morning reminder to log your sleep.",
            "Turn on", vm::turnOnReminders, vm::remindersLater,
        )
    }
    TodayShow.lastNight(m)?.let { ln -> sections.add { LastNightPart(ln, m, vm) } }
    val missing = TodayShow.missing(m)
    if (missing.isNotEmpty()) sections.add { MissingPart(missing.map { it.second }) { i -> vm.prefill(missing[i].first) } }
    sections.add { DebtPart(m) }

    HeroSheet(
        glow = true,
        top = {
            Text(TodayShow.header(m), style = font(15f, FontWeight.Medium), color = c.mute, modifier = Modifier.padding(start = SIDE, end = SIDE, top = 12.dp))
            TonightPart(TodayShow.tonight(m, plan), m, plan, vm, onSetWake)
        },
        sheet = {
            sections.forEachIndexed { i, part ->
                if (i > 0) HorizontalDivider(Modifier.padding(vertical = 20.dp), color = c.line)
                part()
            }
        },
    )
}

/** A big time in Literata: "11:00" with a smaller "PM", glowing faintly in dark mode */
@Composable
fun BigTime(text: String, modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    val glow = with(LocalDensity.current) { 20.dp.toPx() }
    val split = text.lastIndexOf(' ')
    val number = if (split > 0) text.substring(0, split) else text
    val suffix = if (split > 0) text.substring(split + 1) else ""
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(letterSpacing = (-0.02).em)) { append(number) }
            if (suffix.isNotEmpty()) withStyle(SpanStyle(fontSize = 34.sp, fontWeight = FontWeight.Normal)) { append(" $suffix") }
        },
        style = serif(88f, FontWeight.Light, 92f).copy(shadow = if (c.dark) Shadow(c.acc.copy(alpha = 0.2f), Offset.Zero, glow) else null),
        color = c.ink,
        modifier = modifier,
    )
}

@Composable
private fun TonightPart(t: Tonight, m: SleepModel, plan: Plan?, vm: MainViewModel, onSetWake: () -> Unit) {
    val c = LocalAppColors.current
    Column(Modifier.padding(start = SIDE, end = SIDE, top = 20.dp)) {
        Text(t.label, style = font(16f, FontWeight.Medium), color = c.mute)
        if (t.bigSmall) Text(t.big, style = serif(56f, FontWeight.Light, 60f), color = c.ink, modifier = Modifier.padding(top = 6.dp))
        else BigTime(t.big, Modifier.padding(top = 4.dp))
        t.lines.forEachIndexed { i, line ->
            if (t.bigSmall) RichText(line, font(16f), c.mute, Modifier.padding(top = 12.dp), boldColor = c.ink)
            else RichText(line, font(if (i == 0) 18f else 15f), if (i == 0) c.ink else c.mute, Modifier.padding(top = if (i == 0) 14.dp else 2.dp), boldColor = c.ink)
        }
        if (plan != null) {
            val pend = m.pending
            if (pend != null) {
                val start = Time.tod(pend)
                val total = ((plan.wake - start) % 1440 + 1440) % 1440
                NightWave(
                    Words.clock(start), Words.clock(plan.wake), maxOf(1, Math.round(total / 90.0).toInt()), t.strip?.label,
                    Modifier.padding(top = 24.dp), progress = if (total > 0) (m.asleepFor() / total).toFloat() else null,
                )
            } else NightWave(Words.clock(plan.tonight), Words.clock(plan.wake), plan.cycles, t.strip?.label, Modifier.padding(top = 24.dp))
        }
        when (t.mode) {
            Tonight.Mode.ASLEEP -> Row(Modifier.padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton("I’m awake", vm::wakeUp, Modifier.weight(1.35f), Btn.TONAL, height = 56.dp, icon = R.drawable.ic_sun, size = 17f)
                PillButton("Cancel", vm::cancelSleep, Modifier.weight(1f), Btn.OUTLINE, height = 56.dp, size = 17f)
            }
            Tonight.Mode.PLAN -> PillButton("Going to sleep", vm::goToSleep, Modifier.fillMaxWidth().padding(top = 22.dp), Btn.TONAL, height = 60.dp, icon = R.drawable.ic_moon, size = 18f)
            Tonight.Mode.NONE -> Row(Modifier.padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton("Going to sleep", vm::goToSleep, Modifier.weight(1.35f), Btn.TONAL, height = 56.dp, icon = R.drawable.ic_moon, size = 17f)
                PillButton("Set wake-up", onSetWake, Modifier.weight(1f), Btn.OUTLINE, height = 56.dp, size = 17f)
            }
        }
    }
}

/** A question in the sheet with its answer buttons: Set my goal / Not now, Turn on / Not now */
@Composable
private fun Prompt(title: String, text: String, yes: String, onYes: () -> Unit, onLater: () -> Unit) {
    SectionTitle(title, size = 20f)
    Muted(text, Modifier.padding(top = 8.dp), size = 15f)
    Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PillButton(yes, onYes, height = 44.dp, size = 15f)
        PillButton("Not now", onLater, kind = Btn.TEXT, size = 15f)
    }
}

@Composable
private fun LastNightPart(ln: LastNight, m: SleepModel, vm: MainViewModel) {
    val c = LocalAppColors.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SectionTitle("Last night", Modifier.weight(1f))
        if (ln.logged && ln.rating != 0.0) Stars(ln.stars, ln.ratingLabel)
    }
    if (!ln.logged) {
        Muted("Not logged yet.", Modifier.padding(top = 6.dp), size = 15f)
        PillButton("Log last night", { vm.prefill(ln.wakeDay) }, Modifier.padding(top = 14.dp), height = 44.dp, size = 15f)
        return
    }
    Text(glued(ln.times), style = font(16f), color = c.mute, modifier = Modifier.padding(top = 6.dp))
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Bottom) {
        m.nightByWake[m.today]?.let { night ->
            Text(Words.hm(night.minutes), style = serif(32f, FontWeight.Normal, 36f), color = c.ink, modifier = Modifier.alignByBaseline())
            Spacer(Modifier.width(12.dp))
        }
        RichText(ln.versusGoal, font(15f), c.ink, Modifier.alignByBaseline())
    }
    Muted(ln.cycles, Modifier.padding(top = 4.dp), size = 15f)
    if (ln.unrated) {
        Text("How did you sleep?", style = font(15f, FontWeight.Bold), color = c.ink, modifier = Modifier.padding(top = 14.dp))
        StarPicker(0, { q -> if (q > 0) vm.rate(q) }, clearable = false)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MissingPart(names: List<String>, onLog: (Int) -> Unit) {
    SectionTitle("Nights not logged")
    FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        names.forEachIndexed { i, n -> WarnChip(n) { onLog(i) } }
    }
}

@Composable
private fun DebtPart(m: SleepModel) {
    val d = TodayShow.debt(m)
    SectionTitle("Last 14 days")
    StatGrid(listOf(Stat(d.value, d.label, d.tone)) + d.stats, Modifier.padding(top = 12.dp), columns = 2, bigSize = 26f, labelSize = 14f)
}
