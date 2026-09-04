package com.mempharma.app.data.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mempharma.app.di.AppGraph
import com.mempharma.app.ui.alarm.AlarmActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Fires when an exact-alarm reminder is due. Turns the reminder into a real
 * alarm:
 *  1. starts [AlarmRingerService] so the tone/vibration loops until actioned,
 *  2. opens the full-screen [AlarmActivity] (fills the device / lock screen),
 *  3. re-arms the next occurrence for this dose time tomorrow.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmActions.ACTION_REMINDER) return
        val medId = intent.getLongExtra(AlarmActions.EXTRA_MED_ID, -1L)
        val occurrence = intent.getLongExtra(AlarmActions.EXTRA_OCCURRENCE, -1L)
        if (medId < 0 || occurrence < 0) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val graph = AppGraph.from(context)
                val repository = graph.medicationRepository()
                val tracking = graph.trackingRepository()
                val scheduler = graph.alarmScheduler()

                val med = repository.get(medId) ?: return@launch
                if (!med.active || med.isOut) {
                    scheduler.cancelMedication(med)
                    return@launch
                }
                // Edge case: dose already taken/muted from the app before this alarm
                // (e.g. delayed delivery) — do not ring for a resolved dose.
                if (tracking.isResolved(med.id, occurrence)) return@launch

                // 1. Looping ringer (falls back to a plain full-screen notification
                //    if Android blocks a background foreground-service start).
                try {
                    AlarmRingerService.start(context, med.id, occurrence)
                } catch (_: Exception) {
                    Notifications.notifyAlarm(context, med, occurrence)
                }

                // 2. Full-screen activity (covers the whole device).
                runCatching {
                    context.startActivity(
                        Intent(context, AlarmActivity::class.java)
                            .putExtra(AlarmActions.EXTRA_MED_ID, med.id)
                            .putExtra(AlarmActions.EXTRA_OCCURRENCE, occurrence)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }

                // 3. Re-arm for tomorrow's dose.
                scheduler.scheduleMedication(med)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
