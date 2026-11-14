package com.resz073.sleeptracker.data

import android.content.Context
import com.resz073.sleeptracker.engine.Entry
import com.resz073.sleeptracker.engine.Now
import com.resz073.sleeptracker.engine.SleepDoc
import com.resz073.sleeptracker.engine.SleepModel
import com.resz073.sleeptracker.engine.Sync
import com.resz073.sleeptracker.engine.SyncState
import java.time.LocalDateTime
import java.time.ZoneId

sealed interface SleepState {
    data object Loading : SleepState

    /**
     * The account's data as this phone has it, worked out by the sleep engine for the current moment.
     * [sync] says whether it's all in the account; [canSignOut] is false while changes are only on this phone.
     */
    class Ready(val model: SleepModel, val sync: SyncState, val canSignOut: Boolean) : SleepState {
        /** The sleeps that count, newest first */
        val entries: List<Entry> get() = model.entries.asReversed()
        /** Set while a sleep is in progress */
        val asleepSince: LocalDateTime? get() = model.pending
        val zone: ZoneId get() = model.zone
    }
}

/**
 * One signed-in account's data: kept on this phone and in step with the account (users/{uid}) by [Sync],
 * so it opens without internet and changes made offline reach the account later.
 */
class AccountStore(context: Context, val uid: String, private val onUpdate: (SleepState.Ready) -> Unit) {
    private val sync = Sync(FirestoreRemote(uid), FileCopy(context.applicationContext, uid)) { Now.system() }

    init {
        sync.onChange = { onUpdate(snapshot()) }
        sync.start()
    }

    val doc: SleepDoc get() = sync.doc
    val canSignOut: Boolean get() = sync.canSignOut

    /** The data worked out for this moment (call again as time passes) */
    fun snapshot(): SleepState.Ready = SleepState.Ready(SleepModel(sync.doc, Now.system()), sync.state, sync.canSignOut)

    /** Saves a change on this phone, then to the account */
    fun edit(change: (SleepDoc) -> SleepDoc?): Boolean = sync.edit(change)

    /** Back in the app: reconnect if needed and send anything not yet in the account */
    fun retry() = sync.retry()

    fun close() = sync.stop()

    /** Signing out: stops and deletes this phone's copy */
    fun forget() = sync.forget()
}
