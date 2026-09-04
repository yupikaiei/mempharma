package com.mempharma.app.domain

import com.mempharma.app.data.local.entity.DoseAction
import com.mempharma.app.data.local.entity.DoseEvent
import com.mempharma.app.data.local.entity.Medication
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-logic tests for the dose engine. No Android runtime required.
 */
class DoseEngineTest {

    private val zone: ZoneId = ZoneId.of("UTC")

    private fun med(times: String, quantity: Int = 10, doseQty: Int = 1) = Medication(
        name = "Test",
        timesCsv = times,
        quantity = quantity,
        doseQuantity = doseQty
    )

    // --- time parsing -------------------------------------------------------

    @Test
    fun parseTimes_parsesAndDeduplicates() {
        assertEquals(
            listOf(LocalTime.of(8, 0), LocalTime.of(20, 30)),
            DoseEngine.parseTimes("08:00, 20:30, 08:00")
        )
        assertEquals(emptyList<LocalTime>(), DoseEngine.parseTimes(""))
    }

    @Test
    fun toTimesCsv_roundTrips() {
        val csv = DoseEngine.toTimesCsv(listOf(LocalTime.of(8, 0), LocalTime.of(12, 0)))
        assertEquals(listOf(LocalTime.of(8, 0), LocalTime.of(12, 0)), DoseEngine.parseTimes(csv))
    }

    // --- day occurrences -----------------------------------------------------

    @Test
    fun occurrencesForDay_returnsBothSlotsSorted() {
        val date = LocalDate.of(2026, 9, 4)
        val occ = DoseEngine.occurrencesForDay(med("20:00,08:00"), date, zone)
        assertEquals(2, occ.size)
        val expectedMorning = date.atTime(8, 0).atZone(zone).toInstant().toEpochMilli()
        val expectedEvening = date.atTime(20, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(listOf(expectedMorning, expectedEvening), occ)
    }

    // --- next occurrence scheduling ------------------------------------------

    @Test
    fun nextOccurrence_returnsTodayWhenStillAhead() {
        val before = LocalDate.of(2026, 9, 4).atTime(7, 0).atZone(zone).toInstant().toEpochMilli()
        val expected = LocalDate.of(2026, 9, 4).atTime(8, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(expected, DoseEngine.nextOccurrenceForSlot(med("08:00"), 8 * 60, before, zone))
    }

    @Test
    fun nextOccurrence_returnsTomorrowWhenAlreadyPassed() {
        val after = LocalDate.of(2026, 9, 4).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val expected = LocalDate.of(2026, 9, 5).atTime(8, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(expected, DoseEngine.nextOccurrenceForSlot(med("08:00"), 8 * 60, after, zone))
    }

    // --- today's slot status -------------------------------------------------

    @Test
    fun todaysSlots_marksTakenResolved() {
        val noon = LocalDate.of(2026, 9, 4).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val morning = LocalDate.of(2026, 9, 4).atTime(8, 0).atZone(zone).toInstant().toEpochMilli()
        val evening = LocalDate.of(2026, 9, 4).atTime(20, 0).atZone(zone).toInstant().toEpochMilli()

        val events = listOf(
            DoseEvent(
                medicationId = 1L,
                scheduledForEpochMillis = morning,
                actionAtEpochMillis = noon,
                action = DoseAction.TAKEN.name
            )
        )
        val slots = DoseEngine.todaysSlots(med("08:00,20:00"), events, noon, zone)
        assertEquals(2, slots.size)

        assertTrue(slots[0].resolved)          // morning taken
        assertEquals(DoseAction.TAKEN.name, slots[0].resolvedAction)
        assertFalse(slots[0].overdue)
        assertFalse(slots[1].resolved)         // evening still pending, not overdue yet
        assertFalse(slots[1].overdue)
        assertEquals(morning, slots[0].occurrence)
        assertEquals(evening, slots[1].occurrence)
    }

    @Test
    fun todaysSlots_marksOverdueWhenPast() {
        val lateNight = LocalDate.of(2026, 9, 4).atTime(21, 0).atZone(zone).toInstant().toEpochMilli()
        val evening = LocalDate.of(2026, 9, 4).atTime(20, 0).atZone(zone).toInstant().toEpochMilli()

        val slots = DoseEngine.todaysSlots(med("20:00"), emptyList(), lateNight, zone)
        assertEquals(1, slots.size)
        assertTrue(slots[0].overdue)
        assertFalse(slots[0].resolved)
        assertNull(slots[0].resolvedAction)
        assertEquals(evening, slots[0].occurrence)
    }

    // --- quantity math -------------------------------------------------------

    @Test
    fun quantityAfterTaking_decrementsByDose() {
        assertEquals(4, DoseEngine.quantityAfterTaking(med("08:00", quantity = 5, doseQty = 1)))
    }

    @Test
    fun quantityAfterTaking_neverGoesBelowZero() {
        assertEquals(0, DoseEngine.quantityAfterTaking(med("08:00", quantity = 2, doseQty = 5)))
    }
}
