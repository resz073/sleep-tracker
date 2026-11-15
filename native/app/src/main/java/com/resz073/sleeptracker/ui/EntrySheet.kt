package com.resz073.sleeptracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
        containerColor = c.card,
        contentColor = c.ink,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, bottom = 22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(form.title, style = font(21f, FontWeight.ExtraBold), color = c.ink, modifier = Modifier.weight(1f))
                IconButton(onClick = vm::closeForm) { Icon(painterResource(R.drawable.ic_close), contentDescription = "Close", tint = c.ink) }
            }
            Section("Went to bed")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("Time", clockText(form.bedTime), Modifier.weight(1f)) { Pickers.time(ctx, form.bedTime, dark) { t -> update { it.withBedTime(t) } } }
                Field("Date", dayText(form.bedDate), Modifier.weight(1f)) { Pickers.date(ctx, form.bedDate, dark) { d -> update { it.withBedDate(d) } } }
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp).heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Woke up", style = font(15f, FontWeight.ExtraBold), color = c.ink, modifier = Modifier.weight(1f))
                PillButton("Now", { update { it.withNow(Now.system()) } }, kind = Btn.TEXT)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("Time", clockText(form.wakeTime), Modifier.weight(1f)) { Pickers.time(ctx, form.wakeTime, dark) { t -> update { it.withWakeTime(t) } } }
                Field("Date", dayText(form.wakeDate), Modifier.weight(1f)) { Pickers.date(ctx, form.wakeDate, dark) { d -> update { it.withWakeDate(d) } } }
            }
            Text("Quality", style = font(15f, FontWeight.ExtraBold), color = c.ink, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
            MenuField(EntryForm.QUALITY, form.q, "Not rated", { q -> update { it.withQuality(q) } }, Modifier.fillMaxWidth())
            // Hints ("Set the times for this entry.") are quieter than the result or a problem, as in version 1
            val msg = form.message(model)
            val hint = msg.spans.all { !it.bold && it.tone == Tone.NONE }
            RichText(msg, font(if (hint) 13f else 14f), if (hint) c.mute else c.ink, Modifier.padding(top = 10.dp).heightIn(min = 20.dp))
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (form.editing != null) PillButton("Delete", vm::deleteFromForm, kind = Btn.DANGER)
                Spacer(Modifier.weight(1f))
                PillButton("Cancel", vm::closeForm, kind = Btn.TONAL)
                PillButton(form.saveLabel, vm::saveForm, enabled = form.canSave(model))
            }
        }
    }
}

private fun dayText(ymd: String): String = Time.parseDay(ymd)?.let { Words.day(it) } ?: ymd

@Composable
private fun Section(text: String) {
    Text(text, style = font(15f, FontWeight.ExtraBold), color = LocalAppColors.current.ink, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
}

/** A labelled field that opens a picker */
@Composable
private fun Field(label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Column(modifier) {
        Text(label, style = font(13f, FontWeight.Bold), color = c.mute, modifier = Modifier.padding(bottom = 4.dp))
        Surface(onClick = onClick, shape = RoundedCornerShape(14.dp), color = c.bg, contentColor = c.ink, border = BorderStroke(1.5.dp, c.line), modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.heightIn(min = 48.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                Text(value, style = font(15f), color = c.ink)
            }
        }
    }
}
