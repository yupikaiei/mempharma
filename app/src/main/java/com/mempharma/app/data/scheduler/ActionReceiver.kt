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
 * Handles the two notification buttons:
 *  - ACTION_TAKEN -> "I took it": log + decrement quantity + dismiss.
 *  - ACTION_MUTE  -> "Not now": log a mute for this dose only + dismiss (no nag).
 *
 * Runs from the notification (works on the lock screen, no app open needed) and
 * the UI calls the same repository methods for an identical result.
 */
class ActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != AlarmActions.ACTION_TAKEN && action != AlarmActions.ACTION_MUTE) return
        val medId = intent.getLongExtra(AlarmActions.EXTRA_MED_ID, -1L)
        val occurrence = intent.getLongExtra(AlarmActions.EXTRA_OCCURRENCE, -1L)
        if (medId < 0 || occurrence < 0) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val graph = AppGraph.from(context)
                val repository = graph.medicationRepository()
                val tracking = graph.trackingRepository()
                val med = repository.get(medId)
                val now = System.currentTimeMillis()
                if (med != null) {
                    when (action) {
                        AlarmActions.ACTION_TAKEN -> tracking.recordTaken(med, occurrence, now)
                        AlarmActions.ACTION_MUTE -> tracking.recordMuted(med, occurrence, now)
                    }
                }
                // The alarm is answered: stop the ringing service, clear the
                // full-screen activity and dismiss the notification.
                Notifications.dismiss(context, occurrence)
                AlarmRingerService.stop(context)
                runCatching {
                    context.sendBroadcast(Intent(AlarmActions.ACTION_ALARM_FINISH))
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
