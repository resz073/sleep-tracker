package com.resz073.sleeptracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.resz073.sleeptracker.BuildConfig
import com.resz073.sleeptracker.MainViewModel
import com.resz073.sleeptracker.R
import com.resz073.sleeptracker.data.Session
import com.resz073.sleeptracker.data.SleepState
import com.resz073.sleeptracker.engine.SyncState
import com.resz073.sleeptracker.show.GoalSetting
import com.resz073.sleeptracker.show.SettingsShow

const val DELETION_URL = "https://resz073.github.io/sleep-tracker/delete-account.html"

/** Settings: account and sync, the sleep goal, and about */
@Composable
fun SettingsScreen(session: Session.SignedIn, sleep: SleepState, vm: MainViewModel, onBack: () -> Unit) {
    val c = LocalAppColors.current
    val uri = LocalUriHandler.current
    var blocked by remember { mutableStateOf(false) }
    val ready = sleep as? SleepState.Ready
    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), contentDescription = "Back", tint = c.ink) }
            Text("Settings", style = font(21f, FontWeight.ExtraBold), color = c.ink)
        }

        AppCard {
            Text("Account", style = font(15f, FontWeight.ExtraBold), color = c.ink)
            Spacer(Modifier.height(8.dp))
            val who = session.email.ifEmpty { "your account" }
            Muted("Signed in as " + who + (if (session.google) " with Google" else "") + ".")
            Spacer(Modifier.height(6.dp))
            // The current app's wording for how far your entries are synced
            Muted(
                when (ready?.sync) {
                    SyncState.SYNCED -> "Your entries are saved to your account and sync across your devices."
                    SyncState.NOT_SYNCED -> "Not synced yet. Your entries are kept on this phone and will sync when you're back online."
                    else -> "Syncing…"
                }
            )
            Spacer(Modifier.height(12.dp))
            PillButton("Sign out", { blocked = !vm.signOut() }, Modifier.fillMaxWidth(), Btn.TONAL)
            if (blocked && ready?.canSignOut != true) {
                Spacer(Modifier.height(8.dp))
                Text("Connect to the internet first, so your latest entries reach your account.", style = font(14f, FontWeight.Bold), color = c.warn)
            }
        }

        if (ready != null) AppCard { GoalField(SettingsShow.goal(ready.model), vm) }

        AppCard {
            Text("About", style = font(15f, FontWeight.ExtraBold), color = c.ink)
            Spacer(Modifier.height(8.dp))
            Muted("Sleep Tracker Next ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")
            Spacer(Modifier.height(6.dp))
            Muted(
                "The new Android app, while it's being built. Everything here saves to your account and syncs with the current app. " +
                    "Reminders, CSV backup and account deletion come in the next step."
            )
            if (!BuildConfig.FIREBASE_APP_REGISTERED) {
                Spacer(Modifier.height(8.dp))
                Text("Google sign-in needs this preview registered in Firebase. The setup guide on GitHub explains how.", style = font(14f), color = c.warn)
            }
            Spacer(Modifier.height(4.dp))
            PillButton("Privacy policy", { uri.openUri(PRIVACY_URL) }, kind = Btn.TEXT)
            PillButton("How account deletion works", { uri.openUri(DELETION_URL) }, kind = Btn.TEXT)
            Muted("Font: Nunito, under the SIL Open Font License.")
        }
    }
}

@Composable
private fun GoalField(g: GoalSetting, vm: MainViewModel) {
    val c = LocalAppColors.current
    Text("Sleep goal", style = font(15f, FontWeight.ExtraBold), color = c.ink)
    Text("Birth year", style = font(13f, FontWeight.Bold), color = c.mute, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
    MenuField(g.years.map { it to it.toString() }, g.born, "Choose your birth year", { vm.setBorn(it) }, Modifier.fillMaxWidth())
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        PillButton("−", { vm.nudgeGoal(-15) }, Modifier.width(56.dp), Btn.TONAL, enabled = g.canDown)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(g.value, style = font(26f, FontWeight.ExtraBold), color = c.ink)
            Muted("a night")
        }
        PillButton("+", { vm.nudgeGoal(15) }, Modifier.width(56.dp), Btn.TONAL, enabled = g.canUp)
    }
    RichText(g.note, font(13f), c.mute, Modifier.padding(top = 8.dp))
    Muted("Age ranges from the National Sleep Foundation.", Modifier.padding(top = 8.dp).alpha(0.8f))
}
