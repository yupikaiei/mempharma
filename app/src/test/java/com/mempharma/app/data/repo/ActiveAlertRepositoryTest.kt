package com.mempharma.app.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure-logic tests for the alert token encoding used by [ActiveAlertRepository].
 * The DataStore itself needs an Android runtime, so only the stateless
 * encode/decode/parse helpers are exercised here.
 */
class ActiveAlertRepositoryTest {

    @Test
    fun encodeDecode_roundTrips() {
        val alert = ActiveAlert(medicationId = 42L, occurrence = 1_780_000_000_000L)
        assertEquals(alert, ActiveAlertRepository.decode(ActiveAlertRepository.encode(alert)))
    }

    @Test
    fun decode_rejectsMalformedTokens() {
        val malformed = listOf(
            "",
            "abc",
            "1:2:3",
            ":5",
            "5:",
            "-1:5",
            "5:-1",
            "a:b",
            "1:2 "
        )
        malformed.forEach { token ->
            assertNull("expected \"$token\" to be rejected", ActiveAlertRepository.decode(token))
        }
    }

    @Test
    fun parse_deduplicatesAndSortsOldestFirst() {
        val tokens = setOf(
            ActiveAlertRepository.encode(ActiveAlert(2L, 300L)),
            ActiveAlertRepository.encode(ActiveAlert(1L, 100L)),
            ActiveAlertRepository.encode(ActiveAlert(3L, 200L)),
            ActiveAlertRepository.encode(ActiveAlert(1L, 100L)), // duplicate collapses in the set
            "garbage"
        )
        assertEquals(
            listOf(
                ActiveAlert(1L, 100L),
                ActiveAlert(3L, 200L),
                ActiveAlert(2L, 300L)
            ),
            ActiveAlertRepository.parse(tokens)
        )
    }

    @Test
    fun parse_emptySet_returnsEmptyList() {
        assertEquals(emptyList<ActiveAlert>(), ActiveAlertRepository.parse(emptySet()))
    }
}
