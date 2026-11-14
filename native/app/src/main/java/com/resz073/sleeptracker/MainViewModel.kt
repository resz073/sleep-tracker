package com.resz073.sleeptracker

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.resz073.sleeptracker.data.Account
import com.resz073.sleeptracker.data.AccountStore
import com.resz073.sleeptracker.data.Backend
import com.resz073.sleeptracker.data.Backup
import com.resz073.sleeptracker.data.Health
import com.resz073.sleeptracker.data.Session
import com.resz073.sleeptracker.data.SleepState
import com.resz073.sleeptracker.engine.Change
import com.resz073.sleeptracker.engine.Csv
import com.resz073.sleeptracker.engine.Edits
import com.resz073.sleeptracker.engine.Entries
import com.resz073.sleeptracker.engine.Goals
import com.resz073.sleeptracker.engine.Now
import com.resz073.sleeptracker.engine.ReminderKind
import com.resz073.sleeptracker.engine.SleepModel
import com.resz073.sleeptracker.engine.SyncState
import com.resz073.sleeptracker.engine.Time
import com.resz073.sleeptracker.engine.WakeUp
import com.resz073.sleeptracker.remind.Alarms
import com.resz073.sleeptracker.remind.ReminderPrefs
import com.resz073.sleeptracker.show.EntryForm
import com.resz073.sleeptracker.widget.Extras
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** A short message after a button, maybe with an action (Undo); each is shown once, repeats included */
class Toast(val text: String, val action: String? = null, val onAction: (() -> Unit)? = null)

/** Things only the screen can do: ask for a permission, open one of Android's settings pages, share a file */
sealed interface UiEvent {
    /** Ask to show notifications (Android 13 and later), then [then] with whether they're allowed */
    class AskNotifications(val then: (Boolean) -> Unit) : UiEvent
    /** Android's "Alarms & reminders" page, for on-time reminders */
    data object ExactAlarms : UiEvent
    /** This app's notification settings */
    data object NotificationSettings : UiEvent
    /** The share sheet for a CSV export; [text] is shown instead if it can't open */
    class Share(val intent: Intent, val text: String) : UiEvent
    /** Ask Health Connect for permission to save sleep */
    data object AskHealth : UiEvent
    /** Another app's screen (Health Connect, Google Play) */
    class Open(val intent: Intent) : UiEvent
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val account = Account()
    private val ui = app.getSharedPreferences("ui", Context.MODE_PRIVATE)

    val session: StateFlow<Session> =
        if (!Backend.ready) MutableStateFlow<Session>(Session.NotConfigured)
        else account.sessions().stateIn(viewModelScope, SharingStarted.Eagerly, Session.Starting)

    /** The signed-in account's data, kept on this phone and synced with the account */
    private var store: AccountStore? = null
    private val sleepState = MutableStateFlow<SleepState>(SleepState.Loading)
    val sleep: StateFlow<SleepState> = sleepState.asStateFlow()

    /** The goal has been fitted to the person's age since the account's copy came in, on [lastDay] */
    private var fitted = false
    private var lastDay: LocalDate? = null

    var toast by mutableStateOf<Toast?>(null)
        private set

    /** The add/edit entry form, while it's open */
    var form by mutableStateOf<EntryForm?>(null)
        private set

    /** "Not now" on the card asking for a birth year (kept on this phone, like the current app) */
    var goalCardDismissed by mutableStateOf(ui.getBoolean("goal-card-off", false))
        private set

    /** The open view ("today", "trends", "schedule", "history") and Trends tab, kept on this phone like the current app keeps them */
    var view by mutableStateOf(ui.getString("view", null) ?: "today")
        private set
    var trendsTab by mutableStateOf(ui.getInt("trends-tab", 0))
        private set

    /** Reminders, set on this phone (as in the current app), and what Android allows */
    var reminders by mutableStateOf(ReminderPrefs.read(app))
        private set
    var notificationsAllowed by mutableStateOf(Alarms.notificationsAllowed(app))
        private set
    var exactAllowed by mutableStateOf(Alarms.exactAllowed(app))
        private set
    private var awaitingExact = false
    private var reminderJob: Job? = null
    private var scheduledOnce = false

    /** A reminder tapped before the account's data was open: done once it is */
    private var pendingNotification: Pair<String, String?>? = null

    private val uiEvents = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = uiEvents

    /** Health Connect: whether it's on this phone, and whether saving to it is on */
    var healthStatus by mutableStateOf(Health.status(app))
        private set
    var healthOn by mutableStateOf(Health.isOn(app))
        private set

    /** Deleting the account: in progress, or why it didn't go through */
    var deleting by mutableStateOf(false)
        private set
    var deleteError by mutableStateOf<String?>(null)
        private set

    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var notice by mutableStateOf<String?>(null)
        private set

    fun clearMessages() {
        error = null
        notice = null
    }

    fun toastShown(t: Toast) {
        if (toast === t) toast = null
    }

    // After every property above: the first session can arrive straight away, on this thread
    init {
        viewModelScope.launch {
            session.collect { s ->
                open((s as? Session.SignedIn)?.uid)
                if (s is Session.SignedOut) clearReminders() // signed out, here or elsewhere: no reminders for an account that isn't here
            }
        }
    }

    private fun open(uid: String?) {
        if (store?.uid == uid) return
        store?.close()
        store = null
        form = null
        fitted = false
        lastDay = null
        sleepState.value = SleepState.Loading
        if (uid == null) return
        var created: AccountStore? = null
        created = AccountStore(getApplication(), uid) { state ->
            sleepState.value = state
            queueReminders()
            if (pendingNotification != null) viewModelScope.launch {
                val p = pendingNotification
                if (p != null && store === created) onNotification(p.first, p.second)
            }
            // Once the account's copy is in, keep the goal inside the range for the person's age, as the current app does at start
            if (!fitted && state.sync == SyncState.SYNCED) {
                fitted = true
                viewModelScope.launch {
                    if (store === created) {
                        lastDay = Now.system().today
                        fitGoal()
                    }
                }
            }
        }
        store = created
    }

    /* ---------- sleep and entries: saved on this phone, then to the account ---------- */

    private fun change(make: (SleepModel, Now) -> Change?): Boolean {
        val s = store ?: return false
        val now = Now.system()
        val c = make(SleepModel(s.doc, now), now) ?: return false
        s.edit { c.doc }
        c.message?.let { toast = Toast(it) }
        return true
    }

    private fun fitGoal() {
        change { m, now -> Goals.fit(m.prefs, now)?.let { Change(m.doc.copy(prefs = it), null) } }
    }

    fun goToSleep() {
        change { m, now -> Edits.goToSleep(m.doc, now) }
    }

    fun cancelSleep() {
        change { m, now -> Edits.cancelSleep(m.doc, now) }
    }

    /** "I'm awake": logs the sleep, or opens the form when the times need checking (over 24 hours, or overlapping) */
    fun wakeUp() {
        val s = store ?: return
        val now = Now.system()
        val woke = Edits.wakeUp(SleepModel(s.doc, now)) { Edits.newId(now) } ?: return
        s.edit { woke.doc }
        val o = woke.outcome
        if (o is WakeUp.Check) form = EntryForm.check(o.bed, o.wake)
        woke.message?.let { toast = Toast(it) }
    }

    fun rate(q: Int) {
        change { m, _ -> Edits.rate(m, q) }
    }

    /** Deletes an entry; the message offers Undo */
    fun delete(id: String) {
        val s = store ?: return
        val now = Now.system()
        val d = Edits.delete(s.doc, id, now) ?: return
        s.edit { d.doc }
        if (form?.editing == id) form = null
        val removed = d.removed
        toast = Toast(d.message, "Undo") { change { m, at -> Edits.restore(m.doc, removed, at) } }
    }

    /** Add entry: the night to log, or the last hour when everything is logged */
    fun openAdd() {
        val s = store ?: return
        form = EntryForm.add(Entries.suggest(SleepModel(s.doc, Now.system())))
    }

    /** Log a particular night (a chip, "Log last night", "Log it"), with the usual times */
    fun prefill(wakeDay: LocalDate) {
        val s = store ?: return
        form = EntryForm.add(Entries.prefill(SleepModel(s.doc, Now.system()), wakeDay))
    }

    fun startEdit(id: String) {
        val s = store ?: return
        val e = SleepModel(s.doc, Now.system()).entries.firstOrNull { it.id == id } ?: return
        form = EntryForm.edit(e)
    }

    fun editForm(f: EntryForm) {
        if (form != null) form = f
    }

    fun closeForm() {
        form = null
    }

    fun saveForm() {
        val f = form ?: return
        if (change { m, now -> Edits.save(m, f.bedDate, f.bedTime, f.wakeDate, f.wakeTime, f.q, f.editing) { Edits.newId(now) } }) form = null
    }

    fun deleteFromForm() {
        form?.editing?.let { delete(it) }
    }

    /* ---------- settings ---------- */

    fun setWeekend(v: String) {
        change { m, now -> Edits.setWeekend(m.doc, v, now) }
    }

    fun setWakeAutomatic() {
        change { m, now -> Edits.setWakeAutomatic(m.doc, now) }
    }

    fun setWakeFixed() {
        change { m, now -> Edits.setWakeFixed(m, now) }
    }

    fun setWakeTime(v: String) {
        change { m, now -> Edits.setWakeTime(m.doc, v, now) }
    }

    fun setLatency(minutes: Int) {
        change { m, now -> Edits.setLatency(m.doc, minutes, now) }
    }

    fun nudgeGoal(by: Int) {
        change { m, now -> Edits.nudgeGoal(m.doc, by, now) }
    }

    fun setBorn(year: Int) {
        change { m, now -> Edits.setBorn(m.doc, year, now) }
    }

    fun dismissGoalCard() {
        goalCardDismissed = true
        ui.edit().putBoolean("goal-card-off", true).apply()
    }

    fun openView(v: String) {
        view = v
        ui.edit().putString("view", v).apply()
    }

    fun openTrendsTab(i: Int) {
        trendsTab = i
        ui.edit().putInt("trends-tab", i).apply()
    }

    /* ---------- reminders ---------- */

    /** A reminder switch in Settings. Turning one on asks to show notifications first, as the current app does */
    fun setReminder(kind: ReminderKind, on: Boolean) {
        if (!on) {
            saveReminders(reminders.with(kind, false).copy(asked = true))
            return
        }
        askNotifications { granted ->
            if (granted) {
                saveReminders(reminders.with(kind, true).copy(asked = true))
            } else {
                toast = Toast("Notifications are blocked for Sleep Tracker. Allow them in Android Settings.", "Settings") { uiEvents.tryEmit(UiEvent.NotificationSettings) }
            }
        }
    }

    /** "Turn on" on the Get reminders card: all three, once notifications are allowed */
    fun turnOnReminders() {
        askNotifications { granted ->
            if (granted) {
                saveReminders(ReminderPrefs(wind = true, bed = true, morning = true, asked = true))
                toast = if (exactAllowed) Toast("Reminders are on. Change them in Settings.")
                else Toast("Reminders are on. Android may delay them to save battery.", "Allow on time") { askExact() }
            } else {
                saveReminders(reminders.copy(asked = true))
                toast = Toast("Notifications are blocked. You can allow them later in Android Settings.")
            }
        }
    }

    /** "Not now" on the Get reminders card */
    fun remindersLater() {
        saveReminders(reminders.copy(asked = true))
    }

    /** "Allow on-time reminders": Android's Alarms & reminders page, only when asked for. The answer is checked back in the app. */
    fun askExact() {
        awaitingExact = true
        uiEvents.tryEmit(UiEvent.ExactAlarms)
    }

    /** The Alarms & reminders page couldn't open on this phone */
    fun exactUnavailable() {
        awaitingExact = false
        toast = Toast("Reminders stay on, but Android may send them a little late.")
    }

    private fun askNotifications(then: (Boolean) -> Unit) {
        if (Alarms.notificationsAllowed(getApplication())) {
            notificationsAllowed = true
            then(true)
            return
        }
        uiEvents.tryEmit(UiEvent.AskNotifications { granted ->
            refreshPermissions()
            then(granted && notificationsAllowed)
        })
    }

    private fun saveReminders(r: ReminderPrefs) {
        reminders = r
        ReminderPrefs.write(getApplication(), r)
        queueReminders()
    }

    fun refreshPermissions() {
        notificationsAllowed = Alarms.notificationsAllowed(getApplication())
        exactAllowed = Alarms.exactAllowed(getApplication())
        healthStatus = Health.status(getApplication())
    }

    /** Plans the reminders again shortly (changes come in bursts); nothing is scheduled again if they're the same */
    private fun queueReminders() {
        reminderJob?.cancel()
        reminderJob = viewModelScope.launch {
            delay(1200)
            val s = store
            val model = s?.let { SleepModel(it.doc, Now.system()) }
            val force = !scheduledOnce
            scheduledOnce = true
            val app = getApplication<Application>()
            withContext(Dispatchers.IO) {
                Alarms.update(app, model, force)
                Extras.update(app, model, force)
            }
            if (healthOn && model != null) saveToHealth(model)
        }
    }

    private suspend fun saveToHealth(model: SleepModel, everything: Boolean = false) {
        val app = getApplication<Application>()
        try {
            withContext(Dispatchers.IO) { Health.sync(app, model, everything) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            Health.setOn(app, false)
            healthOn = false
            toast = Toast("Health Connect access was turned off, so your sleep isn't being saved there now.")
        } catch (e: Exception) {
            // Health Connect is busy or not reachable: tried again after the next change
        }
    }

    /** Signed out: no reminders, and the widget, shortcuts and tile go back to asking to sign in */
    private fun clearReminders() {
        reminderJob?.cancel()
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            Alarms.update(app, null, force = true)
            Extras.update(app, null, force = true)
        }
    }

    /** A reminder, or one of its buttons, was tapped: what the current app does, once the account's data is open */
    fun onNotification(action: String, kind: String?) {
        val s = store
        if (s == null || sleepState.value !is SleepState.Ready) {
            pendingNotification = action to kind
            return
        }
        pendingNotification = null
        openView("today")
        val m = SleepModel(s.doc, Now.system())
        when {
            action == "sleep" -> if (m.doc.pending.bed == null) goToSleep()
            action == "awake" -> if (m.doc.pending.bed != null) wakeUp()
            action == "add" -> openAdd()
            action == "log" || kind == "morning" -> if (!m.nightByWake.containsKey(m.today) && m.pending == null) prefill(m.today)
        }
    }

    /* ---------- Health Connect ---------- */

    /** The Health Connect switch in Settings: on asks Health Connect for permission first (unless it's already given) */
    fun setHealth(on: Boolean) {
        val app = getApplication<Application>()
        if (!on) {
            Health.setOn(app, false)
            healthOn = false
            toast = Toast("Saving to Health Connect is off. Sleep already saved stays there.")
            return
        }
        when (Health.status(app)) {
            Health.Status.AVAILABLE -> viewModelScope.launch {
                val allowed = try { Health.allowed(app) } catch (e: Exception) { false }
                if (allowed) healthAnswered(true) else uiEvents.tryEmit(UiEvent.AskHealth)
            }
            Health.Status.NEEDS_INSTALL -> getHealth()
            Health.Status.UNAVAILABLE -> toast = Toast("Health Connect isn't available on this phone.")
        }
    }

    /** Health Connect's answer: when allowed, every entry is saved there now, and changes from then on */
    fun healthAnswered(granted: Boolean) {
        val app = getApplication<Application>()
        if (!granted) {
            toast = Toast("Health Connect access wasn't allowed, so nothing was saved there.")
            return
        }
        Health.setOn(app, true)
        healthOn = true
        toast = Toast("Your sleep is being saved to Health Connect.")
        val s = store ?: return
        // Every entry, not only changes: Health Connect may have lost what was saved before
        viewModelScope.launch { saveToHealth(SleepModel(s.doc, Now.system()), everything = true) }
    }

    fun openHealth() {
        try {
            uiEvents.tryEmit(UiEvent.Open(Health.manageIntent(getApplication())))
        } catch (e: Exception) {
            toast = Toast("Health Connect couldn't be opened.")
        }
    }

    fun getHealth() {
        uiEvents.tryEmit(UiEvent.Open(Health.installIntent(getApplication())))
    }

    /* ---------- backup ---------- */

    /** Export CSV: every entry, shared as a file ("Save or send your sleep log"), as the current app does */
    fun exportCsv() {
        val s = store ?: return
        val m = SleepModel(s.doc, Now.system())
        if (m.entries.isEmpty()) {
            toast = Toast("Nothing to export yet")
            return
        }
        val data = Csv.export(m)
        val name = "sleep-log-" + Time.day(m.today) + ".csv"
        val app = getApplication<Application>()
        viewModelScope.launch {
            val uri = withContext(Dispatchers.IO) { Backup.write(app, name, data) }
            if (uri != null) {
                uiEvents.tryEmit(UiEvent.Share(Backup.shareIntent(uri), data))
            } else {
                toast = Toast("Couldn't save the file. Try again.")
            }
        }
    }

    /** Import CSV: adds the entries from a file exported by either app, skipping ones already logged */
    fun importCsv(uri: Uri) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) { Backup.read(app, uri) }
            if (text == null) {
                toast = Toast("Couldn't read that file")
            } else {
                change { m, now -> Edits.import(m, text) { Edits.newId(now) } }
            }
        }
    }

    /* ---------- time ---------- */

    /** Every 30 seconds and when back in the app: a new day fits the goal again, then everything is worked out for now */
    fun refresh() {
        val s = store ?: return
        val today = Now.system().today
        if (fitted && lastDay != null && today != lastDay) {
            lastDay = today
            fitGoal()
        }
        sleepState.value = s.snapshot()
        queueReminders()
    }

    /** Back in the app: reconnect if needed, send anything not yet in the account, check what Android allows, and refresh for the time */
    fun resume() {
        store?.retry()
        refreshPermissions()
        if (awaitingExact) {
            awaitingExact = false
            toast = Toast(if (exactAllowed) "Reminders will arrive on time." else "Reminders stay on, but Android may send them a little late.")
        }
        refresh()
    }

    /* ---------- account ---------- */

    fun submit(email: String, password: String, create: Boolean) {
        val e = email.trim()
        if (e.isEmpty() || password.isEmpty()) {
            error = "Enter your email and password."
            return
        }
        act { if (create) account.signUp(e, password) else account.signIn(e, password) }
    }

    fun resetPassword(email: String) {
        val e = email.trim()
        if (e.isEmpty()) {
            error = "Enter your email first, then tap Forgot password."
            return
        }
        act {
            account.resetPassword(e)
            notice = "If there's an account for $e, a reset email is on its way."
        }
    }

    fun signInWithGoogle(activity: Activity) = act { account.signInWithGoogle(activity) }

    /**
     * Signs out and deletes this phone's copy, as the current app does. Refused (false) while some changes
     * are only on this phone, so they aren't lost.
     */
    fun signOut(): Boolean {
        val s = store
        if (s != null && !s.canSignOut) return false
        s?.forget()
        store = null
        form = null
        fitted = false
        sleepState.value = SleepState.Loading
        clearReminders()
        Health.forget(getApplication())
        healthOn = false
        viewModelScope.launch { account.signOut(getApplication()) }
        return true
    }

    fun clearDeleteError() {
        deleteError = null
    }

    /**
     * Deletes the account and all its sleep data, as the current app does: confirmed with the password (or
     * with Google), then the data and the account are deleted, and this phone's copy and reminders with them.
     */
    fun deleteAccount(password: String?, activity: Activity) {
        if (deleting) return
        deleting = true
        deleteError = null
        val s = store
        val app = getApplication<Application>()
        viewModelScope.launch {
            var stopped = false
            try {
                account.reauth(password, activity)
                s?.close() // nothing more is written to the account
                stopped = true
                account.deleteAccount(app)
                s?.forget()
                try {
                    Health.removeAll(app)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Health.forget(app) // what was saved stays in Health Connect, where it can be deleted
                }
                healthOn = false
                if (store === s) {
                    store = null
                    sleepState.value = SleepState.Loading
                }
                form = null
                fitted = false
                clearReminders()
                notice = "Your account and sleep data were deleted."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                deleteError = Backend.message(e) ?: "Deletion was cancelled."
                if (stopped && store === s) s?.restart()
            } finally {
                deleting = false
            }
        }
    }

    override fun onCleared() {
        store?.close()
    }

    private fun act(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        clearMessages()
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = Backend.message(e)
            } finally {
                busy = false
            }
        }
    }
}
