package com.mempharma.app.domain

import com.mempharma.app.data.local.entity.Medication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-logic tests for the "text my family member" rules. No Android runtime
 * required: the actual sending lives in
 * [com.mempharma.app.data.sms.SmsSender].
 */
class SmsTriggerTest {

    private val day = 24L * 60 * 60 * 1000

    /** The English wording (mirrors res/values-en), so the domain stays resource-free. */
    private val en = SmsTemplates(
        out = "MemPharma: %1\$s has run out. Please arrange a refill.",
        low = "MemPharma: %1\$s is almost finished — only %2\$d %3\$s left. " +
            "Please arrange a refill.",
        test = "MemPharma test text: refill alerts for %1\$s that are running low will arrive here.",
        testNoun = "medicines",
        possessive = "%1\$s's %2\$s",
        possessiveEndingS = "%1\$s' %2\$s"
    )

    private fun med(
        quantity: Int,
        threshold: Int = 3,
        active: Boolean = true,
        name: String = "Paracetamol",
        unit: String = "pill(s)"
    ) = Medication(
        id = 1L,
        name = name,
        quantity = quantity,
        lowStockThreshold = threshold,
        unitLabel = unit,
        active = active
    )

    // --- stage detection -----------------------------------------------------

    @Test
    fun stageFor_isNullWhenStockIsHealthy() {
        assertNull(SmsTrigger.stageFor(med(quantity = 10, threshold = 3)))
    }

    @Test
    fun stageFor_isLowAtOrBelowTheLevel() {
        assertEquals(SmsStage.LOW, SmsTrigger.stageFor(med(quantity = 3, threshold = 3)))
        assertEquals(SmsStage.LOW, SmsTrigger.stageFor(med(quantity = 1, threshold = 3)))
    }

    @Test
    fun stageFor_isOutWhenEmpty() {
        assertEquals(SmsStage.OUT, SmsTrigger.stageFor(med(quantity = 0)))
    }

    @Test
    fun stageFor_ignoresSwitchedOffMedicines() {
        assertNull(SmsTrigger.stageFor(med(quantity = 0, active = false)))
        assertNull(SmsTrigger.stageFor(med(quantity = 1, threshold = 3, active = false)))
    }

    // --- when to send --------------------------------------------------------

    @Test
    fun shouldSend_firstTimeSeeingTheStage() {
        assertTrue(SmsTrigger.shouldSend(med(quantity = 2), previous = null, nowEpochMillis = 0L))
        assertTrue(SmsTrigger.shouldSend(med(quantity = 0), previous = null, nowEpochMillis = 0L))
    }

    @Test
    fun shouldSend_isQuietWithinTheRepeatWindow() {
        val previous = SmsReminderState(1L, SmsStage.LOW, lastSentEpochMillis = 100 * day)
        assertFalse(
            SmsTrigger.shouldSend(
                med(quantity = 2),
                previous,
                nowEpochMillis = 100 * day + day
            )
        )
    }

    @Test
    fun shouldSend_repeatsAfterTheInterval() {
        val previous = SmsReminderState(1L, SmsStage.LOW, lastSentEpochMillis = 100 * day)
        assertTrue(
            SmsTrigger.shouldSend(
                med(quantity = 2),
                previous,
                nowEpochMillis = 100 * day + SmsTrigger.REPEAT_INTERVAL_MILLIS
            )
        )
    }

    @Test
    fun shouldSend_escalatesImmediatelyWhenItRunsOut() {
        val previous = SmsReminderState(1L, SmsStage.LOW, lastSentEpochMillis = 100 * day)
        // Ran out one minute after the "almost finished" text went out.
        assertTrue(
            SmsTrigger.shouldSend(
                med(quantity = 0),
                previous,
                nowEpochMillis = 100 * day + 60_000L
            )
        )
    }

    @Test
    fun shouldSend_isFalseWhenStockIsHealthy() {
        val previous = SmsReminderState(1L, SmsStage.LOW, lastSentEpochMillis = 0L)
        assertFalse(
            SmsTrigger.shouldSend(med(quantity = 10), previous, nowEpochMillis = 999 * day)
        )
    }

    // --- message ------------------------------------------------------------

    @Test
    fun buildMessage_lowNamesTheMedicineAndCount() {
        val message = SmsTrigger.buildMessage(med(quantity = 2), SmsStage.LOW, templates = en)
        assertTrue(message.contains("Paracetamol"))
        assertTrue(message.contains("2 pill(s)"))
        assertTrue(message.contains("refill", ignoreCase = true))
    }

    @Test
    fun buildMessage_outSaysItRanOut() {
        val message = SmsTrigger.buildMessage(med(quantity = 0), SmsStage.OUT, templates = en)
        assertTrue(message.contains("Paracetamol"))
        assertTrue(message.contains("run out", ignoreCase = true))
    }

    @Test
    fun buildMessage_includesPatientNamePossessively() {
        val message = SmsTrigger.buildMessage(
            med(quantity = 2),
            SmsStage.LOW,
            patientName = "John",
            templates = en
        )
        assertTrue(message.contains("John's Paracetamol"))
        assertTrue(message.contains("2 pill(s)"))
    }

    @Test
    fun buildMessage_usesApostropheOnlyWhenNameEndsInS() {
        val message = SmsTrigger.buildMessage(
            med(quantity = 2),
            SmsStage.LOW,
            patientName = "James",
            templates = en
        )
        assertTrue(message.contains("James' Paracetamol"))
    }

    @Test
    fun buildMessage_trimsAndFallsBackWhenNameIsBlank() {
        val blank = SmsTrigger.buildMessage(
            med(quantity = 2),
            SmsStage.LOW,
            patientName = "   ",
            templates = en
        )
        assertTrue(blank.contains("MemPharma: Paracetamol is almost finished"))
        assertFalse(blank.contains("'s"))
    }

    @Test
    fun buildMessage_includesPatientNameForOutToo() {
        val message = SmsTrigger.buildMessage(
            med(quantity = 0),
            SmsStage.OUT,
            patientName = "John",
            templates = en
        )
        assertTrue(message.contains("John's Paracetamol has run out"))
    }

    @Test
    fun buildMessage_usesPortugueseWordingWhenGivenPortugueseTemplates() {
        val pt = SmsTemplates(
            out = "MemPharma: %1\$s acabou. Por favor, providencie um reabastecimento.",
            low = "MemPharma: %1\$s está quase a acabar — restam apenas %2\$d %3\$s. " +
                "Por favor, providencie um reabastecimento.",
            test = "Mensagem de teste da MemPharma: os alertas de reabastecimento de %1\$s " +
                "que estiverem a acabar chegarão aqui.",
            testNoun = "medicamentos",
            possessive = "%2\$s de %1\$s",
            possessiveEndingS = "%2\$s de %1\$s"
        )
        val message = SmsTrigger.buildMessage(
            med(quantity = 2),
            SmsStage.LOW,
            patientName = "João",
            templates = pt
        )
        assertTrue(message.contains("Paracetamol de João"))
        assertTrue(message.contains("restam apenas 2 pill(s)"))
    }

    // --- test message -------------------------------------------------------

    @Test
    fun buildTestMessage_includesPatientName() {
        val message = SmsTrigger.buildTestMessage("John", en)
        assertTrue(message.contains("John's medicines"))
    }

    @Test
    fun buildTestMessage_fallsBackWhenNameIsBlank() {
        val message = SmsTrigger.buildTestMessage("  ", en)
        assertEquals(
            "MemPharma test text: refill alerts for medicines that are running low will arrive here.",
            message
        )
    }

    // --- persisted state -----------------------------------------------------

    @Test
    fun reminderState_roundTrips() {
        val state = SmsReminderState(42L, SmsStage.OUT, lastSentEpochMillis = 1_700_000_000_000L)
        assertEquals(state, SmsReminderState.decode(SmsReminderState.encode(state)))
    }

    @Test
    fun decode_ignoresGarbage() {
        assertNull(SmsReminderState.decode(""))
        assertNull(SmsReminderState.decode("1:LOW"))
        assertNull(SmsReminderState.decode("1:BANANA:1000"))
        assertNull(SmsReminderState.decode("-1:LOW:1000"))
    }

    @Test
    fun parse_dropsMalformedEntriesAndKeepsOnePerMedicine() {
        val parsed = SmsReminderState.parse(
            setOf(
                "1:LOW:100",
                "1:OUT:200", // same medicine twice -> only one entry survives
                "nonsense",
                "2:OUT:300"
            )
        )
        assertEquals(2, parsed.size)
        assertEquals(setOf(1L, 2L), parsed.map { it.medicationId }.toSet())
    }
}
