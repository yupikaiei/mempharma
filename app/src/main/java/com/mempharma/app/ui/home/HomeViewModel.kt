package com.mempharma.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mempharma.app.data.local.entity.DoseEvent
import com.mempharma.app.data.local.entity.Medication
import com.mempharma.app.data.repo.MedicationRepository
import com.mempharma.app.data.repo.TrackingRepository
import com.mempharma.app.domain.DoseEngine
import com.mempharma.app.ui.components.DEFAULT_REFILL_AMOUNT
import com.mempharma.app.ui.components.RefillTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A single medicine's card on the Today screen. */
data class HomeMedCard(
    val med: Medication,
    val slots: List<DoseEngine.TodaysSlot>,
    val nextPending: DoseEngine.TodaysSlot?,
    val allResolvedToday: Boolean,
    val hasSlotsToday: Boolean
) {
    val isDueNow: Boolean get() = nextPending?.overdue == true
}

data class HomeUiState(
    val cards: List<HomeMedCard> = emptyList(),
    val overdueCount: Int = 0,
    val now: Long = System.currentTimeMillis()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val medRepository: MedicationRepository,
    private val trackingRepository: TrackingRepository
) : ViewModel() {

    /** Advances "now" every 30s so overdue states stay live without user input. */
    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(30_000)
        }
    }

    val uiState: StateFlow<HomeUiState> =
        combine(medRepository.all, trackingRepository.history, ticker) { meds, events, now ->
            buildState(meds, events, now)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState()
        )

    private fun buildState(
        meds: List<Medication>,
        events: List<DoseEvent>,
        now: Long
    ): HomeUiState {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val cards = meds.asSequence()
            .filter { it.active }
            .map { med ->
                val todaysEvents = events.filter { event ->
                    event.medicationId == med.id &&
                        event.scheduledForEpochMillis != null &&
                        event.scheduledForEpochMillis in dayStart until dayEnd
                }
                val slots = DoseEngine.todaysSlots(med, todaysEvents, now, zone)
                HomeMedCard(
                    med = med,
                    slots = slots,
                    nextPending = slots.firstOrNull { !it.resolved },
                    allResolvedToday = slots.isNotEmpty() && slots.all { it.resolved },
                    hasSlotsToday = slots.isNotEmpty()
                )
            }
            .sortedWith(
                compareBy { card ->
                    // Meds due right now first, then by next pending time, then by name.
                    when {
                        card.isDueNow -> 0L
                        card.nextPending != null -> card.nextPending.occurrence
                        else -> Long.MAX_VALUE
                    }
                }
            )
            .toList()

        return HomeUiState(
            cards = cards,
            overdueCount = cards.count { it.isDueNow },
            now = now
        )
    }

    fun takeDose(med: Medication, occurrence: Long) {
        viewModelScope.launch {
            trackingRepository.recordTaken(med, occurrence, System.currentTimeMillis())
        }
    }

    fun muteDose(med: Medication, occurrence: Long) {
        viewModelScope.launch {
            trackingRepository.recordMuted(med, occurrence, System.currentTimeMillis())
        }
    }

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
