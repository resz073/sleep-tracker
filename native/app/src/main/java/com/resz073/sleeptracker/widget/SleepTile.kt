package com.resz073.sleeptracker.widget

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.resz073.sleeptracker.MainActivity
import com.resz073.sleeptracker.R
import com.resz073.sleeptracker.data.Offline
import com.resz073.sleeptracker.engine.Planner
import com.resz073.sleeptracker.remind.Alarms
import com.resz073.sleeptracker.show.WidgetShow

/** A Quick Settings tile: Going to sleep, or I'm awake while asleep. It opens the app to do it, as a reminder's buttons do. */
class SleepTile : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        val m = try { Offline.model(applicationContext) } catch (e: Exception) { null }
        val (label, sub) = WidgetShow.tile(m, m?.let { Planner.plan(it) })
        tile.label = label
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = sub
        tile.state = if (m?.pending != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.icon = Icon.createWithResource(this, R.drawable.ic_stat_moon)
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val m = try { Offline.model(applicationContext) } catch (e: Exception) { null }
        val action = if (m == null) "tap" else if (m.pending != null) "awake" else "sleep"
        val open = {
            val i = Intent(this, MainActivity::class.java).setAction("com.resz073.sleeptracker.OPEN_" + action)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(Alarms.EXTRA_ACTION, action)
            if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 403, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            else @Suppress("DEPRECATION") startActivityAndCollapse(i)
        }
        if (isLocked) unlockAndRun { open() } else open()
    }
}
