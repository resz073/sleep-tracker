package com.resz073.sleeptracker.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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

/** The reminder switches, as version 1 labels them */
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

/** Settings, a tab of its own, in version 1's order: account, reminders, sleep goal, backup, Health Connect, sync, about */
@Composable
fun SettingsScreen(session: Session.SignedIn, sleep: SleepState, vm: MainViewModel) {
    val c = LocalAppColors.current
    val uri = LocalUriHandler.current
    val ready = sleep as? SleepState.Ready
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { picked -> picked?.let { vm.importCsv(it) } }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())) {
        ScreenTitle("Settings")
        Column(Modifier.padding(start = SIDE, end = SIDE, top = 16.dp, bottom = 28.dp)) {
            AccountPart(session, ready, vm)
            SectionDivider()
            ReminderPart(vm, hasPlan = ready?.let { Planner.plan(it.model) } != null)
            SectionDivider()
            if (ready != null) {
                GoalPart(SettingsShow.goal(ready.model), vm)
                SectionDivider()
            }
            SectionTitle("Backup")
            Text("Save all your entries as a CSV file, or add entries from one.", style = font(15f), color = c.mute, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton("Export CSV", vm::exportCsv, Modifier.weight(1f), Btn.RAISED)
                // Android's file pickers often label CSV files as plain text or unknown, so every file is shown
                PillButton("Import CSV", { importFile.launch(arrayOf("*/*")) }, Modifier.weight(1f), Btn.RAISED)
            }
            SectionDivider()
            HealthPart(vm)
            SectionDivider()
            SectionTitle("Sync")
            Text(
                when (ready?.sync) {
                    SyncState.SYNCED -> "Your entries are saved to your account and sync across your devices."
                    SyncState.NOT_SYNCED -> "Not synced yet. Your entries are kept on this phone and will sync when you're back online."
                    else -> "Syncing…"
                },
                style = font(15f), color = c.mute, modifier = Modifier.padding(top = 8.dp),
            )
            SectionDivider()
            SectionTitle("About")
            Column(Modifier.padding(top = 8.dp)) {
                LinkRow("Privacy policy") { uri.openUri(PRIVACY_URL) }
                LinkRow("How account deletion works") { uri.openUri(DELETION_URL) }
            }
            Muted("Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})", Modifier.padding(top = 12.dp))
            if (!BuildConfig.FIREBASE_APP_REGISTERED) {
                Text("Google sign-in isn't set up in this build. The setup guide on GitHub explains how.", style = font(14f), color = c.warn, modifier = Modifier.padding(top = 8.dp))
            }
            Muted("Fonts: Literata and Atkinson Hyperlegible Next, under the SIL Open Font License.", Modifier.padding(top = 4.dp))
        }
    }
}

/** A link out of the app: its name and the external-link mark */
@Composable
private fun LinkRow(text: String, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = font(16f), color = c.ink, modifier = Modifier.weight(1f))
        Icon(painterResource(R.drawable.ic_external), contentDescription = null, tint = c.mute, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun AccountPart(session: Session.SignedIn, ready: SleepState.Ready?, vm: MainViewModel) {
    val c = LocalAppColors.current
    val ctx = LocalContext.current
    var blocked by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    SectionTitle("Account")
    Text(
        "Signed in as " + session.email.ifEmpty { "your account" } + (if (session.google) " with Google" else "") + ".",
        style = font(15f), color = c.mute, modifier = Modifier.padding(top = 8.dp),
    )
    Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        PillButton("Sign out", { blocked = !vm.signOut() })
        PillButton("Delete account", { deleting = true; password = ""; vm.clearDeleteError() }, kind = Btn.DANGER)
    }
    if (blocked && ready?.canSignOut != true) {
        Text("Connect to the internet first, so your latest entries reach your account.", style = font(14f, FontWeight.Bold), color = c.warn, modifier = Modifier.padding(top = 10.dp))
    }
    if (deleting) {
        Column(Modifier.padding(top = 16.dp).fillMaxWidth().background(c.raised, RoundedCornerShape(20.dp)).padding(16.dp)) {
            RichText(
                rich { b("Delete your account?"); t(" This permanently deletes your account and all your sleep entries. Export a CSV backup first if you want to keep them.") },
                font(15f), c.ink, boldColor = c.ink,
            )
            if (session.password) {
                LabeledField(
                    "Enter your password to confirm", password, { password = it }, Modifier.padding(top = 14.dp),
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done), secret = true,
                )
            } else Muted("You’ll confirm with your Google account.", Modifier.padding(top = 10.dp), size = 15f)
            vm.deleteError?.let { Text(it, style = font(14f, FontWeight.Bold), color = c.bad, modifier = Modifier.padding(top = 10.dp)) }
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton("Keep account", { deleting = false; vm.clearDeleteError() }, Modifier.weight(1f), Btn.OUTLINE)
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
private fun ReminderPart(vm: MainViewModel, hasPlan: Boolean) {
    val r = vm.reminders
    SectionTitle("Reminders")
    Column(Modifier.padding(top = 6.dp)) {
        SWITCHES.forEach { (kind, title, sub) -> SwitchRow(title, sub, r.isOn(kind), "$title reminder") { vm.setReminder(kind, it) } }
    }
    // Version 1's note under the switches
    val note = if (!vm.notificationsAllowed && r.any) "Notifications are blocked for Sleep Tracker. Allow them in Android Settings to get reminders."
    else if (!hasPlan) "Reminders start once your bedtime is set." else ""
    if (note.isNotEmpty()) Muted(note, Modifier.padding(top = 6.dp))
    if (r.any && vm.notificationsAllowed && !vm.exactAllowed) {
        Muted("Android can hold reminders back by up to an hour to save battery.", Modifier.padding(top = 10.dp))
        PillButton("Allow on-time reminders", vm::askExact, Modifier.fillMaxWidth().padding(top = 12.dp), Btn.RAISED)
    }
}

@Composable
private fun GoalPart(g: GoalSetting, vm: MainViewModel) {
    val c = LocalAppColors.current
    SectionTitle("Sleep goal")
    Text("Birth year", style = font(13f), color = c.mute, modifier = Modifier.padding(top = 14.dp))
    MenuField(g.years.map { it to it.toString() }, g.born, "Choose your birth year", { vm.setBorn(it) }, Modifier.padding(top = 6.dp).fillMaxWidth())
    Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundButton(R.drawable.ic_remove, "15 minutes less", g.canDown) { vm.nudgeGoal(-15) }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(g.value, style = serif(36f, FontWeight.Normal, 40f), color = c.ink)
            Text("a night", style = font(14f), color = c.mute)
        }
        RoundButton(R.drawable.ic_add, "15 minutes more", g.canUp) { vm.nudgeGoal(15) }
    }
    RichText(g.note, font(15f), c.mute, Modifier.padding(top = 16.dp), boldColor = c.ink)
    Muted("Age ranges from the National Sleep Foundation.", Modifier.padding(top = 8.dp), size = 13f)
}

/** The round − and + buttons */
@Composable
private fun RoundButton(icon: Int, label: String, enabled: Boolean, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Surface(
        onClick = onClick, enabled = enabled, shape = CircleShape, color = c.raised, contentColor = c.ink,
        modifier = Modifier.size(56.dp).alpha(if (enabled) 1f else 0.4f).semantics { contentDescription = label },
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(painterResource(icon), contentDescription = null, tint = c.ink, modifier = Modifier.size(24.dp)) }
    }
}

/** Saving sleep to Health Connect (an extra of the native app) */
@Composable
private fun HealthPart(vm: MainViewModel) {
    val c = LocalAppColors.current
    SectionTitle("Health Connect")
    when (vm.healthStatus) {
        Health.Status.UNAVAILABLE -> Text("Health Connect isn't available on this phone.", style = font(15f), color = c.mute, modifier = Modifier.padding(top = 8.dp))
        Health.Status.NEEDS_INSTALL -> {
            Text(
                "Save your sleep to Health Connect, so other health apps you allow can see it. Install or update Health Connect first.",
                style = font(15f), color = c.mute, modifier = Modifier.padding(top = 8.dp),
            )
            PillButton("Get Health Connect", vm::getHealth, Modifier.fillMaxWidth().padding(top = 12.dp), Btn.RAISED)
        }
        Health.Status.AVAILABLE -> {
            Box(Modifier.padding(top = 6.dp)) {
                SwitchRow("Save sleep to Health Connect", "So other health apps you allow can see it", vm.healthOn, "Save sleep to Health Connect") { vm.setHealth(it) }
            }
            if (vm.healthOn) {
                Muted("New, changed and deleted entries are saved there. Nothing is read from Health Connect.", Modifier.padding(top = 4.dp))
                LinkRow("Open Health Connect", vm::openHealth)
            }
        }
    }
}
