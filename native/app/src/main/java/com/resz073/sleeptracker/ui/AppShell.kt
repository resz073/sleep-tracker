package com.resz073.sleeptracker.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import com.resz073.sleeptracker.data.Health
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.resz073.sleeptracker.MainViewModel
import com.resz073.sleeptracker.UiEvent
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

    // What only the screen can do for the view model: permissions, Android's settings pages, sharing a file
    val ctx = LocalContext.current
    var askThen by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        askThen?.invoke(granted)
        askThen = null
    }
    // Health Connect's permission screen (Android 9 and later, where Health Connect works)
    val askHealth = if (Build.VERSION.SDK_INT >= 28) {
        rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
            vm.healthAnswered(granted.containsAll(Health.permissions()))
        }
    } else null
    var csvText by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                is UiEvent.AskNotifications ->
                    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        askThen = e.then
                        askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else e.then(false) // allowed but turned off in Android Settings: only the person can turn them back on there
                UiEvent.ExactAlarms ->
                    if (Build.VERSION.SDK_INT >= 31) {
                        try {
                            ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + ctx.packageName)))
                        } catch (x: ActivityNotFoundException) {
                            vm.exactUnavailable()
                        }
                    } else vm.exactUnavailable()
                UiEvent.NotificationSettings -> openNotificationSettings(ctx)
                is UiEvent.Share ->
                    try {
                        ctx.startActivity(e.intent)
                    } catch (x: ActivityNotFoundException) {
                        csvText = e.text
                    }
                UiEvent.AskHealth ->
                    try {
                        askHealth?.launch(Health.permissions()) ?: vm.healthAnswered(false)
                    } catch (x: ActivityNotFoundException) {
                        vm.healthAnswered(false)
                    }
                is UiEvent.Open ->
                    try {
                        ctx.startActivity(e.intent)
                    } catch (x: ActivityNotFoundException) {
                        // no app for it on this phone
                    }
            }
        }
    }
    csvText?.let { text -> CsvDialog(text, ctx) { csvText = null } }

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
        // Settings has messages too (Imported, Reminders are on…): the same message bar, over it
        Box(Modifier.fillMaxSize()) {
            SettingsScreen(session = session, sleep = sleep, vm = vm, onBack = { settings = false })
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(8.dp)) { data ->
                Snackbar(data, shape = RoundedCornerShape(14.dp), containerColor = c.ink, contentColor = c.bg, actionColor = c.accSoft)
            }
        }
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

private fun openNotificationSettings(ctx: Context) {
    val i = if (Build.VERSION.SDK_INT >= 26) Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
    else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))
    try {
        ctx.startActivity(i)
    } catch (x: ActivityNotFoundException) {
        // no settings page for it on this phone
    }
}

/** When no app can take the CSV file: the text itself, to copy (as the current app shows it) */
@Composable
private fun CsvDialog(text: String, ctx: Context, onClose: () -> Unit) {
    val c = LocalAppColors.current
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = c.card,
        title = { Text("CSV backup", style = font(19f, FontWeight.ExtraBold), color = c.ink) },
        text = {
            Column {
                Muted("Copy the text below and keep it somewhere safe.", Modifier.padding(bottom = 8.dp))
                SelectionContainer(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                    Text(text, style = font(12f).copy(fontFamily = FontFamily.Monospace), color = c.ink)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                ctx.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Sleep log", text))
                onClose()
            }) { Text("Copy", style = font(15f, FontWeight.ExtraBold), color = c.acc) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Close", style = font(15f, FontWeight.ExtraBold), color = c.acc) } },
    )
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
