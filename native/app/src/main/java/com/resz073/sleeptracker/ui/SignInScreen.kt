package com.resz073.sleeptracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.resz073.sleeptracker.MainViewModel
import com.resz073.sleeptracker.R

const val PRIVACY_URL = "https://resz073.github.io/sleep-tracker/privacy.html"

/** Signing in: Google, or email and password (or creating an account), with the privacy policy at the bottom */
@Composable
fun SignInScreen(vm: MainViewModel, configured: Boolean, onGoogle: () -> Unit) {
    val c = LocalAppColors.current
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var create by rememberSaveable { mutableStateOf(false) }
    var show by rememberSaveable { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    // The lamp light behind the title, in dark mode
    val lamp = if (c.dark) Modifier.drawBehind {
        val center = Offset(size.width * 0.5f, 280.dp.toPx())
        val radius = 300.dp.toPx()
        drawCircle(Brush.radialGradient(listOf(c.acc.copy(alpha = 0.075f), c.acc.copy(alpha = 0.025f), Color.Transparent), center, radius), radius, center)
    } else Modifier

    BoxWithConstraints(Modifier.fillMaxSize().then(lamp).windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
        val height = maxHeight
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).heightIn(min = height).padding(start = SIDE, end = SIDE, top = 52.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                WaveMark()
                Text("Sleep Tracker", style = serif(40f, FontWeight.Normal, 48f), color = c.ink, modifier = Modifier.padding(top = 18.dp))
                Text("Sign in to see your sleep. Your entries sync with your account.", style = font(17f), color = c.mute, modifier = Modifier.padding(top = 10.dp))
                if (!configured) {
                    SectionTitle("Accounts aren't set up in this build", Modifier.padding(top = 36.dp))
                    Muted("Add the Firebase settings to the GitHub secrets and build again. The setup guide in the repository explains how.", Modifier.padding(top = 8.dp), size = 15f)
                } else {
                    OutlinedButton(
                        onClick = { vm.clearMessages(); onGoogle() },
                        enabled = !vm.busy,
                        shape = CircleShape,
                        border = BorderStroke(1.5.dp, c.line),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = c.ink),
                        modifier = Modifier.padding(top = 36.dp).fillMaxWidth().height(56.dp),
                    ) {
                        Image(painterResource(R.drawable.ic_google), contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Continue with Google", style = font(16f, FontWeight.Bold), color = c.ink)
                    }
                    Row(Modifier.padding(top = 22.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        HorizontalDivider(Modifier.weight(1f), color = c.line)
                        Text("or use email", style = font(13f), color = c.mute, modifier = Modifier.padding(horizontal = 12.dp))
                        HorizontalDivider(Modifier.weight(1f), color = c.line)
                    }
                    LabeledField(
                        "Email", email, { email = it }, Modifier.padding(top = 14.dp),
                        keyboard = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    )
                    LabeledField(
                        "Password", password, { password = it }, Modifier.padding(top = 14.dp),
                        keyboard = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        // Done on the keyboard signs in, like the button
                        actions = KeyboardActions(onDone = { keyboard?.hide(); if (!vm.busy) vm.submit(email, password, create) }),
                        secret = !show,
                        trailing = { PillButton(if (show) "Hide" else "Show", { show = !show }, kind = Btn.TEXT, size = 15f) },
                    )
                    vm.error?.let { Text(it, style = font(15f, FontWeight.Bold), color = c.bad, modifier = Modifier.padding(top = 12.dp)) }
                    vm.notice?.let { Text(it, style = font(15f, FontWeight.Bold), color = c.acc, modifier = Modifier.padding(top = 12.dp)) }
                    Button(
                        onClick = { vm.submit(email, password, create) },
                        enabled = !vm.busy,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = c.tonal, contentColor = c.onTonal, disabledContainerColor = c.tonal, disabledContentColor = c.onTonal),
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        modifier = Modifier.padding(top = 20.dp).fillMaxWidth().height(56.dp),
                    ) {
                        if (vm.busy) CircularProgressIndicator(Modifier.size(22.dp), color = c.onTonal, strokeWidth = 2.5.dp)
                        else Text(if (create) "Create account" else "Sign in", style = font(17f, FontWeight.Bold))
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        PillButton(if (create) "I have an account" else "Create an account", { create = !create; vm.clearMessages() }, Modifier.offset(x = (-12).dp), kind = Btn.TEXT, size = 15f)
                        if (!create) PillButton("Forgot password?", { vm.resetPassword(email) }, Modifier.offset(x = 12.dp), kind = Btn.TEXT, enabled = !vm.busy, size = 15f)
                    }
                }
            }
            // Continuing means agreeing to the privacy policy, which is linked
            Text(
                buildAnnotatedString {
                    append("By continuing you agree to the ")
                    withLink(LinkAnnotation.Url(PRIVACY_URL, TextLinkStyles(SpanStyle(color = c.acc, fontWeight = FontWeight.Bold)))) {
                        append("Privacy Policy")
                    }
                    append(".")
                },
                style = font(13f), color = c.mute,
                modifier = Modifier.padding(top = 32.dp),
            )
        }
    }
}
