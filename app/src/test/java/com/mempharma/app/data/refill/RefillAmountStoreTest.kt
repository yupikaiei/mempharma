package com.mempharma.app.data.refill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure-logic tests for the refill-amount token encoding used by
 * [RefillAmountStore]. The DataStore itself needs an Android runtime, so only
 * the stateless encode/decode/parse helpers are exercised here.
 */
class RefillAmountStoreTest {

    @Test
    fun encodeDecode_roundTrips() {
        assertEquals(42L to 30, RefillAmountStore.decode(RefillAmountStore.encode(42L, 30)))
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
            "5:0",
            "a:b",
            "1:2 "
        )
        malformed.forEach { token ->
            assertNull("expected \"$token\" to be rejected", RefillAmountStore.decode(token))
        }
    }

    @Test
    fun parse_mapsMedicineIdsToAmounts() {
        val tokens = setOf(
            RefillAmountStore.encode(2L, 20),
            RefillAmountStore.encode(1L, 30),
            RefillAmountStore.encode(3L, 60),
            "garbage"
        )
        assertEquals(
            mapOf(1L to 30, 2L to 20, 3L to 60),
            RefillAmountStore.parse(tokens)
        )
    }

    @Test
    fun parse_emptySet_returnsEmptyMap() {
        assertEquals(emptyMap<Long, Int>(), RefillAmountStore.parse(emptySet()))
    }
}
