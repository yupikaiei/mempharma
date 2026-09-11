package com.mempharma.app.data.refill

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

private val Context.refillDataStore by preferencesDataStore(name = "mempharma_refill")

/**
 * Remembers how many pills the person added the last time they refilled each
 * medicine, so the quick refill dialog can pre-fill that amount next time and
 * they only have to confirm.
 *
 * Backed by DataStore (not Room) on purpose: the feature stays out of the
 * database schema, so adding it needs no schema version bump and no
 * destructive migration — the same trick
 * [com.mempharma.app.data.repo.ActiveAlertRepository] uses.
 */
@Singleton
class RefillAmountStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private val KEY_AMOUNTS = stringSetPreferencesKey("refill_amounts")
        private const val SEPARATOR = ":"

        /** Encode a medicine id + amount as a stable token for DataStore's string set. */
        fun encode(medicationId: Long, amount: Int): String =
            "$medicationId$SEPARATOR$amount"

        /** Decode a token into (medicine id, amount), ignoring anything malformed. */
        fun decode(token: String): Pair<Long, Int>? {
            val parts = token.split(SEPARATOR)
            if (parts.size != 2) return null
            val medId = parts[0].toLongOrNull() ?: return null
            val amount = parts[1].toIntOrNull() ?: return null
            if (medId < 0 || amount <= 0) return null
            return medId to amount
        }

        /**
         * Parse a raw token set into a map of medicine id -> remembered amount.
         * Malformed tokens are skipped. [remember] keeps a single token per
         * medicine, so ids are unique in practice.
         */
        fun parse(tokens: Set<String>): Map<Long, Int> =
            tokens.asSequence()
                .mapNotNull(::decode)
                .toMap()
    }

    /** The amount added last time for this medicine, or null when we have none. */
    suspend fun lastAmount(medicationId: Long): Int? =
        parse(context.refillDataStore.data.first()[KEY_AMOUNTS].orEmpty())[medicationId]

    /** Remember the amount just added for this medicine (replacing any previous value). */
    suspend fun remember(medicationId: Long, amount: Int) {
        if (amount <= 0) return
        context.refillDataStore.edit { prefs ->
            val kept = prefs[KEY_AMOUNTS].orEmpty()
                .filterNot { decode(it)?.first == medicationId }
                .toSet()
            prefs[KEY_AMOUNTS] = kept + encode(medicationId, amount)
        }
    }

    /** Forget a medicine (called when it is deleted). */
    suspend fun forget(medicationId: Long) {
        context.refillDataStore.edit { prefs ->
            val kept = prefs[KEY_AMOUNTS].orEmpty()
                .filterNot { decode(it)?.first == medicationId }
                .toSet()
            if (kept.isEmpty()) prefs.remove(KEY_AMOUNTS) else prefs[KEY_AMOUNTS] = kept
        }
    }
}
