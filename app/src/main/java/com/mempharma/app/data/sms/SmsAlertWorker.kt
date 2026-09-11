package com.mempharma.app.data.sms

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mempharma.app.di.AppGraph

/**
 * Daily safety net for the "text my family member" alerts.
 *
 * [SmsAlertManager] itself decides whether a text is actually due (it repeats at
 * most every 3 days), so running once a day is enough: it keeps the reminders
 * coming even when nobody opens the app, and is tolerant of the slight timing
 * drift Android applies to periodic work.
 *
 * Reaches Hilt through [AppGraph], exactly like the broadcast receivers, which
 * keeps it free of any Hilt-WorkManager wiring.
 */
class SmsAlertWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = runCatching {
        AppGraph.from(applicationContext).smsAlertManager().evaluateAll()
    }.fold(
        onSuccess = { Result.success() },
        onFailure = { Result.retry() }
    )

    companion object {
        /** Unique name so the job is never scheduled twice. */
        const val WORK_NAME = "sms_low_stock_check"
    }
}
