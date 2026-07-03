package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.LockMode
import com.example.data.repository.SaveLockRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class LockOverlayUiState(
    val fullLockdown: Boolean = false,
    val bannerMessage: String =
        "Your distraction apps are locked out. Pay a due plan below or enter a recovery code to regain access immediately."
)

/** Feeds the lock screen with a mode-aware banner. The list of due plans comes from the Dashboard VM. */
class LockOverlayViewModel(private val repository: SaveLockRepository) : ViewModel() {

    val uiState: StateFlow<LockOverlayUiState> =
        repository.config.map { cfg ->
            LockOverlayUiState(
                fullLockdown = cfg.lockMode == LockMode.FULL_LOCKDOWN,
                bannerMessage = when (cfg.lockMode) {
                    LockMode.FULL_LOCKDOWN ->
                        "Full lockdown is active. Only emergency calls are available. Pay a due plan " +
                            "below or enter a recovery code to unlock everything."
                    LockMode.CHOSEN_APPS ->
                        "Your distraction apps are locked out. Pay a due plan below or enter a " +
                            "recovery code to regain access immediately."
                }
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LockOverlayUiState())
}
