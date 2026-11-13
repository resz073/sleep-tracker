package com.resz073.sleeptracker.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.time.Duration
import java.time.LocalDateTime

/** One logged sleep, as the current app stores it: bed and wake as "YYYY-MM-DDTHH:MM" local times. */
data class Entry(val id: String, val bed: LocalDateTime, val wake: LocalDateTime, val quality: Int) {
    val minutes: Long get() = Duration.between(bed, wake).toMinutes()
}

sealed interface SleepState {
    data object Loading : SleepState
    data class Ready(val entries: List<Entry>, val asleepSince: LocalDateTime?, val offline: Boolean) : SleepState
    data class Failed(val message: String) : SleepState
}

/**
 * Reads the account's document (users/{uid}) written by the current app. Read only for now:
 * nothing here writes to the account.
 */
object SleepData {
    private val DT = Regex("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$""")

    fun listen(uid: String): Flow<SleepState> = callbackFlow {
        trySend(SleepState.Loading)
        val registration = FirebaseFirestore.getInstance().collection("users").document(uid)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snap, error ->
                if (error != null) {
                    trySend(SleepState.Failed(Backend.message(error) ?: "Your entries couldn't be loaded."))
                    return@addSnapshotListener
                }
                val data = snap?.data
                trySend(SleepState.Ready(entries(data), asleepSince(data), offline = snap?.metadata?.isFromCache == true))
            }
        awaitClose { registration.remove() }
    }

    /** Entries newest first. Deleted entries (kept as markers for syncing) are left out. */
    fun entries(data: Map<String, Any?>?): List<Entry> {
        val raw = data?.get("entries") as? List<*> ?: return emptyList()
        val seen = HashSet<String>()
        return raw.mapNotNull { item ->
            val m = item as? Map<*, *> ?: return@mapNotNull null
            val del = m["del"]
            if (del == true || (del as? Number)?.toInt() == 1) return@mapNotNull null
            val bedText = (m["bed"] as? String)?.takeIf { DT.matches(it) } ?: return@mapNotNull null
            val wakeText = (m["wake"] as? String)?.takeIf { DT.matches(it) } ?: return@mapNotNull null
            val bed = runCatching { LocalDateTime.parse(bedText) }.getOrNull() ?: return@mapNotNull null
            val wake = runCatching { LocalDateTime.parse(wakeText) }.getOrNull() ?: return@mapNotNull null
            if (!wake.isAfter(bed)) return@mapNotNull null
            val id = (m["id"] as? String)?.takeIf { it.isNotEmpty() } ?: "k$bedText|$wakeText"
            if (!seen.add(id)) return@mapNotNull null
            val quality = (m["q"] as? Number)?.toInt()?.takeIf { it in 1..5 } ?: 0
            Entry(id, bed, wake, quality)
        }.sortedByDescending { it.bed }
    }

    /** When the sleep in progress started, if "Going to sleep" was tapped and not yet "I'm awake". */
    fun asleepSince(data: Map<String, Any?>?): LocalDateTime? {
        val bed = ((data?.get("pending") as? Map<*, *>)?.get("bed") as? String)?.takeIf { DT.matches(it) } ?: return null
        return runCatching { LocalDateTime.parse(bed) }.getOrNull()
    }
}
