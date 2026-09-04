package com.mempharma.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Small formatting helpers so every screen renders dates/times the same friendly
 * way (important for legibility to elderly users).
 */
object TimeFormat {

    private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
    private val dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())
    private val fullDateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault())

    fun formatTime(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).format(timeFormatter)

    fun formatDay(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val date = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
        val today = LocalDate.now(zone)
        return when (date) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> date.format(dateFormatter)
        }
    }

    fun formatFullDate(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).format(fullDateFormatter)

    /** Friendly relative label used for the "started on" trace line. */
    fun startedOn(epochDay: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val date = LocalDate.ofEpochDay(epochDay)
        val today = LocalDate.now(zone)
        val days = today.toEpochDay() - epochDay
        return if (days < 1) {
            "Started today"
        } else if (days == 1L) {
            "Started yesterday"
        } else {
            "Started ${date.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()))}"
        }
    }
}
