package com.mempharma.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.mempharma.app.data.local.entity.DoseEvent
import kotlinx.coroutines.flow.Flow

@Dao
interface DoseEventDao {

    @Insert
    suspend fun insert(event: DoseEvent): Long

    /** True if this exact dose occurrence was already recorded as taken. */
    @Query(
        "SELECT * FROM dose_events WHERE medicationId = :medId " +
            "AND scheduledForEpochMillis = :occurrence AND action = 'TAKEN' LIMIT 1"
    )
    suspend fun findTaken(medId: Long, occurrence: Long): DoseEvent?

    /** True if this exact dose occurrence already has any resolution (taken/muted/missed). */
    @Query(
        "SELECT * FROM dose_events WHERE medicationId = :medId " +
            "AND scheduledForEpochMillis = :occurrence AND action IN ('TAKEN','MUTED','MISSED') LIMIT 1"
    )
    suspend fun findResolved(medId: Long, occurrence: Long): DoseEvent?

    /** Removes any earlier resolution for an occurrence (used when a mute is overridden by taken). */
    @Query("DELETE FROM dose_events WHERE medicationId = :medId AND scheduledForEpochMillis = :occurrence")
    suspend fun deleteForOccurrence(medId: Long, occurrence: Long)

    @Query("SELECT * FROM dose_events ORDER BY actionAtEpochMillis DESC")
    fun observeAll(): Flow<List<DoseEvent>>

    @Query("SELECT * FROM dose_events WHERE medicationId = :medId ORDER BY actionAtEpochMillis DESC")
    fun observeForMedication(medId: Long): Flow<List<DoseEvent>>

    @Query("DELETE FROM dose_events WHERE medicationId = :medId")
    suspend fun deleteForMedication(medId: Long)

    @Query("SELECT COUNT(*) FROM dose_events")
    suspend fun count(): Int
}
