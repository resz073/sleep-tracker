package com.resz073.sleeptracker.data

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.Lifecycle
import com.google.android.gms.auth.api.identity.GetSignInIntentRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.resz073.sleeptracker.BuildConfig
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * An ID token for the Google account the person picks ("Sign in with Google").
 *
 * Credential Manager is asked first. On some phones its window never opens, or its answer never comes
 * back, and the app used to wait for it forever. So the request is watched:
 * - Google's window should cover the app within a few seconds. If it hasn't, the request is dropped and
 *   Google Play services is asked directly, the way Credential Manager itself does it before Android 14.
 * - Once Google's window closes, the answer comes at once. If it hasn't come after a while, the request
 *   is dropped and the person can try again.
 */
object GoogleToken {
    /** Google's window closed, but its answer never came */
    class Unfinished : Exception("Google sign-in didn't finish.")

    private const val OPEN_MS = 10_000L // for Google's window to cover the app
    private const val ANSWER_MS = 20_000L // for the answer, once the app is back in front
    private const val LONGEST_MS = 10 * 60_000L // in all, however long the person takes
    private const val STEP_MS = 200L

    suspend fun get(activity: Activity): String {
        require(BuildConfig.WEB_CLIENT_ID.isNotEmpty()) { "Google sign-in isn't set up in this build." }
        val owner = activity as? ComponentActivity ?: return viaCredentialManager(activity)
        val inFront = { owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        return coroutineScope {
            val call = async { viaCredentialManager(activity) }
            var waited = 0L
            while (call.isActive && inFront() && waited < OPEN_MS) {
                delay(STEP_MS)
                waited += STEP_MS
            }
            if (call.isActive && inFront()) {
                call.cancel() // its window never opened
                return@coroutineScope viaPlayServices(owner)
            }
            var back = 0L
            var total = 0L
            while (call.isActive && back < ANSWER_MS && total < LONGEST_MS) {
                delay(STEP_MS)
                total += STEP_MS
                back = if (inFront()) back + STEP_MS else 0L
            }
            if (call.isActive) {
                call.cancel()
                throw Unfinished()
            }
            call.await()
        }
    }

    private suspend fun viaCredentialManager(activity: Activity): String {
        val option = GetSignInWithGoogleOption.Builder(serverClientId = BuildConfig.WEB_CLIENT_ID).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = CredentialManager.create(activity).getCredential(context = activity, request = request).credential
        if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            error("Google didn't return an account. Try again.")
        }
        return GoogleIdTokenCredential.createFrom(credential.data).idToken
    }

    /** Google Play services' own "Sign in with Google" window */
    private suspend fun viaPlayServices(activity: ComponentActivity): String {
        val client = Identity.getSignInClient(activity)
        val request = GetSignInIntentRequest.builder().setServerClientId(BuildConfig.WEB_CLIENT_ID).build()
        val window = client.getSignInIntent(request).awaitResult()
        val result = open(activity, IntentSenderRequest.Builder(window).build())
        try {
            return client.getSignInCredentialFromIntent(result.data).googleIdToken
                ?: error("Google didn't return an account. Try again.")
        } catch (e: ApiException) {
            // Closed without picking an account
            if (result.resultCode == Activity.RESULT_CANCELED && result.data == null) {
                throw GetCredentialCancellationException("activity is cancelled by the user.")
            }
            throw e
        }
    }

    private suspend fun open(activity: ComponentActivity, request: IntentSenderRequest): ActivityResult =
        suspendCancellableCoroutine { cont ->
            lateinit var launcher: ActivityResultLauncher<IntentSenderRequest>
            launcher = activity.activityResultRegistry.register("google-sign-in", ActivityResultContracts.StartIntentSenderForResult()) {
                launcher.unregister()
                if (cont.isActive) cont.resume(it)
            }
            cont.invokeOnCancellation { launcher.unregister() }
            launcher.launch(request)
        }
}
