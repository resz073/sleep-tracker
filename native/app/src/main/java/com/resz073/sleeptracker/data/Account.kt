package com.resz073.sleeptracker.data

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.resz073.sleeptracker.BuildConfig
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

sealed interface Session {
    data object Starting : Session
    data object NotConfigured : Session
    data object SignedOut : Session
    data class SignedIn(val uid: String, val email: String, val google: Boolean) : Session
}

/** Signing in and out, with the same Firebase accounts as the current app. */
class Account {
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()

    fun sessions(): Flow<Session> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser.toSession()) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password).awaitResult()
    }

    suspend fun signUp(email: String, password: String) {
        auth.createUserWithEmailAndPassword(email, password).awaitResult()
    }

    suspend fun resetPassword(email: String) {
        auth.sendPasswordResetEmail(email).awaitResult()
    }

    /** Google's account picker, then a Firebase sign-in with the token it returns. */
    suspend fun signInWithGoogle(activity: Activity) {
        require(BuildConfig.WEB_CLIENT_ID.isNotEmpty()) { "Google sign-in isn't set up in this build." }
        val option = GetSignInWithGoogleOption.Builder(serverClientId = BuildConfig.WEB_CLIENT_ID).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val result = CredentialManager.create(activity).getCredential(context = activity, request = request)
        val credential = result.credential
        if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            error("Google didn't return an account. Try again.")
        }
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).awaitResult()
    }

    suspend fun signOut(context: Context) {
        auth.signOut()
        // Also forget the Google account choice, so the picker shows next time
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
    }

    private fun FirebaseUser?.toSession(): Session =
        if (this == null) Session.SignedOut
        else Session.SignedIn(uid = uid, email = email.orEmpty(), google = providerData.any { it.providerId == "google.com" })
}
