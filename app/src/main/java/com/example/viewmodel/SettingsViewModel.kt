package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.LockMode
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
    val isRestricted: Boolean
)

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

data class SettingsUiState(
    val dailySavingsAmount: String = "500",
    val lockScheduleTime: String = "20:00",
    val reminderLeadHours: List<Int> = listOf(2, 1),
    val mpesaNumber: String = "",
    val distractionApps: List<DistractionApp> = emptyList(),
    val isSavingEnabled: Boolean = true,
    val lockMode: LockMode = LockMode.CHOSEN_APPS,
    val showGenerateRecoveryWarning: Boolean = false,
    val amountError: String? = null,
    val mpesaError: String? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM
)

/**
 * Settings backed by Room. Persisted fields (amount, time, reminders, mpesa, distraction apps,
 * saving-enabled, lock mode) go through the repository and reflect on next app open. Transient UI
 * bits (validation text, theme choice, dialogs) live in [transient].
 */
class SettingsViewModel(private val repository: SaveLockRepository) : ViewModel() {

    private data class Transient(
        val amountText: String? = null,   // in-progress edit; null => show persisted value
        val mpesaText: String? = null,
        val amountError: String? = null,
        val mpesaError: String? = null,
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
        combine(repository.config, transient, installedApps) { cfg, t, apps ->
            SettingsUiState(
                dailySavingsAmount = t.amountText ?: cfg.dailyAmount.toString(),
                lockScheduleTime = cfg.lockTime,
                reminderLeadHours = cfg.reminderLeadHours,
                mpesaNumber = t.mpesaText ?: cfg.mpesaNumber,
                distractionApps = apps.map {
                    DistractionApp(it.packageName, it.label, it.packageName in cfg.restrictedPackages)
                },
                isSavingEnabled = cfg.savingEnabled,
                lockMode = cfg.lockMode,
                showGenerateRecoveryWarning = t.showGenerateRecoveryWarning,
                amountError = t.amountError,
                mpesaError = t.mpesaError,
                themeMode = t.themeMode
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun updateThemeMode(mode: ThemeMode) {
        transient.update { it.copy(themeMode = mode) }
    }

    fun updateDailySavingsAmount(amount: String) {
        val parsed = amount.toIntOrNull()
        val valid = parsed != null && parsed > 0
        transient.update {
            it.copy(amountText = amount, amountError = if (valid) null else "Enter a valid positive number")
        }
        if (valid) viewModelScope.launch { repository.setDailyAmount(parsed!!) }
    }

    fun updateLockScheduleTime(time: String) {
        viewModelScope.launch { repository.setLockTime(time) }
    }

    fun updateLockMode(mode: LockMode) {
        viewModelScope.launch { repository.setLockMode(mode) }
        Log.d("SettingsVM", "Lock mode set to $mode")
    }

    fun addReminderLeadTime(hours: Int) {
        viewModelScope.launch { repository.addReminderLeadHour(hours) }
    }

    fun removeReminderLeadTime(hours: Int) {
        viewModelScope.launch { repository.removeReminderLeadHour(hours) }
    }

    fun updateMpesaNumber(number: String) {
        val valid = Regex("^2547\\d{8}$").matches(number)
        transient.update {
            it.copy(mpesaText = number, mpesaError = if (valid) null else "Format must be 2547XXXXXXXX")
        }
        if (valid) viewModelScope.launch { repository.setMpesaNumber(number) }
    }

    fun toggleDistractionApp(packageName: String) {
        viewModelScope.launch { repository.toggleDistractionApp(packageName) }
    }

    fun toggleSavingEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setSavingEnabled(enabled) }
    }

    fun triggerGenerateRecoveryWarning(show: Boolean) {
        transient.update { it.copy(showGenerateRecoveryWarning = show) }
    }

    /**
     * User confirmed regeneration. The actual generate + one-time reveal happens on the Recovery
     * Codes screen (see RecoveryViewModel.generateNewCodes), which this navigates to via the UI.
     */
    fun generateNewRecoveryCodes() {
        transient.update { it.copy(showGenerateRecoveryWarning = false) }
    }
}
