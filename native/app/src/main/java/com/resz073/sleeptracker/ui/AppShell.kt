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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
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

/** The five views: version 1's four ("today", "trends", "schedule", "history") and Settings, now in the bottom bar */
enum class Tab(val title: String, val key: String, val icon: Int) {
    Today("Today", "today", R.drawable.ic_nav_today),
    Trends("Trends", "trends", R.drawable.ic_nav_trends),
    Schedule("Schedule", "schedule", R.drawable.ic_nav_schedule),
    History("History", "history", R.drawable.ic_nav_history),
    Settings("Settings", "settings", R.drawable.ic_nav_settings),
}

@Composable
fun AppShell(session: Session.SignedIn, vm: MainViewModel) {
    val c = LocalAppColors.current
    val sleep by vm.sleep.collectAsStateWithLifecycle()
    val tab = Tab.entries.firstOrNull { it.key == vm.view } ?: Tab.Today
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

    // Every 30 seconds, like version 1: the bedtime countdown and the strip move on, a new day starts
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            vm.refresh()
        }
    }

    // Messages after a button, like version 1's (Undo after a deletion)
    val toast = vm.toast
    LaunchedEffect(toast) {
        if (toast != null) {
            val r = snackbar.showSnackbar(toast.text, actionLabel = toast.action, duration = if (toast.action != null) SnackbarDuration.Long else SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) toast.onAction?.invoke()
            vm.toastShown(toast)
        }
    }

    // Back returns to Today, then leaves the app
    BackHandler(enabled = tab != Tab.Today) { vm.openView(Tab.Today.key) }

    Scaffold(
        containerColor = c.bg,
        snackbarHost = {
            SnackbarHost(snackbar) { data ->
                Snackbar(data, shape = RoundedCornerShape(14.dp), containerColor = c.ink, contentColor = c.bg, actionColor = c.acc)
            }
        },
        floatingActionButton = {
            if (tab == Tab.History && sleep is SleepState.Ready) {
                ExtendedFloatingActionButton(
                    text = { Text("Add entry", style = font(16f, FontWeight.Bold)) },
                    icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = "Add entry") }, // the button's name for screen readers: Material leaves the text out
                    onClick = vm::openAdd,
                    shape = RoundedCornerShape(18.dp),
                    containerColor = c.tonal,
                    contentColor = c.onTonal,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = if (c.dark) 0.dp else 3.dp, pressedElevation = if (c.dark) 0.dp else 4.dp,
                        focusedElevation = if (c.dark) 0.dp else 3.dp, hoveredElevation = if (c.dark) 0.dp else 4.dp,
                    ),
                )
            }
        },
        bottomBar = { BottomBar(tab) { vm.openView(it.key) } },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (sleep is SleepState.Ready && (sleep as SleepState.Ready).sync == SyncState.NOT_SYNCED) {
                Text(
                    "Not synced yet: your changes are saved on this phone", style = font(13f, FontWeight.Bold), color = c.warn,
                    modifier = Modifier.padding(horizontal = SIDE, vertical = 6.dp),
                )
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                if (tab == Tab.Settings) SettingsScreen(session = session, sleep = sleep, vm = vm)
                else when (val s = sleep) {
                    is SleepState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = c.acc)
                    is SleepState.Ready -> when (tab) {
                        Tab.Today -> TodayScreen(s, vm, onSetWake = { vm.openView(Tab.Schedule.key) }, onSetGoal = { vm.openView(Tab.Settings.key) })
                        Tab.Trends -> TrendsScreen(s, vm)
                        Tab.Schedule -> ScheduleScreen(s, vm)
                        Tab.History -> HistoryScreen(s, vm)
                        Tab.Settings -> {}
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

/** When no app can take the CSV file: the text itself, to copy (as version 1 shows it) */
@Composable
private fun CsvDialog(text: String, ctx: Context, onClose: () -> Unit) {
    val c = LocalAppColors.current
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = c.raised,
        title = { Text("CSV backup", style = serif(22f, FontWeight.Medium), color = c.ink) },
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
            }) { Text("Copy", style = font(15f, FontWeight.Bold), color = c.acc) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Close", style = font(15f, FontWeight.Bold), color = c.acc) } },
    )
}

/** The bottom bar: an icon and a name for each view, the open one with the accent and a dot under it */
@Composable
private fun BottomBar(current: Tab, onOpen: (Tab) -> Unit) {
    val c = LocalAppColors.current
    Row(Modifier.fillMaxWidth().background(c.panel).navigationBarsPadding().height(68.dp).padding(horizontal = 4.dp)) {
        Tab.entries.forEach { t ->
            val on = t == current
            Column(
                Modifier.weight(1f).fillMaxHeight().selectable(selected = on, onClick = { onOpen(t) }, role = Role.Tab).padding(top = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(painterResource(t.icon), contentDescription = null, tint = if (on) c.acc else c.mute, modifier = Modifier.size(24.dp))
                Spacer(Modifier.height(3.dp))
                Text(t.title, style = font(12f, if (on) FontWeight.Bold else FontWeight.Medium), color = if (on) c.ink else c.mute)
                Spacer(Modifier.height(3.dp))
                Box(Modifier.size(4.dp).background(if (on) c.acc else Color.Transparent, CircleShape))
            }
        }
    }
}
