package com.mempharma.app.domain

import com.mempharma.app.data.local.entity.DoseEvent
import com.mempharma.app.data.local.entity.Medication
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.max

/**
 * Pure, side-effect-free dose scheduling math. No Android dependencies — fully
 * unit testable. All time math is done in the device's local [ZoneId].
 */
object DoseEngine {

    private const val CSV_SEPARATOR = ","

    fun parseTimes(csv: String): List<LocalTime> =
        csv.split(CSV_SEPARATOR)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { runCatching { LocalTime.parse(it) }.getOrNull() }
            .distinct()

    fun toTimesCsv(times: List<LocalTime>): String =
        times.distinct().joinToString(CSV_SEPARATOR) { it.toString() }

    /** All dose occurrences for [med] on [date], sorted ascending. */
    fun occurrencesForDay(
        med: Medication,
        date: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<Long> =
        med.times.map { time ->
            LocalDateTime.of(date, time).atZone(zone).toInstant().toEpochMilli()
        }.sorted()

    /**
     * The next time a given slot fires on/after [fromEpochMillis].
     * If that slot already fired today it returns tomorrow's occurrence.
     */
    fun nextOccurrenceForSlot(
        med: Medication,
        minutesOfDay: Int,
        fromEpochMillis: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): Long {
        val time = LocalTime.of(minutesOfDay / 60, minutesOfDay % 60)
        val today = Instant.ofEpochMilli(fromEpochMillis).atZone(zone).toLocalDate()

        val candidateToday = LocalDateTime.of(today, time).atZone(zone).toInstant().toEpochMilli()
        if (candidateToday >= fromEpochMillis) return candidateToday

        val tomorrow = LocalDateTime.of(today.plusDays(1), time).atZone(zone).toInstant().toEpochMilli()
        return tomorrow
    }

    /** Per-occurrence UI snapshot for one medication on "today". */
    data class TodaysSlot(
        val occurrence: Long,
        val resolved: Boolean,
        val resolvedAction: String? = null, // TAKEN / MUTED / MISSED
        val overdue: Boolean = false
    )

    /**
     * Compute today's dose slots for [med], annotating each with whether it has a
     * logged resolution and whether it is now overdue. [events] should be today's
     * logged events for this medication only (idempotent to filtering inside).
     */
    fun todaysSlots(
        med: Medication,
        events: List<DoseEvent>,
        nowEpochMillis: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<TodaysSlot> {
        val today = Instant.ofEpochMilli(nowEpochMillis).atZone(zone).toLocalDate()
        val resolutions = events
            .filter { it.scheduledForEpochMillis != null }
            .associateBy { it.scheduledForEpochMillis!! }

        return occurrencesForDay(med, today, zone).map { occurrence ->
            val resolved = resolutions[occurrence]
            TodaysSlot(
                occurrence = occurrence,
                resolved = resolved != null,
                resolvedAction = resolved?.action,
                overdue = resolved == null && occurrence < nowEpochMillis
            )
        }
    }

    /** Quantity remaining after taking [med] once (never below zero). */
    fun quantityAfterTaking(med: Medication): Int =
        max(0, med.quantity - med.doseQuantity)
}
