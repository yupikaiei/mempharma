package com.mempharma.app

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.mempharma.app.data.scheduler.Notifications
import com.mempharma.app.data.sms.SmsAlertWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class MemPharmaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Create the high-priority notification channel up front.
        Notifications.ensureChannels(this)
        // Keep the optional "text my family member" refill alerts ticking.
        scheduleSmsChecks()
    }

    /**
     * One check a day is plenty: [com.mempharma.app.data.sms.SmsAlertManager]
     * only sends again after the 3-day repeat window, so being a little early
     * or late changes nothing. WorkManager keeps the job across reboots, and
     * KEEP makes it safe to call on every app start.
     */
    private fun scheduleSmsChecks() {
        val request = PeriodicWorkRequestBuilder<SmsAlertWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            SmsAlertWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
