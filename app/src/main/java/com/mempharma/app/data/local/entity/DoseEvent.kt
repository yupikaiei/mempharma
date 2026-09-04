package com.mempharma.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One entry in the local audit/history log. The table is **append-only from the
 * user's perspective** — every reminder action ("I took it" / mute), missed dose,
 * refill, or edit is stored with a timestamp so it can be traced back later.
 *
 * @param scheduledForEpochMillis the specific dose occurrence this refers to
 *                                (null for non-dose events such as a refill).
 * @param actionAtEpochMillis     wall-clock time of the action.
 * @param action                  see [DoseAction].
 * @param note                    optional human readable note (e.g. refill amount).
 */
@Entity(
    tableName = "dose_events",
    indices = [
        Index("medicationId"),
        Index("actionAtEpochMillis"),
        Index(value = ["medicationId", "scheduledForEpochMillis"])
    ]
)
data class DoseEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val medicationId: Long,
    val scheduledForEpochMillis: Long? = null,
    val actionAtEpochMillis: Long = 0L,
    val action: String = DoseAction.TAKEN.name,
    val note: String? = null
)
