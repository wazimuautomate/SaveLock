package com.example.di

import android.content.Context
import com.example.data.local.SaveLockDatabase
import com.example.data.repository.SaveLockRepository
import com.example.domain.RecoveryCodeManager

/**
 * Ultra-light manual dependency container. We avoid Hilt/Dagger to keep the app small and the build
 * fast. Call [init] once from [com.example.SaveLockApplication] before anything else uses it.
 *
 * More singletons (payment repository, alarm scheduler) are added here as those layers land.
 */
object ServiceLocator {

    private lateinit var appContext: Context

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
}
