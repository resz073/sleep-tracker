package com.resz073.sleeptracker.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Sync against a stand-in for the account: it can go offline, be slow, and have version 1 (on
 * another phone) save in the middle of things. Nothing saved on either side may ever be lost.
 */
class SyncTest {
    private val zone = ZoneId.of("Asia/Dhaka")
    private var clockMs = ms("2026-11-14T21:00")
    private fun ms(local: String) = LocalDateTime.parse(local).atZone(zone).toInstant().toEpochMilli()
    private fun now() = Now(clockMs, zone)
    private fun at(local: String) { clockMs = ms(local) }

    /** The account's document on the server, Firestore's cache of it on this phone, and the network */
    class Account {
        var data: Map<String, Any?>? = null
        var cache: Map<String, Any?>? = null
        var online = true
        var writes = 0
        var listener: ((Map<String, Any?>?, Boolean) -> Unit)? = null
        var listenError: Exception? = null
        /** Runs inside the next transaction, between its read and its write: another phone saving at that moment */
        var meanwhile: (() -> Unit)? = null
        /** Changes what's stored, so the copies never agree (to test the guard against write loops) */
        var mangle: ((Map<String, Any?>) -> Map<String, Any?>)? = null
        /** Holds transaction results until [release], like a slow network */
        var slow = false
        val held = ArrayList<() -> Unit>()

        fun deliver() {
            val l = listener ?: return
            if (online) { cache = data; l(copy(data), false) } else l(copy(cache), true)
        }
        /** Version 1, on another phone, saves the whole document (as it does) */
        fun otherAppSaves(doc: Map<String, Any?>?) { data = copy(doc); if (online) deliver() }
        fun goOnline() { online = true; deliver() }
        fun goOffline() { online = false; deliver() }
        fun release() { val h = held.toList(); held.clear(); h.forEach { it() } }
    }

    class FakeRemote(private val a: Account) : Remote {
        override fun listen(onData: (Map<String, Any?>?, Boolean) -> Unit, onError: (Exception) -> Unit): Listening {
            a.listenError?.let { onError(it); return Listening {} }
            a.listener = onData
            a.deliver() // Firestore calls back straight away with the cached or the server's copy
            return Listening { a.listener = null }
        }

        override fun update(next: (Map<String, Any?>?) -> Map<String, Any?>?, done: (Result<Map<String, Any?>?>) -> Unit) {
            fun finish(r: Result<Map<String, Any?>?>) {
                val f = { done(r); if (r.isSuccess) a.deliver() }
                if (a.slow) a.held += f else f()
            }
            if (!a.online) return finish(Result.failure(IOException("offline")))
            repeat(5) {
                val read = a.data
                val out = next(copy(read))
                a.meanwhile?.let { a.meanwhile = null; it() }
                if (a.data !== read) return@repeat // another phone wrote in between: run again with its copy
                if (out != null) { a.data = copy(a.mangle?.invoke(out) ?: out); a.writes++ }
                return finish(Result.success(copy(a.data)))
            }
            finish(Result.failure(IllegalStateException("too much contention")))
        }
    }

    class Disk : LocalCopy {
        var text: String? = null
        var writes = 0
        override fun read() = text
        override fun write(text: String) { this.text = text; writes++ }
        override fun delete() { text = null }
    }

    private val account = Account()
    private val disk = Disk()
    private fun sync() = Sync(FakeRemote(account), disk, ::now)

    private fun entry(id: String, bed: String, wake: String, q: Int = 0, t: Long = 1000) = linkedMapOf<String, Any?>("id" to id, "bed" to bed, "wake" to wake, "q" to q.toLong(), "t" to t)
    private fun doc(vararg entries: Map<String, Any?>, pending: Map<String, Any?>? = null, prefs: Map<String, Any?>? = null): Map<String, Any?> = linkedMapOf(
        "entries" to entries.toList(),
        "pending" to (pending ?: linkedMapOf("bed" to null, "t" to 0L)),
        "prefs" to (prefs ?: linkedMapOf("weekend" to "fri-sat", "wake" to null, "lat" to 15L, "born" to null, "goals" to emptyList<Any?>(), "t" to 0L))
    )
    private fun ids(d: Map<String, Any?>?): List<String> = ((d?.get("entries") as? List<*>) ?: emptyList<Any?>()).map { (it as Map<*, *>)["id"] as String }.sorted()
    private fun ids(d: SleepDoc): List<String> = d.items.map { it.id }.sorted()
    private fun accountDoc() = SleepDoc.read(account.data, now())

    private val night1 = entry("n1", "2026-11-12T23:00", "2026-11-13T07:00", 4)
    private val night2 = entry("n2", "2026-11-13T23:10", "2026-11-14T06:50")

    private fun saveNight(s: Sync, bed: String, wake: String, id: String) = s.edit { d ->
        Edits.save(SleepModel(d, now()), bed.take(10), bed.drop(11), wake.take(10), wake.drop(11), 3, null) { id }?.doc
    }

    @Test
    fun firstStartTakesTheAccountsCopy() {
        account.data = doc(night1, night2)
        val s = sync().also { it.start() }
        assertEquals(listOf("n1", "n2"), ids(s.doc))
        assertEquals(0, account.writes)
        assertEquals(SyncState.SYNCED, s.state)
        assertEquals(s.doc.canon(), disk.text)
    }

    @Test
    fun aChangeIsSavedToTheAccount() {
        account.data = doc(night1)
        val s = sync().also { it.start() }
        at("2026-11-14T23:05")
        s.edit { Edits.goToSleep(it, now()).doc }
        assertEquals(1, account.writes)
        assertEquals("2026-11-14T23:05", (account.data!!["pending"] as Map<*, *>)["bed"])
        assertEquals(SyncState.SYNCED, s.state)
        assertEquals(s.doc.canon(), disk.text)
        // version 1 reading it gets exactly the same data, so it has nothing to write back
        assertEquals(s.doc.canon(), accountDoc().canon())
    }

    @Test
    fun offlineChangesStayOnThePhoneThenSync() {
        account.data = doc(night1)
        val s = sync().also { it.start() }
        account.goOffline()
        at("2026-11-15T07:00")
        saveNight(s, "2026-11-14T23:00", "2026-11-15T06:40", "new")
        assertEquals(listOf("n1"), ids(account.data))
        assertEquals(SyncState.NOT_SYNCED, s.state)
        assertFalse(s.canSignOut)
        assertTrue(disk.text!!.contains("\"new\""))
        account.goOnline()
        assertEquals(listOf("n1", "new"), ids(account.data))
        assertEquals(SyncState.SYNCED, s.state)
        assertTrue(s.canSignOut)
    }

    @Test
    fun nothingIsLostWhenBothAppsChangeWhileOffline() {
        account.data = doc(night1)
        val s = sync().also { it.start() }
        account.goOffline()
        at("2026-11-15T07:00")
        saveNight(s, "2026-11-14T23:00", "2026-11-15T06:40", "mine")
        // meanwhile version 1, on another phone, deletes n1 and logs a nap
        account.otherAppSaves(doc(linkedMapOf("id" to "n1", "del" to 1L, "t" to 2000L), entry("theirs", "2026-11-14T14:00", "2026-11-14T15:00", 0, 2000)))
        account.goOnline()
        assertEquals(listOf("mine", "n1", "theirs"), ids(account.data))
        assertTrue(accountDoc().items.single { it.id == "n1" } is Deleted)
        assertEquals(accountDoc().canon(), s.doc.canon())
        assertEquals(1, account.writes)
    }

    @Test
    fun aSaveFromAnotherPhoneDuringOurWriteIsKept() {
        account.data = doc(night1)
        val s = sync().also { it.start() }
        account.meanwhile = { account.data = doc(night1, entry("theirs", "2026-11-14T14:00", "2026-11-14T15:00", 0, 2000)) }
        at("2026-11-15T07:00")
        saveNight(s, "2026-11-14T23:00", "2026-11-15T06:40", "mine")
        assertEquals(listOf("mine", "n1", "theirs"), ids(account.data))
        assertEquals(listOf("mine", "n1", "theirs"), ids(s.doc))
        assertEquals(SyncState.SYNCED, s.state)
    }

    @Test
    fun aWriteTheAccountAlreadyHasIsSkipped() {
        account.data = doc(night1)
        val s = sync().also { it.start() }
        account.goOffline()
        at("2026-11-15T07:00")
        saveNight(s, "2026-11-14T23:00", "2026-11-15T06:40", "mine")
        // the save reached the account after all, but its answer was lost with the connection
        account.data = s.doc.toFirestore()
        account.online = true
        s.retry()
        assertEquals(0, account.writes)
        assertEquals(SyncState.SYNCED, s.state)
    }

    @Test
    fun noDocumentIsCreatedForNothing() {
        val s = sync().also { it.start() }
        assertNull(account.data)
        assertEquals(0, account.writes)
        assertEquals(SyncState.SYNCED, s.state)
        assertTrue(s.canSignOut)
    }

    @Test
    fun aNewAccountGetsThePhonesData() {
        disk.text = SleepDoc.read(doc(night1, night2), now()).canon()
        val s = sync().also { it.start() }
        assertEquals(1, account.writes)
        assertEquals(listOf("n1", "n2"), ids(account.data))
        assertEquals(SyncState.SYNCED, s.state)
    }

    @Test
    fun changesDuringASlowWriteFollowIt() {
        account.data = doc(night1)
        val s = sync().also { it.start() }
        account.slow = true
        at("2026-11-14T23:00")
        s.edit { Edits.goToSleep(it, now()).doc }
        assertEquals(SyncState.SYNCING, s.state)
        at("2026-11-14T23:02")
        s.edit { Edits.cancelSleep(it, now()).doc }
        assertEquals(SyncState.SYNCING, s.state)
        account.release()
        account.release()
        assertEquals(2, account.writes)
        assertNull((account.data!!["pending"] as Map<*, *>)["bed"])
        assertEquals(SyncState.SYNCED, s.state)
    }

    @Test
    fun unsyncedChangesSurviveARestart() {
        account.data = doc(night1)
        val first = sync().also { it.start() }
        account.goOffline()
        at("2026-11-15T07:00")
        saveNight(first, "2026-11-14T23:00", "2026-11-15T06:40", "mine")
        first.stop()
        val again = sync().also { it.start() }
        assertEquals(listOf("mine", "n1"), ids(again.doc))
        assertEquals(SyncState.NOT_SYNCED, again.state)
        account.goOnline()
        assertEquals(listOf("mine", "n1"), ids(account.data))
        assertEquals(SyncState.SYNCED, again.state)
    }

    @Test
    fun aDamagedCopyOnThePhoneIsReplaced() {
        disk.text = "{\"entries\":[{\"id\":"
        account.data = doc(night1)
        val s = sync().also { it.start() }
        assertEquals(listOf("n1"), ids(s.doc))
        assertEquals(s.doc.canon(), disk.text)
        assertEquals(0, account.writes)
    }

    @Test
    fun listeningAgainAfterAnError() {
        account.data = doc(night1)
        account.listenError = Exception("PERMISSION_DENIED")
        val s = sync().also { it.start() }
        assertEquals(SyncState.NOT_SYNCED, s.state)
        assertNotNull(s.error)
        account.listenError = null
        s.retry()
        assertEquals(listOf("n1"), ids(s.doc))
        assertEquals(SyncState.SYNCED, s.state)
        assertNull(s.error)
    }

    @Test
    fun oldFormatsAreOnlyRewrittenWithAChange() {
        account.data = linkedMapOf("entries" to listOf(linkedMapOf("d" to "2026-11-13", "bed" to "23:30", "wake" to "06:30", "q" to "4"), night1))
        val s = sync().also { it.start() }
        assertEquals(0, account.writes)
        assertEquals(SyncState.SYNCED, s.state)
        at("2026-11-14T23:00")
        s.edit { Edits.goToSleep(it, now()).doc }
        assertEquals(1, account.writes)
        assertEquals(s.doc.canon(), Json.stringify(account.data))
    }

    @Test
    fun aDeletionReachesTheAccountAndCanBeUndoneElsewhere() {
        account.data = doc(night1, night2)
        val s = sync().also { it.start() }
        at("2026-11-14T21:05")
        s.edit { Edits.delete(it, "n2", now())?.doc }
        assertTrue(accountDoc().items.single { it.id == "n2" } is Deleted)
        assertEquals(listOf("n1"), SleepModel(s.doc, now()).entries.map { it.id })
        // version 1 restores it later
        account.otherAppSaves(doc(night1, entry("n2", "2026-11-13T23:10", "2026-11-14T06:50", 0, ms("2026-11-14T21:06"))))
        assertEquals(listOf("n1", "n2"), SleepModel(s.doc, now()).entries.map { it.id })
        assertEquals(1, account.writes)
    }

    @Test
    fun copiesThatNeverAgreeStopAfterAFewWrites() {
        account.data = doc(night1)
        account.mangle = { m -> LinkedHashMap(m).apply { put("entries", (m["entries"] as List<*>).dropLast(1)) } }
        val s = sync().also { it.start() }
        at("2026-11-15T07:00")
        saveNight(s, "2026-11-14T23:00", "2026-11-15T06:40", "zz")
        assertTrue("writes: ${account.writes}", account.writes in 1..3)
        assertFalse(s.synced)
    }

    @Test
    fun signingOutDeletesThePhonesCopy() {
        account.data = doc(night1)
        val s = sync().also { it.start() }
        assertNotNull(disk.text)
        s.forget()
        assertNull(disk.text)
        assertNull(account.listener)
    }

    @Test
    fun aWriteFinishingAfterSignOutLeavesNothingOnThePhone() {
        account.data = doc(night1)
        val s = sync().also { it.start() }
        account.slow = true
        at("2026-11-14T23:00")
        s.edit { Edits.goToSleep(it, now()).doc }
        s.forget()
        account.release()
        assertNull(disk.text)
        assertFalse(s.edit { Edits.cancelSleep(it, now()).doc })
        assertNull(disk.text)
    }

    @Test
    fun failedAttemptsDontStopLaterOnes() {
        account.data = doc(night1)
        val s = sync().also { it.start() }
        account.online = false
        at("2026-11-14T23:00")
        s.edit { Edits.goToSleep(it, now()).doc }
        repeat(4) { account.deliver() } // the cached copy again and again while offline: each try fails
        assertEquals(SyncState.NOT_SYNCED, s.state)
        account.goOnline()
        assertEquals(1, account.writes)
        assertEquals(SyncState.SYNCED, s.state)
    }

    @Test
    fun anAccountDeletedElsewhereIsNotUploadedAgain() {
        account.data = doc(night1)
        val s = sync().also { it.start() }
        at("2026-11-14T23:00")
        s.edit { Edits.goToSleep(it, now()).doc }
        assertEquals(1, account.writes)
        account.otherAppSaves(null) // Delete account in version 1 removes the document
        assertNull(account.data)
        assertEquals(1, account.writes)
        assertTrue(s.canSignOut)
        s.retry()
        at("2026-11-14T23:01")
        s.edit { Edits.cancelSleep(it, now()).doc }
        assertNull(account.data)
        // if the document comes back (the deletion didn't finish), syncing carries on
        account.otherAppSaves(doc(night1, night2))
        assertEquals(listOf("n1", "n2"), ids(account.data))
        assertEquals(2, account.writes)
        assertEquals(SyncState.SYNCED, s.state)
    }

    @Test
    fun firstStartOfflineWithNothingCached() {
        account.data = doc(night1)
        account.online = false
        val s = sync().also { it.start() }
        assertTrue(s.canSignOut)
        at("2026-11-14T23:00")
        s.edit { Edits.goToSleep(it, now()).doc }
        assertEquals(SyncState.NOT_SYNCED, s.state)
        account.goOnline()
        // the account's entries and the sleep started offline, together
        assertEquals(listOf("n1"), ids(account.data))
        assertEquals("2026-11-14T23:00", (account.data!!["pending"] as Map<*, *>)["bed"])
        assertEquals(listOf("n1"), ids(s.doc))
        assertEquals(SyncState.SYNCED, s.state)
    }

    @Test
    fun theAccountsCopyArrivingBeforeOurWriteFinishes() {
        account.data = doc(night1)
        val s = sync().also { it.start() }
        account.slow = true
        at("2026-11-14T23:00")
        s.edit { Edits.goToSleep(it, now()).doc }
        account.deliver() // the server's copy (with our write) arrives before the write's own result
        assertEquals(SyncState.SYNCED, s.state)
        account.release()
        assertEquals(1, account.writes)
        assertEquals(SyncState.SYNCED, s.state)
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun copy(m: Map<String, Any?>?): Map<String, Any?>? = m?.let { Json.parse(Json.stringify(it)) as Map<String, Any?> }
    }
}
