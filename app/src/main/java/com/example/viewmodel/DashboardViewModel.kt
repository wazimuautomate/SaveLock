package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface PaymentStatus {
    object Idle : PaymentStatus
    object Requesting : PaymentStatus
    object WaitingForSTK : PaymentStatus
    object Success : PaymentStatus
    data class Failed(val error: String) : PaymentStatus
    object Timeout : PaymentStatus
}

data class DashboardUiState(
    val totalSaved: String = "KES 12,450",
    val todaysTarget: String = "KES 500",
    val isSavedToday: Boolean = false,
    val timeUntilLock: String = "2h 14m",
    val isSavingEnabled: Boolean = true,
    val streakDays: Int = 5,
    val showDisablingConfirmation: Boolean = false,
    
    // Payment sheet state
    val mpesaNumber: String = "254712345678",
    val chargeAmount: String = "KES 500",
    val paymentStatus: PaymentStatus = PaymentStatus.Idle,
    val paymentPhoneError: String? = null
)

class DashboardViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    fun toggleSavingEnabled(enabled: Boolean) {
        if (!enabled) {
            // Show confirmation dialog before turning off
            _uiState.update { it.copy(showDisablingConfirmation = true) }
        } else {
            _uiState.update { it.copy(isSavingEnabled = true) }
            Log.d("DashboardVM", "Saving master enabled")
        }
    }

    fun confirmDisableSaving(confirm: Boolean) {
        if (confirm) {
            _uiState.update { it.copy(isSavingEnabled = false, showDisablingConfirmation = false) }
            Log.d("DashboardVM", "Saving master disabled after user confirmation")
        } else {
            _uiState.update { it.copy(showDisablingConfirmation = false) }
            Log.d("DashboardVM", "Disabling cancelled by user")
        }
    }

    fun updateMpesaNumber(number: String) {
        _uiState.update { 
            it.copy(
                mpesaNumber = number,
                paymentPhoneError = if (validateMpesa(number)) null else "Format must be 2547XXXXXXXX"
            ) 
        }
    }

    fun triggerPayment() {
        val currentPhone = _uiState.value.mpesaNumber
        if (!validateMpesa(currentPhone)) {
            _uiState.update { it.copy(paymentPhoneError = "Format must be 2547XXXXXXXX") }
            return
        }

        viewModelScope.launch {
            Log.d("DashboardVM", "Initiating payment for $currentPhone")
            _uiState.update { it.copy(paymentStatus = PaymentStatus.Requesting) }
            delay(1500)
            
            _uiState.update { it.copy(paymentStatus = PaymentStatus.WaitingForSTK) }
            delay(2500)

            // Randomly succeed or fail for rich mock demo interaction
            val isSuccess = Math.random() > 0.3
            if (isSuccess) {
                _uiState.update { 
                    it.copy(
                        paymentStatus = PaymentStatus.Success,
                        isSavedToday = true,
                        totalSaved = "KES 12,950",
                        streakDays = it.streakDays + 1
                    ) 
                }
                Log.d("DashboardVM", "STK push payment mock success")
            } else {
                // Alternates failed and timeout
                if (Math.random() > 0.5) {
                    _uiState.update { it.copy(paymentStatus = PaymentStatus.Failed("Transaction cancelled by user")) }
                    Log.d("DashboardVM", "STK push mock failure")
                } else {
                    _uiState.update { it.copy(paymentStatus = PaymentStatus.Timeout) }
                    Log.d("DashboardVM", "STK push mock timeout")
                }
            }
        }
    }

    fun resetPaymentState() {
        _uiState.update { it.copy(paymentStatus = PaymentStatus.Idle) }
    }

    private fun validateMpesa(phone: String): Boolean {
        val regex = Regex("^2547\\d{8}$")
        return regex.matches(phone)
    }
}
