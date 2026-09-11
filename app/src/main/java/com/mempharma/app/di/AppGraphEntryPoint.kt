package com.mempharma.app.di

import android.content.Context
import com.mempharma.app.data.repo.MedicationRepository
import com.mempharma.app.data.repo.TrackingRepository
import com.mempharma.app.data.scheduler.AlarmScheduler
import com.mempharma.app.data.settings.SettingsRepository
import com.mempharma.app.data.sms.SmsAlertManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Hilt [EntryPoint] that lets Android components which are instantiated by the
 * system (our manifest-registered BroadcastReceivers) reach the singleton graph.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppGraphEntryPoint {
    fun medicationRepository(): MedicationRepository
    fun trackingRepository(): TrackingRepository
    fun alarmScheduler(): AlarmScheduler
    fun settingsRepository(): SettingsRepository
    fun smsAlertManager(): SmsAlertManager
}

object AppGraph {
    fun from(context: Context): AppGraphEntryPoint =
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            AppGraphEntryPoint::class.java
        )
}
