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
 * Re-computes reminder alarms after the clock, timezone or date changes
 * (manual time changes, DST transitions) so reminders stay accurate.
 */
class TimeChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_TIME_CHANGED &&
            action != Intent.ACTION_TIMEZONE_CHANGED &&
            action != Intent.ACTION_DATE_CHANGED
        ) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                AppGraph.from(context).alarmScheduler().rescheduleAll()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
