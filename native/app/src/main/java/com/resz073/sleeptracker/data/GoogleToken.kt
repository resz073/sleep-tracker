package com.resz073.sleeptracker.data

import android.app.Activity
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.identity.GetSignInIntentRequest
import com.google.android.gms.auth.api.identity.Identity
import com.resz073.sleeptracker.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * An ID token for the Google account the person picks, from Google Play services' own "Sign in with
 * Google" window, opened straight from the app.
 *
 * Not through Credential Manager (androidx.credentials): with its version 1.6.0, on some phones its
 * window never shows, or its answer never comes back, and the app waited forever; and Google refusing
 * an account came back looking like the person had closed the window, so nothing was said. Opened
 * directly, the window either shows or fails at once, and every answer says what happened.
 */
object GoogleToken {
    /** The person closed Google's window without picking an account */
    class Closed : Exception("Google's window was closed.")

    /** Google Play services didn't hand over its window */
    class NotOpened : Exception("Google's sign-in window didn't open.")

    suspend fun get(activity: Activity): String {
        require(BuildConfig.WEB_CLIENT_ID.isNotEmpty()) { "Google sign-in isn't set up in this build." }
        val owner = activity as? ComponentActivity ?: throw NotOpened()
        val client = Identity.getSignInClient(owner)
        val request = GetSignInIntentRequest.builder().setServerClientId(BuildConfig.WEB_CLIENT_ID).build()
        val window = withTimeoutOrNull(20_000) { client.getSignInIntent(request).awaitResult() } ?: throw NotOpened()
        val result = open(owner, IntentSenderRequest.Builder(window).build())
        if (result.resultCode == Activity.RESULT_CANCELED && result.data == null) throw Closed()
        // Throws ApiException with Google's status when it refused (Backend.message says which)
        return client.getSignInCredentialFromIntent(result.data).googleIdToken
            ?: error("Google didn't return an account. Try again.")
    }

    /** Forgets the account picked, so the next sign-in asks again */
    suspend fun forget(context: Context) {
        runCatching { Identity.getSignInClient(context).signOut().awaitResult() }
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
