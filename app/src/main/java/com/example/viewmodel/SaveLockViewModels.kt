package com.example.viewmodel

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.di.ServiceLocator

/**
 * One shared factory that builds every ViewModel from the [ServiceLocator]-provided repository.
 * Compose usage: `viewModel<DashboardViewModel>(factory = SaveLockViewModels.Factory)`.
 */
object SaveLockViewModels {
    val Factory: ViewModelProvider.Factory = viewModelFactory {
        initializer { DashboardViewModel(ServiceLocator.repository, ServiceLocator.paymentRepository) }
        initializer { SettingsViewModel(ServiceLocator.repository) }
        initializer { HistoryViewModel(ServiceLocator.repository) }
        initializer { RecoveryViewModel(ServiceLocator.repository) }
        initializer { LockOverlayViewModel(ServiceLocator.repository) }
    }
}
