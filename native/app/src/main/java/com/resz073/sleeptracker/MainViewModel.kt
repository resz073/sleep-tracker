package com.resz073.sleeptracker

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.resz073.sleeptracker.data.Account
import com.resz073.sleeptracker.data.AccountStore
import com.resz073.sleeptracker.data.Backend
import com.resz073.sleeptracker.data.Session
import com.resz073.sleeptracker.data.SleepState
import com.resz073.sleeptracker.engine.Change
import com.resz073.sleeptracker.engine.Edits
import com.resz073.sleeptracker.engine.Entries
import com.resz073.sleeptracker.engine.Goals
import com.resz073.sleeptracker.engine.Now
import com.resz073.sleeptracker.engine.SleepModel
import com.resz073.sleeptracker.engine.SyncState
import com.resz073.sleeptracker.engine.WakeUp
import com.resz073.sleeptracker.show.EntryForm
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** A short message after a button, maybe with an action (Undo); each is shown once, repeats included */
class Toast(val text: String, val action: String? = null, val onAction: (() -> Unit)? = null)

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
        viewModelScope.launch { session.collect { s -> open((s as? Session.SignedIn)?.uid) } }
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
    }

    /** Back in the app: reconnect if needed, send anything not yet in the account, and refresh for the time */
    fun resume() {
        store?.retry()
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
        viewModelScope.launch { account.signOut(getApplication()) }
        return true
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
