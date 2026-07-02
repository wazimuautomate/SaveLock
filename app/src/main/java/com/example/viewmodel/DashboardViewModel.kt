package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.SavingsStatus
import com.example.data.repository.SaveLockRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalTime

sealed interface PaymentStatus {
    object Idle : PaymentStatus
    object Requesting : PaymentStatus
    object WaitingForSTK : PaymentStatus
    object Success : PaymentStatus
    data class Failed(val error: String) : PaymentStatus
    object Timeout : PaymentStatus
}

data class DashboardUiState(
    val totalSaved: String = "KES 0",
    val todaysTarget: String = "KES 500",
    val isSavedToday: Boolean = false,
    val timeUntilLock: String = "--",
    val isSavingEnabled: Boolean = true,
    val streakDays: Int = 0,
    val showDisablingConfirmation: Boolean = false,

    // Payment sheet state
    val mpesaNumber: String = "",
    val chargeAmount: String = "KES 500",
    val paymentStatus: PaymentStatus = PaymentStatus.Idle,
    val paymentPhoneError: String? = null
)

/**
 * Dashboard backed by Room. Balance/streak/target/saved-today come from the repository; payment and
 * the disable-confirmation dialog are transient UI state.
 *
 * NOTE: [triggerPayment] is TEMPORARILY simulated until the Supabase STK-push wiring (task 10)
 * replaces its body. On a simulated success it already writes a real SavingsLog so streaks/history
 * update for real end-to-end.
 */
class DashboardViewModel(private val repository: SaveLockRepository) : ViewModel() {

    private data class PayState(
        val paymentStatus: PaymentStatus = PaymentStatus.Idle,
        val phoneText: String? = null,           // in-progress edit; null => persisted mpesa number
        val phoneError: String? = null,
        val showDisablingConfirmation: Boolean = false
    )

    private val payState = MutableStateFlow(PayState())

    // Emits every minute so the "time until lock" label stays roughly current.
    private val ticker = flow {
        while (true) {
            emit(Unit)
            delay(60_000)
        }
    }

    val uiState: StateFlow<DashboardUiState> =
        combine(repository.config, repository.dashboardTotals, ticker, payState) { cfg, totals, _, pay ->
            val (total, streakDays, today) = totals
            val resolved = today?.let {
                it.status == SavingsStatus.SAVED || it.status == SavingsStatus.RECOVERY_USED
            } ?: false
            DashboardUiState(
                totalSaved = "KES %,d".format(total),
                todaysTarget = "KES %,d".format(cfg.dailyAmount),
                isSavedToday = resolved,
                timeUntilLock = computeTimeUntilLock(cfg.lockTime),
                isSavingEnabled = cfg.savingEnabled,
                streakDays = streakDays,
                showDisablingConfirmation = pay.showDisablingConfirmation,
                mpesaNumber = pay.phoneText ?: cfg.mpesaNumber,
                chargeAmount = "KES %,d".format(cfg.dailyAmount),
                paymentStatus = pay.paymentStatus,
                paymentPhoneError = pay.phoneError
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    fun toggleSavingEnabled(enabled: Boolean) {
        if (!enabled) {
            payState.update { it.copy(showDisablingConfirmation = true) }
        } else {
            viewModelScope.launch { repository.setSavingEnabled(true) }
        }
    }

    fun confirmDisableSaving(confirm: Boolean) {
        payState.update { it.copy(showDisablingConfirmation = false) }
        if (confirm) viewModelScope.launch { repository.setSavingEnabled(false) }
    }

    fun updateMpesaNumber(number: String) {
        val valid = validateMpesa(number)
        payState.update {
            it.copy(phoneText = number, phoneError = if (valid) null else "Format must be 2547XXXXXXXX")
        }
        if (valid) viewModelScope.launch { repository.setMpesaNumber(number) }
    }

    fun triggerPayment() {
        viewModelScope.launch {
            val cfg = repository.getConfig()
            val phone = payState.value.phoneText ?: cfg.mpesaNumber
            if (!validateMpesa(phone)) {
                payState.update { it.copy(phoneError = "Format must be 2547XXXXXXXX") }
                return@launch
            }

            // ---- TEMPORARY simulation (replaced by real Supabase STK push in task 10) ----
            Log.d("DashboardVM", "Simulating STK push for $phone")
            payState.update { it.copy(paymentStatus = PaymentStatus.Requesting) }
            delay(1500)
            payState.update { it.copy(paymentStatus = PaymentStatus.WaitingForSTK) }
            delay(2500)

            val success = kotlin.random.Random.nextDouble() > 0.3
            if (success) {
                repository.markSavedToday(savedAmount = cfg.dailyAmount, checkoutRequestId = null)
                payState.update { it.copy(paymentStatus = PaymentStatus.Success) }
            } else {
                if (kotlin.random.Random.nextBoolean()) {
                    payState.update { it.copy(paymentStatus = PaymentStatus.Failed("Transaction cancelled by user")) }
                } else {
                    payState.update { it.copy(paymentStatus = PaymentStatus.Timeout) }
                }
            }
            // ---- end simulation ----
        }
    }

    fun resetPaymentState() {
        payState.update { it.copy(paymentStatus = PaymentStatus.Idle) }
    }

    private fun validateMpesa(phone: String): Boolean = Regex("^2547\\d{8}$").matches(phone)

    private fun computeTimeUntilLock(lockTime: String): String = runCatching {
        val (h, m) = lockTime.split(":").map { it.toInt() }
        val target = LocalTime.of(h, m)
        val now = LocalTime.now()
        if (!now.isBefore(target)) "0m"
        else {
            val mins = Duration.between(now, target).toMinutes()
            val hh = mins / 60
            val mm = mins % 60
            if (hh > 0) "${hh}h ${mm}m" else "${mm}m"
        }
    }.getOrDefault("--")
}
