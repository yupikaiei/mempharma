package com.mempharma.app.data.scheduler

import com.mempharma.app.data.local.entity.Medication
import java.time.ZoneId

/**
 * Abstraction over Android's AlarmManager so scheduling logic stays testable and
 * can be swapped. All receivers and repositories talk to this interface.
 */
interface AlarmScheduler {

    /**
     * Schedule (or refresh) every daily dose alarm for [med]. Alarms with no
     * remaining stock or inactive medications are cancelled instead.
     */
    fun scheduleMedication(med: Medication, zone: ZoneId = ZoneId.systemDefault())

    /** Re-compute every medication's alarms from the database. */
    suspend fun rescheduleAll(zone: ZoneId = ZoneId.systemDefault())

    /** Remove all pending alarms for [med]. */
    fun cancelMedication(med: Medication)
}

/** Shared intent actions + extra keys used between the scheduler and receivers. */
object AlarmActions {
    const val ACTION_REMINDER = "com.mempharma.action.REMINDER"
    const val ACTION_TAKEN = "com.mempharma.action.TAKEN"
    const val ACTION_MUTE = "com.mempharma.action.MUTE"

    /** Broadcast to [com.mempharma.app.ui.alarm.AlarmActivity] telling it to close. */
    const val ACTION_ALARM_FINISH = "com.mempharma.action.ALARM_FINISH"

    const val EXTRA_MED_ID = "extra_med_id"
    const val EXTRA_OCCURRENCE = "extra_occurrence" // epoch millis of the due dose

    /**
     * Notification channel used by the dose alarms.
     *
     * NOTE: a channel's settings (importance, Do Not Disturb bypass) are immutable
     * once it has been created, so this id was bumped when the alarm gained DND
     * bypass. Installs that predate that keep [LEGACY_CHANNEL_ID] until
     * [Notifications.ensureChannels] deletes it.
     */
    const val CHANNEL_ID = "dose_reminders_alarm"

    /** The channel used before reminders could bypass Do Not Disturb. */
    const val LEGACY_CHANNEL_ID = "dose_reminders"
}
