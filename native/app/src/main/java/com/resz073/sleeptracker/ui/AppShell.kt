package com.resz073.sleeptracker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.resz073.sleeptracker.MainViewModel
import com.resz073.sleeptracker.R
import com.resz073.sleeptracker.data.Session
import com.resz073.sleeptracker.data.SleepState
import com.resz073.sleeptracker.engine.SyncState
import com.resz073.sleeptracker.show.TodayShow
import kotlinx.coroutines.delay
import java.time.LocalDate

/** The four views, named as the current app keeps them ("today", "trends", "schedule", "history") */
enum class Tab(val title: String, val key: String, val icon: Int) {
    Today("Today", "today", R.drawable.ic_nav_today),
    Trends("Trends", "trends", R.drawable.ic_nav_trends),
    Schedule("Schedule", "schedule", R.drawable.ic_nav_schedule),
    History("History", "history", R.drawable.ic_nav_history),
}

@Composable
fun AppShell(session: Session.SignedIn, vm: MainViewModel) {
    val c = LocalAppColors.current
    val sleep by vm.sleep.collectAsStateWithLifecycle()
    val tab = Tab.entries.firstOrNull { it.key == vm.view } ?: Tab.Today
    var settings by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    // Every 30 seconds, like the current app: the bedtime countdown and the strip move on, a new day starts
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            vm.refresh()
        }
    }

    // Messages after a button, like the current app's (Undo after a deletion)
    val toast = vm.toast
    LaunchedEffect(toast) {
        if (toast != null) {
            val r = snackbar.showSnackbar(toast.text, actionLabel = toast.action, duration = if (toast.action != null) SnackbarDuration.Long else SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) toast.onAction?.invoke()
            vm.toastShown(toast)
        }
    }

    // Back closes Settings, then returns to Today, then leaves the app
    BackHandler(enabled = settings) { settings = false }
    BackHandler(enabled = !settings && tab != Tab.Today) { vm.openView(Tab.Today.key) }

    if (settings) {
        SettingsScreen(session = session, sleep = sleep, vm = vm, onBack = { settings = false })
    } else {
        Scaffold(
            containerColor = c.bg,
            topBar = { Header(title = tab.title, sleep = sleep, onSettings = { settings = true }) },
            snackbarHost = {
                SnackbarHost(snackbar) { data ->
                    Snackbar(data, shape = RoundedCornerShape(14.dp), containerColor = c.ink, contentColor = c.bg, actionColor = c.accSoft)
                }
            },
            floatingActionButton = {
                if (tab == Tab.History && sleep is SleepState.Ready) {
                    ExtendedFloatingActionButton(
                        text = { Text("Add entry", style = font(15f, FontWeight.ExtraBold)) },
                        icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                        onClick = vm::openAdd,
                        shape = RoundedCornerShape(18.dp),
                        containerColor = c.acc,
                        contentColor = c.onAcc,
                    )
                }
            },
            bottomBar = {
                NavigationBar(containerColor = c.card2) {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { vm.openView(t.key) },
                            icon = { Icon(painterResource(t.icon), contentDescription = null) },
                            label = { Text(t.title, style = font(12.5f, FontWeight.ExtraBold)) },
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
                when (val s = sleep) {
                    is SleepState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = c.acc)
                    is SleepState.Ready -> when (tab) {
                        Tab.Today -> TodayScreen(s, vm, onSetWake = { vm.openView(Tab.Schedule.key) }, onSetGoal = { settings = true })
                        Tab.Trends -> TrendsScreen(s, vm)
                        Tab.Schedule -> ScheduleScreen(s, vm)
                        Tab.History -> HistoryScreen(s, vm)
                    }
                }
            }
        }
    }

    // The Add sleep / Edit sleep sheet
    val form = vm.form
    val ready = sleep as? SleepState.Ready
    if (form != null && ready != null) EntrySheet(form, ready.model, vm)
}

@Composable
private fun Header(title: String, sleep: SleepState, onSettings: () -> Unit) {
    val c = LocalAppColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.bg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = font(24f, FontWeight.ExtraBold), color = c.ink)
                if (title == Tab.Today.title) {
                    Text(if (sleep is SleepState.Ready) TodayShow.header(sleep.model) else dayLong(LocalDate.now()), style = font(13f, FontWeight.Bold), color = c.mute)
                }
            }
            IconButton(onClick = onSettings) {
                Icon(painterResource(R.drawable.ic_settings), contentDescription = "Settings", tint = c.ink)
            }
        }
        if (sleep is SleepState.Ready && sleep.sync == SyncState.NOT_SYNCED) {
            Text("Not synced yet: your changes are saved on this phone", style = font(13f, FontWeight.Bold), color = c.warn)
        }
    }
}
