package com.resz073.sleeptracker.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** CSV backup files: the export shared from the app's cache, and an import read from a file the person picks */
object Backup {
    /** Writes [text] as [name] and gives a link other apps can read (the previous export is removed) */
    fun write(context: Context, name: String, text: String): Uri? = try {
        val dir = File(context.cacheDir, "backup").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val f = File(dir, name)
        f.writeText(text, Charsets.UTF_8)
        FileProvider.getUriForFile(context, context.packageName + ".files", f)
    } catch (e: Exception) {
        null
    }

    /** Android's share sheet, as version 1 opens it: "Save or send your sleep log" */
    fun shareIntent(uri: Uri): Intent {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/csv")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "Sleep log")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri("Sleep log", uri)
        return Intent.createChooser(send, "Save or send your sleep log")
    }

    /** The text of a picked file, or null if it can't be read (or is far too big for a sleep log) */
    fun read(context: Context, uri: Uri): String? = try {
        context.contentResolver.openInputStream(uri)?.use { s ->
            val b = s.readBytes()
            if (b.size > 5_000_000) null else b.toString(Charsets.UTF_8)
        }
    } catch (e: Exception) {
        null
    }
}
