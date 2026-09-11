package com.mempharma.app.data.repo

import com.mempharma.app.data.local.dao.DoseEventDao
import com.mempharma.app.data.local.dao.MedicationDao
import com.mempharma.app.data.local.entity.DoseAction
import com.mempharma.app.data.local.entity.DoseEvent
import com.mempharma.app.data.local.entity.Medication
import com.mempharma.app.data.refill.RefillAmountStore
import com.mempharma.app.data.scheduler.AlarmScheduler
import com.mempharma.app.data.sms.SmsAlertManager
import com.mempharma.app.domain.DoseEngine
import com.mempharma.app.domain.SmsTrigger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Everything that records a real-world action into the local audit log.
 * Both the notification receivers and the in-app buttons funnel through here so
 * behaviour is identical no matter where the tap happened.
 */
@Singleton
class TrackingRepository @Inject constructor(
    private val medicationDao: MedicationDao,
    private val doseEventDao: DoseEventDao,
    private val scheduler: AlarmScheduler,
    private val activeAlertRepository: ActiveAlertRepository,
    private val smsAlertManager: SmsAlertManager,
    private val refillAmountStore: RefillAmountStore
) {

    /** Whole local history/audit log, newest first. */
    val history: Flow<List<DoseEvent>> = doseEventDao.observeAll()

    /** One-shot snapshot of the whole log (used for CSV export). */
    suspend fun snapshotEvents(): List<DoseEvent> = doseEventDao.observeAll().first()

    fun historyFor(medicationId: Long): Flow<List<DoseEvent>> =
        doseEventDao.observeForMedication(medicationId)

    /**
     * Remember that an alarm actually rang for this dose. From now on the
     * full-screen alert is re-shown whenever the app is (re)opened, until the
     * dose is confirmed taken. Only real alarms are registered — overdue doses
     * that never rang are deliberately not turned into alerts.
     */
    suspend fun registerAlert(med: Medication, occurrence: Long) {
        activeAlertRepository.add(ActiveAlert(med.id, occurrence))
    }

    /**
     * The oldest alert that still needs an answer, or null when nothing is
     * pending. Muted doses still count (mute only silences the ringing — the
     * alert waits until taken). Entries whose medicine disappeared, was turned
     * off, ran out of stock, or was already taken elsewhere are dropped so the
     * queue cannot grow stale.
     */
    suspend fun pendingAlert(): ActiveAlert? {
        for (alert in activeAlertRepository.pending.first()) {
            val med = medicationDao.get(alert.medicationId)
            val stillRelevant = med != null &&
                med.active &&
                !med.isOut &&
                doseEventDao.findTaken(med.id, alert.occurrence) == null
            if (stillRelevant) return alert
            activeAlertRepository.remove(alert)
        }
        return null
    }

    /** Whether this exact dose occurrence was silenced (muted but not taken). */
    suspend fun isMuted(medId: Long, occurrence: Long): Boolean =
        doseEventDao.findMuted(medId, occurrence) != null

    /**
     * "I took it": idempotent per dose occurrence. Decrements stock by the dose
     * size and logs a [DoseAction.TAKEN] event. Returns false if that dose was
     * already recorded as taken.
     */
    suspend fun recordTaken(med: Medication, occurrence: Long, at: Long): Boolean {
        // Clear the persistent alert no matter which surface confirmed the dose
        // (alarm screen, notification button, or Today screen).
        activeAlertRepository.remove(ActiveAlert(med.id, occurrence))

        if (doseEventDao.findTaken(med.id, occurrence) != null) return false

        // If the user first muted then takes it later, the mute is superseded.
        doseEventDao.deleteForOccurrence(med.id, occurrence)

        val newQuantity = DoseEngine.quantityAfterTaking(med)
        if (newQuantity != med.quantity) {
            medicationDao.updateQuantity(med.id, newQuantity)
        }
        doseEventDao.insert(
            DoseEvent(
                medicationId = med.id,
                scheduledForEpochMillis = occurrence,
                actionAtEpochMillis = at,
                action = DoseAction.TAKEN.name,
                note = "Took ${med.doseQuantity} ${med.unitLabel}"
            )
        )
        // No stock left -> stop reminding until a refill is recorded.
        if (newQuantity <= 0) scheduler.cancelMedication(med)

        // Taking a dose is the main way stock drops, so this is where the
        // optional "text my family member" refill alert is checked.
        smsAlertManager.onStockChanged(med.copy(quantity = newQuantity))
        return true
    }

    /**
     * "Not now" (mute): silences/logs this single dose WITHOUT decrementing.
     * No further reminder for that occurrence is shown.
     */
    suspend fun recordMuted(med: Medication, occurrence: Long, at: Long): Boolean {
        if (doseEventDao.findResolved(med.id, occurrence) != null) return false
        doseEventDao.insert(
            DoseEvent(
                medicationId = med.id,
                scheduledForEpochMillis = occurrence,
                actionAtEpochMillis = at,
                action = DoseAction.MUTED.name,
                note = "Reminder muted — not taken"
            )
        )
        return true
    }

    /** Log a missed occurrence (used for lazy overdue marking). */
    suspend fun recordMissed(med: Medication, occurrence: Long, at: Long) {
        if (doseEventDao.findResolved(med.id, occurrence) != null) return
        doseEventDao.insert(
            DoseEvent(
                medicationId = med.id,
                scheduledForEpochMillis = occurrence,
                actionAtEpochMillis = at,
                action = DoseAction.MISSED.name,
                note = "Dose was missed"
            )
        )
    }

    /** Whether this exact dose occurrence already has any resolution. */
    suspend fun isResolved(medId: Long, occurrence: Long): Boolean =
        doseEventDao.findResolved(medId, occurrence) != null

    /**
     * Add pills back into stock. Logs a [DoseAction.REFILLED] event and re-arms
     * reminders (which were paused when stock hit zero).
     */
    suspend fun refill(med: Medication, addQuantity: Int, at: Long): Int {
        val newQuantity = (med.quantity + addQuantity).coerceAtLeast(0)
        if (newQuantity != med.quantity) medicationDao.updateQuantity(med.id, newQuantity)
        doseEventDao.insert(
            DoseEvent(
                medicationId = med.id,
                scheduledForEpochMillis = null,
                actionAtEpochMillis = at,
                action = DoseAction.REFILLED.name,
                note = "Added $addQuantity ${med.unitLabel}"
            )
        )
        scheduler.scheduleMedication(med.copy(quantity = newQuantity))
        // Stock is healthy again -> let the next run-low warning be sent.
        if (SmsTrigger.stageFor(med.copy(quantity = newQuantity)) == null) {
            smsAlertManager.resetFor(med.id)
        }
        // Remember this amount so the quick refill dialog can pre-fill it next time.
        refillAmountStore.remember(med.id, addQuantity)
        return newQuantity
    }

    /** The amount added the last time this medicine was refilled, or null if never. */
    suspend fun lastRefillAmount(medicationId: Long): Int? =
        refillAmountStore.lastAmount(medicationId)
}
