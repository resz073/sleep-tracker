package com.resz073.sleeptracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.resz073.sleeptracker.data.Session
import com.resz073.sleeptracker.ui.AppShell
import com.resz073.sleeptracker.ui.LocalAppColors
import com.resz073.sleeptracker.ui.SignInScreen
import com.resz073.sleeptracker.ui.SleepTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SleepTheme {
                val vm: MainViewModel = viewModel()
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
}
