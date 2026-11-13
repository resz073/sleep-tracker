package com.resz073.sleeptracker.data

import android.content.Context
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseOptions
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.resz073.sleeptracker.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Firebase, set up from the values the build wrote into BuildConfig (see scripts/firebase-config.mjs).
 * Without them the app still runs and says accounts aren't set up.
 */
object Backend {
    val configured: Boolean
        get() = BuildConfig.FIREBASE_API_KEY.isNotEmpty() && BuildConfig.FIREBASE_APP_ID.isNotEmpty() && BuildConfig.FIREBASE_PROJECT_ID.isNotEmpty()

    var ready = false
        private set

    fun init(context: Context) {
        if (!configured) return
        if (FirebaseApp.getApps(context).isEmpty()) {
            val options = FirebaseOptions.Builder()
                .setApiKey(BuildConfig.FIREBASE_API_KEY)
                .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
            if (BuildConfig.FIREBASE_STORAGE_BUCKET.isNotEmpty()) options.setStorageBucket(BuildConfig.FIREBASE_STORAGE_BUCKET)
            if (BuildConfig.FIREBASE_SENDER_ID.isNotEmpty()) options.setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
            FirebaseApp.initializeApp(context, options.build())
        }
        ready = true
    }

    /** A sentence to show the person, or null when there's nothing to say (they cancelled). */
    fun message(e: Throwable): String? = when (e) {
        is GetCredentialCancellationException -> null
        is NoCredentialException -> "No Google account is available on this phone. Add one in Android Settings, or use email."
        is GetCredentialException -> "Google sign-in didn't work." +
            (if (!BuildConfig.FIREBASE_APP_REGISTERED) " This preview isn't registered in Firebase yet, so only email sign-in works." else " Try again, or use email.")
        is FirebaseAuthWeakPasswordException -> "Use a password with at least 6 characters."
        is FirebaseAuthUserCollisionException -> "There's already an account with that email. Sign in instead."
        is FirebaseAuthInvalidUserException ->
            if (e.errorCode == "ERROR_USER_DISABLED") "This account has been turned off." else "That email or password isn't right."
        is FirebaseAuthInvalidCredentialsException ->
            if (e.errorCode == "ERROR_INVALID_EMAIL") "That email address doesn't look right." else "That email or password isn't right."
        is FirebaseNetworkException -> "Can't reach the server. Check your connection and try again."
        is FirebaseTooManyRequestsException -> "Too many attempts. Wait a few minutes and try again."
        is FirebaseFirestoreException ->
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) "Your entries couldn't be read: the account doesn't have access."
            else "Your entries couldn't be loaded. Check your connection."
        else -> e.message?.takeIf { it.isNotBlank() } ?: "Something went wrong. Try again."
    }
}

/** Waits for a Google Play services task without needing another library. */
suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { t ->
        val e = t.exception
        when {
            e != null -> cont.resumeWithException(e)
            t.isCanceled -> cont.cancel()
            else -> cont.resume(t.result)
        }
    }
}
