package com.mempharma.app.data.scheduler

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mempharma.app.R
import com.mempharma.app.data.local.entity.Medication
import com.mempharma.app.ui.alarm.AlarmActivity

/**
 * Builds the dose reminder notifications, including the two action buttons that
 * drive the whole "track locally" flow:
 *   1. "I took it"  -> [ActionReceiver] records TAKEN and decrements stock.
 *   2. "Not now"     -> [ActionReceiver] records MUTED (silences only this dose).
 *
 * The alarm variant is what makes it feel like a real alarm:
 *  - [AlarmActivity] is launched full-screen via [NotificationCompat.setFullScreenIntent]
 *    so it fills the whole device (and appears on the lock screen).
 *  - The notification is [NotificationCompat.FLAG_ONGOING_EVENT]/ongoing so it
 *    cannot simply be swiped away — it stays until the person takes a clear action.
 *  - Sound/vibration is produced continuously by [AlarmRingerService], not by this
 *    one-shot notification, so the alarm does not stop on its own.
 *
 * The notification id is derived from the dose occurrence so an action can
 * dismiss exactly the right notification.
 */
object Notifications {

    /** Stable per-occurrence id used for [NotificationManager.notify]/[cancel]. */
    fun notificationId(occurrence: Long): Int = (occurrence / 1000L).toInt()

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                AlarmActions.CHANNEL_ID,
                context.getString(R.string.notif_channel_reminders_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notif_channel_reminders_desc)
                enableVibration(false) // the ringer service handles sound + vibration
                setSound(null, null)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun actionPendingIntent(context: Context, med: Medication, occurrence: Long, action: String): PendingIntent {
        val intent = Intent(context, ActionReceiver::class.java)
            .setAction(action)
            .putExtra(AlarmActions.EXTRA_MED_ID, med.id)
            .putExtra(AlarmActions.EXTRA_OCCURRENCE, occurrence)
        return PendingIntent.getBroadcast(
            context,
            notificationId(occurrence),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * The ongoing, full-screen alarm notification shown by the ringer service.
     * No default sound here — the service loops the alarm tone until actioned.
     */
    fun buildAlarmNotification(context: Context, med: Medication, occurrence: Long): Notification {
        ensureChannels(context)

        // Tapping the notification body goes straight to the full-screen alert, so
        // the alarm cannot be left behind by opening the app normally — only
        // "✓ I took it" ends it.
        val openAlarm = PendingIntent.getActivity(
            context,
            0,
            Intent(context, AlarmActivity::class.java)
                .putExtra(AlarmActions.EXTRA_MED_ID, med.id)
                .putExtra(AlarmActions.EXTRA_OCCURRENCE, occurrence)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Fills the whole screen when the device is locked or the notification
        // is expanded (subject to Android 14+ "full-screen notifications" setting).
        val fullScreen = PendingIntent.getActivity(
            context,
            1,
            Intent(context, AlarmActivity::class.java)
                .putExtra(AlarmActions.EXTRA_MED_ID, med.id)
                .putExtra(AlarmActions.EXTRA_OCCURRENCE, occurrence)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, AlarmActions.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(context.getColor(R.color.ic_launcher_background))
            .setContentTitle(context.getString(R.string.notif_dose_title, med.name))
            .setContentText(
                context.getString(R.string.notif_dose_text, med.doseQuantity, med.unitLabel)
            )
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        context.getString(
                            R.string.notif_dose_bigtext,
                            med.doseQuantity,
                            med.unitLabel
                        )
                    )
            )
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openAlarm)
            .setFullScreenIntent(fullScreen, true)
            .setOngoing(true) // cannot be swiped away; must take a clear action
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .addAction(
                0,
                context.getString(R.string.notif_action_taken),
                actionPendingIntent(context, med, occurrence, AlarmActions.ACTION_TAKEN)
            )
            .addAction(
                0,
                context.getString(R.string.notif_action_mute),
                actionPendingIntent(context, med, occurrence, AlarmActions.ACTION_MUTE)
            )
            .build()
    }

    /** Fallback when a foreground service cannot start: post the alarm notification
     *  without the service (still full-screen via the intent where allowed). */
    fun notifyAlarm(context: Context, med: Medication, occurrence: Long) {
        ensureChannels(context)
        NotificationManagerCompat.from(context)
            .notify(notificationId(occurrence), buildAlarmNotification(context, med, occurrence))
    }

    fun dismiss(context: Context, occurrence: Long) {
        NotificationManagerCompat.from(context).cancel(notificationId(occurrence))
    }
}
