package com.mempharma.app.ui.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mempharma.app.data.local.entity.Medication
import com.mempharma.app.data.repo.MedicationRepository
import com.mempharma.app.domain.DoseEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddEditState(
    val name: String = "",
    val doseQuantity: String = "1",
    val unitLabel: String = "pill(s)",
    val quantity: String = "30",
    val colorIndex: Int = 0,
    val selectedTimes: Set<Int> = emptySet(), // minutes of day
    val isEditing: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val finished: Boolean = false,
    val deleted: Boolean = false
)

/** Convenient, big, tappable time presets for people who do not want to type times. */
object TimePresets {
    data class Preset(val label: String, val minuteOfDay: Int)

    val presets = listOf(
        Preset("Morning", 8 * 60),
        Preset("Noon", 12 * 60),
        Preset("Evening", 18 * 60),
        Preset("Night", 21 * 60 + 30)
    )
}

@HiltViewModel
class AddEditMedViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MedicationRepository
) : ViewModel() {

    private val medId: Long = savedStateHandle.get<Long>("medId") ?: 0L

    private val _state = MutableStateFlow(
        AddEditState(isEditing = medId > 0L)
    )
    val state: StateFlow<AddEditState> = _state.asStateFlow()

    init {
        if (medId > 0L) {
            loadExisting()
        }
    }

    private fun loadExisting() {
        viewModelScope.launch {
            val med = repository.get(medId) ?: return@launch
            _state.update {
                it.copy(
                    name = med.name,
                    doseQuantity = med.doseQuantity.toString(),
                    unitLabel = med.unitLabel,
                    quantity = med.quantity.toString(),
                    colorIndex = med.colorIndex,
                    selectedTimes = med.times.map { t -> t.hour * 60 + t.minute }.toSet(),
                    isEditing = true,
                    loading = false
                )
            }
        }
    }

    fun updateName(v: String) = _state.update { it.copy(name = v, error = null) }
    fun updateDoseQuantity(v: String) = _state.update { it.copy(doseQuantity = v.filter(Char::isDigit).take(3), error = null) }
    fun updateUnit(v: String) = _state.update { it.copy(unitLabel = v, error = null) }
    fun updateQuantity(v: String) = _state.update { it.copy(quantity = v.filter(Char::isDigit).take(6), error = null) }
    fun updateColor(i: Int) = _state.update { it.copy(colorIndex = i) }

    fun toggleTime(minuteOfDay: Int) = _state.update { s ->
        val set = if (minuteOfDay in s.selectedTimes) s.selectedTimes - minuteOfDay else s.selectedTimes + minuteOfDay
        s.copy(selectedTimes = set, error = null)
    }

    fun addCustomTime(hour: Int, minute: Int) =
        _state.update { s -> s.copy(selectedTimes = s.selectedTimes + (hour * 60 + minute), error = null) }

    fun save() {
        val s = _state.value
        val name = s.name.trim()
        val doseQty = s.doseQuantity.toIntOrNull()
        val qty = s.quantity.toIntOrNull()

        val error = when {
            name.isEmpty() -> "Please type the medicine name."
            s.selectedTimes.isEmpty() -> "Choose at least one reminder time."
            doseQty == null || doseQty < 1 -> "How many pills each time?"
            qty == null || qty < 0 -> "How many pills are in the bottle?"
            else -> null
        }
        if (error != null) {
            _state.update { it.copy(error = error) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val times = DoseEngine.parseTimes(
                DoseEngine.toTimesCsv(
                    s.selectedTimes.map { java.time.LocalTime.of(it / 60, it % 60) }.sorted()
                )
            )
            val med = Medication(
                id = if (medId > 0) medId else 0L,
                name = name,
                colorIndex = s.colorIndex,
                doseQuantity = doseQty!!,
                unitLabel = s.unitLabel.ifBlank { "pill(s)" },
                quantity = qty!!,
                startDateEpochDay = if (medId > 0) {
                    (repository.get(medId)?.startDateEpochDay ?: LocalDate.now().toEpochDay())
                } else {
                    LocalDate.now().toEpochDay()
                },
                timesCsv = times.joinToString(",") { it.toString() },
                lowStockThreshold = 3
            )
            if (medId > 0) repository.update(med) else repository.create(med)
            _state.update { it.copy(loading = false, finished = true) }
        }
    }

    fun delete() {
        if (medId <= 0) return
        viewModelScope.launch {
            repository.delete(medId)
            _state.update { it.copy(deleted = true) }
        }
    }
}
