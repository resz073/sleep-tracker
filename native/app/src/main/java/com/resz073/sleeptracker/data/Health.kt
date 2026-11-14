package com.resz073.sleeptracker.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.metadata.Metadata
import com.resz073.sleeptracker.engine.Entry
import com.resz073.sleeptracker.engine.HealthPlan
import com.resz073.sleeptracker.engine.History
import com.resz073.sleeptracker.engine.SleepModel
import com.resz073.sleeptracker.engine.Time
import java.time.Instant
import java.time.ZoneId

/**
 * Health Connect, when turned on in Settings: each entry is saved there as a sleep session (a nap is titled
 * "Nap"), updated when it changes and removed when it's deleted, so other health apps the person allows can
 * see it. The app only writes; it reads nothing from Health Connect. Health Connect needs Android 9 or later.
 */
object Health {
    private const val PROVIDER = "com.google.android.apps.healthdata"

    enum class Status { UNAVAILABLE, NEEDS_INSTALL, AVAILABLE }

    fun status(context: Context): Status {
        if (Build.VERSION.SDK_INT < 28) return Status.UNAVAILABLE
        return try {
            when (HealthConnectClient.getSdkStatus(context)) {
                HealthConnectClient.SDK_AVAILABLE -> Status.AVAILABLE
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Status.NEEDS_INSTALL
                else -> Status.UNAVAILABLE
            }
        } catch (e: Exception) {
            Status.UNAVAILABLE
        }
    }

    /** The only permission asked for: writing sleep sessions */
    fun permissions(): Set<String> = setOf(HealthPermission.getWritePermission(SleepSessionRecord::class))

    suspend fun allowed(context: Context): Boolean =
        status(context) == Status.AVAILABLE && HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions().containsAll(permissions())

    private fun prefs(context: Context) = context.getSharedPreferences("health", Context.MODE_PRIVATE)

    fun isOn(context: Context): Boolean = prefs(context).getBoolean("on", false)

    fun setOn(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("on", on).apply()
    }

    /** Signed out: off, and what was saved is forgotten (it stays in Health Connect, where it can be deleted) */
    fun forget(context: Context) {
        prefs(context).edit().clear().apply()
    }

    /** Saves new and changed entries and removes deleted ones; returns how many were saved. Throws SecurityException if access was taken away. */
    suspend fun sync(context: Context, model: SleepModel): Int {
        val p = prefs(context)
        val plan = HealthPlan.of(model.entries, HealthPlan.read(p.getStringSet("saved", null) ?: emptySet()))
        if (plan.save.isEmpty() && plan.remove.isEmpty()) return 0
        val client = HealthConnectClient.getOrCreate(context)
        val saved = HashMap(plan.saved)
        for (chunk in plan.save.chunked(100)) {
            try {
                client.insertRecords(chunk.map { record(it, model.zone) })
            } catch (e: SecurityException) {
                throw e
            } catch (e: Exception) {
                // One record Health Connect won't take fails them all: save them one at a time, and try the rest again later
                for (one in chunk) {
                    try {
                        client.insertRecords(listOf(record(one, model.zone)))
                    } catch (x: SecurityException) {
                        throw x
                    } catch (x: Exception) {
                        saved.remove(one.id)
                    }
                }
            }
        }
        for (chunk in plan.remove.chunked(100)) client.deleteRecords(SleepSessionRecord::class, emptyList(), chunk)
        p.edit().putStringSet("saved", HealthPlan.write(saved)).apply()
        return plan.save.size
    }

    /** Removes everything the app saved there (when the account is deleted) */
    suspend fun removeAll(context: Context) {
        val ids = HealthPlan.read(prefs(context).getStringSet("saved", null) ?: emptySet()).keys.toList()
        if (ids.isNotEmpty() && status(context) == Status.AVAILABLE) {
            val client = HealthConnectClient.getOrCreate(context)
            for (chunk in ids.chunked(100)) client.deleteRecords(SleepSessionRecord::class, emptyList(), chunk)
        }
        forget(context)
    }

    private fun record(e: Entry, zone: ZoneId): SleepSessionRecord {
        val start = Instant.ofEpochMilli(Time.ms(e.bed, zone))
        val end = Instant.ofEpochMilli(Time.ms(e.wake, zone))
        return SleepSessionRecord(
            startTime = start,
            startZoneOffset = zone.rules.getOffset(start),
            endTime = end,
            endZoneOffset = zone.rules.getOffset(end),
            metadata = Metadata.manualEntry(clientRecordId = e.id, clientRecordVersion = e.t.toLong()),
            title = if (History.isNap(e)) "Nap" else null,
        )
    }

    /** Health Connect's own screen for this app's data */
    fun manageIntent(context: Context): Intent = HealthConnectClient.getHealthConnectManageDataIntent(context)

    /** Health Connect on Google Play (Android 13 and lower, where it's an app) */
    fun installIntent(context: Context): Intent = Intent(Intent.ACTION_VIEW)
        .setPackage("com.android.vending")
        .setData(Uri.parse("market://details?id=$PROVIDER&url=healthconnect%3A%2F%2Fonboarding"))
        .putExtra("overlay", true)
        .putExtra("callerId", context.packageName)
}
