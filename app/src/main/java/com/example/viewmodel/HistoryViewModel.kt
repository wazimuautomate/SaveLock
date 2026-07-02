package com.example.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface HistoryStatus {
    object Saved : HistoryStatus
    object Missed : HistoryStatus
    object RecoveryUsed : HistoryStatus
}

data class HistoryItem(
    val date: String,
    val targetAmount: String,
    val savedAmount: String,
    val status: HistoryStatus
)

data class HistoryUiState(
    val historyItems: List<HistoryItem> = listOf(
        HistoryItem("Jul 01, 2026", "KES 500", "KES 500", HistoryStatus.Saved),
        HistoryItem("Jun 30, 2026", "KES 500", "KES 0", HistoryStatus.Missed),
        HistoryItem("Jun 29, 2026", "KES 500", "KES 500", HistoryStatus.Saved),
        HistoryItem("Jun 28, 2026", "KES 500", "KES 0", HistoryStatus.RecoveryUsed),
        HistoryItem("Jun 27, 2026", "KES 500", "KES 500", HistoryStatus.Saved),
        HistoryItem("Jun 26, 2026", "KES 500", "KES 500", HistoryStatus.Saved),
        HistoryItem("Jun 25, 2026", "KES 500", "KES 500", HistoryStatus.Saved),
        HistoryItem("Jun 24, 2026", "KES 500", "KES 0", HistoryStatus.Missed),
        HistoryItem("Jun 23, 2026", "KES 500", "KES 500", HistoryStatus.Saved),
        HistoryItem("Jun 22, 2026", "KES 500", "KES 500", HistoryStatus.Saved),
        HistoryItem("Jun 21, 2026", "KES 500", "KES 500", HistoryStatus.Saved)
    ),
    // Trend points represented as values from 0f to 1f (percentage of target reached)
    val trendData: List<Float> = listOf(1.0f, 1.0f, 1.0f, 0.0f, 1.0f, 1.0f, 1.0f, 1.0f, 0.0f, 1.0f, 0.0f, 1.0f)
)

class HistoryViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()
}
