package com.mempharma.app.data.sms

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mempharma.app.domain.SmsReminderState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.smsDataStore by preferencesDataStore(name = "mempharma_sms")

/**
 * The person's "text my family member" settings plus a record of what we have
 * already sent.
 *
 * Backed by DataStore (not Room) on purpose: the whole feature stays out of the
 * database schema, so adding it needs no schema bump and no migration — the
 * same trick [com.mempharma.app.data.repo.ActiveAlertRepository] uses.
 */
@Singleton
class SmsAlertRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private val KEY_ENABLED = booleanPreferencesKey("enabled")
        private val KEY_CONTACT_NAME = stringPreferencesKey("contact_name")
        private val KEY_CONTACT_NUMBER = stringPreferencesKey("contact_number")
        private val KEY_REMINDERS = stringSetPreferencesKey("reminders")
    }

    /** Whether the person turned the feature on. */
    val enabled: Flow<Boolean> = context.smsDataStore.data.map { it[KEY_ENABLED] ?: false }

    /** Display name of the chosen contact (may be blank when a number was typed). */
    val contactName: Flow<String> = context.smsDataStore.data.map { it[KEY_CONTACT_NAME] ?: "" }

    /** Phone number the texts go to (blank = nobody chosen). */
    val contactNumber: Flow<String> = context.smsDataStore.data.map { it[KEY_CONTACT_NUMBER] ?: "" }

    /** What we last sent about each medicine, keyed by medicine id. */
    val reminders: Flow<List<SmsReminderState>> = context.smsDataStore.data
        .map { SmsReminderState.parse(it[KEY_REMINDERS].orEmpty()) }

    suspend fun isEnabled(): Boolean = enabled.first()

    suspend fun setEnabled(value: Boolean) {
        context.smsDataStore.edit { it[KEY_ENABLED] = value }
    }

    suspend fun setContact(name: String, number: String) {
        context.smsDataStore.edit { prefs ->
            prefs[KEY_CONTACT_NAME] = name
            prefs[KEY_CONTACT_NUMBER] = number
        }
    }

    /** Forget the chosen contact (and what we told them). */
    suspend fun clearContact() {
        context.smsDataStore.edit { prefs ->
            prefs.remove(KEY_CONTACT_NAME)
            prefs.remove(KEY_CONTACT_NUMBER)
            prefs.remove(KEY_REMINDERS)
        }
    }

    suspend fun reminderFor(medicationId: Long): SmsReminderState? =
        reminders.first().firstOrNull { it.medicationId == medicationId }

    /** Remember (or replace) what we sent about one medicine. */
    suspend fun upsertReminder(state: SmsReminderState) {
        context.smsDataStore.edit { prefs ->
            val kept = prefs[KEY_REMINDERS].orEmpty()
                .filterNot { SmsReminderState.decode(it)?.medicationId == state.medicationId }
                .toSet()
            prefs[KEY_REMINDERS] = kept + SmsReminderState.encode(state)
        }
    }

    /** Forget one medicine (after a refill or when it is deleted). */
    suspend fun clearReminderFor(medicationId: Long) {
        context.smsDataStore.edit { prefs ->
            val kept = prefs[KEY_REMINDERS].orEmpty()
                .filterNot { SmsReminderState.decode(it)?.medicationId == medicationId }
                .toSet()
            if (kept.isEmpty()) prefs.remove(KEY_REMINDERS) else prefs[KEY_REMINDERS] = kept
        }
    }
}
