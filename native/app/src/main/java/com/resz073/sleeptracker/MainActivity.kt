package com.resz073.sleeptracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.resz073.sleeptracker.data.Session
import com.resz073.sleeptracker.remind.Alarms
import com.resz073.sleeptracker.ui.AppShell
import com.resz073.sleeptracker.ui.LocalAppColors
import com.resz073.sleeptracker.ui.SignInScreen
import com.resz073.sleeptracker.ui.SleepTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Alarms.ensureChannel(this)
        if (savedInstanceState == null) reminderTapped(intent)
        setContent {
            SleepTheme {
                val session by vm.session.collectAsStateWithLifecycle()
                Box(Modifier.fillMaxSize().background(LocalAppColors.current.bg)) {
                    when (val s = session) {
                        Session.Starting -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = LocalAppColors.current.acc)
                        Session.NotConfigured, Session.SignedOut ->
                            SignInScreen(vm = vm, configured = s != Session.NotConfigured, onGoogle = { vm.signInWithGoogle(this@MainActivity) })
                        is Session.SignedIn -> AppShell(session = s, vm = vm)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        reminderTapped(intent)
    }

    /** Opened from a reminder, the widget, a shortcut or the tile: do what was tapped, and clear the reminder */
    private fun reminderTapped(intent: Intent?) {
        val action = intent?.getStringExtra(Alarms.EXTRA_ACTION) ?: return
        intent.removeExtra(Alarms.EXTRA_ACTION)
        if (intent.hasExtra(Alarms.EXTRA_ID)) NotificationManagerCompat.from(this).cancel(intent.getIntExtra(Alarms.EXTRA_ID, 0))
        vm.onNotification(action, intent.getStringExtra(Alarms.EXTRA_KIND))
    }

    // Coming back to the app: sync anything not yet in the account, and bring the time up to date
    override fun onResume() {
        super.onResume()
        vm.resume()
    }
}
