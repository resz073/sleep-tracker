package com.resz073.sleeptracker.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.resz073.sleeptracker.engine.Entry
import com.resz073.sleeptracker.engine.Now
import com.resz073.sleeptracker.engine.SleepDoc
import com.resz073.sleeptracker.engine.SleepModel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.time.LocalDateTime
import java.time.ZoneId

sealed interface SleepState {
    data object Loading : SleepState

    /**
     * The account's data, read by the sleep engine exactly as the current app reads it.
     * [entries] are the sleeps that count, newest first; [asleepSince] is set while a sleep is in progress.
     */
    data class Ready(
        val doc: SleepDoc,
        val entries: List<Entry>,
        val asleepSince: LocalDateTime?,
        val offline: Boolean,
        val zone: ZoneId,
    ) : SleepState

    data class Failed(val message: String) : SleepState
}

/**
 * Reads the account's document (users/{uid}) written by the current app. Read only for now:
 * nothing here writes to the account.
 */
object SleepData {
    fun listen(uid: String): Flow<SleepState> = callbackFlow {
        trySend(SleepState.Loading)
        val registration = FirebaseFirestore.getInstance().collection("users").document(uid)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snap, error ->
                if (error != null) {
                    trySend(SleepState.Failed(Backend.message(error) ?: "Your entries couldn't be loaded."))
                    return@addSnapshotListener
                }
                trySend(ready(snap?.data, offline = snap?.metadata?.isFromCache == true))
            }
        awaitClose { registration.remove() }
    }

    fun ready(data: Map<String, Any?>?, offline: Boolean, now: Now = Now.system()): SleepState.Ready {
        val doc = SleepDoc.read(data, now)
        val model = SleepModel(doc, now)
        // asReversed, not reversed(): on Android before 15 a Java reversed() would crash
        return SleepState.Ready(doc, model.entries.asReversed().toList(), model.pending, offline, now.zone)
    }
}
