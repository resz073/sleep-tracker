package com.resz073.sleeptracker.data

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Firebase didn't accept the account Google returned */
class GoogleRefused(cause: Throwable) : Exception(cause.message, cause)

sealed interface Session {
    data object Starting : Session
    data object NotConfigured : Session
    data object SignedOut : Session
    /** [password]: the account signs in with a password (it confirms deletion with it; Google accounts confirm with Google) */
    data class SignedIn(val uid: String, val email: String, val google: Boolean, val password: Boolean = false) : Session
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
        val token = GoogleToken.get(activity)
        try {
            auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).awaitResult()
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            throw GoogleRefused(e) // not "wrong email or password"
        }
    }

    /**
     * Firebase deletes an account only after a recent sign-in, so deletion is confirmed first, as in the current
     * app: with the password for email accounts, or with Google for Google accounts.
     */
    suspend fun reauth(password: String?, activity: Activity) {
        val u = auth.currentUser ?: return
        val usesPassword = u.providerData.any { it.providerId == "password" }
        if (usesPassword) {
            require(!password.isNullOrEmpty()) { "Enter your password." }
            u.reauthenticate(EmailAuthProvider.getCredential(u.email.orEmpty(), password)).awaitResult()
        } else u.reauthenticate(GoogleAuthProvider.getCredential(GoogleToken.get(activity), null)).awaitResult()
    }

    /** Deletes the account's sleep data (users/{uid}), then the account itself, which signs out */
    suspend fun deleteAccount(context: Context) {
        val u = auth.currentUser ?: return
        FirebaseFirestore.getInstance().collection("users").document(u.uid).delete().awaitResult()
        u.delete().awaitResult()
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
    }

    suspend fun signOut(context: Context) {
        auth.signOut()
        // Also forget the Google account choice, so the picker shows next time
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
    }

    private fun FirebaseUser?.toSession(): Session =
        if (this == null) Session.SignedOut
        else Session.SignedIn(
            uid = uid, email = email.orEmpty(), google = providerData.any { it.providerId == "google.com" },
            password = providerData.any { it.providerId == "password" },
        )
}
