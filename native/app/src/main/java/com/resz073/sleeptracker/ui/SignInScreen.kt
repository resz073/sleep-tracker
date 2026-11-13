package com.resz073.sleeptracker.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.resz073.sleeptracker.MainViewModel
import com.resz073.sleeptracker.R

const val PRIVACY_URL = "https://resz073.github.io/sleep-tracker/privacy.html"

@Composable
fun SignInScreen(vm: MainViewModel, configured: Boolean, onGoogle: () -> Unit) {
    val c = LocalAppColors.current
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var create by rememberSaveable { mutableStateOf(false) }
    var show by rememberSaveable { mutableStateOf(false) }
    val uri = LocalUriHandler.current

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // The night-sky header, as in the current app
            Surface(shape = RoundedCornerShape(28.dp), color = c.sky, contentColor = c.skyInk, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 26.dp)) {
                    Icon(painterResource(R.drawable.ic_moon), contentDescription = null, tint = c.skyAcc, modifier = Modifier.size(44.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Sleep Tracker", style = MaterialTheme.typography.headlineMedium, color = c.skyInk)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Sign in to see your sleep. Your entries sync with your account.",
                        style = MaterialTheme.typography.bodyLarge, color = c.skyMute,
                    )
                }
            }

            AppCard {
                if (!configured) {
                    CardTitle("Accounts aren't set up in this build")
                    Spacer(Modifier.height(6.dp))
                    Muted("Add the Firebase settings to the GitHub secrets and build again. The setup guide in the repository explains how.")
                } else {
                    OutlinedButton(
                        onClick = { vm.clearMessages(); onGoogle() },
                        enabled = !vm.busy,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Image(painterResource(R.drawable.ic_google), contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Continue with Google", style = MaterialTheme.typography.labelLarge, color = c.ink)
                    }

                    Row(Modifier.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        HorizontalDivider(Modifier.weight(1f), color = c.line)
                        Text("  or use email  ", style = MaterialTheme.typography.bodySmall, color = c.mute)
                        HorizontalDivider(Modifier.weight(1f), color = c.line)
                    }

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        trailingIcon = {
                            TextButton(onClick = { show = !show }) { Text(if (show) "Hide" else "Show") }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    vm.error?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(it, color = c.bad, style = MaterialTheme.typography.bodyMedium)
                    }
                    vm.notice?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(it, color = c.good, style = MaterialTheme.typography.bodyMedium)
                    }

                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = { vm.submit(email, password, create) },
                        enabled = !vm.busy,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = c.acc, contentColor = c.onAcc),
                    ) {
                        if (vm.busy) CircularProgressIndicator(Modifier.size(22.dp), color = c.onAcc, strokeWidth = 2.5.dp)
                        else Text(if (create) "Create account" else "Sign in", style = MaterialTheme.typography.labelLarge)
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { create = !create; vm.clearMessages() }) {
                            Text(if (create) "I have an account" else "Create an account")
                        }
                        if (!create) TextButton(onClick = { vm.resetPassword(email) }, enabled = !vm.busy) { Text("Forgot password?") }
                    }
                }
            }

            Muted(
                "This is a preview of the new Android app. It shows your entries but doesn't change anything in your account yet.",
                Modifier.padding(horizontal = 6.dp),
            )
            TextButton(onClick = { uri.openUri(PRIVACY_URL) }) { Text("Privacy policy") }
        }
    }
}
