package com.example

import android.app.Application
import com.example.di.ServiceLocator
import com.example.util.NotificationManagerHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * App entry point. Wires the dependency container, creates notification channels, and seeds the
 * default config on first launch. Registered via android:name in AndroidManifest.
 */
class SaveLockApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        NotificationManagerHelper.init(this)

        // Seed the single config row on the very first launch (off the main thread).
        appScope.launch {
            ServiceLocator.repository.ensureSeeded()
        }
    }
}
