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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.resz073.sleeptracker.BuildConfig
import com.resz073.sleeptracker.R
import com.resz073.sleeptracker.data.Session

@Composable
fun SettingsScreen(session: Session.SignedIn, onBack: () -> Unit, onSignOut: () -> Unit) {
    val c = LocalAppColors.current
    val uri = LocalUriHandler.current
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
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = c.accSoft, contentColor = c.ink),
            ) { Text("Sign out", style = MaterialTheme.typography.labelLarge) }
        }

        AppCard {
            CardTitle("About this preview")
            Spacer(Modifier.height(6.dp))
            Muted("Sleep Tracker Next ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")
            Spacer(Modifier.height(6.dp))
            Muted(
                "The new Android app, while it's being built. It shows your entries but doesn't change anything " +
                    "in your account yet, so keep using the current app to log your sleep."
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
