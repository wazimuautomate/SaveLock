package com.example.viewmodel

import android.graphics.drawable.Drawable
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.AmountType
import com.example.data.local.entity.LockMode
import com.example.data.local.entity.PeriodType
import com.example.data.local.entity.PlanType
import com.example.data.local.entity.SavingsPlanEntity
import com.example.data.repository.SaveLockRepository
import com.example.di.ServiceLocator
import com.example.util.InstalledAppsProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DistractionApp(
    val packageName: String,
    val name: String,
    val isRestricted: Boolean,
    val icon: Drawable?
)

/** Lightweight plan row for the Settings management list. */
data class PlanLite(
    val id: Long,
    val name: String,
    val subtitle: String
)

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

data class SettingsUiState(
    val distractionApps: List<DistractionApp> = emptyList(),
    val isSavingEnabled: Boolean = true,
    val lockMode: LockMode = LockMode.CHOSEN_APPS,
    val plans: List<PlanLite> = emptyList(),
    val showGenerateRecoveryWarning: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM
)

/**
 * Settings backed by Room. Persisted fields (lock mode, saving-enabled, distraction apps) go through
 * the repository. Plan management (create/edit/delete) is surfaced here too — creating/editing is done
 * on the Create screen (via navigation callbacks); this screen lists and deletes.
 */
class SettingsViewModel(private val repository: SaveLockRepository) : ViewModel() {

    private data class Transient(
        val showGenerateRecoveryWarning: Boolean = false,
        val themeMode: ThemeMode = ThemeMode.SYSTEM
    )

    private val transient = MutableStateFlow(Transient())

    // Real apps installed on the phone, loaded once off the main thread.
    private val installedApps = MutableStateFlow<List<InstalledAppsProvider.AppInfo>>(emptyList())

    init {
        viewModelScope.launch {
            installedApps.value = withContext(Dispatchers.IO) {
                InstalledAppsProvider.launchableApps(ServiceLocator.applicationContext)
            }
        }
    }

    val uiState: StateFlow<SettingsUiState> =
        combine(
            repository.config,
            transient,
            installedApps,
            repository.activePlans
        ) { cfg, t, apps, plans ->
            SettingsUiState(
                distractionApps = apps.map {
                    DistractionApp(it.packageName, it.label, it.packageName in cfg.restrictedPackages, it.icon)
                },
                isSavingEnabled = cfg.savingEnabled,
                lockMode = cfg.lockMode,
                plans = plans.map { PlanLite(it.id, it.name, planSubtitle(it)) },
                showGenerateRecoveryWarning = t.showGenerateRecoveryWarning,
                themeMode = t.themeMode
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private fun planSubtitle(p: SavingsPlanEntity): String {
        val kind = if (p.type == PlanType.GOAL) "Goal" else "Savings"
        val min = if (p.amountType == AmountType.FLEXIBLE) " min" else ""
        val period = when (p.period) {
            PeriodType.DAILY -> "daily"
            PeriodType.EVERY_2_DAYS -> "every 2 days"
            PeriodType.WEEKLY -> "weekly"
            PeriodType.MONTHLY -> "monthly"
            PeriodType.EVERY_N_DAYS -> "every ${p.periodValue} days"
            PeriodType.EVERY_N_HOURS -> "every ${p.periodValue} hours"
        }
        val goal = if (p.type == PlanType.GOAL && p.goalTotal > 0) " → KES %,d".format(p.goalTotal) else ""
        return "$kind • KES %,d%s %s%s".format(p.amount, min, period, goal)
    }

    fun updateThemeMode(mode: ThemeMode) {
        transient.update { it.copy(themeMode = mode) }
    }

    fun updateLockMode(mode: LockMode) {
        viewModelScope.launch { repository.setLockMode(mode) }
        Log.d("SettingsVM", "Lock mode set to $mode")
    }

    fun toggleDistractionApp(packageName: String) {
        viewModelScope.launch { repository.toggleDistractionApp(packageName) }
    }

    fun toggleSavingEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setSavingEnabled(enabled) }
    }

    fun deletePlan(id: Long) {
        viewModelScope.launch { repository.deletePlan(id) }
    }

    fun triggerGenerateRecoveryWarning(show: Boolean) {
        transient.update { it.copy(showGenerateRecoveryWarning = show) }
    }

    /** User confirmed regeneration; the actual generate + one-time reveal happens on the Recovery screen. */
    fun generateNewRecoveryCodes() {
        transient.update { it.copy(showGenerateRecoveryWarning = false) }
    }
}
