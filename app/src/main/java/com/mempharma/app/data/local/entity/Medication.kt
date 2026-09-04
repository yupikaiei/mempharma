package com.mempharma.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mempharma.app.domain.DoseEngine
import java.time.LocalTime

/**
 * A single medicine the person is taking.
 *
 * @param quantity           pills currently left in stock.
 * @param doseQuantity       how many pills make up one dose.
 * @param startDateEpochDay  day (epoch day) the person began taking it — kept so we
 *                           can trace when treatment started.
 * @param timesCsv           comma separated "HH:mm" dose times, e.g. "08:00,20:00".
 * @param lowStockThreshold  at or below this remaining count we warn "time to refill".
 */
@Entity(tableName = "medications")
data class Medication(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String = "",
    val colorIndex: Int = 0,
    val doseQuantity: Int = 1,
    val unitLabel: String = "pill(s)",
    val quantity: Int = 0,
    val startDateEpochDay: Long = 0L,
    val timesCsv: String = "08:00",
    val lowStockThreshold: Int = 3,
    val active: Boolean = true
) {
    /** Parsed daily dose times (convenience accessor). */
    val times: List<LocalTime>
        get() = DoseEngine.parseTimes(timesCsv)

    val isLow: Boolean
        get() = active && quantity in 1..lowStockThreshold

    val isOut: Boolean
        get() = quantity <= 0
}
