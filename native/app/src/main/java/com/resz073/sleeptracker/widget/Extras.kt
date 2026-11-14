package com.resz073.sleeptracker.widget

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService
import com.resz073.sleeptracker.engine.Planner
import com.resz073.sleeptracker.engine.SleepModel
import com.resz073.sleeptracker.show.WidgetShow

/** The home screen widget, the app's shortcuts and the Quick Settings tile, kept in step with the data */
object Extras {
    @Volatile private var lastWidget: String? = null
    @Volatile private var lastState: String? = null

    /** Brings them up to date for [model] (null: signed out). Cheap to call often: only what changed is redrawn. */
    fun update(context: Context, model: SleepModel?, force: Boolean = false) {
        val view = WidgetShow.of(model, model?.let { Planner.plan(it) })
        if (force || view.key != lastWidget) {
            SleepWidget.draw(context, view)
            lastWidget = view.key
        }
        val state = if (model == null) "out" else if (model.pending != null) "asleep" else "awake"
        if (force || state != lastState) {
            Shortcuts.update(context, model)
            try {
                TileService.requestListeningState(context, ComponentName(context, SleepTile::class.java))
            } catch (e: Exception) {
                // the tile isn't added
            }
            lastState = state
        }
    }
}
