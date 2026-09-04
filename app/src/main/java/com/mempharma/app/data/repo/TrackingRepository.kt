package com.mempharma.app.data.repo

import com.mempharma.app.data.local.dao.DoseEventDao
import com.mempharma.app.data.local.dao.MedicationDao
import com.mempharma.app.data.local.entity.DoseAction
import com.mempharma.app.data.local.entity.DoseEvent
import com.mempharma.app.data.local.entity.Medication
import com.mempharma.app.data.scheduler.AlarmScheduler
import com.mempharma.app.domain.DoseEngine
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
    private val scheduler: AlarmScheduler
) {

    /** Whole local history/audit log, newest first. */
    val history: Flow<List<DoseEvent>> = doseEventDao.observeAll()

    /** One-shot snapshot of the whole log (used for CSV export). */
    suspend fun snapshotEvents(): List<DoseEvent> = doseEventDao.observeAll().first()

    fun historyFor(medicationId: Long): Flow<List<DoseEvent>> =
        doseEventDao.observeForMedication(medicationId)

    /**
     * "I took it": idempotent per dose occurrence. Decrements stock by the dose
     * size and logs a [DoseAction.TAKEN] event. Returns false if that dose was
     * already recorded as taken.
     */
    suspend fun recordTaken(med: Medication, occurrence: Long, at: Long): Boolean {
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
        return newQuantity
    }
}
