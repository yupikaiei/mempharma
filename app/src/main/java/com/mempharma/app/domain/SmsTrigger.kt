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

    /** The plain-language message the chosen contact receives. */
    fun buildMessage(med: Medication, stage: SmsStage): String = when (stage) {
        SmsStage.OUT ->
            "MemPharma: ${med.name} has run out. Please arrange a refill."
        SmsStage.LOW ->
            "MemPharma: ${med.name} is almost finished — only ${med.quantity} ${med.unitLabel} left. " +
                "Please arrange a refill."
    }
}
