package com.mempharma.app.data.repo

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * A dose occurrence whose alarm has actually rung but that has not been
 * confirmed taken yet. An alert survives process death and app restarts, so the
 * full-screen [com.mempharma.app.ui.alarm.AlarmActivity] can be brought back
 * (from a notification tap or whenever the app is reopened) until the person
 * presses "✓ I took it".
 */
data class ActiveAlert(
    val medicationId: Long,
    val occurrence: Long
)

private val Context.alertDataStore by preferencesDataStore(name = "mempharma_active_alerts")

/**
 * Persists the queue of live dose alerts.
 *
 * Backed by DataStore (not Room) on purpose: it keeps the whole feature out of
 * the database schema, so no schema version bump / destructive migration is
 * needed to add alert persistence.
 */
@Singleton
class ActiveAlertRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private val KEY_PENDING = stringSetPreferencesKey("pending_alerts")
        private const val SEPARATOR = ":"

        /** Encode an alert as a stable token for DataStore's string set. */
        fun encode(alert: ActiveAlert): String =
            "${alert.medicationId}$SEPARATOR${alert.occurrence}"

        /** Decode a token, ignoring anything malformed. */
        fun decode(token: String): ActiveAlert? {
            val parts = token.split(SEPARATOR)
            if (parts.size != 2) return null
            val medId = parts[0].toLongOrNull() ?: return null
            val occurrence = parts[1].toLongOrNull() ?: return null
            if (medId < 0 || occurrence < 0) return null
            return ActiveAlert(medId, occurrence)
        }

        /** Parse, de-duplicate and order (oldest occurrence first) a raw token set. */
        fun parse(tokens: Set<String>): List<ActiveAlert> =
            tokens.asSequence()
                .mapNotNull(::decode)
                .distinct()
                .sortedBy { it.occurrence }
                .toList()
    }

    /** All live alerts, oldest occurrence first. */
    val pending: Flow<List<ActiveAlert>> = context.alertDataStore.data
        .map { prefs -> parse(prefs[KEY_PENDING].orEmpty()) }

    suspend fun add(alert: ActiveAlert) {
        context.alertDataStore.edit { prefs ->
            val current = prefs[KEY_PENDING].orEmpty()
            prefs[KEY_PENDING] = current + encode(alert)
        }
    }

    suspend fun remove(alert: ActiveAlert) {
        context.alertDataStore.edit { prefs ->
            val current = prefs[KEY_PENDING].orEmpty()
            prefs[KEY_PENDING] = current - encode(alert)
        }
    }

    suspend fun removeForMedication(medicationId: Long) {
        context.alertDataStore.edit { prefs ->
            val current = prefs[KEY_PENDING].orEmpty()
            prefs[KEY_PENDING] = current
                .filterNot { decode(it)?.medicationId == medicationId }
                .toSet()
        }
    }
}
