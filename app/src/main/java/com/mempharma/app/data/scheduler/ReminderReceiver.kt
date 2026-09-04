package com.mempharma.app.data.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mempharma.app.di.AppGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Fires when an exact-alarm reminder is due: shows the high-priority
 * notification (with its Taken / Mute actions) and re-arms the next occurrence
 * for this dose time tomorrow.
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
                val scheduler = graph.alarmScheduler()

                val med = repository.get(medId) ?: return@launch
                if (!med.active || med.isOut) {
                    scheduler.cancelMedication(med)
                    return@launch
                }
                Notifications.showReminder(context, med, occurrence)
                scheduler.scheduleMedication(med) // re-arm for tomorrow's dose
            } finally {
                pendingResult.finish()
            }
        }
    }
}
