package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class RecoveryCode(
    val code: String,
    val isUsed: Boolean
)

data class RecoveryUiState(
    val codes: List<RecoveryCode> = listOf(
        RecoveryCode("SL2A-9X7B", false),
        RecoveryCode("SL5K-1W4P", false),
        RecoveryCode("SL8N-3M2Q", true),
        RecoveryCode("SL4V-6H8Y", false),
        RecoveryCode("SL9T-5R1D", false),
        RecoveryCode("SL3X-7Z9J", true),
        RecoveryCode("SL6G-2F4V", false),
        RecoveryCode("SL1B-8P5M", false),
        RecoveryCode("SL7Q-9K3C", false),
        RecoveryCode("SL0Y-2M1W", false)
    ),
    val enteredCode: String = "",
    val codeValidationError: String? = null,
    val codeValidationSuccess: Boolean = false
)

class RecoveryViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(RecoveryUiState())
    val uiState: StateFlow<RecoveryUiState> = _uiState.asStateFlow()

    fun updateEnteredCode(code: String) {
        _uiState.update { it.copy(enteredCode = code.uppercase(), codeValidationError = null, codeValidationSuccess = false) }
    }

    fun submitRecoveryCode() {
        val input = _uiState.value.enteredCode.trim()
        if (input.isEmpty()) {
            _uiState.update { it.copy(codeValidationError = "Please enter a code") }
            return
        }

        val matchingCode = _uiState.value.codes.find { it.code.equals(input, ignoreCase = true) }
        if (matchingCode == null) {
            _uiState.update { it.copy(codeValidationError = "Invalid recovery code. Please check the spelling.") }
            Log.d("RecoveryVM", "Code validation failed: Invalid code")
        } else if (matchingCode.isUsed) {
            _uiState.update { it.copy(codeValidationError = "This recovery code has already been used.") }
            Log.d("RecoveryVM", "Code validation failed: Already used code")
        } else {
            // Mark as success
            _uiState.update { state ->
                val updatedCodes = state.codes.map { item ->
                    if (item.code == matchingCode.code) item.copy(isUsed = true) else item
                }
                state.copy(
                    codes = updatedCodes,
                    codeValidationSuccess = true,
                    codeValidationError = null,
                    enteredCode = ""
                )
            }
            Log.d("RecoveryVM", "Code validation success! Resetting lockout...")
        }
    }

    fun resetValidationState() {
        _uiState.update { it.copy(codeValidationSuccess = false, codeValidationError = null, enteredCode = "") }
    }
}
