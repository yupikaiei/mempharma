package com.mempharma.app.data.repo

import com.mempharma.app.data.local.dao.DoseEventDao
import com.mempharma.app.data.local.dao.MedicationDao
import com.mempharma.app.data.local.entity.Medication
import com.mempharma.app.data.scheduler.AlarmScheduler
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for reading/editing medications. Keeps the Room DAOs
 * and the reminder scheduler in sync: every create/update/delete re-schedules
 * (or removes) that medicine's alarms.
 */
@Singleton
class MedicationRepository @Inject constructor(
    private val medicationDao: MedicationDao,
    private val doseEventDao: DoseEventDao,
    private val scheduler: AlarmScheduler
) {

    val all: Flow<List<Medication>> = medicationDao.observeAll()
    val active: Flow<List<Medication>> = medicationDao.observeActive()

    fun observe(id: Long): Flow<Medication?> = medicationDao.observe(id)

    suspend fun get(id: Long): Medication? = medicationDao.get(id)

    /** One-shot snapshot of all medicines (used for CSV export). */
    suspend fun snapshot(): List<Medication> = medicationDao.getAll()

    suspend fun create(medication: Medication): Long {
        require(medication.times.isNotEmpty()) { "At least one dose time is required" }
        val id = medicationDao.insert(medication.copy(id = 0L))
        scheduler.scheduleMedication(medication.copy(id = id))
        return id
    }

    suspend fun update(medication: Medication) {
        require(medication.times.isNotEmpty()) { "At least one dose time is required" }
        medicationDao.update(medication)
        scheduler.scheduleMedication(medication)
    }

    suspend fun delete(id: Long) {
        val med = medicationDao.get(id) ?: return
        doseEventDao.deleteForMedication(id) // remove this medicine's audit trail too
        medicationDao.delete(id)
        scheduler.cancelMedication(med)
    }
}
