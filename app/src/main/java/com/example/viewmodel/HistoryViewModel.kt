package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.SaveLockRepository
import com.example.util.DateUtils
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed interface HistoryStatus {
    object Saved : HistoryStatus
    object Missed : HistoryStatus
    object RecoveryUsed : HistoryStatus
}

data class HistoryItem(
    val planName: String,
    val date: String,
    val amountLabel: String,
    val status: HistoryStatus
)

data class HistoryUiState(
    val historyItems: List<HistoryItem> = emptyList(),
    // Trend points 0f..1f (recent payment amounts, normalised), oldest -> newest.
    val trendData: List<Float> = listOf(0f, 0f),
    val totalSavedLabel: String = "KES 0",
    val paymentCount: Int = 0
)

/**
 * History of every plan payment (real money) and recovery unlock, newest first, plus a small trend
 * chart of recent payment sizes. Names come from the plan list (active or not).
 */
class HistoryViewModel(private val repository: SaveLockRepository) : ViewModel() {

    val uiState: StateFlow<HistoryUiState> =
        combine(repository.allPayments, repository.allPlans) { payments, plans ->
            val nameById = plans.associate { it.id to it.name }
            val items = payments.map { p ->
                HistoryItem(
                    planName = nameById[p.planId] ?: "Deleted plan",
                    date = DateUtils.epochToDisplay(p.timestamp),
                    amountLabel = if (p.viaRecovery) "Recovery unlock" else "KES %,d".format(p.amount),
                    status = if (p.viaRecovery) HistoryStatus.RecoveryUsed else HistoryStatus.Saved
                )
            }

            // Trend: the last 12 REAL payments (oldest -> newest), each scaled to the biggest of them.
            val recent = payments.filter { !it.viaRecovery }
                .sortedBy { it.timestamp }
                .takeLast(12)
                .map { it.amount }
            val max = (recent.maxOrNull() ?: 0).coerceAtLeast(1)
            val trend = recent.map { (it.toFloat() / max).coerceIn(0f, 1f) }
            val safeTrend = if (trend.size >= 2) trend else List(2) { trend.firstOrNull() ?: 0f }

            val total = payments.filter { !it.viaRecovery }.sumOf { it.amount }
            HistoryUiState(
                historyItems = items,
                trendData = safeTrend,
                totalSavedLabel = "KES %,d".format(total),
                paymentCount = payments.count { !it.viaRecovery }
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())
}
