package com.example.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.di.ServiceLocator
import com.example.service.SaveLockForegroundService
import com.example.util.NotificationManagerHelper
import kotlinx.coroutines.launch

/**
 * Fires when a plan's period boundary is reached. Recomputes the lock, and — if any plan is now
 * due-and-unpaid — turns the lock on (starts the foreground service + status notification). Always
 * re-arms for the next upcoming period boundary.
 */
class LockCheckReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        ServiceLocator.appScope.launch {
            try {
                val locked = ServiceLocator.lockStateManager.recompute()
                if (locked) {
                    SaveLockForegroundService.start(app)
                    NotificationManagerHelper.showLockActive(app, 0)
                }
                // Re-arm for the next upcoming period boundary.
                AlarmScheduler.rescheduleAll(app)
            } finally {
                pending.finish()
            }
        }
    }
}
