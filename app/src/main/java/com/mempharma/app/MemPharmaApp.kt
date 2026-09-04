package com.mempharma.app

import android.app.Application
import com.mempharma.app.data.scheduler.Notifications
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MemPharmaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Create the high-priority notification channel up front.
        Notifications.ensureChannels(this)
    }
}
