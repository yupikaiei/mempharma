package com.mempharma.app.data.sms

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mempharma.app.R
import com.mempharma.app.data.local.dao.MedicationDao
import com.mempharma.app.data.local.entity.Medication
import com.mempharma.app.domain.SmsReminderState
import com.mempharma.app.domain.SmsTrigger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Outcome of the "Send a test text" button, so the screen can explain itself. */
sealed interface SmsTestResult {
    /** The text was handed to the phone's messaging radio. */
    data object Sent : SmsTestResult

    /** No contact has been chosen yet. */
    data object NoContact : SmsTestResult

    /** The person has not allowed MemPharma to send texts. */
    data object NoPermission : SmsTestResult

    /** The phone refused to send (no signal, no SIM, ...). */
    data object Failed : SmsTestResult
}

/**
 * Decides when to text the chosen family member that a medicine is running out,
 * and remembers what was already sent.
 *
 * Triggering happens in two places so a message is never missed:
 *  1. right after a dose is recorded (works even from a notification, when the
 *     app itself was never opened), and
 *  2. once a day from [SmsAlertWorker], which keeps the 3-day repeat going even
 *     if nobody opens the app.
 *
 * A [Mutex] makes those two paths safe to run at the same time, so the contact
 * never receives the same message twice.
 *
 * This class intentionally depends on [MedicationDao] rather than
 * `MedicationRepository` so it can be called *from* the repository without a
 * dependency cycle.
 */
@Singleton
class SmsAlertManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val medicationDao: MedicationDao,
    private val repository: SmsAlertRepository,
    private val sender: SmsSender
) {

    companion object {
        private const val CHANNEL_ID = "sms_alerts"
        private const val BLOCKED_NOTIFICATION_ID = 90001
    }

    private val mutex = Mutex()

    /** Check every medicine — used by the daily background worker. */
    suspend fun evaluateAll() {
        mutex.withLock {
            val number = readyNumber() ?: return@withLock
            val patientName = repository.patientName.first()
            medicationDao.getAll().forEach { evaluateLocked(it, number, patientName) }
        }
    }

    /** Check one medicine right after its stock changed. */
    suspend fun onStockChanged(med: Medication) {
        mutex.withLock {
            val number = readyNumber() ?: return@withLock
            evaluateLocked(med, number, repository.patientName.first())
        }
    }

    /** Forget what we sent about a medicine — after a refill or a delete. */
    suspend fun resetFor(medicationId: Long) {
        mutex.withLock { repository.clearReminderFor(medicationId) }
    }

    /**
     * Remember a medicine's current stage *without* sending anything. Used when
     * a medicine is added already low or empty, so setting the app up does not
     * fire off texts straight away.
     */
    suspend fun seedWithoutSending(med: Medication) {
        mutex.withLock {
            val stage = SmsTrigger.stageFor(med) ?: return@withLock
            repository.upsertReminder(
                SmsReminderState(med.id, stage, System.currentTimeMillis())
            )
        }
    }

    /** Send a one-off message so the person can confirm the feature works. */
    suspend fun sendTest(): SmsTestResult {
        return mutex.withLock {
            val number = repository.contactNumber.first()
            when {
                number.isBlank() -> SmsTestResult.NoContact
                !sender.canSend(context) -> SmsTestResult.NoPermission
                sender.send(
                    context,
                    number,
                    SmsTrigger.buildTestMessage(
                        repository.patientName.first(),
                        SmsTexts.templates(context)
                    )
                ) -> SmsTestResult.Sent
                else -> SmsTestResult.Failed
            }
        }
    }

    /** The number to text, or null when the feature is off / nobody is chosen. */
    private suspend fun readyNumber(): String? {
        if (!repository.isEnabled()) return null
        val number = repository.contactNumber.first()
        return number.takeIf { it.isNotBlank() }
    }

    private suspend fun evaluateLocked(med: Medication, number: String, patientName: String) {
        val stage = SmsTrigger.stageFor(med) ?: return
        val now = System.currentTimeMillis()
        if (!SmsTrigger.shouldSend(med, repository.reminderFor(med.id), now)) return

        if (!sender.canSend(context)) {
            // Do not record anything: once permission is granted the text will
            // go out on the next check.
            notifyCannotSend()
            return
        }

        if (sender.send(
                context,
                number,
                SmsTrigger.buildMessage(med, stage, patientName, SmsTexts.templates(context))
            )
        ) {
            repository.upsertReminder(SmsReminderState(med.id, stage, now))
        }
        // A failed send is deliberately not recorded, so it is retried later.
    }

    /**
     * A quiet, updating notification explaining that the refill text could not
     * go out. A fixed id plus "only alert once" means it never nags.
     */
    private fun notifyCannotSend() {
        ensureChannel()
        val text = context.getString(R.string.notif_sms_blocked_text)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_sms_blocked_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(BLOCKED_NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_sms_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = context.getString(R.string.notif_sms_channel_desc) }
        )
    }
}
