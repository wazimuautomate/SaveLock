package com.example.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LockOverlayUiState(
    val amountDue: String = "KES 500",
    val deadlinePassed: Boolean = true,
    val bannerMessage: String = "Your distraction apps are locked out. Complete your daily savings target or enter a recovery code to regain access immediately."
)

class LockOverlayViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(LockOverlayUiState())
    val uiState: StateFlow<LockOverlayUiState> = _uiState.asStateFlow()
}
