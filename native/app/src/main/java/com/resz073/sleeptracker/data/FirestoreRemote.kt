package com.resz073.sleeptracker.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.resz073.sleeptracker.engine.Listening
import com.resz073.sleeptracker.engine.Remote

/**
 * The account's document, users/{uid}: the same one version 1 reads and writes.
 * Firestore calls back on the main thread, as [com.resz073.sleeptracker.engine.Sync] expects.
 */
class FirestoreRemote(uid: String) : Remote {
    private val db = FirebaseFirestore.getInstance()
    private val ref = db.collection("users").document(uid)

    override fun listen(onData: (Map<String, Any?>?, Boolean) -> Unit, onError: (Exception) -> Unit): Listening {
        val registration = ref.addSnapshotListener(MetadataChanges.INCLUDE) { snap, error ->
            when {
                error != null -> onError(error)
                // A copy with writes not yet confirmed isn't the account's; Sync's writes are transactions, so this is rare
                snap != null && !snap.metadata.hasPendingWrites() -> onData(if (snap.exists()) snap.data else null, snap.metadata.isFromCache)
            }
        }
        return Listening { registration.remove() }
    }

    override fun update(next: (Map<String, Any?>?) -> Map<String, Any?>?, done: (Result<Map<String, Any?>?>) -> Unit) {
        db.runTransaction<Map<String, Any?>?> { tx ->
            val snap = tx.get(ref)
            val current: Map<String, Any?>? = if (snap.exists()) snap.data else null
            val out = next(current)
            if (out != null) tx.set(ref, out)
            out ?: current
        }.addOnCompleteListener { task ->
            done(if (task.isSuccessful) Result.success(task.result) else Result.failure(task.exception ?: Exception("The account couldn't be reached.")))
        }
    }
}
