package com.resz073.sleeptracker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.resz073.sleeptracker.MainViewModel
import com.resz073.sleeptracker.R
import com.resz073.sleeptracker.data.Session
import com.resz073.sleeptracker.data.SleepState
import com.resz073.sleeptracker.engine.SyncState
import com.resz073.sleeptracker.engine.Time
import java.time.LocalDate

enum class Tab(val title: String, val icon: Int) {
    Today("Today", R.drawable.ic_nav_today),
    Trends("Trends", R.drawable.ic_nav_trends),
    Schedule("Schedule", R.drawable.ic_nav_schedule),
    History("History", R.drawable.ic_nav_history),
}

@Composable
fun AppShell(session: Session.SignedIn, vm: MainViewModel) {
    val c = LocalAppColors.current
    val sleep by vm.sleep.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.Today) }
    var settings by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    // Messages after a button, like the current app's
    val toast = vm.toast
    LaunchedEffect(toast) {
        if (toast != null) {
            snackbar.showSnackbar(toast) // a newer message cancels this one and shows instead
            vm.toastShown()
        }
    }

    // Back closes Settings, then returns to Today, then leaves the app
    BackHandler(enabled = settings) { settings = false }
    BackHandler(enabled = !settings && tab != Tab.Today) { tab = Tab.Today }

    if (settings) {
        SettingsScreen(session = session, sleep = sleep, onBack = { settings = false }, onSignOut = { vm.signOut() })
    } else {
        Scaffold(
            containerColor = c.bg,
            topBar = { Header(title = tab.title, sleep = sleep, onSettings = { settings = true }) },
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                NavigationBar(containerColor = c.card) {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = { Icon(painterResource(t.icon), contentDescription = null) },
                            label = { Text(t.title) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = c.acc, selectedTextColor = c.ink, indicatorColor = c.accSoft,
                                unselectedIconColor = c.mute, unselectedTextColor = c.mute,
                            ),
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    Tab.Today -> TodayPreview(sleep, onSleep = vm::goToSleep, onAwake = vm::wakeUp, onCancel = vm::cancelSleep)
                    Tab.Trends -> ComingSoon("Trends", "Your sleep against your goal by month, week and the last 3 days, with your timing and patterns.")
                    Tab.Schedule -> ComingSoon("Schedule", "Your bedtime plan: the target worked out from your wake-up time, and the steps to get there.")
                    Tab.History -> HistoryScreen(sleep)
                }
            }
        }
    }
}

@Composable
private fun Header(title: String, sleep: SleepState, onSettings: () -> Unit) {
    val c = LocalAppColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.bg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 18.dp, end = 6.dp, top = 8.dp, bottom = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineMedium, color = c.ink)
                Text(dayLong(LocalDate.now()), style = MaterialTheme.typography.bodyMedium, color = c.mute)
            }
            IconButton(onClick = onSettings) {
                Icon(painterResource(R.drawable.ic_settings), contentDescription = "Settings", tint = c.ink)
            }
        }
        if (sleep is SleepState.Ready && sleep.sync == SyncState.NOT_SYNCED) {
            Text("Not synced yet: your changes are saved on this phone", style = MaterialTheme.typography.bodySmall, color = c.warn)
        }
    }
}

/**
 * For now Today shows the latest sleep and the Going to sleep / I'm awake buttons, which save to the account
 * like the current app's. The full Today screen comes in step 4.
 */
@Composable
private fun TodayPreview(sleep: SleepState, onSleep: () -> Unit, onAwake: () -> Unit, onCancel: () -> Unit) {
    val c = LocalAppColors.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(shape = RoundedCornerShape(26.dp), color = c.sky, contentColor = c.skyInk, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(22.dp)) {
                when (sleep) {
                    is SleepState.Loading -> Text("Loading your entries…", color = c.skyMute)
                    is SleepState.Ready -> {
                        val since = sleep.asleepSince
                        val last = sleep.entries.firstOrNull()
                        if (since != null) {
                            Text("Sleeping since", style = MaterialTheme.typography.titleMedium, color = c.skyMute)
                            Text(clock(since), style = MaterialTheme.typography.displaySmall, color = c.skyInk)
                            Text(dayLong(since.toLocalDate()), color = c.skyMute)
                            Spacer(Modifier.height(16.dp))
                            Row {
                                Button(
                                    onClick = onAwake,
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = c.skyDawn, contentColor = c.onSkyDawn),
                                ) { Text("I’m awake", style = MaterialTheme.typography.labelLarge) }
                                Spacer(Modifier.width(10.dp))
                                OutlinedButton(
                                    onClick = onCancel,
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    border = BorderStroke(1.5.dp, c.skyLine),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = c.skyInk),
                                ) { Text("Cancel", style = MaterialTheme.typography.labelLarge) }
                            }
                        } else {
                            if (last != null) {
                                Text("Last logged sleep", style = MaterialTheme.typography.titleMedium, color = c.skyMute)
                                Text(hm(Time.minutes(last.bed, last.wake, sleep.zone)), style = MaterialTheme.typography.displaySmall, color = c.skyInk)
                                Text("${clock(last.bed)} – ${clock(last.wake)}, ${dayShort(last.wake.toLocalDate())}", color = c.skyMute)
                            } else {
                                Text("No sleep logged yet", style = MaterialTheme.typography.titleLarge, color = c.skyInk)
                            }
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = onSleep,
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = c.skyAcc, contentColor = c.sky),
                            ) { Text("Going to sleep", style = MaterialTheme.typography.labelLarge) }
                        }
                    }
                }
            }
        }
        AppCard {
            CardTitle("More is on the way")
            Spacer(Modifier.height(6.dp))
            Muted(
                "Going to sleep and I’m awake work like the current app's and sync with it, even when you start offline. " +
                    "Your bedtime for tonight, adding and editing entries, and your sleep debt come in the next step."
            )
        }
    }
}

@Composable
private fun ComingSoon(title: String, text: String) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        AppCard {
            CardTitle("$title is coming")
            Spacer(Modifier.height(6.dp))
            Muted(text)
        }
    }
}
