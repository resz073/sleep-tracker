package com.resz073.sleeptracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.resz073.sleeptracker.MainViewModel
import com.resz073.sleeptracker.R
import com.resz073.sleeptracker.engine.Now
import com.resz073.sleeptracker.engine.SleepModel
import com.resz073.sleeptracker.engine.Time
import com.resz073.sleeptracker.engine.Words
import com.resz073.sleeptracker.show.EntryForm
import com.resz073.sleeptracker.show.Tone

/** The Add sleep / Edit sleep sheet */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntrySheet(form: EntryForm, model: SleepModel, vm: MainViewModel) {
    val c = LocalAppColors.current
    val ctx = LocalContext.current
    val dark = isSystemInDarkTheme()
    // Pickers answer later: change the form as it is then
    fun update(f: (EntryForm) -> EntryForm) { vm.form?.let { vm.editForm(f(it)) } }
    ModalBottomSheet(
        onDismissRequest = vm::closeForm,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = c.sheet,
        contentColor = c.ink,
        tonalElevation = 0.dp,
        scrimColor = if (c.dark) Color(0x9E080506) else c.ink.copy(alpha = 0.45f),
        dragHandle = { Box(Modifier.padding(top = 12.dp).size(32.dp, 4.dp).background(c.mute.copy(alpha = 0.5f), RoundedCornerShape(2.dp))) },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = SIDE, end = SIDE, top = 12.dp, bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(form.title, style = serif(28f, FontWeight.Medium, 34f), color = c.ink, modifier = Modifier.weight(1f))
                IconButton(onClick = vm::closeForm, modifier = Modifier.offset(x = 12.dp)) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = "Close", tint = c.ink)
                }
            }
            Text("Went to bed", style = font(15f, FontWeight.Bold), color = c.ink, modifier = Modifier.padding(top = 12.dp))
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Field("Went to bed", "Time", clockText(form.bedTime), Modifier.weight(1f)) { Pickers.time(ctx, form.bedTime, dark) { t -> update { it.withBedTime(t) } } }
                Field("Went to bed", "Date", dayText(form.bedDate), Modifier.weight(1f)) { Pickers.date(ctx, form.bedDate, dark) { d -> update { it.withBedDate(d) } } }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Woke up", style = font(15f, FontWeight.Bold), color = c.ink, modifier = Modifier.weight(1f))
                PillButton("Now", { update { it.withNow(Now.system()) } }, Modifier.offset(x = 12.dp), kind = Btn.TEXT, size = 15f)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Field("Woke up", "Time", clockText(form.wakeTime), Modifier.weight(1f)) { Pickers.time(ctx, form.wakeTime, dark) { t -> update { it.withWakeTime(t) } } }
                Field("Woke up", "Date", dayText(form.wakeDate), Modifier.weight(1f)) { Pickers.date(ctx, form.wakeDate, dark) { d -> update { it.withWakeDate(d) } } }
            }
            Text("Quality", style = font(15f, FontWeight.Bold), color = c.ink, modifier = Modifier.padding(top = 18.dp))
            // Five stars; tapping the chosen one again clears the rating. The 48 dp targets start 9 dp before the first star's edge
            Row(Modifier.padding(top = 2.dp).offset(x = (-9).dp), verticalAlignment = Alignment.CenterVertically) {
                StarPicker(form.q, { q -> update { it.withQuality(q) } })
                Text(EntryForm.QUALITY.firstOrNull { it.first == form.q }?.second ?: "Not rated", style = font(16f), color = c.mute, modifier = Modifier.padding(start = 6.dp))
            }
            // Hints ("Set the times for this entry.") are quieter than the result or a problem, as in version 1
            val msg = form.message(model)
            val hint = msg.spans.all { !it.bold && it.tone == Tone.NONE }
            RichText(msg, font(if (hint) 14f else 16f), if (hint) c.mute else c.ink, Modifier.padding(top = 12.dp).heightIn(min = 22.dp), boldColor = c.ink)
            Row(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (form.editing != null) PillButton("Delete", vm::deleteFromForm, Modifier.offset(x = (-12).dp), kind = Btn.DANGER)
                Spacer(Modifier.weight(1f))
                PillButton("Cancel", vm::closeForm, kind = Btn.TEXT)
                PillButton(form.saveLabel, vm::saveForm, enabled = form.canSave(model), height = 52.dp)
            }
        }
    }
}

private fun dayText(ymd: String): String = Time.parseDay(ymd)?.let { Words.day(it) } ?: ymd

/** A labelled field that opens a picker: "Went to bed, time: 11:15 PM" to screen readers */
@Composable
private fun Field(group: String, label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Column(modifier) {
        Text(label, style = font(13f), color = c.mute)
        Surface(
            onClick = onClick, shape = RoundedCornerShape(16.dp), color = c.raised, contentColor = c.ink,
            modifier = Modifier.padding(top = 6.dp).fillMaxWidth().semantics { contentDescription = group + ", " + label.lowercase() + ": " + value },
        ) {
            Box(Modifier.heightIn(min = 52.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                Text(value, style = font(17f), color = c.ink)
            }
        }
    }
}
