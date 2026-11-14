package com.resz073.sleeptracker.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.text.format.DateFormat
import com.resz073.sleeptracker.engine.Time
import java.time.LocalDate

/** Android's own time and date pickers (the current app uses the browser's), in light or dark to match the phone */
object Pickers {
    private fun theme(dark: Boolean) = if (dark) android.R.style.Theme_Material_Dialog else android.R.style.Theme_Material_Light_Dialog

    /** [hhmm] is "HH:MM"; [onPick] gets the same format */
    fun time(context: Context, hhmm: String, dark: Boolean, onPick: (String) -> Unit) {
        val h = hhmm.take(2).toIntOrNull() ?: 23
        val m = hhmm.drop(3).take(2).toIntOrNull() ?: 0
        TimePickerDialog(context, theme(dark), { _, hh, mm -> onPick(Time.pad2(hh) + ":" + Time.pad2(mm)) }, h, m, DateFormat.is24HourFormat(context)).show()
    }

    /** [ymd] is "YYYY-MM-DD"; [onPick] gets the same format */
    fun date(context: Context, ymd: String, dark: Boolean, onPick: (String) -> Unit) {
        val d = Time.parseDay(ymd) ?: LocalDate.now()
        DatePickerDialog(context, theme(dark), { _, y, mo, dd -> onPick(Time.day(LocalDate.of(y, mo + 1, dd))) }, d.year, d.monthValue - 1, d.dayOfMonth).show()
    }
}
