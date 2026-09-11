package com.mempharma.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mempharma.app.data.settings.SettingsRepository
import com.mempharma.app.data.sms.SmsAlertManager
import com.mempharma.app.data.sms.SmsAlertRepository
import com.mempharma.app.data.sms.SmsTestResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val smsAlertRepository: SmsAlertRepository,
    private val smsAlertManager: SmsAlertManager
) : ViewModel() {

    val fontScale: StateFlow<Float> = settingsRepository.fontScale.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsRepository.FONT_STANDARD
    )

    /** Raw alert-sound choice; blank means the system default alarm. */
    val alertRingtone: StateFlow<String> = settingsRepository.alertRingtone.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ""
    )

    /** Whether refill texts to the family member are switched on. */
    val smsEnabled: StateFlow<Boolean> = smsAlertRepository.enabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false
    )

    /** Display name of the chosen contact (blank when a number was typed). */
    val smsContactName: StateFlow<String> = smsAlertRepository.contactName.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ""
    )

    /** Phone number the refill texts go to (blank = nobody chosen yet). */
    val smsContactNumber: StateFlow<String> = smsAlertRepository.contactNumber.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ""
    )

    private val _smsTestResult = MutableStateFlow<SmsTestResult?>(null)

    /** Outcome of the last "Send a test text" tap, or null while none was made. */
    val smsTestResult: StateFlow<SmsTestResult?> = _smsTestResult.asStateFlow()

    fun setFontScale(scale: Float) {
        viewModelScope.launch { settingsRepository.setFontScale(scale) }
    }

    fun setAlertRingtone(value: String) {
        viewModelScope.launch { settingsRepository.setAlertRingtone(value) }
    }

    fun setSmsEnabled(value: Boolean) {
        viewModelScope.launch {
            smsAlertRepository.setEnabled(value)
            _smsTestResult.value = null
        }
    }

    fun setSmsContact(name: String, number: String) {
        viewModelScope.launch {
            smsAlertRepository.setContact(name, number)
            _smsTestResult.value = null
        }
    }

    fun clearSmsContact() {
        viewModelScope.launch {
            smsAlertRepository.clearContact()
            _smsTestResult.value = null
        }
    }

    fun sendTestSms() {
        viewModelScope.launch {
            _smsTestResult.value = smsAlertManager.sendTest()
        }
    }
}
