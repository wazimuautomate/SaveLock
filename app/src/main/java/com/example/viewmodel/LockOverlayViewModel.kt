package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.LockMode
import com.example.data.repository.SaveLockRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class LockOverlayUiState(
    val amountDue: String = "KES 500",
    val deadlinePassed: Boolean = true,
    val fullLockdown: Boolean = false,
    val bannerMessage: String =
        "Your distraction apps are locked out. Complete your daily savings target or enter a recovery code to regain access immediately."
)

/** Feeds the lock/overlay screen with the real amount due and a mode-aware message. */
class LockOverlayViewModel(private val repository: SaveLockRepository) : ViewModel() {

    val uiState: StateFlow<LockOverlayUiState> =
        combine(repository.config, repository.todayLog) { cfg, today ->
            val resolved = today?.let {
                it.status == com.example.data.local.entity.SavingsStatus.SAVED ||
                    it.status == com.example.data.local.entity.SavingsStatus.RECOVERY_USED
            } ?: false
            LockOverlayUiState(
                amountDue = "KES %,d".format(cfg.dailyAmount),
                deadlinePassed = !resolved,
                fullLockdown = cfg.lockMode == LockMode.FULL_LOCKDOWN,
                bannerMessage = when (cfg.lockMode) {
                    LockMode.FULL_LOCKDOWN ->
                        "Full lockdown is active. Only phone calls and messages are available. " +
                            "Save your daily target or enter a recovery code to unlock everything."
                    LockMode.CHOSEN_APPS ->
                        "Your distraction apps are locked out. Complete your daily savings target or " +
                            "enter a recovery code to regain access immediately."
                }
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LockOverlayUiState())
}
