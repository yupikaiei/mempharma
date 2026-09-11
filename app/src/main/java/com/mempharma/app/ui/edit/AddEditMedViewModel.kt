package com.mempharma.app.ui.edit

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mempharma.app.R
import com.mempharma.app.data.local.entity.Medication
import com.mempharma.app.data.repo.MedicationRepository
import com.mempharma.app.data.repo.TrackingRepository
import com.mempharma.app.domain.DoseEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    val unitLabel: String = "",
    val quantity: String = "30",
    val lowStockThreshold: String = "3",
    val colorIndex: Int = 0,
    val selectedTimes: Set<Int> = emptySet(), // minutes of day
    val isEditing: Boolean = false,
    val loading: Boolean = false,
    @StringRes val error: Int? = null,
    val finished: Boolean = false,
    val deleted: Boolean = false
)

/** Convenient, big, tappable time presets for people who do not want to type times. */
object TimePresets {
    data class Preset(@StringRes val labelRes: Int, val minuteOfDay: Int)

    val presets = listOf(
        Preset(R.string.edit_preset_morning, 8 * 60),
        Preset(R.string.edit_preset_noon, 12 * 60),
        Preset(R.string.edit_preset_evening, 18 * 60),
        Preset(R.string.edit_preset_night, 21 * 60 + 30)
    )
}

/** Used when the refill-warning box is left empty or cannot be read. */
private const val DEFAULT_LOW_STOCK_THRESHOLD = 3

@HiltViewModel
class AddEditMedViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val repository: MedicationRepository,
    private val trackingRepository: TrackingRepository
) : ViewModel() {

    private val medId: Long = savedStateHandle.get<Long>("medId") ?: 0L

    private val _state = MutableStateFlow(
        AddEditState(
            isEditing = medId > 0L,
            unitLabel = context.getString(R.string.unit_pill_default)
        )
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
                    lowStockThreshold = med.lowStockThreshold.toString(),
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

    fun updateLowStockThreshold(v: String) =
        _state.update { it.copy(lowStockThreshold = v.filter(Char::isDigit).take(3), error = null) }

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
        // Friendly fallback: a blank / unreadable level just means "warn me at 3".
        val lowThreshold = (s.lowStockThreshold.toIntOrNull() ?: DEFAULT_LOW_STOCK_THRESHOLD)
            .coerceAtLeast(1)

        val error = when {
            name.isEmpty() -> R.string.edit_error_name
            s.selectedTimes.isEmpty() -> R.string.edit_error_times
            doseQty == null || doseQty < 1 -> R.string.edit_error_dose
            qty == null || qty < 0 -> R.string.edit_error_stock
            else -> null
        }
        if (error != null) {
            _state.update { it.copy(error = error) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val newQuantity = qty!!
            val times = DoseEngine.parseTimes(
                DoseEngine.toTimesCsv(
                    s.selectedTimes.map { java.time.LocalTime.of(it / 60, it % 60) }.sorted()
                )
            )
            val existing = if (medId > 0) repository.get(medId) else null
            val med = Medication(
                id = if (medId > 0) medId else 0L,
                name = name,
                colorIndex = s.colorIndex,
                doseQuantity = doseQty!!,
                unitLabel = s.unitLabel.ifBlank { context.getString(R.string.unit_pill_default) },
                quantity = newQuantity,
                startDateEpochDay = existing?.startDateEpochDay ?: LocalDate.now().toEpochDay(),
                timesCsv = times.joinToString(",") { it.toString() },
                lowStockThreshold = lowThreshold
            )
            when {
                medId <= 0 -> repository.create(med)
                // Raising the stock is a refill: funnel it through the same path as
                // the quick refill button so the audit log, reminders and family
                // alerts all stay in step (and the amount is remembered).
                existing != null && newQuantity > existing.quantity -> {
                    val withoutQuantity = med.copy(quantity = existing.quantity)
                    repository.update(withoutQuantity)
                    trackingRepository.refill(
                        withoutQuantity,
                        newQuantity - existing.quantity,
                        System.currentTimeMillis()
                    )
                }
                else -> repository.update(med)
            }
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
