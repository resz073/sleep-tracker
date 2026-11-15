package com.resz073.sleeptracker.engine

/** The account's copy in the cloud: Firestore in the app, a stand-in in tests. Callbacks arrive on the main thread. */
interface Remote {
    /**
     * Starts listening to the account's document. [onData] gets it whenever it changes (null when it doesn't
     * exist yet), with [fromCache] true while it's only the copy Firestore keeps on this phone (offline).
     */
    fun listen(onData: (data: Map<String, Any?>?, fromCache: Boolean) -> Unit, onError: (Exception) -> Unit): Listening

    /**
     * One transaction: reads the account's document from the server and writes what [next] returns for it
     * (nothing when it returns null). If another device writes in between, it runs again with their copy,
     * so their change is merged rather than overwritten. [done] gets what the document holds afterwards.
     * [next] may run on another thread, more than once: it must only compute.
     */
    fun update(next: (current: Map<String, Any?>?) -> Map<String, Any?>?, done: (Result<Map<String, Any?>?>) -> Unit)
}

fun interface Listening {
    fun stop()
}

/** The copy of the account's data kept on this phone (text from [SleepDoc.canon]) */
interface LocalCopy {
    fun read(): String?
    fun write(text: String)
    fun delete()
}

enum class SyncState {
    /** Everything on this phone is in the account */
    SYNCED,
    /** Saving to the account, or waiting for its copy */
    SYNCING,
    /** Some changes are only on this phone for now (offline, or the account couldn't be reached) */
    NOT_SYNCED
}

/**
 * Keeps this phone's copy of the account's data and the account's copy in step, the way version 1
 * does, so both apps can be used side by side:
 * - every change is saved on this phone first, so nothing is lost offline or if the app closes;
 * - whenever the account's copy arrives it's merged in entry by entry, the newest change of each winning;
 * - when this phone has something the account lacks, the merged copy is written back in the current
 *   app's format. Writes are transactions, so a change another device saves meanwhile is kept too.
 *
 * Not thread-safe: use it from the main thread, where [Remote] calls back.
 */
class Sync(private val remote: Remote, private val local: LocalCopy, private val clock: () -> Now) {
    /** The data as this phone has it */
    var doc: SleepDoc = SleepDoc.BLANK
        private set

    /** Called after anything changes: the data, or how far it's synced */
    var onChange: (() -> Unit)? = null

    /** The last error reaching the account, until it's reached again */
    var error: Exception? = null
        private set

    private var mine = doc.canon()
    private var account: String? = null   // the account's copy as last seen (canon), null when it doesn't exist
    private var seen = false              // the account's copy has been seen (from the server or the phone's cache)
    private var writing = false
    private var rounds = 0                // writes in a row with no new change here: a guard against copies that never agree
    private var gone = false              // the account's document was deleted (the account deleted elsewhere): don't recreate it
    private var listening: Listening? = null
    private var running = false

    /** Nothing on this phone is missing from the account */
    val synced: Boolean get() = seen && (mine == account || (account == null && doc.isEmpty))

    val state: SyncState get() = when {
        synced -> SyncState.SYNCED
        writing || (!seen && error == null) -> SyncState.SYNCING
        else -> SyncState.NOT_SYNCED
    }

    /** Signing out deletes this phone's copy, so only allow it when nothing would be lost (or the account's data was deleted) */
    val canSignOut: Boolean get() = synced || doc.isEmpty || gone

    /** Loads this phone's copy and starts following the account's */
    fun start() {
        if (running) return
        running = true
        set(SleepDoc.fromText(local.read(), clock()), save = false)
        listen()
        changed()
    }

    fun stop() {
        running = false
        listening?.stop()
        listening = null
    }

    /** Stops, and deletes this phone's copy (when signing out) */
    fun forget() {
        stop()
        local.delete()
    }

    /** When the app comes back: listen again if listening failed, and save anything not yet in the account */
    fun retry() {
        if (!running) return
        if (listening == null) listen()
        rounds = 0
        push()
        changed()
    }

    /** A change made on this phone, e.g. `sync.edit { Edits.goToSleep(it, now).doc }`: saved here at once, then to the account */
    fun edit(change: (SleepDoc) -> SleepDoc?): Boolean {
        if (!running) return false
        val next = change(doc) ?: return false
        set(next)
        rounds = 0
        push()
        changed()
        return true
    }

    private fun listen() {
        var failed = false
        val l = remote.listen(::arrived) { e ->
            error = e
            failed = true
            listening = null // Firestore stops a listener after an error; retry() starts a new one
            changed()
        }
        if (!failed) listening = l
    }

    private fun set(next: SleepDoc, save: Boolean = true) {
        val c = next.canon()
        doc = next
        if (c != mine) {
            mine = c
            if (save) local.write(c)
        }
    }

    private fun arrived(data: Map<String, Any?>?, fromCache: Boolean) {
        if (!running) return
        val server = data?.let { SleepDoc.read(it, clock()) }
        val copy = server?.canon()
        if (!seen || copy != account) rounds = 0 // something new from elsewhere, not the echo of our own write
        // The document disappearing from the server means the account was deleted (version 1's Delete
        // account): this phone mustn't upload the data again. It comes back if the document does.
        if (!fromCache) gone = seen && account != null && data == null || (gone && data == null)
        account = copy
        seen = true
        if (!fromCache) error = null
        if (server != null) set(SleepDoc.merge(doc, server))
        push()
        changed()
    }

    private fun push() {
        if (!running || !seen || synced || writing || gone) return // a change made during a write is sent when it finishes
        if (rounds >= 3) return
        writing = true
        val ours = doc
        val now = clock()
        remote.update({ current ->
            val server = current?.let { SleepDoc.read(it, now) }
            val merged = if (server == null) ours else SleepDoc.merge(ours, server)
            when {
                server == null && merged.isEmpty -> null           // don't create an account document for nothing
                server != null && merged.canon() == server.canon() -> null // the account already has it all
                else -> merged.toFirestore()
            }
        }) { result ->
            writing = false
            if (!running) return@update // stopped (or signed out, with this phone's copy deleted): leave it
            result.onSuccess { stored ->
                error = null
                rounds++ // only writes that went through count toward the guard
                val server = stored?.let { SleepDoc.read(it, clock()) }
                account = server?.canon()
                if (server != null) set(SleepDoc.merge(doc, server))
            }.onFailure { error = it as? Exception ?: Exception(it) }
            if (result.isSuccess && !synced) push() // changed meanwhile; after a failure, wait for the account to be back
            changed()
        }
        changed()
    }

    private fun changed() = onChange?.invoke()
}
