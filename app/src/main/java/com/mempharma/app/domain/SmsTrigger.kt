package com.mempharma.app.domain

import com.mempharma.app.data.local.entity.Medication
import java.util.concurrent.TimeUnit

/**
 * How "empty" a medicine is, for the optional "text my family member" feature:
 *  - [LOW]  -> at or below the person's own refill-warning level
 *  - [OUT]  -> nothing left at all
 */
enum class SmsStage { LOW, OUT }

/**
 * What we last told the chosen contact about a medicine: which [stage] it was
 * in and when the text went out. Persisted as a small token so we can avoid
 * repeating the same message.
 */
data class SmsReminderState(
    val medicationId: Long,
    val stage: SmsStage,
    val lastSentEpochMillis: Long
) {
    companion object {
        private const val SEPARATOR = ":"

        /** Encode as a stable token for DataStore's string set. */
        fun encode(state: SmsReminderState): String =
            "${state.medicationId}$SEPARATOR${state.stage.name}$SEPARATOR${state.lastSentEpochMillis}"

        /** Decode a token, ignoring anything malformed. */
        fun decode(token: String): SmsReminderState? {
            val parts = token.split(SEPARATOR)
            if (parts.size != 3) return null
            val medId = parts[0].toLongOrNull() ?: return null
            val stage = runCatching { SmsStage.valueOf(parts[1]) }.getOrNull() ?: return null
            val lastSent = parts[2].toLongOrNull() ?: return null
            if (medId < 0 || lastSent < 0) return null
            return SmsReminderState(medId, stage, lastSent)
        }

        /** Parse, dropping anything malformed. One entry is kept per medicine. */
        fun parse(tokens: Set<String>): List<SmsReminderState> =
            tokens.asSequence()
                .mapNotNull(::decode)
                .distinctBy { it.medicationId }
                .toList()
    }
}

/**
 * Pure, side-effect-free rules for the "text my family member" feature. No
 * Android dependencies, so the whole decision is unit-testable.
 */
object SmsTrigger {

    /** Repeat the message every 3 days while the medicine stays low / empty. */
    val REPEAT_INTERVAL_MILLIS: Long = TimeUnit.DAYS.toMillis(3)

    /**
     * The stage [med] is currently in, or null when there is nothing to report:
     *  - switched-off medicines are ignored,
     *  - nothing left is [SmsStage.OUT],
     *  - at or below the medicine's own level is [SmsStage.LOW].
     */
    fun stageFor(med: Medication): SmsStage? = when {
        !med.active -> null
        med.quantity <= 0 -> SmsStage.OUT
        med.quantity <= med.lowStockThreshold -> SmsStage.LOW
        else -> null
    }

    /**
     * Should we text the contact about [med] now?
     *  - the first time we notice a stage      -> yes
     *  - the stage got worse (LOW -> OUT)      -> yes, straight away
     *  - otherwise                             -> only every [repeatIntervalMillis]
     *
     * [repeatIntervalMillis] defaults to the 3-day cadence and is injectable so
     * the rule can be tested without waiting three days.
     */
    fun shouldSend(
        med: Medication,
        previous: SmsReminderState?,
        nowEpochMillis: Long,
        repeatIntervalMillis: Long = REPEAT_INTERVAL_MILLIS
    ): Boolean {
        val stage = stageFor(med) ?: return false
        if (previous == null) return true
        if (previous.stage != stage) return true
        return nowEpochMillis - previous.lastSentEpochMillis >= repeatIntervalMillis
    }

    /**
     * The plain-language message the chosen contact receives.
     *
     * [patientName] is the optional name entered in Settings. When it is blank
     * the wording is exactly the same as before the setting existed, so the
     * feature keeps working without it.
     */
    fun buildMessage(med: Medication, stage: SmsStage, patientName: String = ""): String =
        buildMessage(
            medicineName = med.name,
            quantity = med.quantity,
            unitLabel = med.unitLabel,
            stage = stage,
            patientName = patientName
        )

    /**
     * Same as [buildMessage] but from raw values, so the Settings screen can
     * preview the exact wording with a sample medicine.
     */
    fun buildMessage(
        medicineName: String,
        quantity: Int,
        unitLabel: String,
        stage: SmsStage,
        patientName: String = ""
    ): String {
        val subject = ownedNoun(medicineName, patientName)
        return when (stage) {
            SmsStage.OUT ->
                "MemPharma: $subject has run out. Please arrange a refill."
            SmsStage.LOW ->
                "MemPharma: $subject is almost finished — only $quantity $unitLabel left. " +
                    "Please arrange a refill."
        }
    }

    /**
     * The one-off "Send a test text" message. Uses the same patient name so the
     * person sees exactly who the real alerts will mention.
     */
    fun buildTestMessage(patientName: String = ""): String {
        val who = ownedNoun("medicines", patientName)
        return "MemPharma test text: refill alerts for $who that are running low will arrive here."
    }

    /**
     * "Metformin" when no name is given, otherwise the medicine "belongs to" the
     * patient: "John's Metformin", or "James' Metformin" when the name already
     * ends in an s.
     */
    private fun ownedNoun(noun: String, patientName: String): String {
        val name = patientName.trim()
        if (name.isEmpty()) return noun
        val possessive = if (name.endsWith("s", ignoreCase = true)) "$name'" else "$name's"
        return "$possessive $noun"
    }
}
