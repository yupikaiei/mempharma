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
import com.mempharma.app.MainActivity
import com.mempharma.app.R
import com.mempharma.app.data.local.entity.Medication
import com.mempharma.app.util.TimeFormat

/**
 * Builds and shows the dose reminder notifications, including the two action
 * buttons that drive the whole "track locally" flow:
 *   1. "I took it"  -> [ActionReceiver] records TAKEN and decrements stock.
 *   2. "Not now"     -> [ActionReceiver] records MUTED (silences only this dose).
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
                AlarmActions.CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when it is time to take your medicine"
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun showReminder(context: Context, med: Medication, occurrence: Long) {
        ensureChannels(context)

        // Tapping the body opens the app (single top so it just comes forward).
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        fun actionPending(action: String): PendingIntent {
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

        val builder = NotificationCompat.Builder(context, AlarmActions.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(context.getColor(R.color.ic_launcher_background))
            .setContentTitle("${med.name} — time to take it")
            .setContentText(reminderBody(med))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("${reminderBody(med)}\n\nScheduled at ${TimeFormat.formatTime(occurrence)}.")
            )
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentIntent)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .addAction(0, "✓  I took it", actionPending(AlarmActions.ACTION_TAKEN))
            .addAction(0, "Not now", actionPending(AlarmActions.ACTION_MUTE))

        NotificationManagerCompat.from(context)
            .notify(notificationId(occurrence), builder.build())
    }

    fun dismiss(context: Context, occurrence: Long) {
        NotificationManagerCompat.from(context).cancel(notificationId(occurrence))
    }

    private fun reminderBody(med: Medication): String =
        "Take ${med.doseQuantity} ${med.unitLabel}. " +
            "Tap \"I took it\" when done, or \"Not now\" to mute this reminder."
}
