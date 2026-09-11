package com.mempharma.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Small formatting helpers so every screen renders dates/times the same friendly
 * way (important for legibility to elderly users).
 *
 * Every function takes the [Locale] of the language that is currently displayed
 * (see [AppLocale] and `rememberAppLocale`). This matters because the app's
 * language can differ from the device language (e.g. a French phone falls back
 * to the Portuguese default), so [Locale.getDefault] would format dates in the
 * wrong language.
 */
object TimeFormat {

    private fun timeFormatter(locale: Locale): DateTimeFormatter =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)

    private fun dayFormatter(locale: Locale): DateTimeFormatter =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)

    private fun dateFormatter(locale: Locale): DateTimeFormatter =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)

    fun formatTime(
        epochMillis: Long,
        locale: Locale,
        zone: ZoneId = ZoneId.systemDefault()
    ): String = Instant.ofEpochMilli(epochMillis).atZone(zone).format(timeFormatter(locale))

    fun formatLocalTime(time: LocalTime, locale: Locale): String =
        time.format(timeFormatter(locale))

    /** Weekday + date, e.g. "sexta-feira, 11 de setembro de 2026". */
    fun formatDay(
        epochMillis: Long,
        locale: Locale,
        zone: ZoneId = ZoneId.systemDefault()
    ): String = Instant.ofEpochMilli(epochMillis).atZone(zone).format(dayFormatter(locale))

    /** Weekday + date from an epoch day, used for History day headers. */
    fun formatWeekdayDate(epochDay: Long, locale: Locale): String =
        LocalDate.ofEpochDay(epochDay).format(dayFormatter(locale))

    /** Date only, for "<started> <date>" traces. */
    fun formatDate(epochDay: Long, locale: Locale): String =
        LocalDate.ofEpochDay(epochDay).format(dateFormatter(locale))
}

