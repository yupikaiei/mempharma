package com.mempharma.app.ui.meds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mempharma.app.data.local.entity.Medication
import com.mempharma.app.data.repo.MedicationRepository
import com.mempharma.app.data.repo.TrackingRepository
import com.mempharma.app.ui.components.DEFAULT_REFILL_AMOUNT
import com.mempharma.app.ui.components.RefillTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class MedListViewModel @Inject constructor(
    repository: MedicationRepository,
    private val trackingRepository: TrackingRepository
) : ViewModel() {

    val medications: StateFlow<List<Medication>> = repository.all.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    // --- Quick refill ---

    private val _refillTarget = MutableStateFlow<RefillTarget?>(null)

    /** The medicine awaiting a quick refill (dialog open), or null. */
    val refillTarget: StateFlow<RefillTarget?> = _refillTarget.asStateFlow()

    /** Open the refill dialog for [med], pre-filled with the last amount used. */
    fun startRefill(med: Medication) {
        viewModelScope.launch {
            val prefill = trackingRepository.lastRefillAmount(med.id) ?: DEFAULT_REFILL_AMOUNT
            _refillTarget.value = RefillTarget(med, prefill)
        }
    }

    /** Confirm a refill, adding [amount] pills back into the bottle. */
    fun confirmRefill(amount: Int) {
        val target = _refillTarget.value ?: return
        _refillTarget.value = null
        if (amount <= 0) return
        viewModelScope.launch {
            trackingRepository.refill(target.med, amount, System.currentTimeMillis())
        }
    }

    fun cancelRefill() {
        _refillTarget.value = null
    }
}
