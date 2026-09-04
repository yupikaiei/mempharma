package com.mempharma.app.ui.meds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mempharma.app.data.local.entity.Medication
import com.mempharma.app.data.repo.MedicationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class MedListViewModel @Inject constructor(
    repository: MedicationRepository
) : ViewModel() {

    val medications: StateFlow<List<Medication>> = repository.all.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )
}
