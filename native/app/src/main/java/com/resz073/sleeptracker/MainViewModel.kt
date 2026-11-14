package com.resz073.sleeptracker

import android.app.Activity
import android.app.Application
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
import com.resz073.sleeptracker.engine.Now
import com.resz073.sleeptracker.engine.SleepModel
import com.resz073.sleeptracker.engine.WakeUp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val account = Account()

    val session: StateFlow<Session> =
        if (!Backend.ready) MutableStateFlow<Session>(Session.NotConfigured)
        else account.sessions().stateIn(viewModelScope, SharingStarted.Eagerly, Session.Starting)

    /** The signed-in account's data, kept on this phone and synced with the account */
    private var store: AccountStore? = null
    private val sleepState = MutableStateFlow<SleepState>(SleepState.Loading)
    val sleep: StateFlow<SleepState> = sleepState.asStateFlow()

    /** A short message after a button, like the current app's (shown once, then cleared) */
    var toast by mutableStateOf<String?>(null)
        private set

    fun toastShown() {
        toast = null
    }

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

    // After every property above: the first session can arrive straight away, on this thread
    init {
        viewModelScope.launch {
            session.collect { s ->
                val uid = (s as? Session.SignedIn)?.uid
                if (store?.uid != uid) {
                    store?.close()
                    store = null
                    sleepState.value = SleepState.Loading
                    if (uid != null) store = AccountStore(getApplication(), uid) { sleepState.value = it }
                }
            }
        }
    }

    /* ---------- sleep buttons: saved on this phone, then to the account ---------- */

    private fun change(make: (SleepModel, Now) -> Change?) {
        val s = store ?: return
        val now = Now.system()
        val c = make(SleepModel(s.doc, now), now) ?: return
        s.edit { c.doc }
        c.message?.let { toast = it }
    }

    fun goToSleep() = change { m, now -> Edits.goToSleep(m.doc, now) }

    fun cancelSleep() = change { m, now -> Edits.cancelSleep(m.doc, now) }

    fun wakeUp() {
        val s = store ?: return
        val now = Now.system()
        val woke = Edits.wakeUp(SleepModel(s.doc, now)) { Edits.newId(now) } ?: return
        if (woke.outcome is WakeUp.Check) {
            // The current app opens its add-entry form here; until the preview has one, keep the sleep going
            toast = "This sleep is over 24 hours or overlaps another entry. Finish it in the current app for now."
            return
        }
        s.edit { woke.doc }
        woke.message?.let { toast = it }
    }

    /** Back in the app: reconnect if needed, send anything not yet in the account, and refresh for the time */
    fun resume() {
        val s = store ?: return
        s.retry()
        sleepState.value = s.snapshot()
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
