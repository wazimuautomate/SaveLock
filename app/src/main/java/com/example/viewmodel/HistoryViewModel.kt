package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.SavingsLogEntity
import com.example.data.local.entity.SavingsStatus
import com.example.data.repository.SaveLockRepository
import com.example.util.DateUtils
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

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
    val historyItems: List<HistoryItem> = emptyList(),
    // Trend points 0f..1f (fraction of target reached), oldest -> newest.
    val trendData: List<Float> = emptyList()
)

/** Real history/trend backed by Room logs. Same UI contract as the old mock. */
class HistoryViewModel(private val repository: SaveLockRepository) : ViewModel() {

    val uiState: StateFlow<HistoryUiState> =
        repository.logs
            .map { logs ->
                val items = logs.map { it.toHistoryItem() } // logs are newest-first (matches old UI)
                val trend = logs
                    .sortedBy { it.date }        // oldest -> newest for the chart
                    .takeLast(12)
                    .map { l ->
                        if (l.targetAmount <= 0) 0f
                        else (l.savedAmount.toFloat() / l.targetAmount).coerceIn(0f, 1f)
                    }
                // The chart divides by (points - 1), so it needs at least 2 points. Pad safely.
                val safeTrend = if (trend.size >= 2) trend else List(2) { trend.firstOrNull() ?: 0f }
                HistoryUiState(historyItems = items, trendData = safeTrend)
            }
            // Initial value also needs >= 2 trend points so the chart never divides by zero.
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState(trendData = listOf(0f, 0f)))

    private fun SavingsLogEntity.toHistoryItem(): HistoryItem = HistoryItem(
        date = DateUtils.isoToDisplay(date),
        targetAmount = "KES %,d".format(targetAmount),
        savedAmount = "KES %,d".format(savedAmount),
        status = when (status) {
            SavingsStatus.SAVED -> HistoryStatus.Saved
            SavingsStatus.RECOVERY_USED -> HistoryStatus.RecoveryUsed
            else -> HistoryStatus.Missed
        }
    )
}
