package com.mempharma.app.ui.history

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mempharma.app.data.local.entity.DoseEvent
import com.mempharma.app.data.local.entity.Medication
import com.mempharma.app.data.repo.MedicationRepository
import com.mempharma.app.data.repo.TrackingRepository
import com.mempharma.app.util.ExportUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One displayable line of the event log. */
data class HistoryRow(
    val medicationId: Long,
    val medName: String,
    val colorIndex: Int,
    val action: String,
    val atEpoch: Long,
    val dayEpoch: Long,
    val scheduledEpoch: Long?,
    val note: String?
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val medRepository: MedicationRepository,
    private val trackingRepository: TrackingRepository
) : ViewModel() {

    val rows: StateFlow<List<HistoryRow>> = combine(
        medRepository.all,
        trackingRepository.history
    ) { meds, events ->
        buildRows(meds, events)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _exportFile = MutableSharedFlow<File>(extraBufferCapacity = 1)
    val exportFile = _exportFile.asSharedFlow()

    private fun buildRows(meds: List<Medication>, events: List<DoseEvent>): List<HistoryRow> {
        val medsById = meds.associateBy { it.id }
        val zone = ZoneId.systemDefault()
        return events
            .map { event ->
                val med = medsById[event.medicationId]
                HistoryRow(
                    medicationId = event.medicationId,
                    medName = med?.name.orEmpty(),
                    colorIndex = med?.colorIndex ?: 0,
                    action = event.action,
                    atEpoch = event.actionAtEpochMillis,
                    dayEpoch = Instant.ofEpochMilli(event.actionAtEpochMillis)
                        .atZone(zone).toLocalDate().toEpochDay(),
                    scheduledEpoch = event.scheduledForEpochMillis,
                    note = event.note
                )
            }
            .sortedWith(compareByDescending<HistoryRow> { it.atEpoch })
    }

    /** Builds the full CSV trace file and emits it for the UI to share. */
    fun export(context: Context) {
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                val meds = medRepository.snapshot()
                val events = trackingRepository.snapshotEvents()
                ExportUtils.buildCsvFile(context, meds, events, ZoneId.systemDefault())
            }
            _exportFile.tryEmit(file)
        }
    }
}
