package com.resz073.sleeptracker.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.resz073.sleeptracker.BuildConfig
import com.resz073.sleeptracker.MainViewModel
import com.resz073.sleeptracker.R
import com.resz073.sleeptracker.data.Health
import com.resz073.sleeptracker.data.Session
import com.resz073.sleeptracker.data.SleepState
import com.resz073.sleeptracker.engine.Planner
import com.resz073.sleeptracker.engine.ReminderKind
import com.resz073.sleeptracker.engine.SyncState
import com.resz073.sleeptracker.show.GoalSetting
import com.resz073.sleeptracker.show.SettingsShow
import com.resz073.sleeptracker.show.rich

const val DELETION_URL = "https://resz073.github.io/sleep-tracker/delete-account.html"

/** The reminder switches, as the current app labels them */
private val SWITCHES = listOf(
    Triple(ReminderKind.WIND_DOWN, "Wind-down", "30 minutes before bedtime"),
    Triple(ReminderKind.BEDTIME, "Bedtime", "With a Going to sleep button"),
    Triple(ReminderKind.MORNING, "Morning", "An hour after wake-up, to log your sleep"),
)

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/** Settings, in the current app's order: account, reminders, sleep goal, backup, sync, about */
@Composable
fun SettingsScreen(session: Session.SignedIn, sleep: SleepState, vm: MainViewModel, onBack: () -> Unit) {
    val c = LocalAppColors.current
    val uri = LocalUriHandler.current
    val ready = sleep as? SleepState.Ready
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { picked -> picked?.let { vm.importCsv(it) } }
    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), contentDescription = "Back", tint = c.ink) }
            Text("Settings", style = font(21f, FontWeight.ExtraBold), color = c.ink)
        }

        AppCard { AccountField(session, ready, vm) }

        AppCard { ReminderField(vm, hasPlan = ready?.let { Planner.plan(it.model) } != null) }

        if (ready != null) AppCard { GoalField(SettingsShow.goal(ready.model), vm) }

        AppCard {
            FieldLabel("Backup")
            Muted("Save all your entries as a CSV file, or add entries from one.", Modifier.padding(top = 8.dp))
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("Export CSV", vm::exportCsv, Modifier.weight(1f), Btn.TONAL)
                // Android's file pickers often label CSV files as plain text or unknown, so every file is shown
                PillButton("Import CSV", { importFile.launch(arrayOf("*/*")) }, Modifier.weight(1f), Btn.TONAL)
            }
        }

        AppCard { HealthField(vm) }

        AppCard {
            FieldLabel("Sync")
            Muted(
                when (ready?.sync) {
                    SyncState.SYNCED -> "Your entries are saved to your account and sync across your devices."
                    SyncState.NOT_SYNCED -> "Not synced yet. Your entries are kept on this phone and will sync when you're back online."
                    else -> "Syncing…"
                },
                Modifier.padding(top = 8.dp),
            )
        }

        AppCard {
            FieldLabel("About")
            PillButton("Privacy policy", { uri.openUri(PRIVACY_URL) }, kind = Btn.TEXT)
            PillButton("How account deletion works", { uri.openUri(DELETION_URL) }, kind = Btn.TEXT)
            Muted("Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")
            if (!BuildConfig.FIREBASE_APP_REGISTERED) {
                Spacer(Modifier.height(8.dp))
                Text("Google sign-in isn't set up in this build. The setup guide on GitHub explains how.", style = font(14f), color = c.warn)
            }
            Muted("Font: Nunito, under the SIL Open Font License.", Modifier.padding(top = 6.dp))
        }
    }
}

/** A field's heading (.fl) */
@Composable
private fun FieldLabel(text: String) {
    Text(text, style = font(15f, FontWeight.ExtraBold), color = LocalAppColors.current.ink)
}

@Composable
private fun ColumnScope.AccountField(session: Session.SignedIn, ready: SleepState.Ready?, vm: MainViewModel) {
    val c = LocalAppColors.current
    val ctx = LocalContext.current
    var blocked by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    FieldLabel("Account")
    Muted("Signed in as " + session.email.ifEmpty { "your account" } + (if (session.google) " with Google" else "") + ".", Modifier.padding(top = 8.dp))
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PillButton("Sign out", { blocked = !vm.signOut() }, Modifier.weight(1f), Btn.TONAL)
        PillButton("Delete account", { deleting = true; password = ""; vm.clearDeleteError() }, Modifier.weight(1f), Btn.DANGER)
    }
    if (blocked && ready?.canSignOut != true) {
        Text("Connect to the internet first, so your latest entries reach your account.", style = font(14f, FontWeight.Bold), color = c.warn, modifier = Modifier.padding(top = 8.dp))
    }
    if (deleting) {
        Column(
            Modifier.padding(top = 4.dp).fillMaxWidth().background(c.badSoft, RoundedCornerShape(16.dp)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RichText(
                rich { b("Delete your account?"); t(" This permanently deletes your account and all your sleep entries. Export a CSV backup first if you want to keep them.") },
                font(14f), c.ink,
            )
            if (session.password) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Enter your password to confirm") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else Muted("You’ll confirm with your Google account.")
            vm.deleteError?.let { Text(it, style = font(14f, FontWeight.Bold), color = c.bad) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("Keep account", { deleting = false; vm.clearDeleteError() }, Modifier.weight(1f), Btn.TONAL)
                PillButton(
                    if (vm.deleting) "Deleting…" else "Delete account",
                    { ctx.activity()?.let { vm.deleteAccount(if (session.password) password else null, it) } },
                    Modifier.weight(1f), Btn.DANGER_FILL, enabled = !vm.deleting,
                )
            }
        }
    }
}

@Composable
private fun ReminderField(vm: MainViewModel, hasPlan: Boolean) {
    val c = LocalAppColors.current
    val r = vm.reminders
    FieldLabel("Reminders")
    SWITCHES.forEach { (kind, title, sub) ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = font(15f, FontWeight.Bold), color = c.ink)
                Muted(sub)
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = r.isOn(kind),
                onCheckedChange = { vm.setReminder(kind, it) },
                modifier = Modifier.semantics { contentDescription = "$title reminder" },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = c.onAcc, checkedTrackColor = c.acc, checkedBorderColor = c.acc,
                    uncheckedThumbColor = c.mute, uncheckedTrackColor = c.card2, uncheckedBorderColor = c.mute,
                ),
            )
        }
    }
    // The current app's note under the switches
    val note = if (!vm.notificationsAllowed && r.any) "Notifications are blocked for Sleep Tracker. Allow them in Android Settings to get reminders."
    else if (!hasPlan) "Reminders start once your bedtime is set." else ""
    if (note.isNotEmpty()) Muted(note, Modifier.padding(top = 4.dp))
    if (r.any && vm.notificationsAllowed && !vm.exactAllowed) {
        Muted("Android can hold reminders back by up to an hour to save battery.", Modifier.padding(top = 8.dp))
        PillButton("Allow on-time reminders", vm::askExact, Modifier.fillMaxWidth().padding(top = 8.dp), Btn.TONAL)
    }
}

/** Saving sleep to Health Connect (an extra of the native app) */
@Composable
private fun HealthField(vm: MainViewModel) {
    val c = LocalAppColors.current
    FieldLabel("Health Connect")
    when (vm.healthStatus) {
        Health.Status.UNAVAILABLE -> Muted("Health Connect isn't available on this phone.", Modifier.padding(top = 8.dp))
        Health.Status.NEEDS_INSTALL -> {
            Muted("Save your sleep to Health Connect, so other health apps you allow can see it. Install or update Health Connect first.", Modifier.padding(top = 8.dp))
            PillButton("Get Health Connect", vm::getHealth, Modifier.fillMaxWidth().padding(top = 8.dp), Btn.TONAL)
        }
        Health.Status.AVAILABLE -> {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Save sleep to Health Connect", style = font(15f, FontWeight.Bold), color = c.ink)
                    Muted("So other health apps you allow can see it")
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = vm.healthOn,
                    onCheckedChange = { vm.setHealth(it) },
                    modifier = Modifier.semantics { contentDescription = "Save sleep to Health Connect" },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = c.onAcc, checkedTrackColor = c.acc, checkedBorderColor = c.acc,
                        uncheckedThumbColor = c.mute, uncheckedTrackColor = c.card2, uncheckedBorderColor = c.mute,
                    ),
                )
            }
            if (vm.healthOn) {
                Muted("New, changed and deleted entries are saved there. Nothing is read from Health Connect.", Modifier.padding(top = 4.dp))
                PillButton("Open Health Connect", vm::openHealth, kind = Btn.TEXT)
            }
        }
    }
}

@Composable
private fun GoalField(g: GoalSetting, vm: MainViewModel) {
    val c = LocalAppColors.current
    FieldLabel("Sleep goal")
    Text("Birth year", style = font(13f, FontWeight.Bold), color = c.mute, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
    MenuField(g.years.map { it to it.toString() }, g.born, "Choose your birth year", { vm.setBorn(it) }, Modifier.fillMaxWidth())
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        PillButton("−", { vm.nudgeGoal(-15) }, Modifier.width(56.dp).semantics { contentDescription = "15 minutes less" }, Btn.TONAL, enabled = g.canDown)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(g.value, style = font(26f, FontWeight.ExtraBold), color = c.ink)
            Muted("a night")
        }
        PillButton("+", { vm.nudgeGoal(15) }, Modifier.width(56.dp).semantics { contentDescription = "15 minutes more" }, Btn.TONAL, enabled = g.canUp)
    }
    RichText(g.note, font(13f), c.mute, Modifier.padding(top = 8.dp))
    Muted("Age ranges from the National Sleep Foundation.", Modifier.padding(top = 8.dp).alpha(0.8f))
}
