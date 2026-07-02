package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.SaveLockRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecoveryCode(
    val code: String,     // masked display for stored codes ("••••-••••")
    val isUsed: Boolean
)

data class RecoveryUiState(
    val codes: List<RecoveryCode> = emptyList(),
    val enteredCode: String = "",
    val codeValidationError: String? = null,
    val codeValidationSuccess: Boolean = false
)

/**
 * Recovery-code entry (offline redeem) and the stored code list. Generation reveals plaintext ONCE
 * via [revealedCodes]; after that only masked entries are shown.
 */
class RecoveryViewModel(private val repository: SaveLockRepository) : ViewModel() {

    private data class Entry(
        val enteredCode: String = "",
        val error: String? = null,
        val success: Boolean = false
    )

    private val entry = MutableStateFlow(Entry())

    /** Plaintext codes to show exactly once, right after generation. Null the rest of the time. */
    private val _revealedCodes = MutableStateFlow<List<String>?>(null)
    val revealedCodes: StateFlow<List<String>?> = _revealedCodes.asStateFlow()

    val uiState: StateFlow<RecoveryUiState> =
        combine(repository.recoveryCodes, entry) { stored, e ->
            RecoveryUiState(
                codes = stored.map { RecoveryCode(it.maskedDisplay, it.used) },
                enteredCode = e.enteredCode,
                codeValidationError = e.error,
                codeValidationSuccess = e.success
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecoveryUiState())

    fun updateEnteredCode(code: String) {
        entry.update { it.copy(enteredCode = code.uppercase(), error = null, success = false) }
    }

    fun submitRecoveryCode() {
        val input = entry.value.enteredCode.trim()
        if (input.isEmpty()) {
            entry.update { it.copy(error = "Please enter a code") }
            return
        }
        viewModelScope.launch {
            val ok = repository.redeemRecoveryCode(input)  // OFFLINE — no network needed
            if (ok) {
                entry.update { it.copy(success = true, error = null, enteredCode = "") }
                Log.d("RecoveryVM", "Recovery code accepted; today's lock lifted.")
            } else {
                entry.update { it.copy(error = "Invalid or already-used recovery code.") }
            }
        }
    }

    fun resetValidationState() {
        entry.update { it.copy(success = false, error = null, enteredCode = "") }
    }

    /** Generate a fresh batch, wiping old codes. The plaintext appears in [revealedCodes] once. */
    fun generateNewCodes(count: Int = 10) {
        viewModelScope.launch {
            _revealedCodes.value = repository.regenerateRecoveryCodes(count)
            Log.d("RecoveryVM", "Generated $count new recovery codes (shown once).")
        }
    }

    /** Ensure codes exist the first time the list screen opens; reveals them once if newly made. */
    fun ensureCodesExist(count: Int = 10) {
        viewModelScope.launch {
            val created = repository.ensureRecoveryCodesExist(count)
            if (created != null) _revealedCodes.value = created
        }
    }

    fun clearRevealed() {
        _revealedCodes.value = null
    }
}
