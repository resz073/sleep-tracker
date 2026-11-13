package com.resz073.sleeptracker

import android.app.Activity
import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.resz073.sleeptracker.data.Account
import com.resz073.sleeptracker.data.Backend
import com.resz073.sleeptracker.data.Session
import com.resz073.sleeptracker.data.SleepData
import com.resz073.sleeptracker.data.SleepState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val account = Account()

    val session: StateFlow<Session> =
        if (!Backend.ready) MutableStateFlow<Session>(Session.NotConfigured)
        else account.sessions().stateIn(viewModelScope, SharingStarted.Eagerly, Session.Starting)

    @OptIn(ExperimentalCoroutinesApi::class)
    val sleep: StateFlow<SleepState> = session
        .flatMapLatest { s -> if (s is Session.SignedIn) SleepData.listen(s.uid) else flowOf(SleepState.Loading) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SleepState.Loading)

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

    fun signOut() {
        viewModelScope.launch { account.signOut(getApplication()) }
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
