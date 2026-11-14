package com.resz073.sleeptracker.data

import android.content.Context
import android.util.AtomicFile
import com.resz073.sleeptracker.engine.LocalCopy
import java.io.File
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * This phone's copy of an account's data: a file in the app's private storage, replaced whole on each
 * save so a crash mid-save can't leave half a file. Saves happen in order on one background thread.
 */
class FileCopy(context: Context, uid: String) : LocalCopy {
    private val file = AtomicFile(File(context.filesDir, "sleep-data-" + uid.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".json"))

    @Volatile
    private var latest: String? = null

    /** Waits for saves still queued (from this or an earlier copy of the same account), then reads */
    override fun read(): String? = try {
        io.submit(Callable<String?> { try { file.readFully().toString(Charsets.UTF_8) } catch (e: IOException) { null } }).get()
    } catch (e: Exception) {
        null
    }

    override fun write(text: String) {
        latest = text
        io.execute {
            if (latest !== text) return@execute // a newer save is queued
            val out = try { file.startWrite() } catch (e: IOException) { return@execute }
            try {
                out.write(text.toByteArray(Charsets.UTF_8))
                file.finishWrite(out)
            } catch (e: IOException) {
                file.failWrite(out)
            }
        }
    }

    override fun delete() {
        latest = null
        io.execute { file.delete() }
    }

    companion object {
        private val io: ExecutorService = Executors.newSingleThreadExecutor { r -> Thread(r, "sleep-data").apply { isDaemon = true } }
    }
}
