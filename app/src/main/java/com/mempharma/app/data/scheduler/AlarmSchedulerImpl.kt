package com.mempharma.app.data.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.mempharma.app.data.local.dao.MedicationDao
import com.mempharma.app.data.local.entity.Medication
import com.mempharma.app.domain.DoseEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AlarmManager-backed [AlarmScheduler].
 *
 * Design notes:
 *  - One exact alarm is scheduled per dose-time-of-day (a "slot"). When it fires
 *    [ReminderReceiver] re-arms that same slot for the following day, so alarms
 *    are always one-ahead and never "stack".
 *  - Each slot gets a deterministic [requestCode] derived from (medId, time) so we
 *    can cancel/replace it without tracking ids in the database.
 *  - If the user has not granted exact alarms we degrade to inexact alarms and the
 *    in-app overdue banner catches anything the OS delayed.
 */
@Singleton
class AlarmSchedulerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val medicationDao: MedicationDao
) : AlarmScheduler {

    private val alarmManager: AlarmManager
        get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun requestCode(medId: Long, minutesOfDay: Int): Int {
        var result = 17
        result = 31 * result + medId.hashCode()
        result = 31 * result + minutesOfDay
        return result
    }

    private fun reminderIntent(medId: Long, occurrence: Long): Intent =
        Intent(context, ReminderReceiver::class.java)
            .setAction(AlarmActions.ACTION_REMINDER)
            .putExtra(AlarmActions.EXTRA_MED_ID, medId)
            .putExtra(AlarmActions.EXTRA_OCCURRENCE, occurrence)

    override fun scheduleMedication(med: Medication, zone: ZoneId) {
        if (!med.active || med.isOut) {
            cancelMedication(med)
            return
        }
        val now = System.currentTimeMillis()
        med.times.forEach { time ->
            val minutes = time.hour * 60 + time.minute
            val occurrence = DoseEngine.nextOccurrenceForSlot(med, minutes, now, zone)
            val pi = PendingIntent.getBroadcast(
                context,
                requestCode(med.id, minutes),
                reminderIntent(med.id, occurrence),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (canScheduleExact()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, occurrence, pi)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, occurrence, pi)
            }
        }
    }

    override suspend fun rescheduleAll(zone: ZoneId) {
        val all = medicationDao.getAll()
        all.forEach { scheduleMedication(it, zone) }
    }

    override fun cancelMedication(med: Medication) {
        med.times.forEach { time ->
            val minutes = time.hour * 60 + time.minute
            val pi = PendingIntent.getBroadcast(
                context,
                requestCode(med.id, minutes),
                reminderIntent(med.id, 0L),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pi)
            pi.cancel()
        }
    }
}
