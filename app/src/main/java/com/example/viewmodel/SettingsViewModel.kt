package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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
    val mpesaNumber: String = "254712345678",
    val distractionApps: List<DistractionApp> = listOf(
        DistractionApp("com.android.chrome", "Google Chrome", true),
        DistractionApp("com.facebook.katana", "Facebook", true),
        DistractionApp("com.instagram.android", "Instagram", false),
        DistractionApp("com.twitter.android", "X (Twitter)", true),
        DistractionApp("com.zhiliaoapp.musically", "TikTok", false),
        DistractionApp("com.youtube.android", "YouTube", true),
        DistractionApp("com.netflix.mediaclient", "Netflix", false),
        DistractionApp("com.reddit.frontpage", "Reddit", false)
    ),
    val isSavingEnabled: Boolean = true,
    val showGenerateRecoveryWarning: Boolean = false,
    val amountError: String? = null,
    val mpesaError: String? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM
)

class SettingsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun updateThemeMode(mode: ThemeMode) {
        _uiState.update { it.copy(themeMode = mode) }
        Log.d("SettingsVM", "Theme mode updated to: $mode")
    }

    fun updateDailySavingsAmount(amount: String) {
        val parsedInt = amount.toIntOrNull()
        _uiState.update { 
            it.copy(
                dailySavingsAmount = amount,
                amountError = if (parsedInt != null && parsedInt > 0) null else "Enter a valid positive number"
            )
        }
        Log.d("SettingsVM", "Updated savings amount to: $amount")
    }

    fun updateLockScheduleTime(time: String) {
        _uiState.update { it.copy(lockScheduleTime = time) }
        Log.d("SettingsVM", "Updated lock schedule to: $time")
    }

    fun addReminderLeadTime(hours: Int) {
        if (hours in 1..23 && !_uiState.value.reminderLeadHours.contains(hours)) {
            _uiState.update { 
                it.copy(reminderLeadHours = (it.reminderLeadHours + hours).sortedDescending()) 
            }
            Log.d("SettingsVM", "Added reminder lead: $hours hours")
        }
    }

    fun removeReminderLeadTime(hours: Int) {
        _uiState.update { 
            it.copy(reminderLeadHours = it.reminderLeadHours.filter { item -> item != hours }) 
        }
        Log.d("SettingsVM", "Removed reminder lead: $hours hours")
    }

    fun updateMpesaNumber(number: String) {
        val isValid = Regex("^2547\\d{8}$").matches(number)
        _uiState.update { 
            it.copy(
                mpesaNumber = number,
                mpesaError = if (isValid) null else "Format must be 2547XXXXXXXX"
            ) 
        }
        Log.d("SettingsVM", "Updated M-Pesa to: $number")
    }

    fun toggleDistractionApp(packageName: String) {
        _uiState.update { state ->
            val updatedApps = state.distractionApps.map { app ->
                if (app.packageName == packageName) {
                    app.copy(isRestricted = !app.isRestricted)
                } else {
                    app
                }
            }
            state.copy(distractionApps = updatedApps)
        }
        Log.d("SettingsVM", "Toggled app restriction for: $packageName")
    }

    fun toggleSavingEnabled(enabled: Boolean) {
        _uiState.update { it.copy(isSavingEnabled = enabled) }
        Log.d("SettingsVM", "Master saving toggle updated: $enabled")
    }

    fun triggerGenerateRecoveryWarning(show: Boolean) {
        _uiState.update { it.copy(showGenerateRecoveryWarning = show) }
    }

    fun generateNewRecoveryCodes() {
        _uiState.update { it.copy(showGenerateRecoveryWarning = false) }
        Log.d("SettingsVM", "Generating brand new recovery codes. Old codes invalidated!")
    }
}
