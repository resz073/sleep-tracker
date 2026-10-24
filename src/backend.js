// Accounts and cloud sync, using Firebase.
// Email/password runs in the Firebase JS SDK. Google sign-in runs natively on Android
// (@capacitor-firebase/authentication, with skipNativeAuth) and hands its ID token to the JS SDK.
// Each person's data is one Firestore document: users/{uid} = { entries, pending, prefs }.
import { initializeApp } from "firebase/app";
import {
  initializeAuth, indexedDBLocalPersistence, browserLocalPersistence, onAuthStateChanged,
  signInWithEmailAndPassword, createUserWithEmailAndPassword, sendPasswordResetEmail,
  signInWithCredential, GoogleAuthProvider, EmailAuthProvider, reauthenticateWithCredential,
  deleteUser, signOut as fbSignOut
} from "firebase/auth";
import { initializeFirestore, persistentLocalCache, doc, onSnapshot, setDoc, deleteDoc } from "firebase/firestore";
import { Capacitor } from "@capacitor/core";
import { FirebaseAuthentication } from "@capacitor-firebase/authentication";
import { firebaseConfig } from "./firebase-config.js";

export const configured = !!(firebaseConfig && firebaseConfig.apiKey && firebaseConfig.projectId);
const native = () => { try { return Capacitor.isNativePlatform(); } catch (e) { return false; } };
let auth = null, db = null;

export function init(onUser) {
  if (!configured) { onUser(null); return; }
  const app = initializeApp(firebaseConfig);
  auth = initializeAuth(app, { persistence: [indexedDBLocalPersistence, browserLocalPersistence] });
  try { db = initializeFirestore(app, { localCache: persistentLocalCache() }); }
  catch (e) { db = initializeFirestore(app, {}); }
  onAuthStateChanged(auth, u => onUser(u ? {
    uid: u.uid,
    email: u.email || "",
    name: u.displayName || "",
    providers: (u.providerData || []).map(p => p.providerId)
  } : null));
}

export function subscribe(uid, onData, onError) {
  return onSnapshot(doc(db, "users", uid), snap => onData(snap.exists() ? snap.data() : null), onError);
}
export function save(uid, data) { return setDoc(doc(db, "users", uid), data); }

async function googleToken() {
  if (!native()) { const e = new Error("Google sign-in works in the Android app."); e.code = "app/google-native-only"; throw e; }
  const r = await FirebaseAuthentication.signInWithGoogle();
  const t = r && r.credential && r.credential.idToken;
  if (!t) { const e = new Error("Google sign-in didn't return a token."); e.code = "app/google-no-token"; throw e; }
  return t;
}
export async function signInGoogle() { await signInWithCredential(auth, GoogleAuthProvider.credential(await googleToken())); }
export const signInEmail = (email, password) => signInWithEmailAndPassword(auth, email, password);
export const signUpEmail = (email, password) => createUserWithEmailAndPassword(auth, email, password);
export const resetPassword = email => sendPasswordResetEmail(auth, email);

export async function signOut() {
  if (native()) { try { await FirebaseAuthentication.signOut(); } catch (e) { /* not signed in natively */ } }
  if (auth) await fbSignOut(auth);
}

// Firebase only deletes an account after a recent sign-in, so confirm first:
// with the password for email accounts, or with Google for Google accounts.
export async function reauth(password) {
  const u = auth && auth.currentUser; if (!u) return;
  const usesPassword = (u.providerData || []).some(p => p.providerId === "password");
  const cred = usesPassword ? EmailAuthProvider.credential(u.email, password || "") : GoogleAuthProvider.credential(await googleToken());
  await reauthenticateWithCredential(u, cred);
}
export async function deleteAccount() {
  const u = auth && auth.currentUser; if (!u) return;
  await deleteDoc(doc(db, "users", u.uid));
  await deleteUser(u);
  if (native()) { try { await FirebaseAuthentication.signOut(); } catch (e) { /* ignore */ } }
}

export function message(err) {
  const c = (err && err.code) || "", m = String((err && err.message) || "").toLowerCase();
  if (m.includes("cancel")) return "";
  return ({
    "auth/invalid-email": "That email address isn't valid.",
    "auth/missing-email": "Enter your email address.",
    "auth/missing-password": "Enter your password.",
    "auth/weak-password": "Use a password with at least 6 characters.",
    "auth/email-already-in-use": "There's already an account with this email. Sign in instead.",
    "auth/invalid-credential": "Email or password is incorrect.",
    "auth/wrong-password": "Email or password is incorrect.",
    "auth/user-not-found": "There's no account with this email. Create one instead.",
    "auth/user-mismatch": "That's a different account. Use the one you're signed in with.",
    "auth/too-many-requests": "Too many attempts. Wait a few minutes and try again.",
    "auth/network-request-failed": "No internet connection. Connect and try again.",
    "auth/requires-recent-login": "Confirm it's you, then try again.",
    "app/google-native-only": "Google sign-in works in the Android app. Use email here.",
    "app/google-no-token": "Google sign-in didn't finish. Try again."
  })[c] || "That didn't work. Try again.";
}
