package com.example.di

import android.content.Context
import com.example.data.local.SaveLockDatabase
import com.example.data.repository.SaveLockRepository
import com.example.domain.LockStateManager
import com.example.domain.RecoveryCodeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Ultra-light manual dependency container. We avoid Hilt/Dagger to keep the app small and the build
 * fast. Call [init] once from [com.example.SaveLockApplication] before anything else uses it.
 */
object ServiceLocator {

    private lateinit var appContext: Context

    /** Long-lived scope for app-wide reactive state (e.g. LockStateManager collectors). */
    val appScope: CoroutineScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val database: SaveLockDatabase by lazy { SaveLockDatabase.get(appContext) }

    val recoveryCodeManager: RecoveryCodeManager by lazy { RecoveryCodeManager() }

    val repository: SaveLockRepository by lazy {
        SaveLockRepository(
            configDao = database.configDao(),
            logDao = database.logDao(),
            recoveryDao = database.recoveryDao(),
            recoveryCodeManager = recoveryCodeManager
        )
    }

    val lockStateManager: LockStateManager by lazy {
        LockStateManager(repository, appScope)
    }
}
