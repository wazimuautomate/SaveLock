package com.example.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.di.ServiceLocator
import com.example.service.SaveLockForegroundService
import com.example.util.NotificationManagerHelper
import kotlinx.coroutines.launch

/**
 * Fires at the daily lock time. Finalizes stale days, ensures today's row, and — if today isn't
 * saved — turns the lock on (starts the foreground service + status notification). Always re-arms
 * tomorrow's alarm and reminders.
 */
class LockCheckReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        ServiceLocator.appScope.launch {
            try {
                val repo = ServiceLocator.repository
                repo.finalizeStalePendingDays()
                repo.ensureTodayPending()
                val cfg = repo.getConfig()
                val resolved = repo.isTodayResolved()
                ServiceLocator.lockStateManager.recompute()

                if (cfg.savingEnabled && !resolved) {
                    SaveLockForegroundService.start(app)
                    NotificationManagerHelper.showLockActive(app, cfg.dailyAmount)
                }

                // Re-arm for the next day (exact alarm + reminders).
                AlarmScheduler.rescheduleAll(app)
            } finally {
                pending.finish()
            }
        }
    }
}
