package com.example

import android.app.Application
import com.example.di.ServiceLocator
import com.example.scheduling.AlarmScheduler
import com.example.util.NotificationManagerHelper
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * App entry point. Wires the dependency container, creates notification channels, seeds the default
 * config, and keeps the alarms/reminders in sync with the schedule settings.
 */
class SaveLockApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        NotificationManagerHelper.init(this)
        // Touch the lock-state manager so it starts observing config/today immediately.
        ServiceLocator.lockStateManager

        ServiceLocator.appScope.launch {
            ServiceLocator.repository.ensureSeeded()
            // Arm alarms/reminders from the current config on startup.
            AlarmScheduler.rescheduleAll(this@SaveLockApplication)

            // Re-arm whenever the plan set or the master enabled flag changes (a new plan means a
            // new period boundary to watch; disabling saving cancels the alarm).
            combine(
                ServiceLocator.repository.activePlans,
                ServiceLocator.repository.config.map { it.savingEnabled }.distinctUntilChanged()
            ) { plans, enabled -> plans.map { it.id } to enabled }
                .distinctUntilChanged()
                .drop(1) // the initial value was already handled by the rescheduleAll above
                .collect {
                    AlarmScheduler.rescheduleAll(this@SaveLockApplication)
                }
        }
    }
}
