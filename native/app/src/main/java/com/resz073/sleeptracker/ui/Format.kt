package com.resz073.sleeptracker.ui

import com.resz073.sleeptracker.engine.Time
import com.resz073.sleeptracker.engine.Words
import java.time.LocalDate
import java.time.LocalDateTime

// The same English wording as the current app, whatever the phone's language (see engine/Words.kt).
fun dayShort(d: LocalDate): String = Words.day(d)
fun dayLong(d: LocalDate): String = Words.dayLong(d)
fun monthTitle(d: LocalDate): String = Words.month(d)
fun clock(t: LocalDateTime): String = Words.clock(Time.tod(t))

/** 440 -> "7h 20m" */
fun hm(minutes: Int): String = Words.hm(minutes)
