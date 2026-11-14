package com.resz073.sleeptracker.ui

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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.resz073.sleeptracker.BuildConfig
import com.resz073.sleeptracker.R
import com.resz073.sleeptracker.data.Session
import com.resz073.sleeptracker.data.SleepState
import com.resz073.sleeptracker.engine.SyncState

@Composable
fun SettingsScreen(session: Session.SignedIn, sleep: SleepState, onBack: () -> Unit, onSignOut: () -> Boolean) {
    val c = LocalAppColors.current
    val uri = LocalUriHandler.current
    var blocked by remember { mutableStateOf(false) }
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
            Text("Settings", style = MaterialTheme.typography.headlineSmall, color = c.ink)
        }

        AppCard {
            CardTitle("Account")
            Spacer(Modifier.height(6.dp))
            Muted("Signed in as ${session.email.ifEmpty { "your account" }} with ${if (session.google) "Google" else "email"}.")
            Spacer(Modifier.height(6.dp))
            // The current app's wording for how far your entries are synced
            Muted(
                when ((sleep as? SleepState.Ready)?.sync) {
                    SyncState.SYNCED -> "Your entries are saved to your account and sync across your devices."
                    SyncState.NOT_SYNCED -> "Not synced yet. Your entries are kept on this phone and will sync when you're back online."
                    else -> "Syncing…"
                }
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { blocked = !onSignOut() },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = c.accSoft, contentColor = c.ink),
            ) { Text("Sign out", style = MaterialTheme.typography.labelLarge) }
            if (blocked && (sleep as? SleepState.Ready)?.canSignOut != true) {
                Spacer(Modifier.height(8.dp))
                Text("Connect to the internet first, so your latest entries reach your account.", style = MaterialTheme.typography.bodyMedium, color = c.warn)
            }
        }

        AppCard {
            CardTitle("About this preview")
            Spacer(Modifier.height(6.dp))
            Muted("Sleep Tracker Next ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")
            Spacer(Modifier.height(6.dp))
            Muted(
                "The new Android app, while it's being built. Going to sleep and I'm awake save to your account " +
                    "and sync with the current app; everything else is view-only for now."
            )
            if (!BuildConfig.FIREBASE_APP_REGISTERED) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Google sign-in needs this preview registered in Firebase. The setup guide on GitHub explains how.",
                    style = MaterialTheme.typography.bodyMedium, color = c.warn,
                )
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = { uri.openUri(PRIVACY_URL) }) { Text("Privacy policy") }
            Muted("Font: Nunito, under the SIL Open Font License.")
        }
    }
}
