package com.resz073.sleeptracker.ui

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// The app's text is English, so dates use English names whatever the phone's language.
private val DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
private val DAY_LONG = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH)
private val MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
private val TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

fun dayShort(d: LocalDate): String = d.format(DAY)
fun dayLong(d: LocalDate): String = d.format(DAY_LONG)
fun monthTitle(d: LocalDate): String = d.format(MONTH)
fun clock(t: LocalDateTime): String = t.format(TIME)

/** 440 -> "7h 20m" */
fun hm(minutes: Long): String = "${minutes / 60}h ${(minutes % 60).toString().padStart(2, '0')}m"
